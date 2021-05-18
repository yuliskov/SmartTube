package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.framedrops.PlaybackBaseControlGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.framedrops.PlaybackTransportControlGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PlaybackTransportRowPresenter.TopEdgeFocusListener;

public abstract class MaxControlsVideoPlayerGlue<T extends PlayerAdapter>
        extends PlaybackTransportControlGlue<T> implements TopEdgeFocusListener, PlayerView {
    private QualityInfoListener mQualityInfoListener;
    private String mQualityInfo;
    private ControlsVisibilityListener mVisibilityListener;

    /**
     * Constructor for the glue.
     *
     * @param context
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

                    private void fixThumbOverlapping(ViewHolder viewHolder) {
                        LinearLayout.LayoutParams textParam = new LinearLayout.LayoutParams
                                (LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

                        viewHolder.getTitle().setLayoutParams(textParam);
                        viewHolder.getSubtitle().setLayoutParams(textParam);
                    }
                };

        PlaybackTransportRowPresenter rowPresenter = new PlaybackTransportRowPresenter() {
            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                super.onBindRowViewHolder(vh, item);
                vh.setOnKeyListener(MaxControlsVideoPlayerGlue.this);

                ViewHolder viewHolder = (ViewHolder) vh;
                
                mQualityInfoListener = viewHolder.mQualityInfoListener;
                mVisibilityListener = viewHolder.mVisibilityListener;
                viewHolder.mTopEdgeFocusListener = MaxControlsVideoPlayerGlue.this;
                updateQualityInfo();
                updateVisibility();
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

        if (mVisibilityListener != null) {
            mVisibilityListener.onVisibilityChange(show);
        }
    }

    @Override
    public void setQualityInfo(String info) {
        mQualityInfo = info;

        if (mQualityInfoListener != null) {
            mQualityInfoListener.onQualityInfoChanged(info);
        }
    }

    private void updateQualityInfo() {
        if (mQualityInfoListener != null) {
            mQualityInfoListener.onQualityInfoChanged(mQualityInfo);
        }
    }

    private void updateVisibility() {
        if (mVisibilityListener != null) {
            mVisibilityListener.onVisibilityChange(isControlsVisible());
        }
    }

    public interface QualityInfoListener {
        void onQualityInfoChanged(String content);
    }

    public interface ControlsVisibilityListener {
        void onVisibilityChange(boolean isVisible);
    }

    public abstract void onTopEdgeFocused();
}
