package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView;
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
    private WeakReference<PlaybackTransportRowPresenter.ViewHolder> mViewHolder;

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
                        fixClippedTitle(viewHolder);
                        //fixOverlappedTitle(viewHolder);
                        fixThumbOverlapping(viewHolder);

                        PlaybackBaseControlGlue<?> glue = (PlaybackBaseControlGlue<?>) obj;
                        viewHolder.getTitle().setText(glue.getTitle());
                        viewHolder.getSubtitle().setText(glue.getSubtitle());
                        // MOD: add extra title line
                        viewHolder.getBody().setText(glue.getBody());
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
                mViewHolder = new WeakReference<>(viewHolder);

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

        if (getViewHolder() != null) {
            getViewHolder().setDateVisibility(show);
        }
    }

    @Override
    public void setQualityInfo(String info) {
        mQualityInfo = info;

        if (getViewHolder() != null) {
            getViewHolder().setQualityInfo(info);
        }
    }

    @Override
    public void setVideo(Video video) {
        mVideo = video;
    }

    @Override
    public void play() {
        super.play();

        if (getViewHolder() != null) {
            getViewHolder().setPlay(true);
        }
    }

    @Override
    public void pause() {
        super.pause();

        if (getViewHolder() != null) {
            getViewHolder().setPlay(false);
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
        if (getViewHolder() != null) {
            getViewHolder().setSeekPreviewTitle(title);
        }
    }

    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        if (getViewHolder() != null) {
            getViewHolder().setSeekBarSegments(segments);
        }
    }

    private void updateLiveEndingTime() {
        if (mVideo == null) {
            return;
        }

        long liveDurationMs = mVideo.getLiveDurationMs();

        if (liveDurationMs == 0) {
            return;
        }

        PlaybackControlsRow controlsRow = getControlsRow();
        PlayerAdapter playerAdapter = getPlayerAdapter();

        if (controlsRow == null || playerAdapter == null) {
            return;
        }

        // Apply duration on videos with uncommon length.
        if (playerAdapter.getDuration() > Video.MAX_LIVE_DURATION_MS) {
            controlsRow.setDuration(
                    playerAdapter.isPrepared() ? liveDurationMs : -1);
        }
    }

    private ViewHolder getViewHolder() {
        return mViewHolder != null ? mViewHolder.get() : null;
    }

    public abstract void onTopEdgeFocused();
}
