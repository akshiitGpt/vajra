package com.akshit.folio

import com.akshit.folio.core.HandwritingRefiner
import com.akshit.folio.core.HandwritingRefiner.Ink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class HandwritingRefinerTest {
    /** Messy handwriting: 4 words, 3 letters each, 9° uphill baseline, bouncing words, uneven gaps, mixed slant. */
    private fun messyLine(): List<Ink> {
        val out = ArrayList<Ink>()
        val h = 20f
        val slope = tan(Math.toRadians(-9.0)).toFloat() // y-down: negative = uphill
        val gaps = floatArrayOf(0f, 14f, 38f, 9f)
        val bounce = floatArrayOf(0f, 6f, -5f, 3f)
        val slant = floatArrayOf(0.05f, 0.35f, -0.1f, 0.25f)
        var x = 0f
        var seed = 7
        fun rnd(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65535f - 0.5f }
        for (w in 0 until 4) {
            x += gaps[w]
            for (l in 0 until 3) {
                val tall = (w + l) % 3 == 0
                val n = 30
                val xs = FloatArray(n); val ys = FloatArray(n)
                for (j in 0 until n) {
                    val t = j / (n - 1f) * 2 * PI
                    var lx = (6 + 6 * sin(t)).toFloat()
                    var ly = if (tall) (-h * 1.6f * (1 - cos(t).toFloat()) / 2) else (-h * (1 - cos(t).toFloat()) / 2)
                    lx += -ly * slant[w] + rnd() * 1.2f
                    ly += rnd() * 1.2f
                    val px = x + lx
                    xs[j] = px; ys[j] = 100f + slope * px + bounce[w] + ly
                }
                out.add(Ink(xs, ys))
                x += 14f
            }
        }
        return out
    }

    @Test fun straightensAndEvensOut() {
        val r = HandwritingRefiner.refine(messyLine(), 0.9f)
        assertNotNull(r); r!!
        assertEquals(4, r.words)
        assertTrue("angle before ${r.angleBeforeDeg}", abs(r.angleBeforeDeg) > 6)
        assertTrue("angle after ${r.angleAfterDeg}", abs(r.angleAfterDeg) < 1.5)
        assertTrue("gap spread ${r.gapSpreadBefore} -> ${r.gapSpreadAfter}", r.gapSpreadAfter < r.gapSpreadBefore * 0.5f)
    }

    @Test fun zeroStrengthMeansNoChange() {
        assertNull(HandwritingRefiner.refine(messyLine(), 0f))
    }

    @Test fun drawingsAreLeftAlone() {
        val circle = Ink(FloatArray(60) { (100 * cos(it / 59.0 * 2 * PI)).toFloat() }, FloatArray(60) { (100 * sin(it / 59.0 * 2 * PI)).toFloat() })
        val line = Ink(floatArrayOf(-100f, 100f), floatArrayOf(150f, -150f))
        assertNull(HandwritingRefiner.refine(listOf(circle, line), 0.9f))
    }
}
