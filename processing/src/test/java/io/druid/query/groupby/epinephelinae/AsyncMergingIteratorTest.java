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

import com.google.common.collect.ImmutableList;
import io.druid.concurrent.Execs;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

public class AsyncMergingIteratorTest
{
  private final ExecutorService exec = Execs.singleThreaded("async-merging-iterator-test-%d");

  @After
  public void tearDown()
  {
    exec.shutdownNow();
  }

  @Test
  public void testMerge()
  {
    final int numVals = 10000;
    final PrimitiveIterator.OfInt oddIterator = IntStream.range(0, numVals).map(i -> i * 2 + 1).iterator();
    final PrimitiveIterator.OfInt evenIterator = IntStream.range(0, numVals).map(i -> i * 2).iterator();

    final AsyncMergingIterator mergingIterator = new AsyncMergingIterator<>(
        exec,
        0,
        ImmutableList.of(oddIterator, evenIterator),
        Comparator.comparingInt(i -> i)
    );

    int expectedValue = 0;
    while (mergingIterator.hasNext()) {
      Assert.assertEquals(expectedValue++, mergingIterator.next());
    }
    Assert.assertEquals(expectedValue, numVals * 2);
  }

  @Test
  public void testMultiLevelMerge()
  {
    final int numVals = 10000;

    final List<Iterator<Integer>> iterators = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final int r = i;
      iterators.add(IntStream.range(0, numVals).map(v -> v * 5 + r).iterator());
    }

    final Iterator<Integer> iterator = Groupers.parallalMergeIterators(
        exec,
        0,
        0,
        iterators,
        Comparator.comparingInt(i -> i)
    );

    int expectedValue = 0;
    while (iterator.hasNext()) {
      Assert.assertEquals(expectedValue++, iterator.next().intValue());
    }
    Assert.assertEquals(expectedValue, numVals * 5);
  }
}