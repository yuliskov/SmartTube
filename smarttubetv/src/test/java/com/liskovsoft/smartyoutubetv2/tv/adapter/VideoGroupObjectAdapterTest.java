package com.liskovsoft.smartyoutubetv2.tv.adapter;

import androidx.leanback.widget.Presenter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupObjectAdapterTest {
    @Test
    public void releasedRowDropsVideosAndNextLiveRowReceivesUpdates() throws ReflectiveOperationException {
        List<Video> videos = new ArrayList<>();
        videos.add(new Video());
        VideoGroup group = VideoGroup.from(videos);
        Presenter presenter = null;
        VideoGroupObjectAdapter first = new VideoGroupObjectAdapter(group, presenter);
        VideoGroupObjectAdapter second = new VideoGroupObjectAdapter(group, presenter);
        VideoGroupObjectAdapter third = new VideoGroupObjectAdapter(group, presenter);
        try {
            videos.add(new Video());
            first.add(group);
            assertEquals(2, second.size());
            assertEquals(1, third.size());

            second.release();
            Field registry = VideoGroupObjectAdapter.class.getDeclaredField("sAdapterRegistry");
            registry.setAccessible(true);
            for (Object reference : (List<?>) registry.get(null)) {
                assertNotSame(second, ((WeakReference<?>) reference).get());
            }
            videos.add(new Video());
            first.add(group);
            assertEquals(0, second.size());
            assertTrue(second.getAll().isEmpty());
            assertTrue(second.getAllGroups().isEmpty());
            assertEquals(3, third.size());
        } finally {
            first.release();
            second.release();
            third.release();
        }
    }
}
