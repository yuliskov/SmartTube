package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import android.content.Context;
import android.view.KeyEvent;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

public abstract class LongClickPresenter extends Presenter {
    private OnItemLongPressedListener mLongPressedListener;
    private Boolean mLongPressDisabled;

    public void setOnItemViewLongPressedListener(OnItemLongPressedListener listener) {
        mLongPressedListener = listener;
    }

    /**
     * Don't forget to call this method in descendants!
     */
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (mLongPressDisabled == null) {
            mLongPressDisabled = GeneralData.instance(viewHolder.view.getContext()).isOkButtonLongPressDisabled();
        }

        viewHolder.view.setOnLongClickListener(v -> {
            if (mLongPressedListener != null && !mLongPressDisabled) {
                mLongPressedListener.onItemLongPressed(viewHolder, item);

                return true; // don't provoke single click event
            }

            return false; // work as usual
        });

        viewHolder.view.setOnKeyListener((v, keyCode, event) -> {
            if (mLongPressedListener != null) {
                if (KeyHelpers.isMenuKey(keyCode)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        mLongPressedListener.onItemLongPressed(viewHolder, item);
                    }
                }
            }

            // Program keys mapped to 'Add/Remove to Watch Later' work on the focused card
            if (event.getAction() == KeyEvent.ACTION_DOWN && item instanceof Video
                    && isWatchLaterKey(v.getContext(), keyCode)) {
                Video video = (Video) item;

                if (video.hasVideo()) {
                    MediaServiceManager.instance().toggleWatchLaterPlaylist(video, v.getContext());
                    return true;
                }
            }

            return false; // enable navigation events
        });
    }

    private boolean isWatchLaterKey(Context context, int keyCode) {
        return GeneralData.instance(context).getProgramKeyAction(keyCode) == GeneralData.ACTION_WATCH_LATER;
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        if (mLongPressedListener != null) {
            viewHolder.view.setOnLongClickListener(null);
        }

        if (mLongPressedListener != null) {
            viewHolder.view.setOnKeyListener(null);
        }
    }
}
