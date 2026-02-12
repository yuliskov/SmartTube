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
package androidx.leanback.app;

import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.leanback.R;

/**
 * A fragment for displaying an error indication.
 */
public class ErrorSupportFragment extends BrandedSupportFragment {

    private ViewGroup mErrorFrame;
    private ImageView mImageView;
    private TextView mTextView;
    private Button mButton;
    private Drawable mDrawable;
    private CharSequence mMessage;
    private String mButtonText;
    private View.OnClickListener mButtonClickListener;
    private Drawable mBackgroundDrawable;
    private boolean mIsBackgroundTranslucent = true;

    /**
     * Sets the default background.
     *
     * @param translucent True to set a translucent background.
     */
    public void setDefaultBackground(boolean translucent) {
        mBackgroundDrawable = null;
        mIsBackgroundTranslucent = translucent;
        updateBackground();
        updateMessage();
    }

    /**
     * Returns true if the background is translucent.
     */
    public boolean isBackgroundTranslucent() {
        return mIsBackgroundTranslucent;
    }

    /**
     * Sets a drawable for the fragment background.
     *
     * @param drawable The drawable used for the background.
     */
    public void setBackgroundDrawable(Drawable drawable) {
        mBackgroundDrawable = drawable;
        if (drawable != null) {
            final int opacity = drawable.getOpacity();
            mIsBackgroundTranslucent = (opacity == PixelFormat.TRANSLUCENT
                    || opacity == PixelFormat.TRANSPARENT);
        }
        updateBackground();
        updateMessage();
    }

    /**
     * Returns the background drawable.  May be null if a default is used.
     */
    public Drawable getBackgroundDrawable() {
        return mBackgroundDrawable;
    }

    /**
     * Sets the drawable to be used for the error image.
     *
     * @param drawable The drawable used for the error image.
     */
    public void setImageDrawable(Drawable drawable) {
        mDrawable = drawable;
        updateImageDrawable();
    }

    /**
     * Returns the drawable used for the error image.
     */
    public Drawable getImageDrawable() {
        return mDrawable;
    }

    /**
     * Sets the error message.
     *
     * @param message The error message.
     */
    public void setMessage(CharSequence message) {
        mMessage = message;
        updateMessage();
    }

    /**
     * Returns the error message.
     */
    public CharSequence getMessage() {
        return mMessage;
    }

    /**
     * Sets the button text.
     *
     * @param text The button text.
     */
    public void setButtonText(String text) {
        mButtonText = text;
        updateButton();
    }

    /**
     * Returns the button text.
     */
    public String getButtonText() {
        return mButtonText;
    }

    /**
     * Set the button click listener.
     *
     * @param clickListener The click listener for the button.
     */
    public void setButtonClickListener(View.OnClickListener clickListener) {
        mButtonClickListener = clickListener;
        updateButton();
    }

    /**
     * Returns the button click listener.
     */
    public View.OnClickListener getButtonClickListener() {
        return mButtonClickListener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.lb_error_fragment, container, false);

        mErrorFrame = (ViewGroup) root.findViewById(R.id.error_frame);
        updateBackground();

        installTitleView(inflater, mErrorFrame, savedInstanceState);

        mImageView = (ImageView) root.findViewById(R.id.image);
        updateImageDrawable();

        mTextView = (TextView) root.findViewById(R.id.message);
        updateMessage();

        mButton = (Button) root.findViewById(R.id.button);
        updateButton();

        FontMetricsInt metrics = getFontMetricsInt(mTextView);
        int underImageBaselineMargin = container.getResources().getDimensionPixelSize(
                R.dimen.lb_error_under_image_baseline_margin);
        setTopMargin(mTextView, underImageBaselineMargin + metrics.ascent);

        int underMessageBaselineMargin = container.getResources().getDimensionPixelSize(
                R.dimen.lb_error_under_message_baseline_margin);
        setTopMargin(mButton, underMessageBaselineMargin - metrics.descent);

        return root;
    }

    private void updateBackground() {
        if (mErrorFrame != null) {
            if (mBackgroundDrawable != null) {
                mErrorFrame.setBackground(mBackgroundDrawable);
            } else {
                mErrorFrame.setBackgroundColor(mErrorFrame.getResources().getColor(
                        mIsBackgroundTranslucent
                                ? R.color.lb_error_background_color_translucent
                                : R.color.lb_error_background_color_opaque));
            }
        }
    }

    private void updateMessage() {
        if (mTextView != null) {
            mTextView.setText(mMessage);
            mTextView.setVisibility(TextUtils.isEmpty(mMessage) ? View.GONE : View.VISIBLE);
        }
    }

    private void updateImageDrawable() {
        if (mImageView != null) {
            mImageView.setImageDrawable(mDrawable);
            mImageView.setVisibility(mDrawable == null ? View.GONE : View.VISIBLE);
        }
    }

    private void updateButton() {
        if (mButton != null) {
            mButton.setText(mButtonText);
            mButton.setOnClickListener(mButtonClickListener);
            mButton.setVisibility(TextUtils.isEmpty(mButtonText) ? View.GONE : View.VISIBLE);
            mButton.requestFocus();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mErrorFrame.requestFocus();
    }

    private static FontMetricsInt getFontMetricsInt(TextView textView) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textView.getTextSize());
        paint.setTypeface(textView.getTypeface());
        return paint.getFontMetricsInt();
    }

    private static void setTopMargin(TextView textView, int topMargin) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) textView.getLayoutParams();
        lp.topMargin = topMargin;
        textView.setLayoutParams(lp);
    }

}
