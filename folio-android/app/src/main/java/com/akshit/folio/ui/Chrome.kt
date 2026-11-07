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

    init {
        orientation = VERTICAL
        background = UI.card(ctx)
        elevation = UI.dpf(ctx, 6f)
        setPadding(UI.dp(ctx, 6f), UI.dp(ctx, 8f), UI.dp(ctx, 6f), UI.dp(ctx, 6f))
        addView(UI.label(ctx, "Layers", 11f, UI.SUB, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.1f; setOnClickListener { onOpenPanel() } },
            LayoutParams(UI.MATCH, UI.WRAP))
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
