/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.query.groupby.epinephelinae;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import io.druid.java.util.common.IAE;
import io.druid.java.util.common.ISE;
import io.druid.java.util.common.logger.Logger;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.BufferAggregator;
import io.druid.query.groupby.epinephelinae.column.StringGroupByColumnSelectorStrategy;
import io.druid.segment.ColumnSelectorFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;

/**
 * A grouper for array-based aggregation.  The array consists of records.  The first record is to store
 * {@link StringGroupByColumnSelectorStrategy#GROUP_BY_MISSING_VALUE}.
 * The memory format of the record is like below.
 *
 * +---------------+----------+--------------------+--------------------+-----+
 * | used flag (4) |  key (4) | agg_1 (fixed size) | agg_2 (fixed size) | ... |
 * +---------------+----------+--------------------+--------------------+-----+
 */
public class BufferArrayGrouper2<KeyType> implements Grouper<KeyType>
{
  private static final Logger LOG = new Logger(BufferArrayGrouper2.class);

  private final Supplier<ByteBuffer> bufferSupplier;
  private final KeySerde<KeyType> keySerde;
  private final BufferAggregator[] aggregators;
  private final int[] aggregatorOffsets;
  private final int cardinality;
  private final int recordSize; // keySize + size of all aggregated values

  private boolean initialized = false;
  private ByteBuffer keyBuffer;
  private ByteBuffer usedBuffer;
  private ByteBuffer valBuffer;

  public static <KeyType> int requiredBufferCapacity(KeySerde<KeyType> keySerde, int cardinality, AggregatorFactory[] aggregatorFactories)
  {
    final int cardinalityWithMissingVal = cardinality + 1;
    final int recordSize = Arrays.stream(aggregatorFactories)
                                    .mapToInt(AggregatorFactory::getMaxIntermediateSize)
                                    .sum();
    return keySerde.keySize() + cardinalityWithMissingVal * recordSize + (int) Math.ceil((double)(cardinalityWithMissingVal) / 8);
  }

  public BufferArrayGrouper2(
      final Supplier<ByteBuffer> bufferSupplier,
      final KeySerde<KeyType> keySerde,
      final ColumnSelectorFactory columnSelectorFactory,
      final AggregatorFactory[] aggregatorFactories,
      final int cardinality
  )
  {
    Preconditions.checkNotNull(aggregatorFactories, "aggregatorFactories");
    Preconditions.checkArgument(cardinality > 0, "Cardinality must a non-zero positive number");

    this.bufferSupplier = Preconditions.checkNotNull(bufferSupplier, "bufferSupplier");
    this.keySerde = Preconditions.checkNotNull(keySerde, "keySerde");
    this.aggregators = new BufferAggregator[aggregatorFactories.length];
    this.aggregatorOffsets = new int[aggregatorFactories.length];
    this.cardinality = cardinality + 1; // TODO: make better

    int offset = 0;
    for (int i = 0; i < aggregatorFactories.length; i++) {
      aggregators[i] = aggregatorFactories[i].factorizeBuffered(columnSelectorFactory);
      aggregatorOffsets[i] = offset;
      offset += aggregatorFactories[i].getMaxIntermediateSize();
    }
    recordSize = offset;
  }

  @Override
  public void init()
  {
    if (!initialized) {
      final ByteBuffer buffer = bufferSupplier.get().duplicate();

      buffer.position(0);
      buffer.limit(keySerde.keySize());
      keyBuffer = buffer.slice();

      final int usedBufferEnd = keySerde.keySize() + (int) Math.ceil((double)(cardinality) / 8);
      buffer.position(keySerde.keySize());
      buffer.limit(usedBufferEnd);
      usedBuffer = buffer.slice();

      buffer.position(usedBufferEnd);
      buffer.limit(buffer.capacity());
      valBuffer = buffer.slice();

      reset();

      initialized = true;
    }
  }

  @Override
  public boolean isInitialized()
  {
    return initialized;
  }

  private ByteBuffer checkAndGetKey(KeyType key)
  {
    final ByteBuffer fromKey = keySerde.toByteBuffer(key);
    if (fromKey == null) {
      return null;
    }

    if (fromKey.remaining() != keySerde.keySize()) {
      throw new IAE(
          "keySerde.toByteBuffer(key).remaining[%s] != keySerde.keySize[%s], buffer was the wrong size?!",
          fromKey.remaining(),
          keySerde.keySize()
      );
    }

    return fromKey;
  }

  @Override
  public AggregateResult aggregate(KeyType key, int dimIndex)
  {
    Preconditions.checkArgument(dimIndex > -1, "Invalid dimIndex[%s]", dimIndex);

    final ByteBuffer fromKey = checkAndGetKey(key);
    if (fromKey == null) {
      // This may just trigger a spill and get ignored, which is ok. If it bubbles up to the user, the message will
      // be correct.
      return Groupers.DICTIONARY_FULL;
    }

    final int recordOffset = dimIndex * recordSize;

    if (recordOffset + recordSize > valBuffer.capacity()) {
      // This error cannot be recoverd, and the query must fail
      throw new ISE(
          "A record of size [%d] cannot be written to the array buffer at offset[%d] "
          + "because it exceeds the buffer capacity[%d]. Try increasing druid.processing.buffer.sizeBytes",
          recordSize,
          recordOffset,
          valBuffer.capacity()
      );
    }

    if (!isUsedKey(dimIndex)) {
      initializeSlot(dimIndex);
    }

    for (int i = 0; i < aggregators.length; i++) {
      aggregators[i].aggregate(valBuffer, recordOffset + aggregatorOffsets[i]);
    }

    return AggregateResult.ok();
  }

  private void initializeSlot(int dimIndex)
  {
    final int index = dimIndex / 8;
    final int extraIndex = dimIndex % 8;
    usedBuffer.put(index, (byte) (usedBuffer.get(index) | 1 << extraIndex));

    final int recordOffset = dimIndex * recordSize;
    for (int j = 0; j < aggregators.length; ++j) {
      aggregators[j].init(valBuffer, recordOffset + aggregatorOffsets[j]);
    }
  }

  @Override
  public AggregateResult aggregate(KeyType key)
  {
    // BufferArrayGrouper is used only for dictionary-indexed single-value string dimensions.
    // Here, the key contains the dictionary-encoded value of the grouping key
    // and we use it as the index for the aggregation array.

    final ByteBuffer fromKey = checkAndGetKey(key);
    if (fromKey == null) {
      // This may just trigger a spill and get ignored, which is ok. If it bubbles up to the user, the message will
      // be correct.
      return Groupers.DICTIONARY_FULL;
    }

    final int dimIndex = fromKey.getInt() + 1; // the first index is for missing value
    fromKey.rewind();
    return aggregate(key, dimIndex);
  }

  private boolean isUsedKey(int dimIndex)
  {
    final int index = dimIndex / 8;
    final int extraIndex = dimIndex % 8;
    final int expected = 1 << extraIndex;
    return (usedBuffer.get(index) & expected) == expected;
  }

  @Override
  public void reset()
  {
    final int usedBufferCapacity = (int) Math.ceil((double)cardinality / 8);
    for (int i = 0; i < usedBufferCapacity; i++) {
      usedBuffer.put(i, (byte) 0);
    }
  }

  @Override
  public void close()
  {
    for (BufferAggregator aggregator : aggregators) {
      try {
        aggregator.close();
      }
      catch (Exception e) {
        LOG.warn(e, "Could not close aggregator [%s], skipping.", aggregator);
      }
    }
  }

  @Override
  public Iterator<Entry<KeyType>> iterator(boolean sorted)
  {
    return sorted ? sortedIterator() : plainIterator();
  }

  private Iterator<Entry<KeyType>> sortedIterator()
  {
    // Sorted iterator is currently not used because there is no way to get grouping key's cardinality when merging
    // partial aggregation result in brokers and even data nodes (historicals and realtimes).
    // However, it should be used in the future.
//    final BufferComparator comparator = keySerde.bufferComparator();
//    final List<Integer> wrappedOffsets = IntStream.range(0, cardinality)
//                                                  .filter(this::isUsedKey)
//                                                  .boxed()
//                                                  .collect(Collectors.toList());
//    wrappedOffsets.sort(
//        (lhs, rhs) -> comparator.compare(valBuffer, valBuffer, lhs, rhs)
//    );
//
//    return new ResultIterator();
    // TODO
    return plainIterator();
  }

  private Iterator<Entry<KeyType>> plainIterator()
  {
//    return new ResultIterator(
//        IntStream.range(0, cardinality).filter(this::isUsedKey)
//    );

    return IntStream.range(0, cardinality)
                    .filter(this::isUsedKey)
                    .mapToObj(cur -> {
                      keyBuffer.putInt(0, cur - 1);

                      final Object[] values = new Object[aggregators.length];
                      final int recordOffset = cur * recordSize;
                      for (int i = 0; i < aggregators.length; i++) {
                        values[i] = aggregators[i].get(valBuffer, recordOffset + aggregatorOffsets[i]);
                      }
                      return new Entry<>(keySerde.fromByteBuffer(keyBuffer, 0), values);
                    }).iterator();
  }

  private class ResultIterator implements Iterator<Entry<KeyType>>
  {
    private static final int NOT_FOUND = -1;

    private final Iterator<Integer> keyIndexIterator;
    private int cur;
    private boolean needFindNext;

    ResultIterator(IntStream keyOffsets)
    {
      keyIndexIterator = keyOffsets.iterator();
      cur = NOT_FOUND;
      needFindNext = true;
    }

    @Override
    public boolean hasNext()
    {
      if (needFindNext) {
        cur = keyIndexIterator.hasNext() ? keyIndexIterator.next() : NOT_FOUND;
        needFindNext = false;
      }
      return cur > NOT_FOUND;
    }

    @Override
    public Entry<KeyType> next()
    {
      if (cur == NOT_FOUND) {
        throw new NoSuchElementException();
      }

      keyBuffer.putInt(0, cur - 1);

      needFindNext = true;
      final Object[] values = new Object[aggregators.length];
      final int recordOffset = cur * recordSize;
      for (int i = 0; i < aggregators.length; i++) {
        values[i] = aggregators[i].get(valBuffer, recordOffset + aggregatorOffsets[i]);
      }
      return new Entry<>(keySerde.fromByteBuffer(keyBuffer, 0), values);
    }
  }
}
