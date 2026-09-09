package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.youtubeapi.videoinfo.LoginRequiredException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class ErrorFixerControllerTest {
    @Test public void signInRestrictionStopsLoadingAndShowsReasonWithoutReload() {
        List<String> actions = new ArrayList<>();
        String reason = "Sign in to confirm you're not a bot";
        PlaybackView player = (PlaybackView) Proxy.newProxyInstance(
                PlaybackView.class.getClassLoader(), new Class<?>[]{PlaybackView.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isEmbed")) return false;
                    actions.add(method.getName() + ":" + args[0]);
                    return null;
                });
        ErrorFixerController controller = new ErrorFixerController() {
            @Override public PlaybackView getPlayer() { return player; }
        };

        // No loader is attached: taking the old automatic reload path would fail this test.
        controller.runFormatErrorAction(new LoginRequiredException(reason));
        assertEquals(3, actions.size());
        assertEquals("showProgressBar:false", actions.get(0));
        assertEquals("setTitle:" + reason, actions.get(1));
        assertEquals("showOverlay:true", actions.get(2));
    }
}
