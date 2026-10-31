package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.styled;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ProgressBar;
import androidx.annotation.RequiresApi;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class CardProgressBar extends ProgressBar {
    private static final String TAG = CardProgressBar.class.getSimpleName();

    public CardProgressBar(Context context) {
        super(context, null, R.attr.cardProgressStyle);
    }

    public CardProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs, R.attr.cardProgressStyle);
    }

    public CardProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, R.attr.cardProgressStyle);
    }

    @RequiresApi(api = 21)
    public CardProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, R.attr.cardProgressStyle, defStyleRes);
    }
}
