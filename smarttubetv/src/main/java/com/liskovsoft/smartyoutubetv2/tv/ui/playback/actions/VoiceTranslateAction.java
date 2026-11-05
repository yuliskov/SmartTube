package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.leanback.widget.PlaybackControlsRow.MultiAction;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * OFF = idle, PENDING = waiting for Yandex, ON = translation playing.
 */
public class VoiceTranslateAction extends MultiAction {
    public static final int INDEX_OFF = 0;
    public static final int INDEX_PENDING = 1;
    public static final int INDEX_ON = 2;

    private final Context mContext;
    private final String mBaseLabel;
    private final String[] mLabels;

    public VoiceTranslateAction(Context context) {
        super(R.id.action_voice_translate);
        mContext = context;
        mBaseLabel = context.getString(com.liskovsoft.smartyoutubetv2.common.R.string.action_voice_translate);

        int highlightColor = ActionHelpers.getIconHighlightColor(context);
        BitmapDrawable offDrawable = ActionHelpers.getBitmapDrawable(context, R.drawable.action_voice_translate);
        BitmapDrawable pendingDrawable = ActionHelpers.getBitmapDrawable(context, R.drawable.action_voice_translate_pending);
        BitmapDrawable onDrawable = offDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(offDrawable.getBitmap(), highlightColor));

        Drawable[] drawables = new Drawable[3];
        drawables[INDEX_OFF] = offDrawable;
        drawables[INDEX_PENDING] = pendingDrawable;
        drawables[INDEX_ON] = onDrawable;
        setDrawables(drawables);

        mLabels = new String[3];
        mLabels[INDEX_OFF] = mBaseLabel;
        mLabels[INDEX_PENDING] = mBaseLabel;
        mLabels[INDEX_ON] = mBaseLabel;
        setLabels(mLabels);
        setIndex(INDEX_OFF);
    }

    public void updatePendingLabel(int remainingTimeSec) {
        if (remainingTimeSec > 0) {
            int min = Math.max(1, (remainingTimeSec + 59) / 60);
            mLabels[INDEX_PENDING] = mContext.getString(
                    com.liskovsoft.smartyoutubetv2.common.R.string.vot_pending_eta_short, min);
        } else {
            mLabels[INDEX_PENDING] = mContext.getString(
                    com.liskovsoft.smartyoutubetv2.common.R.string.vot_pending_long);
        }
        setLabels(mLabels);
    }

    public void resetLabels() {
        mLabels[INDEX_OFF] = mBaseLabel;
        mLabels[INDEX_PENDING] = mBaseLabel;
        mLabels[INDEX_ON] = mBaseLabel;
        setLabels(mLabels);
    }
}
