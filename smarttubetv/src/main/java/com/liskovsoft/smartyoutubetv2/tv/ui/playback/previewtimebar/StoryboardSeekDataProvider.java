package com.liskovsoft.smartyoutubetv2.tv.ui.playback.previewtimebar;

import android.content.Context;
import androidx.leanback.media.PlaybackGlue;
import androidx.leanback.widget.PlaybackSeekDataProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.framedrops.PlaybackTransportControlGlue;

public class StoryboardSeekDataProvider extends PlaybackSeekDataProvider {
    private final StoryboardManager mStoryboardManager;

    public StoryboardSeekDataProvider(Context context) {
        mStoryboardManager = new StoryboardManager(context);
    }

    public void init(Video video, long lengthMs) {
        mStoryboardManager.init(video, lengthMs);
    }

    @Override
    public long[] getSeekPositions() {
        return mStoryboardManager.getSeekPositions();
    }

    @Override
    public void getThumbnail(int index, ResultCallback callback) {
        mStoryboardManager.getBitmap(index, bitmap -> callback.onThumbnailLoaded(bitmap, index));
    }

    public static void setSeekProvider(PlaybackTransportControlGlue<?> glue) {
        if (glue.isPrepared()) {
            glue.setSeekProvider(new StoryboardSeekDataProvider(glue.getContext()));
        } else {
            glue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                @Override
                public void onPreparedStateChanged(PlaybackGlue glue) {
                    if (glue.isPrepared()) {
                        glue.removePlayerCallback(this);
                        PlaybackTransportControlGlue<?> transportControlGlue =
                                (PlaybackTransportControlGlue<?>) glue;
                        transportControlGlue.setSeekProvider(new StoryboardSeekDataProvider(glue.getContext()));
                    }
                }
            });
        }
    }
}
