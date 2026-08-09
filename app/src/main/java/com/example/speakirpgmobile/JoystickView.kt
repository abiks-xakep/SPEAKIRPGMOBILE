package com.example.speakirpgmobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class JoystickView(context: Context) : View(context) {

    var onDirectionChanged: ((Set<Direction>) -> Unit)? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88000000.toInt()
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
    }

    private val center = PointF()
    private val knob = PointF()

    private var baseRadius = 0f
    private var knobRadius = 0f
    private var activeDirections: Set<Direction> = emptySet()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = min(w, h).toFloat()
        center.set(w / 2f, h / 2f)
        knob.set(center.x, center.y)
        baseRadius = size * 0.43f
        knobRadius = size * 0.19f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawCircle(center.x, center.y, baseRadius, basePaint)
        canvas.drawCircle(center.x, center.y, baseRadius, ringPaint)
        canvas.drawCircle(knob.x, knob.y, knobRadius, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                updateStick(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                knob.set(center.x, center.y)
                setDirections(emptySet())
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun updateStick(x: Float, y: Float) {
        val dx = x - center.x
        val dy = y - center.y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        val maxDistance = baseRadius
        if (distance <= maxDistance || distance == 0f) {
            knob.set(x, y)
        } else {
            val angle = atan2(dy.toDouble(), dx.toDouble())
            knob.set(
                center.x + cos(angle).toFloat() * maxDistance,
                center.y + sin(angle).toFloat() * maxDistance
            )
        }

        // Dead zone stops accidental movement near the center.
        val normalizedX = ((knob.x - center.x) / baseRadius).coerceIn(-1f, 1f)
        val normalizedY = ((knob.y - center.y) / baseRadius).coerceIn(-1f, 1f)
        val deadZone = 0.28f

        val directions = mutableSetOf<Direction>()

        // Independent X/Y thresholds naturally allow diagonals.
        if (normalizedY < -deadZone) directions += Direction.UP
        if (normalizedY > deadZone) directions += Direction.DOWN
        if (normalizedX < -deadZone) directions += Direction.LEFT
        if (normalizedX > deadZone) directions += Direction.RIGHT

        setDirections(directions)
        invalidate()
    }

    private fun setDirections(newDirections: Set<Direction>) {
        if (newDirections == activeDirections) return

        if (activeDirections.isEmpty() && newDirections.isNotEmpty()) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        activeDirections = newDirections
        onDirectionChanged?.invoke(newDirections)
    }
}
