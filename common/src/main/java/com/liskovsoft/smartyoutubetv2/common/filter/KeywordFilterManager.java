
package com.liskovsoft.smartyoutubetv2.common.filter;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class KeywordFilterManager {

    private static final String PREF_BLOCKED_WORDS = "blocked_words";

    private static KeywordFilterManager sInstance;

    private final Context mContext;

    private final Set<String> mBlockedWords = new HashSet<>();

    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_KEYWORDS_COUNT = 200;

    private KeywordFilterManager(Context context) {
        mContext = context.getApplicationContext();
        load();
    }

    public static synchronized KeywordFilterManager instance(Context context) {

        if (sInstance == null) {
            sInstance = new KeywordFilterManager(context);
        }

        return sInstance;
    }

    public synchronized void addWord(String word) {

        if (word == null) {
            return;
        }

        String normalized = normalize(word);

        if (normalized.isEmpty() || normalized.length() > MAX_KEYWORD_LENGTH || mBlockedWords.size() >= MAX_KEYWORDS_COUNT) {
            return;
        }

        mBlockedWords.add(normalized);

        save();
    }

    public synchronized void removeWord(String word) {

        if (word == null) {
            return;
        }

        mBlockedWords.remove(normalize(word));

        save();
    }

    public synchronized Set<String> getWords() {
        return new HashSet<>(mBlockedWords);
    }

    public synchronized boolean isBlocked(String title) {

        if (title == null || title.isEmpty()) {
            return false;
        }

        String normalizedTitle = normalize(title);

        for (String blocked : mBlockedWords) {

            if (normalizedTitle.contains(blocked)) {
                return true;
            }
        }

        return false;
    }

    private void load() {

        SharedPreferences prefs =
                mContext.getSharedPreferences(
                        "blocked_words_prefs",
                        Context.MODE_PRIVATE
                );

        Set<String> stored =
                prefs.getStringSet(
                        PREF_BLOCKED_WORDS,
                        new HashSet<>()
                );

        mBlockedWords.clear();

        if (stored != null) {

            for (String word : stored) {

                if (word != null) {
                    mBlockedWords.add(normalize(word));
                }
            }
        }
    }

    private void save() {

        SharedPreferences prefs =
                mContext.getSharedPreferences(
                        "blocked_words_prefs",
                        Context.MODE_PRIVATE
                );

        prefs.edit()
                .putStringSet(
                        PREF_BLOCKED_WORDS,
                        new HashSet<>(mBlockedWords)
                )
                .apply();
    }

    private String normalize(String input) {

        return input
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
