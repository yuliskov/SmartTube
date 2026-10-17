package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Lists that scroll under a title (side panels, comments): rows partially out of the list fade into the
 * background at the top and the bottom edge instead of being cut.<br/>
 * Drawn over the rows because leanback grids don't report their scroll offset (no system fading edges).
 */
public class ModernEdgeFade extends RecyclerView.ItemDecoration {
    private final Paint mPaint = new Paint();
    private final int mLength;
    private final int mColor;
    private int mShaderHeight = -1;
    private LinearGradient mTop;
    private LinearGradient mBottom;

    public ModernEdgeFade(int color, int length) {
        mColor = color;
        mLength = length;
    }

    /**
     * Adds the fade to every list inside the view (once per list).
     */
    public static void attachAll(View view, int color, int length) {
        if (view instanceof RecyclerView) {
            if (view.getTag(R.id.modern_edge_fade) == null) {
                view.setTag(R.id.modern_edge_fade, Boolean.TRUE);
                ((RecyclerView) view).addItemDecoration(new ModernEdgeFade(color, length));
            }
            return;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                attachAll(group.getChildAt(i), color, length);
            }
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int count = parent.getChildCount();
        RecyclerView.Adapter<?> adapter = parent.getAdapter();

        if (count == 0 || adapter == null) {
            return;
        }

        // Fade where the rows are actually cut: lists that don't clip to their padding draw rows into it, a fade at the
        // padding line would cross the rows below it (a dark band in the middle of the settings pages)
        boolean clipToPadding = parent.getClipToPadding();
        int top = clipToPadding ? parent.getPaddingTop() : 0;
        int bottom = parent.getHeight() - (clipToPadding ? parent.getPaddingBottom() : 0);
        View highest = parent.getChildAt(0);
        View lowest = highest;

        for (int i = 1; i < count; i++) {
            View child = parent.getChildAt(i);
            if (child.getTop() < highest.getTop()) {
                highest = child;
            }
            if (child.getBottom() > lowest.getBottom()) {
                lowest = child;
            }
        }

        // Chat lists are laid out from the bottom (position 0 is the lowest row)
        boolean reversed = parent.getLayoutManager() instanceof LinearLayoutManager
                && ((LinearLayoutManager) parent.getLayoutManager()).getReverseLayout();
        int lastPosition = adapter.getItemCount() - 1;
        int topEnd = reversed ? lastPosition : 0;
        int bottomEnd = reversed ? 0 : lastPosition;
        // A row out by a pixel or two (alignment rounding) isn't cut: no fade over the first / last row of a short list
        int slop = mLength / 4;
        boolean moreAbove = highest.getTop() < top - slop || parent.getChildAdapterPosition(highest) != topEnd;
        boolean moreBelow = lowest.getBottom() > bottom + slop || parent.getChildAdapterPosition(lowest) != bottomEnd;

        updateShaders(top, bottom);

        if (moreAbove) {
            mPaint.setShader(mTop);
            canvas.drawRect(0, top, parent.getWidth(), top + mLength, mPaint);
        }

        if (moreBelow) {
            mPaint.setShader(mBottom);
            canvas.drawRect(0, bottom - mLength, parent.getWidth(), bottom, mPaint);
        }
    }

    private void updateShaders(int top, int bottom) {
        if (mShaderHeight == bottom + top * 31) {
            return;
        }

        mShaderHeight = bottom + top * 31;
        int transparent = Color.argb(0, Color.red(mColor), Color.green(mColor), Color.blue(mColor));
        mTop = new LinearGradient(0, top, 0, top + mLength, mColor, transparent, Shader.TileMode.CLAMP);
        mBottom = new LinearGradient(0, bottom - mLength, 0, bottom, transparent, mColor, Shader.TileMode.CLAMP);
    }
}
