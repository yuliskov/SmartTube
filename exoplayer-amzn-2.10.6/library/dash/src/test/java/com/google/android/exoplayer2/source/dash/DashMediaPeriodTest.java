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
package com.google.android.exoplayer2.source.dash;

import static org.mockito.Mockito.mock;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerEmsgCallback;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Descriptor;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SingleSegmentBase;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;
import com.google.android.exoplayer2.testutil.MediaPeriodAsserts;
import com.google.android.exoplayer2.testutil.MediaPeriodAsserts.FilterableManifestMediaPeriodFactory;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit tests for {@link DashMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public final class DashMediaPeriodTest {

  @Test
  public void getSteamKeys_isCompatibleWithDashManifestFilter() {
    // Test manifest which covers various edge cases:
    //  - Multiple periods.
    //  - Single and multiple representations per adaptation set.
    //  - Switch descriptors combining multiple adaptations sets.
    //  - Embedded track groups.
    // All cases are deliberately combined in one test to catch potential indexing problems which
    // only occur in combination.
    DashManifest testManifest =
        createDashManifest(
            createPeriod(
                createAdaptationSet(
                    /* id= */ 0,
                    /* trackType= */ C.TRACK_TYPE_VIDEO,
                    /* descriptor= */ null,
                    createVideoRepresentation(/* bitrate= */ 1000000))),
            createPeriod(
                createAdaptationSet(
                    /* id= */ 100,
                    /* trackType= */ C.TRACK_TYPE_VIDEO,
                    /* descriptor= */ createSwitchDescriptor(/* ids= */ 103, 104),
                    createVideoRepresentationWithInbandEventStream(/* bitrate= */ 200000),
                    createVideoRepresentationWithInbandEventStream(/* bitrate= */ 400000),
                    createVideoRepresentationWithInbandEventStream(/* bitrate= */ 600000)),
                createAdaptationSet(
                    /* id= */ 101,
                    /* trackType= */ C.TRACK_TYPE_AUDIO,
                    /* descriptor= */ createSwitchDescriptor(/* ids= */ 102),
                    createAudioRepresentation(/* bitrate= */ 48000),
                    createAudioRepresentation(/* bitrate= */ 96000)),
                createAdaptationSet(
                    /* id= */ 102,
                    /* trackType= */ C.TRACK_TYPE_AUDIO,
                    /* descriptor= */ createSwitchDescriptor(/* ids= */ 101),
                    createAudioRepresentation(/* bitrate= */ 256000)),
                createAdaptationSet(
                    /* id= */ 103,
                    /* trackType= */ C.TRACK_TYPE_VIDEO,
                    /* descriptor= */ createSwitchDescriptor(/* ids= */ 100, 104),
                    createVideoRepresentationWithInbandEventStream(/* bitrate= */ 800000),
                    createVideoRepresentationWithInbandEventStream(/* bitrate= */ 1000000)),
                createAdaptationSet(
                    /* id= */ 104,
                    /* trackType= */ C.TRACK_TYPE_VIDEO,
                    /* descriptor= */ createSwitchDescriptor(/* ids= */ 100, 103),
                    createVideoRepresentationWithInbandEventStream(/* bitrate= */ 2000000)),
                createAdaptationSet(
                    /* id= */ 105,
                    /* trackType= */ C.TRACK_TYPE_TEXT,
                    /* descriptor= */ null,
                    createTextRepresentation(/* language= */ "eng")),
                createAdaptationSet(
                    /* id= */ 105,
                    /* trackType= */ C.TRACK_TYPE_TEXT,
                    /* descriptor= */ null,
                    createTextRepresentation(/* language= */ "ger"))));
    FilterableManifestMediaPeriodFactory<DashManifest> mediaPeriodFactory =
        (manifest, periodIndex) ->
            new DashMediaPeriod(
                /* id= */ periodIndex,
                manifest,
                periodIndex,
                mock(DashChunkSource.Factory.class),
                mock(TransferListener.class),
                mock(LoadErrorHandlingPolicy.class),
                new EventDispatcher()
                    .withParameters(
                        /* windowIndex= */ 0,
                        /* mediaPeriodId= */ new MediaPeriodId(/* periodUid= */ new Object()),
                        /* mediaTimeOffsetMs= */ 0),
                /* elapsedRealtimeOffsetMs= */ 0,
                mock(LoaderErrorThrower.class),
                mock(Allocator.class),
                mock(CompositeSequenceableLoaderFactory.class),
                mock(PlayerEmsgCallback.class));

    // Ignore embedded metadata as we don't want to select primary group just to get embedded track.
    MediaPeriodAsserts.assertGetStreamKeysAndManifestFilterIntegration(
        mediaPeriodFactory,
        testManifest,
        /* periodIndex= */ 1,
        /* ignoredMimeType= */ "application/x-emsg");
  }

  private static DashManifest createDashManifest(Period... periods) {
    return new DashManifest(
        /* availabilityStartTimeMs= */ 0,
        /* durationMs= */ 5000,
        /* minBufferTimeMs= */ 1,
        /* dynamic= */ false,
        /* minUpdatePeriodMs= */ 2,
        /* timeShiftBufferDepthMs= */ 3,
        /* suggestedPresentationDelayMs= */ 4,
        /* publishTimeMs= */ 12345,
        /* programInformation= */ null,
        new UtcTimingElement("", ""),
        Uri.EMPTY,
        Arrays.asList(periods));
  }

  private static Period createPeriod(AdaptationSet... adaptationSets) {
    return new Period(/* id= */ null, /* startMs= */ 0, Arrays.asList(adaptationSets));
  }

  private static AdaptationSet createAdaptationSet(
      int id, int trackType, @Nullable Descriptor descriptor, Representation... representations) {
    return new AdaptationSet(
        id,
        trackType,
        Arrays.asList(representations),
        /* accessibilityDescriptors= */ Collections.emptyList(),
        descriptor == null ? Collections.emptyList() : Collections.singletonList(descriptor));
  }

  private static Representation createVideoRepresentation(int bitrate) {
    return Representation.newInstance(
        /* revisionId= */ 0,
        createVideoFormat(bitrate),
        /* baseUrl= */ "",
        new SingleSegmentBase());
  }

  private static Representation createVideoRepresentationWithInbandEventStream(int bitrate) {
    return Representation.newInstance(
        /* revisionId= */ 0,
        createVideoFormat(bitrate),
        /* baseUrl= */ "",
        new SingleSegmentBase(),
        Collections.singletonList(getInbandEventDescriptor()));
  }

  private static Format createVideoFormat(int bitrate) {
    return Format.createContainerFormat(
        /* id= */ null,
        /* label= */ null,
        MimeTypes.VIDEO_MP4,
        MimeTypes.VIDEO_H264,
        /* codecs= */ null,
        bitrate,
        /* selectionFlags= */ 0,
        /* roleFlags= */ 0,
        /* language= */ null);
  }

  private static Representation createAudioRepresentation(int bitrate) {
    return Representation.newInstance(
        /* revisionId= */ 0,
        Format.createContainerFormat(
            /* id= */ null,
            /* label= */ null,
            MimeTypes.AUDIO_MP4,
            MimeTypes.AUDIO_AAC,
            /* codecs= */ null,
            bitrate,
            /* selectionFlags= */ 0,
            /* roleFlags= */ 0,
            /* language= */ null),
        /* baseUrl= */ "",
        new SingleSegmentBase());
  }

  private static Representation createTextRepresentation(String language) {
    return Representation.newInstance(
        /* revisionId= */ 0,
        Format.createContainerFormat(
            /* id= */ null,
            /* label= */ null,
            MimeTypes.APPLICATION_MP4,
            MimeTypes.TEXT_VTT,
            /* codecs= */ null,
            /* bitrate= */ Format.NO_VALUE,
            /* selectionFlags= */ 0,
            /* roleFlags= */ 0,
            language),
        /* baseUrl= */ "",
        new SingleSegmentBase());
  }

  private static Descriptor createSwitchDescriptor(int... ids) {
    StringBuilder idString = new StringBuilder();
    idString.append(ids[0]);
    for (int i = 1; i < ids.length; i++) {
      idString.append(",").append(ids[i]);
    }
    return new Descriptor(
        /* schemeIdUri= */ "urn:mpeg:dash:adaptation-set-switching:2016",
        /* value= */ idString.toString(),
        /* id= */ null);
  }

  private static Descriptor getInbandEventDescriptor() {
    return new Descriptor(
        /* schemeIdUri= */ "inBandSchemeIdUri", /* value= */ "inBandValue", /* id= */ "inBandId");
  }
}
