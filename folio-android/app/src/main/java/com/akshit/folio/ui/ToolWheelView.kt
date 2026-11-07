package com.akshit.folio.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The tool wheel: centre = current color (tap → color wheel), with three arc sliders around it
 * for brush size, opacity and smoothing. Drag along an arc to adjust.
 */
@SuppressLint("ViewConstructor")
class ToolWheelView(ctx: Context) : View(ctx) {
    var color = Color.BLACK; set(v) { field = v; invalidate() }
    var sizeT = 0.4f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var opacity = 1f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var smoothing = 0.3f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var sizeLabel: (Float) -> String = { "" }
    var eraserMode = false; set(v) { field = v; invalidate() }

    var onCenterTap: (() -> Unit)? = null
    var onCenterLongPress: (() -> Unit)? = null
    var onSize: ((Float) -> Unit)? = null
    var onOpacity: ((Float) -> Unit)? = null
    var onSmoothing: ((Float) -> Unit)? = null

    private val arcs = arrayOf(floatArrayOf(145f, 110f), floatArrayOf(265f, 110f), floatArrayOf(25f, 110f)) // start, sweep (deg)
    private val names = arrayOf("SIZE", "OPACITY", "SMOOTH")
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()
    private var active = -1
    private var downX = 0f; private var downY = 0f; private var moved = false
    private val longPress = Runnable { if (active == 3 && !moved) { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onCenterLongPress?.invoke(); active = -1 } }

    override fun onMeasure(w: Int, h: Int) { val s = UI.dp(context, 150f); setMeasuredDimension(s, s) }

    private fun value(i: Int) = when (i) { 0 -> sizeT; 1 -> opacity; else -> smoothing }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f - UI.dpf(context, 14f)
        // backdrop
        fill.color = 0xFAFFFFFF.toInt()
        fill.setShadowLayer(UI.dpf(context, 6f), 0f, UI.dpf(context, 2f), 0x33000000)
        c.drawCircle(cx, cy, r + UI.dpf(context, 12f), fill)
        fill.clearShadowLayer()
        oval.set(cx - r, cy - r, cx + r, cy + r)
        track.strokeWidth = UI.dpf(context, 9f)
        for (i in 0 until 3) {
            if (eraserMode && i != 0) continue
            track.color = 0x1C000000
            c.drawArc(oval, arcs[i][0], arcs[i][1], false, track)
            track.color = if (i == active) 0xFF1A5FD0.toInt() else UI.ACCENT
            c.drawArc(oval, arcs[i][0], arcs[i][1] * value(i).coerceAtLeast(0.01f), false, track)
            // label at arc middle (inside)
            val mid = Math.toRadians((arcs[i][0] + arcs[i][1] / 2).toDouble())
            val lr = r - UI.dpf(context, 17f)
            text.textSize = UI.dpf(context, 8.5f); text.color = UI.SUB; text.isFakeBoldText = true
            c.drawText(names[i], cx + (lr * cos(mid)).toFloat(), cy + (lr * sin(mid)).toFloat() + UI.dpf(context, 3f), text)
        }
        // centre swatch with brush size dot
        val inner = r - UI.dpf(context, 30f)
        fill.color = if (eraserMode) 0xFFF4F4F6.toInt() else color or (0xFF shl 24)
        c.drawCircle(cx, cy, inner, fill)
        track.strokeWidth = UI.dpf(context, 2f); track.color = 0x22000000
        c.drawCircle(cx, cy, inner, track)
        text.isFakeBoldText = true
        text.textSize = UI.dpf(context, 11f)
        val lum = if (eraserMode) 1f else ColorUtil.luminance(color)
        text.color = if (lum > 0.6f) 0xFF222222.toInt() else Color.WHITE
        c.drawText(sizeLabel(sizeT), cx, cy + UI.dpf(context, 4f), text)
        if (active in 0..2) {
            val label = when (active) { 0 -> sizeLabel(sizeT); 1 -> "${(opacity * 100).toInt()}%"; else -> "${(smoothing * 100).toInt()}%" }
            text.textSize = UI.dpf(context, 10f)
            c.drawText(names[active], cx, cy - UI.dpf(context, 10f), text)
            text.textSize = UI.dpf(context, 13f)
            c.drawText(label, cx, cy + UI.dpf(context, 10f), text)
        }
    }

    private fun angleOf(x: Float, y: Float): Float {
        var a = Math.toDegrees(atan2((y - height / 2f).toDouble(), (x - width / 2f).toDouble())).toFloat()
        if (a < 0) a += 360f
        return a
    }

    private fun arcValue(i: Int, a: Float): Float {
        var d = a - arcs[i][0]
        while (d < -60) d += 360f
        while (d > 300) d -= 360f
        return (d / arcs[i][1]).coerceIn(0f, 1f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f - UI.dpf(context, 14f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; moved = false
                val d = hypot(e.x - cx, e.y - cy)
                if (d > r + UI.dpf(context, 22f)) return false
                active = if (d < r - UI.dpf(context, 28f)) 3 else {
                    val a = angleOf(e.x, e.y)
                    var best = 0; var bestD = 999f
                    for (i in 0 until 3) {
                        if (eraserMode && i != 0) continue
                        val mid = arcs[i][0] + arcs[i][1] / 2
                        var dd = kotlin.math.abs(a - mid); if (dd > 180) dd = 360 - dd
                        if (dd < bestD) { bestD = dd; best = i }
                    }
                    best
                }
                if (active == 3) postDelayed(longPress, 500)
                parent?.requestDisallowInterceptTouchEvent(true)
                if (active in 0..2) apply(e.x, e.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(e.x - downX, e.y - downY) > UI.dpf(context, 6f)) moved = true
                if (active in 0..2) apply(e.x, e.y)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (active == 3 && !moved) onCenterTap?.invoke()
                active = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); active = -1; invalidate() }
        }
        return true
    }

    private fun apply(x: Float, y: Float) {
        val v = arcValue(active, angleOf(x, y))
        when (active) {
            0 -> { sizeT = v; onSize?.invoke(v) }
            1 -> { opacity = v.coerceAtLeast(0.02f); onOpacity?.invoke(opacity) }
            2 -> { smoothing = v; onSmoothing?.invoke(v) }
        }
        invalidate()
    }
}
