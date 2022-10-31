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
package com.google.android.exoplayer2.source.hls;

import android.net.Uri;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.Rendition;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.Variant;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link MediaPeriod} that loads an HLS stream.
 */
public final class HlsMediaPeriod implements MediaPeriod, HlsSampleStreamWrapper.Callback,
    HlsPlaylistTracker.PlaylistEventListener {

  private final HlsExtractorFactory extractorFactory;
  private final HlsPlaylistTracker playlistTracker;
  private final HlsDataSourceFactory dataSourceFactory;
  private final @Nullable TransferListener mediaTransferListener;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final EventDispatcher eventDispatcher;
  private final Allocator allocator;
  private final IdentityHashMap<SampleStream, Integer> streamWrapperIndices;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final boolean allowChunklessPreparation;
  private final @HlsMetadataType int metadataType;
  private final boolean useSessionKeys;

  private @Nullable Callback callback;
  private int pendingPrepareCount;
  private TrackGroupArray trackGroups;
  private HlsSampleStreamWrapper[] sampleStreamWrappers;
  private HlsSampleStreamWrapper[] enabledSampleStreamWrappers;
  // Maps sample stream wrappers to variant/rendition index by matching array positions.
  private int[][] manifestUrlIndicesPerWrapper;
  private SequenceableLoader compositeSequenceableLoader;
  private boolean notifiedReadingStarted;

  /**
   * Creates an HLS media period.
   *
   * @param extractorFactory An {@link HlsExtractorFactory} for {@link Extractor}s for the segments.
   * @param playlistTracker A tracker for HLS playlists.
   * @param dataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for segments
   *     and keys.
   * @param mediaTransferListener The transfer listener to inform of any media data transfers. May
   *     be null if no listener is available.
   * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
   * @param eventDispatcher A dispatcher to notify of events.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param compositeSequenceableLoaderFactory A factory to create composite {@link
   *     SequenceableLoader}s for when this media source loads data from multiple streams.
   * @param allowChunklessPreparation Whether chunkless preparation is allowed.
   * @param useSessionKeys Whether to use #EXT-X-SESSION-KEY tags.
   */
  public HlsMediaPeriod(
      HlsExtractorFactory extractorFactory,
      HlsPlaylistTracker playlistTracker,
      HlsDataSourceFactory dataSourceFactory,
      @Nullable TransferListener mediaTransferListener,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher eventDispatcher,
      Allocator allocator,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      boolean allowChunklessPreparation,
      @HlsMetadataType int metadataType,
      boolean useSessionKeys) {
    this.extractorFactory = extractorFactory;
    this.playlistTracker = playlistTracker;
    this.dataSourceFactory = dataSourceFactory;
    this.mediaTransferListener = mediaTransferListener;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.eventDispatcher = eventDispatcher;
    this.allocator = allocator;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.allowChunklessPreparation = allowChunklessPreparation;
    this.metadataType = metadataType;
    this.useSessionKeys = useSessionKeys;
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader();
    streamWrapperIndices = new IdentityHashMap<>();
    timestampAdjusterProvider = new TimestampAdjusterProvider();
    sampleStreamWrappers = new HlsSampleStreamWrapper[0];
    enabledSampleStreamWrappers = new HlsSampleStreamWrapper[0];
    manifestUrlIndicesPerWrapper = new int[0][];
    eventDispatcher.mediaPeriodCreated();
  }

  public void release() {
    playlistTracker.removeListener(this);
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.release();
    }
    callback = null;
    eventDispatcher.mediaPeriodReleased();
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
    playlistTracker.addListener(this);
    buildAndPrepareSampleStreamWrappers(positionUs);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.maybeThrowPrepareError();
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  // TODO: When the master playlist does not de-duplicate variants by URL and allows Renditions with
  // null URLs, this method must be updated to calculate stream keys that are compatible with those
  // that may already be persisted for offline.
  @Override
  public List<StreamKey> getStreamKeys(List<TrackSelection> trackSelections) {
    // See HlsMasterPlaylist.copy for interpretation of StreamKeys.
    HlsMasterPlaylist masterPlaylist = Assertions.checkNotNull(playlistTracker.getMasterPlaylist());
    boolean hasVariants = !masterPlaylist.variants.isEmpty();
    int audioWrapperOffset = hasVariants ? 1 : 0;
    // Subtitle sample stream wrappers are held last.
    int subtitleWrapperOffset = sampleStreamWrappers.length - masterPlaylist.subtitles.size();

    TrackGroupArray mainWrapperTrackGroups;
    int mainWrapperPrimaryGroupIndex;
    int[] mainWrapperVariantIndices;
    if (hasVariants) {
      HlsSampleStreamWrapper mainWrapper = sampleStreamWrappers[0];
      mainWrapperVariantIndices = manifestUrlIndicesPerWrapper[0];
      mainWrapperTrackGroups = mainWrapper.getTrackGroups();
      mainWrapperPrimaryGroupIndex = mainWrapper.getPrimaryTrackGroupIndex();
    } else {
      mainWrapperVariantIndices = new int[0];
      mainWrapperTrackGroups = TrackGroupArray.EMPTY;
      mainWrapperPrimaryGroupIndex = 0;
    }

    List<StreamKey> streamKeys = new ArrayList<>();
    boolean needsPrimaryTrackGroupSelection = false;
    boolean hasPrimaryTrackGroupSelection = false;
    for (TrackSelection trackSelection : trackSelections) {
      TrackGroup trackSelectionGroup = trackSelection.getTrackGroup();
      int mainWrapperTrackGroupIndex = mainWrapperTrackGroups.indexOf(trackSelectionGroup);
      if (mainWrapperTrackGroupIndex != C.INDEX_UNSET) {
        if (mainWrapperTrackGroupIndex == mainWrapperPrimaryGroupIndex) {
          // Primary group in main wrapper.
          hasPrimaryTrackGroupSelection = true;
          for (int i = 0; i < trackSelection.length(); i++) {
            int variantIndex = mainWrapperVariantIndices[trackSelection.getIndexInTrackGroup(i)];
            streamKeys.add(new StreamKey(HlsMasterPlaylist.GROUP_INDEX_VARIANT, variantIndex));
          }
        } else {
          // Embedded group in main wrapper.
          needsPrimaryTrackGroupSelection = true;
        }
      } else {
        // Audio or subtitle group.
        for (int i = audioWrapperOffset; i < sampleStreamWrappers.length; i++) {
          TrackGroupArray wrapperTrackGroups = sampleStreamWrappers[i].getTrackGroups();
          int selectedTrackGroupIndex = wrapperTrackGroups.indexOf(trackSelectionGroup);
          if (selectedTrackGroupIndex != C.INDEX_UNSET) {
            int groupIndexType =
                i < subtitleWrapperOffset
                    ? HlsMasterPlaylist.GROUP_INDEX_AUDIO
                    : HlsMasterPlaylist.GROUP_INDEX_SUBTITLE;
            int[] selectedWrapperUrlIndices = manifestUrlIndicesPerWrapper[i];
            for (int trackIndex = 0; trackIndex < trackSelection.length(); trackIndex++) {
              int renditionIndex =
                  selectedWrapperUrlIndices[trackSelection.getIndexInTrackGroup(trackIndex)];
              streamKeys.add(new StreamKey(groupIndexType, renditionIndex));
            }
            break;
          }
        }
      }
    }
    if (needsPrimaryTrackGroupSelection && !hasPrimaryTrackGroupSelection) {
      // A track selection includes a variant-embedded track, but no variant is added yet. We use
      // the valid variant with the lowest bitrate to reduce overhead.
      int lowestBitrateIndex = mainWrapperVariantIndices[0];
      int lowestBitrate = masterPlaylist.variants.get(mainWrapperVariantIndices[0]).format.bitrate;
      for (int i = 1; i < mainWrapperVariantIndices.length; i++) {
        int variantBitrate =
            masterPlaylist.variants.get(mainWrapperVariantIndices[i]).format.bitrate;
        if (variantBitrate < lowestBitrate) {
          lowestBitrate = variantBitrate;
          lowestBitrateIndex = mainWrapperVariantIndices[i];
        }
      }
      streamKeys.add(new StreamKey(HlsMasterPlaylist.GROUP_INDEX_VARIANT, lowestBitrateIndex));
    }
    return streamKeys;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    // Map each selection and stream onto a child period index.
    int[] streamChildIndices = new int[selections.length];
    int[] selectionChildIndices = new int[selections.length];
    for (int i = 0; i < selections.length; i++) {
      streamChildIndices[i] = streams[i] == null ? C.INDEX_UNSET
          : streamWrapperIndices.get(streams[i]);
      selectionChildIndices[i] = C.INDEX_UNSET;
      if (selections[i] != null) {
        TrackGroup trackGroup = selections[i].getTrackGroup();
        for (int j = 0; j < sampleStreamWrappers.length; j++) {
          if (sampleStreamWrappers[j].getTrackGroups().indexOf(trackGroup) != C.INDEX_UNSET) {
            selectionChildIndices[i] = j;
            break;
          }
        }
      }
    }

    boolean forceReset = false;
    streamWrapperIndices.clear();
    // Select tracks for each child, copying the resulting streams back into a new streams array.
    SampleStream[] newStreams = new SampleStream[selections.length];
    SampleStream[] childStreams = new SampleStream[selections.length];
    TrackSelection[] childSelections = new TrackSelection[selections.length];
    int newEnabledSampleStreamWrapperCount = 0;
    HlsSampleStreamWrapper[] newEnabledSampleStreamWrappers =
        new HlsSampleStreamWrapper[sampleStreamWrappers.length];
    for (int i = 0; i < sampleStreamWrappers.length; i++) {
      for (int j = 0; j < selections.length; j++) {
        childStreams[j] = streamChildIndices[j] == i ? streams[j] : null;
        childSelections[j] = selectionChildIndices[j] == i ? selections[j] : null;
      }
      HlsSampleStreamWrapper sampleStreamWrapper = sampleStreamWrappers[i];
      boolean wasReset = sampleStreamWrapper.selectTracks(childSelections, mayRetainStreamFlags,
          childStreams, streamResetFlags, positionUs, forceReset);
      boolean wrapperEnabled = false;
      for (int j = 0; j < selections.length; j++) {
        if (selectionChildIndices[j] == i) {
          // Assert that the child provided a stream for the selection.
          Assertions.checkState(childStreams[j] != null);
          newStreams[j] = childStreams[j];
          wrapperEnabled = true;
          streamWrapperIndices.put(childStreams[j], i);
        } else if (streamChildIndices[j] == i) {
          // Assert that the child cleared any previous stream.
          Assertions.checkState(childStreams[j] == null);
        }
      }
      if (wrapperEnabled) {
        newEnabledSampleStreamWrappers[newEnabledSampleStreamWrapperCount] = sampleStreamWrapper;
        if (newEnabledSampleStreamWrapperCount++ == 0) {
          // The first enabled wrapper is responsible for initializing timestamp adjusters. This
          // way, if enabled, variants are responsible. Else audio renditions. Else text renditions.
          sampleStreamWrapper.setIsTimestampMaster(true);
          if (wasReset || enabledSampleStreamWrappers.length == 0
              || sampleStreamWrapper != enabledSampleStreamWrappers[0]) {
            // The wrapper responsible for initializing the timestamp adjusters was reset or
            // changed. We need to reset the timestamp adjuster provider and all other wrappers.
            timestampAdjusterProvider.reset();
            forceReset = true;
          }
        } else {
          sampleStreamWrapper.setIsTimestampMaster(false);
        }
      }
    }
    // Copy the new streams back into the streams array.
    System.arraycopy(newStreams, 0, streams, 0, newStreams.length);
    // Update the local state.
    enabledSampleStreamWrappers = Arrays.copyOf(newEnabledSampleStreamWrappers,
        newEnabledSampleStreamWrapperCount);
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(
            enabledSampleStreamWrappers);
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (HlsSampleStreamWrapper sampleStreamWrapper : enabledSampleStreamWrappers) {
      sampleStreamWrapper.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    compositeSequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public boolean continueLoading(long positionUs) {
    if (trackGroups == null) {
      // Preparation is still going on.
      for (HlsSampleStreamWrapper wrapper : sampleStreamWrappers) {
        wrapper.continuePreparing();
      }
      return false;
    } else {
      return compositeSequenceableLoader.continueLoading(positionUs);
    }
  }

  @Override
  public long getNextLoadPositionUs() {
    return compositeSequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    if (!notifiedReadingStarted) {
      eventDispatcher.readingStarted();
      notifiedReadingStarted = true;
    }
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    return compositeSequenceableLoader.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    if (enabledSampleStreamWrappers.length > 0) {
      // We need to reset all wrappers if the one responsible for initializing timestamp adjusters
      // is reset. Else each wrapper can decide whether to reset independently.
      boolean forceReset = enabledSampleStreamWrappers[0].seekToUs(positionUs, false);
      for (int i = 1; i < enabledSampleStreamWrappers.length; i++) {
        enabledSampleStreamWrappers[i].seekToUs(positionUs, forceReset);
      }
      if (forceReset) {
        timestampAdjusterProvider.reset();
      }
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs;
  }

  // HlsSampleStreamWrapper.Callback implementation.

  @Override
  public void onPrepared() {
    if (--pendingPrepareCount > 0) {
      return;
    }

    int totalTrackGroupCount = 0;
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      totalTrackGroupCount += sampleStreamWrapper.getTrackGroups().length;
    }
    TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
    int trackGroupIndex = 0;
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      int wrapperTrackGroupCount = sampleStreamWrapper.getTrackGroups().length;
      for (int j = 0; j < wrapperTrackGroupCount; j++) {
        trackGroupArray[trackGroupIndex++] = sampleStreamWrapper.getTrackGroups().get(j);
      }
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
    callback.onPrepared(this);
  }

  @Override
  public void onPlaylistRefreshRequired(Uri url) {
    playlistTracker.refreshPlaylist(url);
  }

  @Override
  public void onContinueLoadingRequested(HlsSampleStreamWrapper sampleStreamWrapper) {
    callback.onContinueLoadingRequested(this);
  }

  // PlaylistListener implementation.

  @Override
  public void onPlaylistChanged() {
    callback.onContinueLoadingRequested(this);
  }

  @Override
  public boolean onPlaylistError(Uri url, long blacklistDurationMs) {
    boolean noBlacklistingFailure = true;
    for (HlsSampleStreamWrapper streamWrapper : sampleStreamWrappers) {
      noBlacklistingFailure &= streamWrapper.onPlaylistError(url, blacklistDurationMs);
    }
    callback.onContinueLoadingRequested(this);
    return noBlacklistingFailure;
  }

  // Internal methods.

  private void buildAndPrepareSampleStreamWrappers(long positionUs) {
    HlsMasterPlaylist masterPlaylist = Assertions.checkNotNull(playlistTracker.getMasterPlaylist());
    Map<String, DrmInitData> overridingDrmInitData =
        useSessionKeys
            ? deriveOverridingDrmInitData(masterPlaylist.sessionKeyDrmInitData)
            : Collections.emptyMap();

    boolean hasVariants = !masterPlaylist.variants.isEmpty();
    List<Rendition> audioRenditions = masterPlaylist.audios;
    List<Rendition> subtitleRenditions = masterPlaylist.subtitles;

    pendingPrepareCount = 0;
    ArrayList<HlsSampleStreamWrapper> sampleStreamWrappers = new ArrayList<>();
    ArrayList<int[]> manifestUrlIndicesPerWrapper = new ArrayList<>();

    if (hasVariants) {
      buildAndPrepareMainSampleStreamWrapper(
          masterPlaylist,
          positionUs,
          sampleStreamWrappers,
          manifestUrlIndicesPerWrapper,
          overridingDrmInitData);
    }

    // TODO: Build video stream wrappers here.

    buildAndPrepareAudioSampleStreamWrappers(
        positionUs,
        audioRenditions,
        sampleStreamWrappers,
        manifestUrlIndicesPerWrapper,
        overridingDrmInitData);

    // Subtitle stream wrappers. We can always use master playlist information to prepare these.
    for (int i = 0; i < subtitleRenditions.size(); i++) {
      Rendition subtitleRendition = subtitleRenditions.get(i);
      HlsSampleStreamWrapper sampleStreamWrapper =
          buildSampleStreamWrapper(
              C.TRACK_TYPE_TEXT,
              new Uri[] {subtitleRendition.url},
              new Format[] {subtitleRendition.format},
              null,
              Collections.emptyList(),
              overridingDrmInitData,
              positionUs);
      manifestUrlIndicesPerWrapper.add(new int[] {i});
      sampleStreamWrappers.add(sampleStreamWrapper);
      sampleStreamWrapper.prepareWithMasterPlaylistInfo(
          new TrackGroupArray(new TrackGroup(subtitleRendition.format)), 0, TrackGroupArray.EMPTY);
    }

    this.sampleStreamWrappers = sampleStreamWrappers.toArray(new HlsSampleStreamWrapper[0]);
    this.manifestUrlIndicesPerWrapper = manifestUrlIndicesPerWrapper.toArray(new int[0][]);
    pendingPrepareCount = this.sampleStreamWrappers.length;
    // Set timestamp master and trigger preparation (if not already prepared)
    this.sampleStreamWrappers[0].setIsTimestampMaster(true);
    for (HlsSampleStreamWrapper sampleStreamWrapper : this.sampleStreamWrappers) {
      sampleStreamWrapper.continuePreparing();
    }
    // All wrappers are enabled during preparation.
    enabledSampleStreamWrappers = this.sampleStreamWrappers;
  }

  /**
   * This method creates and starts preparation of the main {@link HlsSampleStreamWrapper}.
   *
   * <p>The main sample stream wrapper is the first element of {@link #sampleStreamWrappers}. It
   * provides {@link SampleStream}s for the variant urls in the master playlist. It may be adaptive
   * and may contain multiple muxed tracks.
   *
   * <p>If chunkless preparation is allowed, the media period will try preparation without segment
   * downloads. This is only possible if variants contain the CODECS attribute. If not, traditional
   * preparation with segment downloads will take place. The following points apply to chunkless
   * preparation:
   *
   * <ul>
   *   <li>A muxed audio track will be exposed if the codecs list contain an audio entry and the
   *       master playlist either contains an EXT-X-MEDIA tag without the URI attribute or does not
   *       contain any EXT-X-MEDIA tag.
   *   <li>Closed captions will only be exposed if they are declared by the master playlist.
   *   <li>An ID3 track is exposed preemptively, in case the segments contain an ID3 track.
   * </ul>
   *
   * @param masterPlaylist The HLS master playlist.
   * @param positionUs If preparation requires any chunk downloads, the position in microseconds at
   *     which downloading should start. Ignored otherwise.
   * @param sampleStreamWrappers List to which the built main sample stream wrapper should be added.
   * @param manifestUrlIndicesPerWrapper List to which the selected variant indices should be added.
   * @param overridingDrmInitData Overriding {@link DrmInitData}, keyed by protection scheme type
   *     (i.e. {@link DrmInitData#schemeType}).
   */
  private void buildAndPrepareMainSampleStreamWrapper(
      HlsMasterPlaylist masterPlaylist,
      long positionUs,
      List<HlsSampleStreamWrapper> sampleStreamWrappers,
      List<int[]> manifestUrlIndicesPerWrapper,
      Map<String, DrmInitData> overridingDrmInitData) {
    int[] variantTypes = new int[masterPlaylist.variants.size()];
    int videoVariantCount = 0;
    int audioVariantCount = 0;
    for (int i = 0; i < masterPlaylist.variants.size(); i++) {
      Variant variant = masterPlaylist.variants.get(i);
      Format format = variant.format;
      if (format.height > 0 || Util.getCodecsOfType(format.codecs, C.TRACK_TYPE_VIDEO) != null) {
        variantTypes[i] = C.TRACK_TYPE_VIDEO;
        videoVariantCount++;
      } else if (Util.getCodecsOfType(format.codecs, C.TRACK_TYPE_AUDIO) != null) {
        variantTypes[i] = C.TRACK_TYPE_AUDIO;
        audioVariantCount++;
      } else {
        variantTypes[i] = C.TRACK_TYPE_UNKNOWN;
      }
    }
    boolean useVideoVariantsOnly = false;
    boolean useNonAudioVariantsOnly = false;
    int selectedVariantsCount = variantTypes.length;
    if (videoVariantCount > 0) {
      // We've identified some variants as definitely containing video. Assume variants within the
      // master playlist are marked consistently, and hence that we have the full set. Filter out
      // any other variants, which are likely to be audio only.
      useVideoVariantsOnly = true;
      selectedVariantsCount = videoVariantCount;
    } else if (audioVariantCount < variantTypes.length) {
      // We've identified some variants, but not all, as being audio only. Filter them out to leave
      // the remaining variants, which are likely to contain video.
      useNonAudioVariantsOnly = true;
      selectedVariantsCount = variantTypes.length - audioVariantCount;
    }
    Uri[] selectedPlaylistUrls = new Uri[selectedVariantsCount];
    Format[] selectedPlaylistFormats = new Format[selectedVariantsCount];
    int[] selectedVariantIndices = new int[selectedVariantsCount];
    int outIndex = 0;
    for (int i = 0; i < masterPlaylist.variants.size(); i++) {
      if ((!useVideoVariantsOnly || variantTypes[i] == C.TRACK_TYPE_VIDEO)
          && (!useNonAudioVariantsOnly || variantTypes[i] != C.TRACK_TYPE_AUDIO)) {
        Variant variant = masterPlaylist.variants.get(i);
        selectedPlaylistUrls[outIndex] = variant.url;
        selectedPlaylistFormats[outIndex] = variant.format;
        selectedVariantIndices[outIndex++] = i;
      }
    }
    String codecs = selectedPlaylistFormats[0].codecs;
    HlsSampleStreamWrapper sampleStreamWrapper =
        buildSampleStreamWrapper(
            C.TRACK_TYPE_DEFAULT,
            selectedPlaylistUrls,
            selectedPlaylistFormats,
            masterPlaylist.muxedAudioFormat,
            masterPlaylist.muxedCaptionFormats,
            overridingDrmInitData,
            positionUs);
    sampleStreamWrappers.add(sampleStreamWrapper);
    manifestUrlIndicesPerWrapper.add(selectedVariantIndices);
    if (allowChunklessPreparation && codecs != null) {
      boolean variantsContainVideoCodecs = Util.getCodecsOfType(codecs, C.TRACK_TYPE_VIDEO) != null;
      boolean variantsContainAudioCodecs = Util.getCodecsOfType(codecs, C.TRACK_TYPE_AUDIO) != null;
      List<TrackGroup> muxedTrackGroups = new ArrayList<>();
      if (variantsContainVideoCodecs) {
        Format[] videoFormats = new Format[selectedVariantsCount];
        for (int i = 0; i < videoFormats.length; i++) {
          videoFormats[i] = deriveVideoFormat(selectedPlaylistFormats[i]);
        }
        muxedTrackGroups.add(new TrackGroup(videoFormats));

        if (variantsContainAudioCodecs
            && (masterPlaylist.muxedAudioFormat != null || masterPlaylist.audios.isEmpty())) {
          muxedTrackGroups.add(
              new TrackGroup(
                  deriveAudioFormat(
                      selectedPlaylistFormats[0],
                      masterPlaylist.muxedAudioFormat,
                      /* isPrimaryTrackInVariant= */ false)));
        }
        List<Format> ccFormats = masterPlaylist.muxedCaptionFormats;
        if (ccFormats != null) {
          for (int i = 0; i < ccFormats.size(); i++) {
            muxedTrackGroups.add(new TrackGroup(ccFormats.get(i)));
          }
        }
      } else if (variantsContainAudioCodecs) {
        // Variants only contain audio.
        Format[] audioFormats = new Format[selectedVariantsCount];
        for (int i = 0; i < audioFormats.length; i++) {
          audioFormats[i] =
              deriveAudioFormat(
                  /* variantFormat= */ selectedPlaylistFormats[i],
                  masterPlaylist.muxedAudioFormat,
                  /* isPrimaryTrackInVariant= */ true);
        }
        muxedTrackGroups.add(new TrackGroup(audioFormats));
      } else {
        // Variants contain codecs but no video or audio entries could be identified.
        throw new IllegalArgumentException("Unexpected codecs attribute: " + codecs);
      }

      TrackGroup id3TrackGroup =
          new TrackGroup(
              Format.createSampleFormat(
                  /* id= */ "ID3",
                  MimeTypes.APPLICATION_ID3,
                  /* codecs= */ null,
                  /* bitrate= */ Format.NO_VALUE,
                  /* drmInitData= */ null));
      muxedTrackGroups.add(id3TrackGroup);

      sampleStreamWrapper.prepareWithMasterPlaylistInfo(
          new TrackGroupArray(muxedTrackGroups.toArray(new TrackGroup[0])),
          0,
          new TrackGroupArray(id3TrackGroup));
    }
  }

  private void buildAndPrepareAudioSampleStreamWrappers(
      long positionUs,
      List<Rendition> audioRenditions,
      List<HlsSampleStreamWrapper> sampleStreamWrappers,
      List<int[]> manifestUrlsIndicesPerWrapper,
      Map<String, DrmInitData> overridingDrmInitData) {
    ArrayList<Uri> scratchPlaylistUrls =
        new ArrayList<>(/* initialCapacity= */ audioRenditions.size());
    ArrayList<Format> scratchPlaylistFormats =
        new ArrayList<>(/* initialCapacity= */ audioRenditions.size());
    ArrayList<Integer> scratchIndicesList =
        new ArrayList<>(/* initialCapacity= */ audioRenditions.size());
    HashSet<String> alreadyGroupedNames = new HashSet<>();
    for (int renditionByNameIndex = 0;
        renditionByNameIndex < audioRenditions.size();
        renditionByNameIndex++) {
      String name = audioRenditions.get(renditionByNameIndex).name;
      if (!alreadyGroupedNames.add(name)) {
        // This name already has a corresponding group.
        continue;
      }

      boolean renditionsHaveCodecs = true;
      scratchPlaylistUrls.clear();
      scratchPlaylistFormats.clear();
      scratchIndicesList.clear();
      // Group all renditions with matching name.
      for (int renditionIndex = 0; renditionIndex < audioRenditions.size(); renditionIndex++) {
        if (Util.areEqual(name, audioRenditions.get(renditionIndex).name)) {
          Rendition rendition = audioRenditions.get(renditionIndex);
          scratchIndicesList.add(renditionIndex);
          scratchPlaylistUrls.add(rendition.url);
          scratchPlaylistFormats.add(rendition.format);
          renditionsHaveCodecs &= rendition.format.codecs != null;
        }
      }

      HlsSampleStreamWrapper sampleStreamWrapper =
          buildSampleStreamWrapper(
              C.TRACK_TYPE_AUDIO,
              scratchPlaylistUrls.toArray(new Uri[0]),
              scratchPlaylistFormats.toArray(new Format[0]),
              /* muxedAudioFormat= */ null,
              /* muxedCaptionFormats= */ Collections.emptyList(),
              overridingDrmInitData,
              positionUs);
      manifestUrlsIndicesPerWrapper.add(Util.toArray(scratchIndicesList));
      sampleStreamWrappers.add(sampleStreamWrapper);

      if (allowChunklessPreparation && renditionsHaveCodecs) {
        Format[] renditionFormats = scratchPlaylistFormats.toArray(new Format[0]);
        sampleStreamWrapper.prepareWithMasterPlaylistInfo(
            new TrackGroupArray(new TrackGroup(renditionFormats)), 0, TrackGroupArray.EMPTY);
      }
    }
  }

  private HlsSampleStreamWrapper buildSampleStreamWrapper(
      int trackType,
      Uri[] playlistUrls,
      Format[] playlistFormats,
      Format muxedAudioFormat,
      List<Format> muxedCaptionFormats,
      Map<String, DrmInitData> overridingDrmInitData,
      long positionUs) {
    HlsChunkSource defaultChunkSource =
        new HlsChunkSource(
            extractorFactory,
            playlistTracker,
            playlistUrls,
            playlistFormats,
            dataSourceFactory,
            mediaTransferListener,
            timestampAdjusterProvider,
            muxedCaptionFormats);
    return new HlsSampleStreamWrapper(
        trackType,
        /* callback= */ this,
        defaultChunkSource,
        overridingDrmInitData,
        allocator,
        positionUs,
        muxedAudioFormat,
        loadErrorHandlingPolicy,
        eventDispatcher,
        metadataType);
  }

  private static Map<String, DrmInitData> deriveOverridingDrmInitData(
      List<DrmInitData> sessionKeyDrmInitData) {
    ArrayList<DrmInitData> mutableSessionKeyDrmInitData = new ArrayList<>(sessionKeyDrmInitData);
    HashMap<String, DrmInitData> drmInitDataBySchemeType = new HashMap<>();
    for (int i = 0; i < mutableSessionKeyDrmInitData.size(); i++) {
      DrmInitData drmInitData = sessionKeyDrmInitData.get(i);
      String scheme = drmInitData.schemeType;
      // Merge any subsequent drmInitData instances that have the same scheme type. This is valid
      // due to the assumptions documented on HlsMediaSource.Builder.setUseSessionKeys, and is
      // necessary to get data for different CDNs (e.g. Widevine and PlayReady) into a single
      // drmInitData.
      int j = i + 1;
      while (j < mutableSessionKeyDrmInitData.size()) {
        DrmInitData nextDrmInitData = mutableSessionKeyDrmInitData.get(j);
        if (TextUtils.equals(nextDrmInitData.schemeType, scheme)) {
          drmInitData = drmInitData.merge(nextDrmInitData);
          mutableSessionKeyDrmInitData.remove(j);
        } else {
          j++;
        }
      }
      drmInitDataBySchemeType.put(scheme, drmInitData);
    }
    return drmInitDataBySchemeType;
  }

  private static Format deriveVideoFormat(Format variantFormat) {
    String codecs = Util.getCodecsOfType(variantFormat.codecs, C.TRACK_TYPE_VIDEO);
    String sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    return Format.createVideoContainerFormat(
        variantFormat.id,
        variantFormat.label,
        variantFormat.containerMimeType,
        sampleMimeType,
        codecs,
        variantFormat.metadata,
        variantFormat.bitrate,
        variantFormat.width,
        variantFormat.height,
        variantFormat.frameRate,
        /* initializationData= */ null,
        variantFormat.selectionFlags,
        variantFormat.roleFlags);
  }

  private static Format deriveAudioFormat(
      Format variantFormat, Format mediaTagFormat, boolean isPrimaryTrackInVariant) {
    String codecs;
    Metadata metadata;
    int channelCount = Format.NO_VALUE;
    int selectionFlags = 0;
    int roleFlags = 0;
    String language = null;
    String label = null;
    if (mediaTagFormat != null) {
      codecs = mediaTagFormat.codecs;
      metadata = mediaTagFormat.metadata;
      channelCount = mediaTagFormat.channelCount;
      selectionFlags = mediaTagFormat.selectionFlags;
      roleFlags = mediaTagFormat.roleFlags;
      language = mediaTagFormat.language;
      label = mediaTagFormat.label;
    } else {
      codecs = Util.getCodecsOfType(variantFormat.codecs, C.TRACK_TYPE_AUDIO);
      metadata = variantFormat.metadata;
      if (isPrimaryTrackInVariant) {
        channelCount = variantFormat.channelCount;
        selectionFlags = variantFormat.selectionFlags;
        roleFlags = variantFormat.roleFlags;
        language = variantFormat.language;
        label = variantFormat.label;
      }
    }
    String sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    int bitrate = isPrimaryTrackInVariant ? variantFormat.bitrate : Format.NO_VALUE;
    return Format.createAudioContainerFormat(
        variantFormat.id,
        label,
        variantFormat.containerMimeType,
        sampleMimeType,
        codecs,
        metadata,
        bitrate,
        channelCount,
        /* sampleRate= */ Format.NO_VALUE,
        /* initializationData= */ null,
        selectionFlags,
        roleFlags,
        language);
  }

}
