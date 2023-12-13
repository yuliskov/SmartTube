package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;

import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SearchBar;
import androidx.leanback.widget.SearchEditText;
import androidx.leanback.widget.SearchOrbView;
import androidx.leanback.widget.SpeechOrbView;
import androidx.leanback.widget.SpeechRecognitionCallback;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import net.gotev.speech.Speech;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.SpeechRecognitionNotAvailable;
import net.gotev.speech.SpeechUtil;

import java.util.List;

public class ChannelHeaderPresenter extends RowPresenter {
    private static final String TAG = ChannelHeaderPresenter.class.getSimpleName();
    private static final String EXTRA_LEANBACK_BADGE_PRESENT = "LEANBACK_BADGE_PRESENT";
    private static final int REQUEST_SPEECH = 0x00000010;
    private static final int RESULTS_CHANGED = 0x1;
    private static final int QUERY_COMPLETE = 0x2;
    private boolean mIsKeyboardAutoShowEnabled;
    private boolean mIsKeyboardFixEnabled;
    private Drawable mBadgeDrawable;
    private int mStatus;
    private String mTitle;

    public interface ChannelHeaderProvider {
        boolean onSearchChange(String newQuery);
        boolean onSearchSubmit(String query);
        void onSearchSettingsClicked();
    }

    public static class ChannelHeaderCallback extends Row implements ChannelHeaderProvider {
        @Override
        public boolean onSearchChange(String newQuery) {
            return false;
        }

        @Override
        public boolean onSearchSubmit(String query) {
            return false;
        }

        @Override
        public void onSearchSettingsClicked() {

        }
    }

    @Override
    protected ViewHolder createRowViewHolder(ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View channelHeader = inflater.inflate(R.layout.channel_header, parent, false);

        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);

        SearchData searchData = SearchData.instance(parent.getContext());
        mIsKeyboardAutoShowEnabled = searchData.isKeyboardAutoShowEnabled();
        mIsKeyboardFixEnabled = searchData.isKeyboardFixEnabled();

        return new ViewHolder(channelHeader);
    }

    @Override
    protected void onBindRowViewHolder(ViewHolder vh, Object item) {
        super.onBindRowViewHolder(vh, item);

        ChannelHeaderProvider provider = (ChannelHeaderProvider) item;
        SearchBar searchBar = vh.view.findViewById(R.id.lb_search_bar);
        SearchOrbView searchOrbView = searchBar.findViewById(R.id.lb_search_bar_search_orb);
        SpeechOrbView speechOrbView = searchBar.findViewById(R.id.lb_search_bar_speech_orb);
        SearchEditText searchTextEditor = searchBar.findViewById(R.id.lb_search_text_editor);
        searchBar.setOnFocusChangeListener((v, focused) -> {
            Log.d(TAG, "search bar focused");
        });
        searchBar.setSearchBarListener(new SearchBar.SearchBarListener() {
            @Override
            public void onSearchQueryChange(String query) {
                if (BuildConfig.DEBUG) Log.v(TAG, String.format("onSearchQueryChange %s %s", query,
                        null == provider ? "(null)" : provider));
                if (null != provider) {
                    retrieveResults(provider, query);
                }
            }

            @Override
            public void onSearchQuerySubmit(String query) {
                if (BuildConfig.DEBUG) Log.v(TAG, String.format("onSearchQuerySubmit %s", query));
                submitQuery(provider, query);
            }

            @Override
            public void onKeyboardDismiss(String query) {
                if (BuildConfig.DEBUG) Log.v(TAG, String.format("onKeyboardDismiss %s", query));
                // MOD: don't focus on results row after hiding keyboard
                //queryComplete();
            }
        });
        switch (SearchData.instance(searchBar.getContext()).getSpeechRecognizerType()) {
            case SearchData.SPEECH_RECOGNIZER_SYSTEM:
                // Don't uncomment. Sometimes system recognizer works on lower api
                // Do nothing unless we have old api.
                // Internal recognizer needs API >= 23. See: androidx.leanback.widget.SearchBar.startRecognition()
                //if (Build.VERSION.SDK_INT < 23) {
                //    setSpeechRecognitionCallback(mDefaultCallback);
                //}
                break;
            case SearchData.SPEECH_RECOGNIZER_DEFAULT:
                searchBar.setSpeechRecognitionCallback(new DefaultCallback(searchBar.getContext(), searchBar));
                break;
            case SearchData.SPEECH_RECOGNIZER_GOTEV:
                Speech.init(searchBar.getContext());
                searchBar.setSpeechRecognitionCallback(new GotevCallback(searchBar.getContext(), provider, searchBar, speechOrbView));
                break;
        }
        searchBar.setPermissionListener(() -> PermissionHelpers.verifyMicPermissions(searchBar.getContext()));

        searchTextEditor.setSelectAllOnFocus(true); // Select all on focus (easy clear previous search)
        searchTextEditor.setOnFocusChangeListener((v, focused) -> {
            Log.d(TAG, "on search field focused");

            // User clicked on tag and tries to edit search query
            if (focused && !TextUtils.isEmpty(getSearchBarText(searchTextEditor))) {
                SearchPresenter.instance(v.getContext()).disposeActions();
            }

            if (mIsKeyboardAutoShowEnabled && focused) {
                Helpers.showKeyboardAlt(v.getContext(), v);
            }
        });
        searchTextEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // NOP
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // NOP
            }

            @Override
            public void afterTextChanged(Editable s) {
                //Utils.enableScreensaver(getActivity(), true);
            }
        });

        if (mIsKeyboardFixEnabled) {
            KeyHelpers.fixEnterKey(searchTextEditor);
        }

        searchOrbView.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                Helpers.hideKeyboard(searchBar.getContext(), v);
            }
        });
        searchOrbView.setOnOrbClickedListener(v -> submitQuery(provider, getSearchBarText(searchTextEditor)));

        // MOD: search settings button
        SearchOrbView searchSettingsOrbView = searchBar.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.search_settings_orb);
        searchSettingsOrbView.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                Helpers.hideKeyboard(searchBar.getContext(), v);
            }
        });
        searchSettingsOrbView.setOnOrbClickedListener(v -> provider.onSearchSettingsClicked());

        OnFocusChangeListener previousListener = speechOrbView.getOnFocusChangeListener();
        speechOrbView.setOnFocusChangeListener((v, focused) -> {
            if (!focused) {
                stopSpeechService(searchBar.getContext());
            }

            // Fix: Enable edit field dynamic style: white/grey, listening/non listening
            if (previousListener != null) {
                previousListener.onFocusChange(v, focused);
            }
        });

        if (null != mBadgeDrawable) {
            setBadgeDrawable(searchBar, mBadgeDrawable);
        }

        if (null != mTitle) {
            setTitle(searchBar, mTitle);
        }
    }

    private final class DefaultCallback implements SpeechRecognitionCallback {
        private final Context mContext;
        private final SearchBar mSearchBar;

        public DefaultCallback(Context context, SearchBar searchBar) {
            mContext = context;
            mSearchBar = searchBar;
        }

        @Override
        public void recognizeSpeech() {
            if (PermissionHelpers.hasMicPermissions(mContext)) {
                MessageHelpers.showMessage(mContext, R.string.disable_mic_permission);
            }

            try {
                // TODO: implement
                //startActivityForResult(getRecognizerIntent(mSearchBar), REQUEST_SPEECH);
            } catch (ActivityNotFoundException e) {
                com.liskovsoft.sharedutils.mylogger.Log.e(TAG, "Cannot find activity for speech recognizer", e);
            } catch (NullPointerException e) {
                com.liskovsoft.sharedutils.mylogger.Log.e(TAG, "Speech recognizer can't obtain applicationInfo", e);
            }
        }
    }

    private final class GotevCallback implements SpeechRecognitionCallback {
        private final Context mContext;
        private final ChannelHeaderProvider mProvider;
        private final SearchBar mSearchBar;
        private final SpeechOrbView mSpeechOrbView;

        public GotevCallback(Context context, ChannelHeaderProvider provider, SearchBar searchBar, SpeechOrbView speechOrbView) {
            mContext = context;
            mProvider = provider;
            mSearchBar = searchBar;
            mSpeechOrbView = speechOrbView;
        }

        @Override
        public void recognizeSpeech() {
            try {
                // you must have android.permission.RECORD_AUDIO granted at this point
                PermissionHelpers.verifyMicPermissions(mContext);
                Speech.getInstance().startListening(new SpeechDelegate() {
                    @Override
                    public void onStartOfSpeech() {
                        com.liskovsoft.sharedutils.mylogger.Log.i(TAG, "speech recognition is now active");
                        showListening(mSpeechOrbView);
                    }

                    @Override
                    public void onSpeechRmsChanged(float value) {
                        com.liskovsoft.sharedutils.mylogger.Log.d(TAG, "rms is now: " + value);
                    }

                    @Override
                    public void onSpeechPartialResults(List<String> results) {
                        StringBuilder str = new StringBuilder();
                        for (String res : results) {
                            str.append(res).append(" ");
                        }

                        String result = str.toString().trim();
                        com.liskovsoft.sharedutils.mylogger.Log.i(TAG, "partial result: " + result);
                        applyExternalQuery(mProvider, mSearchBar, result, true);

                        showNotListening(mSpeechOrbView);
                    }

                    @Override
                    public void onSpeechResult(String result) {
                        com.liskovsoft.sharedutils.mylogger.Log.i(TAG, "result: " + result);
                        applyExternalQuery(mProvider, mSearchBar, result, true);

                        showNotListening(mSpeechOrbView);
                    }
                });
            } catch (SpeechRecognitionNotAvailable exc) {
                com.liskovsoft.sharedutils.mylogger.Log.e(TAG, "Speech recognition is not available on this device!");
                // You can prompt the user if he wants to install Google App to have
                // speech recognition, and then you can simply call:
                try {
                    SpeechUtil.redirectUserToGoogleAppOnPlayStore(mContext);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Returns an intent that can be used to request speech recognition.
     * Built from the base {@link RecognizerIntent#ACTION_RECOGNIZE_SPEECH} plus
     * extras:
     *
     * <ul>
     * <li>{@link RecognizerIntent#EXTRA_LANGUAGE_MODEL} set to
     * {@link RecognizerIntent#LANGUAGE_MODEL_FREE_FORM}</li>
     * <li>{@link RecognizerIntent#EXTRA_PARTIAL_RESULTS} set to true</li>
     * <li>{@link RecognizerIntent#EXTRA_PROMPT} set to the search bar hint text</li>
     * </ul>
     */
    private Intent getRecognizerIntent(SearchBar searchBar) {
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        if (searchBar != null && searchBar.getHint() != null) {
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, searchBar.getHint());
        }
        recognizerIntent.putExtra(EXTRA_LEANBACK_BADGE_PRESENT, mBadgeDrawable != null);
        return recognizerIntent;
    }

    private void submitQuery(ChannelHeaderProvider provider, String query) {
        if (query == null) {
            return;
        }
        
        if (null != provider) {
            provider.onSearchSubmit(query);
        }
    }

    private String getSearchBarText(SearchEditText searchTextEditor) {
        return searchTextEditor.getText().toString();
    }

    private void retrieveResults(ChannelHeaderProvider provider, String searchQuery) {
        if (BuildConfig.DEBUG) Log.v(TAG, "retrieveResults " + searchQuery);
        if (provider.onSearchChange(searchQuery)) {
            mStatus &= ~QUERY_COMPLETE;
        }
    }

    private void stopSpeechService(Context context) {
        // Note: Other services don't need to be stopped

        if (SearchData.instance(context).getSpeechRecognizerType() != SearchData.SPEECH_RECOGNIZER_GOTEV) {
            return;
        }

        try {
            Speech.getInstance().stopListening();
        } catch (IllegalArgumentException | NoSuchMethodError e) { // Speech service not registered/Android 4 (no such method)
            e.printStackTrace();
        }
    }

    private void setTitle(SearchBar searchBar, String title) {
        mTitle = title;
        if (null != searchBar) {
            searchBar.setTitle(title);
        }
    }

    private void setBadgeDrawable(SearchBar searchBar, Drawable drawable) {
        mBadgeDrawable = drawable;
        if (null != searchBar) {
            searchBar.setBadgeDrawable(drawable);
        }
    }

    private void applyExternalQuery(ChannelHeaderProvider provider, SearchBar mSearchBar, String query, boolean submit) {
        if (query == null || mSearchBar == null) {
            return;
        }
        mSearchBar.setSearchQuery(query);
        if (submit) {
            submitQuery(provider, query);
        }
    }

    private void showListening(SpeechOrbView speechOrbView) {
        if (speechOrbView != null) {
            speechOrbView.showListening();
        }
    }

    private void showNotListening(SpeechOrbView speechOrbView) {
        if (speechOrbView != null) {
            speechOrbView.showNotListening();
        }

        //if (mSearchTextEditor != null) {
        //    // Hide "Speak to search" when not listening
        //    mSearchTextEditor.setHint("");
        //}
    }
}
