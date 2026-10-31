package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.disposables.Disposable;

public class MediaServiceSearchTagProvider implements SearchTagsProvider {
    private static final String TAG = MediaServiceSearchTagProvider.class.getSimpleName();
    private final ContentService mContentService;
    private final boolean mIgnoreEmptyQuery;
    private Disposable mTagsAction;

    public MediaServiceSearchTagProvider(boolean ignoreEmptyQuery) {
        mIgnoreEmptyQuery = ignoreEmptyQuery;
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
                        tags -> callback.onResults(Tag.from(tags)),
                        error -> Log.e(TAG, "Result is empty. Just ignore it. Error msg: %s", error.getMessage())
                );
    }
}
