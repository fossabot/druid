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

package org.apache.druid.client.cache;

import com.google.common.base.Preconditions;
import org.apache.druid.java.util.common.Pair;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.emitter.service.ServiceEmitter;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 */
public interface Cache
{
  @Nullable
  byte[] get(NamedKey key);

  void put(NamedKey key, byte[] value);

  /**
   * Resulting map should not contain any null values (i.e. cache misses should not be included)
   *
   * @param keys
   *
   * @return
   */
  Map<NamedKey, byte[]> getBulk(Iterable<NamedKey> keys);

  /**
   * Returns a stream of the input keys with an optional byte array if the key was found in the cache
   *
   * @param keys
   *
   * @return
   */
  default Stream<Pair<NamedKey, Optional<byte[]>>> getBulk(Stream<NamedKey> keys)
  {
    return keys.map(key -> new Pair<>(key, Optional.ofNullable(get(key))));
  }

  void close(String namespace);

  CacheStats getStats();

  boolean isLocal();

  /**
   * Custom metrics not covered by CacheStats may be emitted by this method.
   *
   * @param emitter The service emitter to emit on.
   */
  void doMonitor(ServiceEmitter emitter);

  class NamedKey
  {
    public final String namespace;
    public final byte[] key;

    public NamedKey(String namespace, byte[] key)
    {
      Preconditions.checkArgument(namespace != null, "namespace must not be null");
      Preconditions.checkArgument(key != null, "key must not be null");
      this.namespace = namespace;
      this.key = key;
    }

    public byte[] toByteArray()
    {
      final byte[] nsBytes = StringUtils.toUtf8(this.namespace);
      return ByteBuffer.allocate(Integer.BYTES + nsBytes.length + this.key.length)
                       .putInt(nsBytes.length)
                       .put(nsBytes)
                       .put(this.key).array();
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      NamedKey namedKey = (NamedKey) o;

      if (!namespace.equals(namedKey.namespace)) {
        return false;
      }
      if (!Arrays.equals(key, namedKey.key)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode()
    {
      int result = namespace.hashCode();
      result = 31 * result + Arrays.hashCode(key);
      return result;
    }
  }
}
