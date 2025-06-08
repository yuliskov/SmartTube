package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;

public class VideoActionPresenter extends BasePresenter<Void> {
    private static final String TAG = VideoActionPresenter.class.getSimpleName();

    private VideoActionPresenter(Context context) {
        super(context);
    }

    public static VideoActionPresenter instance(Context context) {
        return new VideoActionPresenter(context);
    }

    public void apply(Video item) {
        if (item == null) {
            return;
        }

        // Show playlist contents in channel instead of instant playback
        if (item.hasVideo() && !item.isPlaylistInChannel()) {
            PlaybackPresenter.instance(getContext()).openVideo(item);
        } else if (item.hasChannel() || item.belongsToChannelUploads()) {
            MediaServiceManager.chooseChannelPresenter(getContext(), item);
        } else if (item.hasPlaylist() || item.hasNestedItems()) {
            if (item.belongsToMusic()) {
                startFistPlaylistItem(item);
            } else {
                ChannelUploadsPresenter.instance(getContext()).openChannel(item);
            }
        } else if (item.isChapter) {
            PlaybackPresenter.instance(getContext()).setPosition(item.startTimeMs);
        } else {
            Log.e(TAG, "Video item doesn't contain needed data!");
        }
    }

    private void startFistPlaylistItem(Video item) {
        LoadingManager.showLoading(getContext(), true);
        ChannelUploadsPresenter.instance(getContext()).obtainGroup(item, mediaGroup -> {
            LoadingManager.showLoading(getContext(), false);
            if (!mediaGroup.isEmpty()) {
                PlaybackPresenter.instance(getContext()).openVideo(Video.from(mediaGroup.getMediaItems().get(0)));
            }
        });
    }
}
