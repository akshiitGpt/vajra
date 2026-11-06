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
    private val slotButtons = ArrayList<IconButton>()
    private lateinit var eraserBtn: IconButton
    private lateinit var lassoBtn: IconButton
    private lateinit var dropperBtn: IconButton
    private lateinit var handBtn: IconButton
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
    private fun buildUi() {
        val left = !prefs.leftHanded
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.addView(canvas, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))

        // ---- top bar
        topBar = UI.hbox(this)
        topBar.background = UI.card(this)
        topBar.elevation = UI.dpf(this, 6f)
        topBar.setPadding(UI.dp(this, 4f), 0, UI.dp(this, 4f), 0)
        topBar.addView(IconButton(this, "gallery").apply { setOnClickListener { save(true); finish() } })
        val title = UI.label(this, doc.name, 14f, UI.TEXT, true).apply {
            setPadding(UI.dp(this@CanvasActivity, 4f), 0, UI.dp(this@CanvasActivity, 10f), 0)
            maxWidth = UI.dp(this@CanvasActivity, 160f); maxLines = 1
            setOnClickListener { renameDoc(this) }
        }
        topBar.addView(title)
        undoBtn = IconButton(this, "undo").apply { setOnClickListener { canvas.undo() } }
        redoBtn = IconButton(this, "redo").apply { setOnClickListener { canvas.redo() } }
        topBar.addView(undoBtn); topBar.addView(redoBtn)
        topBar.addView(IconButton(this, "layers").apply { setOnClickListener { togglePanel("layers") } })
        topBar.addView(IconButton(this, "grid").apply { setOnClickListener { togglePanel("bg") } })
        topBar.addView(IconButton(this, "zoomfit").apply { setOnClickListener { canvas.zoomFit() } })
        topBar.addView(IconButton(this, "export").apply { setOnClickListener { showExport() } })
        topBar.addView(IconButton(this, "penlab").apply { setOnClickListener { startActivity(Intent(this@CanvasActivity, PenLabActivity::class.java)) } })
        topBar.addView(IconButton(this, "settings").apply { setOnClickListener { startActivity(Intent(this@CanvasActivity, SettingsActivity::class.java)) } })
        root.addView(topBar, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = UI.dp(this@CanvasActivity, 10f) })

        // ---- tool bar (brush slots + tools)
        toolBar = UI.vbox(this)
        toolBar.setPadding(UI.dp(this, 4f), UI.dp(this, 6f), UI.dp(this, 4f), UI.dp(this, 6f))
        slots.forEachIndexed { i, s ->
            val b = IconButton(this, s.type.icon)
            b.dotColor = slotColors[i]
            b.setOnClickListener { selectSlot(i) }
            b.setOnLongClickListener { selectSlot(i, openSettingsIfSame = false); openBrushPanel(); true }
            slotButtons.add(b); toolBar.addView(b)
        }
        toolBar.addView(View(this).apply { setBackgroundColor(UI.DIV) }, LinearLayout.LayoutParams(UI.dp(this, 32f), UI.dp(this, 1f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = UI.dp(this@CanvasActivity, 4f); bottomMargin = UI.dp(this@CanvasActivity, 4f) })
        eraserBtn = IconButton(this, "eraser").apply { setOnClickListener { if (canvas.tool == CanvasView.Tool.ERASER) togglePanel("eraser") else setTool(CanvasView.Tool.ERASER) } }
        lassoBtn = IconButton(this, "lasso").apply { setOnClickListener { setTool(CanvasView.Tool.LASSO) } }
        dropperBtn = IconButton(this, "eyedropper").apply { setOnClickListener { setTool(CanvasView.Tool.EYEDROPPER) } }
        handBtn = IconButton(this, "hand").apply { setOnClickListener { setTool(CanvasView.Tool.PAN) } }
        for (b in listOf(eraserBtn, lassoBtn, dropperBtn, handBtn)) toolBar.addView(b)
        val toolScroll = android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = UI.card(this@CanvasActivity)
            elevation = UI.dpf(this@CanvasActivity, 6f)
            addView(toolBar)
        }
        root.addView(toolScroll, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, (if (left) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL).apply {
            marginStart = UI.dp(this@CanvasActivity, 10f); marginEnd = UI.dp(this@CanvasActivity, 10f)
            topMargin = UI.dp(this@CanvasActivity, 70f); bottomMargin = UI.dp(this@CanvasActivity, 170f)
        })

        // ---- tool wheel
        wheel = ToolWheelView(this)
        wheel.sizeLabel = { t -> if (canvas.tool == CanvasView.Tool.ERASER) "${BrushPanel.tToSize(t).toInt()}px" else "%.1f".format(BrushPanel.tToSize(t)) }
        wheel.onCenterTap = { togglePanel("color") }
        wheel.onCenterLongPress = { openBrushPanel() }
        wheel.onSize = { t ->
            val s = BrushPanel.tToSize(t)
            if (canvas.tool == CanvasView.Tool.ERASER) { canvas.eraserSizePx = s; prefs.put("eraser_size", s) }
            else { slots[currentSlot].size = s }
            canvas.invalidate()
        }
        wheel.onOpacity = { o -> slots[currentSlot].opacity = o }
        wheel.onSmoothing = { v -> slots[currentSlot].smoothing = v }
        root.addView(wheel, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or (if (left) Gravity.START else Gravity.END)).apply {
            marginStart = UI.dp(this@CanvasActivity, 6f); marginEnd = UI.dp(this@CanvasActivity, 6f); bottomMargin = UI.dp(this@CanvasActivity, 8f)
        })

        // ---- zoom pill
        zoomPill = UI.label(this, "100%", 12.5f, UI.TEXT, true).apply {
            background = UI.rounded(0xCC1E1F23.toInt(), UI.dpf(this@CanvasActivity, 16f))
            setPadding(UI.dp(this@CanvasActivity, 14f), UI.dp(this@CanvasActivity, 7f), UI.dp(this@CanvasActivity, 14f), UI.dp(this@CanvasActivity, 7f))
            setOnClickListener { canvas.zoomReset() }
            setOnLongClickListener { canvas.zoomFit(); true }
            visibility = if (prefs.showZoom) View.VISIBLE else View.GONE
        }
        root.addView(zoomPill, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = UI.dp(this@CanvasActivity, 16f) })

        // ---- selection bar
        selectionBar = UI.hbox(this)
        selectionBar.background = UI.card(this)
        selectionBar.elevation = UI.dpf(this, 6f)
        selectionBar.visibility = View.GONE
        fun sel(icon: String, f: () -> Unit) = selectionBar.addView(IconButton(this, icon).apply { setOnClickListener { f() } })
        selectionBar.addView(UI.label(this, "Selection", 12f, UI.SUB, true).apply { setPadding(UI.dp(this@CanvasActivity, 12f), 0, UI.dp(this@CanvasActivity, 4f), 0) })
        sel("trash") { canvas.deleteSelection() }
        sel("copy") { canvas.duplicateSelection() }
        sel("recolor") { canvas.recolorSelection(slotColors[currentSlot]) }
        sel("front") { canvas.bringSelectionToFront() }
        sel("selectall") { canvas.selectAllInLayer() }
        sel("check") { canvas.clearSelection() }
        root.addView(selectionBar, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = UI.dp(this@CanvasActivity, 70f) })

        // ---- hint
        hint = UI.label(this, "", 13f, Color.WHITE, true).apply {
            background = UI.rounded(0xDD2A2B31.toInt(), UI.dpf(this@CanvasActivity, 18f))
            setPadding(UI.dp(this@CanvasActivity, 16f), UI.dp(this@CanvasActivity, 8f), UI.dp(this@CanvasActivity, 16f), UI.dp(this@CanvasActivity, 8f))
            visibility = View.GONE
        }
        root.addView(hint, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = UI.dp(this@CanvasActivity, 60f) })

        panelHost = FrameLayout(this)
        root.addView(panelHost, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        setContentView(root)
        updateUndo()
        onCameraChanged()
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
        slotButtons.forEachIndexed { k, b -> b.isSelected = t == CanvasView.Tool.BRUSH && k == currentSlot; b.dotColor = slotColors[k] }
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
            "layers" -> showPanel(name, LayersPanel(this, canvas, { }, { closePanel() }))
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

    fun showPanel(name: String, p: Panel, heightFraction: Float = 0.86f) {
        closePanel()
        panelName = name
        currentPanel = p
        p.alpha = 0f; p.translationY = UI.dpf(this, 12f)
        panelHost.addView(p, panelLayoutParams(this, prefs.leftHanded, heightFraction))
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
        zoomPill.visibility = if (prefs.showZoom) v else View.GONE
        if (uiHidden) { closePanel(); selectionBar.visibility = View.GONE }
        else if (canvas.selection.isNotEmpty()) selectionBar.visibility = View.VISIBLE
    }

    val isUiHidden get() = uiHidden

    private fun renameDoc(tv: TextView) {
        val et = android.widget.EditText(this).apply { setText(doc.name); setTextColor(UI.TEXT) }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Rename drawing").setView(et)
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
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Export").setItems(options) { _, which ->
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
