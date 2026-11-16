package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.app.Application;
import android.content.Context;
import android.graphics.Paint;
import android.view.View;
import android.view.TextureView;
import android.view.SurfaceView;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import android.view.ViewGroup;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowViewGroup;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, shadows = SurfacePlaybackFragmentTest.CountingView.class)
public class SurfacePlaybackFragmentTest {
    private PlayerTweaksData mTweaks;
    private TestFragment mFragment;
    private AspectRatioFrameLayout mRoot;
    private CountingView mRootShadow;

    private static class TestFragment extends SurfacePlaybackFragment {
        @Override
        public Context getContext() {
            return RuntimeEnvironment.getApplication();
        }
    }

    @Implements(ViewGroup.class)
    public static class CountingView extends ShadowViewGroup {
        private int mLayerUpdates;
        private Paint mPaint;

        @Implementation
        @Override
        protected void setLayerType(int layerType, Paint layerPaint) {
            super.setLayerType(layerType, layerPaint);
            mLayerUpdates++;
            mPaint = layerPaint;
        }
    }

    @Before
    public void setUp() {
        mTweaks = PlayerTweaksData.instance(RuntimeEnvironment.getApplication());
        mTweaks.setNightlightWarmth(4500);
        mTweaks.setNightlightOnUi(false);
        mTweaks.setNightlightOnHdr(false);
        mTweaks.setNightlightHdrActive(false);
        mFragment = new TestFragment();
    }

    private void createSurface(boolean texture) {
        mRoot = new AspectRatioFrameLayout(mFragment.getContext());
        mRootShadow = Shadow.extract(mRoot);
        mRootShadow.mLayerUpdates = 0;
        SurfaceWrapper wrapper = texture ? new TextureViewWrapper(mFragment.getContext(), mRoot)
                : new SurfaceViewWrapper(mFragment.getContext(), mRoot);
        ReflectionHelpers.setField(mFragment, "mVideoSurfaceRoot", mRoot);
        ReflectionHelpers.setField(mFragment, "mVideoSurfaceWrapper", wrapper);
    }

    @Test
    public void recreatedViewReappliesTintWithoutAddingOverlay() {
        createSurface(true);
        mFragment.applyNightlight();
        assertNotNull(mRootShadow.mPaint);
        assertEquals(0, mRoot.getChildCount());
        mFragment.onDestroyView();
        mFragment.applyNightlight();
        createSurface(true);
        mFragment.applyNightlight();
        assertNotNull(mRootShadow.mPaint);
        assertEquals(1, mRootShadow.mLayerUpdates);
        assertEquals(0, mRoot.getChildCount());
    }

    @Test
    public void unchangedTintIsSkippedButWarmthUiAndHdrChangesApply() {
        createSurface(true);
        mFragment.applyNightlight();
        Paint firstPaint = mRootShadow.mPaint;
        assertNotNull(firstPaint);
        mFragment.applyNightlight();
        assertEquals(1, mRootShadow.mLayerUpdates);
        assertSame(firstPaint, mRootShadow.mPaint);
        mTweaks.setNightlightWarmth(3000);
        mFragment.applyNightlight();
        assertEquals(2, mRootShadow.mLayerUpdates);
        assertNotSame(firstPaint, mRootShadow.mPaint);
        mTweaks.setNightlightOnUi(true);
        mFragment.applyNightlight();
        assertNull(mRootShadow.mPaint);
        mTweaks.setNightlightOnUi(false);
        mFragment.applyNightlight();
        assertNotNull(mRootShadow.mPaint);
        mTweaks.setNightlightHdrActive(true);
        mFragment.applyNightlight();
        assertNull(mRootShadow.mPaint);
        mFragment.applyNightlight();
        assertEquals(5, mRootShadow.mLayerUpdates);
        mTweaks.setNightlightHdrActive(false);
        mFragment.applyNightlight();
        assertNotNull(mRootShadow.mPaint);
        assertEquals(6, mRootShadow.mLayerUpdates);
    }

    @Test
    public void activitySkipsUnchangedTintAndRestoresItAfterHdr() {
        MotherActivity activity = Robolectric.buildActivity(MotherActivity.class).get();
        View decor = activity.getWindow().getDecorView();
        CountingView shadow = Shadow.extract(decor);
        shadow.mLayerUpdates = 0;
        mTweaks.setNightlightOnUi(true);
        activity.applyNightlight();
        Paint firstPaint = shadow.mPaint;
        assertNotNull(firstPaint);
        activity.applyNightlight();
        assertEquals(1, shadow.mLayerUpdates);
        assertSame(firstPaint, shadow.mPaint);
        mTweaks.setNightlightHdrActive(true);
        activity.applyNightlight();
        assertNull(shadow.mPaint);
        mTweaks.setNightlightHdrActive(false);
        activity.applyNightlight();
        assertNotNull(shadow.mPaint);
        assertEquals(3, shadow.mLayerUpdates);

        MotherActivity recreated = Robolectric.buildActivity(MotherActivity.class).get();
        recreated.applyNightlight();
        CountingView newShadow = Shadow.extract(recreated.getWindow().getDecorView());
        assertNotNull(newShadow.mPaint);
    }
    public static class SwitchingFragment extends PlaybackFragment {
        View root;
        int restarts;
        @Override public Context getContext() { return RuntimeEnvironment.getApplication(); }
        @Override public View getView() { return root; }
        @Override public void restartEngine() { restarts++; }
    }

    @Test
    public void togglingNightlightSwitchesRendererOnceAndNeverAddsOverlay() {
        SwitchingFragment fragment = new SwitchingFragment();
        AspectRatioFrameLayout root = new AspectRatioFrameLayout(fragment.getContext());
        fragment.root = root;
        SurfaceWrapper initial = new SurfaceViewWrapper(fragment.getContext(), root);
        root.addView(initial.getSurfaceView());
        ReflectionHelpers.setField(fragment, "mVideoSurfaceRoot", root);
        ReflectionHelpers.setField(fragment, "mVideoSurfaceWrapper", initial);
        mTweaks.setTextureViewEnabled(false);
        mTweaks.setTunneledPlaybackEnabled(true);
        PlayerData.instance(fragment.getContext()).setRotationAngle(0);
        PlayerData.instance(fragment.getContext()).setVideoFlipEnabled(false);
        fragment.applyNightlight();
        assertTrue(fragment.getSurfaceView() instanceof TextureView);
        assertEquals(1, fragment.restarts);
        assertEquals(1, root.getChildCount());
        fragment.applyNightlight();
        mTweaks.setNightlightHdrActive(true);
        fragment.applyNightlight();
        assertEquals(1, fragment.restarts);
        mTweaks.setNightlightWarmth(Utils.NIGHTLIGHT_NEUTRAL);
        fragment.applyNightlight();
        assertTrue(fragment.getSurfaceView() instanceof SurfaceView);
        assertEquals(2, fragment.restarts);
        assertEquals(1, root.getChildCount());
        assertTrue(mTweaks.isTunneledPlaybackEnabled());
    }
}
