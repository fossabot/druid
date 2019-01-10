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

package org.apache.druid.indexing.common.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.apache.druid.indexing.common.LockGranularity;
import org.apache.druid.indexing.common.TaskLock;
import org.apache.druid.indexing.common.TaskLockType;
import org.apache.druid.indexing.common.task.Task;
import org.apache.druid.indexing.overlord.ExistingSegmentLockRequest;
import org.apache.druid.indexing.overlord.LockRequest;
import org.apache.druid.indexing.overlord.LockResult;
import org.apache.druid.indexing.overlord.TimeChunkLockRequest;
import org.joda.time.Interval;

import javax.annotation.Nullable;
import java.util.Set;

public class LockAcquireAction implements TaskAction<TaskLock>
{
  private final LockGranularity granularity;

  private final TaskLockType type;

  @JsonIgnore
  private final Interval interval;

  @Nullable
  private final String version;

  @Nullable
  private final Set<Integer> partitionIds;

  @JsonIgnore
  private final long timeoutMs;

  public static LockAcquireAction createTimeChunkRequest(TaskLockType type, Interval interval, long timeoutMs)
  {
    return new LockAcquireAction(LockGranularity.TIME_CHUNK, type, interval, null, null, timeoutMs);
  }

  public static LockAcquireAction createSegmentRequest(
      TaskLockType type,
      Interval interval,
      String version,
      Set<Integer> partitionIds,
      long timeoutMs
  )
  {
    return new LockAcquireAction(LockGranularity.SEGMENT, type, interval, version, partitionIds, timeoutMs);
  }

  @JsonCreator
  private LockAcquireAction(
      @JsonProperty("lockGranularity") @Nullable LockGranularity granularity, // nullable for backward compatibility
      @JsonProperty("lockType") @Nullable TaskLockType type, // nullable for backward compatibility
      @JsonProperty("interval") Interval interval,
      @JsonProperty("version") @Nullable String version, // null for timeChunk lock
      @JsonProperty("partitionIds") @Nullable Set<Integer> partitionIds, // null for timeChunk lock
      @JsonProperty("timeoutMs") long timeoutMs
  )
  {
    Preconditions.checkArgument(
        granularity == LockGranularity.TIME_CHUNK || version != null,
        "version shouldn't be null for segment lock"
    );
    Preconditions.checkArgument(
        granularity == LockGranularity.TIME_CHUNK || (partitionIds != null && !partitionIds.isEmpty()),
        "partitionIds shouldn't be null or empty for segment lock"
    );

    this.granularity = granularity == null ? LockGranularity.TIME_CHUNK : granularity;
    this.type = type == null ? TaskLockType.EXCLUSIVE : type;
    this.interval = Preconditions.checkNotNull(interval, "interval");
    this.version = version;
    this.partitionIds = partitionIds;
    this.timeoutMs = timeoutMs;
  }

  @JsonProperty("lockGranularity")
  public LockGranularity getGranularity()
  {
    return granularity;
  }

  @JsonProperty("lockType")
  public TaskLockType getType()
  {
    return type;
  }

  @JsonProperty
  public Interval getInterval()
  {
    return interval;
  }

  @JsonProperty
  public String getVersion()
  {
    return version;
  }

  @JsonProperty
  public Set<Integer> getPartitionIds()
  {
    return partitionIds;
  }

  @JsonProperty
  public long getTimeoutMs()
  {
    return timeoutMs;
  }

  @Override
  public TypeReference<TaskLock> getReturnTypeReference()
  {
    return new TypeReference<TaskLock>()
    {
    };
  }

  @Override
  public TaskLock perform(Task task, TaskActionToolbox toolbox)
  {
    final LockRequest request;
    if (granularity == LockGranularity.TIME_CHUNK) {
      request = new TimeChunkLockRequest(
          type,
          task,
          interval,
          version
      );
    } else {
      request = new ExistingSegmentLockRequest(
          type,
          task,
          interval,
          partitionIds,
          version
      );
    }
    try {
      final LockResult result = timeoutMs == 0 ?
                                toolbox.getTaskLockbox().lock(task, request) :
                                toolbox.getTaskLockbox().lock(task, request, timeoutMs);
      return result.isOk() ? result.getTaskLock() : null;
    }
    catch (InterruptedException e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public boolean isAudited()
  {
    return false;
  }

  @Override
  public String toString()
  {
    return "LockAcquireAction{" +
           "granularity=" + granularity +
           ", type=" + type +
           ", interval=" + interval +
           ", timeoutMs=" + timeoutMs +
           '}';
  }
}
