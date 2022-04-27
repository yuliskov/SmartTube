package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.browse;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.leanback.widget.SearchOrbView;
import androidx.leanback.widget.TitleView;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.TooltipCompatHandler;

import static androidx.leanback.widget.TitleViewAdapter.SEARCH_VIEW_VISIBLE;

/**
 * View that supports dpad navigation between children<br/>
 * NOTE: You should set android:nextFocusLeft and android:nextFocusRight<br/>
 * https://stackoverflow.com/questions/38169378/use-multiple-orb-buttons-or-other-buttons-in-the-leanbacks-title-view<br/>
 * https://stackoverflow.com/questions/40802470/add-button-to-browsefragment
 */
public class NavigateTitleView extends TitleView {
    private SearchOrbView mAccountView;
    private SearchOrbView mExitPip;

    public NavigateTitleView(Context context) {
        super(context);
    }

    public NavigateTitleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NavigateTitleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // findViewById is null in constructor. Init mAccountView later.
    }

    @Override
    public View focusSearch(View focused, int direction) {

        View nextFoundFocusableViewInLayout = null;

        // Only concerned about focusing left and right at the moment
        if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {

            // Try to find the next focusable item in this layout for the supplied direction
            int nextFoundFocusableViewInLayoutId = -1;
            switch(direction) {
                case View.FOCUS_LEFT :
                    nextFoundFocusableViewInLayoutId = focused.getNextFocusLeftId();
                    break;
                case View.FOCUS_RIGHT :
                    nextFoundFocusableViewInLayoutId = focused.getNextFocusRightId();
                    break;
            }

            // View id for next focus direction found....get the View
            if (nextFoundFocusableViewInLayoutId != -1) {
                nextFoundFocusableViewInLayout = findViewById(nextFoundFocusableViewInLayoutId);
            }
        }

        //  Return the found View in the layout if it's focusable
        if (nextFoundFocusableViewInLayout != null && nextFoundFocusableViewInLayout != focused && nextFoundFocusableViewInLayout.isFocusable()) {
            if (nextFoundFocusableViewInLayout.getVisibility() == View.VISIBLE) {
                return nextFoundFocusableViewInLayout;
            } else {
                return focusSearch(nextFoundFocusableViewInLayout, direction);
            }
        } else {
            // No focusable view found in layout...propagate to super (should invoke the BrowseFrameLayout.OnFocusSearchListener
            return super.focusSearch(focused, direction);
        }
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        // Gives focus to the SearchOrb first....if not...default to normal descendant focus search
        return getSearchAffordanceView().requestFocus() || super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    public void updateComponentsVisibility(int flags) {
        super.updateComponentsVisibility(flags);

        int visibility = (flags & SEARCH_VIEW_VISIBLE) == SEARCH_VIEW_VISIBLE
                ? View.VISIBLE : View.INVISIBLE;

        if (mAccountView != null) {
            mAccountView.setVisibility(visibility);
        }

        if (mExitPip != null && (PlaybackPresenter.instance(getContext()).isRunningInBackground() || visibility != View.VISIBLE)) {
            mExitPip.setVisibility(visibility);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (MainUIData.instance(getContext()).isButtonEnabled(MainUIData.BUTTON_BROWSE_ACCOUNTS)) {
            mAccountView = (SearchOrbView) findViewById(R.id.account_orb);
            mAccountView.setOnOrbClickedListener(v -> AccountSettingsPresenter.instance(getContext()).show());
            TooltipCompatHandler.setTooltipText(mAccountView, getContext().getString(R.string.settings_accounts));
        }

        if (Helpers.isPictureInPictureSupported(getContext())) {
            mExitPip = (SearchOrbView) findViewById(R.id.exit_pip);
            mExitPip.setOnOrbClickedListener(v -> {
                if (PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
                    ViewManager.instance(getContext()).startView(PlaybackView.class);
                }
            });
            TooltipCompatHandler.setTooltipText(mExitPip, getContext().getString(R.string.return_to_background_video));
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == View.VISIBLE && mExitPip != null) {
            mExitPip.setVisibility(PlaybackPresenter.instance(getContext()).isRunningInBackground() ? View.VISIBLE : View.GONE);
        }
    }
}
