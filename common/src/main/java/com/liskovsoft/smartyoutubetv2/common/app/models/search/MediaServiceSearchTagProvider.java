package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class MediaServiceSearchTagProvider implements SearchTagsProvider {
    private static final String TAG = MediaServiceSearchTagProvider.class.getSimpleName();
    private final ContentService mContentService;
    private final boolean mIgnoreEmptyQuery;
    private final SearchData mSearchData;
    private Disposable mTagsAction;

    public MediaServiceSearchTagProvider(SearchData searchData) {
        mSearchData = searchData;
        mIgnoreEmptyQuery = searchData.isSearchHistoryDisabled();
        ServiceManager service = YouTubeServiceManager.instance();
        mContentService = service.getContentService();
    }

    @Override
    public void search(String query, ResultsCallback callback) {
        RxHelper.disposeActions(mTagsAction);

        if (mIgnoreEmptyQuery && TextUtils.isEmpty(query)) {
            callback.onResults(null);
            return;
        }

        mTagsAction = mContentService.getSearchTagsObserve(query)
                .subscribe(
                        tags -> callback.onResults(Tag.from(removeHidden(tags))),
                        error -> Log.e(TAG, "Result is empty. Just ignore it. Error msg: %s", error.getMessage())
                );
    }

    private List<String> removeHidden(List<String> tags) {
        if (tags == null) {
            return null;
        }

        List<String> result = new ArrayList<>();

        for (String tag : tags) {
            if (!mSearchData.isSearchTagHidden(tag)) {
                result.add(tag);
            }
        }

        return result;
    }
}
