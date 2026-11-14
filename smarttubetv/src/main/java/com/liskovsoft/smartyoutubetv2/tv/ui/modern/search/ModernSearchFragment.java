package com.liskovsoft.smartyoutubetv2.tv.ui.modern.search;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.SinglePresenterSelector;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse.ModernRowsView;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernSpinner;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernCardPresenter;

import java.util.List;

/**
 * Search page: voice button and query field on top, suggestions and on-screen keyboard, results below.
 */
public class ModernSearchFragment extends Fragment implements SearchView, ModernKeyboardView.Listener {
    private static final int REQUEST_VOICE = 1;
    private static final int MAX_SUGGESTIONS = 5;
    private SearchPresenter mPresenter;
    private MediaServiceSearchTagProvider mTagsProvider;
    private CrashRestorer mCrashRestorer;
    private View mPage;
    private TextView mField;
    private EditText mInput;
    private LinearLayout mSuggestions;
    private ModernKeyboardView mKeyboard;
    private ModernRowsView mResults;
    private View mProgress;
    private String mQuery = "";
    private String mSearchedQuery;
    private ModernVoiceSearch mVoice;
    private View mMic;
    private boolean mFocusResultsPending;
    private boolean mIsFragmentCreated;
    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusListener = (oldFocus, newFocus) -> onFocusMoved(newFocus);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null);
        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;
        mPresenter = SearchPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.modern_search, container, false);
        mPage = root.findViewById(R.id.modern_search_page);
        mField = root.findViewById(R.id.modern_search_field);
        mInput = root.findViewById(R.id.modern_search_input);
        mSuggestions = root.findViewById(R.id.modern_search_suggestions);
        mKeyboard = root.findViewById(R.id.modern_search_keyboard);
        mProgress = root.findViewById(R.id.modern_search_progress);
        ModernSpinner.apply((ProgressBar) mProgress);

        ModernCardPresenter cardPresenter = new ModernCardPresenter();
        cardPresenter.setOnItemViewLongPressedListener((viewHolder, item) -> {
            if (item instanceof Video) {
                mPresenter.onVideoItemLongClicked((Video) item);
            }
        });
        mResults = new ModernRowsView(requireContext(), mPresenter, new SinglePresenterSelector(cardPresenter));
        ((FrameLayout) root.findViewById(R.id.modern_search_results)).addView(mResults);
        updateResultsFocusability();

        mKeyboard.setListener(this);
        mMic = root.findViewById(R.id.modern_search_mic);
        mMic.setBackground(ModernColorScheme.micBackground(requireContext()));
        mMic.setOnClickListener(v -> startVoiceRecognition());
        mVoice = new ModernVoiceSearch(requireContext(), new ModernVoiceSearch.Callback() {
            @Override
            public void onListening(boolean listening) {
                mMic.setActivated(listening);
                mField.setHint(listening ? R.string.lb_search_bar_hint_speech : R.string.settings_search);
            }

            @Override
            public void onPartialResult(String text) {
                mField.setText(text);
            }

            @Override
            public void onResult(String text) {
                startSearch(text);
            }

            @Override
            public void onStartIntent(Intent intent) {
                try {
                    startActivityForResult(intent, REQUEST_VOICE);
                } catch (ActivityNotFoundException e) {
                    showSystemKeyboard();
                }
            }
        });
        setupSystemInput();

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.getViewTreeObserver().addOnGlobalFocusChangeListener(mFocusListener);
        mPresenter.onViewInitialized();
        mCrashRestorer.restorePlayback();

        if (SearchData.instance(getContext()).isKeyboardAutoShowEnabled()) {
            // Search setting: the system keyboard opens right away
            view.post(this::showSystemKeyboard);
        } else if (!mKeyboard.hasFocus()) {
            mKeyboard.focusFirstKey();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mVoice.stop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mCrashRestorer.persistVideo(outState, mPresenter.getCurrentVideo());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mIsFragmentCreated) {
            mPresenter.onViewResumed();
        }
        mIsFragmentCreated = false;
    }

    @Override
    public void onDestroyView() {
        if (getView() != null) {
            getView().getViewTreeObserver().removeOnGlobalFocusChangeListener(mFocusListener);
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mPresenter.onFinish();
    }

    // SearchView

    @Override
    public void updateSearch(VideoGroup group) {
        mResults.update(group);
        updateResultsFocusability();

        // Search setting: the results take the focus once they arrive
        if (mFocusResultsPending && !mResults.isEmpty()) {
            mFocusResultsPending = false;
            if (SearchData.instance(getContext()).isFocusOnResultsEnabled()) {
                mResults.post(mResults::focusContent);
            }
        }
    }

    @Override
    public void clearSearch() {
        mSearchedQuery = null;
        mResults.clear();
        updateResultsFocusability();
    }

    /**
     * Down from the keyboard doesn't land on an empty results area (the page would scroll to nothing).
     */
    private void updateResultsFocusability() {
        mResults.setDescendantFocusability(mResults.isEmpty() ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    @Override
    public void clearSearchTags() {
        mSuggestions.removeAllViews();
    }

    @Override
    public void removeSearchTag(Tag tag) {
        for (int i = 0; i < mSuggestions.getChildCount(); i++) {
            View child = mSuggestions.getChildAt(i);
            if (child.getTag() == tag) {
                mSuggestions.removeViewAt(i);
                return;
            }
        }
    }

    @Override
    public void setTagsProvider(MediaServiceSearchTagProvider provider) {
        mTagsProvider = provider;
        loadSuggestions(mQuery);
    }

    @Override
    public void showProgressBar(boolean show) {
        if (mProgress != null) {
            mProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void startSearch(String searchText) {
        if (searchText != null) {
            setQuery(searchText);
            submit();
        } else {
            setQuery("");
            mKeyboard.focusFirstKey();
        }
    }

    @Override
    public String getSearchText() {
        return mQuery;
    }

    @Override
    public void startVoiceRecognition() {
        mVoice.start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VOICE && resultCode == Activity.RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                startSearch(results.get(0));
            }
        }
    }

    @Override
    public void finishReally() {
        if (getActivity() instanceof LeanbackActivity) {
            ((LeanbackActivity) getActivity()).finishReally();
        }
    }

    // Keyboard

    @Override
    public void onKey(KeyboardModel.Key key) {
        switch (key.type) {
            case KeyboardModel.TYPE_MODE:
                mKeyboard.toggleSymbols();
                return;
            case KeyboardModel.TYPE_SYSTEM_KEYBOARD:
                showSystemKeyboard();
                return;
            case KeyboardModel.TYPE_SEARCH:
                submit();
                return;
            default:
                setQuery(KeyboardModel.apply(mQuery, key));
        }
    }

    // Internal

    private void setQuery(String query) {
        mQuery = query != null ? query : "";
        mField.setText(mQuery);

        if (!TextUtils.equals(mInput.getText(), mQuery)) {
            mInput.setText(mQuery);
            mInput.setSelection(mQuery.length());
        }

        loadSuggestions(mQuery);
    }

    private void submit() {
        hideSystemKeyboard();

        if (!TextUtils.isEmpty(mQuery) && !mQuery.equals(mSearchedQuery)) {
            mSearchedQuery = mQuery;
            mFocusResultsPending = true;
            mPresenter.onSearch(mQuery);
        }
    }

    private void loadSuggestions(String query) {
        if (mTagsProvider == null) {
            return;
        }

        mTagsProvider.search(query, results -> {
            if (getContext() == null || !TextUtils.equals(query, mQuery)) {
                return;
            }

            mSuggestions.removeAllViews();

            if (results == null) {
                return;
            }

            int count = 0;
            for (Tag tag : results) {
                if (count++ == MAX_SUGGESTIONS) {
                    break;
                }
                addSuggestion(tag);
            }
        });
    }

    private void addSuggestion(Tag tag) {
        View item = LayoutInflater.from(getContext()).inflate(R.layout.modern_search_suggestion, mSuggestions, false);
        ((TextView) item.findViewById(R.id.modern_suggestion_text)).setText(tag.tag);
        item.setTag(tag);
        item.setOnClickListener(v -> startSearch(tag.tag));
        item.setOnLongClickListener(v -> {
            mPresenter.onTagLongClicked(tag);
            return true;
        });
        mSuggestions.addView(item);
    }

    private void setupSystemInput() {
        if (SearchData.instance(getContext()).isTypingCorrectionDisabled()) {
            // Search setting, same trick as the classic search: no suggestions or corrections from the keyboard
            mInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        }

        mInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.equals(s, mQuery)) {
                    setQuery(s.toString());
                }
            }
        });
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submit();
                return true;
            }
            return false;
        });
    }

    private void showSystemKeyboard() {
        mInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSystemKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && mInput.hasFocus()) {
            imm.hideSoftInputFromWindow(mInput.getWindowToken(), 0);
            mKeyboard.focusFirstKey();
        }
    }

    /**
     * Results scroll up to the top of the page when focused, like the other sections.
     */
    private void onFocusMoved(View focused) {
        if (mPage == null || focused == null) {
            return;
        }

        float target = isInside(focused, mResults) ?
                getResources().getDimension(R.dimen.modern_first_shelf_y) - getResources().getDimension(R.dimen.modern_search_results_top) : 0;

        if (mPage.getTranslationY() != target) {
            mPage.animate().translationY(target).setDuration(ModernTheme.ANIM_SCROLL_MS).setInterpolator(ModernTheme.scrollInterpolator());

            // Query panel makes room for the results
            float alpha = target == 0 ? 1f : 0f;
            for (View view : new View[] {mSuggestions, mKeyboard, (View) mField.getParent()}) {
                view.animate().alpha(alpha).setDuration(ModernTheme.ANIM_FAST_MS);
            }
        }
    }

    private static boolean isInside(View view, View parent) {
        View current = view;
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }
}
