package com.akshit.folio.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.akshit.folio.core.Camera
import com.akshit.folio.model.BackgroundSpec
import com.akshit.folio.model.Pattern
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Infinite, zoom-adaptive page backgrounds. In adaptive mode the grid behaves like an
 * infinite ruler: as you zoom in, finer subdivisions fade in; as you zoom out, coarser
 * levels take over, so there is always a usable grid at any zoom depth.
 */
class BackgroundRenderer {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private var buf = FloatArray(4096)
    private val wb = DoubleArray(4)

    private fun push(i: Int, v: Float): Int {
        if (i >= buf.size) buf = buf.copyOf(buf.size * 2)
        buf[i] = v
        return i + 1
    }

    fun draw(c: Canvas, cam: Camera, bg: BackgroundSpec, density: Float) {
        c.drawColor(bg.paperColor)
        if (bg.pattern == Pattern.NONE) return
        val lineColor = bg.lineColor
        val baseA = bg.lineAlpha.coerceIn(0f, 1f)
        val major = max(2, bg.majorEvery)
        val minPx = 9f * density

        // Level selection: world spacing S * major^k so that on-screen spacing >= minPx
        var spacing = bg.spacing.toDouble()
        var fade = 1f
        if (bg.adaptive) {
            val screen = spacing * cam.scale
            val k = ceil(ln(minPx / screen) / ln(major.toDouble()))
            spacing *= major.toDouble().pow(k)
            val s = spacing * cam.scale // in [minPx, minPx*major)
            // fade the finest level in as it opens up
            fade = ((ln(s / minPx) / ln(major.toDouble())).toFloat() * 1.6f).coerceIn(0f, 1f)
        } else if (spacing * cam.scale < 4.0) {
            return
        }
        cam.visibleWorldBounds(wb)
        val lw = bg.lineWidth * density * 0.75f

        when (bg.pattern) {
            Pattern.DOTS -> drawDots(c, cam, spacing, 0.0, lineColor, baseA, fade, major, lw)
            Pattern.ISO_DOTS -> drawDots(c, cam, spacing, 1.0, lineColor, baseA, fade, major, lw)
            Pattern.GRID -> {
                drawFamilies(c, cam, spacing, doubleArrayOf(0.0, PI / 2), lineColor, baseA * (0.35f + 0.65f * fade), lw, 0)
                drawFamilies(c, cam, spacing * major, doubleArrayOf(0.0, PI / 2), lineColor, baseA, lw, 0)
            }
            Pattern.GRAPH -> {
                drawFamilies(c, cam, spacing, doubleArrayOf(0.0, PI / 2), lineColor, baseA * 0.45f * fade, lw * 0.8f, 0)
                drawFamilies(c, cam, spacing * major, doubleArrayOf(0.0, PI / 2), lineColor, baseA, lw * 1.6f, 0)
            }
            Pattern.LINES -> {
                drawFamilies(c, cam, spacing, doubleArrayOf(0.0), lineColor, baseA * (0.5f + 0.5f * fade), lw, 0)
            }
            Pattern.ISOMETRIC -> {
                val d = spacing * sqrt(3.0) / 2
                drawFamilies(c, cam, d, doubleArrayOf(PI / 6, PI / 2, 5 * PI / 6), lineColor, baseA * (0.35f + 0.65f * fade), lw, 0)
                drawFamilies(c, cam, d * major, doubleArrayOf(PI / 6, PI / 2, 5 * PI / 6), lineColor, baseA, lw * 1.3f, 0)
            }
            Pattern.TRIANGLES -> {
                val d = spacing * sqrt(3.0) / 2
                drawFamilies(c, cam, d, doubleArrayOf(0.0, PI / 3, 2 * PI / 3), lineColor, baseA * (0.35f + 0.65f * fade), lw, 0)
            }
            Pattern.HEX -> drawHex(c, cam, spacing, lineColor, baseA * (0.4f + 0.6f * fade), lw)
            Pattern.PERSPECTIVE_1, Pattern.PERSPECTIVE_2, Pattern.PERSPECTIVE_3 -> drawPerspective(c, cam, bg, lineColor, baseA, lw)
            Pattern.NONE -> {}
        }
    }

    private fun withAlpha(color: Int, a: Float): Int = Color.argb((a * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    /** Draws families of parallel lines. angle = line direction; spacing = perpendicular distance. */
    private fun drawFamilies(c: Canvas, cam: Camera, spacing: Double, angles: DoubleArray, color: Int, alpha: Float, lw: Float, @Suppress("UNUSED_PARAMETER") unused: Int) {
        if (alpha <= 0.01f) return
        if (spacing * cam.scale < 3.0) return
        line.color = withAlpha(color, alpha)
        line.strokeWidth = max(lw, 0.6f)
        val cx = (wb[0] + wb[2]) / 2; val cy = (wb[1] + wb[3]) / 2
        val halfDiag = sqrt((wb[2] - wb[0]).pow(2) + (wb[3] - wb[1]).pow(2)) / 2 + spacing
        var n = 0
        for (a in angles) {
            val dx = cos(a); val dy = sin(a)
            val nx = -dy; val ny = dx
            // offsets o such that line {p : n.p = o} crosses the view
            val oc = nx * cx + ny * cy
            val first = floor((oc - halfDiag) / spacing).toLong()
            val last = ceil((oc + halfDiag) / spacing).toLong()
            if (last - first > 1200) continue
            for (k in first..last) {
                val o = k * spacing
                // point on line closest to centre
                val t = o - oc
                val px = cx + nx * t; val py = cy + ny * t
                val ax = px - dx * halfDiag; val ay = py - dy * halfDiag
                val bx = px + dx * halfDiag; val by = py + dy * halfDiag
                n = push(n, cam.worldToScreenX(ax, ay)); n = push(n, cam.worldToScreenY(ax, ay))
                n = push(n, cam.worldToScreenX(bx, by)); n = push(n, cam.worldToScreenY(bx, by))
            }
        }
        c.drawLines(buf, 0, n, line)
    }

    private fun drawDots(c: Canvas, cam: Camera, spacing0: Double, iso: Double, color: Int, a: Float, fade: Float, major: Int, lw: Float) {
        var spacing = spacing0
        // dots need more room than lines
        while (spacing * cam.scale < 16.0) spacing *= major
        val rowH = if (iso > 0) spacing * sqrt(3.0) / 2 else spacing
        val minRow = floor(wb[1] / rowH).toLong(); val maxRow = ceil(wb[3] / rowH).toLong()
        val minCol = floor(wb[0] / spacing).toLong() - 1; val maxCol = ceil(wb[2] / spacing).toLong() + 1
        if ((maxRow - minRow) * (maxCol - minCol) > 60000) return
        var nMinor = 0
        val majorPts = ArrayList<Float>()
        for (r in minRow..maxRow) {
            val shift = if (iso > 0 && (r and 1L) == 1L) spacing / 2 else 0.0
            for (col in minCol..maxCol) {
                val wx = col * spacing + shift; val wy = r * rowH
                val sx = cam.worldToScreenX(wx, wy); val sy = cam.worldToScreenY(wx, wy)
                val isMajor = iso == 0.0 && col % major == 0L && r % major == 0L
                if (isMajor) { majorPts.add(sx); majorPts.add(sy) } else { nMinor = push(nMinor, sx); nMinor = push(nMinor, sy) }
            }
        }
        dot.color = withAlpha(color, min(1f, a * 1.6f) * (0.45f + 0.55f * fade))
        dot.strokeWidth = max(2.2f * lw / 0.75f, 2f)
        c.drawPoints(buf, 0, nMinor, dot)
        if (majorPts.isNotEmpty()) {
            dot.color = withAlpha(color, min(1f, a * 2.2f))
            dot.strokeWidth = dot.strokeWidth * 1.6f
            c.drawPoints(majorPts.toFloatArray(), dot)
        }
    }

    private fun drawHex(c: Canvas, cam: Camera, spacing: Double, color: Int, a: Float, lw: Float) {
        val r = spacing // hex circumradius
        if (r * cam.scale < 6) return
        val w = sqrt(3.0) * r; val h = 1.5 * r
        val minRow = floor(wb[1] / h).toLong() - 1; val maxRow = ceil(wb[3] / h).toLong() + 1
        val minCol = floor(wb[0] / w).toLong() - 1; val maxCol = ceil(wb[2] / w).toLong() + 1
        if ((maxRow - minRow) * (maxCol - minCol) > 12000) return
        line.color = withAlpha(color, a); line.strokeWidth = max(lw, 0.6f)
        var n = 0
        for (row in minRow..maxRow) for (col in minCol..maxCol) {
            val cx = col * w + if ((row and 1L) == 1L) w / 2 else 0.0
            val cy = row * h
            // draw 3 edges per hex (top-right, right, bottom-right) to avoid duplicates
            for (e in 0 until 3) {
                val a0 = PI / 180 * (-90 + 60 * e); val a1 = PI / 180 * (-30 + 60 * e)
                val x0 = cx + r * cos(a0); val y0 = cy + r * sin(a0)
                val x1 = cx + r * cos(a1); val y1 = cy + r * sin(a1)
                n = push(n, cam.worldToScreenX(x0, y0)); n = push(n, cam.worldToScreenY(x0, y0))
                n = push(n, cam.worldToScreenX(x1, y1)); n = push(n, cam.worldToScreenY(x1, y1))
            }
        }
        c.drawLines(buf, 0, n, line)
    }

    private fun drawPerspective(c: Canvas, cam: Camera, bg: BackgroundSpec, color: Int, a: Float, lw: Float) {
        val hy = bg.horizon.toDouble()
        val d = bg.vpDistance.toDouble()
        val vps = when (bg.pattern) {
            Pattern.PERSPECTIVE_1 -> listOf(doubleArrayOf(0.0, hy))
            Pattern.PERSPECTIVE_2 -> listOf(doubleArrayOf(-d, hy), doubleArrayOf(d, hy))
            else -> listOf(doubleArrayOf(-d, hy), doubleArrayOf(d, hy), doubleArrayOf(0.0, hy + d * 2.2))
        }
        val reach = max(wb[2] - wb[0], wb[3] - wb[1]) * 3 + d * 4
        // horizon
        line.color = withAlpha(Color.rgb(226, 80, 80), min(1f, a * 1.8f)); line.strokeWidth = max(lw * 1.6f, 1f)
        c.drawLine(cam.worldToScreenX(wb[0] - reach, hy), cam.worldToScreenY(wb[0] - reach, hy),
            cam.worldToScreenX(wb[2] + reach, hy), cam.worldToScreenY(wb[2] + reach, hy), line)
        line.color = withAlpha(color, a); line.strokeWidth = max(lw, 0.6f)
        var n = 0
        val rays = 72
        for (vp in vps) {
            for (i in 0 until rays) {
                val ang = 2 * PI * i / rays
                val ex = vp[0] + cos(ang) * reach; val ey = vp[1] + sin(ang) * reach
                n = push(n, cam.worldToScreenX(vp[0], vp[1])); n = push(n, cam.worldToScreenY(vp[0], vp[1]))
                n = push(n, cam.worldToScreenX(ex, ey)); n = push(n, cam.worldToScreenY(ex, ey))
            }
        }
        // verticals for 1/2 point perspective
        if (bg.pattern != Pattern.PERSPECTIVE_3) {
            val sp = bg.spacing.toDouble() * 4
            if (sp * cam.scale > 12) {
                val first = floor(wb[0] / sp).toLong(); val last = ceil(wb[2] / sp).toLong()
                if (last - first < 400) for (k in first..last) {
                    val x = k * sp
                    n = push(n, cam.worldToScreenX(x, wb[1] - reach)); n = push(n, cam.worldToScreenY(x, wb[1] - reach))
                    n = push(n, cam.worldToScreenX(x, wb[3] + reach)); n = push(n, cam.worldToScreenY(x, wb[3] + reach))
                }
            }
        }
        c.drawLines(buf, 0, n, line)
        // vanishing point markers
        dot.color = withAlpha(Color.rgb(226, 80, 80), 0.9f); dot.strokeWidth = 10f
        for (vp in vps) c.drawPoint(cam.worldToScreenX(vp[0], vp[1]), cam.worldToScreenY(vp[0], vp[1]), dot)
    }
}
