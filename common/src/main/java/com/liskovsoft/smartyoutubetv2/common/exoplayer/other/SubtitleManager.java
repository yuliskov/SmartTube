package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import androidx.core.content.ContextCompat;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class SubtitleManager implements TextOutput {
    private static final String TAG = SubtitleManager.class.getSimpleName();
    private final SubtitleView mSubtitleView;
    private final Context mContext;
    private final List<SubtitleStyle> mSubtitleStyles = new ArrayList<>();
    private final AppPrefs mPrefs;
    private final PlayerData mPlayerData;

    public static class SubtitleStyle {
        public final int nameResId;
        public final int subsColorResId;
        public final int backgroundColorResId;
        public final int captionStyle;

        public SubtitleStyle(int nameResId, int subsColorResId, int backgroundColorResId, int captionStyle) {
            this.nameResId = nameResId;
            this.subsColorResId = subsColorResId;
            this.backgroundColorResId = backgroundColorResId;
            this.captionStyle = captionStyle;
        }
    }

    public interface OnSelectSubtitleStyle {
        void onSelectSubtitleStyle(SubtitleStyle style);
    }

    public SubtitleManager(Activity activity, int subViewId) {
        mContext = activity;
        mSubtitleView = activity.findViewById(subViewId);
        mPrefs = AppPrefs.instance(activity);
        mPlayerData = PlayerData.instance(activity);
        configureSubtitleView();
    }

    @Override
    public void onCues(List<Cue> cues) {
        if (mSubtitleView != null) {
            mSubtitleView.setCues(forceCenterAlignment(cues));
        }
    }

    public List<SubtitleStyle> getSubtitleStyles() {
        return mSubtitleStyles;
    }

    public SubtitleStyle getSubtitleStyle() {
        return mPlayerData.getSubtitleStyle();
    }

    public void setSubtitleStyle(SubtitleStyle subtitleStyle) {
        mPlayerData.setSubtitleStyle(subtitleStyle);
        configureSubtitleView();
    }

    private List<Cue> forceCenterAlignment(List<Cue> cues) {
        List<Cue> result = new ArrayList<>();

        for (Cue cue : cues) {
            result.add(new Cue(cue.text)); // sub centered by default

            // CONDITION DOESN'T WORK
            //if (cue.position == 0 && (cue.positionAnchor == Cue.ANCHOR_TYPE_START)) { // unaligned sub encountered
            //    result.add(new Cue(cue.text));
            //} else {
            //    result.add(cue);
            //}
        }

        return result;
    }

    private void configureSubtitleView() {
        if (mSubtitleView != null) {
            // disable default style
            mSubtitleView.setApplyEmbeddedStyles(false);

            SubtitleStyle subtitleStyle = getSubtitleStyle();

            int textColor = ContextCompat.getColor(mContext, subtitleStyle.subsColorResId);
            int outlineColor = ContextCompat.getColor(mContext, R.color.black);
            int backgroundColor = ContextCompat.getColor(mContext, subtitleStyle.backgroundColorResId);

            CaptionStyleCompat style =
                    new CaptionStyleCompat(textColor,
                            backgroundColor, Color.TRANSPARENT,
                            subtitleStyle.captionStyle,
                            outlineColor, Typeface.DEFAULT_BOLD);
            mSubtitleView.setStyle(style);

            float textSize = mSubtitleView.getContext().getResources().getDimension(R.dimen.subtitle_text_size);
            mSubtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        }
    }
}
