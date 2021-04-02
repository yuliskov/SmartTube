package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.videofragment;

import android.view.SurfaceHolder;
import android.view.View;

public interface SurfaceWrapper {
    void setSurfaceHolderCallback(SurfaceHolder.Callback callback);
    View getSurfaceView();
}
