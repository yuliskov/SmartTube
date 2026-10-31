package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.layout;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.leanback.widget.HorizontalGridView;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * NOT working!
 */
public class TouchHorizontalGridView extends HorizontalGridView {
    private static final String TAG = TouchHorizontalGridView.class.getSimpleName();

    public TouchHorizontalGridView(Context context) {
        super(context);
        init();
    }

    public TouchHorizontalGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TouchHorizontalGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        addOnScrollListener(new OnScrollListener() {
            private int mLastState;
            private int mLastPosition;
            private int mLastDx;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mLastState = newState;
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (mLastState != RecyclerView.SCROLL_STATE_DRAGGING || dx == 0) {
                    return;
                }

                Log.d(TAG, dx);

                int width = recyclerView.getLayoutManager().getChildAt(0).getWidth();

                mLastDx += dx;

                if (Math.abs(mLastDx) < (width / 2)) {
                    return;
                }

                if (dy > 0) {
                    recyclerView.smoothScrollToPosition(mLastPosition += 1);
                } else {
                    recyclerView.smoothScrollToPosition(mLastPosition -= 1);
                }

                mLastDx = 0;
            }
        });
    }
}
