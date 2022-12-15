/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.upstream;

import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.util.PriorityTaskManager;

/**
 * A {@link DataSource.Factory} that produces {@link PriorityDataSource} instances.
 */
public final class PriorityDataSourceFactory implements Factory {

  private final Factory upstreamFactory;
  private final PriorityTaskManager priorityTaskManager;
  private final int priority;

  /**
   * @param upstreamFactory A {@link DataSource.Factory} to be used to create an upstream {@link
   *     DataSource} for {@link PriorityDataSource}.
   * @param priorityTaskManager The priority manager to which PriorityDataSource task is registered.
   * @param priority The priority of PriorityDataSource task.
   */
  public PriorityDataSourceFactory(Factory upstreamFactory, PriorityTaskManager priorityTaskManager,
      int priority) {
    this.upstreamFactory = upstreamFactory;
    this.priorityTaskManager = priorityTaskManager;
    this.priority = priority;
  }

  @Override
  public PriorityDataSource createDataSource() {
    return new PriorityDataSource(upstreamFactory.createDataSource(), priorityTaskManager,
        priority);
  }

}
