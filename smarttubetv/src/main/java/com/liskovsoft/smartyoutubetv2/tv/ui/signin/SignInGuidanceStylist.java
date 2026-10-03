package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.leanback.widget.GuidanceStylist;

final class SignInGuidanceStylist extends GuidanceStylist {
    private View mGuidanceView;
    private final ViewTreeObserver.OnGlobalLayoutListener mAlignmentListener = this::alignQrCode;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Guidance guidance) {
        mGuidanceView = super.onCreateView(inflater, container, guidance);
        mGuidanceView.getViewTreeObserver().addOnGlobalLayoutListener(mAlignmentListener);
        return mGuidanceView;
    }

    private void alignQrCode() {
        View icon = getIconView();
        View title = getTitleView();
        View description = getDescriptionView();
        if (icon == null || title == null || description == null) {
            return;
        }

        // Leanback centers icons on the title baseline. Center this QR on the whole text block
        // instead, after Leanback has positioned the text (including wrapped translations).
        float textCenter = (title.getTop() + description.getBottom()) / 2f;
        float iconCenter = (icon.getTop() + icon.getBottom()) / 2f;
        icon.setTranslationY(textCenter - iconCenter);
    }

    @Override
    public void onDestroyView() {
        if (mGuidanceView != null) {
            mGuidanceView.getViewTreeObserver().removeOnGlobalLayoutListener(mAlignmentListener);
            mGuidanceView = null;
        }
        super.onDestroyView();
    }
}
