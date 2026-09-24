package com.google.android.exoplayer2.text.srv3;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
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
import java.util.Collections;
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

  private Integer parseSrv3Color(String colorStr) {
    if (colorStr == null || colorStr.isEmpty()) {
      return null;
    }
    try {
      if (colorStr.startsWith("#")) {
        return Color.parseColor(colorStr);
      }
      try {
        // YTT often uses decimal color integers e.g. 16777215
        long colorLong = Long.parseLong(colorStr);
        // If it's RGB without alpha, add 0xFF000000 alpha
        int color = (int) colorLong;
        if ((color & 0xFF000000) == 0) {
          color |= 0xFF000000;
        }
        return color;
      } catch (NumberFormatException e) {
        // YTT hex without # e.g. FFFFFF or FFFFFFFF
        if (colorStr.length() == 6 || colorStr.length() == 8) {
          return Color.parseColor("#" + colorStr);
        }
      }
    } catch (Exception e) {
      // Ignore
    }
    return null;
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
      pen.fc = parseSrv3Color(fc);
    }
    
    String bc = parser.getAttributeValue(null, "bc");
    if (bc != null) {
      pen.bc = parseSrv3Color(bc);
    }
    // Handle foreground alpha (fo) and background alpha (bo)
    String fo = parser.getAttributeValue(null, "fo");
    if (fo != null) {
      try {
        int alpha = Integer.parseInt(fo);
        if (pen.fc != null) {
          pen.fc = Color.argb(alpha, Color.red(pen.fc), Color.green(pen.fc), Color.blue(pen.fc));
        } else {
          pen.fc = Color.argb(alpha, 255, 255, 255);
        }
      } catch (NumberFormatException e) {
        // Ignore
      }
    }
    String bo = parser.getAttributeValue(null, "bo");
    if (bo != null) {
      try {
        int alpha = Integer.parseInt(bo);
        if (pen.bc != null) {
          pen.bc = Color.argb(alpha, Color.red(pen.bc), Color.green(pen.bc), Color.blue(pen.bc));
        } else if (alpha == 0) {
          pen.bc = Color.TRANSPARENT;
        }
      } catch (NumberFormatException e) {
        // Ignore
      }
    }

    String sz = parser.getAttributeValue(null, "sz");
    if (sz != null) {
      try {
        pen.sz = Float.parseFloat(sz) / 100f;
      } catch (NumberFormatException e) {
        // Ignore
      }
    }

    String of = parser.getAttributeValue(null, "of");
    if (of != null) {
      try {
        pen.of = Integer.parseInt(of);
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

  private static class SpanInfo {
    int start;
    int end;
    String penId;
    long offsetUs;
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
    long endTimeUs = durationUs > 0 ? startTimeUs + durationUs : Long.MAX_VALUE;

    String wpId = parser.getAttributeValue(null, "wp");
    Wp wp = wpId != null ? wps.get(wpId) : null;
    if (wp == null && wps.containsKey("0")) {
      wp = wps.get("0");
    }
    
    String defaultPenId = parser.getAttributeValue(null, "p");

    StringBuilder builder = new StringBuilder();
    List<SpanInfo> spanInfos = new ArrayList<>();

    int eventType = parser.next();
    String currentSpanPenId = null;
    long currentSpanOffsetUs = 0;
    int currentSpanStart = 0;
    boolean inSpan = false;

    while (eventType != XmlPullParser.END_DOCUMENT) {
      if (eventType == XmlPullParser.START_TAG) {
        String tagName = parser.getName();
        if ("s".equals(tagName) || "span".equals(tagName)) {
          inSpan = true;
          currentSpanStart = builder.length();
          currentSpanPenId = parser.getAttributeValue(null, "p");
          String spanT = parser.getAttributeValue(null, "t");
          currentSpanOffsetUs = 0;
          if (spanT != null) {
            try {
              currentSpanOffsetUs = Long.parseLong(spanT) * 1000L;
            } catch (NumberFormatException e) {
              // Ignore
            }
          }
        }
      } else if (eventType == XmlPullParser.TEXT) {
        String text = parser.getText();
        if (text != null) {
          String normalized = text.replace("\\n", "\n");
          if (!inSpan) {
            int textStart = builder.length();
            builder.append(normalized);
            SpanInfo info = new SpanInfo();
            info.start = textStart;
            info.end = builder.length();
            info.penId = null;
            info.offsetUs = currentSpanOffsetUs;
            spanInfos.add(info);
          } else {
            builder.append(normalized);
          }
        }
      } else if (eventType == XmlPullParser.END_TAG) {
        String tagName = parser.getName();
        if ("s".equals(tagName) || "span".equals(tagName)) {
          SpanInfo info = new SpanInfo();
          info.start = currentSpanStart;
          info.end = builder.length();
          info.penId = currentSpanPenId;
          info.offsetUs = currentSpanOffsetUs;
          spanInfos.add(info);
          currentSpanPenId = null;
          inSpan = false;
        } else if ("p".equals(tagName)) {
          break;
        }
      }
      eventType = parser.next();
    }

    if (builder.length() == 0) {
      return;
    }

    // Collect all unique time offsets
    List<Long> offsets = new ArrayList<>();
    offsets.add(0L);
    for (SpanInfo info : spanInfos) {
      if (info.offsetUs > 0 && !offsets.contains(info.offsetUs)) {
        if (durationUs == 0 || info.offsetUs < durationUs) {
          offsets.add(info.offsetUs);
        }
      }
    }
    Collections.sort(offsets);

    Pen defaultPen = defaultPenId != null ? pens.get(defaultPenId) : null;
    String fullText = builder.toString();

    // Generate a cue for each interval
    for (int i = 0; i < offsets.size(); i++) {
      long currentOffsetUs = offsets.get(i);
      long intervalStartUs = startTimeUs + currentOffsetUs;
      long intervalEndUs = (i + 1 < offsets.size()) ? startTimeUs + offsets.get(i + 1) : endTimeUs;

      if (intervalStartUs >= intervalEndUs) {
        continue;
      }

      SpannableStringBuilder intervalBuilder = new SpannableStringBuilder(fullText);

      // Apply styling:
      // Spans arrived up to currentOffsetUs get their style or defaultPen.
      // Upcoming spans (offsetUs > currentOffsetUs) get transparent text color so layout remains stable without jumping.
      for (SpanInfo info : spanInfos) {
        if (info.start >= info.end) {
          continue;
        }
        if (info.offsetUs <= currentOffsetUs) {
          Pen pen = info.penId != null ? pens.get(info.penId) : defaultPen;
          applyPen(pen, intervalBuilder, info.start, info.end);
        } else {
          intervalBuilder.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), info.start, info.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }

      Cue cue = buildCue(intervalBuilder, wp);
      cues.add(new Srv3Subtitle.Srv3Cue(cue, intervalStartUs, intervalEndUs));
    }
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

    if (pen.sz != null && pen.sz > 0) {
      builder.setSpan(new RelativeSizeSpan(pen.sz), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    if (pen.of != null) {
      if (pen.of == 0) {
        builder.setSpan(new SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      } else if (pen.of == 2) {
        builder.setSpan(new SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
  }

  private Cue buildCue(CharSequence text, Wp wp) {
    if (wp == null) {
      return new Cue(text);
    }
    
    // Anchor Points (ap) in YouTube SRV3 (0 to 8):
    // 0 = top-left,    1 = top-center,    2 = top-right
    // 3 = middle-left, 4 = middle-center, 5 = middle-right
    // 6 = bottom-left, 7 = bottom-center, 8 = bottom-right
    
    float position = wp.ah != null ? wp.ah : Cue.DIMEN_UNSET;
    float line = wp.av != null ? wp.av : Cue.DIMEN_UNSET;
    
    @Cue.AnchorType int positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
    @Cue.AnchorType int lineAnchor = Cue.ANCHOR_TYPE_END;
    
    if (wp.ap != null) {
      switch (wp.ap) {
        case 0:
          positionAnchor = Cue.ANCHOR_TYPE_START;
          lineAnchor = Cue.ANCHOR_TYPE_START;
          if (line == Cue.DIMEN_UNSET) line = 0f;
          if (position == Cue.DIMEN_UNSET) position = 0f;
          break;
        case 1:
          positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
          lineAnchor = Cue.ANCHOR_TYPE_START;
          if (line == Cue.DIMEN_UNSET) line = 0f;
          if (position == Cue.DIMEN_UNSET) position = 0.5f;
          break;
        case 2:
          positionAnchor = Cue.ANCHOR_TYPE_END;
          lineAnchor = Cue.ANCHOR_TYPE_START;
          if (line == Cue.DIMEN_UNSET) line = 0f;
          if (position == Cue.DIMEN_UNSET) position = 1f;
          break;
        case 3:
          positionAnchor = Cue.ANCHOR_TYPE_START;
          lineAnchor = Cue.ANCHOR_TYPE_MIDDLE;
          if (line == Cue.DIMEN_UNSET) line = 0.5f;
          if (position == Cue.DIMEN_UNSET) position = 0f;
          break;
        case 4:
          positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
          lineAnchor = Cue.ANCHOR_TYPE_MIDDLE;
          if (line == Cue.DIMEN_UNSET) line = 0.5f;
          if (position == Cue.DIMEN_UNSET) position = 0.5f;
          break;
        case 5:
          positionAnchor = Cue.ANCHOR_TYPE_END;
          lineAnchor = Cue.ANCHOR_TYPE_MIDDLE;
          if (line == Cue.DIMEN_UNSET) line = 0.5f;
          if (position == Cue.DIMEN_UNSET) position = 1f;
          break;
        case 6:
          positionAnchor = Cue.ANCHOR_TYPE_START;
          lineAnchor = Cue.ANCHOR_TYPE_END;
          if (line == Cue.DIMEN_UNSET) line = 1f;
          if (position == Cue.DIMEN_UNSET) position = 0f;
          break;
        case 7:
          positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
          lineAnchor = Cue.ANCHOR_TYPE_END;
          if (line == Cue.DIMEN_UNSET) line = 1f;
          if (position == Cue.DIMEN_UNSET) position = 0.5f;
          break;
        case 8:
          positionAnchor = Cue.ANCHOR_TYPE_END;
          lineAnchor = Cue.ANCHOR_TYPE_END;
          if (line == Cue.DIMEN_UNSET) line = 1f;
          if (position == Cue.DIMEN_UNSET) position = 1f;
          break;
      }
    } else {
      positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
      lineAnchor = Cue.ANCHOR_TYPE_END;
      if (position == Cue.DIMEN_UNSET) position = 0.5f;
      if (line == Cue.DIMEN_UNSET) line = 1f;
    }
    
    // Apply TV safe area margin (8% top inset, 8% bottom inset)
    if (line != Cue.DIMEN_UNSET) {
      line = 0.08f + (line * 0.84f);
    }
    
    Layout.Alignment alignment = Layout.Alignment.ALIGN_CENTER;
    if (positionAnchor == Cue.ANCHOR_TYPE_START) {
      alignment = Layout.Alignment.ALIGN_NORMAL;
    } else if (positionAnchor == Cue.ANCHOR_TYPE_END) {
      alignment = Layout.Alignment.ALIGN_OPPOSITE;
    }

    return new Cue(text, alignment, line, Cue.LINE_TYPE_FRACTION, lineAnchor, position, positionAnchor, Cue.DIMEN_UNSET);
  }

  private static class Pen {
    boolean b;
    boolean i;
    boolean u;
    Integer fc;
    Integer bc;
    Float sz;
    Integer of;
  }

  private static class Wp {
    Integer ap;
    Float ah;
    Float av;
  }
}