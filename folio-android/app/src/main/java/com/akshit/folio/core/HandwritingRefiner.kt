package com.akshit.folio.core

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Neatens handwriting while keeping it *your* handwriting (in the spirit of Apple Notes'
 * "refine handwriting"). Purely geometric, runs on-device in well under a millisecond per line:
 *
 *  1. straightens the baseline (rotates the line to horizontal)
 *  2. sits every word on the same baseline (descenders are detected and left alone)
 *  3. makes the slant consistent between words (and slightly more upright)
 *  4. evens out word sizes
 *  5. evens out the spacing between words
 *  6. smooths shaky jitter in each stroke
 *
 * Coordinates are y-down. Each [Ink] is one stroke's points in a shared local space.
 */
object HandwritingRefiner {
    class Ink(val xs: FloatArray, val ys: FloatArray) {
        val n get() = xs.size
        fun minX() = xs.min(); fun maxX() = xs.max(); fun minY() = ys.min(); fun maxY() = ys.max()
        fun height() = maxY() - minY()
        fun copy() = Ink(xs.copyOf(), ys.copyOf())
    }

    class Result(
        val strokes: List<Ink>,
        val angleBeforeDeg: Double,
        val angleAfterDeg: Double,
        val words: Int,
        val gapSpreadBefore: Float,
        val gapSpreadAfter: Float,
    )

    /** @param strength 0 (off) .. 1 (strong). Returns null when the strokes don't look like a line of writing. */
    fun refine(input: List<Ink>, strength: Float): Result? {
        val k = strength.coerceIn(0f, 1f)
        if (input.size < 2 || input.size > 600 || k <= 0f) return null
        if (input.any { it.n == 0 }) return null
        val heights = input.map { it.height() }.sorted()
        val maxH = heights.last()
        // letter size = median height of the non-dot strokes
        val core = heights.filter { it > maxH * 0.25f }
        val h = core[core.size / 2].coerceAtLeast(1e-4f)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (s in input) { minX = min(minX, s.minX()); maxX = max(maxX, s.maxX()); minY = min(minY, s.minY()); maxY = max(maxY, s.maxY()) }
        val width = maxX - minX
        // Writing is a wide band of similar sized marks; drawings are not.
        if (width < 1.6f * h) return null
        if (maxH > 4.5f * h && input.count { it.height() > 3f * h } > 1) return null
        if (maxY - minY > max(4.5f * h, width * 0.9f)) return null

        val out = input.map { it.copy() }

        // ---------------- 1. baseline angle from the lower envelope
        val base0 = fitBaseline(out, minX, maxX, h)
        val angleBefore = Math.toDegrees(atan(base0.first.toDouble()))
        if (abs(angleBefore) > 25) return null // deliberately angled writing / not a line
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        // a straight baseline is the main win, so rotate fully from medium strength upward
        rotateAll(out, cx, cy, -atan(base0.first) * min(1f, 0.5f + k))

        // ---------------- words (clusters separated by gaps)
        val words = clusterWords(out, h)

        // ---------------- 2. sit each word on the common baseline
        var (slope, icpt) = fitBaseline(out, out.minOf { it.minX() }, out.maxOf { it.maxX() }, h)
        for (w in words) {
            val wb = wordBaseline(out, w, slope, icpt, h) ?: continue
            val wx = (w.minX + w.maxX) / 2
            val line = slope * wx + icpt
            val dy = (line - wb).coerceIn(-0.6f * h, 0.6f * h) * k
            for (i in w.strokes) for (j in 0 until out[i].n) out[i].ys[j] += dy
        }

        // ---------------- 3. consistent slant
        val slants = words.map { slantOf(out, it, h) }
        var tw = 0f; var ts = 0f
        for ((s, wgt) in slants) { ts += s * wgt; tw += wgt }
        if (tw > 0f) {
            val mean = ts / tw
            val target = mean * (1f - 0.35f * k) // a touch more upright
            fitBaseline(out, out.minOf { it.minX() }, out.maxOf { it.maxX() }, h).let { slope = it.first; icpt = it.second }
            for ((wi, w) in words.withIndex()) {
                val (s, wgt) = slants[wi]
                if (wgt <= 0f) continue
                val delta = (s - target).coerceIn(-0.6f, 0.6f) * k
                val wx = (w.minX + w.maxX) / 2
                val by = slope * wx + icpt
                // shear: x' = x - delta * (y - baseline)
                for (i in w.strokes) for (j in 0 until out[i].n) out[i].xs[j] -= delta * (out[i].ys[j] - by)
            }
        }

        // ---------------- 4. even word sizes
        val wordH = words.map { w -> w.strokes.map { out[it].height() }.filter { it > 0.25f * maxH }.let { l -> if (l.isEmpty()) 0f else l.sorted()[l.size / 2] } }
        val nonZero = wordH.filter { it > 0f }.sorted()
        if (nonZero.size >= 2) {
            val target = nonZero[nonZero.size / 2]
            for ((wi, w) in words.withIndex()) {
                if (wordH[wi] <= 0f) continue
                val f = (target / wordH[wi]).coerceIn(0.75f, 1.33f)
                val sc = 1f + (f - 1f) * 0.6f * k
                if (abs(sc - 1f) < 0.01f) continue
                val ax = out.filterIndexed { i, _ -> i in w.strokes }.minOf { it.minX() }
                val ay = slope * ((w.minX + w.maxX) / 2) + icpt
                for (i in w.strokes) for (j in 0 until out[i].n) {
                    out[i].xs[j] = ax + (out[i].xs[j] - ax) * sc
                    out[i].ys[j] = ay + (out[i].ys[j] - ay) * sc
                }
            }
        }

        // ---------------- 5. even spacing between words
        val words2 = clusterWords(out, h)
        val gaps = (1 until words2.size).map { words2[it].minX - words2[it - 1].maxX }
        val spreadBefore = spread(gaps)
        if (gaps.size >= 2) {
            val target = gaps.sorted()[gaps.size / 2]
            var shift = 0f
            for (wi in 1 until words2.size) {
                val g = gaps[wi - 1]
                shift += (target - g) * 0.85f * k
                for (i in words2[wi].strokes) for (j in 0 until out[i].n) out[i].xs[j] += shift
            }
        }

        // ---------------- 6. smooth jitter (keeps endpoints and corners mostly intact)
        val iters = (1 + 2 * k).toInt()
        for (s in out) smooth(s, 0.3f * k, iters)

        val after = fitBaseline(out, out.minOf { it.minX() }, out.maxOf { it.maxX() }, h)
        val words3 = clusterWords(out, h)
        val gapsAfter = (1 until words3.size).map { words3[it].minX - words3[it - 1].maxX }
        return Result(out, angleBefore, Math.toDegrees(atan(after.first.toDouble())), words2.size, spreadBefore, spread(gapsAfter))
    }

    private fun spread(v: List<Float>): Float {
        if (v.size < 2) return 0f
        val m = v.average().toFloat()
        return kotlin.math.sqrt(v.sumOf { ((it - m) * (it - m)).toDouble() }.toFloat() / v.size)
    }

    class Word(val strokes: MutableList<Int>, var minX: Float, var maxX: Float)

    private fun clusterWords(s: List<Ink>, h: Float): List<Word> {
        val order = s.indices.sortedBy { s[it].minX() }
        val words = ArrayList<Word>()
        for (i in order) {
            val last = words.lastOrNull()
            if (last != null && s[i].minX() <= last.maxX + 0.42f * h) {
                last.strokes.add(i); last.maxX = max(last.maxX, s[i].maxX())
            } else words.add(Word(mutableListOf(i), s[i].minX(), s[i].maxX()))
        }
        return words
    }

    /** Lower envelope samples per x-bin, robust least-squares line fit. Returns slope, intercept (y = slope*x + b). */
    private fun fitBaseline(s: List<Ink>, minX: Float, maxX: Float, h: Float): Pair<Float, Float> {
        val bin = max(h * 0.5f, 1e-4f)
        val nb = ((maxX - minX) / bin).toInt() + 1
        val low = FloatArray(nb) { -Float.MAX_VALUE }
        val lowX = FloatArray(nb)
        for (ink in s) for (j in 0 until ink.n) {
            val b = ((ink.xs[j] - minX) / bin).toInt().coerceIn(0, nb - 1)
            if (ink.ys[j] > low[b]) { low[b] = ink.ys[j]; lowX[b] = ink.xs[j] }
        }
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        for (b in 0 until nb) if (low[b] > -Float.MAX_VALUE) { xs.add(lowX[b]); ys.add(low[b]) }
        if (xs.size < 2) return 0f to (ys.firstOrNull() ?: 0f)
        var (m, c) = lsq(xs, ys)
        // drop descenders / outliers and refit twice
        repeat(2) {
            val kx = ArrayList<Float>(); val ky = ArrayList<Float>()
            for (i in xs.indices) {
                val r = ys[i] - (m * xs[i] + c)
                if (r < 0.3f * h && r > -0.45f * h) { kx.add(xs[i]); ky.add(ys[i]) }
            }
            if (kx.size >= 2) { val f = lsq(kx, ky); m = f.first; c = f.second }
        }
        return m to c
    }

    private fun lsq(xs: List<Float>, ys: List<Float>): Pair<Float, Float> {
        val n = xs.size
        val mx = xs.average(); val my = ys.average()
        var sxx = 0.0; var sxy = 0.0
        for (i in 0 until n) { sxx += (xs[i] - mx) * (xs[i] - mx); sxy += (xs[i] - mx) * (ys[i] - my) }
        val m = if (sxx < 1e-12) 0.0 else sxy / sxx
        return m.toFloat() to (my - m * mx).toFloat()
    }

    private fun wordBaseline(s: List<Ink>, w: Word, slope: Float, icpt: Float, h: Float): Float? {
        // lowest points of the word that are not descenders, projected onto the word centre
        val wx = (w.minX + w.maxX) / 2
        val cand = ArrayList<Float>()
        val bin = h * 0.5f
        val nb = ((w.maxX - w.minX) / bin).toInt() + 1
        val low = FloatArray(nb) { -Float.MAX_VALUE }
        for (i in w.strokes) for (j in 0 until s[i].n) {
            val b = ((s[i].xs[j] - w.minX) / bin).toInt().coerceIn(0, nb - 1)
            val yAtCentre = s[i].ys[j] - slope * (s[i].xs[j] - wx)
            if (yAtCentre > low[b]) low[b] = yAtCentre
        }
        val line = slope * wx + icpt
        for (v in low) if (v > -Float.MAX_VALUE && v - line < 0.45f * h && v - line > -0.8f * h) cand.add(v)
        if (cand.isEmpty()) return null
        cand.sort()
        return cand[cand.size / 2]
    }

    /** Returns (shear dx/dy of near-vertical strokes, weight). */
    private fun slantOf(s: List<Ink>, w: Word, h: Float): Pair<Float, Float> {
        var sum = 0f; var wsum = 0f
        val minLen = 0.12f * h
        for (i in w.strokes) {
            val ink = s[i]
            var j0 = 0
            for (j in 1 until ink.n) {
                val dx = ink.xs[j] - ink.xs[j0]; val dy = ink.ys[j] - ink.ys[j0]
                val len = kotlin.math.hypot(dx, dy)
                if (len < minLen) continue
                if (abs(dy) > 1.6f * abs(dx)) {
                    val shear = dx / dy // same sign for up- and down-strokes
                    sum += shear * len; wsum += len
                }
                j0 = j
            }
        }
        return if (wsum > 0f) (sum / wsum) to wsum else 0f to 0f
    }

    private fun rotateAll(s: List<Ink>, cx: Float, cy: Float, ang: Float) {
        val c = cos(ang); val sn = sin(ang)
        for (ink in s) for (j in 0 until ink.n) {
            val x = ink.xs[j] - cx; val y = ink.ys[j] - cy
            ink.xs[j] = cx + x * c - y * sn
            ink.ys[j] = cy + x * sn + y * c
        }
    }

    private fun smooth(ink: Ink, w: Float, iters: Int) {
        if (ink.n < 5 || w <= 0f) return
        val tx = FloatArray(ink.n); val ty = FloatArray(ink.n)
        repeat(iters) {
            for (j in 1 until ink.n - 1) {
                // keep sharp corners: only smooth where the turn is gentle
                val ax = ink.xs[j] - ink.xs[j - 1]; val ay = ink.ys[j] - ink.ys[j - 1]
                val bx = ink.xs[j + 1] - ink.xs[j]; val by = ink.ys[j + 1] - ink.ys[j]
                val la = kotlin.math.hypot(ax, ay); val lb = kotlin.math.hypot(bx, by)
                val cosT = if (la > 0 && lb > 0) (ax * bx + ay * by) / (la * lb) else 1f
                val ww = if (cosT < 0.2f) 0f else w
                tx[j] = ink.xs[j] + ww * ((ink.xs[j - 1] + ink.xs[j + 1]) / 2 - ink.xs[j])
                ty[j] = ink.ys[j] + ww * ((ink.ys[j - 1] + ink.ys[j + 1]) / 2 - ink.ys[j])
            }
            for (j in 1 until ink.n - 1) { ink.xs[j] = tx[j]; ink.ys[j] = ty[j] }
        }
    }

    @Suppress("unused") private fun shearX(dx: Float) = tan(dx)
}
