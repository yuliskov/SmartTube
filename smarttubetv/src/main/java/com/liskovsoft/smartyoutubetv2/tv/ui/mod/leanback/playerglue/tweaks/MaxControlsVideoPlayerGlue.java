package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PlaybackTransportRowPresenter.TopEdgeFocusListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.framedrops.PlaybackBaseControlGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.framedrops.PlaybackTransportControlGlue;

public abstract class MaxControlsVideoPlayerGlue<T extends PlayerAdapter>
        extends PlaybackTransportControlGlue<T> implements TopEdgeFocusListener {
    private final Handler mHandler;
    private TickleListener mTickleListener;
    private final Runnable mTickleHandler = this::onTickle;

    /**
     * Constructor for the glue.
     *
     * @param context
     * @param impl    Implementation to underlying media player.
     */
    public MaxControlsVideoPlayerGlue(Context context, T impl) {
        super(context, impl);

        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected PlaybackRowPresenter onCreateRowPresenter() {
        final AbstractDetailsDescriptionPresenter detailsPresenter =
                new AbstractDetailsDescriptionPresenter() {
                    @Override
                    protected void onBindDescription(ViewHolder
                                                             viewHolder, Object obj) {
                        fixClippedTitle(viewHolder);
                        //fixOverlappedTitle(viewHolder);

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
                };

        PlaybackTransportRowPresenter rowPresenter = new PlaybackTransportRowPresenter() {
            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                super.onBindRowViewHolder(vh, item);
                vh.setOnKeyListener(MaxControlsVideoPlayerGlue.this);

                ViewHolder viewHolder = (ViewHolder) vh;

                addQualityInfoListener(viewHolder.mQualityListener);
                mTickleListener = viewHolder.mTickleListener;
                viewHolder.mTopEdgeFocusListener = MaxControlsVideoPlayerGlue.this;
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

    public interface QualityInfoListener {
        void onQualityInfoChanged(String content);
    }

    public interface TickleListener {
        void onTickle();
    }

    protected abstract void addQualityInfoListener(QualityInfoListener listener);

    public abstract void onTopEdgeFocused();

    @Override
    public void setControlsVisibility(boolean show) {
        super.setControlsVisibility(show);

        onTickle();
    }

    private void onTickle() {
        if (isControlsVisible()) {
            if (mTickleListener != null) {
                mTickleListener.onTickle();
            }

            mHandler.postDelayed(mTickleHandler, 10_000);
        } else {
            mHandler.removeCallbacks(mTickleHandler);
        }
    }
}
