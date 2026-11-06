package com.akshit.folio.core

import android.graphics.Matrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * Infinite-zoom camera.
 *
 * World coordinates are doubles so the canvas can be zoomed ~1e-6x .. 1e6x without
 * floating point jitter. Every stroke stores a double "origin" and float offsets
 * relative to it; [matrixFor] builds a float matrix that maps those local offsets
 * straight to screen pixels, so precision is never lost.
 */
class Camera {
    var cx = 0.0          // world point at the centre of the view
    var cy = 0.0
    var scale = 1.0       // screen pixels per world unit
    var rotation = 0.0    // radians
    var viewW = 1f
    var viewH = 1f

    private var cosR = 1.0
    private var sinR = 0.0

    fun set(o: Camera) {
        cx = o.cx; cy = o.cy; scale = o.scale; rotation = o.rotation
        viewW = o.viewW; viewH = o.viewH
        update()
    }

    fun copy(): Camera = Camera().also { it.set(this) }

    fun update() {
        cosR = cos(rotation); sinR = sin(rotation)
    }

    fun setRotationRad(r: Double) {
        var a = r % (Math.PI * 2)
        if (a > Math.PI) a -= Math.PI * 2
        if (a < -Math.PI) a += Math.PI * 2
        rotation = a; update()
    }

    fun worldToScreenX(wx: Double, wy: Double): Float {
        val dx = (wx - cx) * scale; val dy = (wy - cy) * scale
        return (dx * cosR - dy * sinR + viewW / 2.0).toFloat()
    }

    fun worldToScreenY(wx: Double, wy: Double): Float {
        val dx = (wx - cx) * scale; val dy = (wy - cy) * scale
        return (dx * sinR + dy * cosR + viewH / 2.0).toFloat()
    }

    fun screenToWorldX(sx: Float, sy: Float): Double {
        val dx = sx - viewW / 2.0; val dy = sy - viewH / 2.0
        return cx + (dx * cosR + dy * sinR) / scale
    }

    fun screenToWorldY(sx: Float, sy: Float): Double {
        val dx = sx - viewW / 2.0; val dy = sy - viewH / 2.0
        return cy + (-dx * sinR + dy * cosR) / scale
    }

    private val vals = FloatArray(9)

    /** Matrix mapping local coordinates (relative to world origin ox,oy) to screen. */
    fun matrixFor(ox: Double, oy: Double, out: Matrix) {
        val a = scale * cosR; val b = -scale * sinR
        val c = scale * sinR; val d = scale * cosR
        val px = ox - cx; val py = oy - cy
        vals[0] = a.toFloat(); vals[1] = b.toFloat(); vals[2] = (a * px + b * py + viewW / 2.0).toFloat()
        vals[3] = c.toFloat(); vals[4] = d.toFloat(); vals[5] = (c * px + d * py + viewH / 2.0).toFloat()
        vals[6] = 0f; vals[7] = 0f; vals[8] = 1f
        out.setValues(vals)
    }

    /**
     * Matrix that maps screen pixels as rendered by camera [from] to screen pixels for this camera.
     * Used to re-project the cached vector display list while a gesture is in progress.
     */
    fun relativeMatrix(from: Camera, out: Matrix) {
        // screen_from -> world -> screen_this. Both affine; compose numerically from 3 points.
        val sx0 = 0f; val sy0 = 0f; val sx1 = 1000f; val sy1 = 0f; val sx2 = 0f; val sy2 = 1000f
        val src = floatArrayOf(sx0, sy0, sx1, sy1, sx2, sy2)
        val dst = FloatArray(6)
        for (i in 0 until 3) {
            val wx = from.screenToWorldX(src[i * 2], src[i * 2 + 1])
            val wy = from.screenToWorldY(src[i * 2], src[i * 2 + 1])
            dst[i * 2] = worldToScreenX(wx, wy)
            dst[i * 2 + 1] = worldToScreenY(wx, wy)
        }
        out.setPolyToPoly(src, 0, dst, 0, 3)
    }

    /** Applies a two-finger transform: anchor (world point under old centroid) moves to new centroid. */
    fun applyPinch(
        oldX: Float, oldY: Float, newX: Float, newY: Float,
        scaleFactor: Double, rotationDelta: Double, minScale: Double, maxScale: Double
    ) {
        val wx = screenToWorldX(oldX, oldY)
        val wy = screenToWorldY(oldX, oldY)
        scale = (scale * scaleFactor).coerceIn(minScale, maxScale)
        setRotationRad(rotation + rotationDelta)
        // Solve for centre so that (wx,wy) maps to (newX,newY)
        val dx = newX - viewW / 2.0; val dy = newY - viewH / 2.0
        cx = wx - (dx * cosR + dy * sinR) / scale
        cy = wy - (-dx * sinR + dy * cosR) / scale
    }

    fun panBy(dxScreen: Float, dyScreen: Float) {
        cx -= (dxScreen * cosR + dyScreen * sinR) / scale
        cy -= (-dxScreen * sinR + dyScreen * cosR) / scale
    }

    /** World-space axis aligned bounds of the viewport (handles rotation). out = [minX,minY,maxX,maxY] */
    fun visibleWorldBounds(out: DoubleArray, marginPx: Float = 0f) {
        val xs = floatArrayOf(-marginPx, viewW + marginPx, viewW + marginPx, -marginPx)
        val ys = floatArrayOf(-marginPx, -marginPx, viewH + marginPx, viewH + marginPx)
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (i in 0 until 4) {
            val wx = screenToWorldX(xs[i], ys[i]); val wy = screenToWorldY(xs[i], ys[i])
            if (wx < minX) minX = wx; if (wx > maxX) maxX = wx
            if (wy < minY) minY = wy; if (wy > maxY) maxY = wy
        }
        out[0] = minX; out[1] = minY; out[2] = maxX; out[3] = maxY
    }
}
