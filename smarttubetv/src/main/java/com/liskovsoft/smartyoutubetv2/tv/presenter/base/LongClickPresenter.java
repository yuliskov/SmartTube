package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.tv.R;

public abstract class LongClickPresenter extends Presenter {
    private OnItemLongPressedListener mLongPressedListener;
    private Boolean mLongPressDisabled;

    public void setOnItemViewLongPressedListener(OnItemLongPressedListener listener) {
        mLongPressedListener = listener;
    }

    /**
     * Don't forget to call this method in descendants!
     */
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (mLongPressDisabled == null) {
            mLongPressDisabled = GeneralData.instance(viewHolder.view.getContext()).isOkButtonLongPressDisabled();
        }

        viewHolder.view.setOnLongClickListener(v -> {
            if (mLongPressedListener != null && !mLongPressDisabled) {
                mLongPressedListener.onItemLongPressed(viewHolder, item);

                return true; // don't provoke single click event
            }

            return false; // work as usual
        });

        viewHolder.view.setOnKeyListener((v, keyCode, event) -> {
            if (mLongPressedListener != null) {
                if (KeyHelpers.isMenuKey(keyCode)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        mLongPressedListener.onItemLongPressed(viewHolder, item);
                    }
                }
            }

            return false; // enable navigation events
        });

        if (VERSION.SDK_INT >= 21) {
            viewHolder.view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);

                    if (mLongPressedListener != null && !Boolean.TRUE.equals(mLongPressDisabled)) {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                                R.id.action_context_menu,
                                host.getContext().getString(R.string.action_more_options)));
                    }
                }

                @Override
                public boolean performAccessibilityAction(View host, int action, Bundle args) {
                    if (action == R.id.action_context_menu && mLongPressedListener != null) {
                        mLongPressedListener.onItemLongPressed(viewHolder, item);

                        return true;
                    }

                    return super.performAccessibilityAction(host, action, args);
                }
            });
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        if (mLongPressedListener != null) {
            viewHolder.view.setOnLongClickListener(null);
            viewHolder.view.setOnKeyListener(null);
        }

        if (VERSION.SDK_INT >= 21) {
            viewHolder.view.setAccessibilityDelegate(null);
        }
    }
}
