package com.liskovsoft.android.smartyoutubetv2.adapter;

import androidx.leanback.widget.ObjectAdapter;
import com.liskovsoft.android.smartyoutubetv2.model.Video;
import com.liskovsoft.android.smartyoutubetv2.presenter.CardPresenter;
import com.liskovsoft.myvideotubeapi.VideoSection;

public class VideoSectionObjectAdapter extends ObjectAdapter {
    private final VideoSection mSection;

    public VideoSectionObjectAdapter(VideoSection section) {
        super(new CardPresenter());
        mSection = section;
    }

    @Override
    public int size() {
        return mSection.getVideos().size();
    }

    @Override
    public Object get(int position) {
        com.liskovsoft.myvideotubeapi.Video video = mSection.getVideos().get(position);
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
}
