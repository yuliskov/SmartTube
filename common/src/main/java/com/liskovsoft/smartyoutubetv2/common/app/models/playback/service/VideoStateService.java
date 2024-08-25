package com.liskovsoft.smartyoutubetv2.common.app.models.playback.service;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

import java.util.Map;

public class VideoStateService implements ProfileChangeListener {
    @SuppressLint("StaticFieldLeak")
    private static VideoStateService sInstance;
    private static final int MAX_PERSISTENT_STATE_SIZE = 100;
    // Don't store state inside Video object.
    // As one video might correspond to multiple Video objects.
    private final Map<String, State> mStates = Helpers.createLRUMap(MAX_PERSISTENT_STATE_SIZE);
    private final AppPrefs mPrefs;

    private VideoStateService(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static VideoStateService instance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new VideoStateService(context.getApplicationContext());
        }

        return sInstance;
    }

    public State getByVideoId(String videoId) {
        return mStates.get(videoId);
    }

    public boolean isEmpty() {
        return mStates.isEmpty();
    }

    public void save(State state) {
        mStates.put(state.videoId, state);
    }

    public void removeByVideoId(String videoId) {
        mStates.remove(videoId);
    }

    public void clear() {
        mStates.clear();
        persistState();
    }

    private void restoreState() {
        mStates.clear();
        String data = mPrefs.getStateUpdaterData();

        if (data != null) {
            String[] split = data.split("\\|");

            for (String spec : split) {
                State state = State.from(spec);

                if (state != null) {
                    mStates.put(state.videoId, state);
                }
            }
        }
    }

    public void persistState() {
        StringBuilder sb = new StringBuilder();

        for (State state : mStates.values()) {
            // NOTE: Storage optimization!!!
            //if (state.lengthMs <= MUSIC_VIDEO_LENGTH_MS && !mPlayerData.isRememberSpeedEachEnabled()) {
            //    continue;
            //}

            if (sb.length() != 0) {
                sb.append("|");
            }

            sb.append(state);
        }

        mPrefs.setStateUpdaterData(sb.toString());
    }

    public static class State {
        public final String videoId;
        public final long positionMs;
        public final long durationMs;
        public final float speed;
        public final long timestamp = System.currentTimeMillis();

        public State(String videoId, long positionMs) {
            this(videoId, positionMs, -1);
        }

        public State(String videoId, long positionMs, long durationMs) {
            this(videoId, positionMs, durationMs, 1.0f);
        }

        public State(String videoId, long positionMs, long durationMs, float speed) {
            this.videoId = videoId;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.speed = speed;
        }

        public static State from(String spec) {
            if (spec == null) {
                return null;
            }

            String[] split = spec.split(",");

            if (split.length != 4) {
                return null;
            }

            String videoId = split[0];
            long positionMs = Helpers.parseLong(split[1]);
            long lengthMs = Helpers.parseLong(split[2]);
            float speed = Helpers.parseFloat(split[3]);

            return new State(videoId, positionMs, lengthMs, speed);
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("%s,%s,%s,%s", videoId, positionMs, durationMs, speed);
        }
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
