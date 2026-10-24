package com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.BrowseSupportFragment.MainFragmentAdapter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.CategoryEmptyError;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.SignInError;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.ErrorSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class ErrorDialogFragment extends ErrorSupportFragment implements BrowseSupportFragment.MainFragmentAdapterProvider {
    private static final boolean TRANSLUCENT = true;
    private static final int TIMER_DELAY = 1000;
    // Override style value 'lb_error_message_max_lines'
    private static final int NORMAL_MAX_LINES = 7;
    private static final int EXPANDED_MAX_LINES = 15;

    private final Handler mHandler = new Handler();
    private final ErrorFragmentData mDialogData;
    private final MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new MainFragmentAdapter<Fragment>(this) {
                @Override
                public void setEntranceTransitionState(boolean state) {
                    //setEntranceTransitionState(state);
                }
            };

    public ErrorDialogFragment() {
        // "could not find Fragment constructor" fix
        this(null);
    }

    public ErrorDialogFragment(ErrorFragmentData dialogData) {
        mDialogData = dialogData;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();

        setDialogContent();
    }

    private void setDialogContent() {
        if (mDialogData == null || getActivity() == null) {
            return;
        }

        if (mDialogData instanceof CategoryEmptyError || mDialogData instanceof SignInError) {
            setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.lb_ic_sad_cloud));
        }

        setMessage(mDialogData.getMessage());

        TextView mTextView = (TextView) Helpers.getField(this, "mTextView");
        ImageView mImageView = (ImageView) Helpers.getField(this, "mImageView");
        if (mTextView != null && mImageView != null) {
            mTextView.setMaxLines(mImageView.getVisibility() == View.GONE ? EXPANDED_MAX_LINES : NORMAL_MAX_LINES);
        }

        if (mDialogData.getActionText() != null) {
            setButtonText(mDialogData.getActionText());
            setButtonClickListener(v -> mDialogData.onAction());
        } else {
            Button mButton = (Button) Helpers.getField(this, "mButton");

            if (mButton != null) {
                mButton.setVisibility(View.GONE);
            }
        }

        if (ViewUtil.isYouTubeStyle(getActivity())) {
            applyYouTubeStyle(mImageView, mTextView, (Button) Helpers.getField(this, "mButton"));
        }
    }

    /**
     * YouTube TV look: account icon for sign in, bold message, rounded "Sign in" button
     */
    private void applyYouTubeStyle(ImageView imageView, TextView textView, Button button) {
        boolean isSignIn = mDialogData instanceof SignInError || (mDialogData instanceof CategoryEmptyError && mDialogData.getActionText() != null);

        if (imageView != null && isSignIn) {
            imageView.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.youtube_account));
        }

        if (textView != null) {
            textView.setTextColor(ContextCompat.getColor(getActivity(), R.color.white));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }

        View errorFrame = (View) Helpers.getField(this, "mErrorFrame");
        if (errorFrame != null) {
            errorFrame.setBackgroundColor(Color.TRANSPARENT); // app background instead of a black panel
        }

        if (button != null) {
            // "SIGN IN" -> "Sign in"
            String text = button.getText().toString();
            if (!text.isEmpty()) {
                button.setText(text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase());
            }

            int padding = getResources().getDimensionPixelSize(R.dimen.youtube_pill_button_padding);
            button.setBackground(ContextCompat.getDrawable(getActivity(), R.drawable.youtube_search_pill));
            button.setTextColor(ContextCompat.getColorStateList(getActivity(), R.color.youtube_sidebar_item_text));
            button.setAllCaps(false);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            button.setPadding(padding, 0, padding, 0);
        }
    }

    @Override
    public MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }
}
