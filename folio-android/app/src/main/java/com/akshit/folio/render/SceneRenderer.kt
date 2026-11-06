package com.akshit.folio.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.akshit.folio.brush.StrokeRenderer
import com.akshit.folio.core.Camera
import com.akshit.folio.model.Document
import com.akshit.folio.model.Layer
import com.akshit.folio.model.Stroke
import kotlin.math.max

/** Draws document layers for a camera; shared by the live view, thumbnails, exports and eyedropper. */
object SceneRenderer {
    private val m = Matrix()
    private val wb = DoubleArray(4)
    private val layerPaint = Paint()

    /**
     * @param layers range of layer indices to draw
     * @param skip strokes not to draw (e.g. the live selection)
     * @param marginPx extra margin for culling so small pans don't reveal missing strokes
     */
    fun drawLayers(
        c: Canvas, cam: Camera, doc: Document, layers: IntRange,
        skip: Set<Stroke>? = null, marginPx: Float = 0f
    ): Int {
        cam.visibleWorldBounds(wb, marginPx)
        val minWorld = 0.25 / cam.scale // strokes smaller than 1/4 px are invisible
        var drawn = 0
        for (li in layers) {
            val layer = doc.layers.getOrNull(li) ?: continue
            if (!layer.visible || layer.opacity <= 0.001f) continue
            val needsLayer = layer.opacity < 0.999f
            if (needsLayer) {
                layerPaint.alpha = (layer.opacity * 255).toInt()
                c.saveLayer(null, layerPaint)
            }
            drawn += drawStrokes(c, cam, layer, skip, minWorld)
            if (needsLayer) c.restore()
        }
        return drawn
    }

    private fun drawStrokes(c: Canvas, cam: Camera, layer: Layer, skip: Set<Stroke>?, minWorld: Double): Int {
        var drawn = 0
        val strokes = layer.strokes
        for (i in strokes.indices) {
            val s = strokes[i]
            if (skip != null && skip.contains(s)) continue
            if (s.dirty || (s.path == null && s.stamps == null)) StrokeRenderer.build(s)
            if (!s.intersectsWorld(wb[0], wb[1], wb[2], wb[3])) continue
            if (max(s.bounds.width(), s.bounds.height()) < minWorld) continue
            cam.matrixFor(s.ox, s.oy, m)
            StrokeRenderer.draw(c, s, m)
            drawn++
        }
        return drawn
    }

    fun drawStroke(c: Canvas, cam: Camera, s: Stroke, extra: Matrix? = null, colorOverride: Int? = null, alpha: Float = 1f) {
        cam.matrixFor(s.ox, s.oy, m)
        if (extra != null) m.postConcat(extra)
        StrokeRenderer.draw(c, s, m, colorOverride, alpha)
    }

    /** World bounds of all visible strokes, or null when empty. [minX,minY,maxX,maxY] */
    fun contentBounds(doc: Document, onlySelection: Collection<Stroke>? = null): DoubleArray? {
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        var any = false
        val strokes = onlySelection ?: doc.layers.filter { it.visible }.flatMap { it.strokes }
        for (s in strokes) {
            if (s.dirty || (s.path == null && s.stamps == null)) StrokeRenderer.build(s)
            if (s.bounds.isEmpty && s.n == 0) continue
            any = true
            minX = minOf(minX, s.worldMinX()); minY = minOf(minY, s.worldMinY())
            maxX = maxOf(maxX, s.worldMaxX()); maxY = maxOf(maxY, s.worldMaxY())
        }
        return if (any) doubleArrayOf(minX, minY, maxX, maxY) else null
    }

    /** Renders the drawing to a bitmap that fits [maxSide] (used for thumbnails and PNG export). */
    fun renderToBitmap(doc: Document, maxSide: Int, withBackground: Boolean, bgRenderer: BackgroundRenderer?, density: Float, padding: Float = 0.06f, viewCam: Camera? = null): Bitmap {
        val b = contentBounds(doc)
        val cam = Camera()
        val w: Int; val h: Int
        if (b == null && viewCam != null) {
            w = maxSide; h = (maxSide * viewCam.viewH / viewCam.viewW).toInt().coerceAtLeast(1)
            cam.set(viewCam); cam.scale = viewCam.scale * w / viewCam.viewW; cam.viewW = w.toFloat(); cam.viewH = h.toFloat(); cam.update()
        } else if (b == null) {
            w = maxSide; h = (maxSide * 0.75f).toInt()
            cam.viewW = w.toFloat(); cam.viewH = h.toFloat(); cam.update()
        } else {
            val bw = (b[2] - b[0]).coerceAtLeast(1e-6); val bh = (b[3] - b[1]).coerceAtLeast(1e-6)
            val aspect = bw / bh
            if (aspect >= 1) { w = maxSide; h = (maxSide / aspect).toInt().coerceAtLeast(16) } else { h = maxSide; w = (maxSide * aspect).toInt().coerceAtLeast(16) }
            cam.cx = (b[0] + b[2]) / 2; cam.cy = (b[1] + b[3]) / 2
            cam.scale = minOf(w / bw, h / bh) * (1 - 2 * padding)
            cam.viewW = w.toFloat(); cam.viewH = h.toFloat(); cam.update()
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (withBackground) {
            if (bgRenderer != null) bgRenderer.draw(c, cam, doc.background, density * (w / 1200f).coerceIn(0.3f, 2f))
            else c.drawColor(doc.background.paperColor)
        }
        drawLayers(c, cam, doc, doc.layers.indices)
        return bmp
    }
}
