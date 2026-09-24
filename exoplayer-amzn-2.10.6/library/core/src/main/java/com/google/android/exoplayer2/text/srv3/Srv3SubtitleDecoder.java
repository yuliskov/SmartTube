package com.google.android.exoplayer2.text.srv3;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.XmlPullParserUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link SimpleSubtitleDecoder} for YouTube's SRV3 format.
 */
public final class Srv3SubtitleDecoder extends SimpleSubtitleDecoder {

  private static final String TAG = "Srv3SubtitleDecoder";

  private final XmlPullParserFactory xmlParserFactory;

  public Srv3SubtitleDecoder() {
    super("Srv3SubtitleDecoder");
    try {
      xmlParserFactory = XmlPullParserFactory.newInstance();
      xmlParserFactory.setNamespaceAware(true);
    } catch (XmlPullParserException e) {
      throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
    }
  }

  @Override
  protected Subtitle decode(byte[] data, int size, boolean reset)
      throws SubtitleDecoderException {
    try {
      XmlPullParser xmlParser = xmlParserFactory.newPullParser();
      xmlParser.setInput(new ByteArrayInputStream(data, 0, size), null);

      Map<String, Pen> pens = new HashMap<>();
      Map<String, Wp> wps = new HashMap<>();
      List<Srv3Subtitle.Srv3Cue> cues = new ArrayList<>();

      int eventType = xmlParser.getEventType();
      while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
          String tagName = xmlParser.getName();
          if ("pen".equals(tagName)) {
            parsePen(xmlParser, pens);
          } else if ("wp".equals(tagName)) {
            parseWp(xmlParser, wps);
          } else if ("p".equals(tagName)) {
            parseParagraph(xmlParser, pens, wps, cues);
          }
        }
        eventType = xmlParser.next();
      }

      return new Srv3Subtitle(cues);
    } catch (XmlPullParserException | IOException e) {
      throw new SubtitleDecoderException("Unable to decode SRV3", e);
    }
  }

  private void parsePen(XmlPullParser parser, Map<String, Pen> pens) {
    String id = parser.getAttributeValue(null, "id");
    if (id == null) {
      return;
    }
    Pen pen = new Pen();
    pen.b = "1".equals(parser.getAttributeValue(null, "b"));
    pen.i = "1".equals(parser.getAttributeValue(null, "i"));
    pen.u = "1".equals(parser.getAttributeValue(null, "u"));
    
    String fc = parser.getAttributeValue(null, "fc");
    if (fc != null) {
      try {
        pen.fc = Color.parseColor(fc);
      } catch (IllegalArgumentException e) {
        // Ignore
      }
    }
    
    String bc = parser.getAttributeValue(null, "bc");
    if (bc != null) {
      try {
        pen.bc = Color.parseColor(bc);
      } catch (IllegalArgumentException e) {
        // Ignore
      }
    }
    // Handle foreground alpha (fo) and background alpha (bo)
    String fo = parser.getAttributeValue(null, "fo");
    if (fo != null && pen.fc != null) {
      try {
        int alpha = Integer.parseInt(fo);
        pen.fc = Color.argb(alpha, Color.red(pen.fc), Color.green(pen.fc), Color.blue(pen.fc));
      } catch (NumberFormatException e) {
        // Ignore
      }
    }
    String bo = parser.getAttributeValue(null, "bo");
    if (bo != null && pen.bc != null) {
      try {
        int alpha = Integer.parseInt(bo);
        pen.bc = Color.argb(alpha, Color.red(pen.bc), Color.green(pen.bc), Color.blue(pen.bc));
      } catch (NumberFormatException e) {
        // Ignore
      }
    }

    pens.put(id, pen);
  }

  private void parseWp(XmlPullParser parser, Map<String, Wp> wps) {
    String id = parser.getAttributeValue(null, "id");
    if (id == null) {
      return;
    }
    Wp wp = new Wp();
    
    String apStr = parser.getAttributeValue(null, "ap");
    if (apStr != null) {
      try {
        wp.ap = Integer.parseInt(apStr);
      } catch (NumberFormatException e) {
        // Ignore
      }
    }

    String ahStr = parser.getAttributeValue(null, "ah");
    if (ahStr != null) {
      try {
        wp.ah = Float.parseFloat(ahStr) / 100f;
      } catch (NumberFormatException e) {
        // Ignore
      }
    }

    String avStr = parser.getAttributeValue(null, "av");
    if (avStr != null) {
      try {
        wp.av = Float.parseFloat(avStr) / 100f;
      } catch (NumberFormatException e) {
        // Ignore
      }
    }

    wps.put(id, wp);
  }

  private void parseParagraph(
      XmlPullParser parser,
      Map<String, Pen> pens,
      Map<String, Wp> wps,
      List<Srv3Subtitle.Srv3Cue> cues)
      throws XmlPullParserException, IOException {

    String t = parser.getAttributeValue(null, "t");
    String d = parser.getAttributeValue(null, "d");
    if (t == null) {
      return;
    }

    long startTimeUs;
    try {
      startTimeUs = Long.parseLong(t) * 1000L;
    } catch (NumberFormatException e) {
      return;
    }

    long durationUs = 0;
    if (d != null) {
      try {
        durationUs = Long.parseLong(d) * 1000L;
      } catch (NumberFormatException e) {
        // Ignore
      }
    }
    long endTimeUs = startTimeUs + durationUs;

    String wpId = parser.getAttributeValue(null, "wp");
    Wp wp = wpId != null ? wps.get(wpId) : null;
    
    String defaultPenId = parser.getAttributeValue(null, "p");

    SpannableStringBuilder builder = new SpannableStringBuilder();

    // Iterate through children tags inside <p> or text content
    int eventType = parser.next();
    String currentSpanPenId = null;
    int currentSpanStart = 0;

    while (true) {
      if (eventType == XmlPullParser.START_TAG) {
        String tagName = parser.getName();
        if ("s".equals(tagName) || "span".equals(tagName)) {
          currentSpanStart = builder.length();
          currentSpanPenId = parser.getAttributeValue(null, "p");
        }
      } else if (eventType == XmlPullParser.TEXT) {
        String text = parser.getText();
        if (text != null) {
          builder.append(text.replace("\\n", "\n"));
        }
      } else if (eventType == XmlPullParser.END_TAG) {
        String tagName = parser.getName();
        if ("s".equals(tagName) || "span".equals(tagName)) {
          // Apply pen styles to span
          Pen pen = currentSpanPenId != null ? pens.get(currentSpanPenId) : null;
          applyPen(pen, builder, currentSpanStart, builder.length());
          currentSpanPenId = null;
        } else if ("p".equals(tagName)) {
          break;
        }
      } else if (eventType == XmlPullParser.END_DOCUMENT) {
        break;
      }
      eventType = parser.next();
    }

    if (builder.length() == 0) {
      return;
    }

    // Apply default paragraph pen
    Pen defaultPen = defaultPenId != null ? pens.get(defaultPenId) : null;
    applyPen(defaultPen, builder, 0, builder.length());

    Cue cue = buildCue(builder, wp);
    cues.add(new Srv3Subtitle.Srv3Cue(cue, startTimeUs, endTimeUs));
  }

  private void applyPen(Pen pen, SpannableStringBuilder builder, int start, int end) {
    if (pen == null || start >= end) {
      return;
    }

    if (pen.b && pen.i) {
      builder.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    } else if (pen.b) {
      builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    } else if (pen.i) {
      builder.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    if (pen.u) {
      builder.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    if (pen.fc != null) {
      builder.setSpan(new ForegroundColorSpan(pen.fc), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    if (pen.bc != null) {
      builder.setSpan(new BackgroundColorSpan(pen.bc), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  private Cue buildCue(CharSequence text, Wp wp) {
    if (wp == null) {
      return new Cue(text);
    }
    
    // Anchor Points (ap):
    // 0 = bottom-left, 1 = bottom-center, 2 = bottom-right
    // 3 = middle-left, 4 = middle-center, 5 = middle-right
    // 6 = top-left, 7 = top-center, 8 = top-right
    
    float position = wp.ah != null ? wp.ah : Cue.DIMEN_UNSET;
    float line = wp.av != null ? wp.av : Cue.DIMEN_UNSET;
    
    @Cue.AnchorType int positionAnchor = Cue.ANCHOR_TYPE_START;
    @Cue.AnchorType int lineAnchor = Cue.ANCHOR_TYPE_START;
    
    if (wp.ap != null) {
      switch (wp.ap) {
        case 0: positionAnchor = Cue.ANCHOR_TYPE_START; lineAnchor = Cue.ANCHOR_TYPE_END; break;
        case 1: positionAnchor = Cue.ANCHOR_TYPE_MIDDLE; lineAnchor = Cue.ANCHOR_TYPE_END; break;
        case 2: positionAnchor = Cue.ANCHOR_TYPE_END; lineAnchor = Cue.ANCHOR_TYPE_END; break;
        case 3: positionAnchor = Cue.ANCHOR_TYPE_START; lineAnchor = Cue.ANCHOR_TYPE_MIDDLE; break;
        case 4: positionAnchor = Cue.ANCHOR_TYPE_MIDDLE; lineAnchor = Cue.ANCHOR_TYPE_MIDDLE; break;
        case 5: positionAnchor = Cue.ANCHOR_TYPE_END; lineAnchor = Cue.ANCHOR_TYPE_MIDDLE; break;
        case 6: positionAnchor = Cue.ANCHOR_TYPE_START; lineAnchor = Cue.ANCHOR_TYPE_START; break;
        case 7: positionAnchor = Cue.ANCHOR_TYPE_MIDDLE; lineAnchor = Cue.ANCHOR_TYPE_START; break;
        case 8: positionAnchor = Cue.ANCHOR_TYPE_END; lineAnchor = Cue.ANCHOR_TYPE_START; break;
      }
    } else {
      // Default fallback
      positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
      lineAnchor = Cue.ANCHOR_TYPE_END;
      if (line == Cue.DIMEN_UNSET) {
         line = 0.9f; // default subtitle bottom
      }
      if (position == Cue.DIMEN_UNSET) {
         position = 0.5f;
      }
    }
    
    return new Cue(text, Layout.Alignment.ALIGN_CENTER, line, Cue.LINE_TYPE_FRACTION, lineAnchor, position, positionAnchor, Cue.DIMEN_UNSET);
  }

  private static class Pen {
    boolean b;
    boolean i;
    boolean u;
    Integer fc;
    Integer bc;
  }

  private static class Wp {
    Integer ap;
    Float ah;
    Float av;
  }
}