package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.misc.BrowseProcessorManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class SearchPresenter extends BasePresenter<SearchView> implements VideoGroupPresenter {
    private static final String TAG = SearchPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SearchPresenter sInstance;
    private final BrowseProcessorManager mBrowseProcessor;
    private Disposable mScrollAction;
    private Disposable mLoadAction;
    private String mSearchText;
    private boolean mIsVoice;
    private boolean mStartPlay;
    private int mUploadDateOptions;
    private int mDurationOptions;
    private int mTypeOptions;
    private int mFeatureOptions;
    private int mSortingOptions;

    private SearchPresenter(Context context) {
        super(context);
        mBrowseProcessor = new BrowseProcessorManager(getContext(), this::syncItem);
    }

    public static SearchPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SearchPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        if (!AccountsData.instance(getContext()).isPasswordAccepted()) {
            getView().finishReally();
            return;
        }

        getView().setTagsProvider(new MediaServiceSearchTagProvider(getSearchData().isSearchHistoryDisabled()));

        startSearchInt();
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        disposeActions();
    }

    @Override
    public void onFinish() {
        super.onFinish();

        mSearchText = null;
        mUploadDateOptions = 0;
        mDurationOptions = 0;
        mTypeOptions = 0;
        mFeatureOptions = 0;
        mSortingOptions = 0;
        mIsVoice = false;
    }

    @Override
    public void onVideoItemSelected(Video item) {
        // NOP
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (getView() == null) {
            return;
        }

        VideoActionPresenter.instance(getContext()).apply(item);
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (getView() == null) {
            return;
        }

        VideoMenuPresenter.instance(getContext()).showMenu(item);
    }

    public void onTagLongClicked(Tag item) {
        if (getView() == null) {
            return;
        }

        AppDialogUtil.showConfirmationDialog(
                getContext(),
                getContext().getString(R.string.clear_search_history),
                () -> {
                    MediaServiceManager.instance().clearSearchHistory();
                    getView().clearSearchTags();
                });
    }

    @Override
    public boolean hasPendingActions() {
        return RxHelper.isAnyActionRunning(mLoadAction, mScrollAction);
    }

    public void onSearch(String searchText) {
        // Restore the search in case the view unloaded from the memory
        mSearchText = searchText;

        if (getView() == null) {
            Log.e(TAG, "Search view has been unloaded from the memory. Low RAM?");
            startSearch(searchText);
            return;
        }

        loadSearchResultAlt(searchText);
    }
    
    private void loadSearchResult(String searchText) {
        Log.d(TAG, "Start search for '%s'", searchText);

        disposeActions();
        getView().showProgressBar(true);

        ContentService contentService = getContentService();

        getView().clearSearch();

        mLoadAction = contentService.getSearchObserve(searchText,
                mUploadDateOptions | mDurationOptions | mTypeOptions | mFeatureOptions | mSortingOptions)
                .subscribe(
                        mediaGroup -> {
                            Log.d(TAG, "Receiving results for '%s'", searchText);
                            getView().updateSearch(VideoGroup.from(mediaGroup));
                        },
                        error -> Log.e(TAG, "loadSearchData error: %s", error.getMessage()),
                        () -> getView().showProgressBar(false)
                );
    }

    private void loadSearchResultAlt(String searchText) {
        Log.d(TAG, "Start search for '%s'", searchText);

        disposeActions();
        getView().showProgressBar(true);

        ContentService contentService = getContentService();

        getView().clearSearch();

        mLoadAction = contentService.getSearchAltObserve(searchText,
                mUploadDateOptions | mDurationOptions | mTypeOptions | mFeatureOptions | mSortingOptions)
                .subscribe(
                        mediaGroups -> {
                            Log.d(TAG, "Receiving results for '%s'", searchText);
                            for (MediaGroup mediaGroup : mediaGroups) {
                                VideoGroup group = VideoGroup.from(mediaGroup);
                                startPlayFirstVideo(group);
                                getView().updateSearch(group);
                                mBrowseProcessor.process(group);
                            }
                        },
                        error -> {
                            Log.e(TAG, "loadSearchData error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        },
                        () -> {
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        }
                );
    }

    private void continueGroup(VideoGroup group) {
        if (RxHelper.isAnyActionRunning(mScrollAction)) {
            return;
        }

        if (getView() == null) {
            return;
        }

        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        ContentService contentService = getContentService();

        mScrollAction = contentService.continueGroupObserve(mediaGroup)
                .subscribe(
                        continueMediaGroup -> {
                            VideoGroup newGroup = VideoGroup.from(group, continueMediaGroup);
                            getView().updateSearch(newGroup);
                            mBrowseProcessor.process(newGroup);
                        },
                        error -> {
                            Log.e(TAG, "continueGroup error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        },
                        () -> {
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        }
                );
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        if (item.getGroup() == null) {
            Log.e(TAG, "Can't scroll. Video group is null.");
            return;
        }

        VideoGroup group = item.getGroup();

        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());

        continueGroup(group);
    }

    public void startVoice() {
        startSearch(null, true, false);
    }

    public void startSearch(String searchText) {
        startSearch(searchText, false, false);
    }

    public void startPlay(String searchText) {
        startSearch(searchText, false, true);
    }

    private void startSearch(String searchText, boolean isVoice, boolean startPlay) {
        mSearchText = searchText;
        mIsVoice = isVoice;
        mStartPlay = startPlay;

        getViewManager().startView(SearchView.class);
        startSearchInt();
    }

    private void startSearchInt() {
        if (getView() == null) {
            return;
        }

        if ((mIsVoice || getSearchData().isInstantVoiceSearchEnabled()) && mSearchText == null) {
            getView().startVoiceRecognition();
        } else {
            getView().startSearch(mSearchText);
        }
    }

    public void onSearchSettingsClicked() {
        if (getView() == null) {
            return;
        }

        showSettingsDialog();
    }

    public void disposeActions() {
        RxHelper.disposeActions(mLoadAction, mScrollAction);
        if (getView() != null) {
            getView().showProgressBar(false);
        }
        if (getSearchData().isSearchHistoryDisabled()) {
            MediaServiceManager.instance().clearSearchHistory();
        }
        mBrowseProcessor.dispose();
    }

    private void showSettingsDialog() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendFilterByDateCategory(settingsPresenter);
        appendFilterByDurationCategory(settingsPresenter);
        appendFilterByTypeCategory(settingsPresenter);
        appendFilterByFeatureCategory(settingsPresenter);
        appendSortByCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_search));
    }

    private void appendFilterByDateCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.upload_date_any, 0},
                {R.string.upload_date_last_hour, SearchOptions.UPLOAD_DATE_LAST_HOUR},
                {R.string.upload_date_today, SearchOptions.UPLOAD_DATE_TODAY},
                {R.string.upload_date_this_week, SearchOptions.UPLOAD_DATE_THIS_WEEK},
                {R.string.upload_date_this_month, SearchOptions.UPLOAD_DATE_THIS_MONTH},
                {R.string.upload_date_this_year, SearchOptions.UPLOAD_DATE_THIS_YEAR}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> {
                        mUploadDateOptions = pair[1];
                        loadSearchResult();
                    },
                    mUploadDateOptions == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.upload_date), options);
    }

    private void appendFilterByDurationCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.video_duration_any, 0},
                {R.string.video_duration_under_4, SearchOptions.DURATION_UNDER_4},
                {R.string.video_duration_between_4_20, SearchOptions.DURATION_BETWEEN_4_20},
                {R.string.video_duration_over_20, SearchOptions.DURATION_OVER_20}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> {
                        mDurationOptions = pair[1];
                        loadSearchResult();
                    },
                    mDurationOptions == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.video_duration), options);
    }

    private void appendFilterByTypeCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.content_type_any, 0},
                {R.string.content_type_video, SearchOptions.TYPE_VIDEO},
                {R.string.content_type_channel, SearchOptions.TYPE_CHANNEL},
                {R.string.content_type_playlist, SearchOptions.TYPE_PLAYLIST},
                {R.string.content_type_movie, SearchOptions.TYPE_MOVIE}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> {
                        mTypeOptions = pair[1];
                        loadSearchResult();
                    },
                    mTypeOptions == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.content_type), options);
    }

    private void appendFilterByFeatureCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.video_feature_live, SearchOptions.FEATURE_LIVE},
                {R.string.video_feature_4k, SearchOptions.FEATURE_4K},
                {R.string.video_feature_hdr, SearchOptions.FEATURE_HDR}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> {
                        mFeatureOptions = optionItem.isSelected() ? mFeatureOptions | pair[1] : mFeatureOptions & ~pair[1];
                        loadSearchResult();
                    },
                    (mFeatureOptions & pair[1]) == pair[1]));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.video_features), options);
    }

    private void appendSortByCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.sort_by_relevance, 0},
                {R.string.sort_by_date, SearchOptions.SORT_BY_UPLOAD_DATE},
                {R.string.sort_by_views, SearchOptions.SORT_BY_VIEW_COUNT},
                {R.string.sort_by_rating, SearchOptions.SORT_BY_RATING}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> {
                        mSortingOptions = pair[1];
                        loadSearchResult();
                    },
                    mSortingOptions == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.search_sorting), options);
    }

    public void forceFinish() {
        if (getView() != null) {
            getView().finishReally();
        }
    }

    private void startPlayFirstVideo(VideoGroup group) {
        if (!mStartPlay || group == null || group.isEmpty()) {
            return;
        }

        mStartPlay = false;

        for (Video video : group.getVideos()) {
            if (video.videoId != null) {
                PlaybackPresenter.instance(getContext()).openVideo(video);
                break;
            }
        }
    }

    private void loadSearchResult() {
        if (getView() == null) {
            return;
        }

        String searchText = getView().getSearchText();

        if (searchText != null && !searchText.isEmpty()) {
            loadSearchResultAlt(searchText);
        }
    }
}
