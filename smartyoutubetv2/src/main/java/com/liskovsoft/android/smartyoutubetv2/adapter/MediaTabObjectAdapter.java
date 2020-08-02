package com.liskovsoft.android.smartyoutubetv2.adapter;

import androidx.leanback.widget.ObjectAdapter;
import com.liskovsoft.android.smartyoutubetv2.model.Video;
import com.liskovsoft.android.smartyoutubetv2.presenter.CardPresenter;
import com.liskovsoft.mediaserviceinterfaces.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.MediaTab;

import java.util.List;

public class MediaTabObjectAdapter extends ObjectAdapter {
    private static final String TAG = MediaTabObjectAdapter.class.getSimpleName();
    private final List<MediaItem> mMediaItems;
    private final MediaTab mMediaTab;

    public MediaTabObjectAdapter(MediaTab mediaTab) {
        super(new CardPresenter());
        mMediaTab = mediaTab;
        mMediaItems = mediaTab.getMediaItems();
    }

    @Override
    public int size() {
        return mMediaItems.size();
    }

    @Override
    public Object get(int position) {
        MediaItem mediaItem = mMediaItems.get(position);
        long id = mediaItem.getId();
        String title = mediaItem.getTitle();
        String category = mediaItem.getContentType();
        String desc = mediaItem.getDescription();
        String videoUrl = mediaItem.getVideoUrl();
        String bgImageUrl = mediaItem.getBackgroundImageUrl();
        String cardImageUrl = mediaItem.getCardImageUrl();
        String studio = mediaItem.getDescription();

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

    public void append(MediaTab mediaTab) {
        if (mMediaItems != null && mediaTab != null) {
            mMediaItems.addAll(mediaTab.getMediaItems());
        }
    }

    public MediaTab getMediaTab() {
        return mMediaTab;
    }
}
