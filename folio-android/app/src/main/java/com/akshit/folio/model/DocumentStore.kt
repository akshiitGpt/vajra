package com.akshit.folio.model

import android.content.Context
import android.graphics.Bitmap
import com.akshit.folio.brush.BrushSpec
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/** Binary on-disk format. Compact, versioned and fast to load. */
object DocumentIO {
    private const val MAGIC = 0x464F4C49 // "FOLI"
    private const val VERSION = 2

    fun write(doc: Document, out: DataOutputStream) {
        out.writeInt(MAGIC); out.writeInt(VERSION)
        out.writeUTF(doc.id); out.writeUTF(doc.name)
        out.writeLong(doc.created); out.writeLong(doc.modified)
        out.writeDouble(doc.camCx); out.writeDouble(doc.camCy); out.writeDouble(doc.camScale); out.writeDouble(doc.camRot)
        val b = doc.background
        out.writeInt(b.pattern.ordinal); out.writeFloat(b.spacing); out.writeInt(b.paperColor); out.writeInt(b.lineColor)
        out.writeFloat(b.lineAlpha); out.writeInt(b.majorEvery); out.writeBoolean(b.adaptive); out.writeFloat(b.lineWidth)
        out.writeFloat(b.vpDistance); out.writeFloat(b.horizon)
        out.writeBoolean(b.texture)
        out.writeInt(doc.activeLayer)
        out.writeInt(doc.layers.size)
        for (l in doc.layers) {
            out.writeUTF(l.name); out.writeBoolean(l.visible); out.writeBoolean(l.locked); out.writeFloat(l.opacity)
            out.writeInt(l.strokes.size)
            for (s in l.strokes) {
                s.brush.write(out)
                out.writeInt(s.color); out.writeFloat(s.width); out.writeDouble(s.ox); out.writeDouble(s.oy)
                out.writeInt(s.seed)
                out.writeInt(s.n)
                val c = s.n * Stroke.STRIDE
                for (i in 0 until c) out.writeFloat(s.pts[i])
            }
        }
    }

    fun read(inp: DataInputStream): Document {
        require(inp.readInt() == MAGIC) { "Not a Folio file" }
        val ver = inp.readInt()
        require(ver <= VERSION) { "Newer file version $ver" }
        val doc = Document(inp.readUTF(), inp.readUTF())
        doc.created = inp.readLong(); doc.modified = inp.readLong()
        doc.camCx = inp.readDouble(); doc.camCy = inp.readDouble(); doc.camScale = inp.readDouble(); doc.camRot = inp.readDouble()
        val b = doc.background
        b.pattern = Pattern.entries.getOrElse(inp.readInt()) { Pattern.DOTS }
        b.spacing = inp.readFloat(); b.paperColor = inp.readInt(); b.lineColor = inp.readInt()
        b.lineAlpha = inp.readFloat(); b.majorEvery = inp.readInt(); b.adaptive = inp.readBoolean(); b.lineWidth = inp.readFloat()
        b.vpDistance = inp.readFloat(); b.horizon = inp.readFloat()
        b.texture = if (ver >= 2) inp.readBoolean() else false
        doc.activeLayer = inp.readInt()
        doc.layers.clear()
        val nl = inp.readInt()
        repeat(nl) {
            val l = Layer(inp.readUTF())
            l.visible = inp.readBoolean(); l.locked = inp.readBoolean(); l.opacity = inp.readFloat()
            val ns = inp.readInt()
            l.strokes.ensureCapacity(ns)
            repeat(ns) {
                val brush = BrushSpec.read(inp)
                val s = Stroke(brush, inp.readInt(), inp.readFloat(), inp.readDouble(), inp.readDouble())
                s.seed = inp.readInt()
                val n = inp.readInt()
                s.pts = FloatArray(maxOf(n, 2) * Stroke.STRIDE)
                for (i in 0 until n * Stroke.STRIDE) s.pts[i] = inp.readFloat()
                s.n = n
                l.strokes.add(s)
            }
            doc.layers.add(l)
        }
        if (doc.layers.isEmpty()) doc.layers.add(Layer("Layer 1"))
        return doc
    }
}

data class DocMeta(val id: String, val name: String, val modified: Long, val strokes: Int, val thumb: File)

class DocumentStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "drawings").apply { mkdirs() }

    fun file(id: String) = File(dir, "$id.folio")
    fun thumb(id: String) = File(dir, "$id.png")
    private fun metaFile(id: String) = File(dir, "$id.meta")

    fun create(name: String): Document = Document(UUID.randomUUID().toString(), name)

    fun list(): List<DocMeta> = dir.listFiles { f -> f.name.endsWith(".folio") }.orEmpty().mapNotNull { f ->
        val id = f.name.removeSuffix(".folio")
        val meta = metaFile(id)
        val parts = if (meta.exists()) meta.readText().split("\n") else emptyList()
        DocMeta(id, parts.getOrNull(0) ?: "Untitled", parts.getOrNull(1)?.toLongOrNull() ?: f.lastModified(),
            parts.getOrNull(2)?.toIntOrNull() ?: 0, thumb(id))
    }.sortedByDescending { it.modified }

    fun save(doc: Document, thumbnail: Bitmap?) {
        doc.modified = System.currentTimeMillis()
        val tmp = File(dir, "${doc.id}.tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 1 shl 16)).use { DocumentIO.write(doc, it) }
        tmp.renameTo(file(doc.id))
        metaFile(doc.id).writeText("${doc.name}\n${doc.modified}\n${doc.strokeCount()}")
        if (thumbnail != null) FileOutputStream(thumb(doc.id)).use { thumbnail.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    fun load(id: String): Document? = try {
        DataInputStream(BufferedInputStream(FileInputStream(file(id)), 1 shl 16)).use { DocumentIO.read(it) }
    } catch (e: Exception) { null }

    fun rename(id: String, name: String) {
        val d = load(id) ?: return
        d.name = name
        DataOutputStream(BufferedOutputStream(FileOutputStream(file(id)))).use { DocumentIO.write(d, it) }
        metaFile(id).writeText("${d.name}\n${d.modified}\n${d.strokeCount()}")
    }

    fun duplicate(id: String): String? {
        val d = load(id) ?: return null
        val copy = DataOutputStream(BufferedOutputStream(FileOutputStream(File(dir, "dup.tmp")))).use { out ->
            d.id = UUID.randomUUID().toString(); d.name = d.name + " copy"
            DocumentIO.write(d, out); d.id
        }
        File(dir, "dup.tmp").renameTo(file(copy))
        metaFile(copy).writeText("${d.name}\n${System.currentTimeMillis()}\n${d.strokeCount()}")
        if (thumb(id).exists()) thumb(id).copyTo(thumb(copy), true)
        return copy
    }

    fun delete(id: String) {
        file(id).delete(); thumb(id).delete(); metaFile(id).delete()
    }
}
