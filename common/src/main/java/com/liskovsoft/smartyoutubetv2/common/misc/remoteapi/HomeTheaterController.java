package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

import android.content.Context;
import android.media.AudioManager;

import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Home theater audio control — wraps AudioManager for volume/mute
 * and HDMI CEC shell commands for speaker/theater switching + theater settings.
 *
 * Directly replaces the ADB-based tvaudiocontrol GNOME extension.
 * All functionality from the extension is available natively via REST/WebSocket.
 */
public class HomeTheaterController {
    private static final String TAG = HomeTheaterController.class.getSimpleName();
    private static final int STREAM_MUSIC = AudioManager.STREAM_MUSIC;
    private static final int THEATER_DESTINATION = 5;
    private static final int THEATER_LEVEL_MIN = 0;
    private static final int THEATER_LEVEL_MAX = 12;
    private static final int HDMI_HISTORY_LINES = 260;

    public static final String[] SOUND_MODE_IDS = {"auto", "cinema", "music", "standard"};
    public static final String[] SOUND_MODE_LABELS = {"Auto", "Cinema", "Music", "Standard"};
    public static final String[] SOUND_MODE_BYTES = {"55", "34", "06", "00"};

    // CEC patterns — exact port from tvaudiocontrol extension (no angle brackets)
    private static final Pattern PATTERN_AUDIO_OUTPUT = Pattern.compile(
            "(?:SET SYSTEM AUDIO MODE|SYSTEM AUDIO MODE REQUEST).*(?:5F:72:|05:70:[0-9A-F]{2}:[0-9A-F]{2}:)(00|01)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_LEVELS = Pattern.compile(
            "F2:43:00:FF:([0-9A-F]{2}):([0-9A-F]{2}):([0-9A-F]{2}):([0-9A-F]{2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_SUBWOOFER_SET = Pattern.compile(
            "F2:44:00:FF:([0-9A-F]{2}):FF:FF",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_REAR_SET = Pattern.compile(
            "F2:44:00:FF:FF:FF:FF:([0-9A-F]{2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_IMMERSIVE_SET = Pattern.compile(
            "F2:44:00:FF:FF:FF:(00|01)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_SOUND_MODE = Pattern.compile(
            "F2:0[CD]:00:([0-9A-F]{2}):FF:(?:00|FF):FF:(?:00|FF)",
            Pattern.CASE_INSENSITIVE);

    public interface TheaterStateListener {
        void onTheaterStateChanged(JSONObject state);
    }

    private static HomeTheaterController sInstance;
    private final Context mContext;
    private final AudioManager mAudioManager;
    private final ExecutorService mExecutor;

    private String mAudioOutput = "tv";
    private Integer mSubwooferLevel = null;
    private Integer mRearLevel = null;
    private Boolean mImmersiveAe = null;
    private String mSoundMode = null;
    private TheaterStateListener mListener;

    private HomeTheaterController(Context context) {
        mContext = context.getApplicationContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mExecutor = Executors.newSingleThreadExecutor();
    }

    public static synchronized HomeTheaterController instance(Context context) {
        if (sInstance == null) {
            sInstance = new HomeTheaterController(context);
        }
        return sInstance;
    }

    public void setListener(TheaterStateListener listener) {
        mListener = listener;
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
        runShellCommandAsync("input keyevent KEYCODE_POWER");
    }

    // ---- Audio Output (via HDMI CEC shell commands) ----

    public String getAudioOutput() {
        return mAudioOutput;
    }

    public void setAudioOutput(String output) {
        boolean useTheater = "theater".equals(output);
        mAudioOutput = output;

        if (useTheater) {
            runShellCommandAsync("cmd hdmi_control cec_setting set volume_control_enabled 1");
            runShellCommandAsync("cmd hdmi_control setsystemaudiomode on");
            runShellCommandAsync("cmd hdmi_control setarc on");
        } else {
            runShellCommandAsync("cmd hdmi_control setsystemaudiomode off");
            runShellCommandAsync("cmd hdmi_control setarc off");
        }

        // Refresh state from hardware after switching (like extension does)
        scheduleRefreshAfterDelay(2200);
    }

    // ---- Theater Levels (via HDMI CEC vendor commands) ----

    public Integer getSubwooferLevel() {
        return mSubwooferLevel;
    }

    public void setSubwooferLevel(int level) {
        int clamped = clampTheaterLevel(level);
        mSubwooferLevel = clamped;
        sendVendorCommandAsync(String.format("F2:44:00:FF:%02X:FF:FF", clamped));
        scheduleRefreshAfterDelay(900);
    }

    public Integer getRearLevel() {
        return mRearLevel;
    }

    public void setRearLevel(int level) {
        int clamped = clampTheaterLevel(level);
        mRearLevel = clamped;
        sendVendorCommandAsync(String.format("F2:44:00:FF:FF:FF:FF:%02X", clamped));
        scheduleRefreshAfterDelay(900);
    }

    public Boolean getImmersiveAe() {
        return mImmersiveAe;
    }

    public void setImmersiveAe(boolean enabled) {
        mImmersiveAe = enabled;
        sendVendorCommandAsync(String.format("F2:44:00:FF:FF:FF:%02X", enabled ? 1 : 0));
        scheduleRefreshAfterDelay(900);
    }

    public String getSoundMode() {
        return mSoundMode;
    }

    public void setSoundMode(String modeId) {
        for (int i = 0; i < SOUND_MODE_IDS.length; i++) {
            if (SOUND_MODE_IDS[i].equals(modeId)) {
                mSoundMode = modeId;
                sendVendorCommandAsync(String.format("F2:0D:00:%s:FF:FF:FF:FF", SOUND_MODE_BYTES[i]));
                scheduleRefreshAfterDelay(900);
                return;
            }
        }
    }

    public void cycleSoundMode(int offset) {
        int currentIndex = 0;
        for (int i = 0; i < SOUND_MODE_IDS.length; i++) {
            if (SOUND_MODE_IDS[i].equals(mSoundMode)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + offset + SOUND_MODE_IDS.length) % SOUND_MODE_IDS.length;
        setSoundMode(SOUND_MODE_IDS[nextIndex]);
    }

    // ---- Full State ----

    public JSONObject getState() {
        try {
            JSONObject state = new JSONObject();
            state.put("volume", getVolume());
            state.put("muted", isMuted());
            state.put("audio_output", mAudioOutput);
            state.put("subwoofer_level", mSubwooferLevel != null ? mSubwooferLevel : JSONObject.NULL);
            state.put("rear_level", mRearLevel != null ? mRearLevel : JSONObject.NULL);
            state.put("immersive_ae", mImmersiveAe != null ? mImmersiveAe : JSONObject.NULL);
            state.put("sound_mode", mSoundMode != null ? mSoundMode : JSONObject.NULL);
            return state;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // ---- Refresh from HDMI CEC state (synchronous) ----

    /**
     * Synchronously refresh theater state from dumpsys hdmi_control.
     * Returns the updated state.
     */
    public JSONObject refreshTheaterState() {
        String output = runShellCommand("dumpsys hdmi_control | tail -n " + HDMI_HISTORY_LINES);
        parseTheaterState(output);
        JSONObject state = getState();
        notifyListener();
        return state;
    }

    /**
     * Asynchronously refresh and broadcast state via listener.
     */
    public void refreshTheaterStateAsync() {
        mExecutor.execute(() -> {
            String output = runShellCommand("dumpsys hdmi_control | tail -n " + HDMI_HISTORY_LINES);
            parseTheaterState(output);
            notifyListener();
        });
    }

    /**
     * Parse HDMI CEC dumpsys output to extract theater state.
     * Ported from tvaudiocontrol extension's parseTheaterState().
     */
    private void parseTheaterState(String output) {
        if (output == null || output.isEmpty()) {
            return;
        }

        for (String rawLine : output.split("\n")) {
            String line = rawLine.toUpperCase();

            // Audio output: TV vs Theater
            Matcher audioOutputMatcher = PATTERN_AUDIO_OUTPUT.matcher(line);
            if (audioOutputMatcher.find()) {
                mAudioOutput = "01".equals(audioOutputMatcher.group(1)) ? "theater" : "tv";
            }

            // Combined levels message: subwoofer + immersive AE + rear
            Matcher levelsMatcher = PATTERN_LEVELS.matcher(line);
            if (levelsMatcher.find()) {
                mSubwooferLevel = clampTheaterLevel(Integer.parseInt(levelsMatcher.group(1), 16));
                mImmersiveAe = Integer.parseInt(levelsMatcher.group(3), 16) == 1;
                mRearLevel = clampTheaterLevel(Integer.parseInt(levelsMatcher.group(4), 16));
            }

            // Subwoofer set individually
            Matcher subwooferMatcher = PATTERN_SUBWOOFER_SET.matcher(line);
            if (subwooferMatcher.find()) {
                mSubwooferLevel = clampTheaterLevel(Integer.parseInt(subwooferMatcher.group(1), 16));
            }

            // Rear set individually
            Matcher rearMatcher = PATTERN_REAR_SET.matcher(line);
            if (rearMatcher.find()) {
                mRearLevel = clampTheaterLevel(Integer.parseInt(rearMatcher.group(1), 16));
            }

            // Immersive AE set individually
            Matcher immersiveMatcher = PATTERN_IMMERSIVE_SET.matcher(line);
            if (immersiveMatcher.find()) {
                mImmersiveAe = "01".equals(immersiveMatcher.group(1));
            }

            // Sound mode
            Matcher soundModeMatcher = PATTERN_SOUND_MODE.matcher(line);
            if (soundModeMatcher.find()) {
                String modeByte = soundModeMatcher.group(1);
                for (int i = 0; i < SOUND_MODE_BYTES.length; i++) {
                    if (SOUND_MODE_BYTES[i].equals(modeByte)) {
                        mSoundMode = SOUND_MODE_IDS[i];
                        break;
                    }
                }
            }
        }
    }

    // ---- Shell Command Execution ----

    private void sendVendorCommandAsync(String hexArgs) {
        String cmd = String.format(
                "cmd hdmi_control vendorcommand --device_type 0 --destination %d --args %s --id true",
                THEATER_DESTINATION, hexArgs);
        runShellCommandAsync(cmd);
    }

    private void runShellCommandAsync(String command) {
        mExecutor.execute(() -> runShellCommand(command));
    }

    private String runShellCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();
            String result = output.toString().trim();
            Log.d(TAG, "Shell cmd '%s' => %s", command, result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Shell command failed: %s => %s", command, e.getMessage());
            return "";
        }
    }

    // ---- Helpers ----

    private void notifyListener() {
        if (mListener != null) {
            try {
                JSONObject state = getState();
                mListener.onTheaterStateChanged(state);
            } catch (Exception e) {
                Log.e(TAG, "Listener notify error: %s", e.getMessage());
            }
        }
    }

    private void scheduleRefreshAfterDelay(long delayMs) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            refreshTheaterStateAsync();
        }, delayMs);
    }

    private static int clampTheaterLevel(int value) {
        return Math.max(THEATER_LEVEL_MIN, Math.min(THEATER_LEVEL_MAX, value));
    }

    public static int getTheaterLevelMin() {
        return THEATER_LEVEL_MIN;
    }

    public static int getTheaterLevelMax() {
        return THEATER_LEVEL_MAX;
    }
}
