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

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

        // Title
        TextView title = new TextView(this);
        title.setText("Remote API Debug — Home Theater");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        // Log output
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

        // Button row helper
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.setPadding(0, 16, 0, 0);

        // Buttons
        addButton(buttonRow, "Run dumpsys", () -> runCmd("dumpsys hdmi_control | tail -n 80"));
        addButton(buttonRow, "Set HT", () -> runCmd("cmd hdmi_control setsystemaudiomode on && cmd hdmi_control setarc on"));
        addButton(buttonRow, "Set TV", () -> runCmd("cmd hdmi_control setsystemaudiomode off && cmd hdmi_control setarc off"));
        root.addView(buttonRow);

        LinearLayout buttonRow2 = new LinearLayout(this);
        buttonRow2.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow2.setGravity(Gravity.CENTER);
        buttonRow2.setPadding(0, 8, 0, 0);

        addButton(buttonRow2, "Volume", () -> runCmd("cmd media_session volume --stream 3 --get"));
        addButton(buttonRow2, "CEC cmd test", () -> runCmd("cmd hdmi_control vendorcommand --device_type 0 --destination 5 --args F2:44:00:FF:06:FF:FF --id true"));
        addButton(buttonRow2, "Clear", () -> mHandler.post(() -> mLogView.setText("")));
        root.addView(buttonRow2);

        LinearLayout buttonRow3 = new LinearLayout(this);
        buttonRow3.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow3.setGravity(Gravity.CENTER);
        buttonRow3.setPadding(0, 8, 0, 0);

        addButton(buttonRow3, "Theater State", () -> {
            HomeTheaterController theater = HomeTheaterController.instance(this);
            String state = theater.refreshTheaterState().toString();
            log("Theater state:\n" + state);
        });
        addButton(buttonRow3, "Audio Output", () -> {
            HomeTheaterController theater = HomeTheaterController.instance(this);
            log("Audio output: " + theater.getAudioOutput());
        });
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

    private void runCmd(String command) {
        log("$ " + command);
        mExecutor.execute(() -> {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                // Also read stderr
                try (BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        output.append("[ERR] ").append(line).append("\n");
                    }
                }
                process.waitFor();
                String result = output.toString().trim();
                if (result.isEmpty()) {
                    result = "(empty — command may require shell/root permissions)";
                }
                log(result);
            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
            }
        });
    }

    private void log(String msg) {
        mHandler.post(() -> {
            mLogView.append(msg + "\n\n");
            mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
}
