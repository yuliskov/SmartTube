package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.time;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

import com.liskovsoft.sharedutils.helpers.DateHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.R;

@SuppressLint("AppCompatCustomView")
public class EndingTimeView extends TextView implements TickleListener, OnDataChange {
    private TickleManager mTickleManager;
    private PlayerData mPlayerData;
    private boolean mIconIsSet;

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
        mPlayerData = PlayerData.instance(getContext());
        updateListener();
    }

    private void setIcon() {
        Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.icon_hourglass_bottom);
        if (drawable != null) {
            drawable.setBounds(3, 3, getLineHeight(), getLineHeight()); // add bounds to align vertically
            setCompoundDrawables(drawable, null, null, null);
            mIconIsSet = true;
        }
    }

    private void updateListener() {
        if (getVisibility() == View.VISIBLE) {
            mTickleManager.addListener(this);
            mPlayerData.setOnChange(this);
        } else {
            mTickleManager.removeListener(this);
            mPlayerData.removeOnChange(this);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        updateListener();
    }

    @Override
    public void onDataChange() {
        update();
    }

    @Override
    public void onTickle() {
        update();
    }

    public void update() {
        if (getVisibility() == View.VISIBLE) {
            String endingTime = getEndingTime();

            setText(!TextUtils.isEmpty(endingTime) ? String.format("%s %s", Helpers.HOURGLASS, endingTime) : null);

            //if (endingTime != null) {
            //    // https://stackoverflow.com/questions/5437674/what-unicode-characters-represent-time/9454080
            //    //setText(TextUtils.concat( Utils.icon(getContext(), R.drawable.action_pip, getLineHeight()), " ", endingTime));
            //    setText(String.format("⌛ %s", endingTime));
            //    //setText(String.format("(%s)", endingTime));
            //
            //
            //    // Use external icon
            //    //setText(endingTime);
            //    //if (!mIconIsSet) {
            //    //    setIcon();
            //    //}
            //}
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

        if (playbackView != null && playbackView.getVideo() != null && !playbackView.getVideo().isLive) {
            remainingTimeMs = playbackView.getDurationMs() - playbackView.getPositionMs();
            remainingTimeMs = applySpeedCorrection(remainingTimeMs);
        }

        if (remainingTimeMs == 0) {
            return null;
        }

        return DateHelper.toShortTime(System.currentTimeMillis() + remainingTimeMs);
    }

    private long applySpeedCorrection(long timeMs) {
        timeMs = (long) (timeMs / mPlayerData.getSpeed());

        return timeMs >= 0 ? timeMs : 0;
    }
}
