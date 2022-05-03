package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MediaServiceSearchTagProvider implements SearchTagsProvider {
    private static final String TAG = MediaServiceSearchTagProvider.class.getSimpleName();
    private final MediaGroupManager mGroupManager;
    private Disposable mTagsAction;

    public MediaServiceSearchTagProvider() {
        MediaService mediaService = YouTubeMediaService.instance();
        mGroupManager = mediaService.getMediaGroupManager();
    }

    @Override
    public void search(String query, ResultsCallback callback) {
        RxUtils.disposeActions(mTagsAction);

        mTagsAction = mGroupManager.getSearchTagsObserve(query)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        tags -> callback.onResults(Tag.from(tags)),
                        error -> Log.e(TAG, "Result is empty. Just ignore it. Error msg: %s", error.getMessage())
                );
    }
}
