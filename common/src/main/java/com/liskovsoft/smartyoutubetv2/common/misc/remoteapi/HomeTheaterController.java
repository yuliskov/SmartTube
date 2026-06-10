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
 * @see docs/remote-control-api.md section 4.9 for ADB commands.
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
        int raw = max > 0 ? Math.round(clamped * max / 100f) : 0;
        mAudioManager.setStreamVolume(STREAM_MUSIC, raw, 0);
    }

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
