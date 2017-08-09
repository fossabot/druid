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

import java.util.function.ToIntFunction;

/**
 * {@link Grouper} specialized for the primitive int type
 */
public interface IntGrouper extends Grouper<Integer>
{
  default AggregateResult aggregate(int key)
  {
    return aggregate(key, hashFunction().apply(key));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  default AggregateResult aggregate(Integer key)
  {
    return aggregate(key.intValue());
  }

  AggregateResult aggregate(int key, int keyHash);

  /**
   * {@inheritDoc}
   */
  @Override
  default AggregateResult aggregate(Integer key, int keyHash)
  {
    return aggregate(key.intValue(), keyHash);
  }

  @Override
  default IntGrouperHashFunction hashFunction()
  {
    return Integer::hashCode;
  }

  interface IntGrouperHashFunction extends ToIntFunction<Integer>
  {
    @Override
    default int applyAsInt(Integer value)
    {
      return apply(value);
    }

    int apply(int value);
  }
}
