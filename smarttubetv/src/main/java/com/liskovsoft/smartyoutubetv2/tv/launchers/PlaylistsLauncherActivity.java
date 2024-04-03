package com.liskovsoft.smartyoutubetv2.tv.launchers;

import android.os.Bundle;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

public class PlaylistsLauncherActivity extends MotherActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BrowsePresenter.instance(this).selectSection(MediaGroup.TYPE_USER_PLAYLISTS);

        finish();
    }
}
