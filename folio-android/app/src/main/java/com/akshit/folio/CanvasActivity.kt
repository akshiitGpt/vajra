package com.akshit.folio

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
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
import com.akshit.folio.brush.RenderKind
import com.akshit.folio.core.GestureAction
import com.akshit.folio.core.Prefs
import com.akshit.folio.core.ShapeRecognizer
import com.akshit.folio.model.Document
import com.akshit.folio.model.DocumentStore
import com.akshit.folio.model.LambdaCommand
import com.akshit.folio.model.Layer
import com.akshit.folio.render.SvgExporter
import com.akshit.folio.ui.BackgroundPanel
import com.akshit.folio.ui.BrushPanel
import com.akshit.folio.ui.ColorFanView
import com.akshit.folio.ui.IconButton
import com.akshit.folio.ui.LayersList
import com.akshit.folio.ui.LayersPanel
import com.akshit.folio.ui.Panel
import com.akshit.folio.ui.SettingsSheet
import com.akshit.folio.ui.ToolWheelView
import com.akshit.folio.ui.UI
import com.akshit.folio.ui.WheelPopup
import com.akshit.folio.ui.panelLayoutParams
import com.akshit.folio.view.CanvasView
import kotlin.math.ln
import kotlin.math.exp

class CanvasActivity : Activity(), CanvasView.Listener {
    lateinit var prefs: Prefs
    lateinit var store: DocumentStore
    lateinit var canvas: CanvasView
    lateinit var doc: Document

    private lateinit var root: FrameLayout
    private lateinit var statusLeft: LinearLayout
    private lateinit var statusRight: LinearLayout
    private lateinit var zoomText: TextView
    private lateinit var rotText: TextView
    private lateinit var lockBtn: IconButton
    lateinit var wheel: ToolWheelView
    private lateinit var wheelGroup: FrameLayout
    private var barGroup: View? = null
    private var barSlots = ArrayList<com.akshit.folio.ui.SlotView>()
    private var miniWheel: ToolWheelView? = null
    private lateinit var precisionBtn: IconButton
    private lateinit var layersBtn: IconButton
    private lateinit var libraryBtn: IconButton
    /** Tools on the wheel rim: 0..12 = brush slots, 100 eraser, 101 select, 102 eyedropper, 103 pan */
    private val wheelTools = ArrayList<Int>()
    private lateinit var selToolbar: LinearLayout
    private lateinit var selOptions: LinearLayout
    private lateinit var hint: TextView
    private lateinit var panelHost: FrameLayout
    private var currentPanel: Panel? = null
    var popup: View? = null; private set
    var colorFan: ColorFanView? = null; private set
    var sheet: SettingsSheet? = null; private set
    var layersList: LayersList? = null; private set
    private lateinit var layersHolder: FrameLayout

    val slots = ArrayList<BrushSpec>()
    private val slotColors = ArrayList<Int>()
    private val layersRefresh = Runnable { layersList?.refresh() }
    var currentSlot = 0; private set
    private var lastSlot = 0
    private var uiHidden = false
    private val handler = Handler(Looper.getMainLooper())
    private val autosave = Runnable { save(false) }
    private var demo: DemoDriver? = null
    private var selW = 100; private var selH = 100; private var selDeg = 0

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

    private fun d(v: Float) = UI.dp(this, v)
    private fun df(v: Float) = UI.dpf(this, v)

    // ------------------------------------------------------------------ layout
    /**
     * Workspace layout:
     *  - floating status clusters (no bar): ▦ | title · ⋮⋮⋮ precision · ≡ layers · ▲○ library
     *    on the left; zoom %, rotation lock + angle, Pen Lab, export, settings, help on the right
     *  - the Tool Wheel (with undo/redo in its rim gap), movable, pinch to resize, dock as bar
     *  - Layers list under the wheel, selection toolbar + options bar, settings sheet
     */
    private fun buildUi() {
        val dm = resources.displayMetrics
        root = FrameLayout(this)
        root.setBackgroundColor(Color.WHITE)
        root.addView(canvas, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        loadWheelTools()

        fun cluster(): LinearLayout = UI.hbox(this).apply {
            background = UI.rounded(0xE6F1F1F1.toInt(), df(10f))
            setPadding(d(4f), 0, d(4f), 0)
        }
        fun sbtn(icon: String, f: () -> Unit) = IconButton(this, icon, 44f, 0xFF262626.toInt()).apply { setOnClickListener { f() } }

        // ---- left status cluster
        statusLeft = UI.hbox(this)
        val c1 = cluster()
        c1.addView(sbtn("apps") { save(true); finish() })
        c1.addView(View(this).apply { setBackgroundColor(0x33000000) }, LinearLayout.LayoutParams(d(1f), d(22f)).apply { marginStart = d(2f); marginEnd = d(8f) })
        val title = UI.label(this, doc.name, 16f, 0xFF262626.toInt()).apply {
            setPadding(0, d(10f), d(12f), d(10f)); maxWidth = d(220f); maxLines = 1
            setOnClickListener { renameDoc(this) }
        }
        c1.addView(title)
        statusLeft.addView(c1)
        val c2 = cluster()
        precisionBtn = sbtn("dots9") { togglePanel("precision") }
        layersBtn = sbtn("stack") { toggleLayers() }
        libraryBtn = sbtn("shapes") { togglePanel("library") }
        c2.addView(precisionBtn); c2.addView(layersBtn); c2.addView(libraryBtn)
        statusLeft.addView(c2, LinearLayout.LayoutParams(UI.WRAP, UI.WRAP).apply { marginStart = d(10f) })
        root.addView(statusLeft, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or Gravity.START).apply { topMargin = d(8f); marginStart = d(14f) })

        // ---- right status cluster
        statusRight = UI.hbox(this)
        val c3 = cluster()
        zoomText = UI.label(this, "100%", 14f, 0xFF262626.toInt()).apply {
            setPadding(d(8f), d(10f), d(4f), d(10f))
            setOnClickListener { canvas.zoomReset() }
            setOnLongClickListener { canvas.zoomFit(); true }
        }
        lockBtn = IconButton(this, "lockc", 30f, 0xFF262626.toInt()).apply {
            setOnClickListener { prefs.put(Prefs.K_ROTATE, !prefs.rotateEnabled); onCameraChanged(); showHint(if (prefs.rotateEnabled) "Canvas rotation unlocked" else "Canvas rotation locked") }
        }
        rotText = UI.label(this, "0°", 14f, 0xFF262626.toInt()).apply { setPadding(0, d(10f), d(8f), d(10f)) }
        c3.addView(zoomText); c3.addView(lockBtn); c3.addView(rotText)
        statusRight.addView(c3)
        val c4 = cluster()
        c4.addView(sbtn("penlab") { startActivity(Intent(this, PenLabActivity::class.java)) })
        c4.addView(sbtn("export") { showExport() })
        c4.addView(sbtn("settings") { toggleSheet() })
        c4.addView(sbtn("help") { togglePanel("help") })
        statusRight.addView(c4, LinearLayout.LayoutParams(UI.WRAP, UI.WRAP).apply { marginStart = d(10f) })
        root.addView(statusRight, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or Gravity.END).apply { topMargin = d(8f); marginEnd = d(14f) })

        // ---- layers list (under the wheel)
        layersHolder = FrameLayout(this)
        root.addView(layersHolder, FrameLayout.LayoutParams(d(260f), UI.MATCH, Gravity.START).apply { topMargin = d(330f); bottomMargin = d(12f); marginStart = d(12f) })

        // ---- tool wheel
        wheel = ToolWheelView(this)
        wireWheel(wheel)
        wheel.onSlotTap = { i -> onWheelSlot(i) }
        wheel.onSlotLongPress = { i -> assignWheelSlot(i) }
        wheel.onUndo = { canvas.undo() }
        wheel.onRedo = { canvas.redo() }
        wheel.scaleFactor = prefs.sp.getFloat("wheel_scale", if (dm.widthPixels / dm.density < 600) 0.85f else 1.15f)
        wheel.onScaleChanged = { f -> prefs.put("wheel_scale", f) }
        wheel.onDrag = { dx, dy -> wheelGroup.translationX += dx; wheelGroup.translationY += dy; closePopup() }
        wheel.onDragEnd = { onWheelDropped() }
        wheelGroup = FrameLayout(this)
        wheelGroup.addView(wheel, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP))
        root.addView(wheelGroup, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or Gravity.START))
        wheelGroup.translationX = prefs.sp.getFloat("wheel_x", df(30f))
        wheelGroup.translationY = prefs.sp.getFloat("wheel_y", df(54f))

        // ---- selection toolbar (floats above the selection) + options bar (bottom)
        selToolbar = UI.hbox(this)
        selToolbar.background = UI.rounded(0xFAFFFFFF.toInt(), df(8f), d(1f), 0x14000000)
        selToolbar.elevation = df(6f)
        selToolbar.setPadding(d(4f), 0, d(4f), 0)
        selToolbar.visibility = View.GONE
        fun tb(icon: String, f: () -> Unit) = selToolbar.addView(IconButton(this, icon, 46f, 0xFF1E1E1E.toInt()).apply { setOnClickListener { f() } })
        tb("search") { canvas.zoomToSelection() }
        tb("sparkle") { if (!canvas.refineSelection()) showHint("That doesn't look like handwriting") else showHint("Handwriting refined") }
        tb("lockc") { canvas.lockSelectionLayers(); showHint("Layer locked") }
        tb("copy") { canvas.duplicateSelection() }
        tb("trash") { canvas.deleteSelection() }
        tb("group") { canvas.selectAllInLayer() }
        tb("flip") { canvas.flipSelection(true) }
        tb("send") { canvas.bringSelectionToFront(); showHint("Brought to front") }
        tb("recolor") { canvas.recolorSelection(slotColors[currentSlot]) }
        root.addView(selToolbar, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or Gravity.START))

        selOptions = UI.hbox(this)
        selOptions.background = UI.rounded(0xF2F4F4F4.toInt(), df(8f), d(1f), 0x14000000)
        selOptions.elevation = df(6f)
        selOptions.visibility = View.GONE
        fun opt(icon: String, label: String, f: (TextView) -> Unit): TextView {
            val row = UI.hbox(this)
            row.setPadding(d(14f), d(4f), d(16f), d(4f))
            row.addView(com.akshit.folio.ui.IconView(this, icon, 28f))
            val t = UI.label(this, label, 15f, 0xFF262626.toInt()).apply { setPadding(d(8f), 0, 0, 0) }
            row.addView(t)
            row.setOnClickListener { f(t); paintOptions() }
            selOptions.addView(row)
            return t
        }
        rotateOpt = opt("rotateoff", "Rotate Off") { t -> canvas.selRotateOn = !canvas.selRotateOn; t.text = if (canvas.selRotateOn) "Rotate On" else "Rotate Off" }
        stretchOpt = opt("stretch", "Stretch") { canvas.selStretch = !canvas.selStretch }
        opt("filter", "Filters") { showFilters() }
        root.addView(selOptions, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(26f) })

        // ---- hint
        hint = UI.label(this, "", 13f, Color.WHITE, true).apply {
            background = UI.rounded(0xE62A2B31.toInt(), df(18f))
            setPadding(d(16f), d(8f), d(16f), d(8f))
            visibility = View.GONE
        }
        root.addView(hint, FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(96f) })

        panelHost = FrameLayout(this)
        root.addView(panelHost, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        setContentView(root)
        val bar = prefs.sp.getInt("bar_mode", 0)
        if (bar != 0) setBarMode(true, bar < 0)
        if (prefs.sp.getBoolean("layers_open", dm.widthPixels / dm.density >= 600)) showLayers(true)
        updateUndo()
        onCameraChanged()
    }

    private var rotateOpt: TextView? = null
    private var stretchOpt: TextView? = null

    private fun paintOptions() {
        for (i in 0 until selOptions.childCount) {
            val v = selOptions.getChildAt(i)
            val on = (i == 1 && canvas.selStretch) || (i == 0 && canvas.selRotateOn)
            v.background = if (on) UI.rounded(0xFFDADADA.toInt(), df(6f)) else null
        }
    }

    private fun wireWheel(w: ToolWheelView) {
        w.onCenterTap = { toggleColorFan() }
        w.onSizeTap = { openPopup("size") }
        w.onOpacityTap = { openPopup("opacity") }
        w.onSmoothingTap = { openPopup("smoothing") }
    }

    private fun fmtSize(s: Float) = if (s >= 10) "${s.toInt()}" else if (s >= 1) "%.1f".format(s).removeSuffix(".0") else "%.2f".format(s)

    // ------------------------------------------------------------------ wheel popups
    private fun tToSize(t: Float) = BrushPanel.tToSize(t)
    private fun sizeToT(s: Float) = BrushPanel.sizeToT(s)

    fun openPopup(which: String) {
        closeAll()
        val s = slots[currentSlot]
        val eraser = canvas.tool == CanvasView.Tool.ERASER
        val p: WheelPopup = when (which) {
            "size" -> {
                val base = if (eraser) 24f else BrushSpec.preset(s.type).size
                val presets = listOf(1f, 1.5f, 2f, 3.2f).map { f -> val v = base * f; "${fmtSize(v)} ${if (eraser) "px" else "pts"}" to sizeToT(v) }
                WheelPopup(this, "Size", "═", presets, sizeToT(if (eraser) canvas.eraserSizePx else s.size), { "${fmtSize(tToSize(it))} ${if (eraser) "px" else "pts"}" }) { t ->
                    val v = tToSize(t)
                    if (eraser) { canvas.eraserSizePx = v; prefs.put("eraser_size", v) } else s.size = v
                    refreshToolUi(); canvas.invalidate()
                }
            }
            "opacity" -> WheelPopup(this, "Opacity", "◐", listOf("25%" to 0.25f, "50%" to 0.5f, "75%" to 0.75f, "100%" to 1f), s.opacity, { "${(it * 100).toInt()}%" }) { t ->
                s.opacity = t.coerceAtLeast(0.02f); refreshToolUi()
            }
            else -> WheelPopup(this, "Smoothing", "∿", listOf("0%" to 0f, "10%" to 0.1f, "50%" to 0.5f, "100%" to 1f), s.smoothing, { if (it >= 0.995f) "100% · straight" else "${(it * 100).toInt()}%" }) { t ->
                s.smoothing = t; refreshToolUi()
            }
        }
        val scrim = object : View(this) {
            override fun onTouchEvent(e: MotionEvent): Boolean { if (e.actionMasked == MotionEvent.ACTION_DOWN) closePopup(); return false }
        }
        val host = FrameLayout(this)
        host.addView(scrim, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        val wx = wheelGroup.translationX + wheel.centreX(); val wy = wheelGroup.translationY + wheel.centreY()
        val lp = FrameLayout.LayoutParams(d(300f), UI.WRAP, Gravity.TOP or Gravity.START)
        p.translationX = (wx + df(52f) * wheel.scaleFactor).coerceAtMost(root.width - df(310f))
        p.translationY = wy - df(46f)
        host.addView(p, lp)
        panelHost.addView(host, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        popup = host
        p.alpha = 0f; p.animate().alpha(1f).setDuration(120).start()
    }

    fun closePopup() { popup?.let { panelHost.removeView(it) }; popup = null; persistSlots() }

    // ------------------------------------------------------------------ colour fan
    fun toggleColorFan() {
        if (colorFan != null) { closeColorFan(); return }
        closeAll()
        val cx = wheelGroup.translationX + wheel.centreX(); val cy = wheelGroup.translationY + wheel.centreY()
        val fan = ColorFanView(this, cx, cy, slotColors[currentSlot], { c ->
            setColor(c)
            if (canvas.selectionCount() > 0) canvas.recolorSelection(c)
        }, { closeColorFan(); setTool(CanvasView.Tool.EYEDROPPER) }, { closeColorFan() })
        colorFan = fan
        layersHolder.visibility = View.INVISIBLE
        panelHost.addView(fan, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        fan.alpha = 0f; fan.animate().alpha(1f).setDuration(140).start()
    }

    fun closeColorFan() {
        colorFan?.let { panelHost.removeView(it) }
        colorFan = null
        if (::layersHolder.isInitialized && !uiHidden) layersHolder.visibility = View.VISIBLE
        val c = slotColors[currentSlot]
        val l = prefs.recentColors.toMutableList(); l.remove(c); l.add(0, c); prefs.recentColors = l
    }

    // ------------------------------------------------------------------ wheel tools
    private fun loadWheelTools() {
        wheelTools.clear()
        val saved = prefs.sp.getString("wheel_tools", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        wheelTools.addAll(saved?.takeIf { it.size == 8 } ?: listOf(0, 1, 3, 2, 9, 8, 101, 100))
    }

    private fun toolIcon(id: Int) = when (id) { 100 -> "eraser"; 101 -> "cursor"; 102 -> "eyedropper"; 103 -> "hand"; else -> slots[id].type.icon }
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
        closeAll()
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
            .setItems(ids.map { toolLabel(it) }.toTypedArray()) { _, w -> setWheelTool(i, ids[w]) }.show()
    }

    fun setWheelTool(i: Int, id: Int) {
        wheelTools[i] = id
        prefs.put("wheel_tools", wheelTools.joinToString(","))
        onWheelSlot(i)
        rebuildBar()
        refreshToolUi()
    }

    private fun onWheelDropped() {
        val edge = df(16f)
        val x = wheelGroup.translationX
        val maxX = root.width - wheelGroup.width.toFloat()
        wheelGroup.translationY = wheelGroup.translationY.coerceIn(df(40f), root.height - wheelGroup.height * 0.6f)
        when {
            x < -wheel.width * 0.25f + edge -> setBarMode(true, left = true)
            x > maxX + wheel.width * 0.25f - edge -> setBarMode(true, left = false)
            else -> {
                wheelGroup.translationX = x.coerceIn(-wheel.width * 0.2f, maxX + wheel.width * 0.2f)
                prefs.put("wheel_x", wheelGroup.translationX); prefs.put("wheel_y", wheelGroup.translationY)
                positionLayers()
            }
        }
    }

    /** Tool Bar mode: the wheel docks to a screen edge as a vertical bar. */
    fun setBarMode(on: Boolean, left: Boolean = true) {
        barGroup?.let { root.removeView(it) }
        barGroup = null; miniWheel = null; barSlots.clear()
        if (!on) {
            prefs.put("bar_mode", 0)
            wheelGroup.visibility = if (uiHidden) View.GONE else View.VISIBLE
            wheelGroup.translationX = df(30f); wheelGroup.translationY = df(54f)
            prefs.put("wheel_x", wheelGroup.translationX); prefs.put("wheel_y", wheelGroup.translationY)
            positionLayers(); refreshToolUi(); return
        }
        prefs.put("bar_mode", if (left) -1 else 1)
        wheelGroup.visibility = View.GONE
        val col = UI.vbox(this)
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(d(3f), d(6f), d(3f), d(6f))
        val mw = ToolWheelView(this).apply { compact = true; scaleFactor = 0.64f }
        wireWheel(mw)
        miniWheel = mw
        col.addView(mw)
        for ((i, id) in wheelTools.withIndex()) {
            val sv = com.akshit.folio.ui.SlotView(this, toolIcon(id))
            sv.setOnClickListener { onWheelSlot(i) }
            sv.setOnLongClickListener { assignWheelSlot(i); true }
            barSlots.add(sv); col.addView(sv)
        }
        col.addView(View(this).apply { setBackgroundColor(UI.DIV) }, LinearLayout.LayoutParams(d(36f), d(1f)).apply { topMargin = d(4f); bottomMargin = d(4f) })
        col.addView(IconButton(this, "undo_solid").apply { setOnClickListener { canvas.undo() } })
        col.addView(IconButton(this, "redo_solid").apply { setOnClickListener { canvas.redo() } })
        col.addView(IconButton(this, "wheel").apply { contentDescription = "wheel"; setOnClickListener { setBarMode(false) } })
        val scroll = android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = UI.rounded(0xF2EDEDED.toInt(), df(14f)); elevation = df(4f)
            addView(col)
        }
        root.addView(scroll, root.indexOfChild(panelHost), FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.TOP or (if (left) Gravity.START else Gravity.END)).apply {
            topMargin = d(66f); bottomMargin = d(8f); marginStart = d(8f); marginEnd = d(8f)
        })
        barGroup = scroll
        if (uiHidden) scroll.visibility = View.GONE
        positionLayers(); refreshToolUi()
    }

    private fun rebuildBar() {
        val mode = prefs.sp.getInt("bar_mode", 0)
        if (mode != 0) setBarMode(true, mode < 0)
    }

    val isBarMode get() = barGroup != null

    // ------------------------------------------------------------------ layers list
    private fun positionLayers() {
        val lp = layersHolder.layoutParams as FrameLayout.LayoutParams
        if (barGroup != null) { lp.topMargin = d(66f); lp.marginStart = d(92f) }
        else { lp.topMargin = (wheelGroup.translationY + wheel.height + df(8f)).toInt().coerceAtMost(root.height / 2); lp.marginStart = d(12f) }
        layersHolder.layoutParams = lp
    }

    fun showLayers(show: Boolean) {
        layersHolder.removeAllViews(); layersList = null
        if (::layersBtn.isInitialized) layersBtn.isSelected = show
        if (show) {
            val l = LayersList(this, canvas, prefs, { togglePanel("bg") }, { layer -> layerMenu(layer) })
            layersList = l
            layersHolder.addView(l, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
            l.refresh()
            root.post { positionLayers() }
        }
    }

    fun toggleLayers() {
        val show = layersList == null
        prefs.put("layers_open", show)
        showLayers(show)
    }

    @Suppress("unused") fun toggleSidebar() = toggleLayers()

    private fun layerMenu(layer: Layer) {
        val i = doc.layers.indexOf(layer)
        val items = arrayOf("Rename", "Opacity…", "Duplicate", "Merge down", if (layer.locked) "Unlock" else "Lock", "Move up", "Move down", "Delete")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle(layer.name).setItems(items) { _, w ->
            doc.activeLayer = i
            when (w) {
                0 -> { val et = android.widget.EditText(this).apply { setText(layer.name) }
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("Rename layer").setView(et)
                        .setPositiveButton("OK") { _, _ -> layer.name = et.text.toString().ifBlank { layer.name }; layersList?.refresh() }.setNegativeButton("Cancel", null).show() }
                1 -> togglePanel("layers")
                4 -> { layer.locked = !layer.locked; layersList?.refresh() }
                else -> {
                    // reuse the full layers panel's actions
                    val lp = LayersPanel(this, canvas, { layersList?.refresh() }, { })
                    when (w) { 2 -> lp.duplicateActive(); 3 -> lp.mergeActiveDown(); 5 -> lp.moveActive(1); 6 -> lp.moveActive(-1); 7 -> lp.deleteActive() }
                    canvas.invalidateContent(); layersList?.refresh()
                }
            }
        }.show()
    }

    // ------------------------------------------------------------------ settings sheet
    fun toggleSheet(tab: Int = 0) {
        if (sheet != null) { closeSheet(); return }
        closeAll()
        val s = SettingsSheet(this, prefs, doc, tab, { canvas.invalidate(); layersList?.refresh() }, { closeSheet() }, { setBarMode(it) })
        sheet = s
        val w = minOf(d(430f), (resources.displayMetrics.widthPixels * 0.9f).toInt())
        panelHost.addView(s, FrameLayout.LayoutParams(w, UI.MATCH, Gravity.END).apply { topMargin = d(66f); bottomMargin = d(12f); marginEnd = d(12f) })
        s.translationX = df(40f); s.alpha = 0f
        s.animate().translationX(0f).alpha(1f).setDuration(180).start()
    }

    fun closeSheet() { sheet?.let { panelHost.removeView(it) }; sheet = null }

    fun closeAll() { closePopup(); closeColorFan(); closeSheet(); closePanel() }

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
        if (t == CanvasView.Tool.LASSO) showHint("Tap a stroke or draw around strokes to select")
        if (t == CanvasView.Tool.EYEDROPPER) showHint("Touch the canvas to pick a color")
    }

    private fun slotFor(id: Int): ToolWheelView.Slot {
        if (id >= 100) {
            val kind = when (id) { 100 -> ToolWheelView.Kind.ERASER; 101 -> ToolWheelView.Kind.SELECT; 102 -> ToolWheelView.Kind.EYEDROPPER; else -> ToolWheelView.Kind.PAN }
            return ToolWheelView.Slot(toolIcon(id), toolLabel(id), null, if (id == 100) "${canvas.eraserSizePx.toInt()}" else "", kind)
        }
        val b = slots[id]
        val kind = when {
            b.type == BrushType.MARKER || b.type == BrushType.HIGHLIGHTER || b.type == BrushType.FILL -> ToolWheelView.Kind.MARKER
            b.type.kind == RenderKind.STAMP -> ToolWheelView.Kind.SOFT
            else -> ToolWheelView.Kind.BRUSH
        }
        val mw = UI.dpf(this, 1f) * (1.2f + kotlin.math.ln(1f + b.size) * 1.1f)
        return ToolWheelView.Slot(b.type.icon, b.type.label, slotColors[id], fmtSize(b.size), kind, mw, b.opacity)
    }

    private fun refreshToolUi() {
        if (!::wheel.isInitialized) return
        val t = canvas.tool
        val s = slots[currentSlot]
        val eraser = t == CanvasView.Tool.ERASER
        val slotList = wheelTools.map { slotFor(it) }
        val active = wheelTools.indexOfFirst { isActive(it) }
        for (w in listOfNotNull(wheel, miniWheel)) {
            w.slots = if (w === miniWheel) emptyList() else slotList
            w.activeSlot = active
            w.eraserMode = eraser
            w.color = slotColors[currentSlot]
            w.sizeText = if (eraser) "${canvas.eraserSizePx.toInt()} px" else "${fmtSize(s.size)} pts"
            w.opacityText = "${(s.opacity * 100).toInt()}%"
            w.smoothingText = "${(s.smoothing * 100).toInt()}%"
            w.canUndo = canvas.history.canUndo(); w.canRedo = canvas.history.canRedo()
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
        if (name == "color") { toggleColorFan(); return }
        if (panelName == name) { closePanel(); return }
        closePopup(); closeColorFan(); closeSheet()
        when (name) {
            "layers" -> showPanel(name, LayersPanel(this, canvas, { layersList?.refresh() }, { closePanel() }), nearWheel = false)
            "bg" -> showPanel(name, BackgroundPanel(this, doc, prefs, { canvas.invalidate(); layersList?.refresh() }, { closePanel() }), nearWheel = false)
            "precision" -> showPanel(name, precisionPanel(), nearWheel = false)
            "library" -> showPanel(name, libraryPanel(), nearWheel = false)
            "help" -> showPanel(name, helpPanel(), nearWheel = false)
            "eraser" -> {
                val p = Panel(this, "Eraser", { closePanel() })
                p.body.addView(UI.toggle(this, "Erase whole strokes", "Off = precise vector eraser that slices strokes", canvas.eraserWhole) {
                    canvas.eraserWhole = it; prefs.put("eraser_whole", it)
                })
                showPanel(name, p, 0.4f)
            }
        }
    }

    private fun libraryPanel(): Panel {
        val p = Panel(this, "Brush library", { closePanel() })
        p.body.addView(UI.label(this, "Tap a tool to put it in the active wheel slot. Long-press any wheel slot to choose its tool.", 12.5f, UI.SUB).apply { setPadding(d(4f), 0, 0, d(8f)) })
        val activeIdx = wheelTools.indexOfFirst { isActive(it) }.coerceAtLeast(0)
        val ids = slots.indices.toList() + listOf(100, 101, 102, 103)
        var row: LinearLayout? = null
        ids.forEachIndexed { k, id ->
            if (k % 4 == 0) { row = UI.hbox(this); p.body.addView(row) }
            val cell = UI.vbox(this).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, d(8f), 0, d(8f)) }
            cell.addView(com.akshit.folio.ui.IconView(this, toolIcon(id), 34f))
            cell.addView(UI.label(this, toolLabel(id), 11.5f, UI.TEXT).apply { gravity = Gravity.CENTER })
            cell.background = UI.ripple(this, UI.rounded(Color.TRANSPARENT, df(10f)))
            cell.setOnClickListener { setWheelTool(activeIdx, id); closePanel() }
            row!!.addView(cell, UI.lp(0, UI.WRAP, 1f))
        }
        return p
    }

    private fun helpPanel(): Panel {
        val p = Panel(this, "Gestures & tips", { closePanel() })
        val tips = listOf(
            "Pen draws · fingers pan, pinch-zoom and rotate",
            "Two-finger tap: undo · three-finger tap: redo",
            "Three-finger swipe left/right: scrub through undo",
            "Four-finger tap: hide / show the interface",
            "Double-tap: reset zoom · two-finger double-tap: fit",
            "Long-press with a finger: eyedropper",
            "Draw and hold: snap to a perfect shape",
            "Pause after writing: handwriting is tidied (one undo reverts)",
            "Wheel: tap a slot to pick a tool, tap again for its settings, long-press to swap it",
            "Wheel: tap the size / opacity / smoothing readouts for sliders; tap the colour dot for colours",
            "Drag the wheel to move it, pinch it to resize, drop it on an edge to dock it as a bar",
            "Volume keys: undo / redo (great with a buttonless pen)")
        for (t in tips) p.body.addView(UI.label(this, "•  $t", 14f, UI.TEXT).apply { setPadding(d(4f), d(6f), 0, d(6f)) })
        return p
    }

    private fun precisionPanel(): Panel {
        val p = Panel(this, "Precision", { closePanel() })
        p.body.addView(UI.section(this, "Grid"))
        val row = UI.hbox(this)
        val quick = listOf(com.akshit.folio.model.Pattern.NONE to "Off", com.akshit.folio.model.Pattern.DOTS to "Dots", com.akshit.folio.model.Pattern.GRID to "Grid",
            com.akshit.folio.model.Pattern.ISOMETRIC to "Iso", com.akshit.folio.model.Pattern.LINES to "Lines", com.akshit.folio.model.Pattern.PERSPECTIVE_2 to "Persp")
        val chips = ArrayList<TextView>()
        fun paint() = chips.forEachIndexed { k, o -> val on = quick[k].first == doc.background.pattern; o.background = UI.rounded(if (on) 0xFF2B2B2B.toInt() else UI.PANEL2, df(8f)); o.setTextColor(if (on) Color.WHITE else UI.TEXT) }
        for ((i, pair) in quick.withIndex()) {
            val c = UI.label(this, pair.second, 12.5f, UI.TEXT, true).apply { gravity = Gravity.CENTER; setPadding(0, d(8f), 0, d(8f)) }
            c.setOnClickListener { doc.background.pattern = quick[i].first; canvas.invalidate(); paint(); layersList?.refresh() }
            chips.add(c)
            row.addView(c, UI.lp(0, UI.WRAP, 1f).apply { marginEnd = d(4f) })
        }
        paint()
        p.body.addView(row)
        p.body.addView(UI.button(this, "Paper, grid & perspective options…") { togglePanel("bg") }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = d(8f) })
        p.body.addView(UI.section(this, "Guides & snapping"))
        p.body.addView(UI.toggle(this, "Shape guides", "Draw and hold to snap to a perfect line, circle, ellipse, arc, rectangle or polygon", prefs.shapeHold) { prefs.put(Prefs.K_SHAPE_HOLD, it) })
        p.body.addView(UI.toggle(this, "Refine handwriting", "Straighten and tidy writing when you pause", prefs.refineAuto) { prefs.put(Prefs.K_REFINE, it) })
        p.body.addView(UI.toggle(this, "Zoom snap", "Snap to 100% when you pinch close to it", prefs.zoomSnap) { prefs.put(Prefs.K_ZOOM_SNAP, it) })
        p.body.addView(UI.toggle(this, "Canvas rotation", "Two-finger rotate (snaps to 0° / 90°)", prefs.rotateEnabled) { prefs.put(Prefs.K_ROTATE, it); onCameraChanged() })
        p.body.addView(UI.label(this, "Tip: Smoothing at 100% draws perfectly straight lines.", 12f, UI.SUB).apply { setPadding(d(4f), d(10f), 0, 0) })
        return p
    }

    fun showPanel(name: String, p: Panel, heightFraction: Float = 0.86f, nearWheel: Boolean = true) {
        closePanel()
        panelName = name
        if (::precisionBtn.isInitialized) { precisionBtn.isSelected = name == "precision"; libraryBtn.isSelected = name == "library" }
        currentPanel = p
        p.alpha = 0f; p.translationY = df(10f)
        val lp = panelLayoutParams(this, !prefs.leftHanded, heightFraction, nearWheel)
        if (!nearWheel) { lp.gravity = Gravity.TOP or Gravity.START; lp.topMargin = d(66f); lp.marginStart = d(if (resources.displayMetrics.widthPixels / resources.displayMetrics.density < 600) 12f else 240f) }
        p.maxHeightPx = minOf(lp.height, resources.displayMetrics.heightPixels - lp.topMargin - d(16f))
        lp.height = UI.WRAP
        panelHost.addView(p, lp)
        p.animate().alpha(1f).translationY(0f).setDuration(160).start()
    }

    fun closePanel() {
        currentPanel?.let { panelHost.removeView(it) }
        currentPanel = null; panelName = null
        if (::precisionBtn.isInitialized) { precisionBtn.isSelected = false; libraryBtn.isSelected = false }
        persistSlots()
    }

    val openPanelName get() = panelName
    fun findPanel(): Panel? = currentPanel
    fun togglePanelBrush() = openBrushPanel()

    // ------------------------------------------------------------------ selection filters
    private fun showFilters() {
        val items = arrayOf("Only the current colour", "Only the current brush type", "Only writing tools (pens & pencils)", "Only the active layer")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("Filter selection").setItems(items) { _, w ->
            val c = slotColors[currentSlot]; val t = slots[currentSlot].type; val layer = doc.active()
            canvas.filterSelection { s ->
                when (w) {
                    0 -> (s.color and 0xFFFFFF) == (c and 0xFFFFFF)
                    1 -> s.brush.type == t
                    2 -> s.brush.type in setOf(BrushType.PEN, BrushType.FINELINER, BrushType.PENCIL, BrushType.TECHNICAL, BrushType.INK_BRUSH, BrushType.CALLIGRAPHY)
                    else -> layer.strokes.any { it === s }
                }
            }
        }.show()
    }

    // ------------------------------------------------------------------ listener
    override fun onHistoryChanged() {
        if (!::root.isInitialized) return
        updateUndo()
        handler.removeCallbacks(autosave)
        handler.postDelayed(autosave, prefs.autosaveSec * 1000L)
        if (panelName == "layers") (currentPanel as? LayersPanel)?.build()
        handler.removeCallbacks(layersRefresh)
        handler.postDelayed(layersRefresh, 400)
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
            GestureAction.COLOR_PICKER -> toggleColorFan()
            GestureAction.BRUSH_SETTINGS -> openBrushPanel()
            GestureAction.NEXT_BRUSH -> selectSlot((currentSlot + 1) % slots.size, false)
            GestureAction.PREV_BRUSH -> selectSlot((currentSlot - 1 + slots.size) % slots.size, false)
            GestureAction.TOGGLE_LAST_BRUSH -> selectSlot(lastSlot, false)
            GestureAction.SHOW_WHEEL -> bringWheelToPen()
            else -> canvas.performAction(action)
        }
    }

    override fun onToolChanged() { if (::wheel.isInitialized) refreshToolUi() }

    override fun onCameraChanged() {
        if (!::zoomText.isInitialized) return
        if (canvas.selectionCount() > 0) { showSelectionStatus(); return }
        val pct = canvas.cam.scale * 100
        zoomText.text = when {
            pct >= 10000 -> "%.0fk%%".format(pct / 1000)
            pct >= 10 -> "%.0f%%".format(pct)
            pct >= 0.1 -> "%.1f%%".format(pct)
            else -> "%.2e%%".format(pct)
        }
        lockBtn.visibility = View.VISIBLE
        lockBtn.alpha = if (prefs.rotateEnabled) 0.35f else 1f
        rotText.text = "${canvas.rotationDegrees()}°"
    }

    private fun showSelectionStatus() {
        zoomText.text = "W: $selW%   H: $selH%"
        lockBtn.visibility = View.GONE
        rotText.text = "   $selDeg°"
    }

    override fun onPenDetected() {
        showHint("Pen detected · pen draws, fingers pan / zoom")
    }

    override fun onSelectionChanged(count: Int) {
        if (!::selToolbar.isInitialized) return
        val v = if (count > 0 && !uiHidden) View.VISIBLE else View.GONE
        selToolbar.visibility = v; selOptions.visibility = v
        if (count > 0) { paintOptions(); showSelectionStatus() } else onCameraChanged()
    }

    override fun onSelectionGeometry(box: RectF, wPct: Int, hPct: Int, deg: Int) {
        if (!::selToolbar.isInitialized || selToolbar.visibility != View.VISIBLE) return
        selW = wPct; selH = hPct; selDeg = deg
        showSelectionStatus()
        val tw = selToolbar.width.toFloat().takeIf { it > 0 } ?: df(400f)
        val th = selToolbar.height.toFloat().takeIf { it > 0 } ?: df(46f)
        var x = box.centerX() - tw / 2
        var y = box.top - th - df(56f)
        if (y < df(60f)) y = box.bottom + df(40f)
        x = x.coerceIn(df(8f), root.width - tw - df(8f)); y = y.coerceIn(df(60f), root.height - th - df(90f))
        selToolbar.translationX = x; selToolbar.translationY = y
    }

    override fun onShapeRecognized(kind: ShapeRecognizer.Kind) {
        showHint(kind.name.lowercase().replaceFirstChar { it.uppercase() })
    }

    // ------------------------------------------------------------------ helpers
    private fun updateUndo() {
        refreshToolUi()
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
        statusLeft.visibility = v; statusRight.visibility = v
        if (barGroup == null) wheelGroup.visibility = v
        barGroup?.visibility = v
        layersHolder.visibility = v
        if (uiHidden) { closeAll(); selToolbar.visibility = View.GONE; selOptions.visibility = View.GONE }
        else if (canvas.selectionCount() > 0) { selToolbar.visibility = View.VISIBLE; selOptions.visibility = View.VISIBLE }
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

    /** Handles pen keys (buttons / squeeze / learned codes). Returns true if consumed. */
    fun handlePenKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val act = prefs.penKeyAction(code) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            lastPenKey = code
            onAction(act)
        }
        return true
    }

    var lastPenKey = 0; private set
    private var slideAccum = 0f

    /** Pen slide: scroll-type events from the stylus (or a learned axis). */
    fun handlePenSlide(amount: Float) {
        if (amount == 0f) return
        val s = slots[currentSlot]
        when (prefs.slideAction) {
            com.akshit.folio.core.SlideAction.NONE -> return
            com.akshit.folio.core.SlideAction.BRUSH_SIZE -> {
                if (canvas.tool == CanvasView.Tool.ERASER) canvas.eraserSizePx = (canvas.eraserSizePx * Math.pow(1.12, amount.toDouble()).toFloat()).coerceIn(2f, 400f)
                else s.size = (s.size * Math.pow(1.12, amount.toDouble()).toFloat()).coerceIn(0.3f, 300f)
                refreshToolUi(); showHint("Size ${if (canvas.tool == CanvasView.Tool.ERASER) "${canvas.eraserSizePx.toInt()} px" else "${fmtSize(s.size)} pts"}")
            }
            com.akshit.folio.core.SlideAction.OPACITY -> {
                s.opacity = (s.opacity + 0.05f * amount).coerceIn(0.02f, 1f); refreshToolUi(); showHint("Opacity ${(s.opacity * 100).toInt()}%")
            }
            com.akshit.folio.core.SlideAction.ZOOM -> canvas.zoomBy(Math.pow(1.15, amount.toDouble()), animate = false)
            com.akshit.folio.core.SlideAction.UNDO_REDO -> {
                slideAccum += amount
                while (slideAccum >= 1f) { slideAccum -= 1f; canvas.redo() }
                while (slideAccum <= -1f) { slideAccum += 1f; canvas.undo() }
            }
        }
        persistSlots()
    }

    private val lastAxis = HashMap<Int, Float>()

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val stylus = ev.isFromSource(android.view.InputDevice.SOURCE_STYLUS) || (ev.pointerCount > 0 && ev.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)
        if (ev.actionMasked == MotionEvent.ACTION_SCROLL && (stylus || prefs.sp.getBoolean("slide_any_source", false))) {
            val v = ev.getAxisValue(MotionEvent.AXIS_VSCROLL) + ev.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if (v != 0f) { handlePenSlide(v); return true }
        }
        // a learned axis (some pens report slide/squeeze pressure on a generic axis)
        val ax = prefs.slideAxis
        if (ax >= 0 && stylus) {
            val v = ev.getAxisValue(ax)
            val prev = lastAxis[ax]
            lastAxis[ax] = v
            if (prev != null && kotlin.math.abs(v - prev) > 0.02f) { handlePenSlide((v - prev) * 10f) }
        }
        // stylus button pressed while hovering
        if (ev.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
            val b = ev.actionButton
            val act = when {
                b and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 -> prefs.action(Prefs.G_PEN_BUTTON)
                b and MotionEvent.BUTTON_STYLUS_SECONDARY != 0 -> prefs.action(Prefs.G_PEN_BUTTON2)
                else -> null
            }
            if (act != null && act != GestureAction.NONE) { onAction(act); return true }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    fun wheelCentreOnScreen(): Pair<Float, Float> = (wheelGroup.translationX + wheel.centreX()) to (wheelGroup.translationY + wheel.centreY())

    /** Squeeze default: bring the wheel to where the pen is (like a pop-up tool palette). */
    fun bringWheelToPen() {
        if (barGroup != null) { showHint("Wheel is docked as a bar"); return }
        val x = if (canvas.penX >= 0) canvas.penX else root.width / 2f
        val y = if (canvas.penY >= 0) canvas.penY else root.height / 2f
        val tx = (x - wheel.centreX()).coerceIn(-wheel.width * 0.2f, root.width - wheel.width * 0.8f)
        val ty = (y - wheel.centreY()).coerceIn(df(40f), root.height - wheel.height * 0.8f)
        wheelGroup.animate().translationX(tx).translationY(ty).setDuration(180).withEndAction {
            prefs.put("wheel_x", wheelGroup.translationX); prefs.put("wheel_y", wheelGroup.translationY); positionLayers()
        }.start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handlePenKey(event)) return true
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
        if (::zoomText.isInitialized) zoomText.visibility = if (prefs.showZoom) View.VISIBLE else View.INVISIBLE
        refreshToolUi()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autosave)
        if (demo == null || isFinishing) save(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentPanel != null || popup != null || colorFan != null || sheet != null) { closeAll(); return }
        if (canvas.selection.isNotEmpty()) { canvas.clearSelection(); return }
        save(true)
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        @Suppress("unused") val ALL_TYPES = BrushType.entries
    }
}
