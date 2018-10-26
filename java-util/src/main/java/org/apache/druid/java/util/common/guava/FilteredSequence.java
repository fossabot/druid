/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.java.util.common.guava;

import com.google.common.base.Predicate;

import java.io.IOException;
import java.util.function.Supplier;

/**
 */
public class FilteredSequence<T> implements Sequence<T>
{
  private final Sequence<T> baseSequence;
  private final Predicate<T> pred;

  public FilteredSequence(
      Sequence<T> baseSequence,
      Predicate<T> pred
  )
  {
    this.baseSequence = baseSequence;
    this.pred = pred;
  }

  @Override
  public <OutType> OutType accumulate(Supplier<OutType> initValue, Accumulator<OutType, T> accumulator, Supplier<Accumulator<OutType, T>> accumulatorSupplier)
  {
    return baseSequence.accumulate(initValue, new FilteringAccumulator<>(pred, accumulator), () -> new FilteringAccumulator<>(pred, accumulatorSupplier
        .get()));
  }

  @Override
  public <OutType> Yielder<OutType> toYielder(Supplier<OutType> initValue, YieldingAccumulator<OutType, T> statefulAccumulator, Supplier<YieldingAccumulator<OutType, T>> accumulator)
  {
    final FilteringYieldingAccumulator<OutType, T> filteringAccumulator = new FilteringYieldingAccumulator<>(
        pred, statefulAccumulator
    );

    return wrapYielder(baseSequence.toYielder(initValue, filteringAccumulator, () -> new FilteringYieldingAccumulator<>(pred, accumulator.get())), filteringAccumulator);
  }

  private <OutType> Yielder<OutType> wrapYielder(
      final Yielder<OutType> yielder, final FilteringYieldingAccumulator<OutType, T> accumulator
  )
  {
    return new Yielder<OutType>()
    {
      @Override
      public OutType get()
      {
        return yielder.get();
      }

      @Override
      public Yielder<OutType> next(OutType initValue)
      {
        return wrapYielder(yielder.next(initValue), accumulator);
      }

      @Override
      public boolean isDone()
      {
        return !accumulator.didSomething() || yielder.isDone();
      }

      @Override
      public void close() throws IOException
      {
        yielder.close();
      }
    };
  }
}
