package com.akshit.folio

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.akshit.folio.ui.IconButton
import com.akshit.folio.ui.UI
import kotlin.math.max

/**
 * Pen Lab: shows exactly what the pen and fingers report (pressure, tilt, orientation, hover
 * distance, buttons, sample rate, contact size) and logs any unknown key / button events, so
 * Xiaomi-specific pen features can be discovered and mapped.
 */
class PenLabActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var log: TextView
    private val logLines = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UI.vbox(this)
        root.setBackgroundColor(UI.BG)
        root.fitsSystemWindows = true
        val head = UI.hbox(this)
        head.setPadding(UI.dp(this, 8f), UI.dp(this, 8f), UI.dp(this, 16f), UI.dp(this, 8f))
        head.addView(IconButton(this, "back").apply { setOnClickListener { finish() } })
        head.addView(UI.label(this, "Pen Lab", 22f, UI.TEXT, true), UI.lp(0, UI.WRAP, 1f))
        learnBtn = UI.button(this, "Learn a pen gesture", primary = true) { startLearning() }
        head.addView(learnBtn)
        head.addView(UI.button(this, "Clear") { pad.clear() }.apply { (layoutParams as? android.widget.LinearLayout.LayoutParams)?.marginStart = UI.dp(this@PenLabActivity, 8f) },
            android.widget.LinearLayout.LayoutParams(UI.WRAP, UI.WRAP).apply { marginStart = UI.dp(this@PenLabActivity, 8f) })
        root.addView(head)
        val split = UI.hbox(this)
        val side = UI.vbox(this, 12f)
        info = UI.label(this, "Touch or hover with the pen…", 13f, UI.TEXT).apply { typeface = android.graphics.Typeface.MONOSPACE }
        log = UI.label(this, "", 11.5f, UI.SUB).apply { typeface = android.graphics.Typeface.MONOSPACE }
        side.addView(info)
        side.addView(UI.label(this, "Squeeze or slide your Focus Pen here. Every key, button, scroll and axis event is logged. Tap “Learn a pen gesture”, do the gesture once, and pick what it should do on the canvas.", 12.5f, UI.SUB).apply { setPadding(0, UI.dp(this@PenLabActivity, 10f), 0, 0) })
        side.addView(UI.section(this, "Event log (buttons, keys, Xiaomi extras)"))
        side.addView(log)
        side.addView(UI.section(this, "Input devices"))
        side.addView(UI.label(this, devices(), 11f, UI.SUB).apply { typeface = android.graphics.Typeface.MONOSPACE })
        pad = Pad(this)
        val wide = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        split.orientation = if (wide) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL
        split.addView(pad, if (wide) UI.lp(0, UI.MATCH, 1.5f) else UI.lp(UI.MATCH, 0, 1.2f))
        split.addView(UI.scroll(this, side), if (wide) UI.lp(0, UI.MATCH, 1f) else UI.lp(UI.MATCH, 0, 1f))
        root.addView(split, UI.lp(UI.MATCH, 0, 1f))
        setContentView(root)
    }

    lateinit var pad: Pad

    private fun devices(): String {
        val sb = StringBuilder()
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            val s = d.sources
            val stylus = (s and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS
            val touch = (s and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN
            if (!stylus && !touch && !d.name.contains("pen", true) && !d.name.contains("stylus", true)) continue
            sb.append("• ${d.name}${if (stylus) "  [STYLUS]" else ""}${if (touch) "  [TOUCH]" else ""}\n")
            d.getMotionRange(MotionEvent.AXIS_PRESSURE)?.let { sb.append("   pressure ${it.min}..${it.max} res=${it.resolution}\n") }
            d.getMotionRange(MotionEvent.AXIS_TILT)?.let { sb.append("   tilt ${"%.2f".format(it.min)}..${"%.2f".format(it.max)} rad\n") }
            d.getMotionRange(MotionEvent.AXIS_DISTANCE)?.let { sb.append("   hover distance ${it.min}..${it.max}\n") }
        }
        return sb.toString().ifEmpty { "(no touch / stylus devices reported)" }
    }

    fun addLog(s: String) {
        logLines.addFirst("${SystemClock.uptimeMillis() % 100000}  $s")
        while (logLines.size > 30) logLines.removeLast()
        log.text = logLines.joinToString("\n")
    }

    fun setInfo(s: String) { info.text = s }

    // ---------------- learn mode: the next unknown pen event gets mapped to an action
    private var learning = false
    private lateinit var learnBtn: android.widget.TextView
    private val baseAxes = HashMap<Int, Float>()

    private fun startLearning() {
        learning = true; baseAxes.clear()
        learnBtn.text = "Waiting… squeeze / slide / press your pen"
        addLog("LEARN: do the pen gesture now")
    }

    private fun learnedKey(code: Int) {
        learning = false; learnBtn.text = "Learn a pen gesture"
        val prefs = com.akshit.folio.core.Prefs(this)
        val opts = com.akshit.folio.core.GestureAction.entries.toList()
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Got ${KeyEvent.keyCodeToString(code)} — what should it do?")
            .setItems(opts.map { it.label }.toTypedArray()) { _, w -> prefs.setPenKeyAction(code, opts[w]); addLog("MAPPED ${KeyEvent.keyCodeToString(code)} → ${opts[w].label}") }
            .setNegativeButton("Cancel", null).show()
    }

    private fun learnedAxis(axis: Int) {
        learning = false; learnBtn.text = "Learn a pen gesture"
        val prefs = com.akshit.folio.core.Prefs(this)
        prefs.put(com.akshit.folio.core.Prefs.K_SLIDE_AXIS, axis)
        addLog("MAPPED axis ${MotionEvent.axisToString(axis)} → slide (choose what slide does in Settings)")
        android.widget.Toast.makeText(this, "Slide learned on ${MotionEvent.axisToString(axis)}", android.widget.Toast.LENGTH_LONG).show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) {
            addLog("KEY ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode}) scan=${event.scanCode} ${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"} src=0x${Integer.toHexString(event.source)} dev=${event.device?.name}")
            if (learning && event.action == KeyEvent.ACTION_DOWN) { learnedKey(event.keyCode); return true }
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return super.dispatchKeyEvent(event)
            return true // swallow so pen keys don't trigger system actions while testing
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE ->
                addLog("BUTTON ${MotionEvent.actionToString(ev.actionMasked)} state=0x${Integer.toHexString(ev.buttonState)} actionButton=0x${Integer.toHexString(ev.actionButton)}")
            MotionEvent.ACTION_SCROLL -> {
                addLog("SCROLL v=${ev.getAxisValue(MotionEvent.AXIS_VSCROLL)} h=${ev.getAxisValue(MotionEvent.AXIS_HSCROLL)} src=0x${Integer.toHexString(ev.source)}")
                if (learning) { learning = false; learnBtn.text = "Learn a pen gesture"; addLog("Slide arrives as scroll — already supported. Choose what it does in Settings.") }
            }
        }
        // any unusual axis that changes (generic axes, scroll, etc.)
        val interesting = intArrayOf(MotionEvent.AXIS_SCROLL, MotionEvent.AXIS_WHEEL, MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_THROTTLE, MotionEvent.AXIS_RUDDER) +
            IntArray(16) { MotionEvent.AXIS_GENERIC_1 + it }
        for (a in interesting) {
            val v = ev.getAxisValue(a)
            val base = baseAxes.getOrPut(a) { v }
            if (kotlin.math.abs(v - base) > 0.05f) {
                addLog("AXIS ${MotionEvent.axisToString(a)} = ${"%.3f".format(v)}")
                if (learning) { learnedAxis(a); break }
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    @SuppressLint("ViewConstructor")
    class Pad(ctx: Context) : View(ctx) {
        private val lab = ctx as PenLabActivity
        private val trail = ArrayList<FloatArray>() // x,y,pressure,tool
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val grid = Paint().apply { color = 0x14FFFFFF }
        private var hx = -1f; private var hy = -1f; private var hoverDist = 0f
        private var lastT = 0L; private var rate = 0f
        private val fingers = HashMap<Int, FloatArray>()
        private var lastButtons = 0

        fun clear() { trail.clear(); invalidate() }

        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF1A1B1F.toInt())
            val step = UI.dpf(context, 24f)
            var x = 0f; while (x < width) { c.drawLine(x, 0f, x, height.toFloat(), grid); x += step }
            var y = 0f; while (y < height) { c.drawLine(0f, y, width.toFloat(), y, grid); y += step }
            for (p in trail) {
                paint.color = if (p[3] == 1f) 0xFF4C9AFF.toInt() else if (p[3] == 2f) Color.rgb(255, 120, 120) else 0x99FFFFFF.toInt()
                c.drawCircle(p[0], p[1], 1f + p[2] * UI.dpf(context, 7f), paint)
            }
            for (f in fingers.values) {
                paint.color = if (f[3] > 0f) 0x66FF5555 else 0x5532CD7F
                c.drawCircle(f[0], f[1], max(f[2] / 2f, UI.dpf(context, 12f)), paint)
            }
            if (hx >= 0) {
                paint.style = Paint.Style.STROKE; paint.color = Color.WHITE; paint.strokeWidth = 2f
                c.drawCircle(hx, hy, UI.dpf(context, 6f) + hoverDist * 4f, paint)
                paint.style = Paint.Style.FILL
            }
        }

        override fun onHoverEvent(ev: MotionEvent): Boolean {
            hx = if (ev.actionMasked == MotionEvent.ACTION_HOVER_EXIT) -1f else ev.x
            hy = ev.y
            hoverDist = ev.getAxisValue(MotionEvent.AXIS_DISTANCE)
            report(ev, 0, "HOVER")
            invalidate()
            return true
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val now = SystemClock.uptimeMillis()
            if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
                val n = ev.historySize + 1
                val dt = now - lastT
                if (dt > 0) rate = rate * 0.85f + 0.15f * (n * 1000f / dt)
            }
            lastT = now
            val mm = resources.displayMetrics.xdpi / 25.4f
            for (i in 0 until ev.pointerCount) {
                val tool = ev.getToolType(i)
                val isPen = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
                if (isPen) {
                    for (h in 0 until ev.historySize) trail.add(floatArrayOf(ev.getHistoricalX(i, h), ev.getHistoricalY(i, h), ev.getHistoricalPressure(i, h), if (tool == MotionEvent.TOOL_TYPE_ERASER) 2f else 1f))
                    trail.add(floatArrayOf(ev.getX(i), ev.getY(i), ev.getPressure(i), if (tool == MotionEvent.TOOL_TYPE_ERASER) 2f else 1f))
                } else {
                    val big = ev.getTouchMajor(i) / mm > Folio.prefs(context).palmSizeMm
                    fingers[ev.getPointerId(i)] = floatArrayOf(ev.getX(i), ev.getY(i), ev.getTouchMajor(i), if (big) 1f else 0f)
                }
            }
            if (ev.buttonState != lastButtons) {
                lab.addLog("PEN BUTTONS state=0x${Integer.toHexString(ev.buttonState)}")
                lastButtons = ev.buttonState
            }
            val a = ev.actionMasked
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) fingers.clear()
            else if (a == MotionEvent.ACTION_POINTER_UP) fingers.remove(ev.getPointerId(ev.actionIndex))
            if (a == MotionEvent.ACTION_CANCEL) lab.addLog("CANCEL (system palm rejection)")
            if (Build.VERSION.SDK_INT >= 33 && (ev.flags and 0x20) != 0) lab.addLog("FLAG_CANCELED on ${MotionEvent.actionToString(a)}")
            report(ev, ev.actionIndex.coerceAtMost(ev.pointerCount - 1), MotionEvent.actionToString(a))
            invalidate()
            return true
        }

        private fun report(ev: MotionEvent, i: Int, what: String) {
            val tool = when (ev.getToolType(i)) {
                MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"; MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
                MotionEvent.TOOL_TYPE_FINGER -> "FINGER"; MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"; else -> "UNKNOWN"
            }
            val mm = resources.displayMetrics.xdpi / 25.4f
            lab.setInfo(
                """
                |event      $what
                |tool       $tool   pointers=${ev.pointerCount}
                |x, y       ${"%.1f".format(ev.getX(i))}, ${"%.1f".format(ev.getY(i))}
                |pressure   ${"%.4f".format(ev.getPressure(i))}
                |tilt       ${"%.1f".format(Math.toDegrees(ev.getAxisValue(MotionEvent.AXIS_TILT, i).toDouble()))}°
                |orient.    ${"%.1f".format(Math.toDegrees(ev.getOrientation(i).toDouble()))}°
                |distance   ${"%.2f".format(ev.getAxisValue(MotionEvent.AXIS_DISTANCE, i))}
                |contact    ${"%.1f".format(ev.getTouchMajor(i) / mm)} mm × ${"%.1f".format(ev.getTouchMinor(i) / mm)} mm
                |buttons    0x${Integer.toHexString(ev.buttonState)}
                |samples    ${ev.historySize + 1} in this event
                |rate       ${rate.toInt()} Hz
                |device     ${ev.device?.name ?: "?"}
                """.trimMargin()
            )
        }
    }
}

/** Tiny global accessors. */
object Folio {
    private var p: com.akshit.folio.core.Prefs? = null
    fun prefs(ctx: Context): com.akshit.folio.core.Prefs = p ?: com.akshit.folio.core.Prefs(ctx.applicationContext).also { p = it }
}
