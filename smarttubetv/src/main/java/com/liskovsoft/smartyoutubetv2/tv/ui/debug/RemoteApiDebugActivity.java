package com.liskovsoft.smartyoutubetv2.tv.ui.debug;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.misc.remoteapi.HomeTheaterController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RemoteApiDebugActivity extends Activity {
    private TextView mLogView;
    private ScrollView mScrollView;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(0xFF1A1A2E);

        TextView title = new TextView(this);
        title.setText("Remote API Debug — Theater");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        mScrollView = new ScrollView(this);
        mLogView = new TextView(this);
        mLogView.setTextSize(12);
        mLogView.setTextColor(0xFF00FF88);
        mLogView.setTypeface(android.graphics.Typeface.MONOSPACE);
        mLogView.setPadding(16, 16, 16, 16);
        mLogView.setBackgroundColor(0xFF0D1117);
        mScrollView.addView(mLogView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        mScrollView.setLayoutParams(scrollParams);
        root.addView(mScrollView);

        // Volume row
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.setPadding(0, 16, 0, 0);

        addButton(buttonRow, "Get Volume", () -> {
            HomeTheaterController theater = HomeTheaterController.instance(this);
            log("Volume: " + theater.getVolume() + "  Muted: " + theater.isMuted());
        });
        addButton(buttonRow, "Vol Up", () -> HomeTheaterController.instance(this).volumeUp());
        addButton(buttonRow, "Vol Down", () -> HomeTheaterController.instance(this).volumeDown());
        addButton(buttonRow, "Mute", () -> HomeTheaterController.instance(this).toggleMute());
        root.addView(buttonRow);

        // State row
        LinearLayout buttonRow2 = new LinearLayout(this);
        buttonRow2.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow2.setGravity(Gravity.CENTER);
        buttonRow2.setPadding(0, 8, 0, 0);

        addButton(buttonRow2, "Theater State", () -> {
            HomeTheaterController theater = HomeTheaterController.instance(this);
            try {
                log("State:\n" + theater.getState().toString(2) + "\nOutput: " + theater.getAudioOutput());
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        });
        addButton(buttonRow2, "Power", () -> HomeTheaterController.instance(this).togglePower());
        addButton(buttonRow2, "Clear", () -> mHandler.post(() -> mLogView.setText("")));
        root.addView(buttonRow2);

        // ADB reference row
        LinearLayout buttonRow3 = new LinearLayout(this);
        buttonRow3.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow3.setGravity(Gravity.CENTER);
        buttonRow3.setPadding(0, 8, 0, 0);

        addButton(buttonRow3, "Show ADB Cmds", () -> log(
            "ADB commands (run on host machine):\n\n" +
            "Set HT:\n  adb shell cmd hdmi_control setsystemaudiomode on\n" +
            "  adb shell cmd hdmi_control setarc on\n\n" +
            "Set TV:\n  adb shell cmd hdmi_control setsystemaudiomode off\n" +
            "  adb shell cmd hdmi_control setarc off\n\n" +
            "Subwoofer (0-12):\n  adb shell cmd hdmi_control vendorcommand --device_type 0 --destination 5 --args \"F2:44:00:FF:08:FF:FF\" --id true\n\n" +
            "Rear (0-12):\n  adb shell cmd hdmi_control vendorcommand --device_type 0 --destination 5 --args \"F2:44:00:FF:FF:FF:FF:06\" --id true\n\n" +
            "Immersive AE:\n  adb shell cmd hdmi_control vendorcommand --device_type 0 --destination 5 --args \"F2:44:00:FF:FF:FF:01\" --id true\n\n" +
            "Sound mode (auto/cinema/music/standard):\n  adb shell cmd hdmi_control vendorcommand --device_type 0 --destination 5 --args \"F2:0D:00:55:FF:FF:FF:FF\" --id true\n\n" +
            "Read state:\n  adb shell dumpsys hdmi_control | tail -n 260"
        ));
        root.addView(buttonRow3);

        setContentView(root);
    }

    private void addButton(LinearLayout row, String label, Runnable action) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(11);
        btn.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(params);
        btn.setOnClickListener(v -> action.run());
        row.addView(btn);
    }

    private void log(String msg) {
        mHandler.post(() -> {
            mLogView.append(msg + "\n\n");
            mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
}
