/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.MediaSourceEventListener.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

/** Logs events from {@link Player} and other core components using {@link Log}. */
@SuppressWarnings("UngroupedOverloads")
public class EventLogger implements AnalyticsListener {

  private static final String DEFAULT_TAG = "EventLogger";
  private static final int MAX_TIMELINE_ITEM_LINES = 3;
  private static final NumberFormat TIME_FORMAT;
  static {
    TIME_FORMAT = NumberFormat.getInstance(Locale.US);
    TIME_FORMAT.setMinimumFractionDigits(2);
    TIME_FORMAT.setMaximumFractionDigits(2);
    TIME_FORMAT.setGroupingUsed(false);
  }

  private final @Nullable MappingTrackSelector trackSelector;
  private final String tag;
  private final Timeline.Window window;
  private final Timeline.Period period;
  private final long startTimeMs;

  /**
   * Creates event logger.
   *
   * @param trackSelector The mapping track selector used by the player. May be null if detailed
   *     logging of track mapping is not required.
   */
  public EventLogger(@Nullable MappingTrackSelector trackSelector) {
    this(trackSelector, DEFAULT_TAG);
  }

  /**
   * Creates event logger.
   *
   * @param trackSelector The mapping track selector used by the player. May be null if detailed
   *     logging of track mapping is not required.
   * @param tag The tag used for logging.
   */
  public EventLogger(@Nullable MappingTrackSelector trackSelector, String tag) {
    this.trackSelector = trackSelector;
    this.tag = tag;
    window = new Timeline.Window();
    period = new Timeline.Period();
    startTimeMs = SystemClock.elapsedRealtime();
  }

  // AnalyticsListener

  @Override
  public void onLoadingChanged(EventTime eventTime, boolean isLoading) {
    logd(eventTime, "loading", Boolean.toString(isLoading));
  }

  @Override
  public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int state) {
    logd(eventTime, "state", playWhenReady + ", " + getStateString(state));
  }

  @Override
  public void onRepeatModeChanged(EventTime eventTime, @Player.RepeatMode int repeatMode) {
    logd(eventTime, "repeatMode", getRepeatModeString(repeatMode));
  }

  @Override
  public void onShuffleModeChanged(EventTime eventTime, boolean shuffleModeEnabled) {
    logd(eventTime, "shuffleModeEnabled", Boolean.toString(shuffleModeEnabled));
  }

  @Override
  public void onPositionDiscontinuity(EventTime eventTime, @Player.DiscontinuityReason int reason) {
    logd(eventTime, "positionDiscontinuity", getDiscontinuityReasonString(reason));
  }

  @Override
  public void onSeekStarted(EventTime eventTime) {
    logd(eventTime, "seekStarted");
  }

  @Override
  public void onPlaybackParametersChanged(
      EventTime eventTime, PlaybackParameters playbackParameters) {
    logd(
        eventTime,
        "playbackParameters",
        Util.formatInvariant(
            "speed=%.2f, pitch=%.2f, skipSilence=%s",
            playbackParameters.speed, playbackParameters.pitch, playbackParameters.skipSilence));
  }

  @Override
  public void onTimelineChanged(EventTime eventTime, @Player.TimelineChangeReason int reason) {
    int periodCount = eventTime.timeline.getPeriodCount();
    int windowCount = eventTime.timeline.getWindowCount();
    logd(
        "timelineChanged ["
            + getEventTimeString(eventTime)
            + ", periodCount="
            + periodCount
            + ", windowCount="
            + windowCount
            + ", reason="
            + getTimelineChangeReasonString(reason));
    for (int i = 0; i < Math.min(periodCount, MAX_TIMELINE_ITEM_LINES); i++) {
      eventTime.timeline.getPeriod(i, period);
      logd("  " + "period [" + getTimeString(period.getDurationMs()) + "]");
    }
    if (periodCount > MAX_TIMELINE_ITEM_LINES) {
      logd("  ...");
    }
    for (int i = 0; i < Math.min(windowCount, MAX_TIMELINE_ITEM_LINES); i++) {
      eventTime.timeline.getWindow(i, window);
      logd(
          "  "
              + "window ["
              + getTimeString(window.getDurationMs())
              + ", "
              + window.isSeekable
              + ", "
              + window.isDynamic
              + "]");
    }
    if (windowCount > MAX_TIMELINE_ITEM_LINES) {
      logd("  ...");
    }
    logd("]");
  }

  @Override
  public void onPlayerError(EventTime eventTime, ExoPlaybackException e) {
    loge(eventTime, "playerFailed", e);
  }

  @Override
  public void onTracksChanged(
      EventTime eventTime, TrackGroupArray ignored, TrackSelectionArray trackSelections) {
    MappedTrackInfo mappedTrackInfo =
        trackSelector != null ? trackSelector.getCurrentMappedTrackInfo() : null;
    if (mappedTrackInfo == null) {
      logd(eventTime, "tracksChanged", "[]");
      return;
    }
    logd("tracksChanged [" + getEventTimeString(eventTime) + ", ");
    // Log tracks associated to renderers.
    int rendererCount = mappedTrackInfo.getRendererCount();
    for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
      TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
      TrackSelection trackSelection = trackSelections.get(rendererIndex);
      if (rendererTrackGroups.length > 0) {
        logd("  Renderer:" + rendererIndex + " [");
        for (int groupIndex = 0; groupIndex < rendererTrackGroups.length; groupIndex++) {
          TrackGroup trackGroup = rendererTrackGroups.get(groupIndex);
          String adaptiveSupport =
              getAdaptiveSupportString(
                  trackGroup.length,
                  mappedTrackInfo.getAdaptiveSupport(rendererIndex, groupIndex, false));
          logd("    Group:" + groupIndex + ", adaptive_supported=" + adaptiveSupport + " [");
          for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
            String status = getTrackStatusString(trackSelection, trackGroup, trackIndex);
            String formatSupport =
                getFormatSupportString(
                    mappedTrackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex));
            logd(
                "      "
                    + status
                    + " Track:"
                    + trackIndex
                    + ", "
                    + Format.toLogString(trackGroup.getFormat(trackIndex))
                    + ", supported="
                    + formatSupport);
          }
          logd("    ]");
        }
        // Log metadata for at most one of the tracks selected for the renderer.
        if (trackSelection != null) {
          for (int selectionIndex = 0; selectionIndex < trackSelection.length(); selectionIndex++) {
            Metadata metadata = trackSelection.getFormat(selectionIndex).metadata;
            if (metadata != null) {
              logd("    Metadata [");
              printMetadata(metadata, "      ");
              logd("    ]");
              break;
            }
          }
        }
        logd("  ]");
      }
    }
    // Log tracks not associated with a renderer.
    TrackGroupArray unassociatedTrackGroups = mappedTrackInfo.getUnmappedTrackGroups();
    if (unassociatedTrackGroups.length > 0) {
      logd("  Renderer:None [");
      for (int groupIndex = 0; groupIndex < unassociatedTrackGroups.length; groupIndex++) {
        logd("    Group:" + groupIndex + " [");
        TrackGroup trackGroup = unassociatedTrackGroups.get(groupIndex);
        for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
          String status = getTrackStatusString(false);
          String formatSupport =
              getFormatSupportString(RendererCapabilities.FORMAT_UNSUPPORTED_TYPE);
          logd(
              "      "
                  + status
                  + " Track:"
                  + trackIndex
                  + ", "
                  + Format.toLogString(trackGroup.getFormat(trackIndex))
                  + ", supported="
                  + formatSupport);
        }
        logd("    ]");
      }
      logd("  ]");
    }
    logd("]");
  }

  @Override
  public void onSeekProcessed(EventTime eventTime) {
    logd(eventTime, "seekProcessed");
  }

  @Override
  public void onMetadata(EventTime eventTime, Metadata metadata) {
    logd("metadata [" + getEventTimeString(eventTime) + ", ");
    printMetadata(metadata, "  ");
    logd("]");
  }

  @Override
  public void onDecoderEnabled(EventTime eventTime, int trackType, DecoderCounters counters) {
    logd(eventTime, "decoderEnabled", getTrackTypeString(trackType));
  }

  @Override
  public void onAudioSessionId(EventTime eventTime, int audioSessionId) {
    logd(eventTime, "audioSessionId", Integer.toString(audioSessionId));
  }

  @Override
  public void onDecoderInitialized(
      EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {
    logd(eventTime, "decoderInitialized", getTrackTypeString(trackType) + ", " + decoderName);
  }

  @Override
  public void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {
    logd(
        eventTime,
        "decoderInputFormatChanged",
        getTrackTypeString(trackType) + ", " + Format.toLogString(format));
  }

  @Override
  public void onDecoderDisabled(EventTime eventTime, int trackType, DecoderCounters counters) {
    logd(eventTime, "decoderDisabled", getTrackTypeString(trackType));
  }

  @Override
  public void onAudioUnderrun(
      EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    loge(
        eventTime,
        "audioTrackUnderrun",
        bufferSize + ", " + bufferSizeMs + ", " + elapsedSinceLastFeedMs + "]",
        null);
  }

  @Override
  public void onDroppedVideoFrames(EventTime eventTime, int count, long elapsedMs) {
    logd(eventTime, "droppedFrames", Integer.toString(count));
  }

  @Override
  public void onVideoSizeChanged(
      EventTime eventTime,
      int width,
      int height,
      int unappliedRotationDegrees,
      float pixelWidthHeightRatio) {
    logd(eventTime, "videoSizeChanged", width + ", " + height);
  }

  @Override
  public void onRenderedFirstFrame(EventTime eventTime, @Nullable Surface surface) {
    logd(eventTime, "renderedFirstFrame", String.valueOf(surface));
  }

  @Override
  public void onMediaPeriodCreated(EventTime eventTime) {
    logd(eventTime, "mediaPeriodCreated");
  }

  @Override
  public void onMediaPeriodReleased(EventTime eventTime) {
    logd(eventTime, "mediaPeriodReleased");
  }

  @Override
  public void onLoadStarted(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onLoadError(
      EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    printInternalError(eventTime, "loadError", error);
  }

  @Override
  public void onLoadCanceled(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onLoadCompleted(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onReadingStarted(EventTime eventTime) {
    logd(eventTime, "mediaPeriodReadingStarted");
  }

  @Override
  public void onBandwidthEstimate(
      EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
    // Do nothing.
  }

  @Override
  public void onSurfaceSizeChanged(EventTime eventTime, int width, int height) {
    logd(eventTime, "surfaceSizeChanged", width + ", " + height);
  }

  @Override
  public void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) {
    logd(eventTime, "upstreamDiscarded", Format.toLogString(mediaLoadData.trackFormat));
  }

  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
    logd(eventTime, "downstreamFormatChanged", Format.toLogString(mediaLoadData.trackFormat));
  }

  @Override
  public void onDrmSessionAcquired(EventTime eventTime) {
    logd(eventTime, "drmSessionAcquired");
  }

  @Override
  public void onDrmSessionManagerError(EventTime eventTime, Exception e) {
    printInternalError(eventTime, "drmSessionManagerError", e);
  }

  @Override
  public void onDrmKeysRestored(EventTime eventTime) {
    logd(eventTime, "drmKeysRestored");
  }

  @Override
  public void onDrmKeysRemoved(EventTime eventTime) {
    logd(eventTime, "drmKeysRemoved");
  }

  @Override
  public void onDrmKeysLoaded(EventTime eventTime) {
    logd(eventTime, "drmKeysLoaded");
  }

  @Override
  public void onDrmSessionReleased(EventTime eventTime) {
    logd(eventTime, "drmSessionReleased");
  }

  /**
   * Logs a debug message.
   *
   * @param msg The message to log.
   */
  protected void logd(String msg) {
    Log.d(tag, msg);
  }

  /**
   * Logs an error message and exception.
   *
   * @param msg The message to log.
   * @param tr The exception to log.
   */
  protected void loge(String msg, @Nullable Throwable tr) {
    Log.e(tag, msg, tr);
  }

  // Internal methods

  private void logd(EventTime eventTime, String eventName) {
    logd(getEventString(eventTime, eventName));
  }

  private void logd(EventTime eventTime, String eventName, String eventDescription) {
    logd(getEventString(eventTime, eventName, eventDescription));
  }

  private void loge(EventTime eventTime, String eventName, @Nullable Throwable throwable) {
    loge(getEventString(eventTime, eventName), throwable);
  }

  private void loge(
      EventTime eventTime,
      String eventName,
      String eventDescription,
      @Nullable Throwable throwable) {
    loge(getEventString(eventTime, eventName, eventDescription), throwable);
  }

  private void printInternalError(EventTime eventTime, String type, Exception e) {
    loge(eventTime, "internalError", type, e);
  }

  private void printMetadata(Metadata metadata, String prefix) {
    for (int i = 0; i < metadata.length(); i++) {
      logd(prefix + metadata.get(i));
    }
  }

  private String getEventString(EventTime eventTime, String eventName) {
    return eventName + " [" + getEventTimeString(eventTime) + "]";
  }

  private String getEventString(EventTime eventTime, String eventName, String eventDescription) {
    return eventName + " [" + getEventTimeString(eventTime) + ", " + eventDescription + "]";
  }

  private String getEventTimeString(EventTime eventTime) {
    String windowPeriodString = "window=" + eventTime.windowIndex;
    if (eventTime.mediaPeriodId != null) {
      windowPeriodString +=
          ", period=" + eventTime.timeline.getIndexOfPeriod(eventTime.mediaPeriodId.periodUid);
      if (eventTime.mediaPeriodId.isAd()) {
        windowPeriodString += ", adGroup=" + eventTime.mediaPeriodId.adGroupIndex;
        windowPeriodString += ", ad=" + eventTime.mediaPeriodId.adIndexInAdGroup;
      }
    }
    return getTimeString(eventTime.realtimeMs - startTimeMs)
        + ", "
        + getTimeString(eventTime.currentPlaybackPositionMs)
        + ", "
        + windowPeriodString;
  }

  private static String getTimeString(long timeMs) {
    return timeMs == C.TIME_UNSET ? "?" : TIME_FORMAT.format((timeMs) / 1000f);
  }

  private static String getStateString(int state) {
    switch (state) {
      case Player.STATE_BUFFERING:
        return "BUFFERING";
      case Player.STATE_ENDED:
        return "ENDED";
      case Player.STATE_IDLE:
        return "IDLE";
      case Player.STATE_READY:
        return "READY";
      default:
        return "?";
    }
  }

  private static String getFormatSupportString(int formatSupport) {
    switch (formatSupport) {
      case RendererCapabilities.FORMAT_HANDLED:
        return "YES";
      case RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES:
        return "NO_EXCEEDS_CAPABILITIES";
      case RendererCapabilities.FORMAT_UNSUPPORTED_DRM:
        return "NO_UNSUPPORTED_DRM";
      case RendererCapabilities.FORMAT_UNSUPPORTED_SUBTYPE:
        return "NO_UNSUPPORTED_TYPE";
      case RendererCapabilities.FORMAT_UNSUPPORTED_TYPE:
        return "NO";
      default:
        return "?";
    }
  }

  private static String getAdaptiveSupportString(int trackCount, int adaptiveSupport) {
    if (trackCount < 2) {
      return "N/A";
    }
    switch (adaptiveSupport) {
      case RendererCapabilities.ADAPTIVE_SEAMLESS:
        return "YES";
      case RendererCapabilities.ADAPTIVE_NOT_SEAMLESS:
        return "YES_NOT_SEAMLESS";
      case RendererCapabilities.ADAPTIVE_NOT_SUPPORTED:
        return "NO";
      default:
        return "?";
    }
  }

  // Suppressing reference equality warning because the track group stored in the track selection
  // must point to the exact track group object to be considered part of it.
  @SuppressWarnings("ReferenceEquality")
  private static String getTrackStatusString(
      @Nullable TrackSelection selection, TrackGroup group, int trackIndex) {
    return getTrackStatusString(selection != null && selection.getTrackGroup() == group
        && selection.indexOf(trackIndex) != C.INDEX_UNSET);
  }

  private static String getTrackStatusString(boolean enabled) {
    return enabled ? "[X]" : "[ ]";
  }

  private static String getRepeatModeString(@Player.RepeatMode int repeatMode) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return "OFF";
      case Player.REPEAT_MODE_ONE:
        return "ONE";
      case Player.REPEAT_MODE_ALL:
        return "ALL";
      default:
        return "?";
    }
  }

  private static String getDiscontinuityReasonString(@Player.DiscontinuityReason int reason) {
    switch (reason) {
      case Player.DISCONTINUITY_REASON_PERIOD_TRANSITION:
        return "PERIOD_TRANSITION";
      case Player.DISCONTINUITY_REASON_SEEK:
        return "SEEK";
      case Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
        return "SEEK_ADJUSTMENT";
      case Player.DISCONTINUITY_REASON_AD_INSERTION:
        return "AD_INSERTION";
      case Player.DISCONTINUITY_REASON_INTERNAL:
        return "INTERNAL";
      default:
        return "?";
    }
  }

  private static String getTimelineChangeReasonString(@Player.TimelineChangeReason int reason) {
    switch (reason) {
      case Player.TIMELINE_CHANGE_REASON_PREPARED:
        return "PREPARED";
      case Player.TIMELINE_CHANGE_REASON_RESET:
        return "RESET";
      case Player.TIMELINE_CHANGE_REASON_DYNAMIC:
        return "DYNAMIC";
      default:
        return "?";
    }
  }

  private static String getTrackTypeString(int trackType) {
    switch (trackType) {
      case C.TRACK_TYPE_AUDIO:
        return "audio";
      case C.TRACK_TYPE_DEFAULT:
        return "default";
      case C.TRACK_TYPE_METADATA:
        return "metadata";
      case C.TRACK_TYPE_CAMERA_MOTION:
        return "camera motion";
      case C.TRACK_TYPE_NONE:
        return "none";
      case C.TRACK_TYPE_TEXT:
        return "text";
      case C.TRACK_TYPE_VIDEO:
        return "video";
      default:
        return trackType >= C.TRACK_TYPE_CUSTOM_BASE ? "custom (" + trackType + ")" : "?";
    }
  }
}
