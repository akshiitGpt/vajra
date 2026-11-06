package com.akshit.folio.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderNode
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.MotionPredictor
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.akshit.folio.brush.BrushSpec
import com.akshit.folio.brush.BrushType
import com.akshit.folio.brush.RenderKind
import com.akshit.folio.core.Camera
import com.akshit.folio.core.FingerDraw
import com.akshit.folio.core.GestureAction
import com.akshit.folio.core.Prefs
import com.akshit.folio.core.ShapeRecognizer
import com.akshit.folio.model.Document
import com.akshit.folio.model.History
import com.akshit.folio.model.LambdaCommand
import com.akshit.folio.model.SnapshotCommand
import com.akshit.folio.model.Stroke
import com.akshit.folio.render.BackgroundRenderer
import com.akshit.folio.render.SceneRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The infinite canvas. Owns rendering, palm rejection, pen input and every gesture.
 *
 * Rendering: committed strokes are recorded into GPU display lists (RenderNodes). While you
 * pan / zoom / rotate, the display list is re-projected by a matrix every frame, so the vector
 * paths are re-rasterised crisply by the GPU without re-walking the document. The list is
 * re-recorded only when content changes or the camera drifts far from where it was recorded.
 */
@SuppressLint("ViewConstructor")
class CanvasView(ctx: Context, val prefs: Prefs) : View(ctx) {

    interface Listener {
        fun onHistoryChanged() {}
        fun onColorPicked(color: Int) {}
        fun onAction(action: GestureAction) {}
        fun onToolChanged() {}
        fun onCameraChanged() {}
        fun onPenDetected() {}
        fun onSelectionChanged(count: Int) {}
        fun onShapeRecognized(kind: ShapeRecognizer.Kind) {}
    }

    enum class Tool { BRUSH, ERASER, LASSO, EYEDROPPER, PAN }

    private enum class Mode { IDLE, DRAW, ERASE, LASSO, EYEDROP, GESTURE, PAN1, FINGER_PENDING, SCRUB, SEL_MOVE, SEL_SCALE, SEL_ROTATE, CONSUMED }

    private class Ptr(val id: Int, val tool: Int, var x: Float, var y: Float, val downTime: Long) {
        val downX = x; val downY = y
        var ignored = false
        var moved = 0f
        val isPen get() = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
    }

    var listener: Listener? = null
    var doc: Document = Document("tmp", "Untitled")
        private set
    val history = History()
    val cam = Camera()
    private val bg = BackgroundRenderer()
    private val density = resources.displayMetrics.density
    private val mmToPx = resources.displayMetrics.xdpi / 25.4f
    private val slop = 6f * density

    var tool = Tool.BRUSH
        set(v) { field = v; if (v != Tool.LASSO) clearSelection(); listener?.onToolChanged(); invalidate() }
    var brush: BrushSpec = BrushSpec.preset(BrushType.PEN)
    var color: Int = Color.BLACK
    var eraserSizePx = 24f
    var eraserWhole = false

    // ------------------------------------------------------------------ render state
    private val nodeBelow = RenderNode("below").apply { setClipToBounds(false) }
    private val nodeAbove = RenderNode("above").apply { setClipToBounds(false) }
    private val recCam = Camera()
    private var contentDirty = true
    private var recW = 0; private var recH = 0
    private val rel = Matrix()
    private var gestureActive = false

    // ------------------------------------------------------------------ input state
    private val ptrs = HashMap<Int, Ptr>()
    private var mode = Mode.IDLE
    private var actorId = -1
    private var stylusActiveUntil = 0L
    private var penDown = false

    // stroke in progress
    private var active: Stroke? = null
    private var liveBrush: BrushSpec? = null
    private var activeIsFinger = false
    private var activeStart = 0L
    private var activeDownTime = 0L
    private var lastLx = 0f; private var lastLy = 0f
    private var rawLx = 0f; private var rawLy = 0f
    private var strokeScreenLen = 0f
    private var shaped: ShapeRecognizer.Kind? = null
    private var holdAnchorX = 0f; private var holdAnchorY = 0f
    private var lastPressure = 1f; private var lastTilt = 0f

    // prediction
    private var predictor: MotionPredictor? = null
    private var predX = FloatArray(32); private var predY = FloatArray(32); private var predN = 0

    // gesture baseline
    private var gCx = 0f; private var gCy = 0f; private var gDist = 1f; private var gAngle = 0f; private var gCount = 0
    private var gRawRot = 0.0
    private var gStarted = false
    private var scrubAccum = 0f

    // tap sequences
    private var seqStart = 0L; private var seqMaxFingers = 0; private var seqMoved = false
    private var lastTapTime = 0L; private var lastTapX = 0f; private var lastTapY = 0f
    private var lastTwoTapTime = 0L
    private var pendingTwoTap: Runnable? = null
    private var penTapTime = 0L; private var penTapX = 0f; private var penTapY = 0f

    // eraser
    private var eraseBefore: ArrayList<Stroke>? = null
    private var eraseX = 0f; private var eraseY = 0f

    // lasso & selection
    private val lassoPath = Path()
    private val lassoPts = ArrayList<Float>()
    val selection = ArrayList<Stroke>()
    private val selectionSet = HashSet<Stroke>()
    private var selBounds = DoubleArray(4)
    private var selPivotX = 0.0; private var selPivotY = 0.0
    private var selTx = 0.0; private var selTy = 0.0; private var selScale = 1.0; private var selRot = 0.0
    private var selStartX = 0f; private var selStartY = 0f
    private var selStartScale = 1.0; private var selStartRot = 0.0; private var selStartTx = 0.0; private var selStartTy = 0.0
    private val selExtra = Matrix()

    // eyedropper / hover
    private var eyeX = 0f; private var eyeY = 0f; private var eyeColor = 0
    private var hoverX = 0f; private var hoverY = 0f; private var hoverVisible = false

    // fps
    private var frames = 0; private var fpsT0 = 0L; private var fps = 0
    var lastFrameMs = 0f; private set

    // diagnostics for the automated test driver
    var statStrokesCommitted = 0; private set
    var statPalmRejected = 0; private set
    var statLastShape: ShapeRecognizer.Kind? = null; private set

    private val longPress = Runnable { onFingerLongPress() }
    private val penHold = Runnable { onPenLongPress() }
    private val shapeHold = Runnable { onShapeHold() }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        history.onChange = { invalidateContent(); listener?.onHistoryChanged() }
        if (Build.VERSION.SDK_INT >= 34) predictor = MotionPredictor(ctx)
    }

    // ================================================================== document
    fun setDocument(d: Document) {
        doc = d
        history.clear()
        clearSelection()
        cam.cx = d.camCx; cam.cy = d.camCy; cam.scale = d.camScale; cam.setRotationRad(d.camRot)
        invalidateContent()
        listener?.onCameraChanged()
    }

    fun storeCamera() { doc.camCx = cam.cx; doc.camCy = cam.cy; doc.camScale = cam.scale; doc.camRot = cam.rotation }

    fun invalidateContent() { contentDirty = true; postInvalidateOnAnimation() }

    fun undo() { if (mode == Mode.DRAW) return; clearSelection(); if (history.undo()) haptic() }
    fun redo() { if (mode == Mode.DRAW) return; clearSelection(); if (history.redo()) haptic() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // keep the same world point in the centre
        cam.viewW = w.toFloat(); cam.viewH = h.toFloat(); cam.update()
        invalidateContent()
    }

    // ================================================================== rendering
    private fun needsRerecord(): Boolean {
        if (contentDirty || recW != width || recH != height) return true
        val ratio = cam.scale / recCam.scale
        if (ratio > 2.5 || ratio < 0.4) return true
        if (abs(cam.rotation - recCam.rotation) > 0.6) return true
        val sx = cam.worldToScreenX(recCam.cx, recCam.cy) - width / 2f
        val sy = cam.worldToScreenY(recCam.cx, recCam.cy) - height / 2f
        if (hypot(sx, sy) > max(width, height) * 0.8f) return true
        if (!gestureActive && (cam.scale != recCam.scale || cam.cx != recCam.cx || cam.cy != recCam.cy || cam.rotation != recCam.rotation)) return true
        return false
    }

    private fun record() {
        recCam.set(cam)
        recW = width; recH = height
        val margin = max(width, height).toFloat()
        val skip = if (selection.isNotEmpty()) selectionSet else null
        nodeBelow.setPosition(0, 0, width, height)
        var c = nodeBelow.beginRecording(width, height)
        SceneRenderer.drawLayers(c, recCam, doc, 0..doc.activeLayer, skip, margin)
        nodeBelow.endRecording()
        nodeAbove.setPosition(0, 0, width, height)
        c = nodeAbove.beginRecording(width, height)
        SceneRenderer.drawLayers(c, recCam, doc, (doc.activeLayer + 1) until doc.layers.size, skip, margin)
        nodeAbove.endRecording()
        contentDirty = false
    }

    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val overlayFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    private val tmpM = Matrix()

    override fun onDraw(canvas: Canvas) {
        val t0 = System.nanoTime()
        bg.draw(canvas, cam, doc.background, density)
        val hw = canvas.isHardwareAccelerated
        if (hw) {
            if (needsRerecord()) record()
            cam.relativeMatrix(recCam, rel)
            canvas.save(); canvas.concat(rel); canvas.drawRenderNode(nodeBelow); canvas.restore()
        } else {
            SceneRenderer.drawLayers(canvas, cam, doc, 0..doc.activeLayer, if (selection.isNotEmpty()) selectionSet else null)
        }

        // live selection (drawn separately so it can move freely)
        if (selection.isNotEmpty()) {
            computeSelExtra()
            for (s in selection) SceneRenderer.drawStroke(canvas, cam, s, selExtra)
        }

        // stroke in progress + predicted tail
        active?.let { s ->
            SceneRenderer.drawStroke(canvas, cam, s)
            if (predN > 0 && s.n > 0 && shaped == null) drawPrediction(canvas, s)
        }

        if (hw) {
            canvas.save(); canvas.concat(rel); canvas.drawRenderNode(nodeAbove); canvas.restore()
        } else {
            SceneRenderer.drawLayers(canvas, cam, doc, (doc.activeLayer + 1) until doc.layers.size, if (selection.isNotEmpty()) selectionSet else null)
        }

        drawOverlays(canvas)
        lastFrameMs = (System.nanoTime() - t0) / 1e6f
        if (prefs.showFps) {
            frames++
            val now = SystemClock.uptimeMillis()
            if (now - fpsT0 > 1000) { fps = frames; frames = 0; fpsT0 = now }
            overlayFill.color = Color.argb(170, 0, 0, 0)
            canvas.drawRect(width - 230f * density / 2, 0f, width.toFloat(), 26f * density, overlayFill)
            overlayFill.color = Color.WHITE; overlayFill.textSize = 12f * density
            canvas.drawText("${fps}fps  ${"%.1f".format(lastFrameMs)}ms", width - 108f * density, 18f * density, overlayFill)
        }
    }

    private fun drawPrediction(c: Canvas, s: Stroke) {
        val tail = Stroke(liveBrush ?: brush, s.color, s.width, s.ox, s.oy)
        val from = max(0, s.n - 2)
        for (i in from until s.n) tail.add(s.x(i), s.y(i), s.p(i), s.tilt(i), s.t(i))
        val t = s.t(s.n - 1)
        for (i in 0 until predN) {
            val lx = (cam.screenToWorldX(predX[i], predY[i]) - s.ox).toFloat()
            val ly = (cam.screenToWorldY(predX[i], predY[i]) - s.oy).toFloat()
            tail.add(lx, ly, lastPressure, lastTilt, t + i + 1)
        }
        SceneRenderer.drawStroke(c, cam, tail)
    }

    /** Visualises every contact (used by Pen Lab style debugging and the automated demo). */
    var debugTouches = false

    private fun drawOverlays(c: Canvas) {
        if (debugTouches) {
            for (p in ptrs.values) {
                when {
                    p.isPen -> { overlayFill.color = Color.argb(200, 40, 120, 255); c.drawCircle(p.x, p.y, 7f * density, overlayFill) }
                    p.ignored -> {
                        overlayFill.color = Color.argb(90, 229, 57, 53); c.drawCircle(p.x, p.y, 34f * density, overlayFill)
                        overlayFill.color = Color.argb(230, 183, 28, 28); overlayFill.textSize = 13f * density
                        c.drawText("palm ✕", p.x - 24f * density, p.y + 5f * density, overlayFill)
                    }
                    else -> { overlay.color = Color.argb(220, 46, 160, 90); overlay.strokeWidth = 3f * density; c.drawCircle(p.x, p.y, 22f * density, overlay) }
                }
            }
        }
        // lasso
        if (mode == Mode.LASSO && lassoPts.size >= 4) {
            overlay.color = Color.argb(220, 40, 120, 255); overlay.strokeWidth = 2f * density; overlay.pathEffect = dash
            c.drawPath(lassoPath, overlay)
            overlay.pathEffect = null
        }
        // selection box with handles
        if (selection.isNotEmpty()) {
            val pts = selectionScreenCorners()
            overlay.color = Color.argb(230, 40, 120, 255); overlay.strokeWidth = 1.5f * density; overlay.pathEffect = dash
            val p = Path(); p.moveTo(pts[0], pts[1]); for (i in 1 until 4) p.lineTo(pts[i * 2], pts[i * 2 + 1]); p.close()
            c.drawPath(p, overlay); overlay.pathEffect = null
            overlayFill.color = Color.WHITE
            val hs = 9f * density
            // scale handle (corner 2), rotate handle (above top edge)
            c.drawCircle(pts[4], pts[5], hs, overlayFill); c.drawCircle(pts[4], pts[5], hs, overlay)
            val (rx, ry) = rotateHandle(pts)
            c.drawLine((pts[0] + pts[2]) / 2, (pts[1] + pts[3]) / 2, rx, ry, overlay)
            c.drawCircle(rx, ry, hs, overlayFill); c.drawCircle(rx, ry, hs, overlay)
        }
        // eraser cursor
        if (mode == Mode.ERASE) {
            overlay.color = Color.argb(200, 90, 90, 90); overlay.strokeWidth = 1.5f * density
            c.drawCircle(eraseX, eraseY, eraserSizePx / 2f, overlay)
        }
        // eyedropper loupe
        if (mode == Mode.EYEDROP) {
            val r = 34f * density
            val cx = eyeX; val cy = eyeY - 70f * density
            overlayFill.color = eyeColor
            c.drawCircle(cx, cy, r, overlayFill)
            overlay.color = Color.WHITE; overlay.strokeWidth = 5f * density; c.drawCircle(cx, cy, r, overlay)
            overlay.color = Color.argb(120, 0, 0, 0); overlay.strokeWidth = 1f * density; c.drawCircle(cx, cy, r + 3f * density, overlay)
        }
        // pen hover cursor
        if (hoverVisible && prefs.hoverCursor && mode == Mode.IDLE) {
            overlay.color = Color.argb(150, 60, 60, 60); overlay.strokeWidth = 1f * density
            val r = if (tool == Tool.ERASER) eraserSizePx / 2f else max(brush.size / 2f, 2f * density)
            c.drawCircle(hoverX, hoverY, r, overlay)
            overlayFill.color = Color.argb(160, 30, 30, 30)
            c.drawCircle(hoverX, hoverY, 1.2f * density, overlayFill)
        }
    }

    // ================================================================== input entry points
    override fun onHoverEvent(ev: MotionEvent): Boolean {
        val t = ev.getToolType(0)
        if (t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER) {
            markPen()
            stylusActiveUntil = SystemClock.uptimeMillis() + prefs.palmCooldownMs
            when (ev.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> { hoverX = ev.x; hoverY = ev.y; hoverVisible = true }
                MotionEvent.ACTION_HOVER_EXIT -> hoverVisible = false
            }
            if (prefs.hoverCursor) postInvalidateOnAnimation()
            return true
        }
        return super.onHoverEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val now = ev.eventTime
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = ev.actionIndex
                val p = Ptr(ev.getPointerId(idx), ev.getToolType(idx), ev.getX(idx), ev.getY(idx), now)
                ptrs[p.id] = p
                if (p.isPen) onPenDown(ev, idx, p) else onFingerDown(ev, idx, p)
            }
            MotionEvent.ACTION_MOVE -> onMove(ev)
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val idx = ev.actionIndex
                val id = ev.getPointerId(idx)
                val p = ptrs[id]
                val canceled = prefs.honorCancelFlag && (ev.flags and FLAG_CANCELED) != 0
                if (p != null) {
                    if (p.isPen) onPenUp(ev, idx, p, canceled) else onFingerUp(ev, idx, p, canceled)
                }
                ptrs.remove(id)
                if (ev.actionMasked == MotionEvent.ACTION_UP) onAllUp()
            }
            MotionEvent.ACTION_CANCEL -> {
                // System decided the whole stream was accidental (e.g. palm): discard work in progress.
                if (mode == Mode.DRAW) cancelStroke()
                if (mode == Mode.ERASE) finishErase()
                if (mode == Mode.LASSO) { lassoPts.clear(); lassoPath.reset() }
                ptrs.clear(); mode = Mode.IDLE; actorId = -1; penDown = false
                endGesture(); cancelTimers()
                invalidate()
            }
        }
        if (debugTouches) invalidate()
        return true
    }

    fun palmStatsReset() { statPalmRejected = 0 }

    // ------------------------------------------------------------------ pen
    private fun markPen() {
        if (!prefs.penDetected) { prefs.put(Prefs.K_PEN_DETECTED, true); listener?.onPenDetected() }
    }

    private fun onPenDown(ev: MotionEvent, idx: Int, p: Ptr) {
        markPen()
        penDown = true
        hoverVisible = false
        stylusActiveUntil = Long.MAX_VALUE
        if (prefs.unbuffered && Build.VERSION.SDK_INT >= 30) requestUnbufferedDispatch(ev)

        // A pen touching down always wins: cancel any finger stroke / gesture (it was probably a palm).
        if (mode == Mode.DRAW && activeIsFinger) { cancelStroke(); statPalmRejected++ }
        if (mode == Mode.GESTURE || mode == Mode.PAN1 || mode == Mode.FINGER_PENDING || mode == Mode.SCRUB) endGesture()
        for (o in ptrs.values) if (!o.isPen) o.ignored = true
        cancelTimers()
        pendingTwoTap?.let { removeCallbacks(it) }; pendingTwoTap = null
        if (mode == Mode.ERASE || mode == Mode.LASSO) abortSingleActor()
        if (mode == Mode.SEL_MOVE || mode == Mode.SEL_SCALE || mode == Mode.SEL_ROTATE) commitSelectionTransform()
        mode = Mode.IDLE

        // Which tool does this pen contact use?
        var eff = tool
        val buttons = ev.buttonState
        val mapped: GestureAction? = when {
            p.tool == MotionEvent.TOOL_TYPE_ERASER -> prefs.action(Prefs.G_PEN_ERASER_END)
            buttons and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 -> prefs.action(Prefs.G_PEN_BUTTON)
            buttons and MotionEvent.BUTTON_STYLUS_SECONDARY != 0 -> prefs.action(Prefs.G_PEN_BUTTON2)
            else -> null
        }
        if (mapped != null) {
            when (mapped) {
                GestureAction.TOGGLE_ERASER -> eff = if (tool == Tool.ERASER) Tool.BRUSH else Tool.ERASER
                GestureAction.EYEDROPPER -> eff = Tool.EYEDROPPER
                GestureAction.LASSO -> eff = Tool.LASSO
                GestureAction.PAN -> eff = Tool.PAN
                GestureAction.NONE -> {}
                else -> { performAction(mapped); mode = Mode.CONSUMED; actorId = p.id; return }
            }
        }
        actorId = p.id
        if (selection.isNotEmpty() && beginSelectionManip(p.x, p.y)) return
        startToolAction(eff, ev, idx, p, finger = false)
        if (mode == Mode.DRAW && prefs.action(Prefs.G_PEN_LONG_PRESS) != GestureAction.NONE) postDelayed(penHold, 550)
    }

    private fun onPenUp(ev: MotionEvent, idx: Int, p: Ptr, canceled: Boolean) {
        penDown = false
        stylusActiveUntil = SystemClock.uptimeMillis() + prefs.palmCooldownMs
        removeCallbacks(penHold); removeCallbacks(shapeHold)
        if (p.id != actorId) return
        if (canceled) { if (mode == Mode.DRAW) cancelStroke(); mode = Mode.IDLE; actorId = -1; return }
        when (mode) {
            Mode.DRAW -> {
                addSamples(ev, idx, finalSample = true)
                val tiny = strokeScreenLen < 8f * density && ev.eventTime - activeDownTime < 220
                commitStroke()
                if (tiny) checkPenDoubleTap(p.x, p.y)
            }
            else -> finishSingleActor(p)
        }
        mode = Mode.IDLE; actorId = -1
        predN = 0
        invalidate()
    }

    private fun checkPenDoubleTap(x: Float, y: Float) {
        val act = prefs.action(Prefs.G_PEN_DOUBLE_TAP)
        val now = SystemClock.uptimeMillis()
        if (act != GestureAction.NONE && now - penTapTime < 380 && hypot(x - penTapX, y - penTapY) < 36f * density) {
            // remove the two dots that the taps left behind
            val layer = doc.active()
            repeat(2) { if (layer.strokes.isNotEmpty()) layer.strokes.removeAt(layer.strokes.size - 1) }
            history.dropLast(2)
            statStrokesCommitted -= 2
            penTapTime = 0
            performAction(act)
            return
        }
        penTapTime = now; penTapX = x; penTapY = y
    }

    private fun onPenLongPress() {
        if (mode != Mode.DRAW || activeIsFinger || strokeScreenLen > slop) return
        val act = prefs.action(Prefs.G_PEN_LONG_PRESS)
        cancelStroke()
        haptic(true)
        if (act == GestureAction.EYEDROPPER) {
            mode = Mode.EYEDROP
            val p = ptrs[actorId]
            if (p != null) updateEyedrop(p.x, p.y)
        } else {
            mode = Mode.CONSUMED
            performAction(act)
        }
    }

    // ------------------------------------------------------------------ fingers
    private fun fingersDraw(): Boolean = when (prefs.fingerDraw) {
        FingerDraw.ALWAYS -> true
        FingerDraw.NEVER -> false
        FingerDraw.AUTO -> !prefs.penDetected
    }

    private fun isPalm(ev: MotionEvent, idx: Int): Boolean {
        val major = ev.getTouchMajor(idx)
        if (major > 0f && major / mmToPx > prefs.palmSizeMm) return true
        val edge = prefs.edgeRejectMm * mmToPx
        if (edge > 0f) {
            val x = ev.getX(idx)
            if (x < edge || x > width - edge) return true
        }
        return false
    }

    private fun activeFingers(): List<Ptr> = ptrs.values.filter { !it.isPen && !it.ignored }.sortedBy { it.id }

    private fun onFingerDown(ev: MotionEvent, idx: Int, p: Ptr) {
        val now = SystemClock.uptimeMillis()
        // Palm rejection: big contact patch, pen touching or hovering nearby, screen edge,
        // or a pen-driven action already in progress.
        val penBusy = ptrs[actorId]?.isPen == true && mode != Mode.IDLE
        if (penDown || (prefs.rejectWhileHover && now < stylusActiveUntil) || isPalm(ev, idx) || penBusy ||
            (mode == Mode.DRAW && !activeIsFinger) || mode == Mode.CONSUMED || mode == Mode.EYEDROP ||
            mode == Mode.SEL_MOVE || mode == Mode.SEL_SCALE || mode == Mode.SEL_ROTATE) {
            p.ignored = true
            statPalmRejected++
            return
        }
        val fingers = activeFingers()
        if (fingers.size == 1) {
            seqStart = now; seqMaxFingers = 1; seqMoved = false
        } else {
            seqMaxFingers = max(seqMaxFingers, fingers.size)
        }

        if (fingers.size == 1 && mode == Mode.IDLE) {
            actorId = p.id
            if (selection.isNotEmpty() && beginSelectionManip(p.x, p.y)) return
            if (fingersDraw() || tool == Tool.PAN || tool == Tool.EYEDROPPER || (tool == Tool.LASSO && !prefs.penDetected) || (tool == Tool.ERASER && !prefs.penDetected)) {
                startToolAction(tool, ev, idx, p, finger = true)
            } else {
                mode = Mode.FINGER_PENDING
                if (prefs.action(Prefs.G_LONG_PRESS) != GestureAction.NONE) postDelayed(longPress, 480)
            }
        } else if (fingers.size >= 2) {
            removeCallbacks(longPress)
            when (mode) {
                Mode.DRAW -> {
                    // Second finger soon after starting a finger stroke = it was a pinch, not a stroke.
                    if (activeIsFinger) { cancelStroke(); beginGesture() }
                }
                Mode.FINGER_PENDING, Mode.PAN1, Mode.IDLE, Mode.GESTURE, Mode.SCRUB -> beginGesture()
                Mode.ERASE, Mode.LASSO -> if (ptrs[actorId]?.isPen == false) { abortSingleActor(); beginGesture() }
                else -> {}
            }
        }
    }

    private fun onFingerUp(ev: MotionEvent, idx: Int, p: Ptr, canceled: Boolean) {
        if (p.ignored) return
        if (canceled) {
            statPalmRejected++
            if (mode == Mode.DRAW && activeIsFinger) cancelStroke()
            seqMoved = true // never count as a tap
        }
        if (p.id == actorId && mode == Mode.DRAW) {
            if (!canceled) { addSamples(ev, idx, finalSample = true); commitStroke() }
            mode = Mode.IDLE; actorId = -1
        } else if (p.id == actorId && (mode == Mode.ERASE || mode == Mode.LASSO || mode == Mode.EYEDROP || mode == Mode.PAN1 ||
                    mode == Mode.SEL_MOVE || mode == Mode.SEL_SCALE || mode == Mode.SEL_ROTATE || mode == Mode.FINGER_PENDING)) {
            finishSingleActor(p)
            mode = Mode.IDLE; actorId = -1
        }
        if (mode == Mode.GESTURE || mode == Mode.SCRUB) {
            ptrs.remove(p.id)
            val left = activeFingers()
            if (left.size >= 2) rebaseline(left)
            else if (left.size == 1 && prefs.action(Prefs.G_ONE_FINGER_DRAG) == GestureAction.PAN && mode == Mode.GESTURE) {
                mode = Mode.PAN1; actorId = left[0].id
            } else if (left.isEmpty() || mode == Mode.SCRUB) {
                // keep SCRUB/GESTURE until all fingers lifted
            }
        }
    }

    private fun onAllUp() {
        removeCallbacks(longPress)
        val now = SystemClock.uptimeMillis()
        val wasGesture = mode == Mode.GESTURE || mode == Mode.SCRUB || mode == Mode.PAN1
        if (mode == Mode.DRAW) commitStroke()
        if (mode == Mode.ERASE) finishErase()
        if (wasGesture) endGesture()
        // tap detection (fingers only)
        if (!seqMoved && seqMaxFingers > 0 && now - seqStart < 320) {
            when (seqMaxFingers) {
                1 -> if (!fingersDraw() || tool == Tool.PAN) {
                    if (now - lastTapTime < 320 && hypot(lastTapX - gLastX, lastTapY - gLastY) < 40 * density) {
                        lastTapTime = 0
                        performAction(prefs.action(Prefs.G_DOUBLE_TAP))
                    } else { lastTapTime = now; lastTapX = gLastX; lastTapY = gLastY }
                }
                2 -> {
                    val dbl = prefs.action(Prefs.G_TWO_DOUBLE_TAP)
                    if (dbl != GestureAction.NONE && pendingTwoTap != null && now - lastTwoTapTime < 360) {
                        removeCallbacks(pendingTwoTap); pendingTwoTap = null
                        performAction(dbl)
                    } else if (dbl != GestureAction.NONE) {
                        lastTwoTapTime = now
                        val r = Runnable { pendingTwoTap = null; performAction(prefs.action(Prefs.G_TWO_TAP)) }
                        pendingTwoTap = r
                        postDelayed(r, 280)
                    } else performAction(prefs.action(Prefs.G_TWO_TAP))
                }
                3 -> performAction(prefs.action(Prefs.G_THREE_TAP))
                else -> performAction(prefs.action(Prefs.G_FOUR_TAP))
            }
        }
        seqMaxFingers = 0
        if (mode != Mode.DRAW) { mode = Mode.IDLE; actorId = -1 }
        ptrs.clear()
        invalidate()
    }

    private var gLastX = 0f; private var gLastY = 0f

    private fun onFingerLongPress() {
        if (mode != Mode.FINGER_PENDING) return
        val p = ptrs[actorId] ?: return
        if (p.moved > slop) return
        seqMoved = true
        haptic(true)
        val act = prefs.action(Prefs.G_LONG_PRESS)
        if (act == GestureAction.EYEDROPPER) { mode = Mode.EYEDROP; updateEyedrop(p.x, p.y) }
        else { mode = Mode.CONSUMED; performAction(act) }
    }

    // ------------------------------------------------------------------ move
    private fun onMove(ev: MotionEvent) {
        for (i in 0 until ev.pointerCount) {
            val p = ptrs[ev.getPointerId(i)] ?: continue
            val nx = ev.getX(i); val ny = ev.getY(i)
            p.moved = max(p.moved, hypot(nx - p.downX, ny - p.downY))
            p.x = nx; p.y = ny
            if (!p.isPen && !p.ignored && p.moved > slop) seqMoved = true
            if (!p.isPen && !p.ignored) { gLastX = nx; gLastY = ny }
        }
        when (mode) {
            Mode.DRAW -> {
                val idx = ev.findPointerIndex(actorId)
                if (idx >= 0) {
                    addSamples(ev, idx, finalSample = false)
                    if (!activeIsFinger) predict(ev)
                    invalidate()
                }
            }
            Mode.ERASE -> {
                val idx = ev.findPointerIndex(actorId)
                if (idx >= 0) {
                    for (h in 0 until ev.historySize) eraseAt(ev.getHistoricalX(idx, h), ev.getHistoricalY(idx, h))
                    eraseAt(ev.getX(idx), ev.getY(idx))
                    eraseX = ev.getX(idx); eraseY = ev.getY(idx)
                    invalidate()
                }
            }
            Mode.LASSO -> {
                val idx = ev.findPointerIndex(actorId)
                if (idx >= 0) { lassoAdd(ev.getX(idx), ev.getY(idx)); invalidate() }
            }
            Mode.EYEDROP -> {
                val idx = ev.findPointerIndex(actorId)
                if (idx >= 0) updateEyedrop(ev.getX(idx), ev.getY(idx))
            }
            Mode.FINGER_PENDING -> {
                val p = ptrs[actorId] ?: return
                if (p.moved > slop) {
                    removeCallbacks(longPress)
                    if (prefs.action(Prefs.G_ONE_FINGER_DRAG) == GestureAction.PAN) {
                        mode = Mode.PAN1; gestureActive = true; gCx = p.x; gCy = p.y
                    } else mode = Mode.CONSUMED
                }
            }
            Mode.PAN1 -> {
                val p = ptrs[actorId] ?: return
                gestureActive = true
                cam.panBy(p.x - gCx, p.y - gCy); gCx = p.x; gCy = p.y
                onCameraMoved()
            }
            Mode.GESTURE -> gestureMove()
            Mode.SCRUB -> scrubMove()
            Mode.SEL_MOVE, Mode.SEL_SCALE, Mode.SEL_ROTATE -> {
                val p = ptrs[actorId] ?: return
                selectionManipMove(p.x, p.y)
            }
            else -> {}
        }
    }

    // ------------------------------------------------------------------ gestures
    private fun beginGesture() {
        mode = Mode.GESTURE
        gStarted = false
        actorId = -1
        rebaseline(activeFingers())
    }

    private fun rebaseline(f: List<Ptr>) {
        if (f.isEmpty()) return
        gCx = f.map { it.x }.average().toFloat(); gCy = f.map { it.y }.average().toFloat()
        if (f.size >= 2) {
            gDist = max(hypot(f[1].x - f[0].x, f[1].y - f[0].y), 1f)
            gAngle = atan2(f[1].y - f[0].y, f[1].x - f[0].x)
        }
        gCount = f.size
        gRawRot = cam.rotation
    }

    private fun gestureMove() {
        val f = activeFingers()
        if (f.size < 2) return
        if (f.size != gCount) { rebaseline(f); return }
        val cx = f.map { it.x }.average().toFloat(); val cy = f.map { it.y }.average().toFloat()
        val dist = max(hypot(f[1].x - f[0].x, f[1].y - f[0].y), 1f)
        val angle = atan2(f[1].y - f[0].y, f[1].x - f[0].x)
        if (!gStarted) {
            var da = abs(angle - gAngle); if (da > PI) da = (2 * PI - da).toFloat()
            val moved = hypot(cx - gCx, cy - gCy) > slop || abs(dist - gDist) > slop * 1.5f || (prefs.rotateEnabled && da > 0.08f)
            if (!moved) return
            gStarted = true
            seqMoved = true
            // three fingers moving sideways = undo/redo scrub
            if (f.size == 3 && prefs.action(Prefs.G_THREE_SWIPE) != GestureAction.NONE && abs(cx - gCx) > 1.4f * abs(cy - gCy)) {
                mode = Mode.SCRUB; scrubAccum = 0f; gCx = cx; return
            }
            rebaseline(f); return
        }
        gestureActive = true
        val scaleF = (dist / gDist).toDouble()
        var dAng = 0.0
        if (prefs.rotateEnabled && f.size == 2) {
            var d = (angle - gAngle).toDouble()
            if (d > PI) d -= 2 * PI; if (d < -PI) d += 2 * PI
            gRawRot += d
            var target = gRawRot
            if (prefs.rotateSnap) {
                val q = PI / 2
                val snapped = Math.round(target / q) * q
                if (abs(target - snapped) < Math.toRadians(5.0)) target = snapped
            }
            dAng = target - cam.rotation
        }
        cam.applyPinch(gCx, gCy, cx, cy, scaleF, dAng, MIN_SCALE, MAX_SCALE)
        gCx = cx; gCy = cy; gDist = dist; gAngle = angle
        onCameraMoved()
    }

    private fun scrubMove() {
        val f = activeFingers()
        if (f.isEmpty()) return
        val cx = f.map { it.x }.average().toFloat()
        scrubAccum += cx - gCx; gCx = cx
        val step = 44f * density
        while (scrubAccum <= -step) { scrubAccum += step; if (history.undo()) haptic() }
        while (scrubAccum >= step) { scrubAccum -= step; if (history.redo()) haptic() }
    }

    private fun endGesture() {
        if (gestureActive) {
            gestureActive = false
            invalidateContent() // re-record crisp display list at the final camera
            listener?.onCameraChanged()
        }
        gStarted = false
    }

    private fun onCameraMoved() {
        postInvalidateOnAnimation()
        listener?.onCameraChanged()
    }

    // ------------------------------------------------------------------ tool actions
    private fun startToolAction(t: Tool, ev: MotionEvent, idx: Int, p: Ptr, finger: Boolean) {
        when (t) {
            Tool.BRUSH -> beginStroke(ev, idx, finger)
            Tool.ERASER -> {
                if (doc.active().locked) { mode = Mode.CONSUMED; return }
                mode = Mode.ERASE
                eraseBefore = ArrayList(doc.active().strokes)
                eraseX = p.x; eraseY = p.y
                eraseAt(p.x, p.y)
            }
            Tool.LASSO -> { mode = Mode.LASSO; lassoPts.clear(); lassoPath.reset(); lassoAdd(p.x, p.y) }
            Tool.EYEDROPPER -> { mode = Mode.EYEDROP; updateEyedrop(p.x, p.y) }
            Tool.PAN -> { mode = Mode.PAN1; gCx = p.x; gCy = p.y }
        }
        invalidate()
    }

    private fun finishSingleActor(p: Ptr) {
        when (mode) {
            Mode.ERASE -> finishErase()
            Mode.LASSO -> finishLasso()
            Mode.EYEDROP -> { color = eyeColor; listener?.onColorPicked(eyeColor); haptic() }
            Mode.PAN1 -> endGesture()
            Mode.SEL_MOVE, Mode.SEL_SCALE, Mode.SEL_ROTATE -> commitSelectionTransform()
            Mode.FINGER_PENDING -> {}
            else -> {}
        }
        if (p.isPen) { mode = Mode.IDLE }
    }

    private fun abortSingleActor() {
        when (mode) {
            Mode.ERASE -> finishErase()
            Mode.LASSO -> { lassoPts.clear(); lassoPath.reset() }
            else -> {}
        }
        mode = Mode.IDLE; actorId = -1
    }

    // ------------------------------------------------------------------ strokes
    private fun beginStroke(ev: MotionEvent, idx: Int, finger: Boolean) {
        if (doc.active().locked || !doc.active().visible) { mode = Mode.CONSUMED; return }
        mode = Mode.DRAW
        activeIsFinger = finger
        activeStart = SystemClock.uptimeMillis()
        activeDownTime = ev.eventTime
        shaped = null
        strokeScreenLen = 0f
        val sx = ev.getX(idx); val sy = ev.getY(idx)
        val wx = cam.screenToWorldX(sx, sy); val wy = cam.screenToWorldY(sx, sy)
        val lb = brush.copy().apply { taperEnd = 0f }
        liveBrush = lb
        val width = (brush.size / cam.scale).toFloat()
        active = Stroke(lb, color, width, wx, wy)
        lastLx = 0f; lastLy = 0f; rawLx = 0f; rawLy = 0f
        holdAnchorX = sx; holdAnchorY = sy
        if (!finger) try { predictor?.record(ev) } catch (_: Exception) { predictor = null }
        addSample(sx, sy, ev.getPressure(idx), tiltOf(ev, idx, -1), 0f, finger, force = true)
        scheduleShapeHold()
    }

    private fun tiltOf(ev: MotionEvent, idx: Int, h: Int): Float {
        if (!prefs.tiltEnabled) return 0f
        return if (h < 0) ev.getAxisValue(MotionEvent.AXIS_TILT, idx) else ev.getHistoricalAxisValue(MotionEvent.AXIS_TILT, idx, h)
    }

    private fun addSamples(ev: MotionEvent, idx: Int, finalSample: Boolean) {
        val s = active ?: return
        for (h in 0 until ev.historySize) {
            addSample(ev.getHistoricalX(idx, h), ev.getHistoricalY(idx, h), ev.getHistoricalPressure(idx, h), tiltOf(ev, idx, h),
                (ev.getHistoricalEventTime(h) - activeDownTime).toFloat(), activeIsFinger, false)
        }
        addSample(ev.getX(idx), ev.getY(idx), ev.getPressure(idx), tiltOf(ev, idx, -1), (ev.eventTime - activeDownTime).toFloat(), activeIsFinger, false)
        if (finalSample && shaped == null && s.n > 0) {
            // catch up with the real pen position that smoothing lagged behind
            if (hypot(rawLx - lastLx, rawLy - lastLy) * cam.scale > 0.5) {
                s.add(rawLx, rawLy, s.p(s.n - 1), s.tilt(s.n - 1), s.t(s.n - 1) + 1)
            }
        }
        val x = ev.getX(idx); val y = ev.getY(idx)
        if (hypot(x - holdAnchorX, y - holdAnchorY) > 3f * density) {
            holdAnchorX = x; holdAnchorY = y
            if (!finalSample) scheduleShapeHold()
            if (strokeScreenLen > slop) removeCallbacks(penHold)
        }
    }

    private fun mapPressure(raw: Float, finger: Boolean): Float {
        if (finger) return prefs.fingerPressure
        val lo = prefs.pressureMin; val hi = max(prefs.pressureMax, lo + 0.05f)
        val n = ((raw - lo) / (hi - lo)).coerceIn(0f, 1f)
        return Math.pow(n.toDouble(), prefs.pressureGamma.toDouble()).toFloat().coerceIn(0.02f, 1f)
    }

    private fun addSample(sx: Float, sy: Float, rawP: Float, tilt: Float, t: Float, finger: Boolean, force: Boolean) {
        val s = active ?: return
        val lx = (cam.screenToWorldX(sx, sy) - s.ox).toFloat()
        val ly = (cam.screenToWorldY(sx, sy) - s.oy).toFloat()
        val p = mapPressure(rawP, finger)
        lastPressure = p; lastTilt = tilt
        if (shaped != null) {
            if (shaped == ShapeRecognizer.Kind.LINE && s.n >= 2) {
                // rubber-band the end of a snapped line, with angle snapping to 15deg near axes
                var ex = lx; var ey = ly
                val x0 = s.x(0); val y0 = s.y(0)
                val ang = atan2(ey - y0, ex - x0)
                val deg = Math.toDegrees(ang.toDouble())
                val snapped = Math.round(deg / 45.0) * 45.0
                if (abs(deg - snapped) < 4) {
                    val len = hypot(ex - x0, ey - y0)
                    val a = Math.toRadians(snapped)
                    ex = (x0 + len * kotlin.math.cos(a)).toFloat(); ey = (y0 + len * kotlin.math.sin(a)).toFloat()
                }
                s.setXY(s.n - 1, ex, ey)
            }
            return
        }
        rawLx = lx; rawLy = ly
        if (s.n == 0 || force) {
            s.add(lx, ly, p, tilt, t); lastLx = lx; lastLy = ly; return
        }
        val k = 1f - (s.brush.smoothing.coerceIn(0f, 1f) * 0.88f)
        val nx = lastLx + (lx - lastLx) * k
        val ny = lastLy + (ly - lastLy) * k
        val dScreen = (hypot(nx - lastLx, ny - lastLy) * cam.scale).toFloat()
        if (dScreen < 0.35f) return
        strokeScreenLen += dScreen
        val pp = s.p(s.n - 1) * 0.35f + p * 0.65f // light pressure smoothing
        s.add(nx, ny, pp, tilt, t)
        lastLx = nx; lastLy = ny
    }

    private fun predict(ev: MotionEvent) {
        predN = 0
        val pr = predictor ?: return
        if (!prefs.prediction || Build.VERSION.SDK_INT < 34) return
        val out = try {
            pr.record(ev)
            pr.predict(ev.eventTimeNanos + 14_000_000L)
        } catch (_: Exception) { predictor = null; null } ?: return
        val idx = out.findPointerIndex(actorId).let { if (it < 0) 0 else it }
        for (h in 0 until out.historySize) {
            if (predN >= predX.size) break
            predX[predN] = out.getHistoricalX(idx, h); predY[predN] = out.getHistoricalY(idx, h); predN++
        }
        if (predN < predX.size) { predX[predN] = out.getX(idx); predY[predN] = out.getY(idx); predN++ }
        out.recycle()
    }

    private fun scheduleShapeHold() {
        removeCallbacks(shapeHold)
        if (prefs.shapeHold && mode == Mode.DRAW && brush.type.kind != RenderKind.STAMP) postDelayed(shapeHold, prefs.shapeHoldMs.toLong())
    }

    private fun onShapeHold() {
        val s = active ?: return
        if (mode != Mode.DRAW || shaped != null || strokeScreenLen < 24f * density) return
        val xs = FloatArray(s.n) { s.x(it) }; val ys = FloatArray(s.n) { s.y(it) }
        val res = ShapeRecognizer.recognize(xs, ys, s.n) ?: return
        var avgP = 0f; var avgT = 0f
        for (i in 0 until s.n) { avgP += s.p(i); avgT += s.tilt(i) }
        avgP /= s.n; avgT /= s.n
        val shapeBrush = brush.copy().apply { taperStart = 0f; taperEnd = 0f; smoothing = 0f }
        val ns = Stroke(shapeBrush, s.color, s.width, s.ox, s.oy)
        for (i in res.xs.indices) ns.add(res.xs[i], res.ys[i], avgP, avgT, i.toFloat())
        active = ns
        liveBrush = shapeBrush
        shaped = res.kind
        statLastShape = res.kind
        predN = 0
        haptic(true)
        listener?.onShapeRecognized(res.kind)
        invalidate()
    }

    private fun cancelStroke() {
        active = null; liveBrush = null; shaped = null; predN = 0
        removeCallbacks(shapeHold); removeCallbacks(penHold)
        if (mode == Mode.DRAW) mode = Mode.IDLE
        invalidate()
    }

    private fun commitStroke() {
        val s = active ?: return
        removeCallbacks(shapeHold); removeCallbacks(penHold)
        active = null; predN = 0
        if (mode == Mode.DRAW) mode = Mode.IDLE
        if (s.n == 0) return
        val finalBrush = if (shaped != null) (liveBrush ?: brush).copy() else brush.copy()
        val fin = Stroke(finalBrush, s.color, s.width, s.ox, s.oy)
        fin.pts = s.pts; fin.n = s.n
        val layer = doc.active()
        history.run(LambdaCommand({ layer.strokes.add(fin) }, { layer.strokes.remove(fin) }))
        statStrokesCommitted++
        shaped = null; liveBrush = null
    }

    // ------------------------------------------------------------------ eraser
    private fun eraseAt(sx: Float, sy: Float) {
        val layer = doc.active()
        if (layer.locked) return
        val wx = cam.screenToWorldX(sx, sy); val wy = cam.screenToWorldY(sx, sy)
        val r = eraserSizePx / 2f / cam.scale
        var changed = false
        var i = 0
        val list = layer.strokes
        while (i < list.size) {
            val s = list[i]
            val rr = r + s.width / 2.0
            if (wx + rr < s.worldMinX() || wx - rr > s.worldMaxX() || wy + rr < s.worldMinY() || wy - rr > s.worldMaxY()) { i++; continue }
            val pieces = eraseStroke(s, (wx - s.ox).toFloat(), (wy - s.oy).toFloat(), rr.toFloat())
            if (pieces == null) { i++; continue }
            list.removeAt(i)
            list.addAll(i, pieces)
            i += pieces.size
            changed = true
        }
        if (changed) invalidateContent()
    }

    /** Returns null if not hit, else the remaining pieces (possibly empty). */
    private fun eraseStroke(s: Stroke, cx: Float, cy: Float, r: Float): List<Stroke>? {
        var hit = false
        for (i in 0 until s.n) {
            val d = if (i == 0) hypot(s.x(0) - cx, s.y(0) - cy)
            else ShapeRecognizer.distToSegment(cx, cy, s.x(i - 1), s.y(i - 1), s.x(i), s.y(i))
            if (d <= r) { hit = true; break }
        }
        if (!hit) return null
        if (eraserWhole || s.brush.type.kind == RenderKind.FILL || s.n < 2) return emptyList()
        // Slice: densify then keep runs of points outside the eraser
        val pieces = ArrayList<Stroke>()
        var cur: Stroke? = null
        val step = max(r * 0.5f, 1e-6f)
        fun emit(x: Float, y: Float, p: Float, t: Float, tm: Float) {
            if (hypot(x - cx, y - cy) <= r) {
                cur?.let { if (it.n >= 2) pieces.add(it) }
                cur = null
            } else {
                val c = cur ?: s.slice(0, 0).also { cur = it }
                c.add(x, y, p, t, tm)
            }
        }
        emit(s.x(0), s.y(0), s.p(0), s.tilt(0), s.t(0))
        for (i in 1 until s.n) {
            val x0 = s.x(i - 1); val y0 = s.y(i - 1); val x1 = s.x(i); val y1 = s.y(i)
            val len = hypot(x1 - x0, y1 - y0)
            val k = if (len > step) (len / step).toInt() else 0
            for (j in 1..k) {
                val t = j.toFloat() / (k + 1)
                emit(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, s.p(i - 1) + (s.p(i) - s.p(i - 1)) * t, s.tilt(i), s.t(i - 1) + (s.t(i) - s.t(i - 1)) * t)
            }
            emit(x1, y1, s.p(i), s.tilt(i), s.t(i))
        }
        cur?.let { if (it.n >= 2) pieces.add(it) }
        // sliced pieces shouldn't get fresh tapers at the cut
        for (pc in pieces) { pc.brush.taperStart = 0f; pc.brush.taperEnd = 0f; pc.invalidate() }
        return pieces
    }

    private fun finishErase() {
        val before = eraseBefore ?: return
        eraseBefore = null
        val layer = doc.active()
        if (before.size != layer.strokes.size || before.indices.any { before[it] !== layer.strokes[it] }) {
            history.push(SnapshotCommand(layer, before, ArrayList(layer.strokes)))
        }
        invalidateContent()
    }

    // ------------------------------------------------------------------ eyedropper
    private fun updateEyedrop(sx: Float, sy: Float) {
        eyeX = sx; eyeY = sy
        eyeColor = pickColor(sx, sy)
        invalidate()
    }

    fun pickColor(sx: Float, sy: Float): Int {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(-sx, -sy)
        c.drawColor(doc.background.paperColor)
        SceneRenderer.drawLayers(c, cam, doc, doc.layers.indices)
        val px = bmp.getPixel(0, 0)
        bmp.recycle()
        return px or (0xFF shl 24)
    }

    // ------------------------------------------------------------------ lasso & selection
    private fun lassoAdd(x: Float, y: Float) {
        if (lassoPts.isEmpty()) lassoPath.moveTo(x, y) else lassoPath.lineTo(x, y)
        lassoPts.add(x); lassoPts.add(y)
    }

    private fun finishLasso() {
        val pts = lassoPts.toFloatArray()
        lassoPts.clear(); lassoPath.reset()
        if (pts.size < 6) { clearSelection(); return }
        val layer = doc.active()
        selection.clear(); selectionSet.clear()
        for (s in layer.strokes) {
            var inside = 0; var total = 0
            val stride = max(1, s.n / 40)
            var i = 0
            while (i < s.n) {
                val sx = cam.worldToScreenX(s.ox + s.x(i), s.oy + s.y(i)); val sy = cam.worldToScreenY(s.ox + s.x(i), s.oy + s.y(i))
                if (pointInPoly(sx, sy, pts)) inside++
                total++
                i += stride
            }
            if (total > 0 && inside * 2 >= total) { selection.add(s); selectionSet.add(s) }
        }
        resetSelTransform()
        invalidateContent()
        listener?.onSelectionChanged(selection.size)
    }

    private fun pointInPoly(x: Float, y: Float, p: FloatArray): Boolean {
        var c = false
        var j = p.size / 2 - 1
        for (i in 0 until p.size / 2) {
            val xi = p[i * 2]; val yi = p[i * 2 + 1]; val xj = p[j * 2]; val yj = p[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-9f) + xi) c = !c
            j = i
        }
        return c
    }

    fun selectAllInLayer() {
        selection.clear(); selectionSet.clear()
        selection.addAll(doc.active().strokes); selectionSet.addAll(selection)
        resetSelTransform(); invalidateContent(); listener?.onSelectionChanged(selection.size)
    }

    private fun resetSelTransform() {
        selTx = 0.0; selTy = 0.0; selScale = 1.0; selRot = 0.0
        val b = SceneRenderer.contentBounds(doc, selection)
        if (b != null) { selBounds = b; selPivotX = (b[0] + b[2]) / 2; selPivotY = (b[1] + b[3]) / 2 }
    }

    fun clearSelection() {
        if (selection.isEmpty()) return
        selection.clear(); selectionSet.clear()
        invalidateContent()
        listener?.onSelectionChanged(0)
    }

    /** world -> transformed world for the live selection transform */
    private fun selMapX(x: Double, y: Double): Double {
        val dx = x - selPivotX; val dy = y - selPivotY
        return selPivotX + selScale * (dx * kotlin.math.cos(selRot) - dy * kotlin.math.sin(selRot)) + selTx
    }

    private fun selMapY(x: Double, y: Double): Double {
        val dx = x - selPivotX; val dy = y - selPivotY
        return selPivotY + selScale * (dx * kotlin.math.sin(selRot) + dy * kotlin.math.cos(selRot)) + selTy
    }

    private fun computeSelExtra() {
        val px = doubleArrayOf(selPivotX, selPivotX + 100 / cam.scale, selPivotX)
        val py = doubleArrayOf(selPivotY, selPivotY, selPivotY + 100 / cam.scale)
        val src = FloatArray(6); val dst = FloatArray(6)
        for (i in 0 until 3) {
            src[i * 2] = cam.worldToScreenX(px[i], py[i]); src[i * 2 + 1] = cam.worldToScreenY(px[i], py[i])
            val tx = selMapX(px[i], py[i]); val ty = selMapY(px[i], py[i])
            dst[i * 2] = cam.worldToScreenX(tx, ty); dst[i * 2 + 1] = cam.worldToScreenY(tx, ty)
        }
        selExtra.setPolyToPoly(src, 0, dst, 0, 3)
    }

    private fun selectionScreenCorners(): FloatArray {
        val b = selBounds
        val pad = 8 / cam.scale
        val xs = doubleArrayOf(b[0] - pad, b[2] + pad, b[2] + pad, b[0] - pad)
        val ys = doubleArrayOf(b[1] - pad, b[1] - pad, b[3] + pad, b[3] + pad)
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val tx = selMapX(xs[i], ys[i]); val ty = selMapY(xs[i], ys[i])
            out[i * 2] = cam.worldToScreenX(tx, ty); out[i * 2 + 1] = cam.worldToScreenY(tx, ty)
        }
        return out
    }

    private fun rotateHandle(pts: FloatArray): Pair<Float, Float> {
        val mx = (pts[0] + pts[2]) / 2; val my = (pts[1] + pts[3]) / 2
        val ex = pts[2] - pts[0]; val ey = pts[3] - pts[1]
        val len = max(hypot(ex, ey), 1f)
        // outward normal (pointing away from box centre)
        var nx = ey / len; var ny = -ex / len
        val cx = (pts[0] + pts[4]) / 2; val cy = (pts[1] + pts[5]) / 2
        if ((mx - cx) * nx + (my - cy) * ny < 0) { nx = -nx; ny = -ny }
        val d = 36f * density
        return (mx + nx * d) to (my + ny * d)
    }

    private fun beginSelectionManip(x: Float, y: Float): Boolean {
        val pts = selectionScreenCorners()
        val hs = 22f * density
        val (rx, ry) = rotateHandle(pts)
        selStartX = x; selStartY = y
        selStartScale = selScale; selStartRot = selRot; selStartTx = selTx; selStartTy = selTy
        mode = when {
            hypot(x - pts[4], y - pts[5]) < hs -> Mode.SEL_SCALE
            hypot(x - rx, y - ry) < hs -> Mode.SEL_ROTATE
            pointInPoly(x, y, pts) -> Mode.SEL_MOVE
            else -> { clearSelection(); return false }
        }
        return true
    }

    private fun selectionManipMove(x: Float, y: Float) {
        val pcx = cam.worldToScreenX(selPivotX + selTx, selPivotY + selTy)
        val pcy = cam.worldToScreenY(selPivotX + selTx, selPivotY + selTy)
        when (mode) {
            Mode.SEL_MOVE -> {
                val w0x = cam.screenToWorldX(selStartX, selStartY); val w0y = cam.screenToWorldY(selStartX, selStartY)
                val w1x = cam.screenToWorldX(x, y); val w1y = cam.screenToWorldY(x, y)
                selTx = selStartTx + (w1x - w0x); selTy = selStartTy + (w1y - w0y)
            }
            Mode.SEL_SCALE -> {
                val d0 = max(hypot(selStartX - pcx, selStartY - pcy), 1f)
                val d1 = hypot(x - pcx, y - pcy)
                selScale = (selStartScale * d1 / d0).coerceIn(0.01, 100.0)
            }
            Mode.SEL_ROTATE -> {
                val a0 = atan2(selStartY - pcy, selStartX - pcx); val a1 = atan2(y - pcy, x - pcx)
                var r = selStartRot + (a1 - a0)
                val q = PI / 4
                val snapped = Math.round(r / q) * q
                if (abs(r - snapped) < Math.toRadians(3.0)) r = snapped
                selRot = r
            }
            else -> {}
        }
        invalidate()
    }

    private fun commitSelectionTransform() {
        if (selTx == 0.0 && selTy == 0.0 && selScale == 1.0 && selRot == 0.0) return
        val layer = doc.active()
        val before = ArrayList(layer.strokes)
        val cosR = kotlin.math.cos(selRot).toFloat(); val sinR = kotlin.math.sin(selRot).toFloat(); val sc = selScale.toFloat()
        val replaced = HashMap<Stroke, Stroke>()
        for (s in selection) {
            val ns = s.duplicate()
            ns.ox = selMapX(s.ox, s.oy); ns.oy = selMapY(s.ox, s.oy)
            for (i in 0 until ns.n) {
                val x = s.x(i); val y = s.y(i)
                ns.setXY(i, sc * (x * cosR - y * sinR), sc * (x * sinR + y * cosR))
            }
            ns.width = s.width * sc
            ns.invalidate()
            replaced[s] = ns
        }
        for (i in layer.strokes.indices) replaced[layer.strokes[i]]?.let { layer.strokes[i] = it }
        history.push(SnapshotCommand(layer, before, ArrayList(layer.strokes)))
        val newSel = selection.map { replaced[it] ?: it }
        selection.clear(); selection.addAll(newSel); selectionSet.clear(); selectionSet.addAll(newSel)
        resetSelTransform()
        invalidateContent()
    }

    fun deleteSelection() {
        if (selection.isEmpty()) return
        val layer = doc.active(); val before = ArrayList(layer.strokes)
        layer.strokes.removeAll(selectionSet)
        history.push(SnapshotCommand(layer, before, ArrayList(layer.strokes)))
        clearSelection()
    }

    fun duplicateSelection() {
        if (selection.isEmpty()) return
        val layer = doc.active(); val before = ArrayList(layer.strokes)
        val off = 24 / cam.scale
        val copies = selection.map { it.duplicate().also { d -> d.ox += off; d.oy += off; d.invalidate() } }
        layer.strokes.addAll(copies)
        history.push(SnapshotCommand(layer, before, ArrayList(layer.strokes)))
        selection.clear(); selection.addAll(copies); selectionSet.clear(); selectionSet.addAll(copies)
        resetSelTransform(); invalidateContent(); listener?.onSelectionChanged(selection.size)
    }

    fun recolorSelection(c: Int) {
        if (selection.isEmpty()) return
        val layer = doc.active(); val before = ArrayList(layer.strokes)
        val replaced = HashMap<Stroke, Stroke>()
        for (s in selection) replaced[s] = s.duplicate().also { it.color = c }
        for (i in layer.strokes.indices) replaced[layer.strokes[i]]?.let { layer.strokes[i] = it }
        history.push(SnapshotCommand(layer, before, ArrayList(layer.strokes)))
        val newSel = selection.map { replaced[it] ?: it }
        selection.clear(); selection.addAll(newSel); selectionSet.clear(); selectionSet.addAll(newSel)
        invalidateContent()
    }

    fun bringSelectionToFront() {
        if (selection.isEmpty()) return
        val layer = doc.active(); val before = ArrayList(layer.strokes)
        layer.strokes.removeAll(selectionSet); layer.strokes.addAll(selection)
        history.push(SnapshotCommand(layer, before, ArrayList(layer.strokes)))
        invalidateContent()
    }

    // ------------------------------------------------------------------ camera helpers
    fun zoomFit(animate: Boolean = true) {
        val b = SceneRenderer.contentBounds(doc) ?: return zoomReset(animate)
        val bw = (b[2] - b[0]).coerceAtLeast(1e-9); val bh = (b[3] - b[1]).coerceAtLeast(1e-9)
        val s = min(width / bw, height / bh) * 0.82
        animateTo((b[0] + b[2]) / 2, (b[1] + b[3]) / 2, s.coerceIn(MIN_SCALE, MAX_SCALE), 0.0, animate)
    }

    fun zoomReset(animate: Boolean = true) {
        animateTo(cam.cx, cam.cy, 1.0, 0.0, animate)
    }

    fun zoomBy(f: Double, animate: Boolean = true) {
        animateTo(cam.cx, cam.cy, (cam.scale * f).coerceIn(MIN_SCALE, MAX_SCALE), cam.rotation, animate)
    }

    private var anim: ValueAnimator? = null

    fun animateTo(cx: Double, cy: Double, scale: Double, rot: Double, animate: Boolean = true) {
        anim?.cancel()
        if (!animate) {
            cam.cx = cx; cam.cy = cy; cam.scale = scale; cam.setRotationRad(rot)
            invalidateContent(); listener?.onCameraChanged(); return
        }
        val c0x = cam.cx; val c0y = cam.cy; val ls0 = ln(cam.scale); val ls1 = ln(scale); val r0 = cam.rotation
        var dr = rot - r0
        if (dr > PI) dr -= 2 * PI; if (dr < -PI) dr += 2 * PI
        gestureActive = true
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val t = (it.animatedValue as Float).toDouble()
                val ls = ls0 + (ls1 - ls0) * t
                cam.scale = exp(ls)
                cam.cx = c0x + (cx - c0x) * t; cam.cy = c0y + (cy - c0y) * t
                cam.setRotationRad(r0 + dr * t)
                onCameraMoved()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { gestureActive = false; invalidateContent(); listener?.onCameraChanged() }
            })
            start()
        }
    }

    fun zoomPercent(): Int = (cam.scale * 100).roundToInt()
    fun rotationDegrees(): Int = Math.toDegrees(cam.rotation).roundToInt()

    // ------------------------------------------------------------------ misc
    fun performAction(a: GestureAction) {
        when (a) {
            GestureAction.NONE -> {}
            GestureAction.UNDO -> undo()
            GestureAction.REDO -> redo()
            GestureAction.ZOOM_FIT -> zoomFit()
            GestureAction.ZOOM_RESET -> zoomReset()
            else -> listener?.onAction(a)
        }
    }

    private fun cancelTimers() {
        removeCallbacks(longPress); removeCallbacks(penHold); removeCallbacks(shapeHold)
    }

    private fun haptic(strong: Boolean = false) {
        performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
    }

    fun thumbnail(): Bitmap = SceneRenderer.renderToBitmap(doc, 640, true, bg, density, viewCam = cam)

    fun exportPng(maxSide: Int, withBackground: Boolean): Bitmap = SceneRenderer.renderToBitmap(doc, maxSide, withBackground, if (withBackground) bg else null, density, viewCam = cam)

    companion object {
        const val MIN_SCALE = 1e-6
        const val MAX_SCALE = 1e6
        private const val FLAG_CANCELED = 0x20 // MotionEvent.FLAG_CANCELED (API 33)
    }
}
