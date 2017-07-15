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

package io.druid.indexing.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.joda.time.Interval;

/**
 * Represents a lock held by some task. Immutable.
 */
public class TaskLock
{
  private final TaskLockType type;
  private final String groupId;
  private final String dataSource;
  private final Interval interval;
  private final String version;
  private final int priority;
  private final boolean upgraded;
  private final boolean revoked;

  @JsonCreator
  public TaskLock(
      @JsonProperty("type") TaskLockType type,
      @JsonProperty("groupId") String groupId,
      @JsonProperty("dataSource") String dataSource,
      @JsonProperty("interval") Interval interval,
      @JsonProperty("version") String version,
      @JsonProperty("priority") int priority,
      @JsonProperty("upgraded") boolean upgraded,
      @JsonProperty("revoked") boolean revoked
  )
  {
    Preconditions.checkArgument(!type.equals(TaskLockType.SHARED) || !upgraded, "lock[%s] cannot be upgraded", type);
    Preconditions.checkArgument(!upgraded || !revoked, "Upgraded locks cannot be revoked");

    this.type = type;
    this.groupId = Preconditions.checkNotNull(groupId);
    this.dataSource = Preconditions.checkNotNull(dataSource);
    this.interval = Preconditions.checkNotNull(interval);
    this.version = Preconditions.checkNotNull(version);
    this.priority = priority;
    this.upgraded = upgraded;
    this.revoked = revoked;
  }

  public TaskLock(
      TaskLockType type,
      String groupId,
      String dataSource,
      Interval interval,
      String version,
      int priority
  )
  {
    this(type, groupId, dataSource, interval, version, priority, false, false);
  }

  public TaskLock upgrade()
  {
    Preconditions.checkState(!revoked, "Revoked locks cannot be upgraded");
    Preconditions.checkState(!upgraded, "Already upgraded");
    return new TaskLock(type, groupId, dataSource, interval, version, priority, true, revoked);
  }

  public TaskLock downgrade()
  {
    Preconditions.checkState(!revoked, "Revoked locks cannot be downgraded");
    Preconditions.checkState(upgraded, "Already downgraded");
    return new TaskLock(type, groupId, dataSource, interval, version, priority, false, revoked);
  }

  public TaskLock revoke()
  {
    Preconditions.checkState(!revoked, "Already revoked");
    Preconditions.checkState(!upgraded, "Upgraded locks cannot be revoked");
    return new TaskLock(type, groupId, dataSource, interval, version, priority, upgraded, true);
  }

  @JsonProperty
  public TaskLockType getType()
  {
    return type;
  }

  @JsonProperty
  public String getGroupId()
  {
    return groupId;
  }

  @JsonProperty
  public String getDataSource()
  {
    return dataSource;
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
  public int getPriority()
  {
    return priority;
  }

  @JsonProperty
  public boolean isUpgraded()
  {
    return upgraded;
  }

  @JsonProperty
  public boolean isRevoked()
  {
    return revoked;
  }

  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof TaskLock)) {
      return false;
    } else {
      final TaskLock that = (TaskLock) o;
      return this.type.equals(that.type) &&
             this.groupId.equals(that.groupId) &&
             this.dataSource.equals(that.dataSource) &&
             this.interval.equals(that.interval) &&
             this.version.equals(that.version) &&
             this.priority == that.priority &&
             this.upgraded == that.upgraded &&
             this.revoked == that.revoked;
    }
  }

  @Override
  public int hashCode()
  {
    return Objects.hashCode(type, groupId, dataSource, interval, version, priority, upgraded, revoked);
  }

  @Override
  public String toString()
  {
    return Objects.toStringHelper(this)
                  .add("type", type)
                  .add("groupId", groupId)
                  .add("dataSource", dataSource)
                  .add("interval", interval)
                  .add("version", version)
                  .add("priority", priority)
                  .add("upgraded", upgraded)
                  .add("revoked", revoked)
                  .toString();
  }
}
