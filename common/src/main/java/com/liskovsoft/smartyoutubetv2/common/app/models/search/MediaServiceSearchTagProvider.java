package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import com.liskovsoft.mediaserviceinterfaces.MediaGroupService;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.disposables.Disposable;

public class MediaServiceSearchTagProvider implements SearchTagsProvider {
    private static final String TAG = MediaServiceSearchTagProvider.class.getSimpleName();
    private final MediaGroupService mGroupManager;
    private Disposable mTagsAction;

    public MediaServiceSearchTagProvider() {
        MediaService mediaService = YouTubeMediaService.instance();
        mGroupManager = mediaService.getMediaGroupService();
    }

    @Override
    public void search(String query, ResultsCallback callback) {
        RxHelper.disposeActions(mTagsAction);

        mTagsAction = mGroupManager.getSearchTagsObserve(query)
                .subscribe(
                        tags -> callback.onResults(Tag.from(tags)),
                        error -> Log.e(TAG, "Result is empty. Just ignore it. Error msg: %s", error.getMessage())
                );
    }
}
