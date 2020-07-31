package com.liskovsoft.android.smartyoutubetv2.adapter;

import androidx.leanback.widget.ObjectAdapter;
import com.liskovsoft.android.smartyoutubetv2.model.Video;
import com.liskovsoft.android.smartyoutubetv2.presenter.CardPresenter;
import com.liskovsoft.mediaserviceinterfaces.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.MediaTab;

import java.util.ArrayList;
import java.util.List;

public class MediaSectionObjectAdapter extends ObjectAdapter {
    private final MediaTab mSection;
    private int mPosition;
    private final List<Listener> mListeners = new ArrayList<>();
    private final int mSectionIndex;

    public MediaSectionObjectAdapter(MediaTab section, int sectionIndex) {
        super(new CardPresenter());
        mSection = section;
        mSectionIndex = sectionIndex;
    }

    @Override
    public int size() {
        return mSection.getMediaItems().size();
    }

    @Override
    public Object get(int position) {
        if (mPosition != position) {
            mPosition = position;
            onPositionChange();
        }

        MediaItem video = mSection.getMediaItems().get(position);
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

    private void onPositionChange() {
        for (Listener listener : mListeners) {
            listener.onPositionChange(this);
        }
    }

    public void addListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public int getSectionIndex() {
        return mSectionIndex;
    }

    public void addAll(List<MediaItem> videos) {
        if (mSection != null) {
            mSection.getMediaItems().addAll(videos);
        }
    }

    public int getPosition() {
        return mPosition;
    }

    public interface Listener {
        void onPositionChange(MediaSectionObjectAdapter adapter);
    }
}
