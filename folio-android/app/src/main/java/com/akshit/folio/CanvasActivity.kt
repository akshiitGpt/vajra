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
    private lateinit var topBar: LinearLayout
    private lateinit var toolBar: LinearLayout
    private lateinit var wheel: ToolWheelView
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
    private val slotButtons = ArrayList<com.akshit.folio.ui.SlotView>()
    private lateinit var eraserBtn: com.akshit.folio.ui.SlotView
    private lateinit var lassoBtn: com.akshit.folio.ui.SlotView
    private lateinit var dropperBtn: com.akshit.folio.ui.SlotView
    private lateinit var handBtn: com.akshit.folio.ui.SlotView
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
     * Concepts-style chrome: tool wheel in the top corner with undo/redo and the brush strip
     * hanging below it, an action bar in the opposite top corner, and a docked layers column on
     * the far edge. Everything else is canvas. Mirrored for left-handed use.
     */
    private fun buildUi() {
        val start = !prefs.leftHanded
        val gStart = if (start) Gravity.START else Gravity.END
        val gEnd = if (start) Gravity.END else Gravity.START
        val dm = resources.displayMetrics
        val tablet = dm.widthPixels / dm.density >= 600
        root = FrameLayout(this)
        root.setBackgroundColor(Color.WHITE)
        root.addView(canvas, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        fun d(v: Float) = UI.dp(this, v)

        // ---- tool wheel (top corner)
        wheel = ToolWheelView(this)
        wheel.sizeLabel = { t -> if (canvas.tool == CanvasView.Tool.ERASER) "${BrushPanel.tToSize(t).toInt()}px" else "%.1f".format(BrushPanel.tToSize(t)) }
        wheel.onCenterTap = { togglePanel("color") }
        wheel.onCenterLongPress = { openBrushPanel() }
        wheel.onSize = { t ->
            val s = BrushPanel.tToSize(t)
            if (canvas.tool == CanvasView.Tool.ERASER) { canvas.eraserSizePx = s; prefs.put("eraser_size", s) }
            else { slots[currentSlot].size = s; slotButtons[currentSlot].sizeText = fmtSize(s) }
            canvas.invalidate()
        }
        wheel.onOpacity = { o -> slots[currentSlot].opacity = o }
        wheel.onSmoothing = { v -> slots[currentSlot].smoothing = v }
        root.addView(wheel, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or gStart).apply {
            marginStart = d(6f); marginEnd = d(6f); topMargin = d(6f)
        })

        // ---- undo / redo beside the wheel
        val ur = UI.vbox(this)
        ur.background = UI.card(this); ur.elevation = UI.dpf(this, 4f)
        undoBtn = IconButton(this, "undo", 44f).apply { setOnClickListener { canvas.undo() } }
        redoBtn = IconButton(this, "redo", 44f).apply { setOnClickListener { canvas.redo() } }
        ur.addView(undoBtn); ur.addView(redoBtn)
        root.addView(ur, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or gStart).apply {
            marginStart = d(160f); marginEnd = d(160f); topMargin = d(20f)
        })

        // ---- brush strip under the wheel
        toolBar = UI.vbox(this)
        toolBar.gravity = Gravity.CENTER_HORIZONTAL
        toolBar.setPadding(d(3f), d(4f), d(3f), d(4f))
        slots.forEachIndexed { i, s ->
            val b = com.akshit.folio.ui.SlotView(this, s.type.icon)
            b.dotColor = slotColors[i]
            b.sizeText = fmtSize(s.size)
            b.setOnClickListener { selectSlot(i) }
            b.setOnLongClickListener { selectSlot(i, openSettingsIfSame = false); openBrushPanel(); true }
            slotButtons.add(b); toolBar.addView(b)
        }
        toolBar.addView(View(this).apply { setBackgroundColor(UI.DIV) }, LinearLayout.LayoutParams(d(36f), d(1f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = d(4f); bottomMargin = d(4f) })
        fun tool(icon: String, label: String) = com.akshit.folio.ui.SlotView(this, icon).apply { sizeText = label }
        eraserBtn = tool("eraser", "Erase").apply { setOnClickListener { if (canvas.tool == CanvasView.Tool.ERASER) togglePanel("eraser") else setTool(CanvasView.Tool.ERASER) } }
        lassoBtn = tool("lasso", "Select").apply { setOnClickListener { setTool(CanvasView.Tool.LASSO) } }
        dropperBtn = tool("eyedropper", "Pick").apply { setOnClickListener { setTool(CanvasView.Tool.EYEDROPPER) } }
        handBtn = tool("hand", "Pan").apply { setOnClickListener { setTool(CanvasView.Tool.PAN) } }
        for (b in listOf(eraserBtn, lassoBtn, dropperBtn, handBtn)) toolBar.addView(b)
        val toolScroll = android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = UI.card(this@CanvasActivity)
            elevation = UI.dpf(this@CanvasActivity, 4f)
            addView(toolBar)
        }
        root.addView(toolScroll, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or gStart).apply {
            marginStart = d(12f); marginEnd = d(12f); topMargin = d(166f); bottomMargin = d(if (tablet) 12f else 76f)
        })

        // ---- action bar (opposite top corner)
        topBar = UI.hbox(this)
        topBar.background = UI.card(this)
        topBar.elevation = UI.dpf(this, 4f)
        topBar.setPadding(d(4f), 0, d(4f), 0)
        val title = UI.label(this, doc.name, 14f, UI.TEXT, true).apply {
            setPadding(d(12f), 0, d(6f), 0)
            maxWidth = d(170f); maxLines = 1
            setOnClickListener { renameDoc(this) }
        }
        topBar.addView(title)
        topBar.addView(IconButton(this, "grid").apply { setOnClickListener { togglePanel("bg") } })
        topBar.addView(IconButton(this, "layers").apply { setOnClickListener { toggleSidebar() } })
        topBar.addView(IconButton(this, "zoomfit").apply { setOnClickListener { canvas.zoomFit() } })
        topBar.addView(IconButton(this, "export").apply { setOnClickListener { showExport() } })
        topBar.addView(IconButton(this, "penlab").apply { setOnClickListener { startActivity(Intent(this@CanvasActivity, PenLabActivity::class.java)) } })
        topBar.addView(IconButton(this, "settings").apply { setOnClickListener { startActivity(Intent(this@CanvasActivity, SettingsActivity::class.java)) } })
        topBar.addView(IconButton(this, "gallery").apply { setOnClickListener { save(true); finish() } })
        if (tablet) root.addView(topBar, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or gEnd).apply {
            topMargin = d(12f); marginEnd = d(12f); marginStart = d(12f)
        }) else {
            // phones: action bar sits at the bottom so the wheel keeps the top corner
            title.visibility = View.GONE
            root.addView(topBar, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or gEnd).apply { bottomMargin = d(12f); marginEnd = d(8f); marginStart = d(8f) })
        }

        // ---- docked layers column
        sidebarHolder = FrameLayout(this)
        root.addView(sidebarHolder, FrameLayout.LayoutParams(UI.WRAP, UI.MATCH, gEnd).apply {
            topMargin = d(72f); bottomMargin = d(70f); marginEnd = d(12f); marginStart = d(12f)
        })
        if (tablet && prefs.sp.getBoolean("sidebar", true)) showSidebar(true)

        // ---- zoom pill
        zoomPill = UI.label(this, "100%", 12.5f, UI.TEXT, true).apply {
            background = UI.card(this@CanvasActivity)
            elevation = UI.dpf(this@CanvasActivity, 3f)
            setPadding(d(14f), d(7f), d(14f), d(7f))
            setOnClickListener { canvas.zoomReset() }
            setOnLongClickListener { canvas.zoomFit(); true }
            visibility = if (prefs.showZoom) View.VISIBLE else View.GONE
        }
        root.addView(zoomPill, if (tablet) FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(16f) }
            else FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or gEnd).apply { topMargin = d(20f); marginEnd = d(12f); marginStart = d(12f) })

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
        root.addView(selectionBar, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(64f) })

        // ---- hint
        hint = UI.label(this, "", 13f, Color.WHITE, true).apply {
            background = UI.rounded(0xE62A2B31.toInt(), UI.dpf(this@CanvasActivity, 18f))
            setPadding(d(16f), d(8f), d(16f), d(8f))
            visibility = View.GONE
        }
        root.addView(hint, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(120f) })

        panelHost = FrameLayout(this)
        root.addView(panelHost, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        setContentView(root)
        updateUndo()
        onCameraChanged()
    }

    private fun fmtSize(s: Float) = if (s >= 10) "${s.toInt()}" else "%.1f".format(s)

    private fun showSidebar(show: Boolean) {
        sidebarHolder.removeAllViews()
        sidebar = null
        if (show) {
            val sb = com.akshit.folio.ui.LayerSidebar(this, canvas, { togglePanel("layers") }, {
                val d = doc
                val l = com.akshit.folio.model.Layer("Layer ${d.layers.size + 1}")
                val at = d.activeLayer + 1
                canvas.history.run(com.akshit.folio.model.LambdaCommand({ d.layers.add(at, l); d.activeLayer = at }, { d.layers.remove(l); d.activeLayer = (at - 1).coerceAtLeast(0) }))
            })
            sidebar = sb
            sidebarHolder.addView(sb, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP))
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
        val t = canvas.tool
        slotButtons.forEachIndexed { k, b -> b.isSelected = t == CanvasView.Tool.BRUSH && k == currentSlot; b.dotColor = slotColors[k]; b.sizeText = fmtSize(slots[k].size) }
        eraserBtn.isSelected = t == CanvasView.Tool.ERASER
        lassoBtn.isSelected = t == CanvasView.Tool.LASSO
        dropperBtn.isSelected = t == CanvasView.Tool.EYEDROPPER
        handBtn.isSelected = t == CanvasView.Tool.PAN
        val s = slots[currentSlot]
        wheel.eraserMode = t == CanvasView.Tool.ERASER
        wheel.color = slotColors[currentSlot]
        wheel.sizeT = BrushPanel.sizeToT(if (t == CanvasView.Tool.ERASER) canvas.eraserSizePx else s.size)
        wheel.opacity = s.opacity
        wheel.smoothing = s.smoothing
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
            slotButtons[slot].invalidate(); refreshToolUi()
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
            "bg" -> showPanel(name, BackgroundPanel(this, doc, prefs, { canvas.invalidate() }, { closePanel() }))
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

    fun showPanel(name: String, p: Panel, heightFraction: Float = 0.86f, nearWheel: Boolean = true) {
        closePanel()
        panelName = name
        currentPanel = p
        p.alpha = 0f; p.translationY = UI.dpf(this, 12f)
        panelHost.addView(p, panelLayoutParams(this, !prefs.leftHanded, heightFraction, nearWheel))
        p.animate().alpha(1f).translationY(0f).setDuration(160).start()
    }

    fun closePanel() {
        colorPanel?.commitRecent(); colorPanel = null
        currentPanel?.let { panelHost.removeView(it) }
        currentPanel = null; panelName = null
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
        topBar.visibility = v
        (toolBar.parent as View).visibility = v
        wheel.visibility = v
        (undoBtn.parent as View).visibility = v
        sidebarHolder.visibility = v
        zoomPill.visibility = if (prefs.showZoom) v else View.GONE
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
        if (::zoomPill.isInitialized) zoomPill.visibility = if (prefs.showZoom && !uiHidden) View.VISIBLE else View.GONE
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
