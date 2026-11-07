package com.akshit.folio.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Line icons described with a tiny SVG-like path language on a 24x24 grid (M L H V C Q Z, plus O cx cy r for circles). */
object Icons {
    val DATA = mapOf(
        "back" to "M15 5 L8 12 L15 19",
        "undo" to "M9 6.5 L4 11.5 L9 16.5 M4 11.5 H14 C17.5 11.5 20 13.5 20 17 V18.5",
        "redo" to "M15 6.5 L20 11.5 L15 16.5 M20 11.5 H10 C6.5 11.5 4 13.5 4 17 V18.5",
        "layers" to "M12 4 L21 9 L12 14 L3 9 Z M3 13 L12 18 L21 13",
        "grid" to "M4 4 H20 V20 H4 Z M4 9.3 H20 M4 14.6 H20 M9.3 4 V20 M14.6 4 V20",
        "settings" to "O 12 12 3.2 M12 2.5 V5.5 M12 18.5 V21.5 M2.5 12 H5.5 M18.5 12 H21.5 M5.3 5.3 L7.4 7.4 M16.6 16.6 L18.7 18.7 M5.3 18.7 L7.4 16.6 M16.6 7.4 L18.7 5.3",
        "export" to "M12 15 V4 M7.5 8.5 L12 4 L16.5 8.5 M5 13 V20 H19 V13",
        "pen" to "M4 20 L5.5 15 L16 4.5 L19.5 8 L9 18.5 Z M14 6.5 L17.5 10",
        "fineliner" to "M4.5 19.5 L6 18 M6 18 L15.5 8.5 L17.5 10.5 L8 20 Z M15.5 8.5 L18.5 5.5 L20.5 7.5 L17.5 10.5",
        "technical" to "M12 3 V6 M9 6 H15 V14 H9 Z M10.5 14 L12 21 L13.5 14",
        "pencil" to "M4 20 L5 15.5 L15.5 5 L19 8.5 L8.5 19 Z M5 15.5 L8.5 19 M13.5 7 L17 10.5",
        "marker" to "M6 20 L4 18 L6 14 L15 5 L19 9 L10 18 Z M6 14 L10 18",
        "highlighter" to "M4 20 H10 M7 17 L5.5 15.5 L8 13 L16 5 L19 8 L11 16 L8.5 18.5 Z",
        "brush" to "M14 4 L20 10 L13 15 L9 11 Z M9 11 C6 11 5 13 5 15 C5 17 4 19 3 20 C7 20 11 19 12 15",
        "calligraphy" to "M12 3 L18 10 L12 21 L6 10 Z M12 13 V21 O 12 10.5 1.4",
        "airbrush" to "M4 14 H13 L16 11 H20 M8 14 V19 M17 6 V7.5 M20 6.5 V8 M18.5 3.5 V5",
        "watercolor" to "M12 3 C12 3 6 10 6 14 C6 17.5 8.7 20 12 20 C15.3 20 18 17.5 18 14 C18 10 12 3 12 3 Z",
        "charcoal" to "M5 17 L15 7 L19 11 L9 21 Z M7 15 L11 19",
        "crayon" to "M5 19 L7 21 L18 10 L16 8 Z M16 8 L19 5 L21 7 L18 10",
        "fill" to "M5 11 L11 5 L18 12 L12 18 Z M18 12 C19.5 14 20 15.5 20 16.5 C20 17.5 19.3 18 18.5 18 C17.7 18 17 17.5 17 16.5 C17 15.5 17.5 14 18 12 M11 5 L9 3",
        "eraser" to "M8 20 L3.5 15.5 L13.5 5.5 L20.5 12.5 L13 20 Z M8.5 10.5 L15.5 17.5 M13 20 H20",
        "lasso" to "M12 5 C17 5 20 7 20 10 C20 13 17 15 12 15 C7 15 4 13 4 10 C4 7 7 5 12 5 M8 14 C7 16 8 18 10 18 C11 18 11 20 9 21",
        "eyedropper" to "M14.5 4.5 L19.5 9.5 M13 8 L16 11 M14.5 9.5 L6.5 17.5 L4 20 M6.5 17.5 L5 16",
        "hand" to "M8 13 V6 C8 4.5 10 4.5 10 6 V11 M10 5.5 C10 4 12 4 12 5.5 V11 M12 6 C12 4.5 14 4.5 14 6 V11 M14 7.5 C14 6 16 6 16 7.5 V14 C16 18 14 20 11 20 C8.5 20 7 18.5 5.5 16 L4.5 14 C4 13 5.5 12 6.5 13 L8 14.5",
        "plus" to "M12 5 V19 M5 12 H19",
        "trash" to "M5 7 H19 M10 7 V4.5 H14 V7 M7 7 L8 20 H16 L17 7 M10.5 10.5 V16.5 M13.5 10.5 V16.5",
        "eye" to "M2.5 12 C5 7.5 8.5 5.5 12 5.5 C15.5 5.5 19 7.5 21.5 12 C19 16.5 15.5 18.5 12 18.5 C8.5 18.5 5 16.5 2.5 12 Z O 12 12 3",
        "eye_off" to "M2.5 12 C5 7.5 8.5 5.5 12 5.5 C15.5 5.5 19 7.5 21.5 12 C19 16.5 15.5 18.5 12 18.5 C8.5 18.5 5 16.5 2.5 12 Z M4 4 L20 20",
        "lock" to "M6 11 H18 V20 H6 Z M8.5 11 V8 C8.5 4 15.5 4 15.5 8 V11",
        "unlock" to "M6 11 H18 V20 H6 Z M8.5 11 V8 C8.5 4 15.5 4 15.5 7",
        "copy" to "M8 8 H19 V19 H8 Z M5 15 V5 H15",
        "more" to "O 5 12 1.3 O 12 12 1.3 O 19 12 1.3",
        "penlab" to "M3 12 H6 L8 7 L11 17 L13.5 9 L15.5 14 L17 12 H21",
        "close" to "M6 6 L18 18 M18 6 L6 18",
        "check" to "M5 12.5 L10 17.5 L19 7",
        "front" to "M5 5 H13 V13 H5 Z M11 11 H19 V19 H11 Z",
        "zoomfit" to "M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15",
        "gallery" to "M4 5 H10 L12 7 H20 V19 H4 Z",
        "selectall" to "M4 4 H8 M10 4 H14 M16 4 H20 V8 M20 10 V14 M20 16 V20 H16 M14 20 H10 M8 20 H4 V16 M4 14 V10 M4 8 V4",
        "palette" to "M12 3.5 C7 3.5 3.5 7 3.5 12 C3.5 16.5 7 20.5 11 20.5 C12.5 20.5 13 19.5 12.5 18.5 C12 17.3 12.6 16 14 16 H16 C18.8 16 20.5 14 20.5 11.5 C20.5 7 16.5 3.5 12 3.5 Z O 8 10.5 1.2 O 11.5 7.5 1.2 O 15.5 8.5 1.2",
        "recolor" to "M12 3 C12 3 6 10 6 14 C6 17.5 8.7 20 12 20 C15.3 20 18 17.5 18 14 C18 10 12 3 12 3 Z M9 15 C9.5 16.5 10.5 17 12 17",
        "rename" to "M4 20 H20 M6 16 L15.5 6.5 L18 9 L8.5 18.5 H6 Z",
        "minus" to "M5 12 H19",
        "image" to "M4 5 H20 V19 H4 Z M4 16 L9 11 L13 15 L16 12 L20 16 O 15.5 8.5 1.5",
        "sparkle" to "M12 3 L13.6 9.2 L20 11 L13.6 12.8 L12 19 L10.4 12.8 L4 11 L10.4 9.2 Z M18.5 3.5 V7 M16.8 5.2 H20.2",
        "flip" to "M12 3 V21 M9 7 L4 17 H9 Z M15 7 L20 17 H15 Z",
    )

    private val cache = HashMap<String, Path>()

    fun path(name: String): Path = cache.getOrPut(name) { parse(DATA[name] ?: DATA["more"]!!) }

    fun parse(d: String): Path {
        val p = Path()
        val tokens = Regex("[MLHVCQZOmlhvcqzo]|-?[0-9]*\\.?[0-9]+").findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        var x = 0f; var y = 0f; var sx = 0f; var sy = 0f
        fun num(): Float = tokens[i++].toFloat()
        while (i < tokens.size) {
            val t = tokens[i]
            if (t[0].isLetter()) { cmd = t[0]; i++ }
            when (cmd) {
                'M' -> { x = num(); y = num(); p.moveTo(x, y); sx = x; sy = y; cmd = 'L' }
                'L' -> { x = num(); y = num(); p.lineTo(x, y) }
                'H' -> { x = num(); p.lineTo(x, y) }
                'V' -> { y = num(); p.lineTo(x, y) }
                'C' -> { val a = num(); val b = num(); val c = num(); val e = num(); x = num(); y = num(); p.cubicTo(a, b, c, e, x, y) }
                'Q' -> { val a = num(); val b = num(); x = num(); y = num(); p.quadTo(a, b, x, y) }
                'Z', 'z' -> { p.close(); x = sx; y = sy; if (i < tokens.size && !tokens[i][0].isLetter()) cmd = 'L' }
                'O' -> { val cx = num(); val cy = num(); val r = num(); p.addCircle(cx, cy, r, Path.Direction.CW) }
                else -> i++
            }
        }
        return p
    }
}

class IconDrawable(private val name: String, var color: Int, private val strokeDp: Float = 1.8f) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val m = Matrix()
    private val tmp = Path()

    override fun draw(c: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 24f
        m.setScale(s, s); m.postTranslate(b.left + (b.width() - 24 * s) / 2, b.top + (b.height() - 24 * s) / 2)
        tmp.set(Icons.path(name)); tmp.transform(m)
        paint.color = color
        paint.strokeWidth = strokeDp * s
        c.drawPath(tmp, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
