package com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse.RailModel;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernIcons;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernText;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Left navigation rail. Collapsed it shows icons only, focused it expands and shows labels.<br/>
 * The rail has a fixed size: expanding only animates alpha and translations (no relayout per frame).<br/>
 * While collapsed it can't take focus by itself, the host enters it with {@link #focusSelected()}.
 */
public class ModernNavRail extends FrameLayout {
    private static final int SECTION_FOCUS_DELAY_MS = 300;
    private static final int TYPE_ACCOUNT = 0;
    private static final int TYPE_SEARCH = 1;
    private static final int TYPE_SECTION = 2;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<View> mItems = new ArrayList<>();
    private final View mBackground;
    private final LinearLayout mList;
    private final ScrollView mScroll;
    private final FrameLayout mBottom;
    private final View mAccountItem;
    private final int mListShiftCollapsed;
    private final int mDividerSpace;
    private final int mPrimaryColor;
    private final int mInactiveColor;
    private final int mOnLightColor;
    private Listener mListener;
    private RailModel mModel;
    private int mSelectedId = -1;
    private boolean mIsExpanded;
    private boolean mHasAvatar;
    private float mExpandFraction;
    private ValueAnimator mAnimator;
    private Runnable mPendingFocus;
    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusListener = (oldFocus, newFocus) -> onFocusMoved(newFocus);

    public interface Listener {
        void onSectionFocused(int sectionId);
        void onSectionClicked(int sectionId);
        void onSectionLongPressed(int sectionId);
        void onSearchClicked();
        void onAccountClicked();
        void onAccountLongClicked();
        void onExpandChanged(float fraction);
    }

    private static final class Entry {
        final int type;
        final int id;

        Entry(int type, int id) {
            this.type = type;
            this.id = id;
        }
    }

    public ModernNavRail(Context context) {
        super(context);

        Resources res = getResources();
        mListShiftCollapsed = res.getDimensionPixelSize(R.dimen.modern_rail_first_item_y_collapsed)
                - res.getDimensionPixelSize(R.dimen.modern_rail_first_item_y_expanded);
        mDividerSpace = res.getDimensionPixelSize(R.dimen.modern_rail_divider) + 2 * res.getDimensionPixelSize(R.dimen.modern_rail_divider_margin);
        mPrimaryColor = ContextCompat.getColor(context, R.color.modern_text_primary);
        mInactiveColor = ContextCompat.getColor(context, R.color.modern_icon_inactive);
        mOnLightColor = ContextCompat.getColor(context, R.color.modern_on_light);
        int itemX = res.getDimensionPixelSize(R.dimen.modern_rail_item_x);
        int itemHeight = res.getDimensionPixelSize(R.dimen.modern_rail_item_height);

        // Collapsed the rail is transparent so tiles next to it are never covered
        mBackground = new View(context);
        mBackground.setBackgroundColor(ModernColorScheme.brand(context));
        mBackground.setAlpha(0);
        addView(mBackground, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mAccountItem = createItem(new Entry(TYPE_ACCOUNT, -1));
        LayoutParams accountParams = new LayoutParams(LayoutParams.MATCH_PARENT, itemHeight);
        accountParams.leftMargin = itemX;
        accountParams.rightMargin = itemX;
        accountParams.topMargin = res.getDimensionPixelSize(R.dimen.modern_rail_account_y);
        addView(mAccountItem, accountParams);

        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setVerticalFadingEdgeEnabled(true);
        mScroll.setFadingEdgeLength(itemHeight);
        mList = new LinearLayout(context);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setPadding(itemX, 0, itemX, 0);
        mScroll.addView(mList, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams scrollParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        scrollParams.topMargin = res.getDimensionPixelSize(R.dimen.modern_rail_first_item_y_expanded);
        scrollParams.bottomMargin = res.getDimensionPixelSize(R.dimen.modern_rail_settings_bottom) + itemHeight
                + res.getDimensionPixelSize(R.dimen.modern_rail_divider_margin);
        addView(mScroll, scrollParams);

        mBottom = new FrameLayout(context);
        mBottom.setPadding(itemX, 0, itemX, 0);
        LayoutParams bottomParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomParams.bottomMargin = res.getDimensionPixelSize(R.dimen.modern_rail_settings_bottom);
        addView(mBottom, bottomParams);

        setLayoutParams(new ViewGroup.LayoutParams(res.getDimensionPixelSize(R.dimen.modern_rail_width_expanded), ViewGroup.LayoutParams.MATCH_PARENT));
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        applyExpand();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public int getExpandShift() {
        return getResources().getDimensionPixelSize(R.dimen.modern_rail_content_shift_expanded);
    }

    /**
     * Rebuild items after sections changed.
     */
    public void setModel(RailModel model) {
        mModel = model;
        View focused = findFocus();
        Entry focusedEntry = focused != null ? findEntry(focused) : null;

        mList.removeAllViews();
        mBottom.removeAllViews();
        mItems.clear();
        mItems.add(mAccountItem);

        addListItem(createItem(new Entry(TYPE_SEARCH, -1)));

        View settingsItem = null;

        for (int i = 0; i < model.size(); i++) {
            BrowseSection section = model.get(i);

            if (RailModel.isPinnedBottom(section.getId())) {
                settingsItem = createItem(new Entry(TYPE_SECTION, section.getId()));
                continue;
            }

            if (model.hasDividerBefore(i)) {
                addDivider();
            }

            addListItem(createItem(new Entry(TYPE_SECTION, section.getId())));
        }

        if (settingsItem != null) {
            mBottom.addView(settingsItem, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, getResources().getDimensionPixelSize(R.dimen.modern_rail_item_height)));
            mItems.add(settingsItem);
        }

        applyExpand();
        applyState();

        if (focusedEntry != null) {
            View restored = findItem(focusedEntry.type, focusedEntry.id);
            if (restored != null) {
                restored.requestFocus();
            }
        }
    }

    public void setSelectedSection(int sectionId) {
        mSelectedId = sectionId;
        applyState();
    }

    /**
     * The account item can be hidden (top buttons setting).
     */
    public void setAccountVisible(boolean visible) {
        mAccountItem.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private int firstVisibleIndex() {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).getVisibility() == View.VISIBLE) {
                return i;
            }
        }
        return 0;
    }

    public void setAccount(String name, String avatarUrl) {
        TextView label = mAccountItem.findViewById(R.id.modern_rail_label);
        ImageView icon = mAccountItem.findViewById(R.id.modern_rail_icon);
        label.setText(name);

        mHasAvatar = avatarUrl != null;

        if (mHasAvatar) {
            Glide.with(getContext()).load(avatarUrl).transform(new CircleCrop()).into(icon);
        } else {
            Glide.with(getContext()).clear(icon);
            icon.setImageResource(R.drawable.modern_ic_account_circle);
        }

        applyState();
    }

    /**
     * Enter the rail from the content: focus the selected section.
     */
    public boolean focusSelected() {
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        View item = findItem(TYPE_SECTION, mSelectedId);

        if (item == null) {
            item = findItem(TYPE_SEARCH, -1);
        }

        boolean focused = item != null && item.requestFocus();

        if (!focused) {
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        }

        return focused;
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalFocusChangeListener(mFocusListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalFocusChangeListener(mFocusListener);
        mHandler.removeCallbacksAndMessages(null);
        if (mAnimator != null) {
            mAnimator.cancel();
        }
    }

    private void addListItem(View item) {
        mList.addView(item, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, getResources().getDimensionPixelSize(R.dimen.modern_rail_item_height)));
        mItems.add(item);
    }

    private void addDivider() {
        View divider = new View(getContext());
        divider.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.modern_white_20));
        int margin = getResources().getDimensionPixelSize(R.dimen.modern_rail_divider_margin);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, getResources().getDimensionPixelSize(R.dimen.modern_rail_divider));
        params.topMargin = margin;
        params.bottomMargin = margin;
        mList.addView(divider, params);
    }

    private View createItem(Entry entry) {
        View item = LayoutInflater.from(getContext()).inflate(R.layout.modern_rail_item, this, false);
        item.setTag(entry);
        TextView label = item.findViewById(R.id.modern_rail_label);

        switch (entry.type) {
            case TYPE_ACCOUNT:
                ((ImageView) item.findViewById(R.id.modern_rail_icon)).setImageResource(R.drawable.modern_ic_account_circle);
                item.setOnClickListener(v -> mListener.onAccountClicked());
                item.setOnLongClickListener(v -> {
                    mListener.onAccountLongClicked();
                    return true;
                });
                break;
            case TYPE_SEARCH:
                label.setText(R.string.settings_search);
                item.setOnClickListener(v -> mListener.onSearchClicked());
                break;
            default:
                BrowseSection section = mModel.get(mModel.indexOf(entry.id));
                label.setText(ModernText.sentenceCase(section.getTitle()));
                item.setOnClickListener(v -> mListener.onSectionClicked(entry.id));
                item.setOnLongClickListener(v -> {
                    mListener.onSectionLongPressed(entry.id);
                    return true;
                });
                break;
        }

        item.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            // Leave the rail only to the right. Left, above the first and below the last item are dead ends.
            int index = mItems.indexOf(v);
            return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || (keyCode == KeyEvent.KEYCODE_DPAD_UP && index == firstVisibleIndex())
                    || (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && index == mItems.size() - 1);
        });

        return item;
    }

    private void onFocusMoved(View newFocus) {
        boolean inside = newFocus != null && isDescendant(newFocus);

        if (inside != mIsExpanded) {
            mIsExpanded = inside;
            if (!inside) {
                setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            }
            animateExpand(inside ? 1 : 0);
        }

        // Visual state follows the focus immediately, content loading is debounced
        applyState();

        if (!inside) {
            mHandler.removeCallbacks(mPendingFocus);
            return;
        }

        Entry entry = findEntry(newFocus);

        mHandler.removeCallbacks(mPendingFocus);

        if (entry != null && entry.type == TYPE_SECTION && entry.id != mSelectedId) {
            mPendingFocus = () -> {
                mSelectedId = entry.id;
                mListener.onSectionFocused(entry.id);
            };
            mHandler.postDelayed(mPendingFocus, SECTION_FOCUS_DELAY_MS);
        }
    }

    private void animateExpand(float target) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }

        mAnimator = ValueAnimator.ofFloat(mExpandFraction, target);
        mAnimator.setDuration(ModernTheme.ANIM_FAST_MS);
        mAnimator.setInterpolator(ModernTheme.fastInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mExpandFraction = (float) animation.getAnimatedValue();
            applyExpand();
        });
        mAnimator.start();
    }

    /**
     * Only alpha and translations: cheap enough for every frame on low end devices.
     */
    private void applyExpand() {
        float collapse = 1 - mExpandFraction;
        mBackground.setAlpha(mExpandFraction);
        mScroll.setTranslationY(mListShiftCollapsed * collapse);

        for (View item : mItems) {
            item.findViewById(R.id.modern_rail_label).setAlpha(mExpandFraction);
        }

        // Collapsed there is no gap for the dividers
        float shift = 0;

        for (int i = 0; i < mList.getChildCount(); i++) {
            View child = mList.getChildAt(i);

            if (child.getTag() instanceof Entry) {
                child.setTranslationY(-shift);
            } else {
                child.setAlpha(mExpandFraction);
                child.setTranslationY(-shift);
                shift += mDividerSpace * collapse;
            }
        }

        if (mListener != null) {
            mListener.onExpandChanged(mExpandFraction);
        }
    }

    private void applyState() {
        View focusedView = findFocus();
        Entry focusedEntry = focusedView != null ? findEntry(focusedView) : null;

        for (View item : mItems) {
            Entry entry = (Entry) item.getTag();
            boolean focused = focusedEntry == entry;
            boolean pill = focused && mIsExpanded;
            // Expanded: the focused section shows the filled icon right away
            boolean filled = entry.type == TYPE_SECTION && (mIsExpanded && focusedEntry != null ? focused : entry.id == mSelectedId);

            item.setBackgroundResource(pill ? R.drawable.modern_rail_pill : 0);

            int color = pill ? mOnLightColor : (mIsExpanded || filled) ? mPrimaryColor : mInactiveColor;
            ImageView icon = item.findViewById(R.id.modern_rail_icon);
            TextView label = item.findViewById(R.id.modern_rail_label);
            label.setTextColor(pill ? mOnLightColor : mPrimaryColor);

            if (entry.type == TYPE_SECTION) {
                icon.setImageResource(ModernIcons.forSection(entry.id, filled));
            } else if (entry.type == TYPE_SEARCH) {
                icon.setImageResource(focused ? R.drawable.modern_ic_search_filled : R.drawable.modern_ic_search);
            }

            boolean isAvatar = entry.type == TYPE_ACCOUNT && mHasAvatar;
            ImageViewCompat.setImageTintList(icon, isAvatar ? null : ColorStateList.valueOf(color));
        }
    }

    private boolean isDescendant(View view) {
        View current = view;

        while (current != null) {
            if (current == this) {
                return true;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }

        return false;
    }

    private Entry findEntry(View view) {
        View current = view;

        while (current != null && current != this) {
            if (current.getTag() instanceof Entry) {
                return (Entry) current.getTag();
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }

        return null;
    }

    private View findItem(int type, int id) {
        for (View item : mItems) {
            Entry entry = (Entry) item.getTag();
            if (entry.type == type && (type != TYPE_SECTION || entry.id == id)) {
                return item;
            }
        }

        return null;
    }
}
