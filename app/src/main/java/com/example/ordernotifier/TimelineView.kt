package com.example.ordernotifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MR

/** A strip of dots showing when orders would arrive. Burst extras stack upwards. */
class TimelineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val d = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4 * d
        strokeCap = Paint.Cap.ROUND
        color = MaterialColors.getColor(this@TimelineView, MR.attr.colorOutlineVariant)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@TimelineView, MR.attr.colorPrimary)
    }
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@TimelineView, MR.attr.colorTertiary)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@TimelineView, MR.attr.colorOnSurfaceVariant)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }

    private var points: List<Pair<Float, Int>> = emptyList()
    private var endLabel = ""

    /** [points]: position 0..1 along the window and stack level (0 = normal order). */
    fun setData(points: List<Pair<Float, Int>>, endLabel: String) {
        this.points = points
        this.endLabel = endLabel
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize((76 * d).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = 6 * d
        val left = paddingLeft + r
        val right = width - paddingRight - r
        val cy = height - 26 * d
        canvas.drawLine(left, cy, right, cy, trackPaint)
        for ((f, stack) in points) {
            val x = left + f.coerceIn(0f, 1f) * (right - left)
            if (stack == 0) {
                canvas.drawCircle(x, cy, r, dotPaint)
            } else {
                canvas.drawCircle(x, cy - minOf(stack, 4) * 9 * d, r * 0.8f, burstPaint)
            }
        }
        val baseline = height - 4 * d
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("now", left - r, baseline, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(endLabel, right + r, baseline, labelPaint)
    }
}
