package com.akshit.folio.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.akshit.folio.brush.BlendKind
import com.akshit.folio.brush.BrushSpec
import com.akshit.folio.brush.BrushType
import com.akshit.folio.brush.RenderKind
import com.akshit.folio.brush.StrokeRenderer
import com.akshit.folio.core.Camera
import com.akshit.folio.core.Prefs
import com.akshit.folio.model.BackgroundSpec
import com.akshit.folio.model.Document
import com.akshit.folio.model.Layer
import com.akshit.folio.model.LambdaCommand
import com.akshit.folio.model.Pattern
import com.akshit.folio.model.Stroke
import com.akshit.folio.render.BackgroundRenderer
import com.akshit.folio.view.CanvasView
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.sin

/** Floating card with a title bar and scrollable body. */
open class Panel(ctx: Context, title: String, onClose: () -> Unit) : LinearLayout(ctx) {
    val body: LinearLayout = UI.vbox(ctx, 0f)
    /** Panels hug their content but never grow past this height. */
    var maxHeightPx = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val avail = MeasureSpec.getSize(heightMeasureSpec).let { if (it == 0) maxHeightPx else it }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(minOf(maxHeightPx, avail), MeasureSpec.AT_MOST))
    }

    init {
        orientation = VERTICAL
        background = UI.card(ctx)
        elevation = UI.dpf(ctx, 12f)
        isClickable = true
        val head = UI.hbox(ctx)
        head.setPadding(UI.dp(ctx, 16f), UI.dp(ctx, 8f), UI.dp(ctx, 6f), UI.dp(ctx, 4f))
        head.addView(UI.label(ctx, title, 16f, UI.TEXT, true), UI.lp(0, UI.WRAP, 1f))
        head.addView(IconButton(ctx, "close", 40f).apply { setOnClickListener { onClose() } })
        addView(head)
        body.setPadding(UI.dp(ctx, 14f), 0, UI.dp(ctx, 14f), UI.dp(ctx, 14f))
        addView(UI.scroll(ctx, body), LayoutParams(UI.MATCH, UI.WRAP))
    }
}

// ============================================================================ color picker
@SuppressLint("ViewConstructor")
class ColorPickerPanel(
    ctx: Context, private val prefs: Prefs, initial: Int,
    private val onColor: (Int) -> Unit, onClose: () -> Unit
) : Panel(ctx, "Color", onClose) {
    private var color = initial
    private val original = initial
    private val preview = object : View(ctx) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), UI.dp(ctx, 40f))
        override fun onDraw(c: Canvas) {
            val r = UI.dpf(ctx, 10f)
            p.color = original; c.drawRoundRect(0f, 0f, width / 2f + r, height.toFloat(), r, r, p)
            p.color = color; c.drawRoundRect(width / 2f, 0f, width.toFloat(), height.toFloat(), r, r, p)
            c.drawRect(width / 2f, 0f, width / 2f + r, height.toFloat(), p)
        }
    }
    private val hexLabel = UI.label(ctx, ColorUtil.hex(initial), 14f, UI.TEXT, true)
    private val content = UI.vbox(ctx)
    private val wheel = ColorWheelView(ctx) { setColor(it, fromWheel = true) }
    private val sliders = ArrayList<GradientSlider>()
    private var tab = 0
    private val tabs = ArrayList<View>()

    init {
        body.addView(preview)
        preview.setOnClickListener { setColor(original) }
        val hexRow = UI.hbox(ctx)
        hexRow.setPadding(0, UI.dp(ctx, 8f), 0, UI.dp(ctx, 4f))
        hexRow.addView(hexLabel, UI.lp(0, UI.WRAP, 1f))
        hexRow.addView(UI.button(ctx, "Edit hex") { editHex() })
        body.addView(hexRow)
        val tabRow = UI.hbox(ctx)
        listOf("Wheel", "Sliders", "Palettes").forEachIndexed { i, name ->
            val t = UI.label(ctx, name, 13f, UI.TEXT, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, UI.dp(ctx, 8f), 0, UI.dp(ctx, 8f))
                setOnClickListener { showTab(i) }
            }
            tabs.add(t)
            tabRow.addView(t, UI.lp(0, UI.WRAP, 1f))
        }
        body.addView(tabRow)
        body.addView(content)
        wheel.setColor(initial)
        showTab(0)
    }

    private fun showTab(i: Int) {
        tab = i
        tabs.forEachIndexed { k, v -> v.background = if (k == i) UI.rounded(UI.PANEL2, UI.dpf(context, 8f)) else null }
        content.removeAllViews()
        sliders.clear()
        when (i) {
            0 -> { content.addView(wheel); wheel.setColor(color) }
            1 -> buildSliders()
            else -> buildPalettes()
        }
    }

    private fun buildSliders() {
        val ctx = context
        fun hsv() = ColorUtil.hsv(color)
        val hS = GradientSlider(ctx, "Hue", { IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) } }, hsv()[0] / 360f) { v ->
            val h = hsv(); setColor(ColorUtil.fromHsv(v * 360f, h[1], h[2]))
        }
        val sS = GradientSlider(ctx, "Saturation", { val h = hsv(); intArrayOf(ColorUtil.fromHsv(h[0], 0f, h[2]), ColorUtil.fromHsv(h[0], 1f, h[2])) }, hsv()[1]) { v ->
            val h = hsv(); setColor(ColorUtil.fromHsv(h[0], v, h[2]))
        }
        val vS = GradientSlider(ctx, "Brightness", { val h = hsv(); intArrayOf(Color.BLACK, ColorUtil.fromHsv(h[0], h[1], 1f)) }, hsv()[2]) { v ->
            val h = hsv(); setColor(ColorUtil.fromHsv(h[0], h[1], v))
        }
        val rS = GradientSlider(ctx, "Red", { intArrayOf(color and 0xFF00FFFF.toInt(), color or 0x00FF0000) }, Color.red(color) / 255f) { v ->
            setColor(Color.rgb((v * 255).toInt(), Color.green(color), Color.blue(color)))
        }
        val gS = GradientSlider(ctx, "Green", { intArrayOf(color and 0xFFFF00FF.toInt(), color or 0x0000FF00) }, Color.green(color) / 255f) { v ->
            setColor(Color.rgb(Color.red(color), (v * 255).toInt(), Color.blue(color)))
        }
        val bS = GradientSlider(ctx, "Blue", { intArrayOf(color and 0xFFFFFF00.toInt(), color or 0x000000FF) }, Color.blue(color) / 255f) { v ->
            setColor(Color.rgb(Color.red(color), Color.green(color), (v * 255).toInt()))
        }
        content.addView(UI.section(ctx, "HSB"))
        for (s in listOf(hS, sS, vS)) { content.addView(s); sliders.add(s) }
        content.addView(UI.section(ctx, "RGB"))
        for (s in listOf(rS, gS, bS)) { content.addView(s); sliders.add(s) }
    }

    private fun buildPalettes() {
        val ctx = context
        val recents = prefs.recentColors
        if (recents.isNotEmpty()) {
            content.addView(UI.section(ctx, "Recent"))
            content.addView(SwatchGrid(ctx, recents, { setColor(it) }))
        }
        content.addView(UI.section(ctx, "My palette  (long-press a swatch to remove)"))
        lateinit var custom: SwatchGrid
        custom = SwatchGrid(ctx, prefs.customPalette, { setColor(it) }) { idx ->
            val l = prefs.customPalette.toMutableList(); if (idx in l.indices) l.removeAt(idx); prefs.customPalette = l; custom.update(l)
        }
        content.addView(custom)
        content.addView(UI.button(ctx, "+ Add current color") {
            val l = prefs.customPalette.toMutableList(); l.add(color); prefs.customPalette = l; custom.update(l)
        }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = UI.dp(ctx, 6f) })
        for ((name, cols) in PALETTES) {
            content.addView(UI.section(ctx, name))
            content.addView(SwatchGrid(ctx, cols.map { it.toInt() }, { setColor(it) }))
        }
    }

    private fun editHex() {
        val et = EditText(context).apply { setText(ColorUtil.hex(color)); inputType = InputType.TYPE_CLASS_TEXT; setTextColor(UI.TEXT) }
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Hex color").setView(et)
            .setPositiveButton("OK") { _, _ -> ColorUtil.parse(et.text.toString().trim())?.let { setColor(it) } }
            .setNegativeButton("Cancel", null).show()
    }

    fun setColor(c: Int, fromWheel: Boolean = false) {
        color = c or (0xFF shl 24)
        hexLabel.text = ColorUtil.hex(color)
        preview.invalidate()
        if (!fromWheel && tab == 0) wheel.setColor(color)
        if (tab == 1) {
            val h = ColorUtil.hsv(color)
            val vals = floatArrayOf(h[0] / 360f, h[1], h[2], Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f)
            sliders.forEachIndexed { i, s -> s.value = vals[i]; s.invalidate() }
        }
        onColor(color)
    }

    fun commitRecent() {
        if (color == original) return
        val l = prefs.recentColors.toMutableList()
        l.remove(color); l.add(0, color)
        prefs.recentColors = l
    }

    companion object {
        val PALETTES = listOf(
            "Essentials" to longArrayOf(0xFF111111, 0xFF3C3C3C, 0xFF7A7A7A, 0xFFBDBDBD, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00897B, 0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41),
            "Inks" to longArrayOf(0xFF0B0B0D, 0xFF1A2238, 0xFF22324F, 0xFF0F3D3E, 0xFF3B1F2B, 0xFF5A1A1A, 0xFF2E2A24, 0xFF14213D, 0xFF283618, 0xFF3D405B),
            "Pastels" to longArrayOf(0xFFFFD1DC, 0xFFFFE5B4, 0xFFFFF5BA, 0xFFD4F0C0, 0xFFC1E7E3, 0xFFC6DEF1, 0xFFDBCDF0, 0xFFF2C6DE, 0xFFF7D9C4, 0xFFFAEDCB),
            "Earth" to longArrayOf(0xFF3E2723, 0xFF5D4037, 0xFF8D6E63, 0xFFA1887F, 0xFFD7CCC8, 0xFF827717, 0xFF558B2F, 0xFF33691E, 0xFFBF360C, 0xFFE65100, 0xFFC0A062, 0xFF7B6B43),
            "Skin tones" to longArrayOf(0xFFFFE0BD, 0xFFFFCD94, 0xFFEAC086, 0xFFFFAD60, 0xFFD2996C, 0xFFC68642, 0xFF8D5524, 0xFF6B4423, 0xFF4A2C17, 0xFF3B2219),
            "Neon" to longArrayOf(0xFFFF073A, 0xFFFF6EC7, 0xFFFFFF33, 0xFF39FF14, 0xFF0FF0FC, 0xFF1F51FF, 0xFFBC13FE, 0xFFFF5F1F),
            "Cool greys" to longArrayOf(0xFF0E1013, 0xFF1F2328, 0xFF353B43, 0xFF4E5661, 0xFF6B7480, 0xFF8B939E, 0xFFAAB1BA, 0xFFC9CED4, 0xFFE3E6EA, 0xFFF4F5F7),
            "Warm greys" to longArrayOf(0xFF161412, 0xFF2A2724, 0xFF423D38, 0xFF5D5750, 0xFF7B746B, 0xFF9A938A, 0xFFB8B2A9, 0xFFD3CEC7, 0xFFE8E4DE, 0xFFF7F4F0),
            "Markers" to longArrayOf(0xFFFFF176, 0xFFFFB74D, 0xFFFF8A80, 0xFFF48FB1, 0xFFCE93D8, 0xFF9FA8DA, 0xFF81D4FA, 0xFF80CBC4, 0xFFA5D6A7, 0xFFE6EE9C),
        )
    }
}

// ============================================================================ brush pro settings
@SuppressLint("ViewConstructor")
class BrushPreview(ctx: Context, var spec: BrushSpec, var color: Int, private val paper: Int) : View(ctx) {
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), UI.dp(context, 86f))
    private val m = Matrix()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        bgPaint.color = paper
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), UI.dpf(context, 10f), UI.dpf(context, 10f), bgPaint)
        val s = Stroke(spec.copy(), color, spec.size.coerceAtMost(height * 0.55f), 0.0, 0.0)
        val w = width - UI.dpf(context, 40f)
        val n = 90
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = UI.dpf(context, 20f) + t * w
            val y = height / 2f + sin(t * 6.283f * 1.1f) * height * 0.22f
            val p = (0.15f + 0.85f * sin(t * 3.1416f)).coerceIn(0f, 1f)
            s.add(x, y, p, 0.5f * t, t * 600f)
        }
        if (spec.type.kind == RenderKind.FILL) {
            s.n = 0
            val cx = width / 2f; val cy = height / 2f
            for (i in 0..40) { val a = i / 40f * 6.283f; s.add(cx + kotlin.math.cos(a) * w * 0.3f, cy + sin(a) * height * 0.3f, 1f, 0f, i * 10f) }
        }
        m.reset()
        StrokeRenderer.draw(c, s, m)
    }
}

@SuppressLint("ViewConstructor")
class BrushPanel(
    ctx: Context, private val spec: BrushSpec, color: Int, paper: Int,
    private val onChanged: () -> Unit, private val onReset: () -> Unit, onClose: () -> Unit
) : Panel(ctx, "${spec.type.label} · Pro brush settings", onClose) {
    private val preview = BrushPreview(ctx, spec, color, paper)

    init { build() }

    private fun build() {
        val ctx = context
        body.removeAllViews()
        body.addView(preview)
        fun changed() { preview.spec = spec; preview.invalidate(); onChanged() }
        fun pct(v: Float) = "${(v * 100).toInt()}%"

        body.addView(UI.choice(ctx, "Brush engine", BrushType.entries.map { it.label }, spec.type.ordinal) { i ->
            val fresh = BrushSpec.preset(BrushType.entries[i])
            spec.type = fresh.type
            copyInto(fresh, spec)
            changed(); build()
        })
        body.addView(UI.section(ctx, "Basics"))
        body.addView(UI.slider(ctx, "Size", 0f, 1f, sizeToT(spec.size), { "%.1f px".format(tToSize(it)) }) { spec.size = tToSize(it); changed() })
        body.addView(UI.slider(ctx, "Opacity", 0.02f, 1f, spec.opacity, ::pct) { spec.opacity = it; changed() })
        body.addView(UI.slider(ctx, "Smoothing (streamline)", 0f, 1f, spec.smoothing, ::pct) { spec.smoothing = it; changed() })

        body.addView(UI.section(ctx, "Pressure"))
        body.addView(UI.slider(ctx, "Pressure → size", 0f, 1f, spec.pressureSize, ::pct) { spec.pressureSize = it; changed() })
        body.addView(UI.slider(ctx, "Pressure → opacity", 0f, 1f, spec.pressureOpacity, ::pct) { spec.pressureOpacity = it; changed() })
        body.addView(UI.slider(ctx, "Pressure curve (soft ↔ firm)", 0.3f, 3f, spec.pressureGamma, { "%.2f".format(it) }) { spec.pressureGamma = it; changed() })
        body.addView(UI.slider(ctx, "Minimum size", 0f, 1f, spec.minSize, ::pct) { spec.minSize = it; changed() })

        body.addView(UI.section(ctx, "Tilt & speed"))
        body.addView(UI.slider(ctx, "Tilt → size (shading)", 0f, 1f, spec.tiltSize, ::pct) { spec.tiltSize = it; changed() })
        body.addView(UI.slider(ctx, "Speed thinning", -1f, 1f, spec.velocitySize, { "%+.0f%%".format(it * 100) }) { spec.velocitySize = it; changed() })

        body.addView(UI.section(ctx, "Shape"))
        body.addView(UI.slider(ctx, "Taper start", 0f, 1f, spec.taperStart, ::pct) { spec.taperStart = it; changed() })
        body.addView(UI.slider(ctx, "Taper end", 0f, 1f, spec.taperEnd, ::pct) { spec.taperEnd = it; changed() })
        body.addView(UI.slider(ctx, "Nib roundness", 0.05f, 1f, spec.nibRoundness, ::pct) { spec.nibRoundness = it; changed() })
        body.addView(UI.slider(ctx, "Nib angle", 0f, 180f, spec.nibAngle, { "${it.toInt()}°" }) { spec.nibAngle = it; changed() })

        body.addView(UI.section(ctx, "Texture & stamps"))
        body.addView(UI.slider(ctx, "Paper grain", 0f, 1f, spec.grain, ::pct) { spec.grain = it; changed() })
        if (spec.type.kind == RenderKind.STAMP) {
            body.addView(UI.slider(ctx, "Flow", 0.005f, 1f, spec.flow, ::pct) { spec.flow = it; changed() })
            body.addView(UI.slider(ctx, "Spacing", 0.02f, 1.5f, spec.spacing, ::pct) { spec.spacing = it; changed() })
            body.addView(UI.slider(ctx, "Size jitter", 0f, 1f, spec.jitterSize, ::pct) { spec.jitterSize = it; changed() })
            body.addView(UI.slider(ctx, "Opacity jitter", 0f, 1f, spec.jitterOpacity, ::pct) { spec.jitterOpacity = it; changed() })
            body.addView(UI.slider(ctx, "Angle jitter", 0f, 1f, spec.jitterAngle, ::pct) { spec.jitterAngle = it; changed() })
            body.addView(UI.slider(ctx, "Scatter", 0f, 1f, spec.scatter, ::pct) { spec.scatter = it; changed() })
            body.addView(UI.slider(ctx, "Wet edges", 0f, 1f, spec.wetEdges, ::pct) { spec.wetEdges = it; changed() })
            body.addView(UI.toggle(ctx, "Rotate stamps with stroke", null, spec.followDirection) { spec.followDirection = it; changed() })
        }
        body.addView(UI.section(ctx, "Blending"))
        body.addView(UI.choice(ctx, "Blend mode", BlendKind.entries.map { it.label }, spec.blend.ordinal) { spec.blend = BlendKind.entries[it]; changed() })
        body.addView(UI.button(ctx, "Reset this brush to default") { onReset(); copyInto(BrushSpec.preset(spec.type), spec); changed(); build() }
            .apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = UI.dp(ctx, 12f) })
    }

    companion object {
        fun tToSize(t: Float): Float = exp(ln(0.3f) + t * (ln(300f) - ln(0.3f)))
        fun sizeToT(s: Float): Float = ((ln(s.coerceIn(0.3f, 300f)) - ln(0.3f)) / (ln(300f) - ln(0.3f)))

        fun copyInto(src: BrushSpec, dst: BrushSpec) {
            dst.type = src.type
            dst.size = src.size; dst.opacity = src.opacity; dst.smoothing = src.smoothing; dst.pressureSize = src.pressureSize
            dst.pressureOpacity = src.pressureOpacity; dst.pressureGamma = src.pressureGamma; dst.minSize = src.minSize
            dst.tiltSize = src.tiltSize; dst.velocitySize = src.velocitySize; dst.taperStart = src.taperStart; dst.taperEnd = src.taperEnd
            dst.nibAngle = src.nibAngle; dst.nibRoundness = src.nibRoundness; dst.followDirection = src.followDirection
            dst.spacing = src.spacing; dst.flow = src.flow; dst.jitterSize = src.jitterSize; dst.jitterOpacity = src.jitterOpacity
            dst.jitterAngle = src.jitterAngle; dst.scatter = src.scatter; dst.grain = src.grain; dst.wetEdges = src.wetEdges
            dst.blend = src.blend; dst.eraserWholeStroke = src.eraserWholeStroke
        }
    }
}

// ============================================================================ layers
@SuppressLint("ViewConstructor")
class LayersPanel(ctx: Context, private val canvas: CanvasView, private val onChanged: () -> Unit, onClose: () -> Unit) : Panel(ctx, "Layers", onClose) {
    init { build() }

    private fun doc(): Document = canvas.doc

    fun build() {
        val ctx = context
        body.removeAllViews()
        val d = doc()
        for (i in d.layers.indices.reversed()) {
            val l = d.layers[i]
            val row = UI.hbox(ctx)
            row.setPadding(UI.dp(ctx, 4f), UI.dp(ctx, 2f), UI.dp(ctx, 4f), UI.dp(ctx, 2f))
            row.background = UI.rounded(if (i == d.activeLayer) 0x262F7CF6 else UI.PANEL2, UI.dpf(ctx, 10f))
            val eye = IconButton(ctx, if (l.visible) "eye" else "eye_off", 40f).apply {
                setOnClickListener { l.visible = !l.visible; changed() }
            }
            val name = UI.label(ctx, "${l.name}  ·  ${l.strokes.size}", 14f, UI.TEXT, i == d.activeLayer).apply {
                setPadding(UI.dp(ctx, 6f), UI.dp(ctx, 12f), 0, UI.dp(ctx, 12f))
                setOnClickListener { d.activeLayer = i; canvas.clearSelection(); changed() }
                setOnLongClickListener { rename(l); true }
            }
            val lock = IconButton(ctx, if (l.locked) "lock" else "unlock", 40f).apply {
                setOnClickListener { l.locked = !l.locked; changed() }
            }
            row.addView(eye); row.addView(name, UI.lp(0, UI.WRAP, 1f)); row.addView(lock)
            body.addView(row, LinearLayout.LayoutParams(UI.MATCH, UI.WRAP).apply { topMargin = UI.dp(ctx, 6f) })
        }
        val active = d.active()
        body.addView(UI.slider(ctx, "Opacity of “${active.name}”", 0f, 1f, active.opacity, { "${(it * 100).toInt()}%" }) {
            active.opacity = it; canvas.invalidateContent()
        })
        val actions = UI.hbox(ctx)
        fun btn(icon: String, label: String, f: () -> Unit) {
            val col = UI.vbox(ctx).apply { gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(IconButton(ctx, icon, 42f).apply { setOnClickListener { f() } })
            col.addView(UI.label(ctx, label, 10.5f, UI.SUB).apply { gravity = Gravity.CENTER })
            actions.addView(col, UI.lp(0, UI.WRAP, 1f))
        }
        btn("plus", "Add") { addLayer() }
        btn("copy", "Duplicate") { duplicate() }
        btn("layers", "Merge ↓") { mergeDown() }
        btn("front", "Up") { move(1) }
        btn("flip", "Down") { move(-1) }
        btn("trash", "Delete") { delete() }
        body.addView(actions, LinearLayout.LayoutParams(UI.MATCH, UI.WRAP).apply { topMargin = UI.dp(ctx, 8f) })
        body.addView(UI.label(ctx, "Tap a layer to draw on it · long-press to rename", 11.5f, UI.SUB).apply { setPadding(0, UI.dp(ctx, 8f), 0, 0) })
    }

    private fun changed() { canvas.invalidateContent(); onChanged(); build() }

    private fun addLayer() {
        val d = doc()
        val l = Layer("Layer ${d.layers.size + 1}")
        val at = d.activeLayer + 1
        canvas.history.run(LambdaCommand({ d.layers.add(at, l); d.activeLayer = at }, { d.layers.remove(l); d.activeLayer = (at - 1).coerceAtLeast(0) }))
        changed()
    }

    private fun duplicate() {
        val d = doc(); val src = d.active()
        val l = Layer(src.name + " copy").apply { opacity = src.opacity; src.strokes.forEach { strokes.add(it.duplicate()) } }
        val at = d.activeLayer + 1
        canvas.history.run(LambdaCommand({ d.layers.add(at, l); d.activeLayer = at }, { d.layers.remove(l); d.activeLayer = (at - 1).coerceAtLeast(0) }))
        changed()
    }

    private fun mergeDown() {
        val d = doc(); val i = d.activeLayer
        if (i <= 0) return
        val top = d.layers[i]; val below = d.layers[i - 1]
        val belowBefore = ArrayList(below.strokes)
        canvas.history.run(LambdaCommand({
            below.strokes.addAll(top.strokes); d.layers.remove(top); d.activeLayer = i - 1
        }, {
            below.strokes.clear(); below.strokes.addAll(belowBefore); d.layers.add(i, top); d.activeLayer = i
        }))
        changed()
    }

    private fun move(dir: Int) {
        val d = doc(); val i = d.activeLayer; val j = i + dir
        if (j !in d.layers.indices) return
        canvas.history.run(LambdaCommand({ java.util.Collections.swap(d.layers, i, j); d.activeLayer = j }, { java.util.Collections.swap(d.layers, i, j); d.activeLayer = i }))
        changed()
    }

    private fun delete() {
        val d = doc()
        if (d.layers.size <= 1) { d.active().let { l -> val before = ArrayList(l.strokes); canvas.history.run(LambdaCommand({ l.strokes.clear() }, { l.strokes.addAll(before) })) }; changed(); return }
        val i = d.activeLayer; val l = d.layers[i]
        canvas.history.run(LambdaCommand({ d.layers.removeAt(i); d.activeLayer = (i - 1).coerceAtLeast(0) }, { d.layers.add(i, l); d.activeLayer = i }))
        changed()
    }

    private fun rename(l: Layer) {
        val et = EditText(context).apply { setText(l.name); setTextColor(UI.TEXT) }
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("Rename layer").setView(et)
            .setPositiveButton("OK") { _, _ -> l.name = et.text.toString().ifBlank { l.name }; build() }
            .setNegativeButton("Cancel", null).show()
    }
}

// ============================================================================ backgrounds
@SuppressLint("ViewConstructor")
class PatternTile(ctx: Context, val pattern: Pattern, private val spec: () -> BackgroundSpec) : View(ctx) {
    private val r = BackgroundRenderer()
    private val cam = Camera()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), UI.dp(context, 74f))

    override fun onDraw(c: Canvas) {
        val s = spec().copy().apply { pattern = this@PatternTile.pattern; spacing = 14f; adaptive = false; vpDistance = 60f; horizon = 0f }
        cam.viewW = width.toFloat(); cam.viewH = height - UI.dpf(context, 18f); cam.scale = 1.0 * resources.displayMetrics.density / 1.5; cam.update()
        c.save()
        c.clipRect(0f, 0f, width.toFloat(), cam.viewH)
        r.draw(c, cam, s, resources.displayMetrics.density * 0.7f)
        c.restore()
        border.color = if (isSelected) UI.ACCENT else 0x22000000
        border.strokeWidth = UI.dpf(context, if (isSelected) 3f else 1f)
        c.drawRect(0f, 0f, width.toFloat(), cam.viewH, border)
        text.color = if (isSelected) UI.ACCENT else UI.SUB; text.textSize = UI.dpf(context, 10.5f)
        c.drawText(pattern.label, width / 2f, height - UI.dpf(context, 4f), text)
    }
}

@SuppressLint("ViewConstructor")
class BackgroundPanel(ctx: Context, private val doc: Document, private val prefs: Prefs, private val onChanged: () -> Unit, onClose: () -> Unit) : Panel(ctx, "Paper & grid", onClose) {
    private val tiles = ArrayList<PatternTile>()

    init { build() }

    private fun bg() = doc.background

    private fun build() {
        val ctx = context
        body.removeAllViews(); tiles.clear()
        body.addView(UI.section(ctx, "Pattern"))
        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        var row: LinearLayout? = null
        Pattern.entries.forEachIndexed { i, p ->
            if (i % 3 == 0) { row = UI.hbox(ctx); grid.addView(row, LinearLayout.LayoutParams(UI.MATCH, UI.WRAP).apply { topMargin = UI.dp(ctx, 6f) }) }
            val t = PatternTile(ctx, p) { bg() }
            t.isSelected = p == bg().pattern
            t.setOnClickListener { bg().pattern = p; tiles.forEach { it.isSelected = it.pattern == p; it.invalidate() }; onChanged() }
            tiles.add(t)
            row!!.addView(t, UI.lp(0, UI.WRAP, 1f).apply { marginStart = UI.dp(ctx, 3f); marginEnd = UI.dp(ctx, 3f) })
        }
        body.addView(grid)

        body.addView(UI.section(ctx, "Paper color"))
        body.addView(SwatchGrid(ctx, BackgroundSpec.PAPERS.toList(), { c ->
            bg().paperColor = c
            bg().lineColor = if (ColorUtil.luminance(c) < 0.45f) Color.rgb(200, 210, 230) else Color.rgb(120, 130, 150)
            tiles.forEach { it.invalidate() }; onChanged()
        }))
        body.addView(UI.section(ctx, "Grid color"))
        body.addView(SwatchGrid(ctx, listOf(Color.rgb(120, 130, 150), Color.rgb(90, 140, 220), Color.rgb(220, 120, 120), Color.rgb(110, 170, 120), Color.rgb(60, 60, 60), Color.rgb(200, 210, 230), Color.rgb(230, 190, 90)), { c ->
            bg().lineColor = c; tiles.forEach { it.invalidate() }; onChanged()
        }))
        body.addView(UI.section(ctx, "Grid"))
        body.addView(UI.slider(ctx, "Spacing", 4f, 200f, bg().spacing, { "${it.toInt()}" }) { bg().spacing = it; onChanged() })
        body.addView(UI.slider(ctx, "Line opacity", 0.03f, 1f, bg().lineAlpha, { "${(it * 100).toInt()}%" }) { bg().lineAlpha = it; tiles.forEach { t -> t.invalidate() }; onChanged() })
        body.addView(UI.slider(ctx, "Line weight", 0.3f, 4f, bg().lineWidth, { "%.1f".format(it) }) { bg().lineWidth = it; onChanged() })
        body.addView(UI.slider(ctx, "Major line every", 2f, 10f, bg().majorEvery.toFloat(), { "${it.toInt()}" }) { bg().majorEvery = it.toInt(); onChanged() })
        body.addView(UI.toggle(ctx, "Infinite adaptive grid", "Finer lines fade in as you zoom in, coarser ones as you zoom out", bg().adaptive) { bg().adaptive = it; onChanged() })
        body.addView(UI.section(ctx, "Perspective"))
        body.addView(UI.slider(ctx, "Vanishing point distance", 100f, 5000f, bg().vpDistance, { "${it.toInt()}" }) { bg().vpDistance = it; onChanged() })
        body.addView(UI.slider(ctx, "Horizon height", -2000f, 2000f, bg().horizon, { "${it.toInt()}" }) { bg().horizon = it; onChanged() })
        body.addView(UI.button(ctx, "Use as default for new drawings") {
            val b = bg()
            prefs.put("def_bg", "${b.pattern.ordinal},${b.spacing},${b.paperColor},${b.lineColor},${b.lineAlpha},${b.majorEvery},${b.adaptive},${b.lineWidth}")
        }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = UI.dp(ctx, 12f) })
    }

    companion object {
        fun applyDefault(prefs: Prefs, b: BackgroundSpec) {
            val s = prefs.sp.getString("def_bg", null) ?: return
            try {
                val p = s.split(",")
                b.pattern = Pattern.entries[p[0].toInt()]; b.spacing = p[1].toFloat(); b.paperColor = p[2].toInt(); b.lineColor = p[3].toInt()
                b.lineAlpha = p[4].toFloat(); b.majorEvery = p[5].toInt(); b.adaptive = p[6].toBoolean(); b.lineWidth = p[7].toFloat()
            } catch (_: Exception) {}
        }
    }
}

/**
 * Panels pop out next to the tool wheel / brush strip (start side) like Concepts, or next to the
 * layers sidebar (end side).
 */
fun panelLayoutParams(ctx: Context, leftSide: Boolean, maxHeightFraction: Float = 0.86f, nearWheel: Boolean = true): FrameLayout.LayoutParams {
    val dm = ctx.resources.displayMetrics
    val narrow = dm.widthPixels / dm.density < 600
    val w = minOf(UI.dp(ctx, 360f), dm.widthPixels - UI.dp(ctx, 24f))
    val h = (dm.heightPixels * maxHeightFraction).toInt()
    val atStart = if (nearWheel) leftSide else !leftSide
    return FrameLayout.LayoutParams(w, h, (if (atStart) Gravity.START else Gravity.END) or Gravity.TOP).apply {
        topMargin = UI.dp(ctx, if (narrow) 170f else if (nearWheel) 14f else 78f)
        val side = if (narrow) 12f else if (nearWheel) 214f else 124f
        marginStart = UI.dp(ctx, side); marginEnd = UI.dp(ctx, side)
    }
}
