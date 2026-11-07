package com.akshit.folio.core

import android.content.Context
import android.content.SharedPreferences
import com.akshit.folio.brush.BrushSpec
import com.akshit.folio.brush.BrushType

/** Actions that any gesture / pen event can be mapped to. */
enum class GestureAction(val label: String) {
    NONE("Nothing"),
    UNDO("Undo"),
    REDO("Redo"),
    TOGGLE_ERASER("Toggle eraser"),
    TOGGLE_UI("Hide / show interface"),
    EYEDROPPER("Eyedropper"),
    ZOOM_FIT("Zoom to fit drawing"),
    ZOOM_RESET("Reset zoom & rotation"),
    LASSO("Lasso select"),
    COLOR_PICKER("Open color wheel"),
    BRUSH_SETTINGS("Open brush settings"),
    NEXT_BRUSH("Next brush"),
    PREV_BRUSH("Previous brush"),
    PAN("Pan (hand tool)"),
    TOGGLE_LAST_BRUSH("Swap with last brush"),
    SHOW_WHEEL("Bring the wheel to the pen"),
}

/** What sliding along the pen (scroll-type events) does. */
enum class SlideAction(val label: String) {
    NONE("Nothing"), BRUSH_SIZE("Brush size"), OPACITY("Opacity"), ZOOM("Zoom"), UNDO_REDO("Undo / redo")
}

enum class FingerDraw(val label: String) {
    AUTO("Auto (off once a pen is detected)"),
    ALWAYS("Fingers always draw"),
    NEVER("Fingers never draw (pen only)")
}

/** Typed wrapper around SharedPreferences. Every setting in the Settings screen lives here. */
class Prefs(ctx: Context) {
    val sp: SharedPreferences = ctx.getSharedPreferences("folio", Context.MODE_PRIVATE)

    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun f(k: String, d: Float) = sp.getFloat(k, d)
    private fun i(k: String, d: Int) = sp.getInt(k, d)

    fun put(k: String, v: Any) {
        val e = sp.edit()
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Float -> e.putFloat(k, v)
            is Int -> e.putInt(k, v)
            is String -> e.putString(k, v)
            is Long -> e.putLong(k, v)
        }
        e.apply()
    }

    // ---------------- pen & palm rejection
    val fingerDraw get() = FingerDraw.entries.getOrElse(i(K_FINGER_DRAW, 0)) { FingerDraw.AUTO }
    val penDetected get() = b(K_PEN_DETECTED, false)
    val palmSizeMm get() = f(K_PALM_MM, 11f)
    val palmCooldownMs get() = i(K_PALM_COOLDOWN, 350)
    val rejectWhileHover get() = b(K_REJECT_HOVER, true)
    val edgeRejectMm get() = f(K_EDGE_MM, 0f)
    val honorCancelFlag get() = b(K_CANCEL_FLAG, true)

    // ---------------- pressure
    val pressureGamma get() = f(K_P_GAMMA, 1f)
    val pressureMin get() = f(K_P_MIN, 0.02f)
    val pressureMax get() = f(K_P_MAX, 0.95f)
    val fingerPressure get() = f(K_FINGER_P, 0.7f)
    val tiltEnabled get() = b(K_TILT, true)

    // ---------------- latency / performance
    val prediction get() = b(K_PREDICT, true)
    val unbuffered get() = b(K_UNBUFFERED, true)
    val highRefresh get() = b(K_HIGH_REFRESH, true)
    val showFps get() = b(K_FPS, false)
    val hoverCursor get() = b(K_HOVER_CURSOR, true)

    // ---------------- canvas
    val rotateEnabled get() = b(K_ROTATE, true)
    val rotateSnap get() = b(K_ROTATE_SNAP, true)
    val leftHanded get() = b(K_LEFT, false)
    val keepScreenOn get() = b(K_SCREEN_ON, true)
    val shapeHold get() = b(K_SHAPE_HOLD, true)
    val shapeHoldMs get() = i(K_SHAPE_MS, 450)
    val volumeKeys get() = b(K_VOLUME, true)
    val showZoom get() = b(K_SHOW_ZOOM, true)
    val autosaveSec get() = i(K_AUTOSAVE, 5)

    // ---------------- Xiaomi / stylus extra controls
    /** Action for a pen key code (stylus buttons, squeeze, or any learned code). */
    fun penKeyAction(code: Int): GestureAction? {
        if (sp.contains("penkey_$code")) return GestureAction.entries.getOrNull(sp.getInt("penkey_$code", 0))
        return DEFAULT_PEN_KEYS[code]
    }
    fun setPenKeyAction(code: Int, a: GestureAction) = put("penkey_$code", a.ordinal)
    val slideAction get() = SlideAction.entries.getOrElse(i(K_SLIDE, SlideAction.BRUSH_SIZE.ordinal)) { SlideAction.BRUSH_SIZE }
    /** A learned MotionEvent axis that the pen's slide/squeeze reports (-1 = only scroll axes). */
    val slideAxis get() = i(K_SLIDE_AXIS, -1)
    val learnedKeys: List<Int> get() = sp.all.keys.filter { it.startsWith("penkey_") }.mapNotNull { it.removePrefix("penkey_").toIntOrNull() }

    // ---------------- workspace behaviour
    val autoLayers get() = b(K_AUTO_LAYERS, true)
    val zoomEnabled get() = b(K_ZOOM, true)
    val zoomSnap get() = b(K_ZOOM_SNAP, true)
    val fingerSelect get() = b(K_FINGER_SELECT, false)

    // ---------------- handwriting refinement
    val refineAuto get() = b(K_REFINE, true)
    val refineStrength get() = f(K_REFINE_STRENGTH, 0.7f)
    val refineDelayMs get() = i(K_REFINE_DELAY, 900)

    // ---------------- gestures
    fun action(key: String): GestureAction {
        val def = DEFAULT_ACTIONS[key] ?: GestureAction.NONE
        return GestureAction.entries.getOrElse(i(key, def.ordinal)) { def }
    }

    // ---------------- brushes, colors
    fun brushSlot(i: Int): BrushSpec {
        val s = sp.getString("brush_slot_$i", null)
        return s?.let { BrushSpec.decode(it) } ?: BrushSpec.preset(DEFAULT_SLOTS.getOrElse(i) { BrushType.PEN })
    }

    fun saveBrushSlot(i: Int, b: BrushSpec) = put("brush_slot_$i", b.encode())
    fun resetBrushSlot(i: Int) { sp.edit().remove("brush_slot_$i").apply() }

    fun slotColor(i: Int): Int = i("slot_color_$i", DEFAULT_COLORS[i % DEFAULT_COLORS.size])
    fun saveSlotColor(i: Int, c: Int) = put("slot_color_$i", c)

    var recentColors: List<Int>
        get() = (sp.getString("recent_colors", "") ?: "").split(",").mapNotNull { it.toLongOrNull()?.toInt() }
        set(v) = put("recent_colors", v.take(24).joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })

    var customPalette: List<Int>
        get() = (sp.getString("custom_palette", "") ?: "").split(",").mapNotNull { it.toLongOrNull()?.toInt() }
        set(v) = put("custom_palette", v.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })

    val eraserSize get() = f("eraser_size", 24f)
    val eraserWhole get() = b("eraser_whole", false)

    companion object {
        const val K_FINGER_DRAW = "finger_draw"
        const val K_PEN_DETECTED = "pen_detected"
        const val K_PALM_MM = "palm_mm"
        const val K_PALM_COOLDOWN = "palm_cooldown"
        const val K_REJECT_HOVER = "reject_hover"
        const val K_EDGE_MM = "edge_mm"
        const val K_CANCEL_FLAG = "cancel_flag"
        const val K_P_GAMMA = "p_gamma"
        const val K_P_MIN = "p_min"
        const val K_P_MAX = "p_max"
        const val K_FINGER_P = "finger_p"
        const val K_TILT = "tilt"
        const val K_PREDICT = "predict"
        const val K_UNBUFFERED = "unbuffered"
        const val K_HIGH_REFRESH = "high_refresh"
        const val K_FPS = "fps"
        const val K_HOVER_CURSOR = "hover_cursor"
        const val K_ROTATE = "rotate"
        const val K_ROTATE_SNAP = "rotate_snap"
        const val K_LEFT = "left_handed"
        const val K_SCREEN_ON = "screen_on"
        const val K_SHAPE_HOLD = "shape_hold"
        const val K_SHAPE_MS = "shape_ms"
        const val K_VOLUME = "volume_keys"
        const val K_SHOW_ZOOM = "show_zoom"
        const val K_AUTOSAVE = "autosave"
        const val K_REFINE = "refine_auto"
        const val K_AUTO_LAYERS = "auto_layers"
        const val K_SLIDE = "pen_slide"
        const val K_SLIDE_AXIS = "pen_slide_axis"

        // Android 14+ stylus key codes (KeyEvent.KEYCODE_STYLUS_BUTTON_*); pens such as the
        // Focus Pen Pro may report squeeze / extra buttons through these.
        const val KEY_STYLUS_PRIMARY = 308
        const val KEY_STYLUS_SECONDARY = 309
        const val KEY_STYLUS_TERTIARY = 310
        const val KEY_STYLUS_TAIL = 311
        val PEN_KEY_NAMES = mapOf(KEY_STYLUS_PRIMARY to "Pen button 1", KEY_STYLUS_SECONDARY to "Pen button 2",
            KEY_STYLUS_TERTIARY to "Pen button 3", KEY_STYLUS_TAIL to "Pen squeeze / tail")
        val DEFAULT_PEN_KEYS = mapOf(
            KEY_STYLUS_PRIMARY to GestureAction.TOGGLE_ERASER,
            KEY_STYLUS_SECONDARY to GestureAction.EYEDROPPER,
            KEY_STYLUS_TERTIARY to GestureAction.UNDO,
            KEY_STYLUS_TAIL to GestureAction.SHOW_WHEEL)
        const val K_ZOOM = "zoom_enabled"
        const val K_ZOOM_SNAP = "zoom_snap"
        const val K_FINGER_SELECT = "finger_select"
        const val K_REFINE_STRENGTH = "refine_strength"
        const val K_REFINE_DELAY = "refine_delay"

        // Gesture keys
        const val G_TWO_TAP = "g_two_tap"
        const val G_THREE_TAP = "g_three_tap"
        const val G_FOUR_TAP = "g_four_tap"
        const val G_DOUBLE_TAP = "g_double_tap"
        const val G_TWO_DOUBLE_TAP = "g_two_double_tap"
        const val G_LONG_PRESS = "g_long_press"
        const val G_ONE_FINGER_DRAG = "g_one_drag"     // when fingers don't draw: PAN or NONE
        const val G_PEN_DOUBLE_TAP = "g_pen_double_tap"
        const val G_PEN_LONG_PRESS = "g_pen_long_press"
        const val G_PEN_BUTTON = "g_pen_button"
        const val G_PEN_BUTTON2 = "g_pen_button2"
        const val G_PEN_ERASER_END = "g_pen_eraser_end"
        const val G_THREE_SWIPE = "g_three_swipe"       // horizontal = undo/redo scrub
        const val G_VOL_UP = "g_vol_up"
        const val G_VOL_DOWN = "g_vol_down"

        val GESTURE_KEYS = listOf(
            G_TWO_TAP to "Two-finger tap",
            G_THREE_TAP to "Three-finger tap",
            G_FOUR_TAP to "Four-finger tap",
            G_DOUBLE_TAP to "Finger double-tap",
            G_TWO_DOUBLE_TAP to "Two-finger double-tap",
            G_LONG_PRESS to "Finger long-press",
            G_ONE_FINGER_DRAG to "One-finger drag (when fingers don't draw)",
            G_PEN_DOUBLE_TAP to "Pen double-tap on screen",
            G_PEN_LONG_PRESS to "Pen press & hold (without moving)",
            G_PEN_BUTTON to "Pen button 1 (if your pen has one)",
            G_PEN_BUTTON2 to "Pen button 2 (if your pen has one)",
            G_PEN_ERASER_END to "Pen eraser end / flipped pen",
            G_VOL_UP to "Volume up key",
            G_VOL_DOWN to "Volume down key",
        )

        val DEFAULT_ACTIONS = mapOf(
            G_TWO_TAP to GestureAction.UNDO,
            G_THREE_TAP to GestureAction.REDO,
            G_FOUR_TAP to GestureAction.TOGGLE_UI,
            G_DOUBLE_TAP to GestureAction.ZOOM_RESET,
            G_TWO_DOUBLE_TAP to GestureAction.ZOOM_FIT,
            G_LONG_PRESS to GestureAction.EYEDROPPER,
            G_ONE_FINGER_DRAG to GestureAction.PAN,
            G_PEN_DOUBLE_TAP to GestureAction.NONE,
            G_PEN_LONG_PRESS to GestureAction.NONE,
            G_PEN_BUTTON to GestureAction.TOGGLE_ERASER,
            G_PEN_BUTTON2 to GestureAction.EYEDROPPER,
            G_PEN_ERASER_END to GestureAction.TOGGLE_ERASER,
            G_THREE_SWIPE to GestureAction.UNDO,
            G_VOL_UP to GestureAction.REDO,
            G_VOL_DOWN to GestureAction.UNDO,
        )

        val DEFAULT_SLOTS = listOf(
            BrushType.PEN, BrushType.FINELINER, BrushType.PENCIL, BrushType.MARKER, BrushType.HIGHLIGHTER,
            BrushType.INK_BRUSH, BrushType.CALLIGRAPHY, BrushType.TECHNICAL, BrushType.AIRBRUSH,
            BrushType.WATERCOLOR, BrushType.CHARCOAL, BrushType.CRAYON, BrushType.FILL
        )

        val DEFAULT_COLORS = intArrayOf(
            0xFF1B1B1F.toInt(), 0xFF22262E.toInt(), 0xFF3A3A3A.toInt(), 0xFF2F6BDA.toInt(), 0xFFF2D13A.toInt(),
            0xFF111111.toInt(), 0xFF7A2E2E.toInt(), 0xFF2A2A2A.toInt(), 0xFF4A90D9.toInt(),
            0xFF3C8DBC.toInt(), 0xFF2C2C2C.toInt(), 0xFFD9534F.toInt(), 0xFF5CB85C.toInt()
        )
    }
}
