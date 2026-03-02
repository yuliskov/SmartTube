package com.github.vkay94.dtpv

import com.google.android.exoplayer2.ui.PlayerView

interface DoubleTapPlayerView {
    /**
     * Returns the View width
     */
    val playerWidth: Int
    /**
     * If this field is set to `true` this view will handle double tapping, otherwise it will
     * handle touches the same way as the original [PlayerView][com.google.android.exoplayer2.ui.PlayerView] does
     */
    var isDoubleTapEnabled: Boolean
    /**
     * Time window a double tap is active, so a followed tap is calling a gesture detector
     * method instead of normal tap (see [PlayerView.onTouchEvent])
     */
    var doubleTapDelay: Long
    /**
     * Sets the [PlayerDoubleTapListener] which handles the gesture callbacks.
     *
     * Primarily used for [YouTubeOverlay][com.github.vkay94.dtpv.youtube.YouTubeOverlay]
     */
    fun controller(controller: PlayerDoubleTapListener?): DoubleTapPlayerView
    /**
     * Returns the current state of double tapping.
     */
    fun isInDoubleTapMode(): Boolean
    /**
     * Resets the timeout to keep in double tap mode.
     *
     * Called once in [PlayerDoubleTapListener.onDoubleTapStarted]. Needs to be called
     * from outside if the double tap is customized / overridden to detect ongoing taps
     */
    fun keepInDoubleTapMode()
    /**
     * Cancels double tap mode instantly by calling [PlayerDoubleTapListener.onDoubleTapFinished]
     */
    fun cancelInDoubleTapMode()
}