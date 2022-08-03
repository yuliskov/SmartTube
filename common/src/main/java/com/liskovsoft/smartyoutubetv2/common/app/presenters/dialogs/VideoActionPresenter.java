package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

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

        if (item.hasNestedItems()) {
            // Below doesn't work right now. Api doesn't contains channel id.
            //ChannelPresenter.instance(getContext()).openChannel(item);

            ChannelUploadsPresenter.instance(getContext()).openChannel(item);
        } else if (item.hasVideo()) {
            BasePresenter.enableSync();
            PlaybackPresenter.instance(getContext()).openVideo(item);
        } else if (item.hasChannel()) {
            Utils.chooseChannelPresenter(getContext(), item);
        } else if (item.hasPlaylist()) {
            ChannelUploadsPresenter.instance(getContext()).openChannel(item);
        }
    }
}
