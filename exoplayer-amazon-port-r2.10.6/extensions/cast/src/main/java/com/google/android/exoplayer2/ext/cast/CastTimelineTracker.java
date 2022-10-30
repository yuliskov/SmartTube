/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import java.util.HashSet;

/**
 * Creates {@link CastTimeline CastTimelines} from cast receiver app status updates.
 *
 * <p>This class keeps track of the duration reported by the current item to fill any missing
 * durations in the media queue items [See internal: b/65152553].
 */
/* package */ final class CastTimelineTracker {

  private final SparseArray<CastTimeline.ItemData> itemIdToData;

  public CastTimelineTracker() {
    itemIdToData = new SparseArray<>();
  }

  /**
   * Returns a {@link CastTimeline} that represents the state of the given {@code
   * remoteMediaClient}.
   *
   * <p>Returned timelines may contain values obtained from {@code remoteMediaClient} in previous
   * invocations of this method.
   *
   * @param remoteMediaClient The Cast media client.
   * @return A {@link CastTimeline} that represents the given {@code remoteMediaClient} status.
   */
  public CastTimeline getCastTimeline(RemoteMediaClient remoteMediaClient) {
    int[] itemIds = remoteMediaClient.getMediaQueue().getItemIds();
    if (itemIds.length > 0) {
      // Only remove unused items when there is something in the queue to avoid removing all entries
      // if the remote media client clears the queue temporarily. See [Internal ref: b/128825216].
      removeUnusedItemDataEntries(itemIds);
    }

    // TODO: Reset state when the app instance changes [Internal ref: b/129672468].
    MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
    if (mediaStatus == null) {
      return CastTimeline.EMPTY_CAST_TIMELINE;
    }

    int currentItemId = mediaStatus.getCurrentItemId();
    long durationUs = CastUtils.getStreamDurationUs(mediaStatus.getMediaInfo());
    itemIdToData.put(
        currentItemId,
        itemIdToData
            .get(currentItemId, CastTimeline.ItemData.EMPTY)
            .copyWithDurationUs(durationUs));

    for (MediaQueueItem item : mediaStatus.getQueueItems()) {
      int itemId = item.getItemId();
      itemIdToData.put(
          itemId,
          itemIdToData
              .get(itemId, CastTimeline.ItemData.EMPTY)
              .copyWithDefaultPositionUs((long) (item.getStartTime() * C.MICROS_PER_SECOND)));
    }

    return new CastTimeline(itemIds, itemIdToData);
  }

  private void removeUnusedItemDataEntries(int[] itemIds) {
    HashSet<Integer> scratchItemIds = new HashSet<>(/* initialCapacity= */ itemIds.length * 2);
    for (int id : itemIds) {
      scratchItemIds.add(id);
    }

    int index = 0;
    while (index < itemIdToData.size()) {
      if (!scratchItemIds.contains(itemIdToData.keyAt(index))) {
        itemIdToData.removeAt(index);
      } else {
        index++;
      }
    }
  }
}
