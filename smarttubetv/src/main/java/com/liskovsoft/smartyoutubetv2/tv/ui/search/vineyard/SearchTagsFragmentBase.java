package com.liskovsoft.smartyoutubetv2.tv.ui.search.vineyard;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.SpeechRecognitionCallback;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.vineyard.PaginationAdapter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.vineyard.TagAdapter;
import com.liskovsoft.smartyoutubetv2.tv.model.PrefsSearchTagsProvider;
import com.liskovsoft.smartyoutubetv2.tv.model.SearchTagsProvider;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.SearchSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.vineyard.NetworkUtil;
import com.liskovsoft.smartyoutubetv2.tv.util.vineyard.ToastFactory;

public abstract class SearchTagsFragmentBase extends SearchSupportFragment
        implements SearchSupportFragment.SearchResultProvider, SearchView {
    private static final String TAG = SearchTagsFragmentBase.class.getSimpleName();
    private static final int REQUEST_SPEECH = 0x00000010;

    private DisplayMetrics mMetrics;
    private Handler mHandler;
    private HeaderItem mResultsHeader;
    private Object mSelectedTag;
    private TagAdapter mSearchTagsAdapter;
    private ObjectAdapter mItemResultsAdapter;
    private ArrayObjectAdapter mResultsAdapter;

    private String mSearchQuery;
    private String mTagSearchAnchor;
    private String mUserSearchAnchor;
    private boolean mIsStopping;
    private SearchTagsProvider mSearchTagsProvider;
    private UriBackgroundManager mBackgroundManager;
    private ProgressBarManager mProgressBarManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();
        mResultsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        mSearchTagsAdapter = new TagAdapter(getActivity(), "");
        mSearchTagsProvider = new PrefsSearchTagsProvider();
        mProgressBarManager = new ProgressBarManager();
        mHandler = new Handler();
        setSearchResultProvider(this);
        setupMetrics();
        setupListeners();
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
                        setSearchQuery(data, false);
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
    public boolean onQueryTextChange(String newQuery) {
        loadQuery(newQuery);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        loadQuery(query);
        return true;
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
    public void clearSearch() {
        //mResultsAdapter.clear();
        //mSearchTagsAdapter.clear();
    }

    protected void setItemResultsAdapter(ObjectAdapter adapter) {
        mItemResultsAdapter = adapter;
    }

    public boolean isStopping() {
        return mIsStopping;
    }

    public boolean hasResults() {
        return mResultsAdapter.size() > 0;
    }

    private void setupMetrics() {
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupListeners() {
        //setOnItemViewClickedListener(mOnItemViewClickedListener);
        //setOnItemViewSelectedListener(mOnItemViewSelectedListener);
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            setSpeechRecognitionCallback(new SpeechRecognitionCallback() {
                @Override
                public void recognizeSpeech() {
                    try {
                        startActivityForResult(getRecognizerIntent(), REQUEST_SPEECH);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "Cannot find activity for speech recognizer", e);
                    }
                }
            });
        }
    }

    private boolean hasPermission(final String permission) {
        final Context context = getActivity();
        return PackageManager.PERMISSION_GRANTED == context.getPackageManager().checkPermission(
                permission, context.getPackageName());
    }

    protected void loadQuery(String query) {
        if ((mSearchQuery != null && !mSearchQuery.equals(query))
                && query.trim().length() > 0
                || (!TextUtils.isEmpty(query) && !query.equals("nil"))) {
            if (NetworkUtil.isNetworkConnected(getActivity())) {
                mSearchQuery = query;
                searchTaggedPosts(query);
            } else {
                ToastFactory.createWifiErrorToast(getActivity()).show();
            }
        }
    }

    private void searchTaggedPosts(String tag) {
        mSearchTagsAdapter.setTag(tag);
        mResultsAdapter.clear();
        mResultsHeader = new HeaderItem(0, getString(R.string.text_search_results));
        ListRow listRow = new ListRow(mResultsHeader, mSearchTagsAdapter);
        mResultsAdapter.add(listRow);
        mResultsAdapter.add(new ListRow(mItemResultsAdapter));
        performSearch(mSearchTagsAdapter);
    }

    private void performSearch(PaginationAdapter adapter) {
        String query = adapter.getAdapterOptions().get(PaginationAdapter.KEY_TAG);
        mSearchTagsProvider.search(query, adapter::addAllItems);
    }

    //private void performSearch(final PaginationAdapter adapter) {
    //    if (adapter.shouldShowLoadingIndicator()) {
    //        adapter.showLoadingIndicator();
    //    }
    //    if (mPostResultsAdapter != null) mPostResultsAdapter.clear();
    //    adapter.clear();
    //
    //    Map<String, String> options = adapter.getAdapterOptions();
    //    String tag = options.get(PaginationAdapter.KEY_TAG);
    //    String nextPage = options.get(PaginationAdapter.KEY_NEXT_PAGE);
    //
    //    Observable<VineyardService.KeywordSearchResponse> observable =
    //            mDataManager.search(
    //                    tag, nextPage, mTagSearchAnchor, nextPage, mUserSearchAnchor);
    //
    //    mSearchResultsSubscription = observable
    //            .observeOn(AndroidSchedulers.mainThread())
    //            .subscribeOn(Schedulers.io())
    //            .unsubscribeOn(Schedulers.io())
    //            .subscribe(new Subscriber<VineyardService.KeywordSearchResponse>() {
    //                @Override
    //                public void onCompleted() {
    //                    adapter.removeLoadingIndicator();
    //                }
    //
    //                @Override
    //                public void onError(Throwable e) {
    //                    //TODO: Handle error
    //                    adapter.removeLoadingIndicator();
    //                    Toast.makeText(
    //                            getActivity(),
    //                            getString(R.string.error_message_retrieving_results),
    //                            Toast.LENGTH_SHORT
    //                    ).show();
    //                    Timber.e("There was an error loading the videos", e);
    //                }
    //
    //                @Override
    //                public void onNext(VineyardService.KeywordSearchResponse dualResponse) {
    //                    if (dualResponse.list.isEmpty()) {
    //                        mResultsAdapter.clear();
    //                        mResultsHeader = new HeaderItem(0, getString(R.string.text_no_results));
    //                        mResultsAdapter.add(new ListRow(mResultsHeader, adapter));
    //                        mTagSearchAnchor = "";
    //                        mUserSearchAnchor = "";
    //                    } else {
    //                        adapter.addAllItems(dualResponse.list);
    //                        mTagSearchAnchor = dualResponse.tagSearchAnchor;
    //                        mUserSearchAnchor = dualResponse.userSearchAnchor;
    //                    }
    //                }
    //            });
    //}

    //private void addPageLoadSubscriptionByTag(final PaginationAdapter adapter) {
    //    if (adapter.shouldShowLoadingIndicator()) adapter.showLoadingIndicator();
    //
    //    Map<String, String> options = adapter.getAdapterOptions();
    //    String tag = options.get(PaginationAdapter.KEY_TAG);
    //    String anchor = options.get(PaginationAdapter.KEY_ANCHOR);
    //    String nextPage = options.get(PaginationAdapter.KEY_NEXT_PAGE);
    //
    //    mTagSubscription = mDataManager.getPostsByTag(tag, nextPage, anchor)
    //            .observeOn(AndroidSchedulers.mainThread())
    //            .subscribeOn(Schedulers.io())
    //            .unsubscribeOn(Schedulers.io())
    //            .subscribe(new Subscriber<VineyardService.PostResponse>() {
    //                @Override
    //                public void onCompleted() {
    //                }
    //
    //                @Override
    //                public void onError(Throwable e) {
    //                    adapter.removeLoadingIndicator();
    //                    if (adapter.size() == 0) {
    //                        adapter.showTryAgainCard();
    //                    } else {
    //                        Toast.makeText(
    //                                getActivity(),
    //                                getString(R.string.error_message_loading_more_posts),
    //                                Toast.LENGTH_SHORT
    //                        ).show();
    //                    }
    //                    Timber.e("There was an error loading the posts", e);
    //                }
    //
    //                @Override
    //                public void onNext(VineyardService.PostResponse postResponse) {
    //                    adapter.removeLoadingIndicator();
    //                    if (adapter.size() == 0 && postResponse.data.records.isEmpty()) {
    //                        adapter.showReloadCard();
    //                    } else {
    //                        adapter.setAnchor(postResponse.data.anchorStr);
    //                        adapter.setNextPage(postResponse.data.nextPage);
    //                        adapter.addAllItems(postResponse.data.records);
    //                    }
    //                }
    //            });
    //}

    //private void addPageLoadSubscriptionByUser(final PaginationAdapter adapter) {
    //    if (adapter.shouldShowLoadingIndicator()) adapter.showLoadingIndicator();
    //
    //    Map<String, String> options = adapter.getAdapterOptions();
    //    String tag = options.get(PaginationAdapter.KEY_TAG);
    //    String anchor = options.get(PaginationAdapter.KEY_ANCHOR);
    //    String nextPage = options.get(PaginationAdapter.KEY_NEXT_PAGE);
    //
    //    mUserSubscription = mDataManager.getPostsByUser(tag, nextPage, anchor)
    //            .observeOn(AndroidSchedulers.mainThread())
    //            .subscribeOn(Schedulers.io())
    //            .unsubscribeOn(Schedulers.io())
    //            .subscribe(new Subscriber<VineyardService.PostResponse>() {
    //                @Override
    //                public void onCompleted() {
    //                }
    //
    //                @Override
    //                public void onError(Throwable e) {
    //                    adapter.removeLoadingIndicator();
    //                    if (adapter.size() == 0) {
    //                        adapter.showTryAgainCard();
    //                    } else {
    //                        Toast.makeText(
    //                                getActivity(),
    //                                getString(R.string.error_message_loading_more_posts),
    //                                Toast.LENGTH_SHORT
    //                        ).show();
    //                    }
    //                    Timber.e("There was an error loading the posts", e);
    //                }
    //
    //                @Override
    //                public void onNext(VineyardService.PostResponse postResponse) {
    //                    adapter.removeLoadingIndicator();
    //                    if (adapter.size() == 0 && postResponse.data.records.isEmpty()) {
    //                        adapter.showReloadCard();
    //                    } else {
    //                        adapter.setAnchor(postResponse.data.anchorStr);
    //                        adapter.setNextPage(postResponse.data.nextPage);
    //                        adapter.addAllItems(postResponse.data.records);
    //                    }
    //                }
    //            });
    //}

    //private void showNetworkUnavailableToast() {
    //    ToastFactory.createWifiErrorToast(getActivity()).show();
    //}
    //
    //private void setListAdapterData(String title, String tag) {
    //    if (mPostResultsAdapter != null) {
    //        mResultsAdapter.remove(mPostResultsAdapter);
    //    }
    //    if (mPostResultsAdapter == null) {
    //        mPostResultsAdapter = new PostAdapter(getActivity(), tag);
    //    }
    //    mResultsAdapter.removeItems(1, 1);
    //    final HeaderItem postResultsHeader = new HeaderItem(1, getString(R.string.text_post_results_title, title));
    //    new Handler().post(new Runnable() {
    //        @Override
    //        public void run() {
    //            mResultsAdapter.add(new ListRow(postResultsHeader, mPostResultsAdapter));
    //        }
    //    });
    //
    //    mPostResultsAdapter.setTag(tag);
    //    mPostResultsAdapter.setAnchor("");
    //    mPostResultsAdapter.setNextPage(1);
    //    if (!mPostResultsAdapter.shouldShowLoadingIndicator()) {
    //        mPostResultsAdapter.removeItems(1, mPostResultsAdapter.size() - 2);
    //    } else {
    //        mPostResultsAdapter.clear();
    //    }
    //}

    //private OnItemViewClickedListener mOnItemViewClickedListener = new OnItemViewClickedListener() {
    //    @Override
    //    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
    //                              RowPresenter.ViewHolder rowViewHolder, Row row) {
    //        if (item instanceof Post) {
    //            if (NetworkUtil.isNetworkConnected(getActivity())) {
    //                Post post = (Post) item;
    //                int index = mResultsAdapter.indexOf(row);
    //                PostAdapter arrayObjectAdapter =
    //                        ((PostAdapter) ((ListRow) mResultsAdapter.get(index)).getAdapter());
    //                ArrayList<Post> postList = (ArrayList<Post>) arrayObjectAdapter.getAllItems();
    //                startActivity(PlaybackActivity.newStartIntent(getActivity(), post, postList));
    //            } else {
    //                showNetworkUnavailableToast();
    //            }
    //        } else if (item instanceof Tag) {
    //            if (NetworkUtil.isNetworkConnected(getActivity())) {
    //                Tag tag = (Tag) item;
    //                startActivity(PostGridActivity.getStartIntent(getActivity(), tag));
    //            } else {
    //                showNetworkUnavailableToast();
    //            }
    //        } else if (item instanceof User) {
    //            if (NetworkUtil.isNetworkConnected(getActivity())) {
    //                User user = (User) item;
    //                startActivity(PostGridActivity.getStartIntent(getActivity(), user));
    //            } else {
    //                showNetworkUnavailableToast();
    //            }
    //        } else if (item instanceof Option) {
    //            Option option = (Option) item;
    //            if (option.title.equals(getString(R.string.message_check_again)) ||
    //                    option.title.equals(getString(R.string.message_try_again))) {
    //                int index = mResultsAdapter.indexOf(row);
    //                PostAdapter adapter =
    //                        ((PostAdapter) ((ListRow) mResultsAdapter.get(index)).getAdapter());
    //                adapter.removeReloadCard();
    //                if (mSelectedTag instanceof Tag) {
    //                    addPageLoadSubscriptionByTag(adapter);
    //                } else if (mSelectedTag instanceof User) {
    //                    addPageLoadSubscriptionByUser(adapter);
    //                }
    //            }
    //        }
    //    }
    //};

    //private OnItemViewSelectedListener mOnItemViewSelectedListener = new OnItemViewSelectedListener() {
    //    @Override
    //    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
    //                               RowPresenter.ViewHolder rowViewHolder, Row row) {
    //        if (item instanceof Tag || item instanceof User) {
    //            boolean isValid = true;
    //            if (mSelectedTag != null && mSelectedTag.equals(item)) isValid = false;
    //            mSelectedTag = item;
    //            if (isValid) {
    //                int index = mResultsAdapter.indexOf(row);
    //                PaginationAdapter adapter =
    //                        ((PaginationAdapter) ((ListRow) mResultsAdapter.get(index)).getAdapter());
    //
    //                if (item instanceof Tag) {
    //                    Tag tagOne = (Tag) item;
    //                    String tag = tagOne.tag;
    //                    adapter.setTag(tag);
    //
    //                    setListAdapterData(tag, tag);
    //                    addPageLoadSubscriptionByTag(mPostResultsAdapter);
    //                } else {
    //                    User user = (User) item;
    //                    String userId = user.userId;
    //                    adapter.setTag(userId);
    //
    //                    setListAdapterData(user.username, userId);
    //                    addPageLoadSubscriptionByUser(mPostResultsAdapter);
    //                }
    //            }
    //        } else if (item instanceof Post) {
    //            String backgroundUrl = ((Post) item).thumbnailUrl;
    //            if (backgroundUrl != null) startBackgroundTimer(URI.create(backgroundUrl));
    //        }
    //    }
    //};

}