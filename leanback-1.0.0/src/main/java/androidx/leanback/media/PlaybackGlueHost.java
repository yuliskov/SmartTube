/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.leanback.media;

import android.view.View;

import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PlaybackSeekUi;
import androidx.leanback.widget.Row;

/**
 * This class represents the UI (e.g. Fragment/Activity) hosting playback controls and
 * defines the interaction between {@link PlaybackGlue} and the host.
 * PlaybackGlueHost provides the following functions:
 * <li>Render UI of PlaybackGlue: {@link #setPlaybackRow(Row)},
 * {@link #setPlaybackRowPresenter(PlaybackRowPresenter)}.
 * </li>
 * <li>Client for fragment/activity onStart/onStop: {@link #setHostCallback(HostCallback)}.
 * </li>
 * <li>Auto fade out controls after a short period: {@link #setFadingEnabled(boolean)}.
 * </li>
 * <li>Key listener and ActionListener. {@link #setOnKeyInterceptListener(View.OnKeyListener)},
 * {@link #setOnActionClickedListener(OnActionClickedListener)}.
 * </li>
 *
 * Subclass of PlaybackGlueHost may implement optional interfaces:
 * <li>{@link SurfaceHolderGlueHost} to provide SurfaceView for video playback.</li>
 * <li>{@link PlaybackSeekUi} to provide seek UI to glue</li>
 * These optional interfaces should be accessed by glue in
 * {@link PlaybackGlue#onAttachedToHost(PlaybackGlueHost)}.
 */
public abstract class PlaybackGlueHost {
    PlaybackGlue mGlue;

    /**
     * Callbacks triggered by the host(e.g. fragment) hosting the video controls/surface.
     *
     * @see #setHostCallback(HostCallback)
     */
    public abstract static class HostCallback {
        /**
         * Client triggered once the host(fragment) has started.
         */
        public void onHostStart() {
        }

        /**
         * Client triggered once the host(fragment) has stopped.
         */
        public void onHostStop() {
        }

        /**
         * Client triggered once the host(fragment) has paused.
         */
        public void onHostPause() {
        }

        /**
         * Client triggered once the host(fragment) has resumed.
         */
        public void onHostResume() {
        }

        /**
         * Client triggered once the host(fragment) has been destroyed.
         */
        public void onHostDestroy() {
        }
    }

    /**
     * Optional Client that implemented by PlaybackGlueHost to respond to player event.
     */
    public static class PlayerCallback {
        /**
         * Size of the video changes, the Host should adjust SurfaceView's layout width and height.
         * @param videoWidth
         * @param videoHeight
         */
        public void onVideoSizeChanged(int videoWidth, int videoHeight) {
        }

        /**
         * notify media starts/stops buffering/preparing. The Host could start or stop
         * progress bar.
         * @param start True for buffering start, false otherwise.
         */
        public void onBufferingStateChanged(boolean start) {
        }

        /**
         * notify media has error. The Host could show error dialog.
         * @param errorCode Optional error code for specific implementation.
         * @param errorMessage Optional error message for specific implementation.
         */
        public void onError(int errorCode, CharSequence errorMessage) {
        }
    }

    /**
     * Enables or disables view fading.  If enabled, the view will be faded in when the
     * fragment starts and will fade out after a time period.
     * @deprecated Use {@link #setControlsOverlayAutoHideEnabled(boolean)}
     */
    @Deprecated
    public void setFadingEnabled(boolean enable) {
    }

    /**
     * Enables or disables controls overlay auto hidden.  If enabled, the view will be faded out
     * after a time period.
     * @param enabled True to enable auto hidden of controls overlay.
     *
     */
    public void setControlsOverlayAutoHideEnabled(boolean enabled) {
        setFadingEnabled(enabled);
    }

    /**
     * Returns true if auto hides controls overlay.
     * @return True if auto hiding controls overlay.
     */
    public boolean isControlsOverlayAutoHideEnabled() {
        return false;
    }

    /**
     * Fades out the playback overlay immediately.
     * @deprecated Call {@link #hideControlsOverlay(boolean)}
     */
    @Deprecated
    public void fadeOut() {
    }

    /**
     * Returns true if controls overlay is visible, false otherwise.
     *
     * @return True if controls overlay is visible, false otherwise.
     * @see #showControlsOverlay(boolean)
     * @see #hideControlsOverlay(boolean)
     */
    public boolean isControlsOverlayVisible() {
        return true;
    }

    /**
     * Hide controls overlay.
     *
     * @param runAnimation True to run animation, false otherwise.
     */
    public void hideControlsOverlay(boolean runAnimation) {
    }

    /**
     * Show controls overlay.
     *
     * @param runAnimation True to run animation, false otherwise.
     */
    public void showControlsOverlay(boolean runAnimation) {
    }

    /**
     * Sets the {@link android.view.View.OnKeyListener} on the host. This would trigger
     * the listener when a {@link android.view.KeyEvent} is unhandled by the host.
     */
    public void setOnKeyInterceptListener(View.OnKeyListener onKeyListener) {
    }

    /**
     * Sets the {@link View.OnClickListener} on this fragment.
     */
    public void setOnActionClickedListener(OnActionClickedListener listener) {}

    /**
     * Sets the host {@link HostCallback} callback on the host. This method should only be called
     * by {@link PlaybackGlue}. App should not directly call this method, app should override
     * {@link PlaybackGlue#onHostStart()} etc.
     */
    public void setHostCallback(HostCallback callback) {
    }

    /**
     * Notifies host about a change so it can update the view.
     */
    public void notifyPlaybackRowChanged() {}

    /**
     * Sets {@link PlaybackRowPresenter} for rendering the playback controls.
     */
    public void setPlaybackRowPresenter(PlaybackRowPresenter presenter) {}

    /**
     * Sets the {@link Row} that represents the information on control items that needs
     * to be rendered.
     */
    public void setPlaybackRow(Row row) {}

    final void attachToGlue(PlaybackGlue glue) {
        if (mGlue != null) {
            mGlue.onDetachedFromHost();
        }
        mGlue = glue;
        if (mGlue != null) {
            mGlue.onAttachedToHost(this);
        }
    }

    /**
     * Implemented by PlaybackGlueHost for responding to player events. Such as showing a spinning
     * wheel progress bar when {@link PlayerCallback#onBufferingStateChanged(boolean)}.
     * @return PlayerEventCallback that Host supports, null if not supported.
     */
    public PlayerCallback getPlayerCallback() {
        return null;
    }

}
