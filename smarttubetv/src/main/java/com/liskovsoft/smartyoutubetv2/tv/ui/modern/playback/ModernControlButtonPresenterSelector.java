package com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.Presenter;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.ControlButtonPresenterSelector;

/**
 * Round icon buttons, text pills and channel avatar of the modern player controls.
 */
public class ModernControlButtonPresenterSelector extends ControlButtonPresenterSelector {
    private final ModernControlButtonPresenter mPresenter = new ModernControlButtonPresenter();
    private final Presenter[] mPresenters = new Presenter[] {mPresenter};

    public ModernControlButtonPresenterSelector(boolean tooltipsEnabled) {
        super(tooltipsEnabled, R.layout.modern_control_button, R.layout.modern_control_button);
    }

    @Override
    public Presenter getPrimaryPresenter() {
        return mPresenter;
    }

    @Override
    public Presenter getSecondaryPresenter() {
        return mPresenter;
    }

    @Override
    public Presenter getPresenter(Object item) {
        return mPresenter;
    }

    @Override
    public Presenter[] getPresenters() {
        return mPresenters;
    }

    static boolean isTransport(Action action) {
        return action instanceof PlaybackControlsRow.PlayPauseAction
                || action instanceof PlaybackControlsRow.SkipNextAction
                || action instanceof PlaybackControlsRow.SkipPreviousAction;
    }

    static int transportOrder(Action action) {
        if (action instanceof PlaybackControlsRow.SkipPreviousAction) {
            return 0;
        }
        if (action instanceof PlaybackControlsRow.PlayPauseAction) {
            return 1;
        }
        if (action instanceof PlaybackControlsRow.SkipNextAction) {
            return 2;
        }
        return 3;
    }

    static boolean isActive(Action action) {
        if (action instanceof PlaybackControlsRow.PlayPauseAction) {
            return false;
        }

        return action instanceof PlaybackControlsRow.MultiAction && ((PlaybackControlsRow.MultiAction) action).getIndex() > 0;
    }

    private static final class ModernControlButtonPresenter extends ControlButtonPresenter {
        ModernControlButtonPresenter() {
            super(R.layout.modern_control_button);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            super.onBindViewHolder(viewHolder, item);

            Action action = (Action) item;
            View root = viewHolder.view;
            View button = root.findViewById(R.id.button);
            ImageView icon = root.findViewById(R.id.icon);
            Context context = root.getContext();
            Resources res = context.getResources();

            int actionId = (int) action.getId();
            boolean transport = isTransport(action);
            int group = ModernActionStyle.groupOf(actionId, transport);
            int shape = ModernActionStyle.shapeOf(actionId);
            CharSequence label = !TextUtils.isEmpty(action.getLabel1()) ? action.getLabel1() : action.getLabel2();

            root.setTag(R.id.modern_action_id, actionId);
            root.setTag(R.id.modern_action_group, group);
            root.setTag(R.id.modern_action_label, label);
            root.setTag(R.id.modern_transport_order, transportOrder(action));
            root.setTag(R.id.modern_play_pause, action instanceof PlaybackControlsRow.PlayPauseAction);

            int size = res.getDimensionPixelSize(action instanceof PlaybackControlsRow.PlayPauseAction ?
                    R.dimen.modern_player_play_button : R.dimen.modern_player_button);
            ViewGroup.LayoutParams params = button.getLayoutParams();
            params.height = size;
            params.width = size;
            button.setLayoutParams(params);

            boolean grouped = group == ModernActionStyle.GROUP_SOCIAL || group == ModernActionStyle.GROUP_OTHER;
            button.setBackgroundResource(grouped ? R.drawable.modern_player_button_bg_grouped : R.drawable.modern_player_button_bg);
            applyIcon(icon, action, actionId, shape, res);

            applyFocus(root, button.isFocused());
            button.setOnFocusChangeListener((v, hasFocus) -> {
                applyFocus(root, hasFocus);
                // The bar draws the label of the focused button
                ((View) root.getParent()).invalidate();
            });
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            super.onUnbindViewHolder(viewHolder);
            viewHolder.view.findViewById(R.id.button).setOnFocusChangeListener(null);
        }

        private static void applyIcon(ImageView icon, Action action, int actionId, int shape, Resources res) {
            int avatarSize = res.getDimensionPixelSize(R.dimen.modern_player_avatar);
            int iconSize = res.getDimensionPixelSize(R.dimen.modern_player_button_icon);
            ViewGroup.LayoutParams params = icon.getLayoutParams();
            params.width = params.height = shape == ModernActionStyle.SHAPE_AVATAR ? avatarSize : iconSize;
            icon.setLayoutParams(params);
            icon.setPadding(0, 0, 0, 0);

            int iconRes;

            if (action instanceof PlaybackControlsRow.PlayPauseAction) {
                iconRes = ((PlaybackControlsRow.PlayPauseAction) action).getIndex() == PlaybackControlsRow.PlayPauseAction.INDEX_PLAY ?
                        R.drawable.modern_ic_play_arrow_filled : R.drawable.modern_ic_pause_filled;
            } else if (action instanceof PlaybackControlsRow.SkipNextAction) {
                iconRes = R.drawable.modern_ic_skip_next_filled;
            } else if (action instanceof PlaybackControlsRow.SkipPreviousAction) {
                iconRes = R.drawable.modern_ic_skip_previous_filled;
            } else {
                iconRes = ModernActionStyle.iconOf(actionId, isActive(action));
            }

            if (iconRes != 0) {
                icon.setImageResource(iconRes);
                icon.setTag(Boolean.TRUE); // tintable
            } else {
                Drawable own = action.getIcon();
                icon.setImageDrawable(own);
                icon.setTag(shape == ModernActionStyle.SHAPE_AVATAR ? Boolean.FALSE : Boolean.TRUE);
            }
        }

        private static void applyFocus(View root, boolean focused) {
            Context context = root.getContext();
            int color = ContextCompat.getColor(context, focused ? R.color.modern_on_light : R.color.modern_text_primary);
            ImageView icon = root.findViewById(R.id.icon);

            ImageViewCompat.setImageTintList(icon, Boolean.TRUE.equals(icon.getTag()) ? ColorStateList.valueOf(color) : null);
        }
    }
}
