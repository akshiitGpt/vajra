package com.akshit.folio.brush

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.akshit.folio.model.Stroke
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns raw stroke samples into renderable geometry and draws it.
 *
 * OUTLINE brushes: the stroke is the union of "dabs" (circles, or ellipses for flat nibs)
 * along a Catmull-Rom resampled spine. The union is built as convex tangent quads between
 * consecutive dabs plus discs at sharp joints, all wound clockwise so the non-zero fill rule
 * renders a perfect union (no notches or holes at hairpin turns), as a single cached Path.
 *
 * STAMP brushes: textured dabs placed at a fixed spacing (airbrush, watercolor, charcoal...).
 */
object StrokeRenderer {

    // ---------------------------------------------------------------- textures
    private const val DAB = 128
    private var softDab: Bitmap? = null
    private var grainDab: Bitmap? = null
    private var wetDab: Bitmap? = null
    private var grainTile: Bitmap? = null

    fun grainTile(): Bitmap {
        grainTile?.let { return it }
        val n = 160
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ALPHA_8)
        val px = IntArray(n * n)
        var seed = 1234567
        // Value-noise with two octaves so pencil grain looks like paper tooth, not TV static.
        val coarse = FloatArray(41 * 41) { seed = seed * 1103515245 + 12345; ((seed ushr 8) and 0xFFFF) / 65535f }
        fun cv(x: Int, y: Int) = coarse[(y % 40) * 41 + (x % 40)]
        for (y in 0 until n) for (x in 0 until n) {
            seed = seed * 1103515245 + 12345
            val fine = ((seed ushr 8) and 0xFF) / 255f
            val gx = x / 4f; val gy = y / 4f
            val x0 = gx.toInt(); val y0 = gy.toInt(); val fx = gx - x0; val fy = gy - y0
            val c = (cv(x0, y0) * (1 - fx) + cv(x0 + 1, y0) * fx) * (1 - fy) + (cv(x0, y0 + 1) * (1 - fx) + cv(x0 + 1, y0 + 1) * fx) * fy
            val v = (0.55f * c + 0.45f * fine)
            val a = (v.pow(1.6f) * 255f * 1.35f).toInt().coerceIn(0, 255)
            px[y * n + x] = Color.argb(a, 255, 255, 255)
        }
        val argb = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        argb.setPixels(px, 0, n, 0, 0, n, n)
        Canvas(bmp).drawBitmap(argb, 0f, 0f, null)
        argb.recycle()
        grainTile = bmp
        return bmp
    }

    private fun dab(kind: Int): Bitmap {
        when (kind) {
            0 -> softDab?.let { return it }
            1 -> grainDab?.let { return it }
            else -> wetDab?.let { return it }
        }
        val bmp = Bitmap.createBitmap(DAB, DAB, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = DAB / 2f
        when (kind) {
            0 -> {
                p.shader = RadialGradient(r, r, r, intArrayOf(Color.WHITE, Color.argb(150, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(r, r, r, p)
            }
            1 -> {
                // Grainy, slightly irregular dab (charcoal / crayon)
                var seed = 99
                val pts = IntArray(DAB * DAB)
                for (y in 0 until DAB) for (x in 0 until DAB) {
                    val d = hypot(x - r + 0.5f, y - r + 0.5f) / r
                    seed = seed * 1103515245 + 12345
                    val noise = ((seed ushr 8) and 0xFF) / 255f
                    val edge = (1f - d).coerceIn(0f, 1f)
                    val a = if (d > 1f) 0f else ((edge * 3f).coerceAtMost(1f) * (if (noise > 0.42f) 1f else noise * 0.6f))
                    pts[y * DAB + x] = Color.argb((a * 255).toInt(), 255, 255, 255)
                }
                bmp.setPixels(pts, 0, DAB, 0, 0, DAB, DAB)
            }
            else -> {
                p.shader = RadialGradient(r, r, r,
                    intArrayOf(Color.argb(90, 255, 255, 255), Color.argb(120, 255, 255, 255), Color.argb(255, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                    floatArrayOf(0f, 0.7f, 0.9f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(r, r, r, p)
            }
        }
        when (kind) { 0 -> softDab = bmp; 1 -> grainDab = bmp; else -> wetDab = bmp }
        return bmp
    }

    // ---------------------------------------------------------------- resampling
    private var rx = FloatArray(1024)
    private var ry = FloatArray(1024)
    private var rr = FloatArray(1024)   // radius
    private var ra = FloatArray(1024)   // alpha factor
    private var rd = FloatArray(1024)   // direction angle
    private var rc = 0

    private fun ensure(n: Int) {
        if (n <= rx.size) return
        val s = max(n, rx.size * 2)
        rx = rx.copyOf(s); ry = ry.copyOf(s); rr = rr.copyOf(s); ra = ra.copyOf(s); rd = rd.copyOf(s)
    }

    private fun curve(p: Float, gamma: Float): Float = p.coerceIn(0f, 1f).pow(gamma.coerceIn(0.2f, 5f))

    /** Fills the resample buffers with spine points, radius and alpha factor. */
    private fun resample(s: Stroke) {
        val b = s.brush
        val n = s.n
        rc = 0
        if (n == 0) return
        val baseR = s.width / 2f
        val screenPerWorld = if (s.width > 0f) b.size / s.width else 1f
        // step along the spine (world units)
        val step = when (b.type.kind) {
            RenderKind.STAMP -> max(s.width * b.spacing.coerceIn(0.02f, 2f), s.width * 0.02f)
            else -> max(s.width * 0.45f, 0.6f / screenPerWorld)
        }
        // total length for taper
        var total = 0f
        for (i in 1 until n) total += hypot(s.x(i) - s.x(i - 1), s.y(i) - s.y(i - 1))
        val taperS = min(b.taperStart * s.width * 10f, total * 0.45f)
        val taperE = min(b.taperEnd * s.width * 10f, total * 0.45f)

        var est = 2
        for (i in 1 until n) est += 1 + ceil(hypot(s.x(i) - s.x(i - 1), s.y(i) - s.y(i - 1)) / step).toInt()
        val cap = if (b.type.kind == RenderKind.STAMP) 12000 else 40000
        val stepScale = if (est > cap) est.toFloat() / cap else 1f
        ensure(min(est, cap) + 8)

        var dist = 0f
        var rng = s.seed
        fun rnd(): Float { rng = rng * 1103515245 + 12345; return ((rng ushr 8) and 0xFFFF) / 65535f }

        fun emit(x: Float, y: Float, p: Float, tilt: Float, vel: Float, d: Float, dir: Float) {
            if (rc >= rx.size) ensure(rc + 64)
            val pc = curve(p, b.pressureGamma)
            var f = (1f - b.pressureSize) + b.pressureSize * pc
            if (b.tiltSize > 0f) f *= 1f + b.tiltSize * (tilt / 1.5708f).coerceIn(0f, 1f) * 2.2f
            if (b.velocitySize != 0f) {
                val vScreen = vel * screenPerWorld // px per ms
                f *= (1f - b.velocitySize * (vScreen / 3f).coerceIn(0f, 1f) * 0.75f)
            }
            f = max(f, b.minSize)
            if (taperS > 0f && d < taperS) f *= 0.12f + 0.88f * sqrt(d / taperS)
            if (taperE > 0f && total - d < taperE) f *= 0.12f + 0.88f * sqrt(((total - d) / taperE).coerceAtLeast(0f))
            if (b.jitterSize > 0f) f *= 1f + (rnd() - 0.5f) * 2f * b.jitterSize
            var a = (1f - b.pressureOpacity) + b.pressureOpacity * pc
            if (b.jitterOpacity > 0f) a *= 1f - rnd() * b.jitterOpacity
            var px = x; var py = y
            if (b.scatter > 0f) { px += (rnd() - 0.5f) * 2f * b.scatter * s.width * 2f; py += (rnd() - 0.5f) * 2f * b.scatter * s.width * 2f }
            rx[rc] = px; ry[rc] = py; rr[rc] = max(baseR * f, baseR * 0.02f); ra[rc] = a.coerceIn(0f, 1f); rd[rc] = dir
            rc++
        }

        if (n == 1) { emit(s.x(0), s.y(0), s.p(0), s.tilt(0), 0f, total / 2f, 0f); return }

        val st = step * stepScale
        for (i in 0 until n - 1) {
            val x0 = s.x(max(i - 1, 0)); val y0 = s.y(max(i - 1, 0))
            val x1 = s.x(i); val y1 = s.y(i)
            val x2 = s.x(i + 1); val y2 = s.y(i + 1)
            val x3 = s.x(min(i + 2, n - 1)); val y3 = s.y(min(i + 2, n - 1))
            val seg = hypot(x2 - x1, y2 - y1)
            val dt = max(s.t(i + 1) - s.t(i), 1f)
            val vel = seg / dt
            val dir = atan2(y2 - y1, x2 - x1)
            val k = max(1, ceil(seg / st).toInt())
            for (j in 0 until k) {
                val t = j.toFloat() / k
                val t2 = t * t; val t3 = t2 * t
                // Centripetal-ish uniform Catmull-Rom
                val x = 0.5f * ((2 * x1) + (-x0 + x2) * t + (2 * x0 - 5 * x1 + 4 * x2 - x3) * t2 + (-x0 + 3 * x1 - 3 * x2 + x3) * t3)
                val y = 0.5f * ((2 * y1) + (-y0 + y2) * t + (2 * y0 - 5 * y1 + 4 * y2 - y3) * t2 + (-y0 + 3 * y1 - 3 * y2 + y3) * t3)
                val p = s.p(i) + (s.p(i + 1) - s.p(i)) * t
                val tl = s.tilt(i) + (s.tilt(i + 1) - s.tilt(i)) * t
                emit(x, y, p, tl, vel, dist + seg * t, dir)
            }
            dist += seg
        }
        emit(s.x(n - 1), s.y(n - 1), s.p(n - 1), s.tilt(n - 1), 0f, total, if (rc > 0) rd[rc - 1] else 0f)
    }

    // ---------------------------------------------------------------- build
    private val nibM = Matrix()
    private val nibInv = Matrix()

    fun build(s: Stroke) {
        if (!s.dirty && (s.path != null || s.stamps != null)) return
        resample(s)
        val b = s.brush
        when (b.type.kind) {
            RenderKind.OUTLINE -> buildOutline(s)
            RenderKind.FILL -> buildFill(s)
            RenderKind.STAMP -> buildStamps(s)
        }
        var sum = 0f
        for (i in 0 until rc) sum += ra[i]
        s.meanAlpha = if (rc > 0) sum / rc else 1f
        s.dirty = false
    }

    private fun buildOutline(s: Stroke) {
        val b = s.brush
        val round = b.nibRoundness >= 0.98f
        val path = Path()
        path.fillType = Path.FillType.WINDING
        if (rc == 0) { s.path = path; s.bounds.setEmpty(); return }
        val rnd = b.nibRoundness.coerceIn(0.05f, 1f)
        if (!round) {
            // Map nib ellipses to circles: rotate by -angle, squash y by 1/roundness
            nibM.reset()
            nibM.setRotate(-b.nibAngle)
            nibM.postScale(1f, 1f / rnd)
            nibM.invert(nibInv)
            val tmp = FloatArray(2)
            for (i in 0 until rc) {
                tmp[0] = rx[i]; tmp[1] = ry[i]; nibM.mapPoints(tmp); rx[i] = tmp[0]; ry[i] = tmp[1]
                rr[i] = rr[i] / rnd
            }
        }
        unionOfDiscs(path)
        if (!round) path.transform(nibInv)
        s.path = path
        val bb = RectF(); path.computeBounds(bb, false)
        s.bounds.set(bb)
    }

    private fun unionOfDiscs(path: Path) {
        if (rc == 1) { path.addCircle(rx[0], ry[0], rr[0], Path.Direction.CW); return }
        path.addCircle(rx[0], ry[0], rr[0], Path.Direction.CW)
        path.addCircle(rx[rc - 1], ry[rc - 1], rr[rc - 1], Path.Direction.CW)
        var prevDx = 0f; var prevDy = 0f; var hasPrev = false
        for (i in 0 until rc - 1) {
            val x0 = rx[i]; val y0 = ry[i]; val r0 = rr[i]
            val x1 = rx[i + 1]; val y1 = ry[i + 1]; val r1 = rr[i + 1]
            val dx = x1 - x0; val dy = y1 - y0
            val len = hypot(dx, dy)
            if (len < 1e-6f) continue
            val ux = dx / len; val uy = dy / len
            if (len <= abs(r1 - r0)) {
                // one disc contains the other
                if (r1 > r0) path.addCircle(x1, y1, r1, Path.Direction.CW)
                hasPrev = false
                continue
            }
            // Sharp joint -> add a disc to fill the wedge on the outer side
            if (hasPrev) {
                val cosA = prevDx * ux + prevDy * uy
                if (cosA < 0.955f) path.addCircle(x0, y0, r0, Path.Direction.CW)
            }
            prevDx = ux; prevDy = uy; hasPrev = true
            // External tangent: outward normal u satisfies u.d = (r0-r1)/len
            val k = (r0 - r1) / len
            val m = sqrt(max(0f, 1f - k * k))
            // normals rotated from direction
            val nx = -uy; val ny = ux
            val ax = ux * k + nx * m; val ay = uy * k + ny * m   // side A
            val bx = ux * k - nx * m; val by = uy * k - ny * m   // side B
            val p0x = x0 + ax * r0; val p0y = y0 + ay * r0
            val p1x = x1 + ax * r1; val p1y = y1 + ay * r1
            val p2x = x1 + bx * r1; val p2y = y1 + by * r1
            val p3x = x0 + bx * r0; val p3y = y0 + by * r0
            // Ensure clockwise (positive shoelace in y-down coords)
            val area = (p0x * p1y - p1x * p0y) + (p1x * p2y - p2x * p1y) + (p2x * p3y - p3x * p2y) + (p3x * p0y - p0x * p3y)
            if (area >= 0f) {
                path.moveTo(p0x, p0y); path.lineTo(p1x, p1y); path.lineTo(p2x, p2y); path.lineTo(p3x, p3y)
            } else {
                path.moveTo(p0x, p0y); path.lineTo(p3x, p3y); path.lineTo(p2x, p2y); path.lineTo(p1x, p1y)
            }
            path.close()
        }
    }

    private fun buildFill(s: Stroke) {
        val path = Path()
        path.fillType = Path.FillType.WINDING
        if (rc > 0) {
            path.moveTo(rx[0], ry[0])
            for (i in 1 until rc) path.lineTo(rx[i], ry[i])
            path.close()
        }
        s.path = path
        val bb = RectF(); path.computeBounds(bb, false)
        bb.inset(-s.width, -s.width)
        s.bounds.set(bb)
    }

    private fun buildStamps(s: Stroke) {
        val b = s.brush
        val out = FloatArray(rc * 5)
        var rng = s.seed xor 0x5bd1e995
        fun rnd(): Float { rng = rng * 1103515245 + 12345; return ((rng ushr 8) and 0xFFFF) / 65535f }
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until rc) {
            val o = i * 5
            out[o] = rx[i]; out[o + 1] = ry[i]; out[o + 2] = rr[i]
            out[o + 3] = (b.flow * ra[i]).coerceIn(0.003f, 1f)
            var ang = if (b.followDirection) rd[i] else 0f
            if (b.jitterAngle > 0f) ang += (rnd() - 0.5f) * 6.2832f * b.jitterAngle
            out[o + 4] = Math.toDegrees(ang.toDouble()).toFloat()
            minX = min(minX, rx[i] - rr[i]); maxX = max(maxX, rx[i] + rr[i])
            minY = min(minY, ry[i] - rr[i]); maxY = max(maxY, ry[i] + rr[i])
        }
        s.stamps = out
        s.stampCount = rc
        if (rc > 0) s.bounds.set(minX, minY, maxX, maxY) else s.bounds.setEmpty()
    }

    // ---------------------------------------------------------------- draw
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { style = Paint.Style.FILL }
    private val stampM = Matrix()
    private val inv = Matrix()
    private val grainShaderM = Matrix()
    private var grainShader: BitmapShader? = null

    private fun blendOf(b: BrushSpec): BlendMode? = when (b.blend) {
        BlendKind.NORMAL -> null
        BlendKind.MULTIPLY -> BlendMode.MULTIPLY
        BlendKind.SCREEN -> BlendMode.SCREEN
        BlendKind.DARKEN -> BlendMode.DARKEN
        BlendKind.LIGHTEN -> BlendMode.LIGHTEN
    }

    /** Draws a stroke with [m] mapping stroke-local coordinates to the canvas. */
    fun draw(c: Canvas, s: Stroke, m: Matrix, colorOverride: Int? = null, alphaMul: Float = 1f) {
        build(s)
        val b = s.brush
        val color = colorOverride ?: s.color
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.blendMode = blendOf(b)
        when (b.type.kind) {
            RenderKind.OUTLINE, RenderKind.FILL -> {
                val path = s.path ?: return
                val baseA = Color.alpha(color) / 255f
                paint.alpha = (255 * baseA * b.opacity * s.meanAlpha * alphaMul).toInt().coerceIn(0, 255)
                c.save()
                c.concat(m)
                if (b.grain > 0.01f) {
                    // Screen-locked paper grain: shader matrix cancels the stroke matrix.
                    val sh = grainShader ?: BitmapShader(grainTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also { grainShader = it }
                    m.invert(inv)
                    grainShaderM.set(inv)
                    sh.setLocalMatrix(grainShaderM)
                    paint.shader = sh
                    // A grain-only pass reads as too light; draw a soft solid base underneath.
                    val solid = (1f - b.grain).coerceIn(0f, 1f)
                    if (solid > 0.02f) {
                        val p2 = Paint(paint); p2.shader = null
                        p2.alpha = (paint.alpha * solid).toInt()
                        c.drawPath(path, p2)
                    }
                }
                c.drawPath(path, paint)
                c.restore()
            }
            RenderKind.STAMP -> {
                val st = s.stamps ?: return
                val bmp = when {
                    b.type == BrushType.WATERCOLOR || b.wetEdges > 0.3f -> dab(2)
                    b.grain > 0.3f -> dab(1)
                    else -> dab(0)
                }
                paint.colorFilter = PorterDuffColorFilter(color or (0xFF shl 24), PorterDuff.Mode.SRC_IN)
                val baseA = Color.alpha(color) / 255f * b.opacity * alphaMul
                c.save()
                c.concat(m)
                val half = DAB / 2f
                for (i in 0 until s.stampCount) {
                    val o = i * 5
                    val r = st[o + 2]
                    val sc = r / half
                    stampM.setTranslate(-half, -half)
                    if (st[o + 4] != 0f) stampM.postRotate(st[o + 4])
                    stampM.postScale(sc, sc)
                    stampM.postTranslate(st[o], st[o + 1])
                    paint.alpha = (255 * st[o + 3] * baseA).toInt().coerceIn(1, 255)
                    c.drawBitmap(bmp, stampM, paint)
                }
                c.restore()
            }
        }
    }
}
