package com.liskovsoft.android.smartyoutubetv2.adapter;

import androidx.leanback.widget.ObjectAdapter;
import com.liskovsoft.android.smartyoutubetv2.model.Video;
import com.liskovsoft.android.smartyoutubetv2.presenter.CardPresenter;
import com.liskovsoft.mediaserviceinterfaces.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.MediaTab;

import java.util.List;

public class MediaTabObjectAdapter extends ObjectAdapter {
    private static final String TAG = MediaTabObjectAdapter.class.getSimpleName();
    private final MediaTab mMediaTab;
    private final int mTabIndex;

    public MediaTabObjectAdapter(MediaTab mediaTab) {
        this(mediaTab, 0);
    }

    public MediaTabObjectAdapter(MediaTab mediaTab, int tabIndex) {
        super(new CardPresenter());
        mMediaTab = mediaTab;
        mTabIndex = tabIndex;
    }

    @Override
    public int size() {
        return mMediaTab.getMediaItems().size();
    }

    @Override
    public Object get(int position) {
        MediaItem video = mMediaTab.getMediaItems().get(position);
        long id = video.getId();
        String title = video.getTitle();
        String category = video.getContentType();
        String desc = video.getDescription();
        String videoUrl = video.getVideoUrl();
        String bgImageUrl = video.getBackgroundImageUrl();
        String cardImageUrl = video.getCardImageUrl();
        String studio = video.getDescription();

        // Build a Video object to be processed.
        return new Video.VideoBuilder()
                .id(id)
                .title(title)
                .category(category)
                .description(desc)
                .videoUrl(videoUrl)
                .bgImageUrl(bgImageUrl)
                .cardImageUrl(cardImageUrl)
                .studio(studio)
                .build();
    }

    public int getTabIndex() {
        return mTabIndex;
    }

    public void addAll(List<MediaItem> videos) {
        if (mMediaTab != null) {
            mMediaTab.getMediaItems().addAll(videos);
        }
    }

    public MediaTab getMediaTab() {
        return mMediaTab;
    }
}
