package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.vineyard;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.RowPresenter.ViewHolder;
import androidx.leanback.widget.SpeechRecognitionCallback;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.SearchTagsProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.vineyard.PaginationAdapter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.vineyard.TagAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.presenter.vineyard.TagPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.search.SearchSupportFragment;

import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.Speech;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.SpeechRecognitionNotAvailable;
import net.gotev.speech.SpeechUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class SearchTagsFragmentBase extends SearchSupportFragment
        implements SearchSupportFragment.SearchResultProvider, SearchView {
    private static final String TAG = SearchTagsFragmentBase.class.getSimpleName();
    private static final int REQUEST_SPEECH = 0x00000010;

    private TagAdapter mSearchTagsAdapter;
    //private ObjectAdapter mItemResultsAdapter;
    private ArrayObjectAdapter mResultsAdapter; // contains tags adapter and results adapter (see attachAdapter method)
    private ListRowPresenter mResultsPresenter;
    private TagPresenter mTagsPresenter;

    private boolean mIsStopping;
    private SearchTagsProvider mSearchTagsProvider;
    private ProgressBarManager mProgressBarManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProgressBarManager = new ProgressBarManager();
        mResultsPresenter = new CustomListRowPresenter();
        mResultsAdapter = new ArrayObjectAdapter(mResultsPresenter);
        mTagsPresenter = new TagPresenter();
        mSearchTagsAdapter = new TagAdapter(getActivity(), mTagsPresenter, "");
        setSearchResultProvider(this);
        setupListenersAndPermissions();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mProgressBarManager.setRootView((ViewGroup) root);

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        mIsStopping = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        mIsStopping = true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SPEECH:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        setSearchQuery(data, true);
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(TAG, "Recognizer canceled");
                        break;
                }
                break;
        }
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mResultsAdapter;
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }

    protected abstract void onItemViewSelected(Object item);
    
    protected abstract void onItemViewClicked(Object item);

    protected void setSearchTagsProvider(SearchTagsProvider provider) {
        mSearchTagsProvider = provider;
    }

    protected void setSearchTagsLongPressListener(OnItemLongPressedListener listener) {
        mTagsPresenter.setOnItemViewLongPressedListener(listener);
    }

    public boolean isStopping() {
        return mIsStopping;
    }

    public boolean hasResults() {
        return mResultsAdapter.size() > 0;
    }

    @SuppressWarnings("deprecation")
    private void setupListenersAndPermissions() {
        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> onItemViewClicked(item));
        setOnItemViewSelectedListener((itemViewHolder, item, rowViewHolder, row) -> onItemViewSelected(item));

        // All needed permissions acquired inside SearchBar component.
        // See: androidx.leanback.widget.SearchBar.startRecognition()
        //if (SpeechRecognizer.isRecognitionAvailable(getContext())) {
        //    PermissionHelpers.verifyMicPermissions(getContext());
        //}

        // NOTE: External recognizer makes voice search behave unexpectedly (broken by Google app updates).
        // You should avoid using it till there be a solution.

        switch (SearchData.instance(getContext()).getSpeechRecognizerType()) {
            case SearchData.SPEECH_RECOGNIZER_SYSTEM:
                // Don't uncomment. Sometimes system recognizer works on lower api
                // Do nothing unless we have old api.
                // Internal recognizer needs API >= 23. See: androidx.leanback.widget.SearchBar.startRecognition()
                //if (Build.VERSION.SDK_INT < 23) {
                //    setSpeechRecognitionCallback(mDefaultCallback);
                //}
                break;
            case SearchData.SPEECH_RECOGNIZER_INTENT:
                setSpeechRecognitionCallback(mDefaultCallback);
                break;
            case SearchData.SPEECH_RECOGNIZER_GOTEV:
                Speech.init(getContext());
                setSpeechRecognitionCallback(mGotevCallback);
                break;
        }
    }

    protected void stopSpeechService() {
        // Note: Other services don't need to be stopped

        if (SearchData.instance(getContext()).getSpeechRecognizerType() != SearchData.SPEECH_RECOGNIZER_GOTEV) {
            return;
        }

        try {
            Speech.getInstance().stopListening();
        } catch (IllegalArgumentException | NoSuchMethodError e) { // Speech service not registered/Android 4 (no such method)
            e.printStackTrace();
        }
    }

    protected void loadSearchTags(String searchQuery) {
        searchTaggedPosts(searchQuery);
    }

    private void searchTaggedPosts(String query) {
        mSearchTagsAdapter.setTag(query);
        mResultsAdapter.clear();
        mSearchTagsAdapter.clear();
        performTagSearch(mSearchTagsAdapter);
    }

    private void performTagSearch(TagAdapter adapter) {
        if (mSearchTagsProvider == null) {
            return;
        }

        String query = adapter.getAdapterOptions().get(PaginationAdapter.KEY_TAG);
        mSearchTagsProvider.search(query, results -> {
            adapter.addAllItems(results);
            attachAdapter(0, adapter);
            // Same suggestions in the keyboard
            //displayCompletions(toCompletions(results));
        });
    }

    private List<String> toCompletions(List<Tag> results) {
        List<String> result = null;

        if (results != null) {
            result = new ArrayList<>();

            for (Tag tag : results) {
                result.add(tag.tag);
            }
        }

        return result;
    }

    /**
     * Disable scrolling on partially updated rows. This prevent controls from misbehaving.
     */
    protected void freeze(boolean freeze) {
        // Disable scrolling on partially updated rows. This prevent controls from misbehaving.
        RowsSupportFragment rowsSupportFragment = getRowsSupportFragment();
        if (mResultsPresenter != null && rowsSupportFragment != null) {
            ViewHolder vh = rowsSupportFragment.getRowViewHolder(rowsSupportFragment.getSelectedPosition());
            if (vh != null) {
                mResultsPresenter.freeze(vh, freeze);
            }
        }
    }

    protected void attachAdapter(int index, ObjectAdapter adapter) {
        if (mResultsAdapter != null) {
            if (!containsAdapter(adapter)) {
                index = Math.min(index, mResultsAdapter.size());
                mResultsAdapter.add(index, new ListRow(adapter));
            }
        }
    }

    protected void attachAdapter(int index, HeaderItem header, ObjectAdapter adapter) {
        if (mResultsAdapter != null) {
            if (!containsAdapter(adapter)) {
                index = Math.min(index, mResultsAdapter.size());
                mResultsAdapter.add(index, new ListRow(header, adapter));
            }
        }
    }

    protected void detachAdapter(int index) {
        if (mResultsAdapter != null && index < mResultsAdapter.size()) {
            mResultsAdapter.removeItems(index, 1);
        }
    }

    protected void clearTags() {
        if (containsAdapter(mSearchTagsAdapter)) {
            detachAdapter(0);
        }
    }

    protected boolean containsAdapter(ObjectAdapter adapter) {
        if (mResultsAdapter != null) {
            for (int i = 0; i < mResultsAdapter.size(); i++) {
                ListRow row = (ListRow) mResultsAdapter.get(i);
                if (row.getAdapter() == adapter) {
                    return true;
                }
            }
        }

        return false;
    }

    @SuppressWarnings("deprecation")
    private final SpeechRecognitionCallback mDefaultCallback = () -> {
        if (isAdded()) {
            if (PermissionHelpers.hasMicPermissions(getContext())) {
                MessageHelpers.showMessage(getContext(), R.string.disable_mic_permission);
            }

            try {
                startActivityForResult(getRecognizerIntent(), REQUEST_SPEECH);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Cannot find activity for speech recognizer", e);
            } catch (NullPointerException e) {
                Log.e(TAG, "Speech recognizer can't obtain applicationInfo", e);
            }
        } else {
            Log.e(TAG, "Can't perform search. Fragment is detached.");
        }
    };

    @SuppressWarnings("deprecation")
    private final SpeechRecognitionCallback mGotevCallback = () -> {
        if (isAdded()) {
            try {
                // you must have android.permission.RECORD_AUDIO granted at this point
                PermissionHelpers.verifyMicPermissions(getContext());
                Speech.getInstance().startListening(new SpeechDelegate() {
                    @Override
                    public void onStartOfSpeech() {
                        Log.i(TAG, "speech recognition is now active");
                        showListening();
                    }

                    @Override
                    public void onSpeechRmsChanged(float value) {
                        Log.d(TAG, "rms is now: " + value);
                    }

                    @Override
                    public void onSpeechPartialResults(List<String> results) {
                        StringBuilder str = new StringBuilder();
                        for (String res : results) {
                            str.append(res).append(" ");
                        }

                        String result = str.toString().trim();
                        Log.i(TAG, "partial result: " + result);
                        setSearchQuery(result, true);

                        showNotListening();
                    }

                    @Override
                    public void onSpeechResult(String result) {
                        Log.i(TAG, "result: " + result);
                        setSearchQuery(result, true);

                        showNotListening();
                    }
                });
            } catch (SpeechRecognitionNotAvailable | GoogleVoiceTypingDisabledException exc) {
                Log.e(TAG, "Speech recognition is not available on this device!");
                // You can prompt the user if he wants to install Google App to have
                // speech recognition, and then you can simply call:
                try {
                    SpeechUtil.redirectUserToGoogleAppOnPlayStore(getContext());
                } catch (ActivityNotFoundException | NullPointerException e) {
                    // NullPointerException: android.os.Parcel.readException (Parcel.java:1478)
                    e.printStackTrace();
                }
            }
        } else {
            Log.e(TAG, "Can't perform search. Fragment is detached.");
        }
    };
}