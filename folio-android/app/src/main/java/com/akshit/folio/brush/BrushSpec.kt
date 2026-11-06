package com.akshit.folio.brush

import java.io.DataInputStream
import java.io.DataOutputStream

enum class RenderKind { OUTLINE, STAMP, FILL }

enum class BrushType(val label: String, val icon: String, val kind: RenderKind) {
    PEN("Pen", "pen", RenderKind.OUTLINE),
    FINELINER("Fineliner", "fineliner", RenderKind.OUTLINE),
    PENCIL("Pencil", "pencil", RenderKind.OUTLINE),
    MARKER("Marker", "marker", RenderKind.OUTLINE),
    HIGHLIGHTER("Highlighter", "highlighter", RenderKind.OUTLINE),
    INK_BRUSH("Ink Brush", "brush", RenderKind.OUTLINE),
    CALLIGRAPHY("Calligraphy", "calligraphy", RenderKind.OUTLINE),
    TECHNICAL("Technical", "technical", RenderKind.OUTLINE),
    AIRBRUSH("Airbrush", "airbrush", RenderKind.STAMP),
    WATERCOLOR("Watercolor", "watercolor", RenderKind.STAMP),
    CHARCOAL("Charcoal", "charcoal", RenderKind.STAMP),
    CRAYON("Crayon", "crayon", RenderKind.STAMP),
    FILL("Fill", "fill", RenderKind.FILL);

    companion object {
        fun byOrdinal(i: Int): BrushType = entries.getOrElse(i) { PEN }
    }
}

enum class BlendKind(val label: String) { NORMAL("Normal"), MULTIPLY("Multiply"), SCREEN("Screen"), DARKEN("Darken"), LIGHTEN("Lighten") }

/**
 * All "pro" parameters of a brush. Every field is user editable from the brush settings panel.
 * Sizes are in screen pixels at the zoom level where the stroke is drawn (like Concepts:
 * zoom in to draw finer details with the same brush).
 */
class BrushSpec(var type: BrushType = BrushType.PEN) {
    var size = 4f                 // diameter in px
    var opacity = 1f              // 0..1
    var smoothing = 0.35f         // streamline 0..1
    var pressureSize = 0.8f       // how much pressure changes width 0..1
    var pressureOpacity = 0f      // how much pressure changes opacity 0..1
    var pressureGamma = 1f        // pressure response curve (<1 soft, >1 firm)
    var minSize = 0.1f            // minimum width fraction
    var tiltSize = 0f             // tilt widens the stroke (pencil shading) 0..1
    var velocitySize = 0f         // -1..1 fast strokes thinner (+) or thicker (-)
    var taperStart = 0.15f        // 0..1
    var taperEnd = 0.25f          // 0..1
    var nibAngle = 45f            // degrees, for flat nibs
    var nibRoundness = 1f         // 1 = round, smaller = flat chisel
    var followDirection = false   // nib rotates with stroke direction
    var spacing = 0.12f           // stamp spacing as fraction of size
    var flow = 0.3f               // stamp alpha 0..1
    var jitterSize = 0f
    var jitterOpacity = 0f
    var jitterAngle = 0f
    var scatter = 0f
    var grain = 0f                // paper texture 0..1
    var wetEdges = 0f             // watercolor darker rim 0..1
    var blend = BlendKind.NORMAL
    var eraserWholeStroke = false // eraser only: delete whole strokes instead of slicing

    fun copy(): BrushSpec {
        val b = BrushSpec(type)
        val a = toArray()
        b.fromArray(a)
        return b
    }

    private fun toArray(): FloatArray = floatArrayOf(
        size, opacity, smoothing, pressureSize, pressureOpacity, pressureGamma, minSize, tiltSize,
        velocitySize, taperStart, taperEnd, nibAngle, nibRoundness, if (followDirection) 1f else 0f,
        spacing, flow, jitterSize, jitterOpacity, jitterAngle, scatter, grain, wetEdges,
        blend.ordinal.toFloat(), if (eraserWholeStroke) 1f else 0f
    )

    private fun fromArray(a: FloatArray) {
        fun g(i: Int, d: Float) = if (i < a.size) a[i] else d
        size = g(0, size); opacity = g(1, opacity); smoothing = g(2, smoothing)
        pressureSize = g(3, pressureSize); pressureOpacity = g(4, pressureOpacity)
        pressureGamma = g(5, pressureGamma); minSize = g(6, minSize); tiltSize = g(7, tiltSize)
        velocitySize = g(8, velocitySize); taperStart = g(9, taperStart); taperEnd = g(10, taperEnd)
        nibAngle = g(11, nibAngle); nibRoundness = g(12, nibRoundness); followDirection = g(13, 0f) > 0.5f
        spacing = g(14, spacing); flow = g(15, flow); jitterSize = g(16, jitterSize)
        jitterOpacity = g(17, jitterOpacity); jitterAngle = g(18, jitterAngle); scatter = g(19, scatter)
        grain = g(20, grain); wetEdges = g(21, wetEdges)
        blend = BlendKind.entries.getOrElse(g(22, 0f).toInt()) { BlendKind.NORMAL }
        eraserWholeStroke = g(23, 0f) > 0.5f
    }

    fun write(out: DataOutputStream) {
        out.writeByte(type.ordinal)
        val a = toArray()
        out.writeByte(a.size)
        for (v in a) out.writeFloat(v)
    }

    fun encode(): String = type.ordinal.toString() + ";" + toArray().joinToString(",")

    companion object {
        fun read(inp: DataInputStream): BrushSpec {
            val b = BrushSpec(BrushType.byOrdinal(inp.readUnsignedByte()))
            val n = inp.readUnsignedByte()
            val a = FloatArray(n) { inp.readFloat() }
            b.fromArray(a)
            return b
        }

        fun decode(s: String): BrushSpec? = try {
            val parts = s.split(";")
            val b = BrushSpec(BrushType.byOrdinal(parts[0].toInt()))
            b.fromArray(parts[1].split(",").map { it.toFloat() }.toFloatArray())
            b
        } catch (e: Exception) { null }

        /** Factory presets, one per brush type. */
        fun preset(t: BrushType): BrushSpec = BrushSpec(t).apply {
            when (t) {
                BrushType.PEN -> { size = 4f; pressureSize = 0.85f; taperStart = 0.2f; taperEnd = 0.3f; smoothing = 0.35f }
                BrushType.FINELINER -> { size = 2.5f; pressureSize = 0f; taperStart = 0f; taperEnd = 0f; minSize = 1f; smoothing = 0.3f }
                BrushType.TECHNICAL -> { size = 1.5f; pressureSize = 0.15f; taperStart = 0f; taperEnd = 0f; smoothing = 0.55f }
                BrushType.PENCIL -> { size = 3f; opacity = 0.85f; pressureSize = 0.45f; pressureOpacity = 0.6f; tiltSize = 0.8f; grain = 0.7f; taperStart = 0.1f; taperEnd = 0.1f; smoothing = 0.15f }
                BrushType.MARKER -> { size = 18f; opacity = 0.55f; pressureSize = 0.15f; taperStart = 0f; taperEnd = 0f; nibRoundness = 0.55f; nibAngle = 30f; smoothing = 0.3f; minSize = 0.7f }
                BrushType.HIGHLIGHTER -> { size = 26f; opacity = 0.4f; pressureSize = 0f; minSize = 1f; taperStart = 0f; taperEnd = 0f; nibRoundness = 0.3f; nibAngle = 0f; blend = BlendKind.MULTIPLY; smoothing = 0.5f }
                BrushType.INK_BRUSH -> { size = 14f; pressureSize = 1f; pressureGamma = 1.5f; minSize = 0.03f; velocitySize = 0.35f; taperStart = 0.35f; taperEnd = 0.5f; smoothing = 0.4f }
                BrushType.CALLIGRAPHY -> { size = 16f; pressureSize = 0.4f; nibRoundness = 0.15f; nibAngle = 40f; taperStart = 0.05f; taperEnd = 0.1f; smoothing = 0.4f }
                BrushType.AIRBRUSH -> { size = 60f; opacity = 1f; flow = 0.06f; spacing = 0.08f; pressureSize = 0.3f; pressureOpacity = 1f; smoothing = 0.3f }
                BrushType.WATERCOLOR -> { size = 34f; flow = 0.08f; spacing = 0.1f; pressureSize = 0.5f; pressureOpacity = 0.6f; wetEdges = 0.6f; jitterSize = 0.15f; smoothing = 0.35f }
                BrushType.CHARCOAL -> { size = 12f; flow = 0.35f; spacing = 0.12f; grain = 0.9f; pressureSize = 0.4f; pressureOpacity = 0.8f; tiltSize = 1f; jitterAngle = 1f; smoothing = 0.15f }
                BrushType.CRAYON -> { size = 9f; flow = 0.55f; spacing = 0.18f; grain = 0.8f; pressureSize = 0.3f; pressureOpacity = 0.5f; jitterAngle = 1f; scatter = 0.1f; smoothing = 0.2f }
                BrushType.FILL -> { size = 2f; opacity = 1f; pressureSize = 0f; smoothing = 0.4f }
            }
        }
    }
}
