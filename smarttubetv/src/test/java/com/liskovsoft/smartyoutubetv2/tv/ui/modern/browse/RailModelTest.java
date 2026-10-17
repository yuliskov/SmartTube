package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RailModelTest {
    private static BrowseSection section(int id) {
        return new BrowseSection(id, "s" + id, BrowseSection.TYPE_ROW, 0);
    }

    @Test
    public void add_appendsOrInsertsLikeClassicHeaders() {
        RailModel model = new RailModel();
        model.add(-1, section(MediaGroup.TYPE_HOME));
        model.add(-1, section(MediaGroup.TYPE_MUSIC));
        model.add(1, section(MediaGroup.TYPE_SUBSCRIPTIONS));
        model.add(99, section(MediaGroup.TYPE_GAMING));

        assertEquals(4, model.size());
        assertEquals(MediaGroup.TYPE_HOME, model.get(0).getId());
        assertEquals(MediaGroup.TYPE_SUBSCRIPTIONS, model.get(1).getId());
        assertEquals(MediaGroup.TYPE_MUSIC, model.get(2).getId());
        assertEquals(MediaGroup.TYPE_GAMING, model.get(3).getId());
    }

    @Test
    public void add_sameIdReplacesPreviousEntry() {
        RailModel model = new RailModel();
        model.add(-1, section(MediaGroup.TYPE_HOME));
        model.add(-1, section(MediaGroup.TYPE_MUSIC));
        model.add(0, section(MediaGroup.TYPE_MUSIC));

        assertEquals(2, model.size());
        assertEquals(0, model.indexOf(MediaGroup.TYPE_MUSIC));
    }

    @Test
    public void remove_andClear() {
        RailModel model = new RailModel();
        model.add(-1, section(MediaGroup.TYPE_HOME));
        model.add(-1, section(MediaGroup.TYPE_MUSIC));
        model.remove(MediaGroup.TYPE_HOME);

        assertEquals(1, model.size());
        assertEquals(-1, model.indexOf(MediaGroup.TYPE_HOME));

        model.clear();
        assertEquals(0, model.size());
    }

    @Test
    public void divider_beforeFirstSecondaryEntryAfterPrimary() {
        RailModel model = new RailModel();
        model.add(-1, section(MediaGroup.TYPE_HOME));
        model.add(-1, section(MediaGroup.TYPE_SUBSCRIPTIONS));
        model.add(-1, section(MediaGroup.TYPE_MUSIC));
        model.add(-1, section(MediaGroup.TYPE_GAMING));

        assertFalse(model.hasDividerBefore(0));
        assertFalse(model.hasDividerBefore(1));
        assertTrue(model.hasDividerBefore(2));
        assertFalse(model.hasDividerBefore(3));
    }

    @Test
    public void divider_absentWithoutPrimaryEntries() {
        RailModel model = new RailModel();
        model.add(-1, section(MediaGroup.TYPE_MUSIC));
        model.add(-1, section(MediaGroup.TYPE_GAMING));

        assertFalse(model.hasDividerBefore(0));
        assertFalse(model.hasDividerBefore(1));
    }

    @Test
    public void settings_isPinnedBottom() {
        assertTrue(RailModel.isPinnedBottom(MediaGroup.TYPE_SETTINGS));
        assertFalse(RailModel.isPinnedBottom(MediaGroup.TYPE_HOME));
    }
}
