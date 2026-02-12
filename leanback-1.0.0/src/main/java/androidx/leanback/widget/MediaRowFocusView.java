/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.RestrictTo;
import androidx.leanback.R;

/**
 * Creates a view for a media item row in a playlist
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class MediaRowFocusView extends View {

    private final Paint mPaint;
    private final RectF mRoundRectF = new RectF();
    private int mRoundRectRadius;

    public MediaRowFocusView(Context context) {
        super(context);
        mPaint = createPaint(context);
    }

    public MediaRowFocusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint = createPaint(context);
    }

    public MediaRowFocusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mPaint = createPaint(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mRoundRectRadius = getHeight() / 2;
        int drawHeight = 2 * mRoundRectRadius;
        int drawOffset = (drawHeight - getHeight()) / 2;
        mRoundRectF.set(0, -drawOffset, getWidth(), getHeight() + drawOffset);
        canvas.drawRoundRect(mRoundRectF, mRoundRectRadius, mRoundRectRadius, mPaint);
    }

    private Paint createPaint(Context context) {
        Paint paint = new Paint();
        paint.setColor(context.getResources().getColor(
                R.color.lb_playback_media_row_highlight_color));
        return paint;
    }

    public int getRoundRectRadius() {
        return mRoundRectRadius;
    }
}