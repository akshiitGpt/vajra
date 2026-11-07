package com.akshit.folio

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.akshit.folio.brush.BrushSpec
import com.akshit.folio.brush.BrushType
import com.akshit.folio.core.GestureAction
import com.akshit.folio.core.Prefs
import com.akshit.folio.core.ShapeRecognizer
import com.akshit.folio.model.Document
import com.akshit.folio.model.DocumentStore
import com.akshit.folio.render.SvgExporter
import com.akshit.folio.ui.BackgroundPanel
import com.akshit.folio.ui.BrushPanel
import com.akshit.folio.ui.ColorPickerPanel
import com.akshit.folio.ui.IconButton
import com.akshit.folio.ui.LayersPanel
import com.akshit.folio.ui.Panel
import com.akshit.folio.ui.ToolWheelView
import com.akshit.folio.ui.UI
import com.akshit.folio.ui.panelLayoutParams
import com.akshit.folio.view.CanvasView

class CanvasActivity : Activity(), CanvasView.Listener {
    lateinit var prefs: Prefs
    lateinit var store: DocumentStore
    lateinit var canvas: CanvasView
    lateinit var doc: Document

    private lateinit var root: FrameLayout
    private lateinit var statusBar: LinearLayout
    private lateinit var wheel: ToolWheelView
    private lateinit var wheelGroup: LinearLayout
    private var barGroup: View? = null
    private var barSlots = ArrayList<com.akshit.folio.ui.SlotView>()
    private var miniWheel: ToolWheelView? = null
    private lateinit var precisionBtn: IconButton
    private lateinit var layersBtn: IconButton
    private lateinit var paperBtn: IconButton
    /** Tools on the wheel rim: 0..12 = brush slots, 100 eraser, 101 select, 102 eyedropper, 103 pan */
    private val wheelTools = ArrayList<Int>()
    private lateinit var zoomPill: TextView
    private lateinit var selectionBar: LinearLayout
    private lateinit var hint: TextView
    private lateinit var panelHost: FrameLayout
    private lateinit var undoBtn: IconButton
    private lateinit var redoBtn: IconButton
    private var currentPanel: Panel? = null
    private var colorPanel: ColorPickerPanel? = null

    val slots = ArrayList<BrushSpec>()
    private val slotColors = ArrayList<Int>()
    private var sidebar: com.akshit.folio.ui.LayerSidebar? = null
    private lateinit var sidebarHolder: FrameLayout
    private val sidebarRefresh = Runnable { sidebar?.refresh() }
    var currentSlot = 0; private set
    private var lastSlot = 0
    private var uiHidden = false
    private val handler = Handler(Looper.getMainLooper())
    private val autosave = Runnable { save(false) }
    private var demo: DemoDriver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        store = DocumentStore(this)
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestHighRefreshRate()

        val id = intent.getStringExtra("doc_id")
        doc = id?.let { store.load(it) } ?: store.create(intent.getStringExtra("doc_name") ?: "Untitled").also {
            BackgroundPanel.applyDefault(prefs, it.background)
        }

        for (i in Prefs.DEFAULT_SLOTS.indices) { slots.add(prefs.brushSlot(i)); slotColors.add(prefs.slotColor(i)) }
        currentSlot = prefs.sp.getInt("cur_slot", 0).coerceIn(0, slots.size - 1)

        canvas = CanvasView(this, prefs)
        canvas.listener = this
        canvas.setDocument(doc)
        canvas.eraserSizePx = prefs.eraserSize
        canvas.eraserWhole = prefs.eraserWhole

        buildUi()
        selectSlot(currentSlot, openSettingsIfSame = false)
        goFullscreen()

        intent.getStringExtra("demo")?.let { name ->
            demo = DemoDriver(this)
            handler.postDelayed({ demo?.run(name) }, 1200)
        }
    }

    // ------------------------------------------------------------------ layout
    /**
     * Concepts-style workspace:
     *  - a thin status bar across the top (gallery, title, zoom, Precision / Layers / Paper
     *    toggles, export, settings)
     *  - the Tool Wheel: tools on the outer rim, size/opacity/smoothing on the inner ring,
     *    colour in the centre, undo/redo beside it. Drag it anywhere, pinch to resize, drop it
     *    on the left/right edge to turn it into a tool bar.
     *  - a floating, movable Layers menu
     */
    private fun buildUi() {
        val dm = resources.displayMetrics
        fun d(v: Float) = UI.dp(this, v)
        root = FrameLayout(this)
        root.setBackgroundColor(Color.WHITE)
        root.addView(canvas, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))

        loadWheelTools()

        // ---- status bar
        statusBar = UI.hbox(this)
        statusBar.setBackgroundColor(0xF7F9F9FA.toInt())
        statusBar.elevation = UI.dpf(this, 2f)
        statusBar.setPadding(d(6f), 0, d(6f), 0)
        statusBar.addView(IconButton(this, "gallery", 42f).apply { setOnClickListener { save(true); finish() } })
        val title = UI.label(this, doc.name, 14f, UI.TEXT, true).apply {
            setPadding(d(6f), 0, d(6f), 0); maxWidth = d(200f); maxLines = 1
            setOnClickListener { renameDoc(this) }
        }
        statusBar.addView(title)
        statusBar.addView(View(this), UI.lp(0, 1, 1f))
        zoomPill = UI.label(this, "100%", 13f, UI.SUB, true).apply {
            setPadding(d(12f), d(6f), d(12f), d(6f))
            background = UI.ripple(this@CanvasActivity, UI.rounded(Color.TRANSPARENT, UI.dpf(this@CanvasActivity, 8f)))
            setOnClickListener { canvas.zoomReset() }
            setOnLongClickListener { canvas.zoomFit(); true }
            visibility = if (prefs.showZoom) View.VISIBLE else View.INVISIBLE
        }
        statusBar.addView(zoomPill)
        statusBar.addView(View(this), UI.lp(0, 1, 1f))
        precisionBtn = IconButton(this, "ruler", 42f).apply { setOnClickListener { togglePanel("precision") } }
        layersBtn = IconButton(this, "layers", 42f).apply { setOnClickListener { toggleSidebar() } }
        paperBtn = IconButton(this, "grid", 42f).apply { setOnClickListener { togglePanel("bg") } }
        for (b in listOf(precisionBtn, layersBtn, paperBtn)) statusBar.addView(b)
        statusBar.addView(IconButton(this, "export", 42f).apply { setOnClickListener { showExport() } })
        statusBar.addView(IconButton(this, "penlab", 42f).apply { setOnClickListener { startActivity(Intent(this@CanvasActivity, PenLabActivity::class.java)) } })
        statusBar.addView(IconButton(this, "settings", 42f).apply { setOnClickListener { startActivity(Intent(this@CanvasActivity, SettingsActivity::class.java)) } })
        root.addView(statusBar, FrameLayout.LayoutParams(UI.MATCH, d(48f), Gravity.TOP))

        // ---- tool wheel + undo/redo
        wheel = ToolWheelView(this)
        wireWheel(wheel)
        wheel.onSlotTap = { i -> onWheelSlot(i) }
        wheel.onSlotLongPress = { i -> assignWheelSlot(i) }
        wheel.scaleFactor = prefs.sp.getFloat("wheel_scale", if (dm.widthPixels / dm.density < 600) 0.8f else 1f)
        wheel.onScaleChanged = { f -> prefs.put("wheel_scale", f) }
        wheel.onDrag = { dx, dy -> wheelGroup.translationX += dx; wheelGroup.translationY += dy }
        wheel.onDragEnd = { onWheelDropped() }
        wheelGroup = UI.hbox(this)
        wheelGroup.gravity = Gravity.BOTTOM
        wheelGroup.addView(wheel)
        val ur = UI.vbox(this)
        ur.setPadding(0, 0, 0, d(6f))
        undoBtn = IconButton(this, "undo", 46f).apply { background = UI.ripple(this@CanvasActivity, UI.rounded(0xFAFAFAFB.toInt(), UI.dpf(this@CanvasActivity, 23f), d(1f), 0x14000000)); elevation = UI.dpf(this@CanvasActivity, 3f); setOnClickListener { canvas.undo() } }
        redoBtn = IconButton(this, "redo", 46f).apply { background = UI.ripple(this@CanvasActivity, UI.rounded(0xFAFAFAFB.toInt(), UI.dpf(this@CanvasActivity, 23f), d(1f), 0x14000000)); elevation = UI.dpf(this@CanvasActivity, 3f); setOnClickListener { canvas.redo() } }
        ur.addView(undoBtn, LinearLayout.LayoutParams(UI.WRAP, UI.WRAP).apply { bottomMargin = d(8f) })
        ur.addView(redoBtn)
        wheelGroup.addView(ur, LinearLayout.LayoutParams(UI.WRAP, UI.WRAP).apply { marginStart = d(2f) })
        root.addView(wheelGroup, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or Gravity.START))
        wheelGroup.translationX = prefs.sp.getFloat("wheel_x", UI.dpf(this, 8f))
        wheelGroup.translationY = prefs.sp.getFloat("wheel_y", UI.dpf(this, 56f))

        // ---- floating layers menu
        sidebarHolder = FrameLayout(this)
        root.addView(sidebarHolder, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or Gravity.END).apply { topMargin = d(60f); marginEnd = d(10f) })
        sidebarHolder.translationX = prefs.sp.getFloat("layers_x", 0f)
        sidebarHolder.translationY = prefs.sp.getFloat("layers_y", 0f)
        if (prefs.sp.getBoolean("sidebar", dm.widthPixels / dm.density >= 600)) showSidebar(true)

        // ---- selection bar
        selectionBar = UI.hbox(this)
        selectionBar.background = UI.card(this)
        selectionBar.elevation = UI.dpf(this, 6f)
        selectionBar.visibility = View.GONE
        fun sel(icon: String, f: () -> Unit) = selectionBar.addView(IconButton(this, icon).apply { setOnClickListener { f() } })
        selectionBar.addView(UI.label(this, "Selection", 12f, UI.SUB, true).apply { setPadding(d(12f), 0, d(4f), 0) })
        sel("sparkle") { if (!canvas.refineSelection()) showHint("That doesn't look like handwriting") else showHint("Handwriting refined") }
        sel("trash") { canvas.deleteSelection() }
        sel("copy") { canvas.duplicateSelection() }
        sel("recolor") { canvas.recolorSelection(slotColors[currentSlot]) }
        sel("front") { canvas.bringSelectionToFront() }
        sel("selectall") { canvas.selectAllInLayer() }
        sel("check") { canvas.clearSelection() }
        root.addView(selectionBar, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(24f) })

        // ---- hint
        hint = UI.label(this, "", 13f, Color.WHITE, true).apply {
            background = UI.rounded(0xE62A2B31.toInt(), UI.dpf(this@CanvasActivity, 18f))
            setPadding(d(16f), d(8f), d(16f), d(8f))
            visibility = View.GONE
        }
        root.addView(hint, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(90f) })

        panelHost = FrameLayout(this)
        root.addView(panelHost, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        setContentView(root)
        val bar = prefs.sp.getInt("bar_mode", 0)
        if (bar != 0) setBarMode(true, bar < 0)
        updateUndo()
        onCameraChanged()
    }

    private fun wireWheel(w: ToolWheelView) {
        w.sizeLabel = { t -> if (canvas.tool == CanvasView.Tool.ERASER) "${BrushPanel.tToSize(t).toInt()}px" else fmtSize(BrushPanel.tToSize(t)) }
        w.onCenterTap = { togglePanel("color") }
        w.onCenterLongPress = { openBrushPanel() }
        w.onSize = { t ->
            val s = BrushPanel.tToSize(t)
            if (canvas.tool == CanvasView.Tool.ERASER) { canvas.eraserSizePx = s; prefs.put("eraser_size", s) }
            else slots[currentSlot].size = s
            canvas.invalidate(); refreshToolUi()
        }
        w.onOpacity = { o -> slots[currentSlot].opacity = o }
        w.onSmoothing = { v -> slots[currentSlot].smoothing = v; if (v >= 0.995f) showHint("Smoothing 100% · draws straight lines") }
    }

    private fun fmtSize(s: Float) = if (s >= 10) "${s.toInt()}" else "%.1f".format(s)

    // ------------------------------------------------------------------ wheel tools
    private fun loadWheelTools() {
        wheelTools.clear()
        val saved = prefs.sp.getString("wheel_tools", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        wheelTools.addAll(saved?.takeIf { it.size == 8 } ?: listOf(0, 2, 1, 3, 5, 8, 100, 101))
    }

    private fun toolIcon(id: Int) = when (id) { 100 -> "eraser"; 101 -> "lasso"; 102 -> "eyedropper"; 103 -> "hand"; else -> slots[id].type.icon }
    private fun toolLabel(id: Int) = when (id) { 100 -> "Eraser"; 101 -> "Selection"; 102 -> "Eyedropper"; 103 -> "Pan"; else -> slots[id].type.label }

    private fun isActive(id: Int): Boolean = when (id) {
        100 -> canvas.tool == CanvasView.Tool.ERASER
        101 -> canvas.tool == CanvasView.Tool.LASSO
        102 -> canvas.tool == CanvasView.Tool.EYEDROPPER
        103 -> canvas.tool == CanvasView.Tool.PAN
        else -> canvas.tool == CanvasView.Tool.BRUSH && currentSlot == id
    }

    private fun onWheelSlot(i: Int) {
        val id = wheelTools.getOrNull(i) ?: return
        when (id) {
            100 -> if (canvas.tool == CanvasView.Tool.ERASER) togglePanel("eraser") else setTool(CanvasView.Tool.ERASER)
            101 -> setTool(CanvasView.Tool.LASSO)
            102 -> setTool(CanvasView.Tool.EYEDROPPER)
            103 -> setTool(CanvasView.Tool.PAN)
            else -> selectSlot(id, openSettingsIfSame = true)
        }
    }

    private fun assignWheelSlot(i: Int) {
        val ids = slots.indices.toList() + listOf(100, 101, 102, 103)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Tool for this slot")
            .setItems(ids.map { toolLabel(it) }.toTypedArray()) { _, w ->
                wheelTools[i] = ids[w]
                prefs.put("wheel_tools", wheelTools.joinToString(","))
                onWheelSlot(i)
                rebuildBar()
                refreshToolUi()
            }.show()
    }

    private fun onWheelDropped() {
        val edge = UI.dpf(this, 24f)
        val x = wheelGroup.translationX
        val maxX = root.width - wheelGroup.width.toFloat()
        wheelGroup.translationX = x.coerceIn(-wheel.width * 0.3f, maxX + wheel.width * 0.3f)
        wheelGroup.translationY = wheelGroup.translationY.coerceIn(UI.dpf(this, 48f), root.height - wheelGroup.height * 0.6f)
        when {
            x < edge -> setBarMode(true, left = true)
            x > maxX - edge -> setBarMode(true, left = false)
            else -> { prefs.put("wheel_x", wheelGroup.translationX); prefs.put("wheel_y", wheelGroup.translationY) }
        }
    }

    /** Tool Bar mode: the wheel docks to a screen edge as a vertical bar. */
    fun setBarMode(on: Boolean, left: Boolean = true) {
        barGroup?.let { root.removeView(it) }
        barGroup = null; miniWheel = null; barSlots.clear()
        if (!on) {
            prefs.put("bar_mode", 0)
            wheelGroup.visibility = if (uiHidden) View.GONE else View.VISIBLE
            wheelGroup.translationX = UI.dpf(this, 8f); wheelGroup.translationY = UI.dpf(this, 56f)
            prefs.put("wheel_x", wheelGroup.translationX); prefs.put("wheel_y", wheelGroup.translationY)
            refreshToolUi(); return
        }
        prefs.put("bar_mode", if (left) -1 else 1)
        wheelGroup.visibility = View.GONE
        val col = UI.vbox(this)
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(UI.dp(this, 3f), UI.dp(this, 4f), UI.dp(this, 3f), UI.dp(this, 6f))
        val mw = ToolWheelView(this).apply { compact = true; scaleFactor = 0.5f }
        wireWheel(mw)
        miniWheel = mw
        col.addView(mw)
        for ((i, id) in wheelTools.withIndex()) {
            val sv = com.akshit.folio.ui.SlotView(this, toolIcon(id))
            sv.setOnClickListener { onWheelSlot(i) }
            sv.setOnLongClickListener { assignWheelSlot(i); true }
            barSlots.add(sv); col.addView(sv)
        }
        col.addView(View(this).apply { setBackgroundColor(UI.DIV) }, LinearLayout.LayoutParams(UI.dp(this, 36f), UI.dp(this, 1f)).apply { topMargin = UI.dp(this@CanvasActivity, 4f); bottomMargin = UI.dp(this@CanvasActivity, 4f) })
        col.addView(IconButton(this, "undo").apply { setOnClickListener { canvas.undo() } })
        col.addView(IconButton(this, "redo").apply { setOnClickListener { canvas.redo() } })
        col.addView(IconButton(this, "wheel").apply { contentDescription = "wheel"; setOnClickListener { setBarMode(false) } })
        val scroll = android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = UI.card(this@CanvasActivity); elevation = UI.dpf(this@CanvasActivity, 4f)
            addView(col)
        }
        root.addView(scroll, root.indexOfChild(panelHost), FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or (if (left) Gravity.START else Gravity.END)).apply {
            topMargin = UI.dp(this@CanvasActivity, 56f); bottomMargin = UI.dp(this@CanvasActivity, 8f)
            marginStart = UI.dp(this@CanvasActivity, 8f); marginEnd = UI.dp(this@CanvasActivity, 8f)
        })
        barGroup = scroll
        if (uiHidden) scroll.visibility = View.GONE
        refreshToolUi()
    }

    private fun rebuildBar() {
        val mode = prefs.sp.getInt("bar_mode", 0)
        if (mode != 0) setBarMode(true, mode < 0)
    }

    val isBarMode get() = barGroup != null

    private fun showSidebar(show: Boolean) {
        sidebarHolder.removeAllViews()
        sidebar = null
        if (::layersBtn.isInitialized) layersBtn.isSelected = show
        if (show) {
            val sb = com.akshit.folio.ui.LayerSidebar(this, canvas, { togglePanel("layers") }, {
                val d = doc
                val l = com.akshit.folio.model.Layer("Layer ${d.layers.size + 1}")
                val at = d.activeLayer + 1
                canvas.history.run(com.akshit.folio.model.LambdaCommand({ d.layers.add(at, l); d.activeLayer = at }, { d.layers.remove(l); d.activeLayer = (at - 1).coerceAtLeast(0) }))
            })
            sb.onDrag = { dx, dy -> sidebarHolder.translationX += dx; sidebarHolder.translationY += dy }
            sb.onDragEnd = { prefs.put("layers_x", sidebarHolder.translationX); prefs.put("layers_y", sidebarHolder.translationY) }
            sidebar = sb
            sidebarHolder.addView(sb, FrameLayout.LayoutParams(UI.dp(this, 104f), UI.WRAP))
            sb.refresh()
        }
    }

    fun toggleSidebar() {
        val show = sidebar == null
        prefs.put("sidebar", show)
        showSidebar(show)
    }

    // ------------------------------------------------------------------ brushes & tools
    fun selectSlot(i: Int, openSettingsIfSame: Boolean = true) {
        if (i == currentSlot && canvas.tool == CanvasView.Tool.BRUSH && openSettingsIfSame) { openBrushPanel(); return }
        if (i != currentSlot) lastSlot = currentSlot
        currentSlot = i
        canvas.brush = slots[i]
        canvas.color = slotColors[i]
        canvas.tool = CanvasView.Tool.BRUSH
        prefs.put("cur_slot", i)
        refreshToolUi()
    }

    fun setTool(t: CanvasView.Tool) {
        canvas.tool = t
        if (t == CanvasView.Tool.ERASER) closePanel()
        refreshToolUi()
        if (t == CanvasView.Tool.LASSO) showHint("Draw around strokes to select them")
        if (t == CanvasView.Tool.EYEDROPPER) showHint("Touch the canvas to pick a color")
    }

    private fun refreshToolUi() {
        if (!::wheel.isInitialized) return
        val t = canvas.tool
        val s = slots[currentSlot]
        val slotList = wheelTools.map { id ->
            ToolWheelView.Slot(toolIcon(id), toolLabel(id), if (id < 100) slotColors[id] else null,
                if (id < 100) fmtSize(slots[id].size) else if (id == 100) "${canvas.eraserSizePx.toInt()}" else "")
        }
        val active = wheelTools.indexOfFirst { isActive(it) }
        for (w in listOfNotNull(wheel, miniWheel)) {
            w.slots = if (w === miniWheel) emptyList() else slotList
            w.activeSlot = active
            w.eraserMode = t == CanvasView.Tool.ERASER
            w.color = slotColors[currentSlot]
            w.sizeT = BrushPanel.sizeToT(if (t == CanvasView.Tool.ERASER) canvas.eraserSizePx else s.size)
            w.opacity = s.opacity
            w.smoothing = s.smoothing
        }
        barSlots.forEachIndexed { k, b ->
            val id = wheelTools[k]
            b.isSelected = isActive(id)
            b.dotColor = if (id < 100) slotColors[id] else null
            b.sizeText = slotList[k].sizeText
        }
    }

    fun setColor(c: Int) {
        slotColors[currentSlot] = c
        canvas.color = c
        prefs.saveSlotColor(currentSlot, c)
        refreshToolUi()
    }

    private fun openBrushPanel() {
        val slot = currentSlot
        showPanel("brush", BrushPanel(this, slots[slot], slotColors[slot], doc.background.paperColor, {
            refreshToolUi()
        }, { prefs.resetBrushSlot(slot) }, { closePanel() }))
    }

    // ------------------------------------------------------------------ panels
    private var panelName: String? = null

    fun togglePanel(name: String) {
        if (panelName == name) { closePanel(); return }
        when (name) {
            "color" -> {
                val p = ColorPickerPanel(this, prefs, slotColors[currentSlot], { c ->
                    setColor(c)
                    if (canvas.selection.isNotEmpty()) canvas.recolorSelection(c)
                }, { closePanel() })
                colorPanel = p
                showPanel(name, p)
            }
            "layers" -> showPanel(name, LayersPanel(this, canvas, { sidebar?.refresh() }, { closePanel() }), nearWheel = false)
            "bg" -> showPanel(name, BackgroundPanel(this, doc, prefs, { canvas.invalidate() }, { closePanel() }), nearWheel = false)
            "precision" -> showPanel(name, precisionPanel(), nearWheel = false)
            "eraser" -> {
                val p = Panel(this, "Eraser", { closePanel() })
                p.body.addView(UI.toggle(this, "Erase whole strokes", "Off = precise vector eraser that slices strokes", canvas.eraserWhole) {
                    canvas.eraserWhole = it; prefs.put("eraser_whole", it)
                })
                p.body.addView(UI.slider(this, "Eraser size", 0f, 1f, BrushPanel.sizeToT(canvas.eraserSizePx), { "${BrushPanel.tToSize(it).toInt()} px" }) {
                    canvas.eraserSizePx = BrushPanel.tToSize(it); prefs.put("eraser_size", canvas.eraserSizePx); refreshToolUi()
                })
                showPanel(name, p, 0.4f)
            }
        }
    }

    private fun precisionPanel(): Panel {
        val p = Panel(this, "Precision", { closePanel() })
        p.body.addView(UI.section(this, "Grid"))
        val row = UI.hbox(this)
        val quick = listOf(com.akshit.folio.model.Pattern.NONE to "Off", com.akshit.folio.model.Pattern.DOTS to "Dots", com.akshit.folio.model.Pattern.GRID to "Grid",
            com.akshit.folio.model.Pattern.ISOMETRIC to "Iso", com.akshit.folio.model.Pattern.LINES to "Lines", com.akshit.folio.model.Pattern.PERSPECTIVE_2 to "Persp")
        val chips = ArrayList<TextView>()
        for ((pat, label) in quick) {
            val c = UI.label(this, label, 12.5f, UI.TEXT, true).apply {
                gravity = Gravity.CENTER; setPadding(0, UI.dp(this@CanvasActivity, 8f), 0, UI.dp(this@CanvasActivity, 8f))
            }
            fun paint() { c.background = UI.rounded(if (doc.background.pattern == pat) 0x262F7CF6 else UI.PANEL2, UI.dpf(this, 8f)); c.setTextColor(if (doc.background.pattern == pat) UI.ACCENT else UI.TEXT) }
            paint()
            chips.add(c)
            row.addView(c, UI.lp(0, UI.WRAP, 1f).apply { marginEnd = UI.dp(this@CanvasActivity, 4f) })
        }
        // repaint all chips on any click
        chips.forEachIndexed { i, c -> c.setOnClickListener { doc.background.pattern = quick[i].first; canvas.invalidate()
            chips.forEachIndexed { k, o -> val on = quick[k].first == doc.background.pattern; o.background = UI.rounded(if (on) 0x262F7CF6 else UI.PANEL2, UI.dpf(this, 8f)); o.setTextColor(if (on) UI.ACCENT else UI.TEXT) } } }
        p.body.addView(row)
        p.body.addView(UI.button(this, "Paper, grid & perspective options…") { togglePanel("bg") }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = UI.dp(this@CanvasActivity, 8f)
        })
        p.body.addView(UI.section(this, "Guides & snapping"))
        p.body.addView(UI.toggle(this, "Shape guides", "Draw and hold to snap to a perfect line, circle, ellipse, arc, rectangle or polygon", prefs.shapeHold) { prefs.put(Prefs.K_SHAPE_HOLD, it) })
        p.body.addView(UI.toggle(this, "Refine handwriting", "Straighten and tidy writing when you pause", prefs.refineAuto) { prefs.put(Prefs.K_REFINE, it) })
        p.body.addView(UI.toggle(this, "Canvas rotation", "Two-finger rotate (snaps to 0° / 90°)", prefs.rotateEnabled) { prefs.put(Prefs.K_ROTATE, it) })
        p.body.addView(UI.label(this, "Tip: set Smoothing to 100% on the wheel to draw perfectly straight lines.", 12f, UI.SUB).apply { setPadding(UI.dp(this@CanvasActivity, 4f), UI.dp(this@CanvasActivity, 10f), 0, 0) })
        return p
    }

    fun showPanel(name: String, p: Panel, heightFraction: Float = 0.86f, nearWheel: Boolean = true) {
        closePanel()
        panelName = name
        if (::precisionBtn.isInitialized) { precisionBtn.isSelected = name == "precision"; paperBtn.isSelected = name == "bg" }
        currentPanel = p
        p.alpha = 0f; p.translationY = UI.dpf(this, 12f)
        val lp = panelLayoutParams(this, !prefs.leftHanded, heightFraction, nearWheel)
        p.maxHeightPx = minOf(lp.height, resources.displayMetrics.heightPixels - lp.topMargin - UI.dp(this, 16f))
        lp.height = UI.WRAP
        panelHost.addView(p, lp)
        p.animate().alpha(1f).translationY(0f).setDuration(160).start()
    }

    fun closePanel() {
        colorPanel?.commitRecent(); colorPanel = null
        currentPanel?.let { panelHost.removeView(it) }
        currentPanel = null; panelName = null
        if (::precisionBtn.isInitialized) { precisionBtn.isSelected = false; paperBtn.isSelected = false }
        persistSlots()
    }

    val openPanelName get() = panelName
    fun findPanel(): Panel? = currentPanel
    fun togglePanelBrush() = openBrushPanel()

    // ------------------------------------------------------------------ listener
    override fun onHistoryChanged() {
        if (!::root.isInitialized) return
        updateUndo()
        handler.removeCallbacks(autosave)
        handler.postDelayed(autosave, prefs.autosaveSec * 1000L)
        if (panelName == "layers") (currentPanel as? LayersPanel)?.build()
        handler.removeCallbacks(sidebarRefresh)
        handler.postDelayed(sidebarRefresh, 450)
    }

    override fun onColorPicked(color: Int) {
        setColor(color)
        if (canvas.tool == CanvasView.Tool.EYEDROPPER) selectSlot(currentSlot, openSettingsIfSame = false)
        showHint("Picked ${com.akshit.folio.ui.ColorUtil.hex(color)}")
    }

    override fun onAction(action: GestureAction) {
        when (action) {
            GestureAction.TOGGLE_ERASER -> if (canvas.tool == CanvasView.Tool.ERASER) selectSlot(currentSlot, false) else setTool(CanvasView.Tool.ERASER)
            GestureAction.TOGGLE_UI -> toggleUi()
            GestureAction.EYEDROPPER -> setTool(CanvasView.Tool.EYEDROPPER)
            GestureAction.LASSO -> setTool(CanvasView.Tool.LASSO)
            GestureAction.PAN -> setTool(CanvasView.Tool.PAN)
            GestureAction.COLOR_PICKER -> togglePanel("color")
            GestureAction.BRUSH_SETTINGS -> openBrushPanel()
            GestureAction.NEXT_BRUSH -> selectSlot((currentSlot + 1) % slots.size, false)
            GestureAction.PREV_BRUSH -> selectSlot((currentSlot - 1 + slots.size) % slots.size, false)
            GestureAction.TOGGLE_LAST_BRUSH -> selectSlot(lastSlot, false)
            else -> canvas.performAction(action)
        }
    }

    override fun onToolChanged() { if (::wheel.isInitialized) refreshToolUi() }

    override fun onCameraChanged() {
        if (!::zoomPill.isInitialized) return
        val pct = canvas.cam.scale * 100
        val z = when {
            pct >= 10000 -> "%.0fk%%".format(pct / 1000)
            pct >= 10 -> "%.0f%%".format(pct)
            pct >= 0.1 -> "%.1f%%".format(pct)
            else -> "%.2e%%".format(pct)
        }
        val r = canvas.rotationDegrees()
        zoomPill.text = if (r != 0) "$z  ·  $r°" else z
    }

    override fun onPenDetected() {
        showHint("Pen detected · pen draws, fingers pan / zoom")
    }

    override fun onSelectionChanged(count: Int) {
        if (!::selectionBar.isInitialized) return
        selectionBar.visibility = if (count > 0 && !uiHidden) View.VISIBLE else View.GONE
        if (count > 0) showHint("$count stroke${if (count == 1) "" else "s"} selected · drag to move, handles scale/rotate")
    }

    override fun onShapeRecognized(kind: ShapeRecognizer.Kind) {
        showHint(kind.name.lowercase().replaceFirstChar { it.uppercase() })
    }

    // ------------------------------------------------------------------ helpers
    private fun updateUndo() {
        if (!::undoBtn.isInitialized) return
        undoBtn.enabledLook = canvas.history.canUndo()
        redoBtn.enabledLook = canvas.history.canRedo()
    }

    fun showHint(text: String) {
        hint.text = text
        hint.visibility = View.VISIBLE
        hint.alpha = 1f
        hint.animate().cancel()
        hint.animate().alpha(0f).setStartDelay(1400).setDuration(400).withEndAction { hint.visibility = View.GONE }.start()
    }

    fun toggleUi() {
        uiHidden = !uiHidden
        val v = if (uiHidden) View.GONE else View.VISIBLE
        statusBar.visibility = v
        if (barGroup == null) wheelGroup.visibility = v
        barGroup?.visibility = v
        sidebarHolder.visibility = v
        if (uiHidden) { closePanel(); selectionBar.visibility = View.GONE }
        else if (canvas.selection.isNotEmpty()) selectionBar.visibility = View.VISIBLE
    }

    val isUiHidden get() = uiHidden

    private fun renameDoc(tv: TextView) {
        val et = android.widget.EditText(this).apply { setText(doc.name); setTextColor(UI.TEXT) }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("Rename drawing").setView(et)
            .setPositiveButton("OK") { _, _ -> doc.name = et.text.toString().ifBlank { doc.name }; tv.text = doc.name; save(false) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun persistSlots() {
        slots.forEachIndexed { i, s -> prefs.saveBrushSlot(i, s) }
    }

    fun save(withThumb: Boolean) {
        canvas.storeCamera()
        persistSlots()
        try {
            store.save(doc, if (withThumb) canvas.thumbnail() else null)
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showExport() {
        val options = arrayOf("PNG with paper", "PNG transparent", "SVG (vector)", "PNG with paper · 4K")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("Export").setItems(options) { _, which ->
            when (which) {
                0 -> exportPng(canvas.exportPng(2400, true))
                1 -> exportPng(canvas.exportPng(2400, false))
                2 -> exportSvg()
                3 -> exportPng(canvas.exportPng(3840, true))
            }
        }.show()
    }

    private fun exportPng(bmp: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${doc.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Folio")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("No media store")
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            showHint("Saved to Pictures/Folio")
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportSvg() {
        try {
            val svg = SvgExporter.export(doc)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "${doc.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}_${System.currentTimeMillis()}.svg")
                put(MediaStore.Downloads.MIME_TYPE, "image/svg+xml")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Folio")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("No media store")
            contentResolver.openOutputStream(uri)?.use { it.write(svg.toByteArray()) }
            showHint("Saved to Downloads/Folio")
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestHighRefreshRate() {
        if (!prefs.highRefresh) return
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
        display ?: return
        val cur = display.mode
        val best = display.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }

    private fun goFullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val ctrl = event.isCtrlPressed
            when {
                prefs.volumeKeys && event.keyCode == KeyEvent.KEYCODE_VOLUME_UP -> { onAction(prefs.action(Prefs.G_VOL_UP)); return true }
                prefs.volumeKeys && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> { onAction(prefs.action(Prefs.G_VOL_DOWN)); return true }
                ctrl && event.keyCode == KeyEvent.KEYCODE_Z && event.isShiftPressed -> { canvas.redo(); return true }
                ctrl && event.keyCode == KeyEvent.KEYCODE_Z -> { canvas.undo(); return true }
                ctrl && event.keyCode == KeyEvent.KEYCODE_Y -> { canvas.redo(); return true }
                event.keyCode == KeyEvent.KEYCODE_E && !ctrl -> { onAction(GestureAction.TOGGLE_ERASER); return true }
                event.keyCode == KeyEvent.KEYCODE_TAB -> { toggleUi(); return true }
            }
        } else if (event.action == KeyEvent.ACTION_UP && prefs.volumeKeys &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        canvas.invalidateContent()
        if (::zoomPill.isInitialized) zoomPill.visibility = if (prefs.showZoom) View.VISIBLE else View.INVISIBLE
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autosave)
        if (demo == null || isFinishing) save(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentPanel != null) { closePanel(); return }
        if (canvas.selection.isNotEmpty()) { canvas.clearSelection(); return }
        save(true)
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        @Suppress("unused") val ALL_TYPES = BrushType.entries
    }
}
