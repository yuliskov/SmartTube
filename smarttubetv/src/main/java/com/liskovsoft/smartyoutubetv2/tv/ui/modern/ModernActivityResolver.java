package com.liskovsoft.smartyoutubetv2.tv.ui.modern;

import android.app.Activity;

import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse.ModernBrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.channel.ModernChannelActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.channel.ModernChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.dialogs.ModernDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback.ModernPlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.search.ModernSearchActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.signin.ModernSignInActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * Picks the modern Activity for a view when the modern interface is enabled.<br/>
 * Views without a modern implementation fall back to the classic Activity.
 */
public class ModernActivityResolver {
    static final Map<Class<?>, Class<? extends Activity>> MODERN = new HashMap<>();
    static final Map<Class<? extends Activity>, Class<? extends Activity>> ROOT = new HashMap<>();
    private final boolean mEnabled;

    static {
        MODERN.put(BrowseView.class, ModernBrowseActivity.class);
        MODERN.put(PlaybackView.class, ModernPlaybackActivity.class);
        MODERN.put(SignInView.class, ModernSignInActivity.class);
        MODERN.put(SearchView.class, ModernSearchActivity.class);
        MODERN.put(ChannelView.class, ModernChannelActivity.class);
        MODERN.put(ChannelUploadsView.class, ModernChannelUploadsActivity.class);
        MODERN.put(AppDialogView.class, ModernDialogActivity.class);
        ROOT.put(BrowseActivity.class, ModernBrowseActivity.class);
    }

    public ModernActivityResolver(boolean enabled) {
        mEnabled = enabled;
    }

    public Class<? extends Activity> resolve(Class<?> viewClass, Class<? extends Activity> classic) {
        return pick(MODERN.get(viewClass), classic);
    }

    public Class<? extends Activity> resolveRoot(Class<? extends Activity> classicRoot) {
        return pick(ROOT.get(classicRoot), classicRoot);
    }

    private Class<? extends Activity> pick(Class<? extends Activity> modern, Class<? extends Activity> classic) {
        return mEnabled && modern != null ? modern : classic;
    }
}
