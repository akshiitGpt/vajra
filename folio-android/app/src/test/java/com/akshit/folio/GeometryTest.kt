package com.akshit.folio

import com.akshit.folio.core.Camera
import com.akshit.folio.core.ShapeRecognizer
import com.akshit.folio.core.ShapeRecognizer.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class GeometryTest {
    private fun wob(i: Int, amt: Float) = (sin(i * 1.7) * amt).toFloat()

    private fun recognize(xs: List<Float>, ys: List<Float>) = ShapeRecognizer.recognize(xs.toFloatArray(), ys.toFloatArray(), xs.size)

    @Test fun roughLineBecomesLine() {
        val xs = (0..40).map { it * 10f }; val ys = (0..40).map { wob(it, 3f) + it * 0.5f }
        assertEquals(Kind.LINE, recognize(xs, ys)?.kind)
    }

    @Test fun roughCircleBecomesCircle() {
        val n = 80
        val xs = (0..n).map { 200 + cos(it * 2 * PI / n).toFloat() * 100 + wob(it, 4f) }
        val ys = (0..n).map { 200 + sin(it * 2 * PI / n).toFloat() * 100 + wob(it + 3, 4f) }
        assertEquals(Kind.CIRCLE, recognize(xs, ys)?.kind)
    }

    @Test fun flatEllipseBecomesEllipse() {
        val n = 80
        val xs = (0..n).map { 200 + cos(it * 2 * PI / n).toFloat() * 200 + wob(it, 3f) }
        val ys = (0..n).map { 200 + sin(it * 2 * PI / n).toFloat() * 80 + wob(it + 3, 3f) }
        assertEquals(Kind.ELLIPSE, recognize(xs, ys)?.kind)
    }

    private fun poly(corners: List<Pair<Float, Float>>, per: Int, amt: Float): Pair<List<Float>, List<Float>> {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        for (k in 0 until corners.size - 1) for (j in 0 until per) {
            val t = j / per.toFloat()
            xs.add(corners[k].first + (corners[k + 1].first - corners[k].first) * t + wob(j, amt))
            ys.add(corners[k].second + (corners[k + 1].second - corners[k].second) * t + wob(j + 1, amt))
        }
        xs.add(corners.last().first); ys.add(corners.last().second)
        return xs to ys
    }

    @Test fun roughRectangleBecomesRectangle() {
        val (xs, ys) = poly(listOf(0f to 0f, 300f to 6f, 296f to 200f, 3f to 195f, 0f to 2f), 16, 3f)
        val r = recognize(xs, ys)
        assertEquals(Kind.RECTANGLE, r?.kind)
        // snapped to axis-aligned
        assertTrue(abs(r!!.ys[0] - r.ys[1]) < 0.01f)
    }

    @Test fun roughTriangleBecomesTriangle() {
        val (xs, ys) = poly(listOf(100f to 0f, 200f to 180f, 0f to 180f, 100f to 0f), 18, 3f)
        assertEquals(Kind.TRIANGLE, recognize(xs, ys)?.kind)
    }

    @Test fun scribbleIsNotAShape() {
        val xs = (0..120).map { it * 4f + sin(it * 0.9).toFloat() * 40 }
        val ys = (0..120).map { (sin(it * 0.37) * 80 + cos(it * 1.3) * 50).toFloat() }
        assertNull(recognize(xs, ys))
    }

    @Test fun cameraRoundTripAtDeepZoom() {
        val cam = Camera()
        cam.viewW = 2000f; cam.viewH = 1200f
        cam.cx = 123456.789; cam.cy = -98765.4321; cam.scale = 1e5; cam.setRotationRad(0.7)
        val wx = cam.cx + 0.00123; val wy = cam.cy - 0.00456
        val sx = cam.worldToScreenX(wx, wy); val sy = cam.worldToScreenY(wx, wy)
        assertEquals(wx, cam.screenToWorldX(sx, sy), 1e-7)
        assertEquals(wy, cam.screenToWorldY(sx, sy), 1e-7)
    }

    @Test fun pinchKeepsAnchorFixed() {
        val cam = Camera()
        cam.viewW = 1000f; cam.viewH = 800f; cam.update()
        val wx = cam.screenToWorldX(300f, 200f); val wy = cam.screenToWorldY(300f, 200f)
        cam.applyPinch(300f, 200f, 300f, 200f, 3.0, 0.4, 1e-6, 1e6)
        assertEquals(300f, cam.worldToScreenX(wx, wy), 1e-3f)
        assertEquals(200f, cam.worldToScreenY(wx, wy), 1e-3f)
        assertEquals(3.0, cam.scale, 1e-9)
    }
}
