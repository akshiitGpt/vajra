package com.akshit.folio.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "Draw and hold" shape recognition: turns a rough stroke into a perfect line, circle,
 * ellipse, arc, triangle, rectangle or polyline.
 */
object ShapeRecognizer {
    enum class Kind { LINE, CIRCLE, ELLIPSE, ARC, TRIANGLE, RECTANGLE, POLYGON, POLYLINE }

    class Result(val kind: Kind, val xs: FloatArray, val ys: FloatArray)

    fun recognize(xs: FloatArray, ys: FloatArray, n: Int): Result? {
        if (n < 3) return null
        var length = 0f
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until n) {
            if (i > 0) length += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            minX = min(minX, xs[i]); maxX = max(maxX, xs[i]); minY = min(minY, ys[i]); maxY = max(maxY, ys[i])
        }
        val size = max(maxX - minX, maxY - minY)
        if (size < 1e-4f || length < 1e-4f) return null

        // ---- straight line
        val chord = hypot(xs[n - 1] - xs[0], ys[n - 1] - ys[0])
        if (chord > 0.85f * length) {
            var maxDev = 0f
            for (i in 0 until n) maxDev = max(maxDev, distToLine(xs[i], ys[i], xs[0], ys[0], xs[n - 1], ys[n - 1]))
            if (maxDev < max(0.06f * chord, 1e-3f)) return Result(Kind.LINE, floatArrayOf(xs[0], xs[n - 1]), floatArrayOf(ys[0], ys[n - 1]))
        }

        val closed = chord < 0.22f * length && length > 2.2f * size
        val simplified = douglasPeucker(xs, ys, n, 0.07f * size)

        if (closed) {
            val ell = fitEllipse(xs, ys, n)
            val polyCorners = simplified.size - 1 // last == first approx
            val polyErr = polygonError(xs, ys, n, simplified)
            if (ell != null && (ell.second < 0.10f && (polyCorners > 5 || ell.second < polyErr / size * 0.9f))) {
                return ell.first
            }
            if (polyCorners in 3..6) {
                val cxs = FloatArray(polyCorners) { xs[simplified[it]] }
                val cys = FloatArray(polyCorners) { ys[simplified[it]] }
                return when (polyCorners) {
                    3 -> closedPoly(Kind.TRIANGLE, cxs, cys)
                    4 -> rectify(cxs, cys)
                    else -> closedPoly(Kind.POLYGON, cxs, cys)
                }
            }
            if (ell != null && ell.second < 0.16f) return ell.first
            return null
        }

        // ---- open: arc or polyline
        val circ = fitCircle(xs, ys, n)
        if (circ != null) {
            val (cx, cy, r) = circ
            var err = 0f
            for (i in 0 until n) err += abs(hypot(xs[i] - cx, ys[i] - cy) - r)
            err /= n
            if (err < 0.045f * r && r < size * 4 && simplified.size > 3) {
                val a0 = atan2(ys[0] - cy, xs[0] - cx)
                val am = atan2(ys[n / 2] - cy, xs[n / 2] - cx)
                val a1 = atan2(ys[n - 1] - cy, xs[n - 1] - cx)
                var sweep = norm(a1 - a0)
                // choose direction going through the middle point
                val mid = norm(am - a0)
                if ((sweep > 0) != (mid > 0) || abs(mid) > abs(sweep)) sweep = if (sweep > 0) sweep - 2 * PI.toFloat() else sweep + 2 * PI.toFloat()
                val k = 48
                return Result(Kind.ARC, FloatArray(k + 1) { cx + r * cos(a0 + sweep * it / k) }, FloatArray(k + 1) { cy + r * sin(a0 + sweep * it / k) })
            }
        }
        if (simplified.size in 3..5) {
            return Result(Kind.POLYLINE, FloatArray(simplified.size) { xs[simplified[it]] }, FloatArray(simplified.size) { ys[simplified[it]] })
        }
        return null
    }

    private fun norm(a: Float): Float {
        var x = a
        while (x > PI) x -= (2 * PI).toFloat()
        while (x < -PI) x += (2 * PI).toFloat()
        return x
    }

    private fun closedPoly(kind: Kind, cx: FloatArray, cy: FloatArray): Result =
        Result(kind, FloatArray(cx.size + 1) { cx[it % cx.size] }, FloatArray(cy.size + 1) { cy[it % cy.size] })

    private fun rectify(cx: FloatArray, cy: FloatArray): Result {
        // Make a true rectangle aligned with the dominant edge direction.
        val ang = atan2(cy[1] - cy[0], cx[1] - cx[0])
        val ca = cos(-ang); val sa = sin(-ang)
        var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE; var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
        val mx = cx.average().toFloat(); val my = cy.average().toFloat()
        for (i in cx.indices) {
            val u = (cx[i] - mx) * ca - (cy[i] - my) * sa
            val v = (cx[i] - mx) * sa + (cy[i] - my) * ca
            minU = min(minU, u); maxU = max(maxU, u); minV = min(minV, v); maxV = max(maxV, v)
        }
        // snap near-axis-aligned rectangles
        var a = ang
        val deg = Math.toDegrees(ang.toDouble())
        val snapped = Math.round(deg / 90.0) * 90.0
        if (abs(deg - snapped) < 8) a = Math.toRadians(snapped).toFloat()
        val c2 = cos(a); val s2 = sin(a)
        val us = floatArrayOf(minU, maxU, maxU, minU, minU)
        val vs = floatArrayOf(minV, minV, maxV, maxV, minV)
        return Result(Kind.RECTANGLE, FloatArray(5) { mx + us[it] * c2 - vs[it] * s2 }, FloatArray(5) { my + us[it] * s2 + vs[it] * c2 })
    }

    private fun polygonError(xs: FloatArray, ys: FloatArray, n: Int, idx: IntArray): Float {
        var err = 0f
        for (i in 0 until n) {
            var best = Float.MAX_VALUE
            for (k in 0 until idx.size - 1) best = min(best, distToSegment(xs[i], ys[i], xs[idx[k]], ys[idx[k]], xs[idx[k + 1]], ys[idx[k + 1]]))
            err += best
        }
        return err / n
    }

    /** Returns ellipse result and normalised error. */
    private fun fitEllipse(xs: FloatArray, ys: FloatArray, n: Int): Pair<Result, Float>? {
        var mx = 0.0; var my = 0.0
        for (i in 0 until n) { mx += xs[i]; my += ys[i] }
        mx /= n; my /= n
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in 0 until n) { val dx = xs[i] - mx; val dy = ys[i] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        sxx /= n; syy /= n; sxy /= n
        val tr = sxx + syy; val det = sxx * syy - sxy * sxy
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc; val l2 = tr / 2 - disc
        if (l2 <= 0) return null
        val theta = 0.5 * atan2(2 * sxy, sxx - syy)
        var a = sqrt(2 * l1); var b = sqrt(2 * l2)
        val ct = cos(theta); val st = sin(theta)
        // error: how far normalised radius deviates from 1
        var err = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - mx; val dy = ys[i] - my
            val u = dx * ct + dy * st; val v = -dx * st + dy * ct
            err += abs(sqrt((u / a) * (u / a) + (v / b) * (v / b)) - 1)
        }
        err /= n
        val circle = b / a > 0.86
        if (circle) { val r = (a + b) / 2; a = r; b = r }
        val k = 96
        val rx = FloatArray(k + 1); val ry = FloatArray(k + 1)
        for (i in 0..k) {
            val t = 2 * PI * i / k
            val u = a * cos(t); val v = b * sin(t)
            rx[i] = (mx + u * ct - v * st).toFloat(); ry[i] = (my + u * st + v * ct).toFloat()
        }
        return Result(if (circle) Kind.CIRCLE else Kind.ELLIPSE, rx, ry) to err.toFloat()
    }

    /** Algebraic (Kasa) least-squares circle fit. */
    private fun fitCircle(xs: FloatArray, ys: FloatArray, n: Int): Triple<Float, Float, Float>? {
        var mx = 0.0; var my = 0.0
        for (i in 0 until n) { mx += xs[i]; my += ys[i] }
        mx /= n; my /= n
        var suu = 0.0; var svv = 0.0; var suv = 0.0; var suuu = 0.0; var svvv = 0.0; var suvv = 0.0; var svuu = 0.0
        for (i in 0 until n) {
            val u = xs[i] - mx; val v = ys[i] - my
            suu += u * u; svv += v * v; suv += u * v
            suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u
        }
        val d = suu * svv - suv * suv
        if (abs(d) < 1e-12) return null
        val r1 = 0.5 * (suuu + suvv); val r2 = 0.5 * (svvv + svuu)
        val uc = (r1 * svv - r2 * suv) / d
        val vc = (r2 * suu - r1 * suv) / d
        val r = sqrt(uc * uc + vc * vc + (suu + svv) / n)
        return Triple((uc + mx).toFloat(), (vc + my).toFloat(), r.toFloat())
    }

    fun distToLine(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l = hypot(dx, dy)
        if (l < 1e-9f) return hypot(px - ax, py - ay)
        return abs((px - ax) * dy - (py - ay) * dx) / l
    }

    fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-12f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    fun douglasPeucker(xs: FloatArray, ys: FloatArray, n: Int, eps: Float): IntArray {
        val keep = BooleanArray(n)
        keep[0] = true; keep[n - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, n - 1))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast().let { it[0] to it[1] }
            var maxD = 0f; var idx = -1
            for (i in a + 1 until b) {
                val d = if (a == 0 && b == n - 1 && hypot(xs[a] - xs[b], ys[a] - ys[b]) < eps)
                    hypot(xs[i] - xs[a], ys[i] - ys[a]) else distToSegment(xs[i], ys[i], xs[a], ys[a], xs[b], ys[b])
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > eps) {
                keep[idx] = true
                stack.addLast(intArrayOf(a, idx)); stack.addLast(intArrayOf(idx, b))
            }
        }
        val out = ArrayList<Int>()
        for (i in 0 until n) if (keep[i]) out.add(i)
        return out.toIntArray()
    }
}
