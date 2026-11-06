package com.akshit.folio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.akshit.folio.core.FingerDraw
import com.akshit.folio.core.GestureAction
import com.akshit.folio.core.Prefs
import com.akshit.folio.ui.IconButton
import com.akshit.folio.ui.UI

/** Every customisable behaviour in one place. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val root = UI.vbox(this)
        root.setBackgroundColor(UI.BG)
        root.fitsSystemWindows = true
        val head = UI.hbox(this)
        head.setPadding(UI.dp(this, 8f), UI.dp(this, 8f), UI.dp(this, 16f), UI.dp(this, 8f))
        head.addView(IconButton(this, "back").apply { setOnClickListener { finish() } })
        head.addView(UI.label(this, "Settings", 22f, UI.TEXT, true), UI.lp(0, UI.WRAP, 1f))
        head.addView(UI.button(this, "Pen Lab") { startActivity(Intent(this, PenLabActivity::class.java)) })
        root.addView(head)
        body = UI.vbox(this)
        body.setPadding(UI.dp(this, 20f), 0, UI.dp(this, 20f), UI.dp(this, 40f))
        root.addView(UI.scroll(this, body), UI.lp(UI.MATCH, 0, 1f))
        setContentView(root)
        build()
    }

    private fun build() {
        body.removeAllViews()
        val p = prefs
        fun sec(t: String) = body.addView(UI.section(this, t))
        fun tog(key: String, title: String, desc: String?, v: Boolean) = body.addView(UI.toggle(this, title, desc, v) { p.put(key, it) })
        fun sl(key: String, title: String, min: Float, max: Float, v: Float, fmt: (Float) -> String) =
            body.addView(UI.slider(this, title, min, max, v, fmt) { p.put(key, it) })
        fun sli(key: String, title: String, min: Float, max: Float, v: Int, fmt: (Float) -> String) =
            body.addView(UI.slider(this, title, min, max, v.toFloat(), fmt) { p.put(key, it.toInt()) })

        sec("Pen & palm rejection")
        body.addView(UI.choice(this, "Finger drawing", FingerDraw.entries.map { it.label }, p.fingerDraw.ordinal) { p.put(Prefs.K_FINGER_DRAW, it) })
        body.addView(UI.label(this, if (p.penDetected) "✓ A stylus has been detected on this device." else "No stylus detected yet: touch the screen with your pen once.", 12.5f, if (p.penDetected) 0xFF6CCB7A.toInt() else UI.SUB).apply { setPadding(UI.dp(this@SettingsActivity, 4f), 0, 0, UI.dp(this@SettingsActivity, 6f)) })
        sl(Prefs.K_PALM_MM, "Palm size threshold", 4f, 30f, p.palmSizeMm) { "%.1f mm".format(it) }
        sli(Prefs.K_PALM_COOLDOWN, "Ignore touches after the pen lifts", 0f, 1500f, p.palmCooldownMs) { "${it.toInt()} ms" }
        tog(Prefs.K_REJECT_HOVER, "Reject touches while pen hovers", "Your palm usually lands while the pen is hovering above the glass", p.rejectWhileHover)
        tog(Prefs.K_CANCEL_FLAG, "Use system palm detection", "Discard touches Android marks as accidental", p.honorCancelFlag)
        sl(Prefs.K_EDGE_MM, "Ignore touches near left/right edge", 0f, 20f, p.edgeRejectMm) { if (it < 0.5f) "Off" else "%.0f mm".format(it) }

        sec("Pressure & tilt")
        sl(Prefs.K_P_GAMMA, "Global pressure curve (soft ↔ firm)", 0.3f, 3f, p.pressureGamma) { "%.2f".format(it) }
        sl(Prefs.K_P_MIN, "Pressure floor (lightest touch)", 0f, 0.5f, p.pressureMin) { "%.2f".format(it) }
        sl(Prefs.K_P_MAX, "Pressure ceiling (full press)", 0.3f, 1f, p.pressureMax) { "%.2f".format(it) }
        sl(Prefs.K_FINGER_P, "Finger drawing pressure", 0.1f, 1f, p.fingerPressure) { "%.2f".format(it) }
        tog(Prefs.K_TILT, "Use pen tilt", "Tilt-sensitive brushes (pencil, charcoal) widen when the pen leans", p.tiltEnabled)

        sec("Speed & latency")
        tog(Prefs.K_PREDICT, "Motion prediction", "Draws a few ms ahead of the pen to hide latency (Android 14+)", p.prediction)
        tog(Prefs.K_UNBUFFERED, "Unbuffered pen input", "Receive pen samples immediately instead of once per frame", p.unbuffered)
        tog(Prefs.K_HIGH_REFRESH, "Highest refresh rate", "Ask for 120/144 Hz on the canvas", p.highRefresh)
        tog(Prefs.K_HOVER_CURSOR, "Hover cursor", "Show brush outline while the pen hovers", p.hoverCursor)
        tog(Prefs.K_FPS, "Show FPS meter", null, p.showFps)

        sec("Gestures")
        for ((key, title) in Prefs.GESTURE_KEYS) {
            val opts = if (key == Prefs.G_ONE_FINGER_DRAG) listOf(GestureAction.PAN, GestureAction.NONE) else GestureAction.entries.toList()
            val cur = opts.indexOf(p.action(key)).coerceAtLeast(0)
            body.addView(UI.choice(this, title, opts.map { it.label }, cur) { p.put(key, opts[it].ordinal) })
        }
        body.addView(UI.toggle(this, "Three-finger swipe = undo / redo scrub", "Swipe left to undo, right to redo, step by step", p.action(Prefs.G_THREE_SWIPE) != GestureAction.NONE) {
            p.put(Prefs.G_THREE_SWIPE, if (it) GestureAction.UNDO.ordinal else GestureAction.NONE.ordinal)
        })
        tog(Prefs.K_ROTATE, "Two-finger rotate", null, p.rotateEnabled)
        tog(Prefs.K_ROTATE_SNAP, "Snap rotation to 0° / 90°", null, p.rotateSnap)
        tog(Prefs.K_VOLUME, "Volume keys trigger actions", "Great with a buttonless pen", p.volumeKeys)

        sec("Drawing")
        tog(Prefs.K_SHAPE_HOLD, "Draw & hold → perfect shape", "Lines, circles, ellipses, arcs, rectangles, triangles, polygons", p.shapeHold)
        sli(Prefs.K_SHAPE_MS, "Hold time for shapes", 200f, 1200f, p.shapeHoldMs) { "${it.toInt()} ms" }

        sec("Interface")
        tog(Prefs.K_LEFT, "Left-handed layout", "Moves tools to the right side (re-open the canvas)", p.leftHanded)
        tog(Prefs.K_SHOW_ZOOM, "Show zoom indicator", null, p.showZoom)
        tog(Prefs.K_SCREEN_ON, "Keep screen on while drawing", null, p.keepScreenOn)
        sli(Prefs.K_AUTOSAVE, "Autosave after inactivity", 1f, 30f, p.autosaveSec) { "${it.toInt()} s" }

        sec("Reset")
        body.addView(UI.button(this, "Reset all brushes to factory presets") {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Reset brushes?")
                .setPositiveButton("Reset") { _, _ -> Prefs.DEFAULT_SLOTS.indices.forEach { p.resetBrushSlot(it) } }
                .setNegativeButton("Cancel", null).show()
        })
        body.addView(UI.button(this, "Reset all settings") {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Reset all settings?")
                .setPositiveButton("Reset") { _, _ -> p.sp.edit().clear().apply(); build() }
                .setNegativeButton("Cancel", null).show()
        }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = UI.dp(this@SettingsActivity, 8f) })
        body.addView(UI.label(this, "Folio ${packageManager.getPackageInfo(packageName, 0).versionName}", 12f, UI.SUB).apply { setPadding(UI.dp(this@SettingsActivity, 4f), UI.dp(this@SettingsActivity, 20f), 0, 0) })
    }
}
