package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;

/**
 * Translates text using the free Google Translate web endpoint (no API key required).
 * Results are cached in an LRU map and delivered on the main thread.
 *
 * <p>Subtitle lines are often sentence fragments, so a line translated alone can pick the wrong
 * meaning ("bank" next to "river"), the wrong person ("he sat" for "we sat") or turn into an
 * imperative. When neighbouring lines are known, {@link #translate(String, String, String, Callback)}
 * sends them as context in one paragraph with the current line wrapped in markers, then extracts
 * the marked part. Joining lines with newlines does not work: the endpoint translates each line
 * independently. The context result is validated against the plain per-line translation and
 * dropped (plain is used) when the marker is lost, it is much longer, or it pulled words from a
 * neighbouring line, so it is never worse than translating the line alone.
 */
public final class GoogleTranslateService {
    private static final String TAG = GoogleTranslateService.class.getSimpleName();
    private static final String TRANSLATE_URL =
            "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=%s&q=%s";
    private static final int CACHE_SIZE = 200;
    private static final long REQUEST_TIMEOUT_MS = 8000;

    private static final String MARKER_OPEN = "[[";
    private static final String MARKER_CLOSE = "]]";
    private static final Pattern MARKED = Pattern.compile("\\[\\[(.*?)\\]\\]", Pattern.DOTALL);
    /** Context result longer than this factor of the plain result (plus slack) is rejected. */
    private static final float MAX_CONTEXT_LENGTH_RATIO = 1.6f;
    private static final int CONTEXT_LENGTH_SLACK = 10;

    private static final Pattern TOKEN_SPLIT =
            Pattern.compile("[\\s\\u200c\\u200e\\u200f.,\\u060c\\u061b;:!?\\u061f\"'()\\[\\]\\u00ab\\u00bb\\u201c\\u201d\\u201e-]+");
    /** Arabic harakat, tanween, superscript alef and tatweel: ignored when comparing words. */
    private static final Pattern ARABIC_MARKS = Pattern.compile("[\\u064B-\\u065F\\u0670\\u0640]");

    /** Runs one translate job at a time (keeps request order); sub-requests go to the pool. */
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    /** Plain and context requests of one job run in parallel so context adds little latency. */
    private final ExecutorService mRequestPool = Executors.newFixedThreadPool(4);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> mCache =
            new LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    private volatile String mTargetLanguage;

    public interface Callback {
        void onTranslated(@Nullable String translatedText);
    }

    public GoogleTranslateService(String targetLanguage) {
        mTargetLanguage = targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        if (targetLanguage != null && !targetLanguage.equals(mTargetLanguage)) {
            mTargetLanguage = targetLanguage;
            synchronized (mCache) {
                mCache.clear();
            }
        }
    }

    public String getTargetLanguage() {
        return mTargetLanguage;
    }

    /**
     * Translate text asynchronously without context. Callback fires on the main thread.
     * Returns immediately if the result is cached.
     */
    public void translate(String text, Callback callback) {
        translate(null, text, null, callback);
    }

    /**
     * Translate {@code text} using the neighbouring subtitle lines as context. Either neighbour may
     * be null (e.g. only the previous line is known). Callback fires on the main thread.
     */
    public void translate(@Nullable String prev, String text, @Nullable String next, Callback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onTranslated(null);
            return;
        }
        final String lang = mTargetLanguage;
        final String p = isBlank(prev) ? null : prev.trim();
        final String n = isBlank(next) ? null : next.trim();
        final boolean useContext = p != null || n != null;
        final String resultKey = useContext ? contextKey(lang, p, text, n) : plainKey(lang, text);

        String cached = getCached(resultKey);
        if (cached != null) {
            callback.onTranslated(cached);
            return;
        }

        mExecutor.execute(() -> {
            String result = useContext ? translateWithContextSync(lang, p, text, n) : translatePlainSync(lang, text);
            if (result != null) {
                putCached(resultKey, result);
            }
            mMainHandler.post(() -> callback.onTranslated(result));
        });
    }

    @Nullable
    private String translateWithContextSync(String lang, @Nullable String prev, String text, @Nullable String next) {
        // Newlines would split the paragraph into independent segments and lose the context.
        String paragraph = join(flatten(prev), MARKER_OPEN + flatten(text) + MARKER_CLOSE, flatten(next));

        Future<String> plainF = mRequestPool.submit(() -> translatePlainSync(lang, text));
        Future<String> contextF = mRequestPool.submit(() -> requestTranslation(lang, paragraph));
        Future<String> prevF = prev != null ? mRequestPool.submit(() -> translatePlainSync(lang, prev)) : null;
        Future<String> nextF = next != null ? mRequestPool.submit(() -> translatePlainSync(lang, next)) : null;

        String plain = await(plainF);
        String contextRaw = await(contextF);
        String prevPlain = await(prevF);
        String nextPlain = await(nextF);

        String marked = extractMarked(contextRaw);
        String rejectReason = validateContextResult(marked, plain, prevPlain, nextPlain);
        if (rejectReason == null) {
            return marked;
        }
        Log.d(TAG, "Context translation rejected (%s), using per-line result", rejectReason);
        return plain;
    }

    /**
     * @return null when the context result is acceptable, otherwise the reason it was rejected
     */
    @Nullable
    static String validateContextResult(
            @Nullable String marked, @Nullable String plain, @Nullable String prevPlain, @Nullable String nextPlain) {
        if (isBlank(marked)) {
            return "marker lost";
        }
        if (isBlank(plain)) {
            return null; // nothing to compare against, the context result is all we have
        }
        if (marked.length() > MAX_CONTEXT_LENGTH_RATIO * plain.length() + CONTEXT_LENGTH_SLACK) {
            return "too long";
        }
        String leaked = findLeakedWord(marked, plain, prevPlain, nextPlain);
        if (leaked != null) {
            return "word from neighbour line: " + leaked;
        }
        return null;
    }

    /**
     * A word that is in the context result but not in the line's own translation, while it is in a
     * neighbouring line's translation, was most likely moved across the marker by reordering. The
     * viewer would then see it twice (or on the wrong line), so the context result is dropped.
     */
    @Nullable
    private static String findLeakedWord(
            String marked, String plain, @Nullable String prevPlain, @Nullable String nextPlain) {
        Set<String> own = tokens(plain);
        Set<String> neighbours = new HashSet<>();
        if (prevPlain != null) {
            neighbours.addAll(tokens(prevPlain));
        }
        if (nextPlain != null) {
            neighbours.addAll(tokens(nextPlain));
        }
        if (neighbours.isEmpty()) {
            return null;
        }
        for (String word : tokens(marked)) {
            if (word.length() < 4 || containsRelated(own, word)) {
                continue;
            }
            if (containsRelated(neighbours, word)) {
                return word;
            }
        }
        return null;
    }

    /** Same word or a different form of it (one is a prefix of the other, e.g. قرض / قرضا). */
    private static boolean containsRelated(Set<String> words, String word) {
        for (String w : words) {
            boolean wIsShorter = w.length() <= word.length();
            String shorter = wIsShorter ? w : word;
            String longer = wIsShorter ? word : w;
            if (shorter.length() >= 3 && longer.startsWith(shorter)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        for (String raw : TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            String t = ARABIC_MARKS.matcher(raw).replaceAll("");
            t = t.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا');
            if (t.startsWith("وال") && t.length() > 4) { // وال
                t = t.substring(3);
            } else if (t.startsWith("ال") && t.length() > 3) { // ال
                t = t.substring(2);
            }
            if (t.length() >= 3) {
                out.add(t);
            }
        }
        return out;
    }

    @Nullable
    private static String extractMarked(@Nullable String translated) {
        if (translated == null) {
            return null;
        }
        Matcher m = MARKED.matcher(translated);
        return m.find() ? m.group(1).trim() : null;
    }

    @Nullable
    private String translatePlainSync(String lang, String text) {
        String key = plainKey(lang, text);
        String cached = getCached(key);
        if (cached != null) {
            return cached;
        }
        String result = requestTranslation(lang, text);
        if (result != null) {
            putCached(key, result);
        }
        return result;
    }

    @Nullable
    private String requestTranslation(String lang, String text) {
        try {
            String encoded = Uri.encode(text);
            String url = String.format(TRANSLATE_URL, lang, encoded);

            Response response = OkHttpManager.instance().doGetRequest(url);
            if (response == null || !response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "Translation request failed: %s",
                        response != null ? response.code() : "null response");
                return null;
            }

            String body = response.body().string();
            return parseTranslation(body);
        } catch (Exception e) {
            Log.e(TAG, "Translation error: %s", e.getMessage());
            return null;
        }
    }

    @Nullable
    private static String await(@Nullable Future<String> future) {
        if (future == null) {
            return null;
        }
        try {
            return future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return null;
        } catch (Exception e) {
            future.cancel(true);
            Log.w(TAG, "Translation sub-request failed: %s", e.getMessage());
            return null;
        }
    }

    @Nullable
    private String getCached(String key) {
        synchronized (mCache) {
            return mCache.get(key);
        }
    }

    private void putCached(String key, String value) {
        synchronized (mCache) {
            mCache.put(key, value);
        }
    }

    private static String plainKey(String lang, String text) {
        return lang + ":" + text;
    }

    private static String contextKey(String lang, @Nullable String prev, String text, @Nullable String next) {
        return lang + "\u0001" + prev + "\u0001" + text + "\u0001" + next;
    }

    private static String flatten(@Nullable String s) {
        return s == null ? null : s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (isBlank(part)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    @Nullable
    private String parseTranslation(String json) {
        try {
            // Response can be a JSON array: [["translated text","original text",...]]
            // or an object with "sentences" array
            json = json.trim();
            if (json.startsWith("[")) {
                JSONArray outer = new JSONArray(json);
                if (outer.length() > 0) {
                    Object first = outer.get(0);
                    if (first instanceof JSONArray) {
                        return ((JSONArray) first).optString(0, null);
                    } else if (first instanceof String) {
                        return (String) first;
                    }
                }
            } else if (json.startsWith("{")) {
                JSONObject obj = new JSONObject(json);
                JSONArray sentences = obj.optJSONArray("sentences");
                if (sentences != null && sentences.length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < sentences.length(); i++) {
                        JSONObject sentence = sentences.optJSONObject(i);
                        if (sentence != null && sentence.has("trans")) {
                            sb.append(sentence.getString("trans"));
                        }
                    }
                    return sb.length() > 0 ? sb.toString() : null;
                }
            }
            Log.w(TAG, "Unexpected translate response format: %s",
                    json.length() > 100 ? json.substring(0, 100) : json);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "JSON parse error: %s", e.getMessage());
            return null;
        }
    }

    public void shutdown() {
        mExecutor.shutdownNow();
        mRequestPool.shutdownNow();
        synchronized (mCache) {
            mCache.clear();
        }
    }
}
