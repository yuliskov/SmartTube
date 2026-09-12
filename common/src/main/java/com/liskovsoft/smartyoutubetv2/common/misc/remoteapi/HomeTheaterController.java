package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Home theater audio control — wraps AudioManager for volume/mute.
 * HDMI CEC features (output switching, subwoofer/rear levels, sound mode,
 * immersive AE) require ADB shell access and should be implemented by
 * the Chrome extension or an ADB bridge.
 *
 * @see docs/remote-control-api.md section 4.10 for ADB commands.
 */
public class HomeTheaterController {
    private static final String TAG = HomeTheaterController.class.getSimpleName();
    private static final int STREAM_MUSIC = AudioManager.STREAM_MUSIC;

    private static HomeTheaterController sInstance;
    private final Context mContext;
    private final AudioManager mAudioManager;

    private HomeTheaterController(Context context) {
        mContext = context.getApplicationContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public static synchronized HomeTheaterController instance(Context context) {
        if (sInstance == null) {
            sInstance = new HomeTheaterController(context);
        }
        return sInstance;
    }

    // ---- Volume (via AudioManager) ----

    public int getVolume() {
        int raw = mAudioManager.getStreamVolume(STREAM_MUSIC);
        int max = mAudioManager.getStreamMaxVolume(STREAM_MUSIC);
        return max > 0 ? Math.round(raw * 100f / max) : 0;
    }

    public void setVolume(int volume) {
        int clamped = Math.max(0, Math.min(100, volume));
        int max = mAudioManager.getStreamMaxVolume(STREAM_MUSIC);
        int targetRaw = max > 0 ? Math.round(clamped * max / 100f) : 0;

        // Try an absolute set first (works when the TV's own speakers are active).
        mAudioManager.setStreamVolume(STREAM_MUSIC, targetRaw, 0);

        // With a CEC audio system (soundbar/HT) the absolute set is silently ignored —
        // CEC only understands volume-key steps. Measured on a Sony HT-A9: steps need
        // ~200ms pacing or the device drops them, and the volume reported back via CEC
        // <Report Audio Status> lags 1-2s. A single PERSISTENT worker walks toward the
        // target counting its own steps (re-reading mid-ramp returns stale values);
        // new setVolume calls just retarget it — never cancel/restart, otherwise a
        // burst of slider updates strangles the ramp after one step each.
        synchronized (sRampLock) {
            sTargetRaw = targetRaw;
            if (sRampThread == null || !sRampThread.isAlive()) {
                sRampThread = new Thread(this::rampWorker, "TheaterVolumeRamp");
                sRampThread.start();
            }
        }
    }

    private void rampWorker() {
        int expected = mAudioManager.getStreamVolume(STREAM_MUSIC);
        int verifyPasses = 0;
        while (true) {
            int target;
            synchronized (sRampLock) {
                target = sTargetRaw;
            }
            int delta = target - expected;
            if (delta != 0) {
                verifyPasses = 0;
                mAudioManager.adjustStreamVolume(STREAM_MUSIC,
                        delta > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                expected += delta > 0 ? 1 : -1;
                if (!sleepQuiet(220)) {
                    return;
                }
                continue;
            }
            // Reached target by our own count. Wait for the lagging CEC report,
            // then verify once; correct drift with a bounded number of passes.
            if (!sleepQuiet(1500)) {
                return;
            }
            int actual = mAudioManager.getStreamVolume(STREAM_MUSIC);
            synchronized (sRampLock) {
                if (sTargetRaw != target) {
                    continue; // retargeted while settling — keep going from our count
                }
                if (actual == target || verifyPasses >= 2) {
                    sRampThread = null;
                    return;
                }
            }
            expected = actual;
            verifyPasses++;
        }
    }

    private static boolean sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static final Object sRampLock = new Object();
    private static int sTargetRaw = -1;
    private static Thread sRampThread;

    public void volumeUp() {
        mAudioManager.adjustStreamVolume(STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
    }

    public void volumeDown() {
        mAudioManager.adjustStreamVolume(STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
    }

    public boolean isMuted() {
        return mAudioManager.getStreamVolume(STREAM_MUSIC) == 0;
    }

    public void toggleMute() {
        int current = mAudioManager.getStreamVolume(STREAM_MUSIC);
        if (current > 0) {
            mAudioManager.setStreamVolume(STREAM_MUSIC, 0, 0);
        } else {
            mAudioManager.setStreamVolume(STREAM_MUSIC, mAudioManager.getStreamMaxVolume(STREAM_MUSIC) / 2, 0);
        }
    }

    // ---- Power ----

    public void togglePower() {
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "KEYCODE_POWER"});
        } catch (Exception e) {
            Log.e(TAG, "togglePower failed: %s", e.getMessage());
        }
    }

    // ---- Audio Output (detected via AudioManager) ----

    /**
     * Detect current audio output device.
     * Returns "theater" if audio is routed to HDMI/ARC/eARC (home theater),
     * "tv" if routed to built-in speakers.
     */
    public String getAudioOutput() {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_HDMI
                    || type == AudioDeviceInfo.TYPE_HDMI_ARC
                    || type == AudioDeviceInfo.TYPE_HDMI_EARC
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                return "theater";
            }
        }
        return "tv";
    }

    // ---- Full State ----

    public JSONObject getState() {
        try {
            JSONObject state = new JSONObject();
            state.put("volume", getVolume());
            state.put("muted", isMuted());
            state.put("audio_output", getAudioOutput());
            return state;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
