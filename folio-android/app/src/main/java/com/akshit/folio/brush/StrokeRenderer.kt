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
                    intArrayOf(Color.argb(150, 255, 255, 255), Color.argb(165, 255, 255, 255), Color.argb(200, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                    floatArrayOf(0f, 0.6f, 0.82f, 1f), Shader.TileMode.CLAMP)
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
        var vs = -1f
        for (i in 0 until n - 1) {
            val x0 = s.x(max(i - 1, 0)); val y0 = s.y(max(i - 1, 0))
            val x1 = s.x(i); val y1 = s.y(i)
            val x2 = s.x(i + 1); val y2 = s.y(i + 1)
            val x3 = s.x(min(i + 2, n - 1)); val y3 = s.y(min(i + 2, n - 1))
            val seg = hypot(x2 - x1, y2 - y1)
            val dt = max(s.t(i + 1) - s.t(i), 4f)
            vs = if (vs < 0f) seg / dt else vs * 0.8f + (seg / dt) * 0.2f
            val vel = vs
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

    // filtered spine + per-segment tangent points
    private var fx = FloatArray(1024); private var fy = FloatArray(1024); private var fr = FloatArray(1024)
    private var sux = FloatArray(1024); private var suy = FloatArray(1024)
    private var a0x = FloatArray(1024); private var a0y = FloatArray(1024); private var a1x = FloatArray(1024); private var a1y = FloatArray(1024)
    private var b0x = FloatArray(1024); private var b0y = FloatArray(1024); private var b1x = FloatArray(1024); private var b1y = FloatArray(1024)
    private val oval = RectF()

    private fun ensureSeg(n: Int) {
        if (n <= fx.size) return
        val s = max(n, fx.size * 2)
        fx = fx.copyOf(s); fy = fy.copyOf(s); fr = fr.copyOf(s); sux = sux.copyOf(s); suy = suy.copyOf(s)
        a0x = a0x.copyOf(s); a0y = a0y.copyOf(s); a1x = a1x.copyOf(s); a1y = a1y.copyOf(s)
        b0x = b0x.copyOf(s); b0y = b0y.copyOf(s); b1x = b1x.copyOf(s); b1y = b1y.copyOf(s)
    }

    /**
     * Builds the stroke as ONE closed contour (like Skia's own stroker): side A forward with
     * round outer joins, round end cap, side B backward, round start cap. Inner joins go through
     * the spine point so the non-zero fill stays positive. A single contour has no internal
     * edges, so translucent brushes render without seams or hatching.
     */
    private fun unionOfDiscs(path: Path) {
        ensureSeg(rc + 2)
        // 1) drop dabs fully contained in their neighbour (and duplicates)
        var m = 0
        for (i in 0 until rc) {
            val x = rx[i]; val y = ry[i]; val r = rr[i]
            var skip = false
            while (m > 0) {
                val d = hypot(x - fx[m - 1], y - fy[m - 1])
                if (d <= abs(r - fr[m - 1]) + 1e-5f * max(r, fr[m - 1]) || d < 1e-6f) {
                    if (r > fr[m - 1]) { m--; continue } else { skip = true }
                }
                break
            }
            if (!skip) { fx[m] = x; fy[m] = y; fr[m] = r; m++ }
        }
        if (m == 0) return
        if (m == 1) { path.addCircle(fx[0], fy[0], fr[0], Path.Direction.CW); return }
        // 2) external tangents for every segment
        for (i in 0 until m - 1) {
            val dx = fx[i + 1] - fx[i]; val dy = fy[i + 1] - fy[i]
            val len = hypot(dx, dy)
            val ux = dx / len; val uy = dy / len
            sux[i] = ux; suy[i] = uy
            val k = ((fr[i] - fr[i + 1]) / len).coerceIn(-0.999f, 0.999f)
            val mm = sqrt(1f - k * k)
            val nx = -uy; val ny = ux
            val ax = ux * k + nx * mm; val ay = uy * k + ny * mm
            val bx = ux * k - nx * mm; val by = uy * k - ny * mm
            a0x[i] = fx[i] + ax * fr[i]; a0y[i] = fy[i] + ay * fr[i]
            a1x[i] = fx[i + 1] + ax * fr[i + 1]; a1y[i] = fy[i + 1] + ay * fr[i + 1]
            b0x[i] = fx[i] + bx * fr[i]; b0y[i] = fy[i] + by * fr[i]
            b1x[i] = fx[i + 1] + bx * fr[i + 1]; b1y[i] = fy[i + 1] + by * fr[i + 1]
        }
        val last = m - 2
        // 3) side A forward
        path.moveTo(a0x[0], a0y[0])
        for (i in 0..last) {
            path.lineTo(a1x[i], a1y[i])
            if (i < last) {
                val j = i + 1
                val cross = sux[i] * suy[j] - suy[i] * sux[j]
                if (cross <= 0f) arc(path, fx[j], fy[j], fr[j], a1x[i], a1y[i], a0x[j], a0y[j], Float.NaN, 0f)
                else { path.lineTo(fx[j], fy[j]); path.lineTo(a0x[j], a0y[j]) }
            }
        }
        // 4) end cap through the forward direction
        arc(path, fx[m - 1], fy[m - 1], fr[m - 1], a1x[last], a1y[last], b1x[last], b1y[last], sux[last], suy[last])
        // 5) side B backward
        for (i in last downTo 0) {
            path.lineTo(b0x[i], b0y[i])
            if (i > 0) {
                val cross = sux[i - 1] * suy[i] - suy[i - 1] * sux[i]
                if (cross > 0f) arc(path, fx[i], fy[i], fr[i], b0x[i], b0y[i], b1x[i - 1], b1y[i - 1], Float.NaN, 0f)
                else { path.lineTo(fx[i], fy[i]); path.lineTo(b1x[i - 1], b1y[i - 1]) }
            }
        }
        // 6) start cap through the backward direction
        arc(path, fx[0], fy[0], fr[0], b0x[0], b0y[0], a0x[0], a0y[0], -sux[0], -suy[0])
        path.close()
    }

    /** Arc on circle (cx,cy,r) from one point to another; short way, or through direction (vx,vy). */
    private fun arc(path: Path, cx: Float, cy: Float, r: Float, fromX: Float, fromY: Float, toX: Float, toY: Float, vx: Float, vy: Float) {
        val a0 = atan2(fromY - cy, fromX - cx)
        val a1 = atan2(toY - cy, toX - cx)
        var sweep = a1 - a0
        val pi = Math.PI.toFloat()
        while (sweep > pi) sweep -= 2 * pi
        while (sweep <= -pi) sweep += 2 * pi
        if (!vx.isNaN()) {
            val mid = a0 + sweep / 2
            if (cos(mid) * vx + sin(mid) * vy < 0f) sweep = if (sweep > 0) sweep - 2 * pi else sweep + 2 * pi
        }
        if (abs(sweep) < 0.02f || r < 1e-6f) { path.lineTo(toX, toY); return }
        oval.set(cx - r, cy - r, cx + r, cy + r)
        path.arcTo(oval, Math.toDegrees(a0.toDouble()).toFloat(), Math.toDegrees(sweep.toDouble()).toFloat(), false)
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
