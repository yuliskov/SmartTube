package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.LiveStateProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.framedrops.PlaybackBaseControlGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.framedrops.PlaybackTransportControlGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PlaybackTransportRowPresenter.TopEdgeFocusListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PlaybackTransportRowPresenter.ViewHolder;

import java.lang.ref.WeakReference;
import java.util.List;

public abstract class MaxControlsVideoPlayerGlue<T extends PlayerAdapter>
        extends PlaybackTransportControlGlue<T> implements TopEdgeFocusListener, PlayerView {
    private String mQualityInfo;
    private Video mVideo;
    private LiveStateProvider mLiveStateProvider;
    private WeakReference<PlaybackTransportRowPresenter.ViewHolder> mTransportViewHolder;
    private WeakReference<AbstractDetailsDescriptionPresenter.ViewHolder> mDescriptionViewHolder;

    /**
     * Constructor for the glue.
     *
     * @param context context
     * @param impl    Implementation to underlying media player.
     */
    public MaxControlsVideoPlayerGlue(Context context, T impl) {
        super(context, impl);
    }

    @Override
    protected PlaybackRowPresenter onCreateRowPresenter() {
        final AbstractDetailsDescriptionPresenter detailsPresenter =
                new AbstractDetailsDescriptionPresenter() {
                    @Override
                    protected void onBindDescription(ViewHolder viewHolder, Object obj) {
                        mDescriptionViewHolder = new WeakReference<>(viewHolder);

                        fixClippedTitle(viewHolder);
                        //fixOverlappedTitle(viewHolder);
                        fixThumbOverlapping(viewHolder);

                        PlaybackBaseControlGlue<?> glue = (PlaybackBaseControlGlue<?>) obj;
                        viewHolder.getTitle().setText(glue.getTitle());
                        viewHolder.getSubtitle().setText(glue.getSubtitle());

                        if (MainUIData.instance(viewHolder.view.getContext()).isYouTubeColorScheme()) {
                            // YouTube TV: bold, smaller title
                            viewHolder.getTitle().setTypeface(Typeface.DEFAULT_BOLD);
                            viewHolder.getTitle().setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                            viewHolder.getSubtitle().setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                            viewHolder.getBody().setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                            // ResizingTextView restores its cached defaults on every measure.
                            // Keep those defaults and Leanback's baseline spacing in sync with the font.
                            viewHolder.getTitle().setLineSpacing(0, 1);
                            Helpers.setField(viewHolder.getTitle(), "mDefaultsInitialized", false);
                            Helpers.setField(viewHolder.getTitle(), "mResizedTextSize", -1);
                            Helpers.setField(viewHolder, "mTitleLineSpacing", viewHolder.getTitle().getLineHeight());
                            Helpers.setField(viewHolder, "mBodyLineSpacing", viewHolder.getBody().getLineHeight());
                            Helpers.setField(viewHolder, "mTitleFontMetricsInt", viewHolder.getTitle().getPaint().getFontMetricsInt());
                            Helpers.setField(viewHolder, "mSubtitleFontMetricsInt", viewHolder.getSubtitle().getPaint().getFontMetricsInt());
                            Helpers.setField(viewHolder, "mBodyFontMetricsInt", viewHolder.getBody().getPaint().getFontMetricsInt());
                            int baselineGap = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                    20, viewHolder.view.getResources().getDisplayMetrics()));
                            Helpers.setField(viewHolder, "mUnderTitleBaselineMargin", baselineGap);
                            Helpers.setField(viewHolder, "mUnderSubtitleBaselineMargin", baselineGap);
                        }
                        // MOD: add extra title line
                        //viewHolder.getBody().setText(glue.getBody());
                    }

                    private void fixOverlappedTitle(ViewHolder viewHolder) {
                        // Fix overlapped title on big size fonts
                        Integer titleLineSpacing = (Integer) Helpers.getField(viewHolder, "mTitleLineSpacing");
                        if (titleLineSpacing != null) {
                            Helpers.setField(viewHolder, "mTitleLineSpacing", titleLineSpacing * 1.2);
                        }
                    }

                    private void fixClippedTitle(ViewHolder viewHolder) {
                        // Fix clipped title on videos with embedded icons
                        Helpers.setField(viewHolder, "mTitleMargin", 0);
                    }

                    /**
                     * MOD: Also fixes cropped title, subtitle, body
                     */
                    private void fixThumbOverlapping(ViewHolder viewHolder) {
                        LinearLayout.LayoutParams textParam = new LinearLayout.LayoutParams
                                (LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

                        viewHolder.getTitle().setLayoutParams(textParam);
                        viewHolder.getSubtitle().setLayoutParams(textParam);
                        viewHolder.getBody().setLayoutParams(textParam);
                    }
                };

        PlaybackTransportRowPresenter rowPresenter = new PlaybackTransportRowPresenter() {
            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                super.onBindRowViewHolder(vh, item);
                vh.setOnKeyListener(MaxControlsVideoPlayerGlue.this);

                ViewHolder viewHolder = (ViewHolder) vh;
                mTransportViewHolder = new WeakReference<>(viewHolder);

                viewHolder.setTopEdgeFocusListener(MaxControlsVideoPlayerGlue.this);
                viewHolder.setQualityInfo(mQualityInfo);
                viewHolder.setDateVisibility(isControlsVisible());
                // Don't uncomment
                // Reset to defaults
                //viewHolder.setSeekPreviewTitle(null);
                // Don't uncomment
                //viewHolder.setSeekBarSegments(null);
            }
            @Override
            protected void onUnbindRowViewHolder(RowPresenter.ViewHolder vh) {
                super.onUnbindRowViewHolder(vh);
                vh.setOnKeyListener(null);
            }
        };
        rowPresenter.setDescriptionPresenter(detailsPresenter);
        return rowPresenter;
    }

    @Override
    public void setControlsVisibility(boolean show) {
        super.setControlsVisibility(show);

        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setDateVisibility(show);
        }
    }

    @Override
    public void setQualityInfo(String info) {
        mQualityInfo = info;

        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setQualityInfo(info);
        }
    }

    @Override
    public void setVideo(Video video) {
        mVideo = video;
    }

    @Override
    public void play() {
        super.play();

        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setPlay(true);
        }
    }

    @Override
    public void pause() {
        super.pause();

        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setPlay(false);
        }
    }

    @Override
    protected void onUpdateControlsVisibility() {
        super.onUpdateControlsVisibility();

        if (isControlsVisible()) {
            updateLiveEndingTime();
        }
    }

    @Override
    protected void onUpdateProgress() {
        super.onUpdateProgress();

        if (isControlsVisible()) {
            updateLiveEndingTime();
        }
    }

    public void setSeekPreviewTitle(String title) {
        if (getTransportViewHolder() != null) { // the chapter title when show seeking ui
            getTransportViewHolder().setSeekPreviewTitle(title);
        }
        if (getDescriptionViewHolder() != null) { // the chapter title when show full ui
            getDescriptionViewHolder().getBody().setText(title);
            getDescriptionViewHolder().getBody().setVisibility(title != null ? View.VISIBLE: View.GONE);
        }
    }

    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setSeekBarSegments(segments);
        }
    }

    public void setLiveStateProvider(LiveStateProvider provider) {
        mLiveStateProvider = provider;
    }

    @Override
    public boolean isSeekBlocked() {
        return mLiveStateProvider != null && mLiveStateProvider.isSeekBlocked();
    }

    /**
     * Show only the window that YouTube lets to watch, not the absolute stream time.
     * Blocked bar (no rewind) shows the live edge as is.
     */
    @Override
    protected long getTimelineOffsetMs() {
        return mLiveStateProvider != null && !isSeekBlocked() ? mLiveStateProvider.getLiveStartMs() : 0;
    }

    private void updateLiveEndingTime() {
        // The blocked bar has its own duration (see pinProgressToEnd)
        if (mVideo == null || isSeekBlocked()) {
            return;
        }

        long providedDurationMs = mLiveStateProvider != null ? mLiveStateProvider.getLiveDurationMs() : -1;
        boolean hasProvidedDuration = providedDurationMs > 0;
        long liveDurationMs = hasProvidedDuration
                ? Math.max(providedDurationMs - getTimelineOffsetMs(), getCurrentPosition()) : mVideo.getLiveDurationMs();

        if (liveDurationMs == 0) {
            return;
        }

        PlaybackControlsRow controlsRow = getControlsRow();
        PlayerAdapter playerAdapter = getPlayerAdapter();

        if (controlsRow == null || playerAdapter == null) {
            return;
        }

        // Apply duration on videos with uncommon length.
        if (hasProvidedDuration || playerAdapter.getDuration() > Video.MAX_LIVE_DURATION_MS) {
            controlsRow.setDuration(
                    playerAdapter.isPrepared() ? liveDurationMs : -1);
        }
    }

    private ViewHolder getTransportViewHolder() {
        return mTransportViewHolder != null ? mTransportViewHolder.get() : null;
    }

    private AbstractDetailsDescriptionPresenter.ViewHolder getDescriptionViewHolder() {
        return mDescriptionViewHolder != null ? mDescriptionViewHolder.get() : null;
    }

    public abstract void onTopEdgeFocused();
}
