package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ShelfListModelTest {
    private static final PresenterSelector NO_PRESENTER = new PresenterSelector() {
        @Override
        public Presenter getPresenter(Object item) {
            return null;
        }
    };

    private static Video video(String id) {
        Video video = new Video();
        video.videoId = id;
        return video;
    }

    private static VideoGroup group(int id, int position, String... videoIds) {
        List<Video> videos = new ArrayList<>();
        for (String videoId : videoIds) {
            videos.add(video(videoId));
        }
        VideoGroup group = VideoGroup.from(videos, null, position);
        group.setId(id);
        group.setTitle("g" + id);
        return group;
    }

    private static class RecordingListener implements ShelfListModel.Listener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onShelfInserted(int position) {
            events.add("ins" + position);
        }

        @Override
        public void onShelfRemoved(int position) {
            events.add("rem" + position);
        }

        @Override
        public void onShelvesCleared() {
            events.add("clear");
        }
    }

    @Test
    public void newGroups_appendAsShelves() {
        RecordingListener listener = new RecordingListener();
        ShelfListModel model = new ShelfListModel(NO_PRESENTER, listener);

        model.apply(group(1, -1, "a", "b"));
        model.apply(group(2, -1, "c"));

        assertEquals(2, model.size());
        assertEquals("g1", model.get(0).getTitle());
        assertEquals(2, model.get(0).getAdapter().size());
        assertEquals(Arrays.asList("ins0", "ins1"), listener.events);
    }

    @Test
    public void sameGroupId_continuesExistingShelf() {
        ShelfListModel model = new ShelfListModel(NO_PRESENTER, new RecordingListener());
        model.apply(group(1, -1, "a", "b"));
        model.apply(group(1, -1, "c"));

        assertEquals(1, model.size());
        assertEquals(3, model.get(0).getAdapter().size());
    }

    @Test
    public void emptyGroup_isIgnored() {
        ShelfListModel model = new ShelfListModel(NO_PRESENTER, new RecordingListener());
        model.apply(group(1, -1));

        assertEquals(0, model.size());
    }

    @Test
    public void groupWithPosition_insertsAtPosition() {
        ShelfListModel model = new ShelfListModel(NO_PRESENTER, new RecordingListener());
        model.apply(group(1, -1, "a"));
        model.apply(group(2, -1, "b"));
        model.apply(group(3, 1, "c"));

        assertEquals("g3", model.get(1).getTitle());
    }

    @Test
    public void replaceWithoutPosition_clearsAll() {
        RecordingListener listener = new RecordingListener();
        ShelfListModel model = new ShelfListModel(NO_PRESENTER, listener);
        model.apply(group(1, -1, "a"));

        VideoGroup replace = group(2, -1, "b");
        replace.setAction(VideoGroup.ACTION_REPLACE);
        model.apply(replace);

        assertEquals(1, model.size());
        assertEquals("g2", model.get(0).getTitle());
        assertEquals(Arrays.asList("ins0", "clear", "ins0"), listener.events);
    }

    @Test
    public void replaceWithPosition_replacesOnlyThatShelf() {
        ShelfListModel model = new ShelfListModel(NO_PRESENTER, new RecordingListener());
        model.apply(group(1, -1, "a"));
        model.apply(group(2, -1, "b"));

        VideoGroup replace = group(1, 0, "x", "y");
        replace.setAction(VideoGroup.ACTION_REPLACE);
        model.apply(replace);

        assertEquals(2, model.size());
        assertEquals(2, model.get(0).getAdapter().size());
        assertEquals("g2", model.get(1).getTitle());
    }

    @Test
    public void remove_removesVideosFromShelf() {
        ShelfListModel model = new ShelfListModel(NO_PRESENTER, new RecordingListener());
        VideoGroup first = group(1, -1, "a", "b");
        model.apply(first);

        VideoGroup remove = VideoGroup.from(Arrays.asList(first.get(0)), null, -1);
        remove.setId(1);
        remove.setAction(VideoGroup.ACTION_REMOVE);
        model.apply(remove);

        assertEquals(1, model.get(0).getAdapter().size());
    }

    @Test
    public void nearEnd_followsContinueThreshold() {
        ShelfListModel model = new ShelfListModel(NO_PRESENTER, new RecordingListener());
        model.apply(group(1, -1, "a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));

        assertEquals(false, model.isNearEnd(0, 0));
        assertEquals(true, model.isNearEnd(0, 9));
    }

    @Test
    public void verticalThumbnails_makeShortsShelf() {
        VideoGroup shorts = group(1, -1, "a", "b");
        shorts.getVideos().get(0).cardImageUrl = "https://i.ytimg.com/vi/a/oardefault.jpg?sqp=x";
        shorts.getVideos().get(1).cardImageUrl = "https://i.ytimg.com/vi/b/oar2.jpg";
        assertTrue(ShelfListModel.isShorts(shorts));

        VideoGroup mixed = group(2, -1, "c", "d");
        mixed.getVideos().get(0).cardImageUrl = "https://i.ytimg.com/vi/c/sardefault.jpg";
        mixed.getVideos().get(1).cardImageUrl = "https://i.ytimg.com/vi/d/hq720.jpg";
        assertFalse(ShelfListModel.isShorts(mixed));
    }
}
