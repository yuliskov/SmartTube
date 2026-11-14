package com.liskovsoft.smartyoutubetv2.tv.ui.modern.search;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;

import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.Speech;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.SpeechRecognitionNotAvailable;

import java.util.List;

/**
 * Voice search with the recognizer chosen in the search settings, like the classic search:<br/>
 * system (in-app, no extra screen), intent (the system voice screen) or Gotev library.
 */
final class ModernVoiceSearch {
    private static final String TAG = ModernVoiceSearch.class.getSimpleName();
    private final Context mContext;
    private final Callback mCallback;
    private SpeechRecognizer mRecognizer;
    private boolean mIsGotevActive;

    interface Callback {
        void onListening(boolean listening);
        void onPartialResult(String text);
        void onResult(String text);
        /**
         * The chosen recognizer isn't available: use the system voice screen.
         */
        void onStartIntent(Intent intent);
    }

    ModernVoiceSearch(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
    }

    static Intent createIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        return intent;
    }

    void start() {
        stop();

        switch (SearchData.instance(mContext).getSpeechRecognizerType()) {
            case SearchData.SPEECH_RECOGNIZER_SYSTEM:
                startSystem();
                break;
            case SearchData.SPEECH_RECOGNIZER_GOTEV:
                startGotev();
                break;
            default:
                mCallback.onStartIntent(createIntent());
        }
    }

    void stop() {
        if (mRecognizer != null) {
            mRecognizer.destroy();
            mRecognizer = null;
        }

        if (mIsGotevActive) {
            mIsGotevActive = false;
            try {
                Speech.getInstance().stopListening();
            } catch (IllegalArgumentException | NoSuchMethodError e) { // Speech service not registered/Android 4
                Log.e(TAG, "Gotev stop error: %s", e.getMessage());
            }
        }

        mCallback.onListening(false);
    }

    private void startSystem() {
        // Same requirement as the classic search bar
        if (Build.VERSION.SDK_INT < 23 || !SpeechRecognizer.isRecognitionAvailable(mContext)) {
            mCallback.onStartIntent(createIntent());
            return;
        }

        if (!PermissionHelpers.hasMicPermissions(mContext)) {
            // Asks for the permission, the screen is used meanwhile
            PermissionHelpers.verifyMicPermissions(mContext);
            mCallback.onStartIntent(createIntent());
            return;
        }

        mRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
        mRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                mCallback.onListening(true);
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "Recognizer error: %s", error);
                stop();

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    mCallback.onStartIntent(createIntent());
                }
            }

            @Override
            public void onResults(Bundle results) {
                String text = first(results);
                stop();

                if (text != null) {
                    mCallback.onResult(text);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String text = first(partialResults);

                if (text != null) {
                    mCallback.onPartialResult(text);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        Intent intent = createIntent();
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        mRecognizer.startListening(intent);
    }

    @SuppressWarnings("deprecation")
    private void startGotev() {
        try {
            Speech.init(mContext);
            PermissionHelpers.verifyMicPermissions(mContext);
            mIsGotevActive = true;
            Speech.getInstance().startListening(new SpeechDelegate() {
                @Override
                public void onStartOfSpeech() {
                    mCallback.onListening(true);
                }

                @Override
                public void onSpeechRmsChanged(float value) {
                }

                @Override
                public void onSpeechPartialResults(List<String> results) {
                    StringBuilder text = new StringBuilder();
                    for (String result : results) {
                        text.append(result).append(" ");
                    }
                    mCallback.onPartialResult(text.toString().trim());
                }

                @Override
                public void onSpeechResult(String result) {
                    mIsGotevActive = false;
                    mCallback.onListening(false);
                    mCallback.onResult(result);
                }
            });
        } catch (SpeechRecognitionNotAvailable | GoogleVoiceTypingDisabledException | IllegalArgumentException e) {
            Log.e(TAG, "Gotev recognizer not available: %s", e.getMessage());
            mIsGotevActive = false;
            mCallback.onStartIntent(createIntent());
        }
    }

    @Nullable
    private static String first(Bundle results) {
        List<String> texts = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
        return texts != null && !texts.isEmpty() && texts.get(0) != null ? texts.get(0) : null;
    }
}
