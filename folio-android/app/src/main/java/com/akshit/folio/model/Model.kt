package com.akshit.folio.model

import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import com.akshit.folio.brush.BrushSpec
import java.util.concurrent.atomic.AtomicLong

/**
 * A single vector stroke. Points are stored relative to a double precision world origin
 * (ox, oy) so strokes stay razor sharp at any zoom depth.
 *
 * Point layout (STRIDE floats): x, y, pressure(0..1), tilt(rad), timeMs
 */
class Stroke(
    val brush: BrushSpec,
    var color: Int,
    /** Brush diameter in world units. */
    var width: Float,
    var ox: Double,
    var oy: Double,
) {
    val id: Long = NEXT_ID.incrementAndGet()
    var pts = FloatArray(STRIDE * 64)
    var n = 0
    var seed = (id * 2654435761L).toInt()

    /** Local-space bounds including brush radius; valid after [invalidate]. */
    val bounds = RectF()

    // ---- render cache (built by StrokeRenderer) ----
    @Transient var path: Path? = null
    @Transient var stamps: FloatArray? = null
    @Transient var stampCount = 0
    @Transient var meanAlpha = 1f
    @Transient var dirty = true

    fun add(x: Float, y: Float, p: Float, tilt: Float, t: Float) {
        if ((n + 1) * STRIDE > pts.size) pts = pts.copyOf(pts.size * 2)
        val i = n * STRIDE
        pts[i] = x; pts[i + 1] = y; pts[i + 2] = p; pts[i + 3] = tilt; pts[i + 4] = t
        n++
        dirty = true
    }

    fun x(i: Int) = pts[i * STRIDE]
    fun y(i: Int) = pts[i * STRIDE + 1]
    fun p(i: Int) = pts[i * STRIDE + 2]
    fun tilt(i: Int) = pts[i * STRIDE + 3]
    fun t(i: Int) = pts[i * STRIDE + 4]

    fun setXY(i: Int, x: Float, y: Float) { pts[i * STRIDE] = x; pts[i * STRIDE + 1] = y; dirty = true }

    fun truncate(count: Int) { n = count.coerceAtMost(n); dirty = true }

    fun invalidate() { dirty = true; path = null; stamps = null }

    fun worldMinX() = ox + bounds.left
    fun worldMinY() = oy + bounds.top
    fun worldMaxX() = ox + bounds.right
    fun worldMaxY() = oy + bounds.bottom

    fun intersectsWorld(minX: Double, minY: Double, maxX: Double, maxY: Double): Boolean =
        worldMaxX() >= minX && worldMinX() <= maxX && worldMaxY() >= minY && worldMinY() <= maxY

    /** Deep copy (new id). */
    fun duplicate(): Stroke {
        val s = Stroke(brush.copy(), color, width, ox, oy)
        s.pts = pts.copyOf(); s.n = n; s.seed = seed
        s.bounds.set(bounds)
        return s
    }

    /** New stroke from a point range of this one. */
    fun slice(from: Int, toExclusive: Int): Stroke {
        val s = Stroke(brush.copy(), color, width, ox, oy)
        val count = toExclusive - from
        s.pts = FloatArray(maxOf(count, 2) * STRIDE)
        System.arraycopy(pts, from * STRIDE, s.pts, 0, count * STRIDE)
        s.n = count
        s.seed = seed
        return s
    }

    companion object {
        const val STRIDE = 5
        private val NEXT_ID = AtomicLong(System.nanoTime() and 0xFFFFFFFFL)
    }
}

class Layer(var name: String) {
    val id: Long = System.nanoTime()
    var visible = true
    var locked = false
    var opacity = 1f
    val strokes = ArrayList<Stroke>()
}

enum class Pattern(val label: String) {
    NONE("Blank"), DOTS("Dots"), GRID("Grid"), GRAPH("Graph"), LINES("Ruled"),
    ISOMETRIC("Isometric"), ISO_DOTS("Iso Dots"), TRIANGLES("Triangles"), HEX("Hexagons"),
    PERSPECTIVE_1("1-pt Perspective"), PERSPECTIVE_2("2-pt Perspective"), PERSPECTIVE_3("3-pt Perspective")
}

class BackgroundSpec {
    var pattern = Pattern.DOTS
    var spacing = 32f            // world units at 100% zoom
    var paperColor = Color.rgb(250, 249, 246)
    var lineColor = Color.rgb(120, 130, 150)
    var lineAlpha = 0.35f
    var majorEvery = 5
    var adaptive = true          // grid subdivides/merges as you zoom (infinite grid)
    var lineWidth = 1f           // px
    var vpDistance = 900f        // perspective vanishing point distance (world)
    var horizon = 0f             // perspective horizon y (world)

    fun copy(): BackgroundSpec = BackgroundSpec().also {
        it.pattern = pattern; it.spacing = spacing; it.paperColor = paperColor; it.lineColor = lineColor
        it.lineAlpha = lineAlpha; it.majorEvery = majorEvery; it.adaptive = adaptive; it.lineWidth = lineWidth
        it.vpDistance = vpDistance; it.horizon = horizon
    }

    companion object {
        val PAPERS = intArrayOf(
            Color.rgb(250, 249, 246), Color.WHITE, Color.rgb(246, 239, 222), Color.rgb(232, 232, 230),
            Color.rgb(210, 190, 160), Color.rgb(28, 63, 120), Color.rgb(48, 50, 54), Color.rgb(18, 18, 20)
        )
        val PAPER_NAMES = arrayOf("Paper", "White", "Cream", "Light Grey", "Kraft", "Blueprint", "Slate", "Night")
    }
}

class Document(var id: String, var name: String) {
    val layers = ArrayList<Layer>().apply { add(Layer("Layer 1")) }
    var activeLayer = 0
    var background = BackgroundSpec()
    var camCx = 0.0
    var camCy = 0.0
    var camScale = 1.0
    var camRot = 0.0
    var created = System.currentTimeMillis()
    var modified = System.currentTimeMillis()

    fun active(): Layer {
        if (activeLayer !in layers.indices) activeLayer = layers.size - 1
        return layers[activeLayer]
    }

    fun strokeCount(): Int = layers.sumOf { it.strokes.size }

    fun findLayerOf(s: Stroke): Layer? = layers.firstOrNull { l -> l.strokes.any { it === s } }
}
