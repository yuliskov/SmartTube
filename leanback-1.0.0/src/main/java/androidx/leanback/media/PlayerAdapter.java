/*
 * Copyright (C) 2017 The Android Open Source Project
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

/**
 * Base class that wraps underlying media player. The class is used by PlaybackGlue, for example
 * {@link PlaybackTransportControlGlue} is bound to a PlayerAdapter.
 * This class is intended to be subclassed, {@link MediaPlayerAdapter} is a concrete subclass
 * using {@link android.media.MediaPlayer}.
 * ExoPlayer also provides a leanback extension that implements PlayerAdapter. Please see
 * <a href="https://developer.android.com/guide/topics/media/exoplayer">ExoPlayer</a>
 * https://developer.android.com/guide/topics/media/exoplayer
 */
public abstract class PlayerAdapter {

    /**
     * Client for client of PlayerAdapter.
     */
    public static class Callback {

        /**
         * Client for Play/Pause state change. See {@link #isPlaying()}.
         */
        public void onPlayStateChanged(PlayerAdapter adapter) {
        }

        /**
         * Client for {@link #isPrepared()} changed.
         * @param adapter The adapter that has changed ready state.
         */
        public void onPreparedStateChanged(PlayerAdapter adapter) {
        }

        /**
         * Client when the current media is finished.
         * @param adapter The adapter that has just finished current media.
         */
        public void onPlayCompleted(PlayerAdapter adapter) {
        }

        /**
         * Event for {@link #getCurrentPosition()} changed.
         * @param adapter The adapter whose {@link #getCurrentPosition()} changed.
         */
        public void onCurrentPositionChanged(PlayerAdapter adapter) {
        }

        /**
         * Event for {@link #getBufferedPosition()} changed.
         * @param adapter The adapter whose {@link #getBufferedPosition()} changed.
         */
        public void onBufferedPositionChanged(PlayerAdapter adapter) {
        }

        /**
         * Event for {@link #getDuration()} changed. Usually the duration does not change
         * after playing except for live stream.
         * @param adapter The adapter whose {@link #getDuration()} changed.
         */
        public void onDurationChanged(PlayerAdapter adapter) {
        }

        /**
         * Event for video size changed.
         * @param adapter The adapter whose video size has been detected or changed.
         * @param width Intrinsic width of the video.
         * @param height Intrinsic height of the video.
         */
        public void onVideoSizeChanged(PlayerAdapter adapter, int width, int height) {
        }

        /**
         * Event for error.
         * @param adapter The adapter that encounters error.
         * @param errorCode Optional error code, specific to implementation.
         * @param errorMessage Optional error message, specific to implementation.
         */
        public void onError(PlayerAdapter adapter, int errorCode, String errorMessage) {
        }

        /**
         * Event for buffering start or stop. Initial default value is false.
         * @param adapter The adapter that begins buffering or finishes buffering.
         * @param start True for buffering start, false otherwise.
         */
        public void onBufferingStateChanged(PlayerAdapter adapter, boolean start) {
        }

        /**
         * Event for meta data changed.
         * @param adapter The adapter that finishes current media item.
         */
        public void onMetadataChanged(PlayerAdapter adapter) {
        }
    }

    Callback mCallback;

    /**
     * Sets callback for event of PlayerAdapter.
     * @param callback Client for event of PlayerAdapter.
     */
    public final void setCallback(Callback callback) {
        mCallback = callback;
    }

    /**
     * Gets callback for event of PlayerAdapter.
     * @return Client for event of PlayerAdapter.
     */
    public final Callback getCallback() {
        return mCallback;
    }

    /**
     * @return True if media is ready for playback, false otherwise.
     */
    public boolean isPrepared() {
        return true;
    }

    /**
     * Starts the media player.
     */
    public abstract void play();

    /**
     * Pauses the media player.
     */
    public abstract void pause();

    /**
     * Optional method. Override this method if {@link #getSupportedActions()} include
     * {@link PlaybackBaseControlGlue#ACTION_SKIP_TO_NEXT} to skip
     * to next item.
     */
    public void next() {
    }

    /**
     * Optional method. Override this method if {@link #getSupportedActions()} include
     * {@link PlaybackBaseControlGlue#ACTION_SKIP_TO_PREVIOUS} to skip
     * to previous item.
     */
    public void previous() {
    }

    /**
     * Optional method. Override this method if {@link #getSupportedActions()} include
     * {@link PlaybackBaseControlGlue#ACTION_FAST_FORWARD} to fast
     * forward current media item.
     */
    public void fastForward() {
    }

    /**
     * Optional method. Override this method if {@link #getSupportedActions()} include
     * {@link PlaybackBaseControlGlue#ACTION_REWIND} to rewind in
     * current media item.
     */
    public void rewind() {
    }

    /**
     * Seek to new position.
     * @param positionInMs New position in milliseconds.
     */
    public void seekTo(long positionInMs) {
    }

    /**
     * Implement this method to enable or disable progress updating.
     * @param enable True to enable progress updating, false otherwise.
     */
    public void setProgressUpdatingEnabled(boolean enable) {
    }

    /**
     * Optional method. Override this method if {@link #getSupportedActions()} include
     * {@link PlaybackBaseControlGlue#ACTION_SHUFFLE} to set the shuffle action.
     *
     * @param shuffleActionIndex The repeat action. Must be one of the followings:
     *                           {@link androidx.leanback.widget.PlaybackControlsRow.ShuffleAction#INDEX_OFF}
     *                           {@link androidx.leanback.widget.PlaybackControlsRow.ShuffleAction#INDEX_ON}
     */
    public void setShuffleAction(int shuffleActionIndex) {
    }

    /**
     * Optional method. Override this method if {@link #getSupportedActions()} include
     * {@link PlaybackBaseControlGlue#ACTION_REPEAT} to set the repeat action.
     *
     * @param repeatActionIndex The shuffle action. Must be one of the followings:
     *                          {@link androidx.leanback.widget.PlaybackControlsRow.RepeatAction#INDEX_ONE}
     *                          {@link androidx.leanback.widget.PlaybackControlsRow.RepeatAction#INDEX_ALL},
     *                          {@link androidx.leanback.widget.PlaybackControlsRow.RepeatAction#INDEX_NONE},
     */
    public void setRepeatAction(int repeatActionIndex) {
    }

    /**
     * Returns true if media is currently playing.
     */
    public boolean isPlaying() {
        return false;
    }

    /**
     * Returns the duration of the media item in milliseconds.
     */
    public long getDuration() {
        return 0;
    }

    /**
     * Return xor combination of values defined in PlaybackBaseControlGlue.
     * Default is PLAY_PAUSE (unless subclass enforce to be 0)
     */
    public long getSupportedActions() {
        return PlaybackBaseControlGlue.ACTION_PLAY_PAUSE;
    }

    /**
     * Returns the current position of the media item in milliseconds.
     */
    public long getCurrentPosition() {
        return 0;
    }

    /**
     * Returns the current buffered position of the media item in milliseconds.
     */
    public long getBufferedPosition() {
        return 0;
    }

    /**
     * This method is called attached to associated {@link PlaybackGlueHost}.
     * @param host
     */
    public void onAttachedToHost(PlaybackGlueHost host) {
    }

    /**
     * This method is called when current associated {@link PlaybackGlueHost} is attached to a
     * different {@link PlaybackGlue} or {@link PlaybackGlueHost} is destroyed. Subclass may
     * override. A typical implementation will release resources (e.g. MediaPlayer or connection
     * to playback service) in this method.
     */
    public void onDetachedFromHost() {
    }
}
