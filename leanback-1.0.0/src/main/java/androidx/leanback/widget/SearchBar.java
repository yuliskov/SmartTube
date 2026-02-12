/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.widget;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.leanback.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A search widget containing a search orb and a text entry view.
 *
 * <p>
 * Note: When {@link SpeechRecognitionCallback} is not used, i.e. using {@link SpeechRecognizer},
 * your application will need to declare android.permission.RECORD_AUDIO in manifest file.
 * If your application target >= 23 and the device is running >= 23, it needs implement
 * {@link SearchBarPermissionListener} where requests runtime permission.
 * </p>
 */
public class SearchBar extends RelativeLayout {
    static final String TAG = SearchBar.class.getSimpleName();
    static final boolean DEBUG = false;

    static final float FULL_LEFT_VOLUME = 1.0f;
    static final float FULL_RIGHT_VOLUME = 1.0f;
    static final int DEFAULT_PRIORITY = 1;
    static final int DO_NOT_LOOP = 0;
    static final float DEFAULT_RATE = 1.0f;

    /**
     * Interface for receiving notification of search query changes.
     */
    public interface SearchBarListener {

        /**
         * Method invoked when the search bar detects a change in the query.
         *
         * @param query The current full query.
         */
        public void onSearchQueryChange(String query);

        /**
         * <p>Method invoked when the search query is submitted.</p>
         *
         * <p>This method can be called without a preceeding onSearchQueryChange,
         * in particular in the case of a voice input.</p>
         *
         * @param query The query being submitted.
         */
        public void onSearchQuerySubmit(String query);

        /**
         * Method invoked when the IME is being dismissed.
         *
         * @param query The query set in the search bar at the time the IME is being dismissed.
         */
        public void onKeyboardDismiss(String query);

    }

    /**
     * Interface that handles runtime permissions requests. App sets listener on SearchBar via
     * {@link #setPermissionListener(SearchBarPermissionListener)}.
     */
    public interface SearchBarPermissionListener {

        /**
         * Method invoked when SearchBar asks for "android.permission.RECORD_AUDIO" runtime
         * permission.
         */
        void requestAudioPermission();

    }

    SearchBarListener mSearchBarListener;
    SearchEditText mSearchTextEditor;
    SpeechOrbView mSpeechOrbView;
    private ImageView mBadgeView;
    String mSearchQuery;
    private String mHint;
    private String mTitle;
    private Drawable mBadgeDrawable;
    final Handler mHandler = new Handler();
    private final InputMethodManager mInputMethodManager;
    boolean mAutoStartRecognition = false;
    private Drawable mBarBackground;

    private final int mTextColor;
    private final int mTextColorSpeechMode;
    private final int mTextHintColor;
    private final int mTextHintColorSpeechMode;
    private int mBackgroundAlpha;
    private int mBackgroundSpeechAlpha;
    private int mBarHeight;
    private SpeechRecognizer mSpeechRecognizer;
    private SpeechRecognitionCallback mSpeechRecognitionCallback;
    private boolean mListening;
    SoundPool mSoundPool;
    SparseIntArray mSoundMap = new SparseIntArray();
    boolean mRecognizing = false;
    private final Context mContext;
    private AudioManager mAudioManager;
    private SearchBarPermissionListener mPermissionListener;

    public SearchBar(Context context) {
        this(context, null);
    }

    public SearchBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;

        Resources r = getResources();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.lb_search_bar, this, true);

        mBarHeight = getResources().getDimensionPixelSize(R.dimen.lb_search_bar_height);
        RelativeLayout.LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                mBarHeight);
        params.addRule(ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        setLayoutParams(params);
        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(false);

        mSearchQuery = "";
        mInputMethodManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);

        mTextColorSpeechMode = r.getColor(R.color.lb_search_bar_text_speech_mode);
        mTextColor = r.getColor(R.color.lb_search_bar_text);

        mBackgroundSpeechAlpha = r.getInteger(R.integer.lb_search_bar_speech_mode_background_alpha);
        mBackgroundAlpha = r.getInteger(R.integer.lb_search_bar_text_mode_background_alpha);

        mTextHintColorSpeechMode = r.getColor(R.color.lb_search_bar_hint_speech_mode);
        mTextHintColor = r.getColor(R.color.lb_search_bar_hint);

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        RelativeLayout items = (RelativeLayout)findViewById(R.id.lb_search_bar_items);
        mBarBackground = items.getBackground();

        mSearchTextEditor = (SearchEditText)findViewById(R.id.lb_search_text_editor);
        mBadgeView = (ImageView)findViewById(R.id.lb_search_bar_badge);
        if (null != mBadgeDrawable) {
            mBadgeView.setImageDrawable(mBadgeDrawable);
        }

        mSearchTextEditor.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (DEBUG) Log.v(TAG, "EditText.onFocusChange " + hasFocus);
                if (hasFocus) {
                    showNativeKeyboard();
                } else {
                    hideNativeKeyboard();
                }
                updateUi(hasFocus);
            }
        });
        final Runnable mOnTextChangedRunnable = new Runnable() {
            @Override
            public void run() {
                setSearchQueryInternal(mSearchTextEditor.getText().toString());
            }
        };
        mSearchTextEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                // don't propagate event during speech recognition.
                if (mRecognizing) {
                    return;
                }
                // while IME opens,  text editor becomes "" then restores to current value
                mHandler.removeCallbacks(mOnTextChangedRunnable);
                mHandler.post(mOnTextChangedRunnable);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        mSearchTextEditor.setOnKeyboardDismissListener(
                new SearchEditText.OnKeyboardDismissListener() {
                    @Override
                    public void onKeyboardDismiss() {
                        if (null != mSearchBarListener) {
                            mSearchBarListener.onKeyboardDismiss(mSearchQuery);
                        }
                    }
                });

        mSearchTextEditor.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int action, KeyEvent keyEvent) {
                if (DEBUG) Log.v(TAG, "onEditorAction: " + action + " event: " + keyEvent);
                boolean handled = true;
                if ((EditorInfo.IME_ACTION_SEARCH == action
                        || EditorInfo.IME_NULL == action) && null != mSearchBarListener) {
                    if (DEBUG) Log.v(TAG, "Action or enter pressed");
                    hideNativeKeyboard();
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (DEBUG) Log.v(TAG, "Delayed action handling (search)");
                            submitQuery();
                        }
                    }, 500);

                } else if (EditorInfo.IME_ACTION_NONE == action && null != mSearchBarListener) {
                    if (DEBUG) Log.v(TAG, "Escaped North");
                    hideNativeKeyboard();
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (DEBUG) Log.v(TAG, "Delayed action handling (escape_north)");
                            mSearchBarListener.onKeyboardDismiss(mSearchQuery);
                        }
                    }, 500);
                } else if (EditorInfo.IME_ACTION_GO == action) {
                    if (DEBUG) Log.v(TAG, "Voice Clicked");
                        hideNativeKeyboard();
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (DEBUG) Log.v(TAG, "Delayed action handling (voice_mode)");
                                mAutoStartRecognition = true;
                                mSpeechOrbView.requestFocus();
                            }
                        }, 500);
                } else {
                    handled = false;
                }

                return handled;
            }
        });

        mSearchTextEditor.setPrivateImeOptions("escapeNorth,voiceDismiss");

        mSpeechOrbView = (SpeechOrbView)findViewById(R.id.lb_search_bar_speech_orb);
        mSpeechOrbView.setOnOrbClickedListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRecognition();
            }
        });
        mSpeechOrbView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (DEBUG) Log.v(TAG, "SpeechOrb.onFocusChange " + hasFocus);
                if (hasFocus) {
                    hideNativeKeyboard();
                    if (mAutoStartRecognition) {
                        startRecognition();
                        mAutoStartRecognition = false;
                    }
                } else {
                    stopRecognition();
                }
                updateUi(hasFocus);
            }
        });

        updateUi(hasFocus());
        updateHint();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (DEBUG) Log.v(TAG, "Loading soundPool");
        mSoundPool = new SoundPool(2, AudioManager.STREAM_SYSTEM, 0);
        loadSounds(mContext);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopRecognition();
        if (DEBUG) Log.v(TAG, "Releasing SoundPool");
        mSoundPool.release();
        super.onDetachedFromWindow();
    }

    /**
     * Sets a listener for when the term search changes
     * @param listener
     */
    public void setSearchBarListener(SearchBarListener listener) {
        mSearchBarListener = listener;
    }

    /**
     * Sets the search query
     * @param query the search query to use
     */
    public void setSearchQuery(String query) {
        stopRecognition();
        mSearchTextEditor.setText(query);
        setSearchQueryInternal(query);
    }

    void setSearchQueryInternal(String query) {
        if (DEBUG) Log.v(TAG, "setSearchQueryInternal " + query);
        if (TextUtils.equals(mSearchQuery, query)) {
            return;
        }
        mSearchQuery = query;

        if (null != mSearchBarListener) {
            mSearchBarListener.onSearchQueryChange(mSearchQuery);
        }
    }

    /**
     * Sets the title text used in the hint shown in the search bar.
     * @param title The hint to use.
     */
    public void setTitle(String title) {
        mTitle = title;
        updateHint();
    }

    /**
     * Sets background color of not-listening state search orb.
     *
     * @param colors SearchOrbView.Colors.
     */
    public void setSearchAffordanceColors(SearchOrbView.Colors colors) {
        if (mSpeechOrbView != null) {
            mSpeechOrbView.setNotListeningOrbColors(colors);
        }
    }

    /**
     * Sets background color of listening state search orb.
     *
     * @param colors SearchOrbView.Colors.
     */
    public void setSearchAffordanceColorsInListening(SearchOrbView.Colors colors) {
        if (mSpeechOrbView != null) {
            mSpeechOrbView.setListeningOrbColors(colors);
        }
    }

    /**
     * Returns the current title
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Returns the current search bar hint text.
     */
    public CharSequence getHint() {
        return mHint;
    }

    /**
     * Sets the badge drawable showing inside the search bar.
     * @param drawable The drawable to be used in the search bar.
     */
    public void setBadgeDrawable(Drawable drawable) {
        mBadgeDrawable = drawable;
        if (null != mBadgeView) {
            mBadgeView.setImageDrawable(drawable);
            if (null != drawable) {
                mBadgeView.setVisibility(View.VISIBLE);
            } else {
                mBadgeView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Returns the badge drawable
     */
    public Drawable getBadgeDrawable() {
        return mBadgeDrawable;
    }

    /**
     * Updates the completion list shown by the IME
     *
     * @param completions list of completions shown in the IME, can be null or empty to clear them
     */
    public void displayCompletions(List<String> completions) {
        List<CompletionInfo> infos = new ArrayList<>();
        if (null != completions) {
            for (String completion : completions) {
                infos.add(new CompletionInfo(infos.size(), infos.size(), completion));
            }
        }
        CompletionInfo[] array = new CompletionInfo[infos.size()];
        displayCompletions(infos.toArray(array));
    }

    /**
     * Updates the completion list shown by the IME
     *
     * @param completions list of completions shown in the IME, can be null or empty to clear them
     */
    public void displayCompletions(CompletionInfo[] completions) {
        mInputMethodManager.displayCompletions(mSearchTextEditor, completions);
    }

    /**
     * Sets the speech recognizer to be used when doing voice search. The Activity/Fragment is in
     * charge of creating and destroying the recognizer with its own lifecycle.
     *
     * @param recognizer a SpeechRecognizer
     */
    public void setSpeechRecognizer(SpeechRecognizer recognizer) {
        stopRecognition();
        if (null != mSpeechRecognizer) {
            mSpeechRecognizer.setRecognitionListener(null);
            if (mListening) {
                mSpeechRecognizer.cancel();
                mListening = false;
            }
        }
        mSpeechRecognizer = recognizer;
        if (mSpeechRecognitionCallback != null && mSpeechRecognizer != null) {
            throw new IllegalStateException("Can't have speech recognizer and request");
        }
    }

    /**
     * Sets the speech recognition callback.
     *
     * @deprecated Launching voice recognition activity is no longer supported. App should declare
     *             android.permission.RECORD_AUDIO in AndroidManifest file. See details in
     *             {@link androidx.leanback.app.SearchSupportFragment}.
     */
    @Deprecated
    public void setSpeechRecognitionCallback(SpeechRecognitionCallback request) {
        mSpeechRecognitionCallback = request;
        if (mSpeechRecognitionCallback != null && mSpeechRecognizer != null) {
            throw new IllegalStateException("Can't have speech recognizer and request");
        }
    }

    void hideNativeKeyboard() {
        mInputMethodManager.hideSoftInputFromWindow(mSearchTextEditor.getWindowToken(),
                InputMethodManager.RESULT_UNCHANGED_SHOWN);
    }

    void showNativeKeyboard() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mSearchTextEditor.requestFocusFromTouch();
                mSearchTextEditor.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN,
                        mSearchTextEditor.getWidth(), mSearchTextEditor.getHeight(), 0));
                mSearchTextEditor.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                        mSearchTextEditor.getWidth(), mSearchTextEditor.getHeight(), 0));
            }
        });
    }

    /**
     * This will update the hint for the search bar properly depending on state and provided title
     */
    private void updateHint() {
        String title = getResources().getString(R.string.lb_search_bar_hint);
        if (!TextUtils.isEmpty(mTitle)) {
            // MOD: hide default search prefix
            //if (isVoiceMode()) {
            //    title = getResources().getString(R.string.lb_search_bar_hint_with_title_speech, mTitle);
            //} else {
            //    title = getResources().getString(R.string.lb_search_bar_hint_with_title, mTitle);
            //}
            title = mTitle;
        } else if (isVoiceMode()) {
            title = getResources().getString(R.string.lb_search_bar_hint_speech);
        }
        mHint = title;
        if (mSearchTextEditor != null) {
            mSearchTextEditor.setHint(mHint);
        }
    }

    void toggleRecognition() {
        if (mRecognizing) {
            stopRecognition();
        } else {
            startRecognition();
        }
    }

    /**
     * Returns true if is not running Recognizer, false otherwise.
     * @return True if is not running Recognizer, false otherwise.
     */
    public boolean isRecognizing() {
        return mRecognizing;
    }

    /**
     * Stops the speech recognition, if already started.
     */
    public void stopRecognition() {
        if (DEBUG) Log.v(TAG, String.format("stopRecognition (listening: %s, recognizing: %s)",
                mListening, mRecognizing));

        if (!mRecognizing) return;

        // Edit text content was cleared when starting recognition; ensure the content is restored
        // in error cases
        mSearchTextEditor.setText(mSearchQuery);
        mSearchTextEditor.setHint(mHint);

        mRecognizing = false;

        if (mSpeechRecognitionCallback != null || null == mSpeechRecognizer) return;

        mSpeechOrbView.showNotListening();

        if (mListening) {
            mSpeechRecognizer.cancel();
            mListening = false;
        }

        mSpeechRecognizer.setRecognitionListener(null);
    }

    /**
     * Sets listener that handles runtime permission requests.
     * @param listener Listener that handles runtime permission requests.
     */
    public void setPermissionListener(SearchBarPermissionListener listener) {
        mPermissionListener = listener;
    }

    public void startRecognition() {
        if (DEBUG) Log.v(TAG, String.format("startRecognition (listening: %s, recognizing: %s)",
                mListening, mRecognizing));

        if (mRecognizing) return;
        if (!hasFocus()) {
            requestFocus();
        }
        if (mSpeechRecognitionCallback != null) {
            mSearchTextEditor.setText("");
            mSearchTextEditor.setHint("");
            mSpeechRecognitionCallback.recognizeSpeech();
            mRecognizing = true;
            return;
        }
        if (null == mSpeechRecognizer) return;
        int res = getContext().checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO);
        if (PackageManager.PERMISSION_GRANTED != res) {
            if (Build.VERSION.SDK_INT >= 23 && mPermissionListener != null) {
                mPermissionListener.requestAudioPermission();
                return;
            } else {
                // MOD: don't crash
                //throw new IllegalStateException(Manifest.permission.RECORD_AUDIO
                //        + " required for search");
                return;
            }
        }

        mRecognizing = true;

        mSearchTextEditor.setText("");

        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {
                if (DEBUG) Log.v(TAG, "onReadyForSpeech");
                mSpeechOrbView.showListening();
                playSearchOpen();
            }

            @Override
            public void onBeginningOfSpeech() {
                if (DEBUG) Log.v(TAG, "onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                if (DEBUG) Log.v(TAG, "onRmsChanged " + rmsdB);
                int level = rmsdB < 0 ? 0 : (int)(10 * rmsdB);
                mSpeechOrbView.setSoundLevel(level);
            }

            @Override
            public void onBufferReceived(byte[] bytes) {
                if (DEBUG) Log.v(TAG, "onBufferReceived " + bytes.length);
            }

            @Override
            public void onEndOfSpeech() {
                if (DEBUG) Log.v(TAG, "onEndOfSpeech");
            }

            @Override
            public void onError(int error) {
                if (DEBUG) Log.v(TAG, "onError " + error);
                switch (error) {
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        Log.w(TAG, "recognizer network timeout");
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                        Log.w(TAG, "recognizer network error");
                        break;
                    case SpeechRecognizer.ERROR_AUDIO:
                        Log.w(TAG, "recognizer audio error");
                        break;
                    case SpeechRecognizer.ERROR_SERVER:
                        Log.w(TAG, "recognizer server error");
                        break;
                    case SpeechRecognizer.ERROR_CLIENT:
                        Log.w(TAG, "recognizer client error");
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        Log.w(TAG, "recognizer speech timeout");
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        Log.w(TAG, "recognizer no match");
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        Log.w(TAG, "recognizer busy");
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        Log.w(TAG, "recognizer insufficient permissions");
                        break;
                    default:
                        Log.d(TAG, "recognizer other error");
                        break;
                }

                stopRecognition();
                playSearchFailure();
            }

            @Override
            public void onResults(Bundle bundle) {
                if (DEBUG) Log.v(TAG, "onResults");
                final ArrayList<String> matches =
                        bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    if (DEBUG) Log.v(TAG, "Got results" + matches);

                    mSearchQuery = matches.get(0);
                    mSearchTextEditor.setText(mSearchQuery);
                    submitQuery();
                }

                stopRecognition();
                playSearchSuccess();
            }

            @Override
            public void onPartialResults(Bundle bundle) {
                ArrayList<String> results = bundle.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (DEBUG) {
                    Log.v(TAG, "onPartialResults " + bundle + " results "
                            + (results == null ? results : results.size()));
                }
                if (results == null || results.size() == 0) {
                    return;
                }

                // stableText: high confidence text from PartialResults, if any.
                // Otherwise, existing stable text.
                final String stableText = results.get(0);
                if (DEBUG) Log.v(TAG, "onPartialResults stableText " + stableText);

                // pendingText: low confidence text from PartialResults, if any.
                // Otherwise, empty string.
                final String pendingText = results.size() > 1 ? results.get(1) : null;
                if (DEBUG) Log.v(TAG, "onPartialResults pendingText " + pendingText);

                mSearchTextEditor.updateRecognizedText(stableText, pendingText);
            }

            @Override
            public void onEvent(int i, Bundle bundle) {

            }
        });

        mListening = true;
        try {
            mSpeechRecognizer.startListening(recognizerIntent);
        } catch (SecurityException e) {
            // MOD: Swallow the exception
            // Not allowed to bind to service Intent { act=android.speech.RecognitionService cmp=com.touchtype.swiftkey/com.swiftkey.microsoftspeechservice.MicrosoftSpeechRecognitionService }
            e.printStackTrace();
        }
    }

    void updateUi(boolean hasFocus) {
        if (hasFocus) {
            mBarBackground.setAlpha(mBackgroundSpeechAlpha);
            if (isVoiceMode()) {
                mSearchTextEditor.setTextColor(mTextHintColorSpeechMode);
                mSearchTextEditor.setHintTextColor(mTextHintColorSpeechMode);
            } else {
                mSearchTextEditor.setTextColor(mTextColorSpeechMode);
                mSearchTextEditor.setHintTextColor(mTextHintColorSpeechMode);
            }
        } else {
            mBarBackground.setAlpha(mBackgroundAlpha);
            mSearchTextEditor.setTextColor(mTextColor);
            mSearchTextEditor.setHintTextColor(mTextHintColor);
        }

        updateHint();
    }

    private boolean isVoiceMode() {
        return mSpeechOrbView.isFocused();
    }

    void submitQuery() {
        if (!TextUtils.isEmpty(mSearchQuery) && null != mSearchBarListener) {
            mSearchBarListener.onSearchQuerySubmit(mSearchQuery);
        }
    }

    private void loadSounds(Context context) {
        int[] sounds = {
                R.raw.lb_voice_failure,
                R.raw.lb_voice_open,
                R.raw.lb_voice_no_input,
                R.raw.lb_voice_success,
        };
        for (int sound : sounds) {
            mSoundMap.put(sound, mSoundPool.load(context, sound, 1));
        }
    }

    private void play(final int resId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int sound = mSoundMap.get(resId);
                mSoundPool.play(sound, FULL_LEFT_VOLUME, FULL_RIGHT_VOLUME, DEFAULT_PRIORITY,
                        DO_NOT_LOOP, DEFAULT_RATE);
            }
        });
    }

    void playSearchOpen() {
        play(R.raw.lb_voice_open);
    }

    void playSearchFailure() {
        play(R.raw.lb_voice_failure);
    }

    private void playSearchNoInput() {
        play(R.raw.lb_voice_no_input);
    }

    void playSearchSuccess() {
        play(R.raw.lb_voice_success);
    }

    @Override
    public void setNextFocusDownId(int viewId) {
        mSpeechOrbView.setNextFocusDownId(viewId);
        mSearchTextEditor.setNextFocusDownId(viewId);
    }

}
