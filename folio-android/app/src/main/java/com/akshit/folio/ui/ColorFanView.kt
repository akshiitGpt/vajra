package com.akshit.folio.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Full-screen colour picker that fans out around the tool wheel:
 *  - Palette: rings of greys plus hue families as radial columns of swatches (dark → pale)
 *  - HSL / RGB: concentric arc sliders
 * Mode labels and an eyedropper sit on an arc right next to the wheel.
 */
@SuppressLint("ViewConstructor")
class ColorFanView(
    ctx: Context, var cx: Float, var cy: Float, initial: Int,
    private val onPick: (Int) -> Unit, private val onEyedropper: () -> Unit, private val onClose: () -> Unit
) : View(ctx) {
    class Cell(val a0: Float, val sweep: Float, val r0: Float, val r1: Float, val color: Int, val label: String)

    var mode = 0 // 0 palette, 1 HSL, 2 RGB
    private var color = initial or (0xFF shl 24)
    private val cells = ArrayList<Cell>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val o1 = RectF(); private val o2 = RectF()
    private val eye = IconDrawable("eyedropper", UI.ICON, 1.9f)
    private fun dp(v: Float) = UI.dpf(context, v)

    // label positions (angle deg, radius dp)
    private val labels = listOf("Palette" to 2f, "HSL" to 31f, "RGB" to 60f)
    private val labelR get() = dp(146f)
    private val eyeAngle = 88f

    init { buildPalette() }

    private fun hsl(h: Float, s: Float, l: Float): Int {
        val c = (1 - abs(2 * l - 1)) * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = l - c / 2
        return Color.rgb(((r1 + m) * 255).toInt().coerceIn(0, 255), ((g1 + m) * 255).toInt().coerceIn(0, 255), ((b1 + m) * 255).toInt().coerceIn(0, 255))
    }

    private fun buildPalette() {
        cells.clear()
        // neutrals ring
        val neutrals = listOf(Color.BLACK to "black", Color.rgb(64, 64, 64) to "N8", Color.rgb(110, 110, 110) to "N6", Color.rgb(160, 160, 160) to "N4", Color.rgb(205, 205, 205) to "N2", Color.WHITE to "white")
        val nA = 95f; val nS = 80f / neutrals.size
        neutrals.forEachIndexed { i, (c, l) -> cells.add(Cell(nA + i * nS, nS, dp(166f), dp(196f), c, l)) }
        // warm greys (top) and cool greys (right/bottom)
        for (i in 0..10) {
            val l = 0.95f - i * 0.075f
            cells.add(Cell(-100f + i * 7.3f, 7.3f, dp(200f), dp(228f), hsl(30f, 0.07f, l), "W$i"))
            cells.add(Cell(30f + i * 13.5f, 13.5f, dp(200f), dp(228f), hsl(210f, 0.10f, l), "C$i"))
        }
        // hue families: radial columns, dark (inner) → pale (outer)
        val fam = listOf("V" to 275f, "RV" to 322f, "R" to 356f, "YR" to 22f, "Y" to 50f, "YG" to 82f, "G" to 135f, "BG" to 172f, "B" to 205f, "BV" to 238f, "E" to 26f)
        val famSweep = 25.5f
        var a = -98f
        for ((name, hue) in fam) {
            val earth = name == "E"
            for (j in 0..1) {
                val sat = if (earth) (if (j == 0) 0.42f else 0.24f) else (if (j == 0) 0.82f else 0.48f)
                for (k in 0..5) {
                    val l = 0.30f + k * 0.112f
                    val r0 = dp(236f) + k * dp(36f)
                    cells.add(Cell(a + j * famSweep / 2, famSweep / 2, r0, r0 + dp(35f), hsl(hue, sat, l), "$name${j}${5 - k}"))
                }
            }
            a += famSweep
        }
    }

    override fun onDraw(c: Canvas) {
        // gentle veil so the fan reads above the drawing
        c.drawColor(0x26FFFFFF)
        when (mode) {
            0 -> for (cell in cells) drawCell(c, cell)
            1 -> drawHsl(c)
            else -> drawRgb(c)
        }
        // mode labels + eyedropper
        for ((i, pair) in labels.withIndex()) {
            val (label, ang) = pair
            val r = labelR
            val x = cx + (r * cos(Math.toRadians(ang.toDouble()))).toFloat()
            val y = cy + (r * sin(Math.toRadians(ang.toDouble()))).toFloat()
            text.textSize = dp(13f); text.isFakeBoldText = false; text.color = 0xFF222222.toInt()
            val w = text.measureText(label) + dp(16f)
            if (i == mode) { fill.color = 0xFFC9C9C9.toInt(); c.drawRoundRect(x - w / 2, y - dp(14f), x + w / 2, y + dp(12f), dp(5f), dp(5f), fill) }
            c.drawText(label, x, y + dp(4.5f), text)
        }
        val ex = cx + (labelR * cos(Math.toRadians(eyeAngle.toDouble()))).toFloat()
        val ey = cy + (labelR * sin(Math.toRadians(eyeAngle.toDouble()))).toFloat()
        fill.color = 0xFFEDEDED.toInt(); c.drawCircle(ex, ey, dp(17f), fill)
        eye.setBounds((ex - dp(10f)).toInt(), (ey - dp(10f)).toInt(), (ex + dp(10f)).toInt(), (ey + dp(10f)).toInt()); eye.draw(c)
    }

    private fun wedge(a0: Float, sweep: Float, r0: Float, r1: Float) {
        o1.set(cx - r1, cy - r1, cx + r1, cy + r1); o2.set(cx - r0, cy - r0, cx + r0, cy + r0)
        path.reset(); path.arcTo(o1, a0, sweep, true); path.arcTo(o2, a0 + sweep, -sweep); path.close()
    }

    private fun drawCell(c: Canvas, cell: Cell) {
        wedge(cell.a0, cell.sweep, cell.r0, cell.r1)
        fill.color = cell.color; c.drawPath(path, fill)
        line.color = 0x30FFFFFF; line.strokeWidth = dp(0.8f); c.drawPath(path, line)
        if ((cell.color or (0xFF shl 24)) == color) { line.color = 0xFF111111.toInt(); line.strokeWidth = dp(2.5f); c.drawPath(path, line) }
        // label, rotated along the radius like a ruler tick
        val mid = cell.a0 + cell.sweep / 2
        val rm = (cell.r0 + cell.r1) / 2
        val x = cx + (rm * cos(Math.toRadians(mid.toDouble()))).toFloat()
        val y = cy + (rm * sin(Math.toRadians(mid.toDouble()))).toFloat()
        c.save(); c.translate(x, y)
        var rot = mid
        if (rot > 90f && rot < 270f) rot -= 180f
        c.rotate(rot)
        text.textSize = dp(8f); text.isFakeBoldText = false
        text.color = if (ColorUtil.luminance(cell.color) > 0.55f) 0xAA000000.toInt() else 0xDDFFFFFF.toInt()
        c.drawText(cell.label, 0f, dp(3f), text)
        c.restore()
    }

    // ---------------- HSL / RGB arc sliders
    private val A0 = -98f; private val SWEEP = 278f
    private fun bands() = listOf(dp(172f) to dp(206f), dp(214f) to dp(246f), dp(254f) to dp(286f))

    private fun drawBand(c: Canvas, idx: Int, colorAt: (Float) -> Int, value: Float, title: String) {
        val (r0, r1) = bands()[idx]
        val steps = 140
        for (i in 0 until steps) {
            val t = i / (steps - 1f)
            wedge(A0 + SWEEP * i / steps, SWEEP / steps + 0.6f, r0, r1)
            fill.color = colorAt(t); c.drawPath(path, fill)
        }
        // knob
        val ang = Math.toRadians((A0 + SWEEP * value).toDouble())
        val rm = (r0 + r1) / 2
        val kx = cx + (rm * cos(ang)).toFloat(); val ky = cy + (rm * sin(ang)).toFloat()
        fill.color = colorAt(value); c.drawCircle(kx, ky, (r1 - r0) / 2 + dp(2f), fill)
        line.color = Color.WHITE; line.strokeWidth = dp(3f); c.drawCircle(kx, ky, (r1 - r0) / 2 + dp(2f), line)
        line.color = 0x55000000; line.strokeWidth = dp(1f); c.drawCircle(kx, ky, (r1 - r0) / 2 + dp(3.5f), line)
        // title at the band start
        val ta = Math.toRadians((A0 - 4f).toDouble())
        text.textSize = dp(10f); text.color = 0xFF333333.toInt(); text.isFakeBoldText = true
        c.drawText(title, cx + (rm * cos(ta)).toFloat(), cy + (rm * sin(ta)).toFloat(), text)
    }

    private fun hslOf(c: Int): FloatArray {
        val r = Color.red(c) / 255f; val g = Color.green(c) / 255f; val b = Color.blue(c) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val l = (mx + mn) / 2
        if (mx == mn) return floatArrayOf(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2 - mx - mn) else d / (mx + mn)
        val h = when (mx) { r -> ((g - b) / d + (if (g < b) 6 else 0)); g -> ((b - r) / d + 2); else -> ((r - g) / d + 4) } * 60f
        return floatArrayOf(h, s, l)
    }

    private var hslCache = hslOf(color)

    private fun drawHsl(c: Canvas) {
        val (h, s, l) = hslCache.let { Triple(it[0], it[1], it[2]) }
        drawBand(c, 0, { t -> hsl(t * 360f, 0.85f, 0.5f) }, h / 360f, "H")
        drawBand(c, 1, { t -> hsl(h, t, l.coerceIn(0.15f, 0.85f)) }, s, "S")
        drawBand(c, 2, { t -> hsl(h, s, t) }, l, "L")
        drawPreview(c)
    }

    private fun drawRgb(c: Canvas) {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        drawBand(c, 0, { t -> Color.rgb((t * 255).toInt(), g, b) }, r / 255f, "R")
        drawBand(c, 1, { t -> Color.rgb(r, (t * 255).toInt(), b) }, g / 255f, "G")
        drawBand(c, 2, { t -> Color.rgb(r, g, (t * 255).toInt()) }, b / 255f, "B")
        drawPreview(c)
    }

    private fun drawPreview(c: Canvas) {
        val a = Math.toRadians(130.0); val r = dp(232f)
        val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
        fill.color = color; c.drawCircle(x, y, dp(22f), fill)
        line.color = Color.WHITE; line.strokeWidth = dp(3f); c.drawCircle(x, y, dp(22f), line)
        text.textSize = dp(11f); text.color = 0xFF333333.toInt(); text.isFakeBoldText = true
        c.drawText(ColorUtil.hex(color), x, y + dp(40f), text)
    }

    // ---------------- touch
    private var dragBand = -1

    private fun angle(x: Float, y: Float): Float {
        var a = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
        if (a < -100f) a += 360f
        return a
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        val d = hypot(x - cx, y - cy)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragBand = -1
                // labels / eyedropper
                for ((i, pair) in labels.withIndex()) {
                    val a = Math.toRadians(pair.second.toDouble())
                    if (hypot(x - (cx + labelR * cos(a)).toFloat(), y - (cy + labelR * sin(a)).toFloat()) < dp(30f)) { mode = i; invalidate(); return true }
                }
                val ea = Math.toRadians(eyeAngle.toDouble())
                if (hypot(x - (cx + labelR * cos(ea)).toFloat(), y - (cy + labelR * sin(ea)).toFloat()) < dp(22f)) { onEyedropper(); return true }
                if (mode != 0) {
                    bands().forEachIndexed { i, (r0, r1) -> if (d in (r0 - dp(6f))..(r1 + dp(6f))) dragBand = i }
                    if (dragBand >= 0) { applyBand(x, y); return true }
                } else {
                    val a = angle(x, y)
                    val hit = cells.firstOrNull { d >= it.r0 && d <= it.r1 && ((a - it.a0 + 360f) % 360f) <= it.sweep }
                    if (hit != null) { color = hit.color; hslCache = hslOf(color); onPick(color); invalidate(); return true }
                }
                // anywhere else closes the picker (but not on the wheel itself, which sits under us)
                if (d > dp(90f)) onClose() else return false
            }
            MotionEvent.ACTION_MOVE -> if (dragBand >= 0) applyBand(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragBand = -1
        }
        return true
    }

    private fun applyBand(x: Float, y: Float) {
        val t = ((angle(x, y) - A0) / SWEEP).coerceIn(0f, 1f)
        if (mode == 1) {
            val h = hslCache.copyOf()
            when (dragBand) { 0 -> h[0] = t * 360f; 1 -> h[1] = t; 2 -> h[2] = t }
            hslCache = h
            color = hsl(h[0], h[1], h[2])
        } else {
            val v = (t * 255).toInt()
            color = when (dragBand) {
                0 -> Color.rgb(v, Color.green(color), Color.blue(color))
                1 -> Color.rgb(Color.red(color), v, Color.blue(color))
                else -> Color.rgb(Color.red(color), Color.green(color), v)
            }
        }
        onPick(color)
        invalidate()
    }
}
