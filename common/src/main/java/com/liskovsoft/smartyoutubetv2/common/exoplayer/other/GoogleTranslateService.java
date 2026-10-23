package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Response;

/**
 * Translates text using the free Google Translate web endpoint (no API key required).
 * Results are cached in an LRU map and delivered on the main thread.
 */
public final class GoogleTranslateService {
    private static final String TAG = GoogleTranslateService.class.getSimpleName();
    private static final String TRANSLATE_URL =
            "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=%s&q=%s";
    private static final int CACHE_SIZE = 200;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> mCache =
            new LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    private String mTargetLanguage;

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
     * Translate text asynchronously. Callback fires on the main thread.
     * Returns immediately if the result is cached.
     */
    public void translate(String text, Callback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onTranslated(null);
            return;
        }

        String cacheKey = mTargetLanguage + ":" + text;
        synchronized (mCache) {
            String cached = mCache.get(cacheKey);
            if (cached != null) {
                callback.onTranslated(cached);
                return;
            }
        }

        mExecutor.execute(() -> {
            String result = translateSync(text);
            if (result != null) {
                synchronized (mCache) {
                    mCache.put(cacheKey, result);
                }
            }
            mMainHandler.post(() -> callback.onTranslated(result));
        });
    }

    @Nullable
    private String translateSync(String text) {
        try {
            String encoded = Uri.encode(text);
            String url = String.format(TRANSLATE_URL, mTargetLanguage, encoded);

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
        synchronized (mCache) {
            mCache.clear();
        }
    }
}
