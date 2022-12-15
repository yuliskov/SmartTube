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
package com.google.android.exoplayer2.source.smoothstreaming;

import static com.google.android.exoplayer2.source.smoothstreaming.SsTestUtils.createSsManifest;
import static com.google.android.exoplayer2.source.smoothstreaming.SsTestUtils.createStreamElement;
import static org.mockito.Mockito.mock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.testutil.MediaPeriodAsserts;
import com.google.android.exoplayer2.testutil.MediaPeriodAsserts.FilterableManifestMediaPeriodFactory;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit tests for {@link SsMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class SsMediaPeriodTest {

  @Test
  public void getSteamKeys_isCompatibleWithSsManifestFilter() {
    SsManifest testManifest =
        createSsManifest(
            createStreamElement(
                /* name= */ "video",
                C.TRACK_TYPE_VIDEO,
                createVideoFormat(/* bitrate= */ 200000),
                createVideoFormat(/* bitrate= */ 400000),
                createVideoFormat(/* bitrate= */ 800000)),
            createStreamElement(
                /* name= */ "audio",
                C.TRACK_TYPE_AUDIO,
                createAudioFormat(/* bitrate= */ 48000),
                createAudioFormat(/* bitrate= */ 96000)),
            createStreamElement(
                /* name= */ "text", C.TRACK_TYPE_TEXT, createTextFormat(/* language= */ "eng")));
    FilterableManifestMediaPeriodFactory<SsManifest> mediaPeriodFactory =
        (manifest, periodIndex) ->
            new SsMediaPeriod(
                manifest,
                mock(SsChunkSource.Factory.class),
                mock(TransferListener.class),
                mock(CompositeSequenceableLoaderFactory.class),
                mock(LoadErrorHandlingPolicy.class),
                new EventDispatcher()
                    .withParameters(
                        /* windowIndex= */ 0,
                        /* mediaPeriodId= */ new MediaPeriodId(/* periodUid= */ new Object()),
                        /* mediaTimeOffsetMs= */ 0),
                mock(LoaderErrorThrower.class),
                mock(Allocator.class));

    MediaPeriodAsserts.assertGetStreamKeysAndManifestFilterIntegration(
        mediaPeriodFactory, testManifest);
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

  private static Format createAudioFormat(int bitrate) {
    return Format.createContainerFormat(
        /* id= */ null,
        /* label= */ null,
        MimeTypes.AUDIO_MP4,
        MimeTypes.AUDIO_AAC,
        /* codecs= */ null,
        bitrate,
        /* selectionFlags= */ 0,
        /* roleFlags= */ 0,
        /* language= */ null);
  }

  private static Format createTextFormat(String language) {
    return Format.createContainerFormat(
        /* id= */ null,
        /* label= */ null,
        MimeTypes.APPLICATION_MP4,
        MimeTypes.TEXT_VTT,
        /* codecs= */ null,
        /* bitrate= */ Format.NO_VALUE,
        /* selectionFlags= */ 0,
        /* roleFlags= */ 0,
        language);
  }
}
