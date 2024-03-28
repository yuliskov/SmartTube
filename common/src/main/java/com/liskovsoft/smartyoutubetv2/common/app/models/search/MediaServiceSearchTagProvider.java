package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import com.liskovsoft.mediaserviceinterfaces.yt.ContentService;
import com.liskovsoft.mediaserviceinterfaces.yt.MotherService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMotherService;
import io.reactivex.disposables.Disposable;

public class MediaServiceSearchTagProvider implements SearchTagsProvider {
    private static final String TAG = MediaServiceSearchTagProvider.class.getSimpleName();
    private final ContentService mContentService;
    private Disposable mTagsAction;

    public MediaServiceSearchTagProvider() {
        MotherService service = YouTubeMotherService.instance();
        mContentService = service.getContentService();
    }

    @Override
    public void search(String query, ResultsCallback callback) {
        RxHelper.disposeActions(mTagsAction);

        mTagsAction = mContentService.getSearchTagsObserve(query)
                .subscribe(
                        tags -> callback.onResults(Tag.from(tags)),
                        error -> Log.e(TAG, "Result is empty. Just ignore it. Error msg: %s", error.getMessage())
                );
    }
}
