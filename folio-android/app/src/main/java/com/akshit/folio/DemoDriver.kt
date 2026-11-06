package com.akshit.folio

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.ScrollView
import com.akshit.folio.brush.BrushType
import com.akshit.folio.core.GestureAction
import com.akshit.folio.core.Prefs
import com.akshit.folio.core.ShapeRecognizer
import com.akshit.folio.model.Layer
import com.akshit.folio.model.Pattern
import com.akshit.folio.ui.ColorPickerPanel
import com.akshit.folio.view.CanvasView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Automated end-to-end test + demo. Injects synthetic pen (pressure, tilt, hover), finger and
 * palm events through the real input pipeline of [CanvasView], checks the outcome, and prints
 * "SHOT:<name>" markers so CI can capture screenshots / video at the right moments.
 *
 * adb shell am start -n com.akshit.folio/.CanvasActivity --es demo all
 */
class DemoDriver(private val a: CanvasActivity) {
    private val h = Handler(Looper.getMainLooper())
    private val c: CanvasView get() = a.canvas
    private var t = 0L
    private val steps = ArrayList<Pair<Long, () -> Unit>>()
    private var pass = 0; private var fail = 0
    private var w = 0f; private var hh = 0f
    private val d = a.resources.displayMetrics.density

    data class P(val id: Int, val x: Float, val y: Float, val tool: Int = MotionEvent.TOOL_TYPE_STYLUS,
                 val pressure: Float = 0.6f, val tilt: Float = 0.3f, val majorMm: Float = 1f)

    private fun log(s: String) = Log.i(TAG, s)

    private fun check(name: String, ok: Boolean, detail: String = "") {
        if (ok) { pass++; log("ASSERT PASS $name $detail") } else { fail++; log("ASSERT FAIL $name $detail") }
    }

    private fun at(dt: Long, f: () -> Unit) { t += dt; steps.add(t to f) }
    private fun pause(ms: Long) = at(ms) {}
    private fun shot(name: String) { at(500) { log("SHOT:$name") }; pause(2200) }
    private fun step(name: String) = at(10) { log("STEP $name"); a.showHint(name) }

    fun run(which: String) {
        w = c.width.toFloat(); hh = c.height.toFloat()
        log("DEMO START $which size=${w}x$hh density=$d")
        c.debugTouches = true
        a.prefs.put(Prefs.K_SHAPE_MS, 450)
        when (which) {
            "brushes" -> brushes()
            "palm" -> palm()
            else -> all()
        }
        at(800) {
            log("DEMO DONE pass=$pass fail=$fail")
            c.debugTouches = false
        }
        val start = SystemClock.uptimeMillis()
        for ((time, f) in steps) h.postAtTime({
            try { f() } catch (e: Throwable) { fail++; log("ASSERT FAIL exception ${e.javaClass.simpleName}: ${e.message}"); Log.e(TAG, "step failed", e) }
        }, start + time)
    }

    // ================================================================== scenarios
    private fun all() {
        brushes()
        colors()
        brushPro()
        backgrounds()
        zoomAndGestures()
        palm()
        shapes()
        eraser()
        lasso()
        eyedropper()
        layers()
        finale()
    }

    private fun resetDoc(pattern: Pattern = Pattern.DOTS) = at(50) {
        a.closePanel()
        c.clearSelection()
        val doc = c.doc
        doc.layers.clear(); doc.layers.add(Layer("Layer 1")); doc.activeLayer = 0
        doc.background.pattern = pattern
        doc.background.paperColor = android.graphics.Color.rgb(250, 249, 246)
        doc.background.lineColor = android.graphics.Color.rgb(120, 130, 150)
        doc.background.spacing = 32f
        c.history.clear()
        c.animateTo(0.0, 0.0, 1.0, 0.0, animate = false)
        a.selectSlot(0, openSettingsIfSame = false)
        c.invalidateContent()
    }

    private fun brushes() {
        step("All 13 brush engines · pressure + tilt")
        resetDoc()
        val n = BrushType.entries.size
        val cols = if (w > hh) 2 else 1
        val rows = (n + cols - 1) / cols
        val left = 120f * d; val right = w - 40f * d
        val top = 90f * d; val bottom = hh - 40f * d
        val colW = (right - left) / cols
        val rowH = (bottom - top) / rows
        val colors = intArrayOf(0xFF1B1B1F.toInt(), 0xFF2F6BDA.toInt(), 0xFF3A3A3A.toInt(), 0xFFE5534B.toInt(), 0xFFF2D13A.toInt(),
            0xFF111111.toInt(), 0xFF7A2E2E.toInt(), 0xFF22324F.toInt(), 0xFF8E24AA.toInt(), 0xFF1E88E5.toInt(), 0xFF2C2C2C.toInt(), 0xFF43A047.toInt(), 0xFFFB8C00.toInt())
        for (i in 0 until n) {
            val col = i / rows; val row = i % rows
            val x0 = left + col * colW + 20f * d; val x1 = x0 + colW - 60f * d
            val cy = top + row * rowH + rowH / 2
            at(30) { a.selectSlot(i, openSettingsIfSame = false); a.setColor(colors[i]) }
            val pts = ArrayList<P>()
            val k = 36
            for (j in 0..k) {
                val tt = j / k.toFloat()
                val x = x0 + (x1 - x0) * tt
                val y = cy + sin(tt * 2 * PI * 1.5).toFloat() * rowH * 0.28f
                val p = (0.15f + 0.85f * sin(tt * PI).toFloat()).coerceIn(0.05f, 1f)
                pts.add(P(0, x, y, pressure = p, tilt = 0.2f + 0.9f * tt))
            }
            if (BrushType.entries[i] == BrushType.FILL) {
                pts.clear()
                val cx = (x0 + x1) / 2; val r = rowH * 0.35f
                for (j in 0..32) { val an = j / 32.0 * 2 * PI; pts.add(P(0, cx + (cos(an) * r * 2.2).toFloat(), cy + (sin(an) * r).toFloat(), pressure = 0.7f)) }
            }
            penPath(pts, 9)
            pause(60)
        }
        at(100) { check("brushes.strokeCount", c.doc.active().strokes.size == n, "got=${c.doc.active().strokes.size} expected=$n") }
        shot("01_all_brushes")
    }

    private fun colors() {
        step("Color wheel · sliders · palettes")
        at(50) { a.togglePanel("color") }
        pause(700)
        shot("02_color_wheel")
        // drag on the wheel's hue ring through the real panel is fiddly to synthesise; drive the tabs
        at(10) { (a.findPanel() as? ColorPickerPanel)?.let { p -> tabClick(p, 1) } }
        pause(500)
        shot("03_color_sliders")
        at(10) { (a.findPanel() as? ColorPickerPanel)?.let { p -> tabClick(p, 2) } }
        pause(500)
        shot("04_color_palettes")
        at(10) { (a.findPanel() as? ColorPickerPanel)?.setColor(0xFF1E88E5.toInt()) }
        at(200) { check("color.applied", c.color == 0xFF1E88E5.toInt(), "color=#${Integer.toHexString(c.color)}") }
        at(50) { a.closePanel() }
    }

    private fun tabClick(p: ColorPickerPanel, i: Int) {
        // tabs row is the 3rd child of the body (preview, hex row, tabs)
        val tabs = p.body.getChildAt(2) as android.widget.LinearLayout
        tabs.getChildAt(i).performClick()
    }

    private fun brushPro() {
        step("Pro brush settings")
        at(50) { a.selectSlot(2, openSettingsIfSame = false); a.togglePanelBrush() }
        pause(700)
        shot("05_brush_pro_settings")
        at(10) { a.findPanel()?.let { p -> (p.getChildAt(1) as? ScrollView)?.scrollTo(0, (900 * d).toInt()) } }
        pause(400)
        shot("06_brush_pro_settings_more")
        at(10) { a.findPanel()?.let { p -> (p.getChildAt(1) as? ScrollView)?.scrollTo(0, (2200 * d).toInt()) } }
        pause(400)
        shot("07_brush_pro_settings_stamps")
        at(50) { a.closePanel() }
    }

    private fun backgrounds() {
        step("Paper & grids")
        at(50) { a.togglePanel("bg") }
        pause(700)
        shot("08_background_panel")
        at(50) { a.closePanel() }
        val list = listOf(Pattern.GRAPH to "09_bg_graph", Pattern.ISOMETRIC to "10_bg_isometric", Pattern.HEX to "11_bg_hex",
            Pattern.PERSPECTIVE_2 to "12_bg_perspective_2pt", Pattern.ISO_DOTS to "13_bg_iso_dots_night")
        for ((p, name) in list) {
            at(50) {
                c.doc.background.pattern = p
                if (p == Pattern.ISO_DOTS) { c.doc.background.paperColor = android.graphics.Color.rgb(18, 18, 20); c.doc.background.lineColor = android.graphics.Color.rgb(200, 210, 230) }
                if (p == Pattern.PERSPECTIVE_2) { c.doc.background.vpDistance = w / 3; c.doc.background.horizon = 0f }
                c.invalidate()
            }
            shot(name)
        }
    }

    private fun zoomAndGestures() {
        step("Infinite zoom · pinch · rotate · tap gestures")
        resetDoc(Pattern.GRID)
        at(10) { a.selectSlot(0, openSettingsIfSame = false); a.setColor(0xFF1B1B1F.toInt()) }
        // a spiral
        val cx = w / 2; val cy = hh / 2
        val sp = ArrayList<P>()
        for (j in 0..120) { val an = j / 120.0 * 6 * PI; val r = 10 * d + j * 1.6f * d; sp.add(P(0, cx + (cos(an) * r).toFloat(), cy + (sin(an) * r).toFloat(), pressure = 0.3f + 0.6f * j / 120f)) }
        penPath(sp, 8)
        // zoom in 5 times around the centre (4x each = 1024x)
        var s0 = 0.0
        at(50) { s0 = c.cam.scale }
        repeat(7) { pinch(cx, cy, 60f * d, 240f * d, 0f) }
        at(200) { check("zoom.deepIn", c.cam.scale > s0 * 500, "scale=${c.cam.scale}") }
        shot("14_zoomed_in_deep")
        // draw a tiny detail at deep zoom
        at(10) { a.selectSlot(1, openSettingsIfSame = false); a.setColor(0xFFE5534B.toInt()) }
        val det = ArrayList<P>()
        for (j in 0..40) { val an = j / 40.0 * 2 * PI; det.add(P(0, cx + 160f * d + (cos(an) * 60 * d).toFloat(), cy + (sin(an) * 60 * d).toFloat(), pressure = 0.7f)) }
        penPath(det, 8)
        shot("15_detail_drawn_deep_zoom")
        repeat(7) { pinch(cx, cy, 240f * d, 60f * d, 0f) }
        at(200) { check("zoom.backOut", c.cam.scale < s0 * 4, "scale=${c.cam.scale}") }
        shot("16_zoomed_back_out")
        // rotate with two fingers (45deg)
        pinch(cx, cy, 150f * d, 150f * d, (PI / 4).toFloat())
        at(200) { check("gesture.rotate", abs(c.rotationDegrees()) in 30..60, "rot=${c.rotationDegrees()}") }
        shot("17_rotated_canvas")
        // double-tap with one finger = reset zoom & rotation
        tap(listOf(cx to cy)); pause(120); tap(listOf(cx to cy))
        pause(800)
        at(10) { check("gesture.doubleTapReset", c.rotationDegrees() == 0 && abs(c.cam.scale - 1.0) < 0.01, "rot=${c.rotationDegrees()} scale=${c.cam.scale}") }
        // two-finger tap = undo, three-finger tap = redo
        var before = 0
        at(50) { before = c.doc.active().strokes.size }
        tap(listOf(cx - 60 * d to cy, cx + 60 * d to cy))
        pause(700)
        at(10) { check("gesture.twoFingerTapUndo", c.doc.active().strokes.size == before - 1, "before=$before after=${c.doc.active().strokes.size}") }
        tap(listOf(cx - 80 * d to cy, cx to cy - 40 * d, cx + 80 * d to cy))
        pause(500)
        at(10) { check("gesture.threeFingerTapRedo", c.doc.active().strokes.size == before, "after=${c.doc.active().strokes.size}") }
        // four-finger tap toggles UI
        tap(listOf(cx - 120 * d to cy, cx - 40 * d to cy, cx + 40 * d to cy, cx + 120 * d to cy))
        pause(500)
        at(10) { check("gesture.fourFingerTapHidesUi", a.isUiHidden) }
        shot("18_four_finger_tap_hides_ui")
        tap(listOf(cx - 120 * d to cy, cx - 40 * d to cy, cx + 40 * d to cy, cx + 120 * d to cy))
        pause(400)
        // three-finger swipe = undo scrub
        at(50) { before = c.doc.active().strokes.size }
        swipe3(cx, cy, -200f * d)
        pause(300)
        at(10) { check("gesture.threeFingerScrubUndo", c.doc.active().strokes.size < before, "before=$before after=${c.doc.active().strokes.size}") }
        swipe3(cx, cy, 260f * d)
        pause(300)
        at(10) { check("gesture.threeFingerScrubRedo", c.doc.active().strokes.size == before, "after=${c.doc.active().strokes.size}") }
    }

    private fun palm() {
        step("Palm rejection")
        resetDoc(Pattern.LINES)
        at(10) { a.selectSlot(0, openSettingsIfSame = false); a.setColor(0xFF22324F.toInt()); c.palmStatsReset() }
        val baseY = hh * 0.35f
        // 1) palm resting on the screen BEFORE the pen touches, pen writes, palm stays
        val frames = ArrayList<List<P>>()
        val palm = P(9, w * 0.62f, baseY + 140 * d, MotionEvent.TOOL_TYPE_FINGER, 0.9f, 0f, majorMm = 22f)
        frames.add(listOf(palm)); frames.add(listOf(palm.copy(x = palm.x + 2)))
        val word = handwriting(w * 0.3f, baseY, w * 0.32f, 50 * d)
        word.forEach { pt -> frames.add(listOf(palm.copy(x = palm.x + pt.x * 0.02f), pt.copy(id = 1))) }
        frames.add(listOf(palm))
        frames.add(emptyList())
        touches(frames, 9)
        pause(200)
        // 2) small accidental finger touch while the pen is hovering just above the screen
        at(10) { hover(w * 0.5f, baseY + 200 * d) }
        at(30) { hover(w * 0.5f + 4, baseY + 202 * d) }
        touches(listOf(listOf(P(3, w * 0.55f, baseY + 260 * d, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f)),
            listOf(P(3, w * 0.58f, baseY + 280 * d, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f)),
            listOf(P(3, w * 0.62f, baseY + 300 * d, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f)), emptyList()), 30)
        pause(500)
        // 3) pen writes a second line while a palm lands half-way through
        val word2 = handwriting(w * 0.3f, baseY + 110 * d, w * 0.32f, 50 * d)
        val f2 = ArrayList<List<P>>()
        word2.forEachIndexed { i, pt ->
            if (i < word2.size / 3) f2.add(listOf(pt.copy(id = 0)))
            else f2.add(listOf(pt.copy(id = 0), P(7, w * 0.7f + i, baseY + 260 * d, MotionEvent.TOOL_TYPE_FINGER, 1f, 0f, 25f)))
        }
        f2.add(listOf(P(7, w * 0.7f, baseY + 260 * d, MotionEvent.TOOL_TYPE_FINGER, 1f, 0f, 25f)))
        f2.add(emptyList())
        touches(f2, 9)
        pause(400)
        // 4) a large palm alone: must neither draw nor pan
        var camX = 0.0
        at(10) { camX = c.cam.cx }
        touches(listOf(listOf(P(5, w * 0.5f, hh * 0.8f, MotionEvent.TOOL_TYPE_FINGER, 1f, 0f, 28f)),
            listOf(P(5, w * 0.45f, hh * 0.8f, MotionEvent.TOOL_TYPE_FINGER, 1f, 0f, 28f)),
            listOf(P(5, w * 0.40f, hh * 0.8f, MotionEvent.TOOL_TYPE_FINGER, 1f, 0f, 28f)), emptyList()), 30)
        pause(700)
        at(10) {
            check("palm.onlyPenStrokes", c.doc.active().strokes.size == 2, "strokes=${c.doc.active().strokes.size}")
            check("palm.rejectedContacts", c.statPalmRejected >= 3, "rejected=${c.statPalmRejected}")
            check("palm.noPanFromPalm", abs(c.cam.cx - camX) < 1e-6, "dx=${c.cam.cx - camX}")
        }
        // 5) normal finger drag after a pen has been used = pans, doesn't draw
        touches((0..12).map { listOf(P(4, w * 0.5f - it * 15 * d, hh * 0.75f, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f)) } + listOf(emptyList()), 16)
        pause(400)
        at(10) {
            check("palm.fingerPansNotDraws", c.doc.active().strokes.size == 2 && abs(c.cam.cx - camX) > 1, "strokes=${c.doc.active().strokes.size} dx=${c.cam.cx - camX}")
        }
        shot("19_palm_rejection")
        // visual moment with the palm overlay showing while writing
        val word3 = handwriting(w * 0.3f, baseY + 220 * d, w * 0.25f, 40 * d)
        val f3 = ArrayList<List<P>>()
        val pm = P(8, w * 0.66f, baseY + 330 * d, MotionEvent.TOOL_TYPE_FINGER, 1f, 0f, 24f)
        f3.add(listOf(pm))
        word3.forEachIndexed { i, pt -> f3.add(listOf(pt.copy(id = 0), pm.copy(x = pm.x + i * 0.5f))) }
        touches(f3.subList(0, f3.size * 2 / 3), 9)
        at(50) { log("SHOT:20_palm_overlay_while_writing") }
        pause(2200)
        touches(f3.subList(f3.size * 2 / 3, f3.size) + listOf(listOf(pm), emptyList()), 9)
        pause(300)
    }

    private fun shapes() {
        step("Draw & hold → perfect shapes")
        resetDoc(Pattern.DOTS)
        at(10) { a.selectSlot(0, openSettingsIfSame = false); a.setColor(0xFF2F6BDA.toInt()); a.slots[0].size = 5f }
        val r = minOf(w, hh) * 0.12f
        fun wobble(j: Int) = (sin(j * 1.7) * 4 * d).toFloat()
        // rough circle
        val circ = (0..60).map { j -> val an = j / 60.0 * 2 * PI; P(0, w * 0.3f + (cos(an) * r).toFloat() + wobble(j), hh * 0.35f + (sin(an) * r).toFloat() + wobble(j + 3)) }
        penPath(circ, 8, holdMs = 700)
        at(50) { check("shape.circle", c.statLastShape == ShapeRecognizer.Kind.CIRCLE, "got=${c.statLastShape}") }
        // rough rectangle
        val rect = ArrayList<P>()
        val x0 = w * 0.55f; val y0 = hh * 0.22f; val rw = r * 2.4f; val rh = r * 1.6f
        val corners = listOf(x0 to y0, x0 + rw to y0 + 6, x0 + rw - 4 to y0 + rh, x0 + 3 to y0 + rh - 5, x0 to y0 + 2)
        for (k in 0 until corners.size - 1) for (j in 0 until 14) {
            val tt = j / 14f
            rect.add(P(0, corners[k].first + (corners[k + 1].first - corners[k].first) * tt + wobble(j), corners[k].second + (corners[k + 1].second - corners[k].second) * tt + wobble(j + 1)))
        }
        penPath(rect, 8, holdMs = 700)
        at(50) { check("shape.rectangle", c.statLastShape == ShapeRecognizer.Kind.RECTANGLE, "got=${c.statLastShape}") }
        // rough line
        val line = (0..30).map { j -> P(0, w * 0.2f + j * w * 0.018f, hh * 0.72f + wobble(j) * 0.6f + j * 0.8f) }
        penPath(line, 8, holdMs = 700)
        at(50) { check("shape.line", c.statLastShape == ShapeRecognizer.Kind.LINE, "got=${c.statLastShape}") }
        // rough triangle
        val tri = ArrayList<P>()
        val tc = listOf(w * 0.75f to hh * 0.55f, w * 0.88f to hh * 0.82f, w * 0.62f to hh * 0.82f, w * 0.75f to hh * 0.55f)
        for (k in 0 until 3) for (j in 0 until 16) {
            val tt = j / 16f
            tri.add(P(0, tc[k].first + (tc[k + 1].first - tc[k].first) * tt + wobble(j), tc[k].second + (tc[k + 1].second - tc[k].second) * tt + wobble(j + 2)))
        }
        tri.add(P(0, tc[3].first, tc[3].second))
        penPath(tri, 8, holdMs = 700)
        at(50) { check("shape.triangle", c.statLastShape == ShapeRecognizer.Kind.TRIANGLE, "got=${c.statLastShape}") }
        shot("21_perfect_shapes")
    }

    private fun eraser() {
        step("Vector eraser slices strokes")
        resetDoc(Pattern.DOTS)
        at(10) { a.selectSlot(3, openSettingsIfSame = false); a.setColor(0xFFE5534B.toInt()) }
        for (k in 0 until 3) penPath((0..30).map { j -> P(0, w * 0.25f + j * w * 0.016f, hh * (0.35f + k * 0.12f), pressure = 0.8f) }, 6)
        at(30) { a.setTool(CanvasView.Tool.ERASER); c.eraserSizePx = 40 * d; c.eraserWhole = false }
        penPath((0..30).map { j -> P(0, w * 0.5f, hh * 0.28f + j * hh * 0.012f) }, 10)
        at(100) { check("eraser.slices", c.doc.active().strokes.size == 6, "strokes=${c.doc.active().strokes.size}") }
        shot("22_vector_eraser")
        at(30) { a.selectSlot(3, openSettingsIfSame = false) }
    }

    private fun lasso() {
        step("Lasso select · move · scale")
        at(30) { a.setTool(CanvasView.Tool.LASSO) }
        val lp = (0..40).map { j -> val an = j / 40.0 * 2 * PI; P(0, w * 0.37f + (cos(an) * w * 0.14f).toFloat(), hh * 0.47f + (sin(an) * hh * 0.24f).toFloat()) }
        penPath(lp, 8)
        at(100) { check("lasso.selects", c.selection.size >= 3, "selected=${c.selection.size}") }
        shot("23_lasso_selection")
        // drag the selection
        penPath((0..20).map { j -> P(0, w * 0.37f + j * 6 * d, hh * 0.47f + j * 3 * d) }, 12)
        pause(300)
        shot("24_selection_moved")
        at(50) { c.clearSelection(); a.selectSlot(0, openSettingsIfSame = false) }
    }

    private fun eyedropper() {
        step("Long-press = eyedropper")
        at(30) { a.setColor(0xFF000000.toInt()) }
        // long-press with a finger on a red stroke (y = 0.35h line left part survived the eraser)
        // the right half of the middle marker line (left of it was lasso-moved)
        val x = w * 0.65f; val y = hh * 0.47f
        touches(listOf(listOf(P(2, x, y, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f))), 10)
        pause(900)
        at(10) { log("SHOT:26a_eyedropper_loupe") }
        pause(1500)
        touches(listOf(listOf(P(2, x, y, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f)), emptyList()), 10, continuing = true)
        at(200) {
            val col = c.color
            val r = android.graphics.Color.red(col); val g = android.graphics.Color.green(col)
            check("eyedropper.picksRed", r > 180 && r - g > 40, "picked=#${Integer.toHexString(col)}")
        }
    }

    private fun layers() {
        step("Layers")
        at(50) { a.togglePanel("layers") }
        pause(300)
        at(10) { a.findPanel()?.let { p -> findByDesc(p, "plus")?.performClick() } }
        pause(300)
        at(10) { check("layers.added", c.doc.layers.size == 2, "layers=${c.doc.layers.size}") }
        at(10) { a.selectSlot(9, openSettingsIfSame = false); a.setColor(0xFF1E88E5.toInt()) }
        penPath((0..40).map { j -> P(0, w * 0.2f + j * w * 0.012f, hh * 0.8f + (sin(j / 6.0) * 20 * d).toFloat(), pressure = 0.8f) }, 8)
        at(50) { a.closePanel(); a.togglePanel("layers") }
        pause(500)
        shot("25_layers_panel")
        at(50) { a.closePanel() }
    }

    private fun finale() {
        step("A little sketch")
        resetDoc(Pattern.DOTS)
        // simple landscape sketch using several brushes
        at(10) { a.selectSlot(9, openSettingsIfSame = false); a.setColor(0xFF81D4FA.toInt()); a.slots[9].size = 120f }
        for (k in 0 until 4) penPath((0..30).map { j -> P(0, w * 0.15f + j * w * 0.024f, hh * (0.22f + k * 0.05f) + (sin(j / 4.0 + k) * 8 * d).toFloat(), pressure = 0.9f) }, 6)
        at(10) { a.selectSlot(5, openSettingsIfSame = false); a.setColor(0xFF2E3B2F.toInt()) }
        penPath((0..60).map { j -> P(0, w * 0.1f + j * w * 0.013f, hh * 0.62f - (sin(j / 9.0) * 90 * d).toFloat() - j * 0.6f * d, pressure = 0.4f + 0.5f * sin(j / 60.0 * PI).toFloat()) }, 7)
        at(10) { a.selectSlot(2, openSettingsIfSame = false); a.setColor(0xFF3A3A3A.toInt()) }
        for (k in 0 until 10) penPath((0..12).map { j -> P(0, w * 0.2f + k * w * 0.06f + j * 4 * d, hh * 0.72f + j * 9 * d, pressure = 0.5f, tilt = 1.1f) }, 6)
        at(10) { a.selectSlot(4, openSettingsIfSame = false); a.setColor(0xFFF2D13A.toInt()) }
        penPath((0..30).map { j -> val an = j / 30.0 * 2 * PI; P(0, w * 0.78f + (cos(an) * 40 * d).toFloat(), hh * 0.2f + (sin(an) * 40 * d).toFloat()) }, 7)
        at(200) { c.zoomFit() }
        pause(800)
        shot("26_sketch")
    }

    // ================================================================== input synthesis
    private fun hover(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0f; setAxisValue(MotionEvent.AXIS_DISTANCE, 0.5f) })
        val ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        c.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    private fun penPath(pts: List<P>, stepMs: Long, holdMs: Long = 0) {
        val frames = pts.map { listOf(it.copy(id = 0)) }.toMutableList<List<P>>()
        if (holdMs > 0) {
            val last = frames.last()
            val n = (holdMs / 40).toInt()
            touches(frames, stepMs)
            repeat(n) { touches(listOf(last), 40, continuing = true) }
            touches(listOf(emptyList()), 30, continuing = true)
            return
        }
        frames.add(emptyList())
        touches(frames, stepMs)
    }

    private var current: List<P> = emptyList()
    private var downTime = 0L

    /** Plays a sequence of pointer frames, emitting DOWN / POINTER_DOWN / MOVE / POINTER_UP / UP. */
    private fun touches(frames: List<List<P>>, stepMs: Long, continuing: Boolean = false) {
        if (!continuing) at(0) { current = emptyList() }
        for (f in frames) at(stepMs) { applyFrame(f) }
    }

    private fun applyFrame(next: List<P>) {
        val prevIds = current.map { it.id }.toSet()
        val nextIds = next.map { it.id }.toSet()
        // pointers going up
        for (id in prevIds - nextIds) {
            val set = current.sortedBy { it.id }
            val idx = set.indexOfFirst { it.id == id }
            val action = if (set.size == 1) MotionEvent.ACTION_UP else MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            dispatch(action, set)
            current = current.filter { it.id != id }
        }
        // pointers going down
        for (p in next.filter { it.id !in prevIds }) {
            val set = (current + p).sortedBy { it.id }
            val idx = set.indexOfFirst { it.id == p.id }
            if (current.isEmpty()) downTime = SystemClock.uptimeMillis()
            val action = if (set.size == 1) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_POINTER_DOWN or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            dispatch(action, set)
            current = set
        }
        // moves
        val moved = next.filter { it.id in prevIds }
        if (moved.isNotEmpty()) {
            current = next.sortedBy { it.id }
            dispatch(MotionEvent.ACTION_MOVE, current)
        }
    }

    private fun dispatch(action: Int, set: List<P>) {
        val now = SystemClock.uptimeMillis()
        val mm = a.resources.displayMetrics.xdpi / 25.4f
        val props = Array(set.size) { i -> MotionEvent.PointerProperties().apply { id = set[i].id; toolType = set[i].tool } }
        val coords = Array(set.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = set[i].x; y = set[i].y; pressure = set[i].pressure; size = 0.1f
                touchMajor = set[i].majorMm * mm; touchMinor = set[i].majorMm * mm * 0.8f
                setAxisValue(MotionEvent.AXIS_TILT, set[i].tilt)
            }
        }
        val anyPen = set.any { it.tool == MotionEvent.TOOL_TYPE_STYLUS }
        val src = if (anyPen) InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_TOUCHSCREEN
        val ev = MotionEvent.obtain(downTime, now, action, set.size, props, coords, 0, 0, 1f, 1f, 0, 0, src, 0)
        c.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun pinch(cx: Float, cy: Float, r0: Float, r1: Float, rot: Float) {
        val frames = ArrayList<List<P>>()
        val steps = 14
        for (s in 0..steps) {
            val tt = s / steps.toFloat()
            val r = r0 + (r1 - r0) * tt
            val an = rot * tt
            frames.add(listOf(
                P(0, cx - r * cos(an), cy - r * sin(an), MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f),
                P(1, cx + r * cos(an), cy + r * sin(an), MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f)))
        }
        frames.add(emptyList())
        touches(frames, 16)
        pause(150)
    }

    private fun tap(pts: List<Pair<Float, Float>>) {
        val f = pts.mapIndexed { i, (x, y) -> P(i, x, y, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f) }
        touches(listOf(f, f, emptyList()), 30)
    }

    private fun swipe3(cx: Float, cy: Float, dx: Float) {
        val frames = (0..16).map { s ->
            val off = dx * s / 16f
            listOf(P(0, cx - 80 * d + off, cy, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f),
                P(1, cx + off, cy - 30 * d, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f),
                P(2, cx + 80 * d + off, cy, MotionEvent.TOOL_TYPE_FINGER, 0.5f, 0f, 7f))
        } + listOf(emptyList())
        touches(frames, 16)
    }

    /** Cursive-ish scribble that looks like handwriting. */
    private fun handwriting(x0: Float, y0: Float, width: Float, height: Float): List<P> {
        val out = ArrayList<P>()
        val n = 140
        for (j in 0..n) {
            val tt = j / n.toFloat()
            val x = x0 + tt * width + sin(tt * 40).toFloat() * height * 0.18f
            val y = y0 - (abs(sin(tt * 20.0)) * height * 0.6).toFloat() + sin(tt * 7.0).toFloat() * height * 0.1f
            out.add(P(0, x, y, pressure = 0.35f + 0.5f * abs(cos(tt * 20.0)).toFloat(), tilt = 0.6f))
        }
        return out
    }

    private fun findByDesc(v: android.view.View, desc: String): android.view.View? {
        if (v.contentDescription == desc) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findByDesc(v.getChildAt(i), desc)?.let { return it }
        return null
    }

    companion object {
        const val TAG = "FolioDemo"
        @Suppress("unused") private val unused = GestureAction.NONE
    }
}
