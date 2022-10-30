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
package com.google.android.exoplayer2.source.dash.manifest;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/** Unit tests for {@link DashManifestParser}. */
@RunWith(AndroidJUnit4.class)
public class DashManifestParserTest {

  private static final String SAMPLE_MPD = "sample_mpd";
  private static final String SAMPLE_MPD_UNKNOWN_MIME_TYPE = "sample_mpd_unknown_mime_type";
  private static final String SAMPLE_MPD_SEGMENT_TEMPLATE = "sample_mpd_segment_template";
  private static final String SAMPLE_MPD_EVENT_STREAM = "sample_mpd_event_stream";
  private static final String SAMPLE_MPD_LABELS = "sample_mpd_labels";

  private static final String NEXT_TAG_NAME = "Next";
  private static final String NEXT_TAG = "<" + NEXT_TAG_NAME + "/>";

  /** Simple test to ensure the sample manifests parse without any exceptions being thrown. */
  @Test
  public void parseMediaPresentationDescription() throws IOException {
    DashManifestParser parser = new DashManifestParser();
    parser.parse(
        Uri.parse("https://example.com/test.mpd"),
        TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), SAMPLE_MPD));
    parser.parse(
        Uri.parse("https://example.com/test.mpd"),
        TestUtil.getInputStream(
            ApplicationProvider.getApplicationContext(), SAMPLE_MPD_UNKNOWN_MIME_TYPE));
  }

  @Test
  public void parseMediaPresentationDescription_segmentTemplate() throws IOException {
    DashManifestParser parser = new DashManifestParser();
    DashManifest mpd =
        parser.parse(
            Uri.parse("https://example.com/test.mpd"),
            TestUtil.getInputStream(
                ApplicationProvider.getApplicationContext(), SAMPLE_MPD_SEGMENT_TEMPLATE));

    assertThat(mpd.getPeriodCount()).isEqualTo(1);

    Period period = mpd.getPeriod(0);
    assertThat(period).isNotNull();
    assertThat(period.adaptationSets).hasSize(2);

    for (AdaptationSet adaptationSet : period.adaptationSets) {
      assertThat(adaptationSet).isNotNull();
      for (Representation representation : adaptationSet.representations) {
        if (representation instanceof Representation.MultiSegmentRepresentation) {
          Representation.MultiSegmentRepresentation multiSegmentRepresentation =
              (Representation.MultiSegmentRepresentation) representation;
          long firstSegmentIndex = multiSegmentRepresentation.getFirstSegmentNum();
          RangedUri uri = multiSegmentRepresentation.getSegmentUrl(firstSegmentIndex);
          assertThat(
                  uri.resolveUriString(representation.baseUrl)
                      .contains("redirector.googlevideo.com"))
              .isTrue();
        }
      }
    }
  }

  @Test
  public void parseMediaPresentationDescription_eventStream() throws IOException {
    DashManifestParser parser = new DashManifestParser();
    DashManifest mpd =
        parser.parse(
            Uri.parse("https://example.com/test.mpd"),
            TestUtil.getInputStream(
                ApplicationProvider.getApplicationContext(), SAMPLE_MPD_EVENT_STREAM));

    Period period = mpd.getPeriod(0);
    assertThat(period.eventStreams).hasSize(3);

    // assert text-only event stream
    EventStream eventStream1 = period.eventStreams.get(0);
    assertThat(eventStream1.events.length).isEqualTo(1);
    EventMessage expectedEvent1 =
        new EventMessage(
            "urn:uuid:XYZY",
            "call",
            10000,
            0,
            "+ 1 800 10101010".getBytes(Charset.forName(C.UTF8_NAME)));
    assertThat(eventStream1.events[0]).isEqualTo(expectedEvent1);
    assertThat(eventStream1.presentationTimesUs[0]).isEqualTo(0);

    // assert CData-structured event stream
    EventStream eventStream2 = period.eventStreams.get(1);
    assertThat(eventStream2.events.length).isEqualTo(1);
    EventMessage expectedEvent2 =
        new EventMessage(
            "urn:dvb:iptv:cpm:2014",
            "",
            1500000,
            1,
            Util.getUtf8Bytes(
                "<![CDATA[<BroadcastEvent>\n"
                    + "      <Program crid=\"crid://broadcaster.example.com/ABCDEF\"/>\n"
                    + "      <InstanceDescription>\n"
                    + "      <Title xml:lang=\"en\">The title</Title>\n"
                    + "      <Synopsis xml:lang=\"en\" length=\"medium\">"
                    + "The description</Synopsis>\n"
                    + "      <ParentalGuidance>\n"
                    + "      <mpeg7:ParentalRating href=\"urn:dvb:iptv:rating:2014:15\"/>\n"
                    + "      <mpeg7:Region>GB</mpeg7:Region>\n"
                    + "      </ParentalGuidance>\n"
                    + "      </InstanceDescription>\n"
                    + "      </BroadcastEvent>]]>"));

    assertThat(eventStream2.events[0]).isEqualTo(expectedEvent2);
    assertThat(eventStream2.presentationTimesUs[0]).isEqualTo(300000000);

    // assert xml-structured event stream
    EventStream eventStream3 = period.eventStreams.get(2);
    assertThat(eventStream3.events.length).isEqualTo(1);
    EventMessage expectedEvent3 =
        new EventMessage(
            "urn:scte:scte35:2014:xml+bin",
            "",
            1000000,
            2,
            Util.getUtf8Bytes(
                "<scte35:Signal>\n"
                    + "         <scte35:Binary>\n"
                    + "         /DAIAAAAAAAAAAAQAAZ/I0VniQAQAgBDVUVJQAAAAH+cAAAAAA==\n"
                    + "         </scte35:Binary>\n"
                    + "       </scte35:Signal>"));
    assertThat(eventStream3.events[0]).isEqualTo(expectedEvent3);
    assertThat(eventStream3.presentationTimesUs[0]).isEqualTo(1000000000);
  }

  @Test
  public void parseMediaPresentationDescription_programInformation() throws IOException {
    DashManifestParser parser = new DashManifestParser();
    DashManifest mpd =
        parser.parse(
            Uri.parse("Https://example.com/test.mpd"),
            TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), SAMPLE_MPD));
    ProgramInformation expectedProgramInformation =
        new ProgramInformation(
            "MediaTitle", "MediaSource", "MediaCopyright", "www.example.com", "enUs");
    assertThat(mpd.programInformation).isEqualTo(expectedProgramInformation);
  }

  @Test
  public void parseMediaPresentationDescription_labels() throws IOException {
    DashManifestParser parser = new DashManifestParser();
    DashManifest manifest =
        parser.parse(
            Uri.parse("https://example.com/test.mpd"),
            TestUtil.getInputStream(
                ApplicationProvider.getApplicationContext(), SAMPLE_MPD_LABELS));

    List<AdaptationSet> adaptationSets = manifest.getPeriod(0).adaptationSets;

    assertThat(adaptationSets.get(0).representations.get(0).format.label).isEqualTo("audio label");
    assertThat(adaptationSets.get(1).representations.get(0).format.label).isEqualTo("video label");
  }

  @Test
  public void parseLabel() throws Exception {
    DashManifestParser parser = new DashManifestParser();
    XmlPullParser xpp = XmlPullParserFactory.newInstance().newPullParser();
    xpp.setInput(new StringReader("<Label>test label</Label>" + NEXT_TAG));
    xpp.next();

    String label = parser.parseLabel(xpp);
    assertThat(label).isEqualTo("test label");
    assertNextTag(xpp);
  }

  @Test
  public void parseLabel_noText() throws Exception {
    DashManifestParser parser = new DashManifestParser();
    XmlPullParser xpp = XmlPullParserFactory.newInstance().newPullParser();
    xpp.setInput(new StringReader("<Label/>" + NEXT_TAG));
    xpp.next();

    String label = parser.parseLabel(xpp);
    assertThat(label).isEqualTo("");
    assertNextTag(xpp);
  }

  @Test
  public void parseCea608AccessibilityChannel() {
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC1=eng")))
        .isEqualTo(1);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC2=eng")))
        .isEqualTo(2);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC3=eng")))
        .isEqualTo(3);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC4=eng")))
        .isEqualTo(4);

    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors(null)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC0=eng")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC5=eng")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("Wrong format")))
        .isEqualTo(Format.NO_VALUE);
  }

  @Test
  public void parseCea708AccessibilityChannel() {
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("1=lang:eng")))
        .isEqualTo(1);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("2=lang:eng")))
        .isEqualTo(2);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("3=lang:eng")))
        .isEqualTo(3);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("62=lang:eng")))
        .isEqualTo(62);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("63=lang:eng")))
        .isEqualTo(63);

    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors(null)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("0=lang:eng")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("64=lang:eng")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("Wrong format")))
        .isEqualTo(Format.NO_VALUE);
  }

  private static List<Descriptor> buildCea608AccessibilityDescriptors(String value) {
    return Collections.singletonList(new Descriptor("urn:scte:dash:cc:cea-608:2015", value, null));
  }

  private static List<Descriptor> buildCea708AccessibilityDescriptors(String value) {
    return Collections.singletonList(new Descriptor("urn:scte:dash:cc:cea-708:2015", value, null));
  }

  private static void assertNextTag(XmlPullParser xpp) throws Exception {
    xpp.next();
    assertThat(xpp.getEventType()).isEqualTo(XmlPullParser.START_TAG);
    assertThat(xpp.getName()).isEqualTo(NEXT_TAG_NAME);
  }
}
