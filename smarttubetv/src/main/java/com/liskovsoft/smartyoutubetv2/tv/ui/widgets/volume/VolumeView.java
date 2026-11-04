package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.volume;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SeekBar;

import java.util.Locale;

/**
 * Transient volume indicator placed at the top of the player.
 * Replaces the volume toast, which couldn't show the level at a glance.
 */
public class VolumeView extends LinearLayout {
    private static final int HIDE_DELAY_MS = 2_000;
    private static final int MAX_VOLUME = 100;
    private final Runnable mHideCallback = () -> setVisibility(View.GONE);
    private SeekBar mSeekBar;
    private TextView mLabel;

    public VolumeView(Context context) {
        super(context);
        init();
    }

    public VolumeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VolumeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setBackgroundResource(R.drawable.volume_view_bg);
        LayoutInflater.from(getContext()).inflate(R.layout.volume_view, this, true);

        mSeekBar = findViewById(R.id.volume_seek_bar);
        mLabel = findViewById(R.id.volume_label);

        mSeekBar.setMax(MAX_VOLUME);
        mSeekBar.setProgressColor(Color.WHITE);
    }

    /**
     * Volume: 0 - 100
     */
    public void show(int volume) {
        mSeekBar.setProgress(volume);
        mLabel.setText(String.format(Locale.US, "%d%%", volume));

        setVisibility(View.VISIBLE);

        // Repeated presses keep it on screen instead of stacking hide timers
        removeCallbacks(mHideCallback);
        postDelayed(mHideCallback, HIDE_DELAY_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Player has been closed
        removeCallbacks(mHideCallback);
    }
}
