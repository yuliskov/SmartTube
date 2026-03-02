package com.github.vkay94.dtpv.youtube.views

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.github.vkay94.dtpv.R

/**
 * View class
 *
 * Draws a arc shape and provides a circle scaling animation.
 * Used by [YouTubeOverlay][com.github.vkay94.dtpv.youtube.YouTubeOverlay].
 */
internal class CircleClipTapView(context: Context?, attrs: AttributeSet) :
    View(context, attrs) {

    private var backgroundPaint = Paint()
    private var circlePaint = Paint()

    private var widthPx = 0
    private var heightPx = 0

    // Background

    private var shapePath = Path()
    private var isLeft = true

    // Circle

    private var cX = 0f
    private var cY = 0f

    private var currentRadius = 0f
    private var minRadius: Int = 0
    private var maxRadius: Int = 0

    // Animation

    private var valueAnimator: ValueAnimator? = null
    private var forceReset = false

    init {
        requireNotNull(context) { "Context is null." }

        backgroundPaint.apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            color = ContextCompat.getColor(context, R.color.dtpv_yt_background_circle_color)
        }

        circlePaint.apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            color = ContextCompat.getColor(context, R.color.dtpv_yt_tap_circle_color)
        }

        // Pre-configuations depending on device display metrics
        val dm = context.resources.displayMetrics

        widthPx = dm.widthPixels
        heightPx = dm.heightPixels

        minRadius = (30f * dm.density).toInt()
        maxRadius = (400f * dm.density).toInt()

        updatePathShape()

        valueAnimator = getCircleAnimator()
    }

    var performAtEnd: () -> Unit = { }

    /*
        Getter and setter
     */

    var arcSize: Float = 80f
        set(value) {
            field = value
            updatePathShape()
        }

    var circleBackgroundColor: Int
        get() = backgroundPaint.color
        set(value) {
            backgroundPaint.color = value
        }

    var circleColor: Int
        get() = circlePaint.color
        set(value) {
            circlePaint.color = value
        }

    var animationDuration: Long
        get() = valueAnimator?.duration ?: 650
        set(value) {
            getCircleAnimator().duration = value
        }

    /*
       Methods
    */

    /*
        Circle
     */

    fun updatePosition(x: Float, y: Float) {
        cX = x
        cY = y

        val newIsLeft = x <= resources.displayMetrics.widthPixels / 2
        if (isLeft != newIsLeft) {
            isLeft = newIsLeft
            updatePathShape()
        }
    }

    private fun invalidateWithCurrentRadius(factor: Float) {
        currentRadius = minRadius + ((maxRadius - minRadius) * factor)
        invalidate()
    }

    /*
        Background
     */

    private fun updatePathShape() {
        val halfWidth = widthPx * 0.5f

        shapePath.reset()
//        shapePath.fillType = Path.FillType.WINDING

        val w = if (isLeft) 0f else widthPx.toFloat()
        val f = if (isLeft) 1 else -1

        shapePath.moveTo(w, 0f)
        shapePath.lineTo(f * (halfWidth - arcSize) + w, 0f)
        shapePath.quadTo(
            f * (halfWidth + arcSize) + w,
            heightPx.toFloat() / 2,
            f * (halfWidth - arcSize) + w,
            heightPx.toFloat()
        )
        shapePath.lineTo(w, heightPx.toFloat())

        shapePath.close()
        invalidate()
    }

    /*
        Animation
     */

    private fun getCircleAnimator(): ValueAnimator {
        if (valueAnimator == null) {
            valueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = animationDuration
//                interpolator = LinearInterpolator()
                addUpdateListener {
                    invalidateWithCurrentRadius(it.animatedValue as Float)
                }

                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                        visibility = VISIBLE
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!forceReset) performAtEnd()
                    }

                    override fun onAnimationRepeat(animation: Animator) {}
                    override fun onAnimationCancel(animation: Animator) {}
                })
            }
        }
        return valueAnimator!!
    }

    fun resetAnimation(body: () -> Unit) {
        forceReset = true
        getCircleAnimator().end()
        body()
        forceReset = false
        getCircleAnimator().start()
    }

    fun endAnimation() {
        getCircleAnimator().end()
    }

    /*
        Others: Drawing and Measurements
     */

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        widthPx = w
        heightPx = h
        updatePathShape()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.clipPath(shapePath)
        canvas.drawPath(shapePath, backgroundPaint)

        // Circle
        canvas.drawCircle(cX, cY, currentRadius, circlePaint)
    }
}