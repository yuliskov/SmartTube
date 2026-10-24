package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunWith(RobolectricTestRunner.class)
public class PlaylistTest {

    @Test
    public void shuffle_incrementsGeneration() {
        Playlist playlist = Playlist.instance();
        playlist.add(Video.from("dQw4w9WgXcQ"));
        playlist.add(Video.from("9bZkp7q19f0"));
        int before = playlist.getGeneration();

        playlist.shuffle();

        assertNotEquals(before, playlist.getGeneration());
    }

    @Test
    public void move_incrementsGeneration() {
        Playlist playlist = Playlist.instance();
        Video v1 = Video.from("dQw4w9WgXcQ");
        Video v2 = Video.from("9bZkp7q19f0");
        playlist.add(v1);
        playlist.add(v2);
        int before = playlist.getGeneration();

        playlist.move(0, 1);

        assertNotEquals(before, playlist.getGeneration());
    }

    @Test
    public void clear_incrementsGeneration() {
        Playlist playlist = Playlist.instance();
        playlist.add(Video.from("dQw4w9WgXcQ"));
        int before = playlist.getGeneration();

        playlist.clear();

        assertNotEquals(before, playlist.getGeneration());
    }

    @Test
    public void add_doesNotIncrementGeneration() {
        Playlist playlist = Playlist.instance();
        playlist.clear();
        int before = playlist.getGeneration();

        playlist.add(Video.from("dQw4w9WgXcQ"));

        assertEquals(before, playlist.getGeneration());
    }

    @Test
    public void remove_doesNotIncrementGeneration() {
        Playlist playlist = Playlist.instance();
        playlist.clear();
        Video video = Video.from("dQw4w9WgXcQ");
        playlist.add(video);
        int before = playlist.getGeneration();

        playlist.remove(video);

        assertEquals(before, playlist.getGeneration());
    }

    @Test
    public void resetCount_bumpedOnlyOnClear() {
        Playlist playlist = Playlist.instance();
        playlist.clear();
        int resets = playlist.getResetCount();

        playlist.add(Video.from("dQw4w9WgXcQ"));
        playlist.add(Video.from("9bZkp7q19f0"));
        assertEquals(resets, playlist.getResetCount());

        playlist.shuffle();
        assertEquals(resets, playlist.getResetCount());

        playlist.move(0, 1);
        assertEquals(resets, playlist.getResetCount());

        playlist.remove(Video.from("dQw4w9WgXcQ"));
        assertEquals(resets, playlist.getResetCount());

        playlist.clear();
        assertEquals(resets + 1, playlist.getResetCount());
    }
}
