package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.layout;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * NOT working!<br/>
 * lb_rows_fragment.xml<br/>
 * lb_vertical_grid.xml
 */
public class TouchVerticalGridView extends VerticalGridView {
    private static final String TAG = TouchVerticalGridView.class.getSimpleName();

    public TouchVerticalGridView(Context context) {
        super(context);
        init();
    }

    public TouchVerticalGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TouchVerticalGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        addOnScrollListener(new OnScrollListener() {
            private int mLastState;
            private int mLastPosition;
            private int mLastDy;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mLastState = newState;
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (mLastState != RecyclerView.SCROLL_STATE_DRAGGING || dy == 0) {
                    return;
                }

                Log.d(TAG, dy);

                int height = recyclerView.getLayoutManager().getChildAt(0).getHeight();

                mLastDy += dy;

                if (Math.abs(mLastDy) < (height / 2)) {
                    return;
                }

                if (dy > 0) {
                    recyclerView.smoothScrollToPosition(mLastPosition += 1);
                } else {
                    recyclerView.smoothScrollToPosition(mLastPosition -= 1);
                }

                mLastDy = 0;
            }
        });
    }
}
