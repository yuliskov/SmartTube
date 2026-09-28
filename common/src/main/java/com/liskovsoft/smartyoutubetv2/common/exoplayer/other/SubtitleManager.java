package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.view.accessibility.CaptioningManager.CaptionStyle;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.SubtitleTrack;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SubtitleManager implements TextOutput, OnDataChange, AnalyticsListener {
    private static final String TAG = SubtitleManager.class.getSimpleName();
    private static final float TV_LEFT_PADDING_FRACTION = 0.14f;
    private static final long MAX_ANIMATION_DURATION_MS = 15_000;
    private static final String LOADING_TEXT = "[Loading subtitles...]";

    private final SubtitleView mSubtitleView;
    private final Context mContext;
    private final List<SubtitleStyle> mSubtitleStyles = new ArrayList<>();
    private final AppPrefs mPrefs;
    private final PlayerData mPlayerData;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean mIsLoading = false;
    private boolean mIsSubtitlesSelected = false;
    private boolean mIsActiveTrackAuto = false;
    private boolean mIsShown = false;
    private boolean mIsSubtitleLoaded = false;
    private List<Cue> mLastCues = null;

    private final Runnable mTimeoutRunnable = this::stopLoadingAnimation;

    public static class SubtitleStyle {
        public final int nameResId;
        public final int subsColorResId;
        public final int backgroundColorResId;
        public final int captionStyle;

        public SubtitleStyle(int nameResId) {
            this(nameResId, -1, -1, -1);
        }

        public SubtitleStyle(int nameResId, int subsColorResId, int backgroundColorResId, int captionStyle) {
            this.nameResId = nameResId;
            this.subsColorResId = subsColorResId;
            this.backgroundColorResId = backgroundColorResId;
            this.captionStyle = captionStyle;
        }

        public boolean isSystem() {
            return subsColorResId == -1 && backgroundColorResId == -1 && captionStyle == -1;
        }
    }

    public SubtitleManager(SubtitleView subtitleView) {
        mContext = subtitleView.getContext();
        mSubtitleView = subtitleView;
        mPrefs = AppPrefs.instance(mContext);
        mPlayerData = PlayerData.instance(mContext);
        mPlayerData.setOnChange(this);
        configureSubtitleView();
    }

    @Override
    public void onDataChange() {
        configureSubtitleView();
        if (mSubtitleView != null && mIsShown && mLastCues != null && !mLastCues.isEmpty() && hasText(mLastCues)) {
            mSubtitleView.setCues(formatCues(mLastCues));
        }
    }

    @Override
    public void onCues(List<Cue> cues) {
        mLastCues = cues;
        mIsSubtitleLoaded = true;
        if (cues != null && !cues.isEmpty() && hasText(cues)) {
            stopLoadingAnimation();
            if (mSubtitleView != null && mIsShown) {
                mSubtitleView.setCues(formatCues(cues));
            }
        } else {
            if (!mIsLoading && mSubtitleView != null) {
                mSubtitleView.setCues(Collections.emptyList());
            }
        }
    }

    public void show(boolean show) {
        mIsShown = show;
        if (mSubtitleView != null) {
            mSubtitleView.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                if (mLastCues != null && !mLastCues.isEmpty() && hasText(mLastCues)) {
                    mSubtitleView.setCues(formatCues(mLastCues));
                } else if (mIsSubtitlesSelected && !mIsSubtitleLoaded && mPlayerData.isSubtitleLoadingAnimationEnabled()) {
                    startLoadingAnimation();
                }
            } else {
                stopLoadingAnimation();
            }
        }
    }

    public boolean isShown() {
        return mIsShown;
    }

    public void startLoadingAnimation() {
        if (!mIsShown || !mPlayerData.isSubtitleLoadingAnimationEnabled()) {
            return;
        }

        if (mSubtitleView != null) {
            mSubtitleView.setVisibility(View.VISIBLE);
        }

        if (mIsLoading) {
            return;
        }

        mIsLoading = true;
        mLastCues = null;
        mHandler.removeCallbacks(mTimeoutRunnable);

        Cue loadingCue = new Cue(LOADING_TEXT);
        if (mSubtitleView != null) {
            mSubtitleView.setCues(Collections.singletonList(loadingCue));
        }

        mHandler.postDelayed(mTimeoutRunnable, MAX_ANIMATION_DURATION_MS);
    }

    public void stopLoadingAnimation() {
        mHandler.removeCallbacks(mTimeoutRunnable);
        if (mIsLoading) {
            mIsLoading = false;
            if (mSubtitleView != null && (mLastCues == null || mLastCues.isEmpty() || !hasText(mLastCues))) {
                mSubtitleView.setCues(Collections.emptyList());
            }
        }
    }

    public void dispose() {
        stopLoadingAnimation();
        mHandler.removeCallbacksAndMessages(null);
        mLastCues = null;
        mIsSubtitleLoaded = false;
        mIsShown = false;
    }

    @Override
    public void onTracksChanged(EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        boolean hasTextTrack = false;
        boolean isAutoTrack = false;
        if (trackSelections != null) {
            for (TrackSelection selection : trackSelections.getAll()) {
                if (selection != null && selection.length() > 0) {
                    Format format = selection.getFormat(0);
                    if (isSubtitleFormat(format)) {
                        hasTextTrack = true;
                        if (isAutoTrack(format)) {
                            isAutoTrack = true;
                        }
                        break;
                    }
                }
            }
        }

        mIsSubtitlesSelected = hasTextTrack;
        mIsActiveTrackAuto = isAutoTrack;
        mIsSubtitleLoaded = false;
        mLastCues = null;

        if (mIsSubtitlesSelected) {
            if (mIsShown && mPlayerData.isSubtitleLoadingAnimationEnabled()) {
                startLoadingAnimation();
            }
        } else {
            stopLoadingAnimation();
            if (mSubtitleView != null) {
                mSubtitleView.setCues(Collections.emptyList());
            }
        }
    }

    @Override
    public void onLoadStarted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        if (mediaLoadData != null && mediaLoadData.trackType == C.TRACK_TYPE_TEXT) {
            if (mIsShown && mIsSubtitlesSelected && mPlayerData.isSubtitleLoadingAnimationEnabled()) {
                if (mLastCues == null || mLastCues.isEmpty() || !hasText(mLastCues)) {
                    startLoadingAnimation();
                }
            }
        }
    }

    @Override
    public void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        // Do not stop loading animation here; wait until onCues() receives parsed cues with text.
    }

    @Override
    public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
        if (mediaLoadData != null && mediaLoadData.trackType == C.TRACK_TYPE_TEXT) {
            stopLoadingAnimation();
        }
    }

    private static boolean isSubtitleFormat(Format format) {
        if (format == null) {
            return false;
        }
        if ((format.roleFlags & C.ROLE_FLAG_SUBTITLE) != 0) {
            return true;
        }
        String mime = format.sampleMimeType;
        if (mime == null) {
            return false;
        }
        return MimeTypes.isText(mime)
                || mime.startsWith("text/")
                || mime.contains("srv3")
                || mime.contains("ttml")
                || mime.contains("vtt")
                || mime.contains("subrip");
    }

    private static boolean isAutoTrack(Format format) {
        if (format == null) {
            return false;
        }
        if (SubtitleTrack.isAuto(format.language)) {
            return true;
        }
        if (format.id != null && (format.id.startsWith("a.") || format.id.startsWith("asr") || format.id.contains(".asr"))) {
            return true;
        }
        if (format.label != null && SubtitleTrack.isAuto(format.label)) {
            return true;
        }
        return false;
    }

    private boolean hasText(List<Cue> cues) {
        for (Cue cue : cues) {
            if (cue != null && cue.text != null && cue.text.length() > 0) {
                return true;
            }
        }
        return false;
    }

    private List<SubtitleStyle> getSubtitleStyles() {
        return mSubtitleStyles;
    }

    private SubtitleStyle getSubtitleStyle() {
        return mPlayerData.getSubtitleStyle();
    }

    private void setSubtitleStyle(SubtitleStyle subtitleStyle) {
        mPlayerData.setSubtitleStyle(subtitleStyle);
        configureSubtitleView();
    }

    private List<Cue> formatCues(List<Cue> cues) {
        // If not an auto-generated track, keep manual subtitles untouched (preserves manual positioning/centering)
        if (!mIsActiveTrackAuto) {
            return cues;
        }

        List<Cue> result = new ArrayList<>();
        boolean wordByWord = mPlayerData.isWordByWordAutoSubtitlesEnabled();

        for (Cue cue : cues) {
            // Keep explicitly vertically positioned cues (e.g. top/window positioned subtitles)
            if (cue.line != Cue.DIMEN_UNSET) {
                result.add(cue);
                continue;
            }

            if (wordByWord) {
                // For unpositioned auto-generated cues when word-by-word is ON:
                // Left-align text and anchor to the left side with TV padding, matching official YouTube TV app.
                result.add(new Cue(
                        cue.text,
                        Layout.Alignment.ALIGN_NORMAL,
                        Cue.DIMEN_UNSET,
                        Cue.TYPE_UNSET,
                        Cue.TYPE_UNSET,
                        TV_LEFT_PADDING_FRACTION,
                        Cue.ANCHOR_TYPE_START,
                        1.0f - (2 * TV_LEFT_PADDING_FRACTION)
                ));
            } else {
                // When word-by-word is OFF:
                // Center the subtitles in the middle of the screen (legacy behavior).
                // Strips any WebVTT default 'align:start' and 'position:0%'.
                result.add(new Cue(cue.text));
            }
        }

        return result;
    }

    private void configureSubtitleView() {
        if (mSubtitleView != null) {
            // enable embedded styles for SRV3 formats with spans/colors
            mSubtitleView.setApplyEmbeddedStyles(true);

            SubtitleStyle subtitleStyle = getSubtitleStyle();

            if (subtitleStyle.isSystem()) {
                if (VERSION.SDK_INT >= 19) {
                    applySystemStyle();
                }
            } else {
                applyStyle(subtitleStyle);
            }

            // Ensure a safe 8% bottom padding margin on TV screens for unpositioned subtitles
            mSubtitleView.setBottomPaddingFraction(Math.max(0.08f, mPlayerData.getSubtitlePosition()));
        }
    }

    private void applyStyle(SubtitleStyle subtitleStyle) {
        int textColor = ContextCompat.getColor(mContext, subtitleStyle.subsColorResId);
        int outlineColor = ContextCompat.getColor(mContext, R.color.black);
        int backgroundColor = ContextCompat.getColor(mContext, subtitleStyle.backgroundColorResId);

        CaptionStyleCompat style =
                new CaptionStyleCompat(textColor,
                        backgroundColor, Color.TRANSPARENT,
                        subtitleStyle.captionStyle,
                        outlineColor, Typeface.DEFAULT_BOLD);
        mSubtitleView.setStyle(style);

        float textSize = getTextSizePx();
        mSubtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
    }

    @RequiresApi(19)
    private void applySystemStyle() {
        CaptioningManager captioningManager =
                (CaptioningManager) mContext.getSystemService(Context.CAPTIONING_SERVICE);

        if (captioningManager != null) {
            CaptionStyle userStyle = captioningManager.getUserStyle();

            CaptionStyleCompat style =
                    new CaptionStyleCompat(userStyle.foregroundColor,
                            userStyle.backgroundColor, VERSION.SDK_INT >= 21 ? userStyle.windowColor : Color.TRANSPARENT,
                            userStyle.edgeType,
                            userStyle.edgeColor, userStyle.getTypeface());
            mSubtitleView.setStyle(style);

            float textSizePx = getTextSizePx();
            mSubtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx * captioningManager.getFontScale());
        }
    }

    private float getTextSizePx() {
        float textSizePx = mSubtitleView.getContext().getResources().getDimension(R.dimen.subtitle_text_size);
        return textSizePx * mPlayerData.getSubtitleScale();
    }
}
