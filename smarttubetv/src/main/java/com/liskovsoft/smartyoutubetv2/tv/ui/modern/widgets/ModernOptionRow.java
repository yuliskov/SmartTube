package com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;

/**
 * Row of a settings page. Its switch, checkbox or radio state is drawn as an icon only,
 * so the row tells accessibility services (and UI tests) whether it's checked.<br/>
 * Done in the view itself: a RecyclerView replaces the accessibility delegate of its items.
 */
public class ModernOptionRow extends LinearLayout {
    public static final int ROLE_NONE = 0;
    public static final int ROLE_SWITCH = 1;
    public static final int ROLE_CHECKBOX = 2;
    public static final int ROLE_RADIO = 3;
    private Boolean mChecked;
    private int mRole;

    public ModernOptionRow(Context context) {
        super(context);
    }

    public ModernOptionRow(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ModernOptionRow(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * @param role one of the ROLE constants, {@link #ROLE_NONE} for plain rows
     */
    public void setCheckedState(int role, boolean checked) {
        Boolean state = role != ROLE_NONE ? checked : null;

        if (mRole == role && ObjectsCompat.equals(mChecked, state)) {
            return;
        }

        mRole = role;
        mChecked = state;

        AccessibilityManager manager = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);

        if (Build.VERSION.SDK_INT >= 19 && manager != null && manager.isEnabled() && getParent() != null) {
            getParent().notifySubtreeAccessibilityStateChanged(this, this, AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(mChecked != null);
        info.setChecked(Boolean.TRUE.equals(mChecked));

        // Read like the matching widget (e.g. "radio button, checked")
        switch (mRole) {
            case ROLE_SWITCH:
                info.setClassName(Switch.class.getName());
                break;
            case ROLE_CHECKBOX:
                info.setClassName(CheckBox.class.getName());
                break;
            case ROLE_RADIO:
                info.setClassName(RadioButton.class.getName());
                break;
        }
    }
}
