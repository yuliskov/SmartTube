package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SponsorBlockController.SegmentAction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SponsorBlockDataTest {
    private static final String DATA_KEY = "content_block_data";
    private Context mContext;
    private AppPrefs mPrefs;

    @Before
    public void setUp() throws Exception {
        resetSingleton(SponsorBlockData.class);
        resetSingleton(AppPrefs.class);
        mContext = RuntimeEnvironment.getApplication();
        mPrefs = AppPrefs.instance(mContext);
        mPrefs.setData(DATA_KEY, null);
    }

    @After
    public void tearDown() throws Exception {
        mPrefs.setData(DATA_KEY, null);
        resetSingleton(SponsorBlockData.class);
        resetSingleton(AppPrefs.class);
    }

    @Test
    public void newCategoriesHaveSettingsButDoNotStartSkipping() {
        SponsorBlockData data = SponsorBlockData.instance(mContext);

        assertTrue(data.getAllCategories().contains(SponsorSegment.CATEGORY_HOOK));
        assertEquals(SponsorBlockData.ACTION_DO_NOTHING, data.getAction(SponsorSegment.CATEGORY_HOOK));
        assertEquals(SponsorBlockData.ACTION_DO_NOTHING, data.getAction(SponsorSegment.CATEGORY_FILLER));
        assertEquals(SponsorBlockData.ACTION_SKIP_WITH_TOAST, data.getAction(SponsorSegment.CATEGORY_SPONSOR));
        assertTrue(data.isColorMarkerEnabled(SponsorSegment.CATEGORY_HOOK));
        assertTrue(data.isColorMarkerEnabled(SponsorSegment.CATEGORY_FILLER));
        for (String category : data.getAllCategories()) {
            assertNotNull(data.getColorRes(category));
            assertFalse(mContext.getString(data.getLocalizedRes(category)).isEmpty());
            assertFalse(mContext.getString(data.getDescriptionRes(category)).isEmpty());
        }
    }

    @Test
    public void addingHookPreservesExistingActionsAndMarkerSelection() throws Exception {
        SponsorBlockData data = restore(new String[]{"sponsor,2", "intro,0", "filler,1", "poi_highlight,2"},
                new String[]{"sponsor", "poi_highlight"});

        assertEquals(SponsorBlockData.ACTION_SHOW_DIALOG, data.getAction(SponsorSegment.CATEGORY_SPONSOR));
        assertEquals(SponsorBlockData.ACTION_SKIP_ONLY, data.getAction(SponsorSegment.CATEGORY_INTRO));
        assertEquals(SponsorBlockData.ACTION_SKIP_WITH_TOAST, data.getAction(SponsorSegment.CATEGORY_FILLER));
        assertEquals(SponsorBlockData.ACTION_SHOW_DIALOG, data.getAction(SponsorSegment.CATEGORY_POI_HIGHLIGHT));
        assertEquals(SponsorBlockData.ACTION_DO_NOTHING, data.getAction(SponsorSegment.CATEGORY_HOOK));
        assertTrue(data.isColorMarkerEnabled(SponsorSegment.CATEGORY_SPONSOR));
        assertFalse(data.isColorMarkerEnabled(SponsorSegment.CATEGORY_HOOK));
    }

    @Test
    public void chosenHookActionAndMarkersSurviveRecreation() throws Exception {
        SponsorBlockData data = restore(new String[]{"sponsor,3"}, new String[]{"sponsor"});
        data.setAction(SponsorSegment.CATEGORY_HOOK, SponsorBlockData.ACTION_SHOW_DIALOG);
        data.enableColorMarker(SponsorSegment.CATEGORY_HOOK);
        data.disableColorMarker(SponsorSegment.CATEGORY_SPONSOR);
        data.setIgnoredDurationMs(7_500);

        data = reload();

        assertEquals(SponsorBlockData.ACTION_SHOW_DIALOG, data.getAction(SponsorSegment.CATEGORY_HOOK));
        assertEquals(SponsorBlockData.ACTION_DO_NOTHING, data.getAction(SponsorSegment.CATEGORY_SPONSOR));
        assertTrue(data.isColorMarkerEnabled(SponsorSegment.CATEGORY_HOOK));
        assertFalse(data.isColorMarkerEnabled(SponsorSegment.CATEGORY_SPONSOR));
        assertEquals(7_500, data.getIgnoredDurationMs());
    }

    @Test
    public void savedEmptyMarkerSelectionDoesNotEnableNewMarkers() throws Exception {
        SponsorBlockData data = restore(new String[]{"sponsor,3"}, new String[]{});

        assertFalse(data.isColorMarkersEnabled());
        assertFalse(data.isColorMarkerEnabled(SponsorSegment.CATEGORY_HOOK));
    }

    @Test
    public void malformedKnownActionsFailClosed() throws Exception {
        SponsorBlockData data = restore(new String[]{
                "sponsor,not-a-number", "intro,", "outro,2147483648", "interaction,-1", "selfpromo,4",
                "music_offtopic,0,extra", "preview", "filler,null", "hook, 1"
        }, new String[]{});

        for (String category : data.getAllCategories()) {
            if (!SponsorSegment.CATEGORY_POI_HIGHLIGHT.equals(category)) {
                assertEquals(category, SponsorBlockData.ACTION_DO_NOTHING, data.getAction(category));
            }
        }
        assertFalse(data.isActionsEnabled());
        assertTrue(data.getEnabledCategories().isEmpty());
    }

    @Test
    public void nullUnknownAndEmptyEntriesAreDiscardedOnSave() throws Exception {
        SponsorBlockData data = restore(new String[]{null, "", ",1", "future_category,0", "sponsor,2"},
                new String[]{null, "", "future_category", "sponsor", "sponsor"});

        assertEquals(data.getAllCategories().size(), data.getActions().size());
        assertEquals(SponsorBlockData.ACTION_SHOW_DIALOG, data.getAction(SponsorSegment.CATEGORY_SPONSOR));
        assertEquals(SponsorBlockData.ACTION_UNDEFINED, data.getAction("future_category"));
        assertFalse(data.getEnabledCategories().contains("future_category"));
        assertFalse(data.getEnabledCategories().contains(null));
        for (SegmentAction action : data.getActions()) {
            assertNotNull(action);
            assertTrue(data.getAllCategories().contains(action.segmentCategory));
        }

        data.setSponsorBlockEnabled(false);
        assertFalse(mPrefs.getData(DATA_KEY).contains("future_category"));
        data = reload();
        assertEquals(SponsorBlockData.ACTION_SHOW_DIALOG, data.getAction(SponsorSegment.CATEGORY_SPONSOR));
        assertTrue(data.isColorMarkerEnabled(SponsorSegment.CATEGORY_SPONSOR));
        assertFalse(data.isColorMarkerEnabled("future_category"));
    }

    @Test
    public void duplicateActionsAreCollapsedAndConflictsFailClosed() throws Exception {
        SponsorBlockData data = restore(new String[]{
                "sponsor,1", "sponsor,1", "intro,0", "intro,2", "intro,0", "outro,2", "outro,invalid"
        }, new String[]{});

        assertEquals(data.getAllCategories().size(), data.getActions().size());
        assertEquals(SponsorBlockData.ACTION_SKIP_WITH_TOAST, data.getAction(SponsorSegment.CATEGORY_SPONSOR));
        assertEquals(SponsorBlockData.ACTION_DO_NOTHING, data.getAction(SponsorSegment.CATEGORY_INTRO));
        assertEquals(SponsorBlockData.ACTION_DO_NOTHING, data.getAction(SponsorSegment.CATEGORY_OUTRO));
    }

    @Test
    public void unsupportedSettingValuesCannotEnableActionsOrMarkers() {
        SponsorBlockData data = SponsorBlockData.instance(mContext);
        data.setAction(SponsorSegment.CATEGORY_SPONSOR, 99);
        data.setAction("future_category", SponsorBlockData.ACTION_SKIP_ONLY);
        data.setAction(null, SponsorBlockData.ACTION_SKIP_ONLY);
        data.enableColorMarker("future_category");
        data.enableColorMarker(null);

        assertEquals(SponsorBlockData.ACTION_DO_NOTHING, data.getAction(SponsorSegment.CATEGORY_SPONSOR));
        assertEquals(SponsorBlockData.ACTION_UNDEFINED, data.getAction("future_category"));
        assertFalse(data.getEnabledCategories().contains("future_category"));
        assertFalse(data.getEnabledCategories().contains(null));
    }

    @Test
    public void highlightOnlyUsesItsMarkerSettingWhileRetainingTheSavedAction() throws Exception {
        SponsorBlockData data = SponsorBlockData.instance(mContext);
        for (String category : data.getAllCategories()) {
            data.setAction(category, SponsorBlockData.ACTION_DO_NOTHING);
            data.disableColorMarker(category);
        }
        data.setAction(SponsorSegment.CATEGORY_POI_HIGHLIGHT, SponsorBlockData.ACTION_SKIP_WITH_TOAST);

        assertFalse(data.isActionsEnabled());
        assertTrue(data.getEnabledCategories().isEmpty());
        data.enableColorMarker(SponsorSegment.CATEGORY_POI_HIGHLIGHT);
        data = reload();
        assertEquals(SponsorBlockData.ACTION_SKIP_WITH_TOAST, data.getAction(SponsorSegment.CATEGORY_POI_HIGHLIGHT));
        assertFalse(data.isActionsEnabled());
        assertEquals(Collections.singleton(SponsorSegment.CATEGORY_POI_HIGHLIGHT), data.getEnabledCategories());
    }

    private SponsorBlockData restore(String[] actions, String[] markers) throws Exception {
        mPrefs.setData(DATA_KEY, Helpers.mergeData(true, null, null, null, null, null,
                actions == null ? null : Helpers.mergeArray((Object[]) actions),
                markers == null ? null : Helpers.mergeArray((Object[]) markers),
                false, null, false, 5_000));
        return reload();
    }

    private SponsorBlockData reload() throws Exception {
        resetSingleton(SponsorBlockData.class);
        return SponsorBlockData.instance(mContext);
    }

    private static void resetSingleton(Class<?> type) throws Exception {
        Field field = type.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
