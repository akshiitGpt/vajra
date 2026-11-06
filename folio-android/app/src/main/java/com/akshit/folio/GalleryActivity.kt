package com.akshit.folio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.akshit.folio.core.Prefs
import com.akshit.folio.model.DocMeta
import com.akshit.folio.model.DocumentStore
import com.akshit.folio.ui.BackgroundPanel
import com.akshit.folio.ui.IconButton
import com.akshit.folio.ui.UI

class GalleryActivity : Activity() {
    private lateinit var store: DocumentStore
    private lateinit var prefs: Prefs
    private lateinit var grid: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DocumentStore(this)
        prefs = Prefs(this)
        val root = UI.vbox(this)
        root.setBackgroundColor(UI.BG)
        root.fitsSystemWindows = true

        val head = UI.hbox(this)
        head.setPadding(UI.dp(this, 20f), UI.dp(this, 16f), UI.dp(this, 12f), UI.dp(this, 8f))
        val titleCol = UI.vbox(this)
        titleCol.addView(UI.label(this, "Folio", 28f, UI.TEXT, true))
        titleCol.addView(UI.label(this, "Infinite canvas sketchbook", 13f, UI.SUB))
        head.addView(titleCol, UI.lp(0, UI.WRAP, 1f))
        head.addView(IconButton(this, "penlab", 48f).apply { setOnClickListener { startActivity(Intent(this@GalleryActivity, PenLabActivity::class.java)) } })
        head.addView(IconButton(this, "settings", 48f).apply { setOnClickListener { startActivity(Intent(this@GalleryActivity, SettingsActivity::class.java)) } })
        head.addView(UI.button(this, "+  New drawing", primary = true) { newDrawing() }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = UI.dp(this@GalleryActivity, 8f)
        })
        root.addView(head)

        grid = UI.vbox(this, 12f)
        root.addView(UI.scroll(this, grid), UI.lp(UI.MATCH, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        grid.removeAllViews()
        val items = store.list()
        if (items.isEmpty()) {
            val empty = UI.vbox(this).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, UI.dp(this@GalleryActivity, 80f), 0, 0) }
            empty.addView(UI.label(this, "No drawings yet", 18f, UI.TEXT, true))
            empty.addView(UI.label(this, "Tap “New drawing” to start on an infinite canvas.", 14f, UI.SUB).apply { setPadding(0, UI.dp(this@GalleryActivity, 6f), 0, UI.dp(this@GalleryActivity, 16f)) })
            empty.addView(UI.button(this, "+  New drawing", primary = true) { newDrawing() })
            grid.addView(empty)
            return
        }
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val cols = (widthDp / 230f).toInt().coerceIn(2, 6)
        var row: LinearLayout? = null
        items.forEachIndexed { i, m ->
            if (i % cols == 0) { row = UI.hbox(this); grid.addView(row) }
            row!!.addView(card(m), UI.lp(0, UI.WRAP, 1f).apply { setMargins(UI.dp(this@GalleryActivity, 6f), UI.dp(this@GalleryActivity, 6f), UI.dp(this@GalleryActivity, 6f), UI.dp(this@GalleryActivity, 6f)) })
        }
        val rem = items.size % cols
        if (rem != 0) repeat(cols - rem) { row!!.addView(FrameLayout(this), UI.lp(0, 1, 1f).apply { setMargins(UI.dp(this@GalleryActivity, 6f), 0, UI.dp(this@GalleryActivity, 6f), 0) }) }
    }

    private fun card(m: DocMeta): LinearLayout {
        val c = UI.vbox(this)
        c.background = UI.ripple(this, UI.rounded(UI.PANEL2, UI.dpf(this, 12f)))
        c.setPadding(UI.dp(this, 8f), UI.dp(this, 8f), UI.dp(this, 8f), UI.dp(this, 10f))
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.background = UI.rounded(0xFFF4F3EF.toInt(), UI.dpf(this, 8f))
        img.clipToOutline = true
        if (m.thumb.exists()) img.setImageBitmap(BitmapFactory.decodeFile(m.thumb.absolutePath))
        c.addView(img, UI.lp(UI.MATCH, UI.dp(this, 140f)))
        c.addView(UI.label(this, m.name, 14f, UI.TEXT, true).apply { setPadding(UI.dp(this@GalleryActivity, 2f), UI.dp(this@GalleryActivity, 8f), 0, 0); maxLines = 1 })
        c.addView(UI.label(this, "${DateUtils.getRelativeTimeSpanString(m.modified)} · ${m.strokes} strokes", 11.5f, UI.SUB).apply { setPadding(UI.dp(this@GalleryActivity, 2f), UI.dp(this@GalleryActivity, 2f), 0, 0) })
        c.setOnClickListener { open(m.id) }
        c.setOnLongClickListener { menu(m); true }
        return c
    }

    private fun newDrawing() {
        val n = store.list().size + 1
        val d = store.create("Drawing $n")
        BackgroundPanel.applyDefault(prefs, d.background)
        store.save(d, null)
        open(d.id)
    }

    private fun open(id: String) = startActivity(Intent(this, CanvasActivity::class.java).putExtra("doc_id", id))

    private fun menu(m: DocMeta) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(m.name)
            .setItems(arrayOf("Open", "Rename", "Duplicate", "Delete")) { _, w ->
                when (w) {
                    0 -> open(m.id)
                    1 -> {
                        val et = EditText(this).apply { setText(m.name); setTextColor(UI.TEXT) }
                        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Rename").setView(et)
                            .setPositiveButton("OK") { _, _ -> store.rename(m.id, et.text.toString().ifBlank { m.name }); refresh() }
                            .setNegativeButton("Cancel", null).show()
                    }
                    2 -> { store.duplicate(m.id); refresh() }
                    3 -> AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Delete “${m.name}”?")
                        .setMessage("This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ -> store.delete(m.id); refresh() }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }
}
