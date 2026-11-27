package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.app.Activity;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BrowsePresenterTest {
    private BrowsePresenter mPresenter;
    private CountingBrowseView mView;

    @Before
    public void setUp() throws Exception {
        Utils.sHandler.removeCallbacksAndMessages(null);
        BrowsePresenter.unhold();

        mView = Robolectric.buildActivity(CountingBrowseView.class).setup().get();
        mPresenter = BrowsePresenter.instance(mView);
        mPresenter.setView(mView);

        Field currentSectionField = BrowsePresenter.class.getDeclaredField("mCurrentSection");
        currentSectionField.setAccessible(true);
        currentSectionField.set(mPresenter, new BrowseSection(1, "Test", BrowseSection.TYPE_ERROR, -1));
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        BrowsePresenter.unhold();
    }

    @Test
    public void activeViewRunsScheduledErrorRefreshAfterDelay() {
        mPresenter.scheduleErrorRefresh();

        advanceSeconds(29);
        assertEquals("Refresh should not fire before 30s delay", 0, mView.refreshCount);

        advanceSeconds(2);
        assertEquals("Active view should fire scheduled error refresh after 30s", 1, mView.refreshCount);
    }

    @Test
    public void pauseCancelsScheduledErrorRefreshAndResumesWhenForegrounded() {
        mPresenter.scheduleErrorRefresh();

        mPresenter.onViewPaused();
        advanceSeconds(65);
        assertEquals("Paused view should not run scheduled error refreshes", 0, mView.refreshCount);

        mPresenter.onViewResumed();
        assertEquals("Resuming with a pending error refresh should trigger refresh once", 1, mView.refreshCount);

        // No further refresh should be pending once resumed cleanly
        mPresenter.onViewPaused();
        mPresenter.onViewResumed();
        advanceSeconds(65);
        assertEquals("Subsequent resume without error should not trigger extra refresh", 1, mView.refreshCount);
    }

    @Test
    public void errorWhilePausedDefersRefreshUntilResumed() {
        mPresenter.onViewPaused();
        mPresenter.scheduleErrorRefresh();

        advanceSeconds(65);
        assertEquals("In-flight failure while paused should not schedule background retries", 0, mView.refreshCount);

        mPresenter.onViewResumed();
        assertEquals("Resuming after an error while paused should refresh once", 1, mView.refreshCount);
    }

    private void advanceSeconds(long seconds) {
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(seconds));
    }

    public static class CountingBrowseView extends Activity implements BrowseView {
        int refreshCount;

        @Override public void addSection(int index, BrowseSection section) {}
        @Override public void removeSection(BrowseSection category) {}
        @Override public void removeAllSections() {}
        @Override public void selectSection(int index, boolean focusOnContent) {}
        @Override public void updateSection(VideoGroup group) {}
        @Override public void updateSection(SettingsGroup group) {}
        @Override public void clearSection(BrowseSection section) {}
        @Override public void selectSectionItem(int index) {}
        @Override public void selectSectionItem(Video item) {}
        @Override public void showError(ErrorFragmentData data) {}
        @Override public void showProgressBar(boolean show) {
            if (!show) {
                refreshCount++;
            }
        }
        @Override public boolean isProgressBarShowing() { return false; }
        @Override public void focusOnContent() {}
        @Override public boolean isEmpty() { return true; }
        @Override public void updateBadge() {}
    }
}
