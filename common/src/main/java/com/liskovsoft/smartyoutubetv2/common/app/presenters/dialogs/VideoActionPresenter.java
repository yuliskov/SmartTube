package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;

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

        if (item.hasUploads()) {
            // Below doesn't work right now. Api doesn't contains channel id.
            //ChannelPresenter.instance(getContext()).openChannel(item);

            ChannelUploadsPresenter.instance(getContext()).openChannel(item);
        } else if (item.hasVideo()) {
            PlaybackPresenter.instance(getContext()).openVideo(item);
        } else if (item.hasChannel()) {
            chooseChannelPresenter(item);
        } else if (item.hasPlaylist()) {
            ChannelUploadsPresenter.instance(getContext()).openChannel(item);
        }
    }

    private void chooseChannelPresenter(Video item) {
        if (item.hasVideo() || item.badge == null) { // games section stream lists (no badge)
            ChannelPresenter.instance(getContext()).openChannel(item);
            return;
        }

        // Special cases, like new mix format (try search: 'Mon mix')
        MediaServiceManager.instance().loadChannelRows(item, group -> {
            if (group == null || group.size() == 0) {
                return;
            }

            if (group.size() == 1) {
                // Start first video or open full list?
                if (group.get(0).getMediaItems() != null) {
                    PlaybackPresenter.instance(getContext()).openVideo(Video.from(group.get(0).getMediaItems().get(0)));
                }
                //ChannelUploadsPresenter.instance(getContext()).updateGrid(group.get(0));
            } else {
                ChannelPresenter.instance(getContext()).updateRows(group);
            }
        });
    }
}
