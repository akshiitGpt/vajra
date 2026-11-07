package com.akshit.folio.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** HSV helpers */
object ColorUtil {
    fun hsv(c: Int): FloatArray = FloatArray(3).also { Color.colorToHSV(c, it) }
    fun fromHsv(h: Float, s: Float, v: Float, a: Int = 255): Int = Color.HSVToColor(a, floatArrayOf(h, s, v))
    fun hex(c: Int, withAlpha: Boolean = false): String =
        if (withAlpha && Color.alpha(c) != 255) "#%08X".format(c) else "#%06X".format(c and 0xFFFFFF)
    fun parse(s: String): Int? = try { Color.parseColor(if (s.startsWith("#")) s else "#$s") } catch (e: Exception) { null }
    fun luminance(c: Int): Float = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
}

/** Hue ring + saturation/value square. */
@SuppressLint("ViewConstructor")
class ColorWheelView(ctx: Context, var onChange: (Int) -> Unit) : View(ctx) {
    var hue = 0f; var sat = 1f; var value = 1f; var alpha = 255
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sqPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val knobFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dragging = 0 // 1 ring, 2 square
    private val sq = RectF()

    fun setColor(c: Int) {
        val h = ColorUtil.hsv(c); hue = h[0]; sat = h[1]; value = h[2]; alpha = Color.alpha(c); invalidate()
    }

    fun color(): Int = ColorUtil.fromHsv(hue, sat, value, alpha)

    override fun onMeasure(w: Int, h: Int) {
        val size = MeasureSpec.getSize(w)
        setMeasuredDimension(size, min(size, UI.dp(context, 300f)))
    }

    private fun geom(): FloatArray {
        val cx = width / 2f; val cy = height / 2f
        val outer = min(width, height) / 2f - UI.dpf(context, 6f)
        val thick = UI.dpf(context, 22f)
        val inner = outer - thick
        return floatArrayOf(cx, cy, outer, inner, thick)
    }

    override fun onDraw(c: Canvas) {
        val (cx, cy, outer, inner, thick) = geom().let { arrayOf(it[0], it[1], it[2], it[3], it[4]) }
        ringPaint.strokeWidth = thick
        ringPaint.shader = SweepGradient(cx, cy, intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED), null)
        c.drawCircle(cx, cy, (outer + inner) / 2f, ringPaint)
        // square inscribed in inner circle
        val half = (inner - UI.dpf(context, 10f)) / 1.4142f
        sq.set(cx - half, cy - half, cx + half, cy + half)
        val pure = ColorUtil.fromHsv(hue, 1f, 1f)
        val satShader = LinearGradient(sq.left, 0f, sq.right, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        val valShader = LinearGradient(0f, sq.top, 0f, sq.bottom, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
        sqPaint.shader = ComposeShader(valShader, satShader, PorterDuff.Mode.MULTIPLY)
        c.drawRoundRect(sq, UI.dpf(context, 6f), UI.dpf(context, 6f), sqPaint)
        // hue knob
        val a = Math.toRadians(hue.toDouble())
        val rr = (outer + inner) / 2f
        val hx = cx + (rr * cos(a)).toFloat(); val hy = cy + (rr * sin(a)).toFloat()
        knobFill.color = pure
        c.drawCircle(hx, hy, thick * 0.42f, knobFill)
        knob.color = Color.WHITE; knob.strokeWidth = UI.dpf(context, 3f)
        c.drawCircle(hx, hy, thick * 0.42f, knob)
        // sv knob
        val sx = sq.left + sat * sq.width(); val sy = sq.top + (1 - value) * sq.height()
        knobFill.color = color() or (0xFF shl 24)
        c.drawCircle(sx, sy, UI.dpf(context, 10f), knobFill)
        knob.color = if (value > 0.6f && sat < 0.4f) Color.DKGRAY else Color.WHITE
        c.drawCircle(sx, sy, UI.dpf(context, 10f), knob)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = geom()
        val cx = g[0]; val cy = g[1]; val inner = g[3]
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val d = hypot(e.x - cx, e.y - cy)
                dragging = if (d > inner - UI.dpf(context, 4f)) 1 else 2
                update(e.x, e.y, cx, cy)
            }
            MotionEvent.ACTION_MOVE -> update(e.x, e.y, cx, cy)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = 0
        }
        return true
    }

    private fun update(x: Float, y: Float, cx: Float, cy: Float) {
        if (dragging == 1) {
            var a = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
            if (a < 0) a += 360f
            hue = a
        } else if (dragging == 2) {
            sat = ((x - sq.left) / sq.width()).coerceIn(0f, 1f)
            value = (1f - (y - sq.top) / sq.height()).coerceIn(0f, 1f)
        }
        invalidate()
        onChange(color())
    }
}

/** Slider whose track shows a live gradient (for H/S/V, R/G/B, alpha). */
@SuppressLint("ViewConstructor")
class GradientSlider(ctx: Context, val title: String, private val colorsFor: () -> IntArray, var value: Float, val onChange: (Float) -> Unit) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; setShadowLayer(3f, 0f, 1f, 0x55000000) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UI.SUB }
    private val checker: BitmapShader by lazy {
        val n = UI.dp(ctx, 6f)
        val b = Bitmap.createBitmap(n * 2, n * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(0xFFDDDDDD.toInt())
        val p = Paint().apply { color = 0xFF999999.toInt() }
        c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p); c.drawRect(n.toFloat(), n.toFloat(), 2f * n, 2f * n, p)
        BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val checkerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), UI.dp(context, 46f))

    override fun onDraw(c: Canvas) {
        text.textSize = UI.dpf(context, 11f)
        c.drawText(title, UI.dpf(context, 2f), UI.dpf(context, 12f), text)
        val l = UI.dpf(context, 12f); val r = width - l
        val top = UI.dpf(context, 20f); val bot = height - UI.dpf(context, 6f)
        val rad = (bot - top) / 2
        checkerPaint.shader = checker
        c.drawRoundRect(l, top, r, bot, rad, rad, checkerPaint)
        track.shader = LinearGradient(l, 0f, r, 0f, colorsFor(), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(l, top, r, bot, rad, rad, track)
        val x = l + value * (r - l)
        knob.strokeWidth = UI.dpf(context, 3f)
        c.drawCircle(x, (top + bot) / 2, rad + UI.dpf(context, 2f), knob)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        val l = UI.dpf(context, 12f); val r = width - l
        value = ((e.x - l) / (r - l)).coerceIn(0f, 1f)
        onChange(value)
        invalidate()
        return true
    }
}

/** A grid of color swatches. */
@SuppressLint("ViewConstructor")
class SwatchGrid(ctx: Context, var colors: List<Int>, private val onPick: (Int) -> Unit, private val onLong: ((Int) -> Unit)? = null) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x22000000 }
    private val cell get() = UI.dpf(context, 34f)
    private val gap get() = UI.dpf(context, 6f)
    private var downIdx = -1
    private var downTime = 0L
    private val longRunnable = Runnable { if (downIdx >= 0) { onLong?.invoke(downIdx); downIdx = -1 } }

    private fun cols(w: Int) = maxOf(1, ((w + gap) / (cell + gap)).toInt())

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        val rows = (colors.size + cols(width) - 1) / cols(width)
        setMeasuredDimension(width, (rows * (cell + gap)).toInt().coerceAtLeast(1))
    }

    override fun onDraw(c: Canvas) {
        val n = cols(width)
        val r = UI.dpf(context, 7f)
        colors.forEachIndexed { i, col ->
            val x = (i % n) * (cell + gap); val y = (i / n) * (cell + gap)
            p.color = col
            c.drawRoundRect(x, y, x + cell, y + cell, r, r, p)
            c.drawRoundRect(x, y, x + cell, y + cell, r, r, ring)
        }
    }

    fun update(list: List<Int>) { colors = list; requestLayout(); invalidate() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val n = cols(width)
        val idx = (e.y / (cell + gap)).toInt() * n + (e.x / (cell + gap)).toInt()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downIdx = if (idx in colors.indices) idx else -1; downTime = e.eventTime; if (onLong != null) postDelayed(longRunnable, 550) }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longRunnable)
                if (downIdx >= 0 && idx == downIdx) onPick(colors[idx])
                downIdx = -1
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longRunnable); downIdx = -1 }
        }
        return true
    }
}
