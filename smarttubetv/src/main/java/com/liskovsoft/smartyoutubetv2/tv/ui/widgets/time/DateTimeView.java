package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.time;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import androidx.appcompat.widget.AppCompatTextView;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.DateFormatter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Note, same view is used inside player and in as global time view
 */
public class DateTimeView extends AppCompatTextView implements TickleListener {
    private TickleManager mTickleManager;
    private boolean mIsDateEnabled = true;
    private boolean mIsEndingTimeEnabled = false;

    public DateTimeView(Context context) {
        super(context);
        init();
    }

    public DateTimeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DateTimeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mTickleManager = TickleManager.instance();
        updateListener();
    }

    private void updateListener() {
        mTickleManager.removeListener(this);

        if (getVisibility() == View.VISIBLE) {
            mTickleManager.addListener(this);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        updateListener();
    }

    @Override
    public void onTickle() {
        if (getVisibility() == View.VISIBLE) {
            List<String> infoItems = new ArrayList<>();
            infoItems.add(mIsDateEnabled ?
                    DateFormatter.getCurrentDateTimeShort(getContext()) : DateFormatter.getCurrentTimeShort(getContext()));
            if (mIsEndingTimeEnabled) {
                infoItems.add(getEndingTime());
            }

            setText(TextUtils.join("\n", infoItems));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Player has been closed
        mTickleManager.removeListener(this);
    }

    /**
     * Note, same view is used inside player and in as global time view
     */
    public void showDate(boolean show) {
        mIsDateEnabled = show;
    }

    public void showEndingTime(boolean show) {
        mIsEndingTimeEnabled = show;
    }

    private String getEndingTime() {
        PlaybackView playbackView = PlaybackPresenter.instance(getContext()).getView();

        long remainingTimeMs = 0;

        if (playbackView != null) {
            remainingTimeMs = playbackView.getController().getLengthMs() - playbackView.getController().getPositionMs();
            remainingTimeMs = applySpeedCorrection(remainingTimeMs);
        }

        return getContext().getString(R.string.player_ending_time, DateFormatter.formatTimeShort(getContext(), System.currentTimeMillis() + remainingTimeMs));
    }

    private long applySpeedCorrection(long timeMs) {
        timeMs = (long) (timeMs / PlayerData.instance(getContext()).getSpeed());

        return timeMs >= 0 ? timeMs : 0;
    }
}
