/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.ActionMode;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;
import androidx.leanback.R;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows the recognized text as a continuous stream of words.
 */
class StreamingTextView extends EditText {

    private static final boolean DEBUG = false;
    private static final String TAG = "StreamingTextView";

    private static final float TEXT_DOT_SCALE = 1.3F;
    private static final boolean DOTS_FOR_STABLE = false;
    private static final boolean DOTS_FOR_PENDING = true;
    static final boolean ANIMATE_DOTS_FOR_PENDING = true;

    private static final long STREAM_UPDATE_DELAY_MILLIS = 50;

    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\S+");

    private static final Property<StreamingTextView,Integer> STREAM_POSITION_PROPERTY =
            new Property<StreamingTextView,Integer>(Integer.class, "streamPosition") {

        @Override
        public Integer get(StreamingTextView view) {
            return view.getStreamPosition();
        }

        @Override
        public void set(StreamingTextView view, Integer value) {
            view.setStreamPosition(value);
        }
    };

    final Random mRandom = new Random();

    Bitmap mOneDot;
    Bitmap mTwoDot;

    int mStreamPosition;
    private ObjectAnimator mStreamingAnimation;

    public StreamingTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StreamingTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mOneDot = getScaledBitmap(R.drawable.lb_text_dot_one, TEXT_DOT_SCALE);
        mTwoDot = getScaledBitmap(R.drawable.lb_text_dot_two, TEXT_DOT_SCALE);

        reset();
    }

    private Bitmap getScaledBitmap(int resourceId, float scaled) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resourceId);
        return Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * scaled),
                (int) (bitmap.getHeight() * scaled), false);
    }

    /**
     * Resets the text view.
     */
    public void reset() {
        if (DEBUG) Log.d(TAG, "#reset");

        mStreamPosition = -1;
        cancelStreamAnimation();
        setText("");
    }

    /**
     * Updates the recognized text.
     */
    public void updateRecognizedText(String stableText, String pendingText) {
        if (DEBUG) Log.d(TAG, "updateText(" + stableText + "," + pendingText + ")");

        if (stableText == null) {
            stableText = "";
        }

        SpannableStringBuilder displayText = new SpannableStringBuilder(stableText);

        if (DOTS_FOR_STABLE) {
            addDottySpans(displayText, stableText, 0);
        }

        if (pendingText != null) {
            int pendingTextStart = displayText.length();
            displayText.append(pendingText);
            if (DOTS_FOR_PENDING) {
                addDottySpans(displayText, pendingText, pendingTextStart);
            } else {
                int pendingColor = getResources().getColor(
                        R.color.lb_search_plate_hint_text_color);
                addColorSpan(displayText, pendingColor, pendingText, pendingTextStart);
            }
        }

        // Start streaming in dots from beginning of partials, or current position,
        // whichever is larger
        mStreamPosition = Math.max(stableText.length(), mStreamPosition);

        // Copy the text and spans to a SpannedString, since editable text
        // doesn't redraw in invalidate() when hardware accelerated
        // if the text or spans haven't changed. (probably a framework bug)
        updateText(new SpannedString(displayText));

        if (ANIMATE_DOTS_FOR_PENDING) {
            startStreamAnimation();
        }
    }

    int getStreamPosition() {
        return mStreamPosition;
    }

    void setStreamPosition(int streamPosition) {
        mStreamPosition = streamPosition;
        invalidate();
    }

    private void startStreamAnimation() {
        cancelStreamAnimation();
        int pos = getStreamPosition();
        int totalLen = length();
        int animLen = totalLen - pos;
        if (animLen > 0) {
            if (mStreamingAnimation == null) {
                mStreamingAnimation = new ObjectAnimator();
                mStreamingAnimation.setTarget(this);
                mStreamingAnimation.setProperty(STREAM_POSITION_PROPERTY);
            }
            mStreamingAnimation.setIntValues(pos, totalLen);
            mStreamingAnimation.setDuration(STREAM_UPDATE_DELAY_MILLIS * animLen);
            mStreamingAnimation.start();
        }
    }

    private void cancelStreamAnimation() {
        if (mStreamingAnimation != null) {
            mStreamingAnimation.cancel();
        }
    }

    private void addDottySpans(SpannableStringBuilder displayText, String text, int textStart) {
        Matcher m = SPLIT_PATTERN.matcher(text);
        while (m.find()) {
            int wordStart = textStart + m.start();
            int wordEnd = textStart + m.end();
            DottySpan span = new DottySpan(text.charAt(m.start()), wordStart);
            displayText.setSpan(span, wordStart, wordEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void addColorSpan(SpannableStringBuilder displayText, int color, String text,
            int textStart) {
        ForegroundColorSpan span = new ForegroundColorSpan(color);
        int start = textStart;
        int end = textStart + text.length();
        displayText.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * Sets the final, non changing, full text result. This should only happen at the very end of
     * a recognition.
     *
     * @param finalText to the view to.
     */
    public void setFinalRecognizedText(CharSequence finalText) {
        if (DEBUG) Log.d(TAG, "setFinalRecognizedText(" + finalText + ")");

        updateText(finalText);
    }

    private void updateText(CharSequence displayText) {
        setText(displayText);
        bringPointIntoView(length());
    }

    /**
     * This is required to make the View findable by uiautomator.
     */
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(StreamingTextView.class.getCanonicalName());
    }

    private class DottySpan extends ReplacementSpan {

        private final int mSeed;
        private final int mPosition;

        public DottySpan(int seed, int pos) {
            mSeed = seed;
            mPosition = pos;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, Paint paint) {

            int width = (int) paint.measureText(text, start, end);

            int dotWidth = mOneDot.getWidth();
            int sliceWidth = 2 * dotWidth;
            int sliceCount = width / sliceWidth;
            int excess = width % sliceWidth;
            int prop = excess / 2;
            boolean rtl = isLayoutRtl(StreamingTextView.this);

            mRandom.setSeed(mSeed);
            int oldAlpha = paint.getAlpha();
            for (int i = 0; i < sliceCount; i++) {
                if (ANIMATE_DOTS_FOR_PENDING) {
                    if (mPosition + i >= mStreamPosition) break;
                }

                float left = i * sliceWidth + prop + dotWidth / 2;
                float dotLeft = rtl ? x + width - left - dotWidth : x + left;

                // give the dots some visual variety
                paint.setAlpha((mRandom.nextInt(4) + 1) * 63);

                if (mRandom.nextBoolean()) {
                    canvas.drawBitmap(mTwoDot, dotLeft, y - mTwoDot.getHeight(), paint);
                } else {
                    canvas.drawBitmap(mOneDot, dotLeft, y - mOneDot.getHeight(), paint);
                }
            }
            paint.setAlpha(oldAlpha);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end,
                Paint.FontMetricsInt fontMetricsInt) {
            return (int) paint.measureText(text, start, end);
        }
    }

    public static boolean isLayoutRtl(View view) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return View.LAYOUT_DIRECTION_RTL == view.getLayoutDirection();
        } else {
            return false;
        }
    }

    public void updateRecognizedText(String stableText, List<Float> rmsValues) {}

    /**
     * See
     * {@link TextViewCompat#setCustomSelectionActionModeCallback(TextView, ActionMode.Callback)}
     */
    @Override
    public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
        super.setCustomSelectionActionModeCallback(TextViewCompat
                .wrapCustomSelectionActionModeCallback(this, actionModeCallback));
    }
}
