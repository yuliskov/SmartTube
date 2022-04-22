package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.time;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.DateFormatter;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class EndingTimeView extends AppCompatTextView implements TickleListener {
    private TickleManager mTickleManager;

    public EndingTimeView(Context context) {
        super(context);
        init();
    }

    public EndingTimeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EndingTimeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mTickleManager = TickleManager.instance();
        setIcon();
        updateListener();
    }

    private void setIcon() {
        Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.icon_hourglass_bottom);
        if (drawable != null) {
            drawable.setBounds(3, 3, getLineHeight(), getLineHeight()); // add bounds to align vertically
            setCompoundDrawables(drawable, null, null, null);
        }
    }

    private void updateListener() {
        if (getVisibility() == View.VISIBLE) {
            mTickleManager.addListener(this);
        } else {
            mTickleManager.removeListener(this);
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
            String endingTime = getEndingTime();
            if (endingTime != null) {
                // https://stackoverflow.com/questions/5437674/what-unicode-characters-represent-time/9454080
                setText(endingTime);
                //setText(TextUtils.concat( Utils.icon(getContext(), R.drawable.action_pip, getLineHeight()), " ", endingTime));
                //setText(String.format("⌛ %s", endingTime));
                //setText(String.format("(%s)", endingTime));
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Player has been closed
        mTickleManager.removeListener(this);
    }

    private String getEndingTime() {
        PlaybackView playbackView = PlaybackPresenter.instance(getContext()).getView();

        long remainingTimeMs = 0;

        if (playbackView != null) {
            remainingTimeMs = playbackView.getController().getLengthMs() - playbackView.getController().getPositionMs();
            remainingTimeMs = applySpeedCorrection(remainingTimeMs);
        }

        if (remainingTimeMs == 0) {
            return null;
        }

        return DateFormatter.formatTimeShort(getContext(), System.currentTimeMillis() + remainingTimeMs);
    }

    private long applySpeedCorrection(long timeMs) {
        timeMs = (long) (timeMs / PlayerData.instance(getContext()).getSpeed());

        return timeMs >= 0 ? timeMs : 0;
    }
}
