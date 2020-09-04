package com.liskovsoft.smartyoutubetv2.common.exoplayer.managers;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import androidx.core.content.ContextCompat;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;

import java.util.ArrayList;
import java.util.List;

/**
 * My wrapper<br/>
 * Main intent to bring subtitle alignment on some videos
 */
public class SubtitleStyleRendererDecorator implements TextOutput {
    private static final String TAG = SubtitleStyleRendererDecorator.class.getSimpleName();
    private final TextOutput mTextRendererOutput;

    public SubtitleStyleRendererDecorator(TextOutput textRendererOutput) {
        mTextRendererOutput = textRendererOutput;
    }

    @Override
    public void onCues(List<Cue> cues) {
        Log.d(TAG, cues);
        mTextRendererOutput.onCues(forceCenterAlignment(cues));
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

    public static void configureSubtitleView(PlayerView playerView) {
        if (playerView != null) {
            SubtitleView subtitleView = playerView.getSubtitleView();
            Context context = playerView.getContext();

            if (subtitleView != null && context != null) {
                // disable default style
                subtitleView.setApplyEmbeddedStyles(false);

                int textColor = ContextCompat.getColor(context, R.color.sub_text_color2);
                int outlineColor = ContextCompat.getColor(context, R.color.sub_outline_color);
                int backgroundColor = ContextCompat.getColor(context, R.color.sub_background_color);

                CaptionStyleCompat style =
                        new CaptionStyleCompat(textColor,
                                backgroundColor, Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                outlineColor, Typeface.DEFAULT);
                subtitleView.setStyle(style);

                float textSize = subtitleView.getContext().getResources().getDimension(R.dimen.subtitle_text_size);
                subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            }
        }
    }
}
