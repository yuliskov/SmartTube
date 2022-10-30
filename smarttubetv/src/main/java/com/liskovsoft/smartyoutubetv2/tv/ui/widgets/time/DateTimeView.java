package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.time;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;
import com.liskovsoft.smartyoutubetv2.common.utils.DateFormatter;

/**
 * Note, same view is used inside player and in as global time view
 */
public class DateTimeView extends TextView implements TickleListener {
    private TickleManager mTickleManager;
    private boolean mIsDateEnabled = true;
    private boolean mIsTimeEnabled = true;

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
            String time;

            if (mIsDateEnabled && !mIsTimeEnabled) {
                time = DateFormatter.getCurrentDateShort(getContext());
            } else if (!mIsDateEnabled && mIsTimeEnabled) {
                time = DateFormatter.getCurrentTimeShort(getContext());
            } else {
                time = DateFormatter.getCurrentDateTimeShort(getContext());
            }

            // https://stackoverflow.com/questions/5437674/what-unicode-characters-represent-time/9454080
            //setText(String.format("⌚ %s", time));
            setText(time);
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

    public void showTime(boolean show) {
        mIsTimeEnabled = show;
    }
}
