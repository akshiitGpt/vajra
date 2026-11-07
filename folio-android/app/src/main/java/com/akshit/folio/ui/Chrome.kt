package com.akshit.folio.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.akshit.folio.core.Camera
import com.akshit.folio.render.SceneRenderer
import com.akshit.folio.view.CanvasView

/** One brush slot in the strip under the tool wheel: icon, size readout and color dot. */
@SuppressLint("ViewConstructor")
class SlotView(ctx: Context, icon: String) : View(ctx) {
    private val d = IconDrawable(icon, UI.ICON, 1.6f)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = UI.SUB }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33000000 }
    var sizeText: String = ""; set(v) { field = v; invalidate() }
    var dotColor: Int? = null; set(v) { field = v; invalidate() }

    init {
        isClickable = true; isLongClickable = true
        background = UI.ripple(ctx, UI.rounded(Color.TRANSPARENT, UI.dpf(ctx, 12f)))
        contentDescription = icon
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(UI.dp(context, 58f), UI.dp(context, 58f))
    override fun setSelected(selected: Boolean) { super.setSelected(selected); invalidate() }

    override fun onDraw(c: Canvas) {
        val dp = UI.dpf(context, 1f)
        if (isSelected) {
            bg.color = 0x1F2F7CF6
            c.drawRoundRect(3 * dp, 3 * dp, width - 3 * dp, height - 3 * dp, 12 * dp, 12 * dp, bg)
        }
        d.color = if (isSelected) UI.ACCENT else UI.ICON
        val s = (24 * dp).toInt()
        val l = (width - s) / 2; val t = (8 * dp).toInt()
        d.setBounds(l, t, l + s, t + s); d.draw(c)
        text.textSize = 10.5f * dp
        text.color = if (isSelected) UI.ACCENT else UI.SUB
        text.isFakeBoldText = isSelected
        c.drawText(sizeText, width / 2f, height - 9 * dp, text)
        dotColor?.let {
            dot.color = it
            val dx = width / 2f + 16 * dp; val dy = 10 * dp
            c.drawCircle(dx, dy, 4.5f * dp, dot)
            c.drawCircle(dx, dy, 4.5f * dp, ring)
        }
    }
}

/**
 * Docked layers column on the right edge (Concepts-style): live thumbnails, tap to switch layer,
 * long-press for the full layer panel, + to add.
 */
@SuppressLint("ViewConstructor")
class LayerSidebar(ctx: Context, private val canvas: CanvasView, private val onOpenPanel: () -> Unit, private val onAdd: () -> Unit) : LinearLayout(ctx) {
    private val list = UI.vbox(ctx)
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    private var lx = 0f; private var ly = 0f; private var dragging = false

    init {
        orientation = VERTICAL
        background = UI.card(ctx)
        elevation = UI.dpf(ctx, 6f)
        setPadding(UI.dp(ctx, 6f), UI.dp(ctx, 8f), UI.dp(ctx, 6f), UI.dp(ctx, 6f))
        val header = UI.label(ctx, "Layers  ⋮", 11.5f, UI.SUB, true).apply {
            gravity = Gravity.CENTER; setPadding(0, UI.dp(ctx, 4f), 0, UI.dp(ctx, 6f))
            // drag the header to move the menu, tap it for full layer options
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { lx = e.rawX; ly = e.rawY; dragging = false }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (!dragging && kotlin.math.hypot(e.rawX - lx, e.rawY - ly) > UI.dpf(ctx, 8f)) dragging = true
                        if (dragging) { onDrag?.invoke(e.rawX - lx, e.rawY - ly); lx = e.rawX; ly = e.rawY }
                    }
                    android.view.MotionEvent.ACTION_UP -> if (dragging) onDragEnd?.invoke() else onOpenPanel()
                }
                true
            }
        }
        addView(header, LayoutParams(UI.MATCH, UI.WRAP))
        addView(ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(list) }, LayoutParams(UI.MATCH, 0, 1f))
        addView(IconButton(ctx, "plus", 40f).apply { setOnClickListener { onAdd() } }, LayoutParams(UI.WRAP, UI.WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    fun refresh() {
        list.removeAllViews()
        val doc = canvas.doc
        val bounds = SceneRenderer.contentBounds(doc)
        val tw = UI.dp(context, 84f); val th = UI.dp(context, 62f)
        val cam = Camera().apply {
            viewW = tw.toFloat(); viewH = th.toFloat()
            if (bounds != null) {
                cx = (bounds[0] + bounds[2]) / 2; cy = (bounds[1] + bounds[3]) / 2
                scale = minOf(tw / (bounds[2] - bounds[0]).coerceAtLeast(1e-9), th / (bounds[3] - bounds[1]).coerceAtLeast(1e-9)) * 0.86
            } else scale = 0.1
            update()
        }
        for (i in doc.layers.indices.reversed()) {
            val layer = doc.layers[i]
            val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(doc.background.paperColor)
            if (layer.visible) SceneRenderer.drawLayers(c, cam, doc, i..i)
            val active = i == doc.activeLayer
            val item = object : View(context) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = UI.dpf(context, 9.5f) }
                override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(tw + UI.dp(context, 4f), th + UI.dp(context, 18f))
                override fun onDraw(cv: Canvas) {
                    val o = UI.dpf(context, 2f)
                    if (!layer.visible) p.alpha = 90 else p.alpha = 255
                    cv.drawBitmap(bmp, o, o, p)
                    border.color = if (active) UI.ACCENT else 0x22000000
                    border.strokeWidth = UI.dpf(context, if (active) 2.5f else 1f)
                    cv.drawRoundRect(o, o, o + tw, o + th, UI.dpf(context, 6f), UI.dpf(context, 6f), border)
                    text.color = if (active) UI.ACCENT else UI.SUB
                    text.isFakeBoldText = active
                    val label = (if (layer.locked) "🔒 " else "") + layer.name
                    cv.drawText(label, o + 2, height - UI.dpf(context, 4f), text)
                }
            }
            item.setOnClickListener { doc.activeLayer = i; canvas.clearSelection(); canvas.invalidateContent(); refresh() }
            item.setOnLongClickListener { onOpenPanel(); true }
            list.addView(item, LayoutParams(UI.WRAP, UI.WRAP).apply { bottomMargin = UI.dp(context, 4f); gravity = Gravity.CENTER_HORIZONTAL })
        }
    }
}

/**
 * Grey slider popup that opens beside the wheel (Size / Opacity / Smoothing):
 * header, a slider with tick marks at the presets, and preset chips.
 */
@SuppressLint("ViewConstructor")
class WheelPopup(
    ctx: Context, title: String, private val glyph: String,
    private val presets: List<Pair<String, Float>>, value: Float,
    private val format: (Float) -> String, private val onChange: (Float) -> Unit
) : LinearLayout(ctx) {
    private var t = value.coerceIn(0f, 1f)
    private val chips = ArrayList<android.widget.TextView>()
    private val valueLabel = UI.label(ctx, format(t), 12.5f, 0xFF333333.toInt())
    private val slider = object : View(ctx) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), UI.dp(context, 36f))
        override fun onDraw(c: Canvas) {
            val l = UI.dpf(context, 6f); val r = width - UI.dpf(context, 6f); val y = height / 2f
            p.color = 0xFF555555.toInt(); p.strokeWidth = UI.dpf(context, 1.2f)
            c.drawLine(l, y, r, y, p)
            p.color = 0xFF222222.toInt(); p.strokeWidth = UI.dpf(context, 2.4f)
            c.drawLine(l, y, l + (r - l) * t, y, p)
            p.strokeWidth = UI.dpf(context, 1.2f); p.color = 0xFF555555.toInt()
            for ((_, v) in presets) { val x = l + (r - l) * v; c.drawLine(x, y - UI.dpf(context, 7f), x, y + UI.dpf(context, 7f), p) }
            p.color = 0xFF111111.toInt(); c.drawCircle(l + (r - l) * t, y, UI.dpf(context, 5.5f), p)
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            val l = UI.dpf(context, 6f); val r = width - UI.dpf(context, 6f)
            set(((e.x - l) / (r - l)).coerceIn(0f, 1f))
            return true
        }
    }

    init {
        orientation = VERTICAL
        background = UI.rounded(0xF0CFCFCF.toInt(), UI.dpf(ctx, 12f))
        elevation = UI.dpf(ctx, 8f)
        isClickable = true
        setPadding(UI.dp(ctx, 16f), UI.dp(ctx, 12f), UI.dp(ctx, 16f), UI.dp(ctx, 12f))
        val head = UI.hbox(ctx)
        head.addView(UI.label(ctx, glyph, 15f, 0xFF333333.toInt(), true).apply { setPadding(0, 0, UI.dp(ctx, 10f), 0) })
        head.addView(UI.label(ctx, title, 14f, 0xFF333333.toInt()), UI.lp(0, UI.WRAP, 1f))
        head.addView(valueLabel)
        addView(head)
        addView(slider, LayoutParams(UI.MATCH, UI.WRAP).apply { topMargin = UI.dp(ctx, 4f) })
        val row = UI.hbox(ctx)
        for ((label, v) in presets) {
            val chip = UI.label(ctx, label, 13f, 0xFF333333.toInt()).apply {
                gravity = Gravity.CENTER; setPadding(UI.dp(ctx, 10f), UI.dp(ctx, 6f), UI.dp(ctx, 10f), UI.dp(ctx, 6f))
                setOnClickListener { set(v) }
            }
            chips.add(chip)
            row.addView(chip, UI.lp(0, UI.WRAP, 1f))
        }
        addView(row, LayoutParams(UI.MATCH, UI.WRAP).apply { topMargin = UI.dp(ctx, 2f) })
        paintChips()
    }

    private fun set(v: Float) {
        t = v; valueLabel.text = format(t); slider.invalidate(); paintChips(); onChange(t)
    }

    private fun paintChips() {
        chips.forEachIndexed { i, c ->
            val on = kotlin.math.abs(presets[i].second - t) < 0.004f
            c.background = if (on) UI.rounded(0xFFE6E6E6.toInt(), UI.dpf(context, 6f)) else null
        }
    }
}

/** Horizontally scrolling row of round choices with labels underneath (paper, grid, finger action...). */
@SuppressLint("ViewConstructor")
class CircleChoices(ctx: Context, private val items: List<Triple<String, (Canvas, Float, Float, Float) -> Unit, Any>>, selected: Any?, private val onSelect: (Any) -> Unit) : android.widget.HorizontalScrollView(ctx) {
    private var sel = selected
    private val row = UI.hbox(ctx)
    init {
        isHorizontalScrollBarEnabled = false
        addView(row)
        for ((label, painter, key) in items) {
            val col = UI.vbox(ctx).apply { gravity = Gravity.CENTER_HORIZONTAL }
            val circle = object : View(ctx) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onMeasure(w: Int, h: Int) { val s = UI.dp(context, 74f); setMeasuredDimension(s, s) }
                override fun onDraw(c: Canvas) {
                    val cx = width / 2f; val cy = height / 2f; val r = width / 2f - UI.dpf(context, 4f)
                    p.style = Paint.Style.FILL; p.color = Color.WHITE
                    p.setShadowLayer(UI.dpf(context, 3f), 0f, UI.dpf(context, 1f), 0x33000000)
                    c.drawCircle(cx, cy, r, p); p.clearShadowLayer()
                    c.save(); val clip = android.graphics.Path(); clip.addCircle(cx, cy, r, android.graphics.Path.Direction.CW); c.clipPath(clip)
                    painter(c, cx, cy, r); c.restore()
                    if (sel == key) { p.style = Paint.Style.STROKE; p.strokeWidth = UI.dpf(context, 2.5f); p.color = 0xFF111111.toInt(); c.drawCircle(cx, cy, r, p) }
                }
            }
            col.addView(circle)
            col.addView(UI.label(ctx, label, 13f, if (sel == key) UI.TEXT else UI.SUB).apply { gravity = Gravity.CENTER; setPadding(0, UI.dp(ctx, 6f), 0, 0) })
            col.setOnClickListener { sel = key; onSelect(key); refresh() }
            circle.setOnClickListener { col.performClick() }
            row.addView(col, LayoutParams(UI.dp(ctx, 96f), UI.WRAP))
        }
    }
    private fun refresh() {
        for (i in 0 until row.childCount) {
            val col = row.getChildAt(i) as LinearLayout
            col.getChildAt(0).invalidate()
            (col.getChildAt(1) as android.widget.TextView).setTextColor(if (sel == items[i].third) UI.TEXT else UI.SUB)
        }
    }
}
