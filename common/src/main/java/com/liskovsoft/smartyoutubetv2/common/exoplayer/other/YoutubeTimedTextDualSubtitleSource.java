package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Response;

/**
 * Loads the YouTube timedtext document for the active caption track and the same track with
 * {@code tlang}, then maps on-screen lines by timing + text for dual subtitles without web translate
 * latency when YouTube provides a translation.
 */
public final class YoutubeTimedTextDualSubtitleSource {
    private static final String TAG = YoutubeTimedTextDualSubtitleSource.class.getSimpleName();
    /** YouTube cue timing can differ slightly from ExoPlayer clock / XML; keep generous for VOD. */
    private static final long TIME_MATCH_TOLERANCE_MS = 1500;
    private static final long START_ALIGN_MAX_DIFF_MS = 900;

    private final String mPrimaryUrl;
    private final String mTranslatedUrl;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean mReady;
    private List<Segment> mPrimary = Collections.emptyList();
    private List<Segment> mTranslated = Collections.emptyList();

    private static final class Segment {
        final long startMs;
        final long endMs;
        final String text;

        Segment(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
    }

    public YoutubeTimedTextDualSubtitleSource(String captionBaseUrl, String youtubeTlangCode) {
        this(captionBaseUrl, youtubeTlangCode, null);
    }

    public YoutubeTimedTextDualSubtitleSource(String captionBaseUrl, String youtubeTlangCode,
                                              @Nullable String poToken) {
        String base = ensureXmlCaptionFormat(stripTlangParam(captionBaseUrl));
        base = appendPoToken(base, poToken);
        mPrimaryUrl = base;
        mTranslatedUrl = appendTlang(base, youtubeTlangCode);
        Log.d(TAG, "Created: tlang=%s, poToken=%s", youtubeTlangCode,
                poToken != null ? "present(" + poToken.length() + " chars)" : "null");
    }

    /**
     * Maps UI language codes (see subtitle settings) to YouTube {@code tlang} query values.
     */
    public static String appLanguageToYoutubeTlang(@Nullable String appCode) {
        if (appCode == null || appCode.isEmpty()) {
            return "en";
        }
        switch (appCode) {
            case "zh":
                return "zh-Hans";
            case "pt":
                return "pt";
            case "fa":
                return "fa";
            default:
                return appCode;
        }
    }

    public boolean isReady() {
        return mReady;
    }

    /**
     * Prefetch caption XML on a background thread. Optional callback runs on the main thread after
     * load completes (success or failure).
     */
    public void loadAsync(@Nullable Runnable onMainThreadComplete) {
        mReady = false;
        mExecutor.execute(
                () -> {
                    try {
                        Log.d(TAG, "Loading timedtext primary URL (fmt normalized): %s", mPrimaryUrl);
                        String primaryBody = fetchBody(mPrimaryUrl);
                        String translatedBody = fetchBody(mTranslatedUrl);
                        List<Segment> primary = parseTimedTextXml(primaryBody, "primary");
                        List<Segment> translated = parseTimedTextXml(translatedBody, "tlang");
                        synchronized (YoutubeTimedTextDualSubtitleSource.this) {
                            mPrimary = primary;
                            mTranslated = translated;
                            mReady = !primary.isEmpty() && !translated.isEmpty();
                            if (mReady) {
                                Log.d(
                                        TAG,
                                        "Timed tlang ready: primary segments=%d, translated=%d",
                                        primary.size(),
                                        translated.size());
                            } else {
                                Log.d(
                                        TAG,
                                        "Timed tlang unavailable (primary=%d, translated=%d) — using translate fallback",
                                        primary.size(),
                                        translated.size());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "loadAsync: %s", e.getMessage());
                        synchronized (YoutubeTimedTextDualSubtitleSource.this) {
                            mPrimary = Collections.emptyList();
                            mTranslated = Collections.emptyList();
                            mReady = false;
                        }
                    }
                    if (onMainThreadComplete != null) {
                        mMainHandler.post(onMainThreadComplete);
                    }
                });
    }

    public void release() {
        mExecutor.shutdownNow();
        mReady = false;
        mPrimary = Collections.emptyList();
        mTranslated = Collections.emptyList();
    }

    /**
     * Looks up a translated line for the cue currently shown. Uses playback position when possible
     * to disambiguate repeated lines.
     */
    @Nullable
    public String lookup(String cuePlainText, long positionMs) {
        if (!mReady) {
            return null;
        }
        List<Segment> primary;
        List<Segment> translated;
        synchronized (this) {
            primary = mPrimary;
            translated = mTranslated;
        }
        if (primary.isEmpty() || translated.isEmpty()) {
            return null;
        }

        String normCue = normalizeText(cuePlainText);
        if (normCue.isEmpty() && positionMs < 0) {
            return null;
        }

        long pos = positionMs;
        int idx = findPrimaryIndex(primary, normCue, pos);
        if (idx < 0) {
            Log.d(
                    TAG,
                    "lookup miss: pos=%d cue=%s",
                    positionMs,
                    normCue.length() > 48 ? normCue.substring(0, 48) + "…" : normCue);
            return null;
        }
        return translationForPrimaryIndex(primary, translated, idx);
    }

    /**
     * Prefer matching by playback time (handles TTML vs timedtext wording/punctuation). Then require
     * compatible text when multiple segments overlap {@code pos}.
     */
    private static int findPrimaryIndex(List<Segment> primary, String normCue, long positionMs) {
        boolean havePos = positionMs >= 0;
        List<Integer> timeMatches = new ArrayList<>();
        if (havePos) {
            for (int i = 0; i < primary.size(); i++) {
                Segment p = primary.get(i);
                if (positionMs >= p.startMs - TIME_MATCH_TOLERANCE_MS
                        && positionMs < p.endMs + TIME_MATCH_TOLERANCE_MS) {
                    timeMatches.add(i);
                }
            }
            if (timeMatches.size() == 1) {
                return timeMatches.get(0);
            }
            if (timeMatches.size() > 1) {
                for (int i : timeMatches) {
                    if (textCompatible(normCue, normalizeText(primary.get(i).text))) {
                        return i;
                    }
                }
                return timeMatches.get(0);
            }
        }

        int bestIdx = -1;
        long bestScore = Long.MIN_VALUE;
        for (int i = 0; i < primary.size(); i++) {
            Segment p = primary.get(i);
            String normP = normalizeText(p.text);
            if (!textCompatible(normCue, normP)) {
                continue;
            }
            if (havePos
                    && positionMs >= p.startMs - TIME_MATCH_TOLERANCE_MS
                    && positionMs < p.endMs + TIME_MATCH_TOLERANCE_MS) {
                long overlap =
                        Math.min(p.endMs, positionMs + 50) - Math.max(p.startMs, positionMs - 50);
                if (overlap > bestScore) {
                    bestScore = overlap;
                    bestIdx = i;
                }
            }
        }
        if (bestIdx >= 0) {
            return bestIdx;
        }

        for (int i = 0; i < primary.size(); i++) {
            if (normCue.equals(normalizeText(primary.get(i).text))) {
                return i;
            }
        }

        if (havePos) {
            int closest = -1;
            long bestDist = Long.MAX_VALUE;
            for (int i = 0; i < primary.size(); i++) {
                Segment p = primary.get(i);
                long mid = (p.startMs + p.endMs) / 2;
                long d = Math.abs(positionMs - mid);
                if (d < bestDist) {
                    bestDist = d;
                    closest = i;
                }
            }
            if (closest >= 0 && bestDist <= TIME_MATCH_TOLERANCE_MS + 1200) {
                return closest;
            }
        }
        return -1;
    }

    private static boolean textCompatible(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.length() < 3 || b.length() < 3) {
            return a.equals(b);
        }
        return a.contains(b) || b.contains(a);
    }

    @Nullable
    private static String translationForPrimaryIndex(
            List<Segment> primary, List<Segment> translated, int primaryIdx) {
        // Prefer timing-based match: YouTube's translation engine can split or merge segments,
        // so the translated list may have a different count than the primary. Index parity is
        // unreliable; startMs alignment is the authoritative way to find the right segment.
        long start = primary.get(primaryIdx).startMs;
        Segment best = null;
        long bestDiff = Long.MAX_VALUE;
        for (Segment seg : translated) {
            long d = Math.abs(seg.startMs - start);
            if (d < bestDiff) {
                bestDiff = d;
                best = seg;
            }
        }
        if (best != null && bestDiff <= START_ALIGN_MAX_DIFF_MS && !TextUtils.isEmpty(best.text)) {
            return best.text;
        }
        // Fall back to direct index access when no timing match is close enough (e.g. the
        // translated track uses identical timing, so every diff is 0, but bestDiff check
        // already handles that; this covers edge cases where both lists have equal length).
        if (primaryIdx < translated.size()) {
            String t = translated.get(primaryIdx).text;
            if (!TextUtils.isEmpty(t)) {
                return t;
            }
        }
        return null;
    }

    private static String normalizeText(String s) {
        if (s == null) {
            return "";
        }
        String t =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        ? Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
                        : Html.fromHtml(s).toString();
        t = t.replace('\n', ' ').replace('\r', ' ').trim();
        while (t.contains("  ")) {
            t = t.replace("  ", " ");
        }
        return t;
    }

    @Nullable
    private static String fetchBody(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        Response response = null;
        try {
            response = OkHttpManager.instance().doGetRequest(url);
            if (response == null) {
                Log.w(TAG, "fetchBody: null response");
                return null;
            }
            int code = response.code();
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "fetchBody: HTTP %s for timedtext request", code);
                return null;
            }
            String body = response.body().string();
            if (body != null && body.length() < 4000) {
                Log.d(TAG, "fetchBody: HTTP %s, len=%d", code, body.length());
            } else if (body != null) {
                Log.d(TAG, "fetchBody: HTTP %s, len=%d (truncated log)", code, body.length());
            }
            return body;
        } catch (IOException e) {
            Log.w(TAG, "fetchBody: %s", e.getMessage());
            return null;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Player subtitle URLs often use {@code fmt} for the in-player format (e.g. webvtt). Dual
     * subs need XML {@code srv3} so we can parse {@code <text>} reliably alongside {@code tlang}.
     */
    private static String ensureXmlCaptionFormat(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        int q = url.indexOf('?');
        String base;
        String query;
        if (q < 0) {
            base = url;
            query = "";
        } else {
            base = url.substring(0, q);
            query = url.substring(q + 1);
        }
        StringBuilder newQuery = new StringBuilder();
        for (String pair : query.split("&")) {
            if (pair.isEmpty() || pair.startsWith("fmt=")) {
                continue;
            }
            if (newQuery.length() > 0) {
                newQuery.append('&');
            }
            newQuery.append(pair);
        }
        if (newQuery.length() > 0) {
            newQuery.append('&');
        }
        newQuery.append("fmt=srv3");
        return base + "?" + newQuery;
    }

    private static List<Segment> parseTimedTextXml(@Nullable String xml, String label) {
        if (xml == null) {
            Log.d(TAG, "parseTimedTextXml(%s): null body", label);
            return Collections.emptyList();
        }
        String trim = xml.trim();
        if (trim.isEmpty()) {
            Log.d(TAG, "parseTimedTextXml(%s): empty body", label);
            return Collections.emptyList();
        }
        if (!trim.startsWith("<")) {
            Log.d(
                    TAG,
                    "parseTimedTextXml(%s): non-XML prefix: %s",
                    label,
                    trim.length() > 80 ? trim.substring(0, 80) + "…" : trim);
            return Collections.emptyList();
        }
        try {
            XmlPullParser parser = android.util.Xml.newPullParser();
            parser.setInput(new StringReader(trim));
            List<Segment> out = new ArrayList<>();
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "text".equals(parser.getName())) {
                    String startAttr = parser.getAttributeValue(null, "start");
                    String durAttr = parser.getAttributeValue(null, "dur");
                    if (startAttr != null) {
                        double startSec = Double.parseDouble(startAttr);
                        double durSec = durAttr != null ? Double.parseDouble(durAttr) : 4.0;
                        long startMs = Math.round(startSec * 1000);
                        long endMs = startMs + Math.round(durSec * 1000);
                        String inner = readTextElement(parser);
                        out.add(new Segment(startMs, endMs, inner));
                    }
                }
                event = parser.next();
            }
            Log.d(TAG, "parseTimedTextXml(%s): %d <text> segments", label, out.size());
            return out;
        } catch (XmlPullParserException | IOException | NumberFormatException e) {
            Log.w(TAG, "parseTimedTextXml(%s): %s", label, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String readTextElement(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        int depth = parser.getDepth();
        int type = parser.next();
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.END_TAG
                    && "text".equals(parser.getName())
                    && parser.getDepth() == depth) {
                break;
            }
            if (type == XmlPullParser.TEXT || type == XmlPullParser.CDSECT) {
                String t = parser.getText();
                if (t != null) {
                    sb.append(t);
                }
            }
            type = parser.next();
        }
        return sb.toString();
    }

    private static String stripTlangParam(@Nullable String url) {
        if (url == null || !url.contains("tlang=")) {
            return url;
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        String base = url.substring(0, q + 1);
        String query = url.substring(q + 1);
        String[] pairs = query.split("&");
        StringBuilder sb = new StringBuilder(base);
        boolean first = true;
        for (String pair : pairs) {
            if (pair.isEmpty() || pair.startsWith("tlang=")) {
                continue;
            }
            if (first) {
                sb.append(pair);
                first = false;
            } else {
                sb.append('&').append(pair);
            }
        }
        String result = sb.toString();
        if (result.endsWith("?")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String appendTlang(String url, String tlang) {
        if (url == null || tlang == null || tlang.isEmpty()) {
            return url;
        }
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "tlang=" + Uri.encode(tlang);
    }

    private static String appendPoToken(@Nullable String url, @Nullable String poToken) {
        if (url == null || poToken == null || poToken.isEmpty()) {
            return url;
        }
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "pot=" + Uri.encode(poToken);
    }
}
