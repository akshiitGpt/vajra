package com.akshit.folio.model

interface Command {
    fun apply()
    fun revert()
}

/** Removes [removed] strokes from [layer] and inserts [added] in their place (eraser, transform, recolor, add). */
class ReplaceStrokes(private val layer: Layer, removed: List<Stroke>, private val added: List<Stroke>) : Command {
    private val removedWithIndex: List<Pair<Int, Stroke>> =
        removed.map { s -> layer.strokes.indexOfFirst { it === s } to s }.filter { it.first >= 0 }.sortedBy { it.first }

    override fun apply() {
        val insertAt = removedWithIndex.firstOrNull()?.first ?: layer.strokes.size
        for ((_, s) in removedWithIndex.asReversed()) {
            val i = layer.strokes.indexOfFirst { it === s }
            if (i >= 0) layer.strokes.removeAt(i)
        }
        layer.strokes.addAll(insertAt.coerceAtMost(layer.strokes.size), added)
    }

    override fun revert() {
        for (s in added) {
            val i = layer.strokes.indexOfFirst { it === s }
            if (i >= 0) layer.strokes.removeAt(i)
        }
        for ((idx, s) in removedWithIndex) layer.strokes.add(idx.coerceAtMost(layer.strokes.size), s)
    }
}

class Composite(private val parts: List<Command>) : Command {
    override fun apply() { parts.forEach { it.apply() } }
    override fun revert() { parts.asReversed().forEach { it.revert() } }
}

class LambdaCommand(private val doIt: () -> Unit, private val undoIt: () -> Unit) : Command {
    override fun apply() = doIt()
    override fun revert() = undoIt()
}

class History(private val limit: Int = 1000) {
    private val undo = ArrayDeque<Command>()
    private val redo = ArrayDeque<Command>()
    var onChange: (() -> Unit)? = null

    /** Executes and records. */
    fun run(c: Command) {
        c.apply()
        push(c)
    }

    /** Records a command whose effect has already been applied. */
    fun push(c: Command) {
        undo.addLast(c)
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
        onChange?.invoke()
    }

    fun canUndo() = undo.isNotEmpty()
    fun canRedo() = redo.isNotEmpty()
    val undoCount get() = undo.size

    fun undo(): Boolean {
        val c = undo.removeLastOrNull() ?: return false
        c.revert(); redo.addLast(c); onChange?.invoke(); return true
    }

    fun redo(): Boolean {
        val c = redo.removeLastOrNull() ?: return false
        c.apply(); undo.addLast(c); onChange?.invoke(); return true
    }

    /** Forgets the most recent [n] commands without reverting them (their effects must already be undone by the caller). */
    fun dropLast(n: Int) { repeat(n) { undo.removeLastOrNull() }; onChange?.invoke() }

    fun clear() { undo.clear(); redo.clear(); onChange?.invoke() }
}

/** Swaps a layer's whole stroke list between two snapshots (eraser, transforms). */
class SnapshotCommand(private val layer: Layer, before: List<Stroke>, after: List<Stroke>) : Command {
    private val before = ArrayList(before)
    private val after = ArrayList(after)
    override fun apply() { layer.strokes.clear(); layer.strokes.addAll(after) }
    override fun revert() { layer.strokes.clear(); layer.strokes.addAll(before) }
}
