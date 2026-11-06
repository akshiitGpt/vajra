package com.akshit.folio.render

import android.graphics.Color
import android.graphics.Path
import android.graphics.PathIterator
import android.os.Build
import com.akshit.folio.brush.RenderKind
import com.akshit.folio.brush.StrokeRenderer
import com.akshit.folio.model.Document
import com.akshit.folio.model.Stroke
import java.util.Locale

/** Exports the drawing as SVG. On Android 14+ exact filled outlines are exported; older versions get centre-lines. */
object SvgExporter {
    fun export(doc: Document): String {
        val b = SceneRenderer.contentBounds(doc) ?: doubleArrayOf(0.0, 0.0, 100.0, 100.0)
        val pad = (b[2] - b[0]).coerceAtLeast(b[3] - b[1]) * 0.03
        val x0 = b[0] - pad; val y0 = b[1] - pad
        val w = b[2] - b[0] + 2 * pad; val h = b[3] - b[1] + 2 * pad
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ${f(w)} ${f(h)}\" width=\"${f(w)}\" height=\"${f(h)}\">\n")
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"${hex(doc.background.paperColor)}\"/>\n")
        for (layer in doc.layers) {
            if (!layer.visible) continue
            sb.append("<g opacity=\"${f(layer.opacity.toDouble())}\">\n")
            for (s in layer.strokes) appendStroke(sb, s, s.ox - x0, s.oy - y0)
            sb.append("</g>\n")
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun appendStroke(sb: StringBuilder, s: Stroke, dx: Double, dy: Double) {
        StrokeRenderer.build(s)
        val color = hex(s.color)
        val op = (s.brush.opacity * s.meanAlpha).toDouble()
        when (s.brush.type.kind) {
            RenderKind.STAMP -> {
                val st = s.stamps ?: return
                sb.append("<g fill=\"$color\">")
                for (i in 0 until s.stampCount) {
                    val o = i * 5
                    sb.append("<circle cx=\"${f(st[o] + dx)}\" cy=\"${f(st[o + 1] + dy)}\" r=\"${f(st[o + 2].toDouble())}\" opacity=\"${f((st[o + 3] * s.brush.opacity).toDouble())}\"/>")
                }
                sb.append("</g>\n")
            }
            else -> {
                val path = s.path
                if (path != null && Build.VERSION.SDK_INT >= 34) {
                    sb.append("<path fill=\"$color\" fill-opacity=\"${f(op)}\" d=\"").append(pathData(path, dx, dy)).append("\"/>\n")
                } else {
                    sb.append("<polyline fill=\"none\" stroke=\"$color\" stroke-opacity=\"${f(op)}\" stroke-width=\"${f(s.width.toDouble())}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" points=\"")
                    for (i in 0 until s.n) sb.append(f(s.x(i) + dx)).append(',').append(f(s.y(i) + dy)).append(' ')
                    sb.append("\"/>\n")
                }
            }
        }
    }

    private fun pathData(path: Path, dx: Double, dy: Double): String {
        if (Build.VERSION.SDK_INT < 34) return ""
        val sb = StringBuilder()
        val it = path.pathIterator
        val p = FloatArray(8)
        while (it.hasNext()) {
            when (it.next(p, 0)) {
                PathIterator.VERB_MOVE -> sb.append("M").append(f(p[0] + dx)).append(' ').append(f(p[1] + dy))
                PathIterator.VERB_LINE -> sb.append("L").append(f(p[2] + dx)).append(' ').append(f(p[3] + dy))
                PathIterator.VERB_QUAD -> sb.append("Q").append(f(p[2] + dx)).append(' ').append(f(p[3] + dy)).append(' ').append(f(p[4] + dx)).append(' ').append(f(p[5] + dy))
                PathIterator.VERB_CONIC -> sb.append("Q").append(f(p[2] + dx)).append(' ').append(f(p[3] + dy)).append(' ').append(f(p[4] + dx)).append(' ').append(f(p[5] + dy))
                PathIterator.VERB_CUBIC -> sb.append("C").append(f(p[2] + dx)).append(' ').append(f(p[3] + dy)).append(' ').append(f(p[4] + dx)).append(' ').append(f(p[5] + dy)).append(' ').append(f(p[6] + dx)).append(' ').append(f(p[7] + dy))
                PathIterator.VERB_CLOSE -> sb.append("Z")
                PathIterator.VERB_DONE -> break
            }
        }
        return sb.toString()
    }

    private fun f(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun f(v: Float) = String.format(Locale.US, "%.2f", v)
    private fun hex(c: Int) = String.format(Locale.US, "#%02X%02X%02X", Color.red(c), Color.green(c), Color.blue(c))
}
