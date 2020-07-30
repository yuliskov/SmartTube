package com.liskovsoft.android.smartyoutubetv2.adapter;

import androidx.leanback.widget.ObjectAdapter;
import com.liskovsoft.android.smartyoutubetv2.model.Video;
import com.liskovsoft.android.smartyoutubetv2.presenter.CardPresenter;
import com.liskovsoft.videoserviceinterfaces.VideoSection;

import java.util.ArrayList;
import java.util.List;

public class VideoSectionObjectAdapter extends ObjectAdapter {
    private final VideoSection mSection;
    private int mPosition;
    private final List<Listener> mListeners = new ArrayList<>();
    private final int mSectionIndex;

    public VideoSectionObjectAdapter(VideoSection section, int sectionIndex) {
        super(new CardPresenter());
        mSection = section;
        mSectionIndex = sectionIndex;
    }

    @Override
    public int size() {
        return mSection.getVideos().size();
    }

    @Override
    public Object get(int position) {
        if (mPosition != position) {
            mPosition = position;
            onPositionChange();
        }

        com.liskovsoft.videoserviceinterfaces.Video video = mSection.getVideos().get(position);
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

    public void addAll(List<com.liskovsoft.videoserviceinterfaces.Video> videos) {
        if (mSection != null) {
            mSection.getVideos().addAll(videos);
        }
    }

    public int getPosition() {
        return mPosition;
    }

    public interface Listener {
        void onPositionChange(VideoSectionObjectAdapter adapter);
    }
}
