package com.liskovsoft.smartyoutubetv2.tv.ui.search.regular;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SpeechRecognitionCallback;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemViewPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SearchSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class SearchFragment extends SearchSupportFragment
        implements SearchSupportFragment.SearchResultProvider, SearchView {
    private static final String TAG = SearchFragment.class.getSimpleName();
    private static final boolean FINISH_ON_RECOGNIZER_CANCELED = true;
    private static final int REQUEST_SPEECH = 0x00000010;

    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mRowsAdapter;
    private String mQuery;
    private boolean mResultsFound = false;
    private SearchPresenter mSearchPresenter;
    private UriBackgroundManager mBackgroundManager;
    private VideoGroupObjectAdapter mAdapter;
    private ProgressBarManager mProgressBarManager;
    private VideoCardPresenter mCardPresenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();
        mSearchPresenter = SearchPresenter.instance(getContext());
        mSearchPresenter.setView(this);
        mCardPresenter = new VideoCardPresenter();

        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        mProgressBarManager = new ProgressBarManager();

        setSearchResultProvider(this);
        setupEventListeners();

        // TODO: move permission acquirement to presenter
        Log.d(TAG, "User is initiating a search. Do we have RECORD_AUDIO permission? " +
                hasPermission(Manifest.permission.RECORD_AUDIO));
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            Log.d(TAG, "Does not have RECORD_AUDIO, using SpeechRecognitionCallback");

            // SpeechRecognitionCallback is not required and if not provided recognition will be
            // handled using internal speech recognizer, in which case you must have RECORD_AUDIO
            // permission
            setSpeechRecognitionCallback(new SpeechRecognitionCallback() {
                @Override
                public void recognizeSpeech() {
                    if (isAdded()) {
                        try {
                            startActivityForResult(getRecognizerIntent(), REQUEST_SPEECH);
                        } catch (ActivityNotFoundException e) {
                            Log.e(TAG, "Cannot find activity for speech recognizer", e);
                        }
                    } else {
                        Log.e(TAG, "Can't perform search. Fragment is detached.");
                    }
                }
            });
        } else {
            Log.d(TAG, "We DO have RECORD_AUDIO");
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSearchPresenter.onViewInitialized();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mProgressBarManager.setRootView((ViewGroup) root);

        return root;
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        mCardPresenter.setOnItemViewLongPressedListener(new ItemViewLongClickedListener());
        mCardPresenter.setOnItemViewMenuPressedListener(new ItemViewLongClickedListener());
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacksAndMessages(null);

        // fix Service not registered: android.speech.SpeechRecognizer$Connection
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSearchPresenter.onViewDestroyed();
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mRowsAdapter;
    }

    @Override
    public boolean onQueryTextChange(String newQuery) {
        Log.i(TAG, String.format("Search text changed: %s", newQuery));
        //loadQuery(newQuery);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        Log.i(TAG, String.format("Search text submitted: %s", query));
        loadQuery(query);
        return true;
    }

    public boolean hasResults() {
        return mRowsAdapter.size() > 0 && mResultsFound;
    }

    private boolean hasPermission(final String permission) {
        final Context context = getActivity();
        return PackageManager.PERMISSION_GRANTED == context.getPackageManager().checkPermission(
                permission, context.getPackageName());
    }

    private void loadQuery(String query) {
        if (!TextUtils.isEmpty(query) && !query.equals("nil")) {
            mQuery = query;
            mSearchPresenter.onSearch(query);
        }
    }

    public void focusOnSearch() {
        getView().findViewById(R.id.lb_search_bar).requestFocus();
    }

    @Override
    public void clearSearch() {
        mRowsAdapter.clear();
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }

    @Override
    public void updateSearch(VideoGroup group) {
        int titleRes;
        if (group.getVideos() != null) {
            mResultsFound = true;
            titleRes = R.string.search_results;
        } else {
            mResultsFound = false;
            titleRes = R.string.no_search_results;
        }

        if (mRowsAdapter.size() == 0) {
            HeaderItem header = new HeaderItem(getString(titleRes, mQuery));
            mAdapter = new VideoGroupObjectAdapter(group, mCardPresenter);
            ListRow row = new ListRow(header, mAdapter);
            mRowsAdapter.add(row);
        } else {
            mAdapter.append(group);
        }
    }

    private final class ItemViewLongClickedListener implements OnItemViewPressedListener {
        @Override
        public void onItemPressed(Presenter.ViewHolder itemViewHolder, Object item) {

            if (item instanceof Video) {
                mSearchPresenter.onVideoItemLongClicked((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewClickedListener implements androidx.leanback.widget.OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                mSearchPresenter.onVideoItemClicked((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                mBackgroundManager.setBackgroundFrom((Video) item);

                checkScrollEnd((Video) item);
            }
        }

        private void checkScrollEnd(Video item) {
            int size = mAdapter.size();
            int index = mAdapter.indexOf(item);

            if (index > (size - ViewUtil.ROW_SCROLL_CONTINUE_NUM)) {
                mSearchPresenter.onScrollEnd((Video) mAdapter.get(size - 1));
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SPEECH:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        setSearchQuery(data, true);
                        break;
                    default:
                        // If recognizer is canceled or failed, keep focus on the search orb
                        if (FINISH_ON_RECOGNIZER_CANCELED) {
                            if (!hasResults()) {
                                Log.i(TAG, "Voice search canceled");
                                getView().findViewById(R.id.lb_search_bar_speech_orb).requestFocus();
                            }
                        }
                        break;
                }
                break;
        }
    }

    @Override
    public void startSearch(String searchText) {
        startSearch(searchText, false);
    }

    @Override
    public String getSearchText() {
        // NOP
        return null;
    }

    @Override
    public void startVoiceRecognition() {
        startSearch(null, true);
    }

    private void startSearch(String searchText, boolean enableRecognition) {
        if (searchText != null) {
            setSearchQuery(searchText, true);
        } else if (enableRecognition) {
            startRecognition();
        }
    }
}
