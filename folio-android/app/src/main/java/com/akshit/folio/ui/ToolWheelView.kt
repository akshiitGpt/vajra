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
import kotlin.math.sin

/**
 * The Tool Wheel, laid out like a classic sketching wheel:
 *
 *  - outer rim: 8 tool slots spread over ~280°, each showing a sample mark in the tool's
 *    colour and its size; the active slot pops out in black.
 *  - left gap of the rim: undo (upper) and redo (lower).
 *  - grey inner disk: size readout on top, the colour dot in the middle, smoothing and
 *    opacity readouts at the bottom; thin coloured arcs echo each slot's colour.
 *  - tap a readout to open its slider popup, tap the colour dot for the colour fan,
 *    drag the wheel to move it, pinch to resize, drop it on an edge to dock as a bar.
 */
@SuppressLint("ViewConstructor")
class ToolWheelView(ctx: Context) : View(ctx) {
    enum class Kind { BRUSH, MARKER, SOFT, ERASER, SELECT, EYEDROPPER, PAN }
    class Slot(val icon: String, val label: String, val color: Int?, val sizeText: String, val kind: Kind = Kind.BRUSH, val markWidth: Float = 2f, val markAlpha: Float = 1f)

    var slots: List<Slot> = emptyList(); set(v) { field = v; invalidate() }
    var activeSlot = 0; set(v) { field = v; invalidate() }
    var color = Color.BLACK; set(v) { field = v; invalidate() }
    var sizeText = "2.5 pts"; set(v) { field = v; invalidate() }
    var opacityText = "100%"; set(v) { field = v; invalidate() }
    var smoothingText = "10%"; set(v) { field = v; invalidate() }
    var canUndo = true; set(v) { field = v; invalidate() }
    var canRedo = false; set(v) { field = v; invalidate() }
    var eraserMode = false; set(v) { field = v; invalidate() }
    /** Compact = disk only (used at the top of the docked Tool Bar). */
    var compact = false; set(v) { field = v; requestLayout(); invalidate() }
    var scaleFactor = 1f; set(v) { field = v.coerceIn(if (compact) 0.6f else 0.75f, 1.45f); requestLayout(); invalidate() }

    var onCenterTap: (() -> Unit)? = null
    var onSizeTap: (() -> Unit)? = null
    var onOpacityTap: (() -> Unit)? = null
    var onSmoothingTap: (() -> Unit)? = null
    var onSlotTap: ((Int) -> Unit)? = null
    var onSlotLongPress: ((Int) -> Unit)? = null
    var onUndo: (() -> Unit)? = null
    var onRedo: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null

    // legacy hooks kept so older call sites compile
    var onCenterLongPress: (() -> Unit)? = null
    var onSize: ((Float) -> Unit)? = null
    var onOpacity: ((Float) -> Unit)? = null
    var onSmoothing: ((Float) -> Unit)? = null
    var sizeLabel: (Float) -> String = { "" }
    var sizeT = 0f; var opacity = 1f; var smoothing = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF(); private val oval2 = RectF()
    private val wedge = Path(); private val mark = Path()
    private val icons = HashMap<String, IconDrawable>()

    private fun dp(v: Float) = UI.dpf(context, v) * scaleFactor
    private val R get() = dp(80f)          // rim outer radius
    private val RA get() = dp(87f)         // active slot pops out to here
    private val ri get() = dp(50f)         // rim inner radius == disk radius
    private val dotR get() = dp(17f)

    companion object {
        const val SLOT_SWEEP = 35f
        const val FIRST_CENTRE = -122.5f   // degrees, 0 = 3 o'clock, clockwise
    }

    fun centreX() = width / 2f
    fun centreY() = height / 2f
    fun diskRadius() = ri

    override fun onMeasure(w: Int, h: Int) {
        val s = if (compact) (ri * 2 + dp(8f)).toInt() else (RA * 2 + dp(6f)).toInt()
        setMeasuredDimension(s, s)
    }

    private fun slotCentre(i: Int) = FIRST_CENTRE + i * SLOT_SWEEP

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        if (!compact) drawRim(c, cx, cy)
        drawDisk(c, cx, cy)
    }

    private fun drawRim(c: Canvas, cx: Float, cy: Float) {
        oval.set(cx - R, cy - R, cx + R, cy + R)
        oval2.set(cx - ri, cy - ri, cx + ri, cy + ri)
        val start = FIRST_CENTRE - SLOT_SWEEP / 2
        val total = SLOT_SWEEP * slots.size.coerceAtLeast(8)
        // rim body
        wedge.reset()
        wedge.arcTo(oval, start, total, true)
        wedge.arcTo(oval2, start + total, -total)
        wedge.close()
        fill.color = 0xF2E9E9E9.toInt()
        fill.setShadowLayer(dp(4f), 0f, dp(1f), 0x22000000)
        c.drawPath(wedge, fill)
        fill.clearShadowLayer()
        // slots
        for (i in slots.indices) {
            val a0 = slotCentre(i) - SLOT_SWEEP / 2
            val active = i == activeSlot
            if (active) {
                val ro = RA
                oval.set(cx - ro, cy - ro, cx + ro, cy + ro)
                wedge.reset()
                wedge.arcTo(oval, a0, SLOT_SWEEP, true)
                wedge.arcTo(oval2, a0 + SLOT_SWEEP, -SLOT_SWEEP)
                wedge.close()
                fill.color = 0xFF111111.toInt()
                fill.setShadowLayer(dp(3f), 0f, dp(1f), 0x44000000)
                c.drawPath(wedge, fill)
                fill.clearShadowLayer()
                oval.set(cx - R, cy - R, cx + R, cy + R)
            }
            drawSlotContent(c, cx, cy, i, active)
            // separator
            if (i > 0) {
                val a = Math.toRadians(a0.toDouble())
                stroke.color = Color.WHITE; stroke.strokeWidth = dp(1.4f)
                c.drawLine(cx + (ri * cos(a)).toFloat(), cy + (ri * sin(a)).toFloat(), cx + (R * cos(a)).toFloat(), cy + (R * sin(a)).toFloat(), stroke)
            }
        }
        // undo / redo in the gap
        drawArrow(c, cx, cy, 197f, true, canUndo)
        drawArrow(c, cx, cy, 163f, false, canRedo)
    }

    private fun drawArrow(c: Canvas, cx: Float, cy: Float, angDeg: Float, undo: Boolean, enabled: Boolean) {
        val a = Math.toRadians(angDeg.toDouble())
        val r = (R + ri) / 2 + dp(4f)
        val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
        val d = icons.getOrPut(if (undo) "undo_solid" else "redo_solid") { IconDrawable(if (undo) "undo_solid" else "redo_solid", UI.ICON, 2.6f) }
        d.color = if (enabled) 0xFF2A2A2A.toInt() else 0x55000000
        val h = dp(12f)
        d.setBounds((x - h).toInt(), (y - h).toInt(), (x + h).toInt(), (y + h).toInt())
        d.draw(c)
    }

    private fun drawSlotContent(c: Canvas, cx: Float, cy: Float, i: Int, active: Boolean) {
        val s = slots[i]
        val ang = slotCentre(i)
        val r = (ri + (if (active) RA else R)) / 2
        val a = Math.toRadians(ang.toDouble())
        c.save()
        c.translate(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat())
        c.rotate(ang + 90f) // "up" points away from the centre
        val ink = if (active) Color.WHITE else 0xFF555555.toInt()
        // size number (outer)
        text.textSize = dp(9.5f); text.color = if (active) Color.WHITE else 0xFF4A4A4A.toInt(); text.isFakeBoldText = false
        if (s.sizeText.isNotEmpty()) c.drawText(s.sizeText, 0f, -dp(6f), text)
        // sample mark (inner)
        when (s.kind) {
            Kind.BRUSH, Kind.MARKER, Kind.SOFT -> {
                val col = if (active) Color.WHITE else (s.color ?: ink)
                mark.reset()
                val w = dp(7f)
                mark.moveTo(-w, dp(8f)); mark.cubicTo(-w * 0.2f, -dp(2f), -w * 0.1f, dp(14f), w * 0.4f, dp(3f)); mark.lineTo(w, dp(1f))
                stroke.color = col
                stroke.alpha = (255 * s.markAlpha).toInt().coerceIn(60, 255)
                stroke.strokeWidth = (s.markWidth * scaleFactor).coerceIn(dp(1.2f), dp(6.5f))
                if (s.kind == Kind.SOFT) stroke.setShadowLayer(dp(2f), 0f, 0f, col) else stroke.clearShadowLayer()
                if (s.kind == Kind.MARKER) stroke.strokeCap = Paint.Cap.SQUARE
                c.drawPath(mark, stroke)
                stroke.clearShadowLayer(); stroke.strokeCap = Paint.Cap.ROUND; stroke.alpha = 255
            }
            else -> {
                val d = icons.getOrPut(s.icon) { IconDrawable(s.icon, ink, 1.8f) }
                d.color = ink
                val h = dp(8.5f)
                d.setBounds((-h).toInt(), (dp(5f) - h).toInt(), h.toInt(), (dp(5f) + h).toInt())
                d.draw(c)
            }
        }
        c.restore()
    }

    private fun drawDisk(c: Canvas, cx: Float, cy: Float) {
        fill.color = 0xF2C6C6C6.toInt()
        if (compact) fill.setShadowLayer(dp(4f), 0f, dp(1f), 0x33000000)
        c.drawCircle(cx, cy, ri, fill)
        fill.clearShadowLayer()
        // thin coloured arcs echoing each slot
        if (!compact) {
            oval.set(cx - ri + dp(1.5f), cy - ri + dp(1.5f), cx + ri - dp(1.5f), cy + ri - dp(1.5f))
            stroke.strokeWidth = dp(3f); stroke.strokeCap = Paint.Cap.BUTT
            for (i in slots.indices) {
                val col = slots[i].color ?: continue
                stroke.color = col
                c.drawArc(oval, slotCentre(i) - SLOT_SWEEP / 2 + 1f, SLOT_SWEEP - 2f, false, stroke)
            }
            stroke.strokeCap = Paint.Cap.ROUND
        }
        val dark = 0xFF2B2B2B.toInt()
        // size readout (top)
        text.color = dark; text.textSize = dp(10.5f); text.isFakeBoldText = false
        val tw = text.measureText(sizeText)
        stroke.color = dark; stroke.strokeWidth = dp(1.4f)
        val lx = cx - tw / 2 - dp(8f); val ly = cy - dp(27f)
        c.drawLine(lx - dp(5f), ly - dp(2.5f), lx + dp(4f), ly - dp(2.5f), stroke)
        c.drawLine(lx - dp(5f), ly + dp(1f), lx + dp(4f), ly + dp(1f), stroke)
        c.drawText(sizeText, cx + dp(4f), cy - dp(23.5f), text)
        // colour dot
        fill.color = if (eraserMode) 0xFFF4F4F4.toInt() else color or (0xFF shl 24)
        c.drawCircle(cx, cy, dotR, fill)
        if (ColorUtil.luminance(color) > 0.85f || eraserMode) {
            stroke.color = 0x33000000; stroke.strokeWidth = dp(1f); c.drawCircle(cx, cy, dotR, stroke)
        }
        // smoothing (bottom-left) and opacity (bottom-right)
        stroke.color = dark; stroke.strokeWidth = dp(1.3f)
        val sx = cx - dp(24f); val sy = cy + dp(10f)
        mark.reset(); mark.moveTo(sx - dp(6f), sy + dp(1f))
        var k = 0f
        while (k < 1f) { mark.lineTo(sx - dp(6f) + k * dp(12f), sy + sin(k * 18f) * dp(2.5f) * (1 - k)); k += 0.08f }
        c.drawPath(mark, stroke)
        val ox = cx + dp(24f); val oy = cy + dp(10f)
        stroke.strokeWidth = dp(1.2f); c.drawCircle(ox, oy, dp(5f), stroke)
        oval.set(ox - dp(5f), oy - dp(5f), ox + dp(5f), oy + dp(5f))
        fill.color = dark; c.drawArc(oval, 90f, 180f, true, fill)
        text.textSize = dp(9.5f)
        c.drawText(smoothingText, cx - dp(17f), cy + dp(29f), text)
        c.drawText(opacityText, cx + dp(15f), cy + dp(29f), text)
    }

    // ------------------------------------------------------------------ touch
    private var mode = 0 // 1 size, 2 smoothing, 3 opacity, 4 colour, 5 slot, 6 drag, 7 pinch, 8 undo, 9 redo
    private var downRawX = 0f; private var downRawY = 0f; private var moved = false; private var downSlot = -1; private var longFired = false
    private var pinchD0 = 0f; private var pinchS0 = 1f
    private val longPress = Runnable {
        if (moved || mode != 5 || downSlot < 0) return@Runnable
        longFired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onSlotLongPress?.invoke(downSlot)
    }

    private fun angleOf(x: Float, y: Float): Float {
        var a = Math.toDegrees(atan2((y - height / 2f).toDouble(), (x - width / 2f).toDouble())).toFloat()
        if (a < -180f) a += 360f
        return a
    }

    private fun hit(x: Float, y: Float): Int {
        val cx = width / 2f; val cy = height / 2f
        val d = hypot(x - cx, y - cy)
        if (d <= ri) {
            val dx = x - cx; val dy = y - cy
            return when {
                d < dotR + dp(4f) -> 4
                dy < -dp(12f) -> 1
                dy > 0 && dx < 0 -> 2
                dy > 0 && dx >= 0 -> 3
                else -> 4
            }
        }
        if (compact || d > RA + dp(3f)) return 0
        var a = angleOf(x, y)
        if (a > 140f || a < -140f) { // the gap: undo above, redo below
            return if (y < cy) 8 else 9
        }
        a = a.coerceIn(FIRST_CENTRE - SLOT_SWEEP / 2, FIRST_CENTRE - SLOT_SWEEP / 2 + SLOT_SWEEP * slots.size - 0.01f)
        downSlot = ((a - (FIRST_CENTRE - SLOT_SWEEP / 2)) / SLOT_SWEEP).toInt().coerceIn(0, slots.size - 1)
        return 5
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = hit(e.x, e.y)
                if (mode == 0) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                downRawX = e.rawX; downRawY = e.rawY; moved = false; longFired = false
                if (mode == 5) postDelayed(longPress, 480)
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2 && !compact) {
                removeCallbacks(longPress)
                mode = 7; pinchS0 = scaleFactor
                pinchD0 = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0)).coerceAtLeast(1f)
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(e.rawX - downRawX, e.rawY - downRawY) > UI.dpf(context, 9f)) moved = true
                when (mode) {
                    7 -> if (e.pointerCount >= 2) {
                        val d = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
                        scaleFactor = pinchS0 * d / pinchD0
                        onScaleChanged?.invoke(scaleFactor)
                    }
                    6 -> { onDrag?.invoke(e.rawX - downRawX, e.rawY - downRawY); downRawX = e.rawX; downRawY = e.rawY }
                    0 -> {}
                    else -> if (moved && !longFired && !compact) { removeCallbacks(longPress); mode = 6 }
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (!moved && !longFired) when (mode) {
                    1 -> onSizeTap?.invoke()
                    2 -> onSmoothingTap?.invoke()
                    3 -> onOpacityTap?.invoke()
                    4 -> onCenterTap?.invoke()
                    5 -> if (downSlot >= 0) onSlotTap?.invoke(downSlot)
                    8 -> { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onUndo?.invoke() }
                    9 -> { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onRedo?.invoke() }
                }
                if (mode == 6) onDragEnd?.invoke()
                mode = 0
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); if (mode == 6) onDragEnd?.invoke(); mode = 0 }
        }
        return true
    }
}
