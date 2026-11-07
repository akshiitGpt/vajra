package com.akshit.folio.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.akshit.folio.core.Camera
import com.akshit.folio.core.FingerDraw
import com.akshit.folio.core.GestureAction
import com.akshit.folio.core.Prefs
import com.akshit.folio.model.BackgroundSpec
import com.akshit.folio.model.Document
import com.akshit.folio.model.Layer
import com.akshit.folio.model.Pattern
import com.akshit.folio.render.BackgroundRenderer
import com.akshit.folio.render.SceneRenderer
import com.akshit.folio.view.CanvasView

/** A plain icon. */
@SuppressLint("ViewConstructor")
class IconView(ctx: Context, icon: String, sizeDp: Float, color: Int = UI.ICON) : View(ctx) {
    private val d = IconDrawable(icon, color, 1.8f)
    private val s = UI.dp(ctx, sizeDp)
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(s, s)
    override fun onDraw(c: Canvas) { val p = (width * 0.12f).toInt(); d.setBounds(p, p, width - p, height - p); d.draw(c) }
}

/**
 * Layers list that sits under the wheel: header, sorting mode, "New Layer", then a timeline of
 * layer cards (eye on the rail, thumbnail card, name + opacity) and the grid as the last layer.
 */
@SuppressLint("ViewConstructor")
class LayersList(
    ctx: Context, private val canvas: CanvasView, private val prefs: Prefs,
    private val onGrid: () -> Unit, private val onLayerMenu: (Layer) -> Unit
) : LinearLayout(ctx) {
    private val list = UI.vbox(ctx)
    private val sortingValue: TextView
    private var lastPattern = Pattern.DOTS

    init {
        orientation = VERTICAL
        val head = UI.hbox(ctx)
        head.addView(IconView(ctx, "stack", 34f, 0xFF262626.toInt()))
        head.addView(UI.label(ctx, "Layers", 19f, 0xFF262626.toInt()).apply { setPadding(UI.dp(ctx, 8f), 0, 0, 0) })
        head.setPadding(UI.dp(ctx, 4f), UI.dp(ctx, 4f), UI.dp(ctx, 12f), UI.dp(ctx, 4f))
        head.background = UI.rounded(0xD9F1F1F1.toInt(), UI.dpf(ctx, 8f))
        addView(head, LayoutParams(UI.WRAP, UI.WRAP))
        val sort = UI.hbox(ctx)
        sort.setPadding(UI.dp(ctx, 10f), UI.dp(ctx, 8f), UI.dp(ctx, 10f), UI.dp(ctx, 8f))
        sort.background = UI.rounded(0xD9F1F1F1.toInt(), UI.dpf(ctx, 8f))
        sort.addView(UI.label(ctx, "⇅  Sorting", 15f, 0xFF262626.toInt()))
        sort.addView(View(ctx).apply { setBackgroundColor(0x33000000) }, LayoutParams(UI.dp(ctx, 1f), UI.dp(ctx, 16f)).apply { marginStart = UI.dp(ctx, 10f); marginEnd = UI.dp(ctx, 10f) })
        sortingValue = UI.label(ctx, if (prefs.autoLayers) "Automatic" else "Manual", 15f, 0xFF262626.toInt())
        sort.addView(sortingValue)
        sort.setOnClickListener {
            prefs.put(Prefs.K_AUTO_LAYERS, !prefs.autoLayers)
            sortingValue.text = if (prefs.autoLayers) "Automatic" else "Manual"
        }
        addView(sort, LayoutParams(UI.WRAP, UI.WRAP).apply { topMargin = UI.dp(ctx, 10f) })
        val add = UI.label(ctx, "+  New Layer", 15f, 0xFF262626.toInt()).apply {
            setPadding(UI.dp(ctx, 10f), UI.dp(ctx, 8f), UI.dp(ctx, 12f), UI.dp(ctx, 8f))
            background = UI.rounded(0xD9F1F1F1.toInt(), UI.dpf(ctx, 8f))
            setOnClickListener {
                val d = canvas.doc
                val l = Layer("Layer ${d.layers.size + 1}")
                val at = d.activeLayer + 1
                canvas.history.run(com.akshit.folio.model.LambdaCommand({ d.layers.add(at, l); d.activeLayer = at }, { d.layers.remove(l); d.activeLayer = (at - 1).coerceAtLeast(0) }))
                refresh()
            }
        }
        addView(add, LayoutParams(UI.WRAP, UI.WRAP).apply { topMargin = UI.dp(ctx, 6f) })
        addView(ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(list) }, LayoutParams(UI.MATCH, 0, 1f).apply { topMargin = UI.dp(ctx, 6f) })
    }

    fun refresh() {
        list.removeAllViews()
        val doc = canvas.doc
        val bounds = SceneRenderer.contentBounds(doc)
        val tw = UI.dp(context, 86f); val th = UI.dp(context, 58f)
        val cam = Camera().apply {
            viewW = tw.toFloat(); viewH = th.toFloat()
            if (bounds != null) {
                cx = (bounds[0] + bounds[2]) / 2; cy = (bounds[1] + bounds[3]) / 2
                scale = minOf(tw / (bounds[2] - bounds[0]).coerceAtLeast(1e-9), th / (bounds[3] - bounds[1]).coerceAtLeast(1e-9)) * 0.86
            } else scale = 0.1
            update()
        }
        val n = doc.layers.size
        for (i in doc.layers.indices.reversed()) {
            val layer = doc.layers[i]
            val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.drawColor(Color.WHITE)
            SceneRenderer.drawLayers(c, cam, doc, i..i)
            list.addView(LayerRow(context, bmp, null, layer.name, "${(layer.opacity * 100).toInt()}%", layer.visible, i == doc.activeLayer, layer.locked, first = i == n - 1, last = false,
                onEye = { layer.visible = !layer.visible; canvas.invalidateContent(); refresh() },
                onTap = { doc.activeLayer = i; canvas.clearSelection(); canvas.invalidateContent(); refresh() },
                onLong = { onLayerMenu(layer) }))
        }
        // the grid is the bottom "layer"
        val bg = doc.background
        val gridOn = bg.pattern != Pattern.NONE
        if (gridOn) lastPattern = bg.pattern
        list.addView(LayerRow(context, null, if (gridOn) bg.pattern else lastPattern, (if (gridOn) bg.pattern else lastPattern).label.let { if (it == "Dots") "Dot Grid" else it }, "${(bg.lineAlpha * 100).toInt()}%",
            gridOn, false, false, first = n == 0, last = true,
            onEye = { bg.pattern = if (bg.pattern == Pattern.NONE) lastPattern else Pattern.NONE; canvas.invalidate(); refresh() },
            onTap = onGrid, onLong = onGrid))
    }

    @SuppressLint("ViewConstructor")
    private class LayerRow(
        ctx: Context, val thumb: Bitmap?, val gridIcon: Pattern?, val name: String, val pct: String,
        val visible: Boolean, val active: Boolean, val locked: Boolean, val first: Boolean, val last: Boolean,
        onEye: () -> Unit, onTap: () -> Unit, onLong: () -> Unit
    ) : FrameLayout(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val eye = IconDrawable(if (visible) "eye" else "eye_off", 0xFF555555.toInt(), 1.6f)
        private val grid = IconDrawable("dots9", 0xFF333333.toInt(), 1.2f)
        private val lock = IconDrawable("lockc", 0xFF555555.toInt(), 1.2f)
        private fun dp(v: Float) = UI.dpf(context, v)

        init {
            setWillNotDraw(false)
            val eyeHit = View(ctx).apply { setOnClickListener { onEye() } }
            addView(eyeHit, LayoutParams(UI.dp(ctx, 40f), UI.MATCH))
            val body = View(ctx).apply { setOnClickListener { onTap() }; setOnLongClickListener { onLong(); true } }
            addView(body, LayoutParams(UI.MATCH, UI.MATCH).apply { marginStart = UI.dp(ctx, 42f) })
        }

        override fun onMeasure(w: Int, h: Int) {
            super.onMeasure(w, h)
            setMeasuredDimension(MeasureSpec.getSize(w), UI.dp(context, 76f))
            for (i in 0 until childCount) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(if (i == 0) UI.dp(context, 40f) else measuredWidth - UI.dp(context, 42f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
        }

        override fun onDraw(c: Canvas) {
            val railX = dp(20f); val cy = height / 2f
            // rail
            p.color = 0x55808080; p.strokeWidth = dp(1.5f)
            c.drawLine(railX, if (first) cy else 0f, railX, if (last) cy else height.toFloat(), p)
            // eye bubble
            p.color = if (active) 0xFFD2D2D2.toInt() else 0x00000000
            c.drawCircle(railX, cy, dp(15f), p)
            eye.alpha = if (visible) 255 else 140
            eye.setBounds((railX - dp(11f)).toInt(), (cy - dp(11f)).toInt(), (railX + dp(11f)).toInt(), (cy + dp(11f)).toInt()); eye.draw(c)
            // name pill
            val cardL = dp(46f); val cardT = dp(8f); val cardW = dp(86f); val cardH = dp(60f)
            if (active) { p.color = 0xFFDCDCDC.toInt(); c.drawRoundRect(cardL + cardW - dp(8f), cardT, cardL + cardW + dp(84f), cardT + cardH, dp(6f), dp(6f), p) }
            // card
            p.color = Color.WHITE; p.setShadowLayer(dp(2f), 0f, dp(0.5f), 0x33000000)
            c.drawRoundRect(cardL, cardT, cardL + cardW, cardT + cardH, dp(5f), dp(5f), p); p.clearShadowLayer()
            if (thumb != null) c.drawBitmap(thumb, null, android.graphics.RectF(cardL + dp(2f), cardT + dp(2f), cardL + cardW - dp(2f), cardT + cardH - dp(2f)), p)
            if (gridIcon != null) { grid.setBounds((cardL + cardW / 2 - dp(14f)).toInt(), (cardT + cardH / 2 - dp(14f)).toInt(), (cardL + cardW / 2 + dp(14f)).toInt(), (cardT + cardH / 2 + dp(14f)).toInt()); grid.draw(c) }
            if (active) { p.style = Paint.Style.STROKE; p.color = 0xFF555555.toInt(); p.strokeWidth = dp(1.5f); c.drawRoundRect(cardL, cardT, cardL + cardW, cardT + cardH, dp(5f), dp(5f), p); p.style = Paint.Style.FILL }
            // labels
            p.textSize = dp(15f); p.color = if (visible) 0xFF3A3A3A.toInt() else 0x883A3A3A.toInt()
            val tx = cardL + cardW + dp(10f)
            val label = if (name.length > 12) name.take(11) + "…" else name
            c.drawText(label, tx, cy - dp(3f), p)
            c.drawText(pct, tx, cy + dp(16f), p)
            if (locked) { lock.setBounds((tx + dp(48f)).toInt(), (cy + dp(4f)).toInt(), (tx + dp(62f)).toInt(), (cy + dp(18f)).toInt()); lock.draw(c) }
        }
    }
}

/**
 * Right-side settings sheet with Workspace / Interaction tabs, collapsible sections and rows of
 * round choices — every customisation lives here.
 */
@SuppressLint("ViewConstructor")
class SettingsSheet(
    ctx: Context, private val prefs: Prefs, private val doc: Document, startTab: Int,
    private val onChanged: () -> Unit, onClose: () -> Unit, private val onBarMode: (Boolean) -> Unit
) : LinearLayout(ctx) {
    private val body = UI.vbox(ctx)
    private val tabs = ArrayList<TextView>()
    private var tab = startTab
    private fun dp(v: Float) = UI.dp(context, v)

    init {
        orientation = VERTICAL
        background = UI.rounded(Color.WHITE, UI.dpf(ctx, 16f))
        elevation = UI.dpf(ctx, 14f)
        isClickable = true
        val head = UI.hbox(ctx)
        head.setPadding(dp(10f), dp(10f), dp(14f), 0)
        head.addView(IconButton(ctx, "close", 46f, 0xFF262626.toInt()).apply { setOnClickListener { onClose() } })
        for ((i, name) in listOf("Workspace", "Interaction").withIndex()) {
            val t = UI.label(ctx, name, 17f, UI.SUB).apply {
                gravity = Gravity.CENTER; setPadding(dp(12f), dp(10f), dp(12f), dp(12f))
                setOnClickListener { tab = i; build() }
            }
            tabs.add(t); head.addView(t)
        }
        head.addView(View(ctx), UI.lp(0, 1, 1f))
        addView(head)
        addView(View(ctx).apply { setBackgroundColor(0x14000000) }, LayoutParams(UI.MATCH, dp(1f)))
        body.setPadding(dp(24f), dp(6f), dp(20f), dp(30f))
        addView(UI.scroll(ctx, body), LayoutParams(UI.MATCH, 0, 1f))
        build()
    }

    private fun build() {
        tabs.forEachIndexed { i, t ->
            t.setTextColor(if (i == tab) 0xFF1E1E1E.toInt() else UI.SUB)
            t.background = if (i == tab) object : android.graphics.drawable.Drawable() {
                val p = Paint().apply { color = 0xFF1E1E1E.toInt() }
                override fun draw(c: Canvas) { val b = bounds; c.drawRect(b.left.toFloat(), b.bottom - UI.dpf(context, 3f), b.right.toFloat(), b.bottom.toFloat(), p) }
                override fun setAlpha(a: Int) {}
                override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
                @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
            } else null
        }
        body.removeAllViews()
        if (tab == 0) workspace() else interaction()
    }

    // ---------------------------------------------------------------- building blocks
    private fun section(title: String, open: Boolean = true, content: (LinearLayout) -> Unit) {
        val box = UI.vbox(context)
        val head = UI.hbox(context)
        head.setPadding(0, dp(18f), 0, dp(8f))
        head.addView(UI.label(context, title, 26f, 0xFF1E1E1E.toInt()), UI.lp(0, UI.WRAP, 1f))
        val chev = IconView(context, "chev_down", 28f, 0xFF1E1E1E.toInt())
        head.addView(chev)
        val inner = UI.vbox(context)
        inner.visibility = if (open) View.VISIBLE else View.GONE
        head.setOnClickListener { val o = inner.visibility != View.VISIBLE; inner.visibility = if (o) View.VISIBLE else View.GONE; chev.rotation = if (o) 0f else -90f }
        if (!open) chev.rotation = -90f
        box.addView(head); box.addView(inner)
        content(inner)
        body.addView(box)
    }

    private fun sub(parent: LinearLayout, title: String, desc: String?, link: String? = null, onLink: (() -> Unit)? = null) {
        val row = UI.hbox(context)
        row.setPadding(0, dp(14f), 0, dp(2f))
        row.addView(UI.label(context, title, 17f, 0xFF262626.toInt()), UI.lp(0, UI.WRAP, 1f))
        if (link != null) row.addView(UI.label(context, link, 16f, 0xFF3B8EE0.toInt()).apply { setOnClickListener { onLink?.invoke() } })
        parent.addView(row)
        if (desc != null) parent.addView(UI.label(context, desc, 14.5f, UI.SUB).apply { setPadding(0, dp(6f), 0, dp(10f)) })
    }

    private fun toggleGrid(parent: LinearLayout, items: List<Triple<String, Boolean, (Boolean) -> Unit>>) {
        var row: LinearLayout? = null
        items.forEachIndexed { i, (label, on, f) ->
            if (i % 2 == 0) { row = UI.hbox(context); parent.addView(row) }
            val cell = UI.hbox(context)
            cell.setPadding(0, dp(8f), dp(14f), dp(8f))
            cell.addView(UI.label(context, label, 15.5f, 0xFF262626.toInt()), UI.lp(0, UI.WRAP, 1f))
            val sw = android.widget.Switch(context)
            sw.isChecked = on
            sw.thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0xFF3E5C60.toInt(), Color.WHITE))
            sw.trackTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0xFF3E5C60.toInt(), 0xFF6F6F6F.toInt()))
            sw.setOnCheckedChangeListener { _, b -> f(b) }
            cell.addView(sw)
            row!!.addView(cell, UI.lp(0, UI.WRAP, 1f))
        }
    }

    private fun iconPainter(icon: String): (Canvas, Float, Float, Float) -> Unit {
        val d = IconDrawable(icon, 0xFF262626.toInt(), 1.6f)
        return { c, cx, cy, r -> val h = (r * 0.45f).toInt(); d.setBounds((cx - h).toInt(), (cy - h).toInt(), (cx + h).toInt(), (cy + h).toInt()); d.draw(c) }
    }

    private val bgr = BackgroundRenderer()
    private fun patternPainter(pat: Pattern): (Canvas, Float, Float, Float) -> Unit = { c, cx, cy, r ->
        val spec = doc.background.copy().apply { pattern = pat; spacing = 9f; adaptive = false; paperColor = Color.WHITE; lineColor = 0xFF555555.toInt(); lineAlpha = 0.6f; vpDistance = 40f; horizon = 0f; texture = false }
        val cam = Camera().apply { viewW = r * 2; viewH = r * 2; scale = UI.dpf(context, 1f).toDouble(); update() }
        c.save(); c.translate(cx - r, cy - r); bgr.draw(c, cam, spec, UI.dpf(context, 0.6f)); c.restore()
    }

    private fun paperPainter(p: BackgroundSpec.Paper): (Canvas, Float, Float, Float) -> Unit = { c, cx, cy, r ->
        val spec = BackgroundSpec().apply { pattern = Pattern.NONE; paperColor = p.color; texture = p.texture }
        val cam = Camera().apply { viewW = r * 2; viewH = r * 2; update() }
        c.save(); c.translate(cx - r, cy - r); bgr.draw(c, cam, spec, UI.dpf(context, 1f)); c.restore()
    }

    // ---------------------------------------------------------------- tabs
    private fun workspace() {
        section("Canvas") { s ->
            sub(s, "Background", "Standard paper type or custom background color?")
            val papers = BackgroundSpec.PAPER_PRESETS
            val cur = papers.firstOrNull { it.color == doc.background.paperColor }
            s.addView(CircleChoices(context, papers.map { Triple(it.name, paperPainter(it), it as Any) }, cur) { k ->
                val p = k as BackgroundSpec.Paper
                doc.background.paperColor = p.color; doc.background.texture = p.texture; doc.background.lineColor = p.line
                onChanged()
            })
            sub(s, "Grid Type", "You can quickly toggle the grid on/off on the Precision menu. Tap the active grid to edit.", "Edit Grid") { onChanged(); (context as? com.akshit.folio.CanvasActivity)?.togglePanel("bg") }
            val grids = listOf(Pattern.NONE to "No Grid", Pattern.DOTS to "Dot Grid", Pattern.GRAPH to "Graph Paper", Pattern.LINES to "Lined Paper", Pattern.GRID to "Square Grid",
                Pattern.ISOMETRIC to "Isometric", Pattern.ISO_DOTS to "Iso Dots", Pattern.TRIANGLES to "Triangles", Pattern.HEX to "Hexagons",
                Pattern.PERSPECTIVE_1 to "1-Point", Pattern.PERSPECTIVE_2 to "2-Point", Pattern.PERSPECTIVE_3 to "3-Point")
            s.addView(CircleChoices(context, grids.map { Triple(it.second, patternPainter(it.first), it.first as Any) }, doc.background.pattern) { k ->
                if (doc.background.pattern == k) (context as? com.akshit.folio.CanvasActivity)?.togglePanel("bg")
                doc.background.pattern = k as Pattern; onChanged()
            })
            sub(s, "Infinite grid", null)
            toggleGrid(s, listOf(Triple("Adaptive grid", doc.background.adaptive) { b: Boolean -> doc.background.adaptive = b; onChanged() },
                Triple("Paper texture", doc.background.texture) { b: Boolean -> doc.background.texture = b; onChanged() }))
        }
        section("Layers") { s ->
            sub(s, "Sorting", "Automatic puts every tool on its own layer (Pen, Pencil, Watercolor…). Manual draws on the layer you pick.")
            s.addView(CircleChoices(context, listOf(Triple("Automatic", iconPainter("stack"), true as Any), Triple("Manual", iconPainter("layers"), false as Any)), prefs.autoLayers) { k ->
                prefs.put(Prefs.K_AUTO_LAYERS, k as Boolean); onChanged()
            })
        }
        section("Writing") { s ->
            toggleGrid(s, listOf(
                Triple("Refine handwriting", prefs.refineAuto) { b: Boolean -> prefs.put(Prefs.K_REFINE, b) },
                Triple("Shape guides", prefs.shapeHold) { b: Boolean -> prefs.put(Prefs.K_SHAPE_HOLD, b) }))
            s.addView(UI.slider(context, "Refinement strength", 0.1f, 1f, prefs.refineStrength, { "${(it * 100).toInt()}%" }) { prefs.put(Prefs.K_REFINE_STRENGTH, it) })
            s.addView(UI.slider(context, "Refine after a pause of", 300f, 3000f, prefs.refineDelayMs.toFloat(), { "${it.toInt()} ms" }) { prefs.put(Prefs.K_REFINE_DELAY, it.toInt()) })
            s.addView(UI.slider(context, "Hold time for shapes", 200f, 1200f, prefs.shapeHoldMs.toFloat(), { "${it.toInt()} ms" }) { prefs.put(Prefs.K_SHAPE_MS, it.toInt()) })
        }
        section("Interface", open = false) { s ->
            toggleGrid(s, listOf(
                Triple("Tool bar instead of wheel", prefs.sp.getInt("bar_mode", 0) != 0) { b: Boolean -> onBarMode(b) },
                Triple("Left-handed layout", prefs.leftHanded) { b: Boolean -> prefs.put(Prefs.K_LEFT, b) },
                Triple("Hover cursor", prefs.hoverCursor) { b: Boolean -> prefs.put(Prefs.K_HOVER_CURSOR, b) },
                Triple("Show zoom", prefs.showZoom) { b: Boolean -> prefs.put(Prefs.K_SHOW_ZOOM, b) },
                Triple("Keep screen on", prefs.keepScreenOn) { b: Boolean -> prefs.put(Prefs.K_SCREEN_ON, b) },
                Triple("FPS meter", prefs.showFps) { b: Boolean -> prefs.put(Prefs.K_FPS, b) }))
        }
    }

    private fun interaction() {
        section("Touch Input") { s ->
            sub(s, "Finger Action", "While your stylus draws, you can set your finger to do something else.")
            val cur = when {
                prefs.fingerDraw == FingerDraw.ALWAYS -> 1
                prefs.fingerSelect -> 3
                prefs.action(Prefs.G_ONE_FINGER_DRAG) == GestureAction.NONE -> 0
                else -> 2
            }
            s.addView(CircleChoices(context, listOf(
                Triple("Do Nothing", iconPainter("close"), 0 as Any), Triple("Use Active Tool", iconPainter("brush"), 1 as Any),
                Triple("Pan Canvas", iconPainter("move4"), 2 as Any), Triple("Select", iconPainter("cursor"), 3 as Any)), cur) { k ->
                when (k as Int) {
                    0 -> { prefs.put(Prefs.K_FINGER_DRAW, FingerDraw.NEVER.ordinal); prefs.put(Prefs.K_FINGER_SELECT, false); prefs.put(Prefs.G_ONE_FINGER_DRAG, GestureAction.NONE.ordinal) }
                    1 -> { prefs.put(Prefs.K_FINGER_DRAW, FingerDraw.ALWAYS.ordinal); prefs.put(Prefs.K_FINGER_SELECT, false) }
                    2 -> { prefs.put(Prefs.K_FINGER_DRAW, FingerDraw.AUTO.ordinal); prefs.put(Prefs.K_FINGER_SELECT, false); prefs.put(Prefs.G_ONE_FINGER_DRAG, GestureAction.PAN.ordinal) }
                    3 -> { prefs.put(Prefs.K_FINGER_DRAW, FingerDraw.AUTO.ordinal); prefs.put(Prefs.K_FINGER_SELECT, true) }
                }
            })
            sub(s, "Two Fingers", "Use two fingers to move, scale and rotate the canvas and selections.")
            toggleGrid(s, listOf(
                Triple("Enable Canvas Zoom", prefs.zoomEnabled) { b: Boolean -> prefs.put(Prefs.K_ZOOM, b) },
                Triple("Enable Zoom Snap", prefs.zoomSnap) { b: Boolean -> prefs.put(Prefs.K_ZOOM_SNAP, b) },
                Triple("Enable Canvas Rotation", prefs.rotateEnabled) { b: Boolean -> prefs.put(Prefs.K_ROTATE, b) },
                Triple("Enable Rotation Snap", prefs.rotateSnap) { b: Boolean -> prefs.put(Prefs.K_ROTATE_SNAP, b) }))
            sub(s, "Tap & Hold", "You can pick things up with a tap & hold gesture.")
            val holds = listOf(GestureAction.NONE to "Nothing", GestureAction.EYEDROPPER to "Eyedropper", GestureAction.LASSO to "Select", GestureAction.COLOR_PICKER to "Colors")
            val icons = listOf("close", "eyedropper", "cursor", "palette")
            s.addView(CircleChoices(context, holds.mapIndexed { i, h -> Triple(h.second, iconPainter(icons[i]), h.first as Any) }, prefs.action(Prefs.G_LONG_PRESS)) { k ->
                prefs.put(Prefs.G_LONG_PRESS, (k as GestureAction).ordinal)
            })
        }
        section("Gestures") { s ->
            for ((key, title) in Prefs.GESTURE_KEYS) {
                if (key == Prefs.G_ONE_FINGER_DRAG || key == Prefs.G_LONG_PRESS) continue
                val opts = GestureAction.entries.toList()
                s.addView(UI.choice(context, title, opts.map { it.label }, opts.indexOf(prefs.action(key)).coerceAtLeast(0)) { prefs.put(key, opts[it].ordinal) })
            }
            toggleGrid(s, listOf(
                Triple("3-finger swipe = undo scrub", prefs.action(Prefs.G_THREE_SWIPE) != GestureAction.NONE) { b: Boolean -> prefs.put(Prefs.G_THREE_SWIPE, if (b) GestureAction.UNDO.ordinal else GestureAction.NONE.ordinal) },
                Triple("Volume keys", prefs.volumeKeys) { b: Boolean -> prefs.put(Prefs.K_VOLUME, b) }))
        }
        section("Pen buttons, squeeze & slide") { s ->
            sub(s, "Pen controls", "Squeeze, slide and pen buttons. If your pen does something that isn't listed, open Pen Lab → Learn and do it once.")
            val opts = GestureAction.entries.toList()
            for ((code, name) in Prefs.PEN_KEY_NAMES) {
                s.addView(UI.choice(context, name, opts.map { it.label }, opts.indexOf(prefs.penKeyAction(code) ?: GestureAction.NONE).coerceAtLeast(0)) { prefs.setPenKeyAction(code, opts[it]) })
            }
            for (code in prefs.learnedKeys.filter { it !in Prefs.PEN_KEY_NAMES.keys }) {
                s.addView(UI.choice(context, "Learned: ${android.view.KeyEvent.keyCodeToString(code)}", opts.map { it.label }, opts.indexOf(prefs.penKeyAction(code) ?: GestureAction.NONE).coerceAtLeast(0)) { prefs.setPenKeyAction(code, opts[it]) })
            }
            val slides = com.akshit.folio.core.SlideAction.entries.toList()
            s.addView(UI.choice(context, "Slide along the pen", slides.map { it.label }, prefs.slideAction.ordinal) { prefs.put(Prefs.K_SLIDE, it) })
            s.addView(UI.button(context, "Open Pen Lab to learn a pen gesture") { context.startActivity(android.content.Intent(context, com.akshit.folio.PenLabActivity::class.java)) }
                .apply { (layoutParams as? LayoutParams)?.topMargin = dp(8f) })
        }
        section("Stylus & Palm", open = false) { s ->
            s.addView(UI.slider(context, "Palm size threshold", 4f, 30f, prefs.palmSizeMm, { "%.1f mm".format(it) }) { prefs.put(Prefs.K_PALM_MM, it) })
            s.addView(UI.slider(context, "Ignore touches after the pen lifts", 0f, 1500f, prefs.palmCooldownMs.toFloat(), { "${it.toInt()} ms" }) { prefs.put(Prefs.K_PALM_COOLDOWN, it.toInt()) })
            s.addView(UI.slider(context, "Pressure curve (soft ↔ firm)", 0.3f, 3f, prefs.pressureGamma, { "%.2f".format(it) }) { prefs.put(Prefs.K_P_GAMMA, it) })
            s.addView(UI.slider(context, "Pressure floor", 0f, 0.5f, prefs.pressureMin, { "%.2f".format(it) }) { prefs.put(Prefs.K_P_MIN, it) })
            s.addView(UI.slider(context, "Pressure ceiling", 0.3f, 1f, prefs.pressureMax, { "%.2f".format(it) }) { prefs.put(Prefs.K_P_MAX, it) })
            toggleGrid(s, listOf(
                Triple("Reject touches while hovering", prefs.rejectWhileHover) { b: Boolean -> prefs.put(Prefs.K_REJECT_HOVER, b) },
                Triple("System palm detection", prefs.honorCancelFlag) { b: Boolean -> prefs.put(Prefs.K_CANCEL_FLAG, b) },
                Triple("Pen tilt", prefs.tiltEnabled) { b: Boolean -> prefs.put(Prefs.K_TILT, b) },
                Triple("Motion prediction", prefs.prediction) { b: Boolean -> prefs.put(Prefs.K_PREDICT, b) },
                Triple("Unbuffered input", prefs.unbuffered) { b: Boolean -> prefs.put(Prefs.K_UNBUFFERED, b) },
                Triple("Highest refresh rate", prefs.highRefresh) { b: Boolean -> prefs.put(Prefs.K_HIGH_REFRESH, b) }))
            val row = UI.hbox(context)
            row.addView(UI.button(context, "Pen Lab") { context.startActivity(android.content.Intent(context, com.akshit.folio.PenLabActivity::class.java)) })
            row.addView(UI.button(context, "All settings…") { context.startActivity(android.content.Intent(context, com.akshit.folio.SettingsActivity::class.java)) }.apply { (layoutParams as? LayoutParams)?.marginStart = dp(8f) },
                LayoutParams(UI.WRAP, UI.WRAP).apply { marginStart = dp(8f) })
            s.addView(row, LayoutParams(UI.WRAP, UI.WRAP).apply { topMargin = dp(12f) })
        }
    }
}
