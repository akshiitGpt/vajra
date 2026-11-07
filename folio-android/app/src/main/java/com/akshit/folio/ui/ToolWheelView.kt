package com.akshit.folio.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
 * The Tool Wheel.
 *
 *  - centre: current colour (tap → colour wheel)
 *  - inner ring: three arc sliders: size, opacity, smoothing
 *  - outer rim: up to 8 tool slots (tap = select, tap again = brush settings,
 *    long-press = assign a different tool, drag = move the wheel)
 *  - pinch on the wheel to resize it
 */
@SuppressLint("ViewConstructor")
class ToolWheelView(ctx: Context) : View(ctx) {
    class Slot(val icon: String, val label: String, val color: Int?, val sizeText: String)

    var slots: List<Slot> = emptyList(); set(v) { field = v; invalidate() }
    var activeSlot = 0; set(v) { field = v; invalidate() }
    var color = Color.BLACK; set(v) { field = v; invalidate() }
    var sizeT = 0.4f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var opacity = 1f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var smoothing = 0.3f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var sizeLabel: (Float) -> String = { "" }
    var eraserMode = false; set(v) { field = v; invalidate() }
    /** Compact = colour + sliders only (used at the top of the Tool Bar). */
    var compact = false; set(v) { field = v; requestLayout(); invalidate() }
    /** Wheel scale (pinch to resize), 0.7 .. 1.4 */
    var scaleFactor = 1f; set(v) { field = v.coerceIn(if (compact) 0.45f else 0.7f, 1.4f); requestLayout(); invalidate() }

    var onCenterTap: (() -> Unit)? = null
    var onCenterLongPress: (() -> Unit)? = null
    var onSlotTap: ((Int) -> Unit)? = null
    var onSlotLongPress: ((Int) -> Unit)? = null
    var onSize: ((Float) -> Unit)? = null
    var onOpacity: ((Float) -> Unit)? = null
    var onSmoothing: ((Float) -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null

    // inner ring arcs: start, sweep (deg, 0 = 3 o'clock, clockwise)
    private val arcs = arrayOf(floatArrayOf(150f, 100f), floatArrayOf(270f, 100f), floatArrayOf(30f, 100f))
    private val names = arrayOf("SIZE", "OPACITY", "SMOOTH")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()
    private val wedge = Path()
    private val icons = HashMap<String, IconDrawable>()

    private var mode = 0 // 0 none, 1 size, 2 opacity, 3 smoothing, 4 centre, 5 slot, 6 drag, 7 pinch
    private var downX = 0f; private var downY = 0f; private var downRawX = 0f; private var downRawY = 0f
    private var moved = false; private var downSlot = -1; private var longFired = false
    private var pinchD0 = 0f; private var pinchS0 = 1f
    private val longPress = Runnable {
        if (moved) return@Runnable
        longFired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        when (mode) { 4 -> onCenterLongPress?.invoke(); 5 -> if (downSlot >= 0) onSlotLongPress?.invoke(downSlot) }
    }

    private fun dp(v: Float) = UI.dpf(context, v) * scaleFactor
    private val outerR get() = if (compact) dp(66f) else dp(104f)
    private val rimInner get() = if (compact) dp(66f) else dp(68f)
    private val ringR get() = dp(55f)
    private val centreR get() = dp(36f)

    override fun onMeasure(w: Int, h: Int) { val s = (outerR * 2 + dp(14f)).toInt(); setMeasuredDimension(s, s) }

    private fun value(i: Int) = when (i) { 0 -> sizeT; 1 -> opacity; else -> smoothing }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        // body with soft shadow
        fill.color = 0xFAFAFAFB.toInt()
        fill.setShadowLayer(dp(7f), 0f, dp(2f), 0x38000000)
        c.drawCircle(cx, cy, outerR, fill)
        fill.clearShadowLayer()
        // rim separator
        stroke.strokeWidth = dp(1f); stroke.color = 0x14000000
        if (!compact) c.drawCircle(cx, cy, rimInner, stroke)

        // ---- tool slots on the rim
        val n = slots.size.coerceAtLeast(1)
        val sweep = 360f / n
        for (i in slots.indices) {
            val start = -90f - sweep / 2 + i * sweep
            if (i == activeSlot) {
                oval.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
                wedge.reset()
                wedge.arcTo(oval, start + 1.5f, sweep - 3f, true)
                val inner = RectF(cx - rimInner, cy - rimInner, cx + rimInner, cy + rimInner)
                wedge.arcTo(inner, start + sweep - 1.5f, -(sweep - 3f))
                wedge.close()
                fill.color = 0x242F7CF6
                c.drawPath(wedge, fill)
            }
            // divider ticks
            val a = Math.toRadians(start.toDouble())
            stroke.color = 0x12000000; stroke.strokeWidth = dp(1f)
            c.drawLine(cx + (rimInner * cos(a)).toFloat(), cy + (rimInner * sin(a)).toFloat(),
                cx + (outerR * cos(a)).toFloat(), cy + (outerR * sin(a)).toFloat(), stroke)
            val mid = Math.toRadians((start + sweep / 2).toDouble())
            val r = (outerR + rimInner) / 2
            val ix = cx + (r * cos(mid)).toFloat(); val iy = cy + (r * sin(mid)).toFloat()
            val s = slots[i]
            val d = icons.getOrPut(s.icon) { IconDrawable(s.icon, UI.ICON, 1.7f) }
            d.color = if (i == activeSlot) UI.ACCENT else UI.ICON
            val half = dp(11f)
            d.setBounds((ix - half).toInt(), (iy - half - dp(4f)).toInt(), (ix + half).toInt(), (iy + half - dp(4f)).toInt())
            d.draw(c)
            text.textSize = dp(8.5f); text.isFakeBoldText = i == activeSlot
            text.color = if (i == activeSlot) UI.ACCENT else UI.SUB
            c.drawText(s.sizeText, ix, iy + dp(14f), text)
            s.color?.let { col ->
                fill.color = col
                c.drawCircle(ix + dp(12f), iy - dp(11f), dp(3.5f), fill)
            }
        }

        // ---- inner ring sliders
        oval.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        stroke.strokeWidth = dp(8f)
        for (i in 0 until 3) {
            if (eraserMode && i != 0) continue
            stroke.color = 0x1A000000
            c.drawArc(oval, arcs[i][0], arcs[i][1], false, stroke)
            stroke.color = if (mode == i + 1) 0xFF1A5FD0.toInt() else UI.ACCENT
            c.drawArc(oval, arcs[i][0], arcs[i][1] * value(i).coerceAtLeast(0.02f), false, stroke)
        }

        // ---- centre colour
        fill.color = if (eraserMode) 0xFFEDEDF0.toInt() else color or (0xFF shl 24)
        c.drawCircle(cx, cy, centreR, fill)
        stroke.strokeWidth = dp(2f); stroke.color = 0x22000000
        c.drawCircle(cx, cy, centreR, stroke)
        val lum = if (eraserMode) 1f else ColorUtil.luminance(color)
        text.color = if (lum > 0.6f) 0xFF222222.toInt() else Color.WHITE
        text.isFakeBoldText = true
        if (mode in 1..3) {
            val i = mode - 1
            val label = when (i) { 0 -> sizeLabel(sizeT); 1 -> "${(opacity * 100).toInt()}%"; else -> "${(smoothing * 100).toInt()}%" }
            text.textSize = dp(8f); c.drawText(names[i], cx, cy - dp(6f), text)
            text.textSize = dp(13f); c.drawText(label, cx, cy + dp(10f), text)
        } else {
            text.textSize = dp(11f)
            c.drawText(sizeLabel(sizeT), cx, cy + dp(4f), text)
        }
    }

    private fun angleOf(x: Float, y: Float): Float {
        var a = Math.toDegrees(atan2((y - height / 2f).toDouble(), (x - width / 2f).toDouble())).toFloat()
        if (a < 0) a += 360f
        return a
    }

    private fun slotAt(x: Float, y: Float): Int {
        val n = slots.size
        if (n == 0) return -1
        val sweep = 360f / n
        var a = angleOf(x, y) + 90f + sweep / 2
        a = ((a % 360f) + 360f) % 360f
        return (a / sweep).toInt().coerceIn(0, n - 1)
    }

    private fun arcValue(i: Int, a: Float): Float {
        var d = a - arcs[i][0]
        while (d < -80) d += 360f
        while (d > 280) d -= 360f
        return (d / arcs[i][1]).coerceIn(0f, 1f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val cx = width / 2f; val cy = height / 2f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dist = hypot(e.x - cx, e.y - cy)
                if (dist > outerR + dp(4f)) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.x; downY = e.y; downRawX = e.rawX; downRawY = e.rawY; moved = false; longFired = false
                mode = when {
                    dist < centreR + dp(3f) -> 4
                    dist < rimInner -> {
                        val a = angleOf(e.x, e.y)
                        var best = 0; var bestD = 999f
                        for (i in 0 until 3) {
                            if (eraserMode && i != 0) continue
                            val m = arcs[i][0] + arcs[i][1] / 2
                            var dd = kotlin.math.abs(a - m); if (dd > 180) dd = 360 - dd
                            if (dd < bestD) { bestD = dd; best = i }
                        }
                        best + 1
                    }
                    else -> { downSlot = if (compact) -1 else slotAt(e.x, e.y); 5 }
                }
                if (mode in 1..3) apply(e.x, e.y)
                if (mode == 4 || mode == 5) postDelayed(longPress, 480)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2) {
                    removeCallbacks(longPress)
                    mode = 7; pinchS0 = scaleFactor
                    pinchD0 = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0)).coerceAtLeast(1f)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(e.rawX - downRawX, e.rawY - downRawY) > UI.dpf(context, 8f)) moved = true
                when (mode) {
                    in 1..3 -> apply(e.x, e.y)
                    4, 5 -> if (moved && !longFired) { removeCallbacks(longPress); mode = 6 }
                    6 -> { onDrag?.invoke(e.rawX - downRawX, e.rawY - downRawY); downRawX = e.rawX; downRawY = e.rawY }
                    7 -> if (e.pointerCount >= 2) {
                        val d = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
                        scaleFactor = pinchS0 * d / pinchD0
                        onScaleChanged?.invoke(scaleFactor)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (!longFired && !moved) {
                    if (mode == 4) onCenterTap?.invoke()
                    if (mode == 5 && downSlot >= 0) onSlotTap?.invoke(downSlot)
                }
                if (mode == 6) onDragEnd?.invoke()
                mode = 0; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); if (mode == 6) onDragEnd?.invoke(); mode = 0; invalidate() }
        }
        return true
    }

    private fun apply(x: Float, y: Float) {
        val i = mode - 1
        val v = arcValue(i, angleOf(x, y))
        when (i) {
            0 -> { sizeT = v; onSize?.invoke(v) }
            1 -> { opacity = v.coerceAtLeast(0.02f); onOpacity?.invoke(opacity) }
            2 -> { smoothing = v; onSmoothing?.invoke(v) }
        }
        invalidate()
    }

    @Suppress("unused") private fun unused() = min(1, 2)
}
