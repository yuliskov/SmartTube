package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.styled;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ProgressBar;
import androidx.annotation.RequiresApi;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class StyledProgressBar extends ProgressBar {
    private static final String TAG = StyledProgressBar.class.getSimpleName();

    public StyledProgressBar(Context context) {
        super(context, null, R.attr.customProgressStyle);
    }

    public StyledProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs, R.attr.customProgressStyle);
    }

    public StyledProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, R.attr.customProgressStyle);
    }

    @RequiresApi(api = 21)
    public StyledProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, R.attr.customProgressStyle, defStyleRes);
    }
}
