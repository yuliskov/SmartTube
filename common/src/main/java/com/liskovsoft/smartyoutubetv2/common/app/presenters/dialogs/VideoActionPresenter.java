package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;
import com.liskovsoft.sharedutils.mylogger.Log;
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

        if (item.hasVideo() && !item.isMix()) {
            PlaybackPresenter.instance(getContext()).openVideo(item);
        } else if (item.hasPlaylist() || item.hasNestedItems()) {
            ChannelUploadsPresenter.instance(getContext()).openChannel(item);
        } else if (item.hasChannel()) {
            Utils.chooseChannelPresenter(getContext(), item);
        } else if (item.isChapter) {
            PlaybackPresenter.instance(getContext()).setPosition(item.startTimeMs);
        } else {
            Log.e(TAG, "Video item doesn't contain needed data!");
        }
    }
}
