package androidx.leanback.widget;

import android.view.View;

/**
 * Subclass of {@link RowPresenter} that can define the desired behavior when the view
 * reappears. This is presently used by {@link PlaybackControlsRowPresenter} to update the UI
 * after we show/hide the controls view.
 */
public abstract class PlaybackRowPresenter extends RowPresenter {

    /**
     * This container is used for trapping click events and passing them to the
     * playback controls.
     */
    public static class ViewHolder extends RowPresenter.ViewHolder {
        public ViewHolder(View view) {
            super(view);
        }
    }

    /**
     * Provides hook to update the UI when the view reappears.
     */
    public void onReappear(RowPresenter.ViewHolder rowViewHolder) {
    }
}
