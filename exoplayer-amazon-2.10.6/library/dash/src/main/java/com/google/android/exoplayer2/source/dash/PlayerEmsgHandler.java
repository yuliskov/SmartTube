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
package com.google.android.exoplayer2.source.dash;

import static com.google.android.exoplayer2.util.Util.parseXsDateTime;

import android.os.Handler;
import android.os.Message;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.emsg.EventMessageDecoder;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Handles all emsg messages from all media tracks for the player.
 *
 * <p>This class will only respond to emsg messages which have schemeIdUri
 * "urn:mpeg:dash:event:2012", and value "1"/"2"/"3". When it encounters one of these messages, it
 * will handle the message according to Section 4.5.2.1 DASH -IF IOP Version 4.1:
 *
 * <ul>
 *   <li>If both presentation time delta and event duration are zero, it means the media
 *       presentation has ended.
 *   <li>Else, it will parse the message data from the emsg message to find the publishTime of the
 *       expired manifest, and mark manifest with publishTime smaller than that values to be
 *       expired.
 * </ul>
 *
 * In both cases, the DASH media source will be notified, and a manifest reload should be triggered.
 */
public final class PlayerEmsgHandler implements Handler.Callback {

  private static final int EMSG_MANIFEST_EXPIRED = 1;

  /** Callbacks for player emsg events encountered during DASH live stream. */
  public interface PlayerEmsgCallback {

    /** Called when the current manifest should be refreshed. */
    void onDashManifestRefreshRequested();

    /**
     * Called when the manifest with the publish time has been expired.
     *
     * @param expiredManifestPublishTimeUs The manifest publish time that has been expired.
     */
    void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs);
  }

  private final Allocator allocator;
  private final PlayerEmsgCallback playerEmsgCallback;
  private final EventMessageDecoder decoder;
  private final Handler handler;
  private final TreeMap<Long, Long> manifestPublishTimeToExpiryTimeUs;

  private DashManifest manifest;

  private long expiredManifestPublishTimeUs;
  private long lastLoadedChunkEndTimeUs;
  private long lastLoadedChunkEndTimeBeforeRefreshUs;
  private boolean isWaitingForManifestRefresh;
  private boolean released;

  /**
   * @param manifest The initial manifest.
   * @param playerEmsgCallback The callback that this event handler can invoke when handling emsg
   *     messages that generate DASH media source events.
   * @param allocator An {@link Allocator} from which allocations can be obtained.
   */
  public PlayerEmsgHandler(
      DashManifest manifest, PlayerEmsgCallback playerEmsgCallback, Allocator allocator) {
    this.manifest = manifest;
    this.playerEmsgCallback = playerEmsgCallback;
    this.allocator = allocator;

    manifestPublishTimeToExpiryTimeUs = new TreeMap<>();
    handler = Util.createHandler(/* callback= */ this);
    decoder = new EventMessageDecoder();
    lastLoadedChunkEndTimeUs = C.TIME_UNSET;
    lastLoadedChunkEndTimeBeforeRefreshUs = C.TIME_UNSET;
  }

  /**
   * Updates the {@link DashManifest} that this handler works on.
   *
   * @param newManifest The updated manifest.
   */
  public void updateManifest(DashManifest newManifest) {
    isWaitingForManifestRefresh = false;
    expiredManifestPublishTimeUs = C.TIME_UNSET;
    this.manifest = newManifest;
    removePreviouslyExpiredManifestPublishTimeValues();
  }

  /* package */ boolean maybeRefreshManifestBeforeLoadingNextChunk(long presentationPositionUs) {
    if (!manifest.dynamic) {
      return false;
    }
    if (isWaitingForManifestRefresh) {
      return true;
    }
    boolean manifestRefreshNeeded = false;
    // Find the smallest publishTime (greater than or equal to the current manifest's publish time)
    // that has a corresponding expiry time.
    Map.Entry<Long, Long> expiredEntry = ceilingExpiryEntryForPublishTime(manifest.publishTimeMs);
    if (expiredEntry != null) {
      long expiredPointUs = expiredEntry.getValue();
      if (expiredPointUs < presentationPositionUs) {
        expiredManifestPublishTimeUs = expiredEntry.getKey();
        notifyManifestPublishTimeExpired();
        manifestRefreshNeeded = true;
      }
    }
    if (manifestRefreshNeeded) {
      maybeNotifyDashManifestRefreshNeeded();
    }
    return manifestRefreshNeeded;
  }

  /**
   * For live streaming with emsg event stream, forward seeking can seek pass the emsg messages that
   * signals end-of-stream or Manifest expiry, which results in load error. In this case, we should
   * notify the Dash media source to refresh its manifest.
   *
   * @param chunk The chunk whose load encountered the error.
   * @return True if manifest refresh has been requested, false otherwise.
   */
  /* package */ boolean maybeRefreshManifestOnLoadingError(Chunk chunk) {
    if (!manifest.dynamic) {
      return false;
    }
    if (isWaitingForManifestRefresh) {
      return true;
    }
    boolean isAfterForwardSeek =
        lastLoadedChunkEndTimeUs != C.TIME_UNSET && lastLoadedChunkEndTimeUs < chunk.startTimeUs;
    if (isAfterForwardSeek) {
      // if we are after a forward seek, and the playback is dynamic with embedded emsg stream,
      // there's a chance that we have seek over the emsg messages, in which case we should ask
      // media source for a refresh.
      maybeNotifyDashManifestRefreshNeeded();
      return true;
    }
    return false;
  }

  /**
   * Called when the a new chunk in the current media stream has been loaded.
   *
   * @param chunk The chunk whose load has been completed.
   */
  /* package */ void onChunkLoadCompleted(Chunk chunk) {
    if (lastLoadedChunkEndTimeUs != C.TIME_UNSET || chunk.endTimeUs > lastLoadedChunkEndTimeUs) {
      lastLoadedChunkEndTimeUs = chunk.endTimeUs;
    }
  }

  /**
   * Returns whether an event with given schemeIdUri and value is a DASH emsg event targeting the
   * player.
   */
  public static boolean isPlayerEmsgEvent(String schemeIdUri, String value) {
    return "urn:mpeg:dash:event:2012".equals(schemeIdUri)
        && ("1".equals(value) || "2".equals(value) || "3".equals(value));
  }

  /** Returns a {@link TrackOutput} that emsg messages could be written to. */
  public PlayerTrackEmsgHandler newPlayerTrackEmsgHandler() {
    return new PlayerTrackEmsgHandler(new SampleQueue(allocator));
  }

  /** Release this emsg handler. It should not be reused after this call. */
  public void release() {
    released = true;
    handler.removeCallbacksAndMessages(null);
  }

  @Override
  public boolean handleMessage(Message message) {
    if (released) {
      return true;
    }
    switch (message.what) {
      case (EMSG_MANIFEST_EXPIRED):
        ManifestExpiryEventInfo messageObj = (ManifestExpiryEventInfo) message.obj;
        handleManifestExpiredMessage(
            messageObj.eventTimeUs, messageObj.manifestPublishTimeMsInEmsg);
        return true;
      default:
        // Do nothing.
    }
    return false;
  }

  // Internal methods.

  private void handleManifestExpiredMessage(long eventTimeUs, long manifestPublishTimeMsInEmsg) {
    Long previousExpiryTimeUs = manifestPublishTimeToExpiryTimeUs.get(manifestPublishTimeMsInEmsg);
    if (previousExpiryTimeUs == null) {
      manifestPublishTimeToExpiryTimeUs.put(manifestPublishTimeMsInEmsg, eventTimeUs);
    } else {
      if (previousExpiryTimeUs > eventTimeUs) {
        manifestPublishTimeToExpiryTimeUs.put(manifestPublishTimeMsInEmsg, eventTimeUs);
      }
    }
  }

  private @Nullable Map.Entry<Long, Long> ceilingExpiryEntryForPublishTime(long publishTimeMs) {
    return manifestPublishTimeToExpiryTimeUs.ceilingEntry(publishTimeMs);
  }

  private void removePreviouslyExpiredManifestPublishTimeValues() {
    for (Iterator<Map.Entry<Long, Long>> it =
            manifestPublishTimeToExpiryTimeUs.entrySet().iterator();
        it.hasNext(); ) {
      Map.Entry<Long, Long> entry = it.next();
      long expiredManifestPublishTime = entry.getKey();
      if (expiredManifestPublishTime < manifest.publishTimeMs) {
        it.remove();
      }
    }
  }

  private void notifyManifestPublishTimeExpired() {
    playerEmsgCallback.onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs);
  }

  /** Requests DASH media manifest to be refreshed if necessary. */
  private void maybeNotifyDashManifestRefreshNeeded() {
    if (lastLoadedChunkEndTimeBeforeRefreshUs != C.TIME_UNSET
        && lastLoadedChunkEndTimeBeforeRefreshUs == lastLoadedChunkEndTimeUs) {
      // Already requested manifest refresh.
      return;
    }
    isWaitingForManifestRefresh = true;
    lastLoadedChunkEndTimeBeforeRefreshUs = lastLoadedChunkEndTimeUs;
    playerEmsgCallback.onDashManifestRefreshRequested();
  }

  private static long getManifestPublishTimeMsInEmsg(EventMessage eventMessage) {
    try {
      return parseXsDateTime(Util.fromUtf8Bytes(eventMessage.messageData));
    } catch (ParserException ignored) {
      // if we can't parse this event, ignore
      return C.TIME_UNSET;
    }
  }

  /** Handles emsg messages for a specific track for the player. */
  public final class PlayerTrackEmsgHandler implements TrackOutput {

    private final SampleQueue sampleQueue;
    private final FormatHolder formatHolder;
    private final MetadataInputBuffer buffer;

    /* package */ PlayerTrackEmsgHandler(SampleQueue sampleQueue) {
      this.sampleQueue = sampleQueue;

      formatHolder = new FormatHolder();
      buffer = new MetadataInputBuffer();
    }

    @Override
    public void format(Format format) {
      sampleQueue.format(format);
    }

    @Override
    public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
        throws IOException, InterruptedException {
      return sampleQueue.sampleData(input, length, allowEndOfInput);
    }

    @Override
    public void sampleData(ParsableByteArray data, int length) {
      sampleQueue.sampleData(data, length);
    }

    @Override
    public void sampleMetadata(
        long timeUs, int flags, int size, int offset, @Nullable CryptoData encryptionData) {
      sampleQueue.sampleMetadata(timeUs, flags, size, offset, encryptionData);
      parseAndDiscardSamples();
    }

    /**
     * For live streaming, check if the DASH manifest is expired before the next segment start time.
     * If it is, the DASH media source will be notified to refresh the manifest.
     *
     * @param presentationPositionUs The next load position in presentation time.
     * @return True if manifest refresh has been requested, false otherwise.
     */
    public boolean maybeRefreshManifestBeforeLoadingNextChunk(long presentationPositionUs) {
      return PlayerEmsgHandler.this.maybeRefreshManifestBeforeLoadingNextChunk(
          presentationPositionUs);
    }

    /**
     * Called when the a new chunk in the current media stream has been loaded.
     *
     * @param chunk The chunk whose load has been completed.
     */
    public void onChunkLoadCompleted(Chunk chunk) {
      PlayerEmsgHandler.this.onChunkLoadCompleted(chunk);
    }

    /**
     * For live streaming with emsg event stream, forward seeking can seek pass the emsg messages
     * that signals end-of-stream or Manifest expiry, which results in load error. In this case, we
     * should notify the Dash media source to refresh its manifest.
     *
     * @param chunk The chunk whose load encountered the error.
     * @return True if manifest refresh has been requested, false otherwise.
     */
    public boolean maybeRefreshManifestOnLoadingError(Chunk chunk) {
      return PlayerEmsgHandler.this.maybeRefreshManifestOnLoadingError(chunk);
    }

    /** Release this track emsg handler. It should not be reused after this call. */
    public void release() {
      sampleQueue.reset();
    }

    // Internal methods.

    private void parseAndDiscardSamples() {
      while (sampleQueue.hasNextSample()) {
        MetadataInputBuffer inputBuffer = dequeueSample();
        if (inputBuffer == null) {
          continue;
        }
        long eventTimeUs = inputBuffer.timeUs;
        Metadata metadata = decoder.decode(inputBuffer);
        if (metadata == null) {
          continue;
        }
        EventMessage eventMessage = (EventMessage) metadata.get(0);
        if (isPlayerEmsgEvent(eventMessage.schemeIdUri, eventMessage.value)) {
          parsePlayerEmsgEvent(eventTimeUs, eventMessage);
        }
      }
      sampleQueue.discardToRead();
    }

    @Nullable
    private MetadataInputBuffer dequeueSample() {
      buffer.clear();
      int result = sampleQueue.read(formatHolder, buffer, false, false, 0);
      if (result == C.RESULT_BUFFER_READ) {
        buffer.flip();
        return buffer;
      }
      return null;
    }

    private void parsePlayerEmsgEvent(long eventTimeUs, EventMessage eventMessage) {
      long manifestPublishTimeMsInEmsg = getManifestPublishTimeMsInEmsg(eventMessage);
      if (manifestPublishTimeMsInEmsg == C.TIME_UNSET) {
        return;
      }
      onManifestExpiredMessageEncountered(eventTimeUs, manifestPublishTimeMsInEmsg);
    }

    private void onManifestExpiredMessageEncountered(
        long eventTimeUs, long manifestPublishTimeMsInEmsg) {
      ManifestExpiryEventInfo manifestExpiryEventInfo =
          new ManifestExpiryEventInfo(eventTimeUs, manifestPublishTimeMsInEmsg);
      handler.sendMessage(handler.obtainMessage(EMSG_MANIFEST_EXPIRED, manifestExpiryEventInfo));
    }
  }

  /** Holds information related to a manifest expiry event. */
  private static final class ManifestExpiryEventInfo {

    public final long eventTimeUs;
    public final long manifestPublishTimeMsInEmsg;

    public ManifestExpiryEventInfo(long eventTimeUs, long manifestPublishTimeMsInEmsg) {
      this.eventTimeUs = eventTimeUs;
      this.manifestPublishTimeMsInEmsg = manifestPublishTimeMsInEmsg;
    }
  }
}
