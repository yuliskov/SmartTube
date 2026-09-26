package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager.LayoutParams;

import com.liskovsoft.sharedutils.misc.WeakHashSet;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScreensaverManagerTest {
    private static final long DIM_TIMEOUT_MS = 1_000L;
    private static final long SCREEN_OFF_TIMEOUT_MS = 5_000L;

    public static class HostActivity extends Activity {}

    public static class BrowseHostActivity extends MotherActivity {}

    public static class PlayerHostActivity extends MotherActivity {}

    @Before
    public void setUp() throws Exception {
        resetScreensaverRegistry();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @After
    public void tearDown() throws Exception {
        Utils.sHandler.removeCallbacksAndMessages(null);
        resetScreensaverRegistry();
    }

    @Test
    public void pauseReleasesSuppressionAndKeepsItReleasedPastTimers() {
        ActivityController<HostActivity> controller = Robolectric.buildActivity(HostActivity.class);
        HostActivity activity = controller.setup().get();
        configurePrefs(activity);
        ScreensaverManager manager = new ScreensaverManager(activity);

        manager.disable();
        drainImmediateTasks();
        assertTrue(isScreensaverSuppressed(activity));

        manager.suspend();
        drainImmediateTasks();
        advance(DIM_TIMEOUT_MS + SCREEN_OFF_TIMEOUT_MS + 1_000L);

        assertFalse(isScreensaverSuppressed(activity));
        assertFalse(isDimOverlayVisible(activity));
        assertFalse(manager.isScreenOff());
    }

    @Test
    public void queuedCallbacksCannotReacquireSuppressionWhileSuspended() throws Exception {
        ActivityController<HostActivity> pausedController = Robolectric.buildActivity(HostActivity.class);
        HostActivity pausedActivity = pausedController.setup().get();
        configurePrefs(pausedActivity);
        ScreensaverManager paused = new ScreensaverManager(pausedActivity);

        ActivityController<HostActivity> activeController = Robolectric.buildActivity(HostActivity.class);
        HostActivity activeActivity = activeController.setup().get();
        configurePrefs(activeActivity);
        ScreensaverManager active = new ScreensaverManager(activeActivity);
        drainImmediateTasks();

        paused.disable();
        paused.suspend();

        paused.enableChecked();
        paused.disableChecked();
        paused.doScreenOff();
        active.disable();
        drainImmediateTasks();
        advance(DIM_TIMEOUT_MS + SCREEN_OFF_TIMEOUT_MS + 1_000L);

        assertFalse(isScreensaverSuppressed(pausedActivity));
        assertFalse(isDimOverlayVisible(pausedActivity));
        assertFalse(paused.isScreenOff());
        assertFalse(isRegistryLocked());
    }

    @Test
    public void suspendReleasesSuppressionEvenWhenBlocked() {
        ActivityController<HostActivity> controller = Robolectric.buildActivity(HostActivity.class);
        HostActivity activity = controller.setup().get();
        configurePrefs(activity);
        ScreensaverManager manager = new ScreensaverManager(activity);

        manager.disable();
        drainImmediateTasks();
        manager.setBlocked(true);

        manager.suspend();
        manager.suspend();
        manager.cleanup();
        drainImmediateTasks();
        advance(DIM_TIMEOUT_MS + SCREEN_OFF_TIMEOUT_MS);

        assertFalse(isScreensaverSuppressed(activity));
        assertFalse(isDimOverlayVisible(activity));

        manager.resume();
        drainImmediateTasks();
        // NOTE: changed logic. The blocked mode should hold screensaver after resume.
        assertTrue(isScreensaverSuppressed(activity));

        manager.setBlocked(false);
        manager.enable();
        drainImmediateTasks();
        assertTrue(isScreensaverSuppressed(activity));
    }

    @Test
    public void resumeRestoresPlaybackAndDimmingPoliciesWithoutStaleCallbacks() {
        ActivityController<HostActivity> controller = Robolectric.buildActivity(HostActivity.class);
        HostActivity activity = controller.setup().get();
        configurePrefs(activity);
        ScreensaverManager manager = new ScreensaverManager(activity);
        drainImmediateTasks();

        manager.disable();
        drainImmediateTasks();
        assertTrue(isScreensaverSuppressed(activity));

        manager.suspend();
        drainImmediateTasks();
        assertFalse(isScreensaverSuppressed(activity));

        manager.resume();
        drainImmediateTasks();
        manager.disableChecked();
        drainImmediateTasks();
        assertTrue(isScreensaverSuppressed(activity));
        assertFalse(isDimOverlayVisible(activity));

        manager.suspend();
        manager.resume();
        manager.enableChecked();
        drainImmediateTasks();
        assertTrue(isScreensaverSuppressed(activity));
        assertFalse(isDimOverlayVisible(activity));

        advance(DIM_TIMEOUT_MS / 2);
        assertFalse(isDimOverlayVisible(activity));
        assertTrue(isScreensaverSuppressed(activity));

        advance(DIM_TIMEOUT_MS / 2 + 100L);
        assertTrue(isDimOverlayVisible(activity));
        assertFalse(isScreensaverSuppressed(activity));
    }

    @Test
    public void constructionTimeCallbacksAreCancelledOnPause() {
        ActivityController<HostActivity> controller = Robolectric.buildActivity(HostActivity.class);
        HostActivity activity = controller.setup().get();
        configurePrefs(activity);
        ScreensaverManager manager = new ScreensaverManager(activity);

        manager.suspend();
        drainImmediateTasks();
        advance(DIM_TIMEOUT_MS + SCREEN_OFF_TIMEOUT_MS);

        assertFalse(isScreensaverSuppressed(activity));
        assertFalse(isDimOverlayVisible(activity));
    }

    @Test
    public void rapidPauseResumeDoesNotLeaveStaleDimCallback() {
        ActivityController<HostActivity> controller = Robolectric.buildActivity(HostActivity.class);
        HostActivity activity = controller.setup().get();
        configurePrefs(activity);
        ScreensaverManager manager = new ScreensaverManager(activity);
        drainImmediateTasks();

        manager.enable();
        manager.suspend();
        manager.resume();
        drainImmediateTasks();

        advance(DIM_TIMEOUT_MS / 2);
        assertFalse(isDimOverlayVisible(activity));
        assertTrue(isScreensaverSuppressed(activity));

        advance(DIM_TIMEOUT_MS / 2 + 100L);
        assertTrue(isDimOverlayVisible(activity));
        assertFalse(isScreensaverSuppressed(activity));
    }

    @Test
    public void pausedInstanceDoesNotAffectActiveInstanceOrStrandRegistryLock() throws Exception {
        ActivityController<BrowseHostActivity> browseController =
                Robolectric.buildActivity(BrowseHostActivity.class);
        BrowseHostActivity browse = browseController.create().start().resume().get();
        configurePrefs(browse);
        ScreensaverManager browseManager = browse.getScreensaverManager();
        drainImmediateTasks();

        browseController.pause();
        drainImmediateTasks();
        assertFalse(isScreensaverSuppressed(browse));

        ActivityController<PlayerHostActivity> playerController =
                Robolectric.buildActivity(PlayerHostActivity.class);
        PlayerHostActivity player = playerController.create().start().resume().get();
        configurePrefs(player);
        ScreensaverManager playerManager = player.getScreensaverManager();
        drainImmediateTasks();

        playerManager.disableChecked();
        drainImmediateTasks();
        assertTrue(isScreensaverSuppressed(player));
        assertFalse(isScreensaverSuppressed(browse));

        browseManager.enableChecked();
        browseManager.disableChecked();
        browseManager.doScreenOff();
        drainImmediateTasks();
        advance(DIM_TIMEOUT_MS + SCREEN_OFF_TIMEOUT_MS);

        assertTrue(isScreensaverSuppressed(player));
        assertFalse(isScreensaverSuppressed(browse));
        assertFalse(isDimOverlayVisible(browse));
        assertFalse(isRegistryLocked());

        playerController.pause();
        browseController.resume();
        drainImmediateTasks();

        assertFalse(isScreensaverSuppressed(player));
        assertFalse(isRegistryLocked());

        playerController.destroy();
        browseController.destroy();
        drainImmediateTasks();
        assertFalse(isRegistryLocked());
        assertFalse(isScreensaverSuppressed(player));
        assertFalse(isScreensaverSuppressed(browse));
    }

    @Test
    public void unlockCallbackStillRunsAfterSuspend() throws Exception {
        ActivityController<HostActivity> firstController = Robolectric.buildActivity(HostActivity.class);
        HostActivity firstActivity = firstController.setup().get();
        configurePrefs(firstActivity);
        ScreensaverManager first = new ScreensaverManager(firstActivity);

        ActivityController<HostActivity> secondController = Robolectric.buildActivity(HostActivity.class);
        HostActivity secondActivity = secondController.setup().get();
        configurePrefs(secondActivity);
        ScreensaverManager second = new ScreensaverManager(secondActivity);
        drainImmediateTasks();

        first.disable();
        ShadowLooper.shadowMainLooper().runOneTask();
        assertTrue(isRegistryLocked());

        first.suspend();
        drainImmediateTasks();

        assertFalse(isRegistryLocked());
        assertFalse(isScreensaverSuppressed(firstActivity));

        second.disable();
        drainImmediateTasks();
        assertTrue(isScreensaverSuppressed(secondActivity));
    }

    @Test
    public void motherActivityPauseReleasesSuppression() {
        ActivityController<BrowseHostActivity> controller =
                Robolectric.buildActivity(BrowseHostActivity.class);
        BrowseHostActivity activity = controller.create().start().resume().get();
        configurePrefs(activity);
        ScreensaverManager manager = activity.getScreensaverManager();
        assertNotNull(manager);

        manager.disable();
        drainImmediateTasks();
        assertTrue(isScreensaverSuppressed(activity));

        controller.pause();
        drainImmediateTasks();
        advance(DIM_TIMEOUT_MS + SCREEN_OFF_TIMEOUT_MS);

        assertFalse(isScreensaverSuppressed(activity));
        assertFalse(isDimOverlayVisible(activity));
    }

    @Test
    public void motherActivityDestroyCleansUpEvenWhenBlocked() {
        ActivityController<BrowseHostActivity> controller =
                Robolectric.buildActivity(BrowseHostActivity.class);
        BrowseHostActivity activity = controller.create().start().resume().get();
        configurePrefs(activity);
        ScreensaverManager manager = activity.getScreensaverManager();

        manager.disable();
        drainImmediateTasks();
        manager.setBlocked(true);

        controller.pause();
        controller.destroy();
        drainImmediateTasks();
        advance(DIM_TIMEOUT_MS + SCREEN_OFF_TIMEOUT_MS);

        assertFalse(isScreensaverSuppressed(activity));
        assertFalse(isDimOverlayVisible(activity));
    }

    private static void configurePrefs(Activity activity) {
        GeneralData generalData = GeneralData.instance(activity);
        generalData.setScreensaverTimeoutMs((int) DIM_TIMEOUT_MS);
        generalData.setScreensaverDisabled(false);

        PlayerTweaksData tweaksData = PlayerTweaksData.instance(activity);
        tweaksData.setScreenOffTimeoutSec((int) (SCREEN_OFF_TIMEOUT_MS / 1_000L));
        tweaksData.setScreenOffTimeoutEnabled(true);
    }

    private static boolean isScreensaverSuppressed(Activity activity) {
        return (activity.getWindow().getAttributes().flags & LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }

    private static boolean isDimOverlayVisible(Activity activity) {
        View dimContainer = activity.findViewById(R.id.dim_container);
        return dimContainer != null && dimContainer.getVisibility() == View.VISIBLE;
    }

    private static void drainImmediateTasks() {
        ShadowLooper.shadowMainLooper().idle();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Helpers.enable/disableScreensaver posts to the activity UI thread.
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    private static void advance(long delayMs) {
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(delayMs));
    }

    private static boolean isRegistryLocked() throws Exception {
        Field field = ScreensaverManager.class.getDeclaredField("sLockInstance");
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    @SuppressWarnings("unchecked")
    private static void resetScreensaverRegistry() throws Exception {
        Field lockField = ScreensaverManager.class.getDeclaredField("sLockInstance");
        lockField.setAccessible(true);
        lockField.setBoolean(null, false);

        Field instancesField = ScreensaverManager.class.getDeclaredField("sInstances");
        instancesField.setAccessible(true);
        WeakHashSet<ScreensaverManager> instances =
                (WeakHashSet<ScreensaverManager>) instancesField.get(null);
        if (instances != null) {
            instances.clear();
        }
    }
}
