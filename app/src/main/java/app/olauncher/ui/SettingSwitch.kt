package app.olauncher.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import app.olauncher.R
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemAnimationsDisabled
import kotlin.math.ceil

/**
 * On/off state at the end of a settings row. The whole row takes the tap and sets [isChecked];
 * this view only shows the state. Drawn by hand rather than with SwitchCompat, whose size follows
 * its thumb, to get the 36 x 20 dp track with a 16 dp thumb 2 dp inside it.
 */
class SettingSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val trackWidth = 36 * density
    private val trackHeight = 20 * density
    private val thumbRadius = 8 * density
    private val thumbInset = 2 * density

    private val trackOn = context.getColorFromAttr(R.attr.primaryColor)
    private val trackOff = context.getColorFromAttr(R.attr.switchTrackOffColor)
    private val thumbOn = context.getColorFromAttr(R.attr.settingsBackgroundColor)
    private val thumbOff = context.getColorFromAttr(R.attr.switchThumbOffColor)
    private val animated = !context.isEinkDisplay() && !context.isSystemAnimationsDisabled()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val evaluator = ArgbEvaluator()
    private val track = RectF()
    private var animator: ValueAnimator? = null

    // 0 is off, 1 is on
    private var position = 0f

    var isChecked = false
        set(value) {
            if (field == value) return
            field = value
            val target = if (value) 1f else 0f
            animator?.cancel()
            if (animated && isLaidOut) {
                animator = ValueAnimator.ofFloat(position, target).apply {
                    duration = 150
                    addUpdateListener {
                        position = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else {
                position = target
                invalidate()
            }
        }

    init {
        // The row it sits in is what gets focus and clicks
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(ceil(trackWidth).toInt(), widthMeasureSpec),
            resolveSize(ceil(trackHeight).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val left = (width - trackWidth) / 2
        val top = (height - trackHeight) / 2
        track.set(left, top, left + trackWidth, top + trackHeight)
        paint.color = evaluator.evaluate(position, trackOff, trackOn) as Int
        canvas.drawRoundRect(track, trackHeight / 2, trackHeight / 2, paint)

        val travel = trackWidth - 2 * (thumbInset + thumbRadius)
        paint.color = evaluator.evaluate(position, thumbOff, thumbOn) as Int
        canvas.drawCircle(left + thumbInset + thumbRadius + position * travel, top + trackHeight / 2, thumbRadius, paint)
    }

    override fun onDetachedFromWindow() {
        animator?.end()
        super.onDetachedFromWindow()
    }
}
