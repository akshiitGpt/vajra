package com.akshit.folio.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

/** Minimal programmatic UI kit (no XML, no AppCompat) so the app stays tiny and fast. */
object UI {
    // Light, quiet chrome so the artwork is the brightest thing on screen (Concepts-style)
    const val BG = 0xFFF1F1F3.toInt()
    const val PANEL = 0xFAFFFFFF.toInt()
    const val PANEL_SOLID = 0xFFFFFFFF.toInt()
    const val PANEL2 = 0xFFEEEEF1.toInt()
    const val TEXT = 0xFF222226.toInt()
    const val SUB = 0xFF808289.toInt()
    const val ACCENT = 0xFF2F7CF6.toInt()
    const val DIV = 0x1A000000
    const val DANGER = 0xFFE5534B.toInt()
    const val ICON = 0xFF3A3B40.toInt()

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).roundToInt()
    fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

    fun ripple(ctx: Context, base: GradientDrawable?): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(0x22000000), base, base ?: rounded(Color.WHITE, dpf(ctx, 10f)))

    fun card(ctx: Context): GradientDrawable = rounded(PANEL, dpf(ctx, 16f), dp(ctx, 1f), 0x12000000)

    fun label(ctx: Context, text: String, sizeSp: Float = 14f, color: Int = TEXT, bold: Boolean = false): TextView = TextView(ctx).apply {
        this.text = text; textSize = sizeSp; setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun section(ctx: Context, title: String): TextView = label(ctx, title.uppercase(), 11.5f, SUB, true).apply {
        letterSpacing = 0.08f
        setPadding(dp(ctx, 4f), dp(ctx, 16f), 0, dp(ctx, 6f))
    }

    fun vbox(ctx: Context, padDp: Float = 0f): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(ctx, padDp); setPadding(p, p, p, p)
    }

    fun hbox(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }

    fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun button(ctx: Context, text: String, primary: Boolean = false, onClick: () -> Unit): TextView = label(ctx, text, 14f, if (primary) Color.WHITE else TEXT, true).apply {
        gravity = Gravity.CENTER
        val bg = rounded(if (primary) ACCENT else PANEL2, dpf(ctx, 10f))
        background = ripple(ctx, bg)
        setPadding(dp(ctx, 14f), dp(ctx, 10f), dp(ctx, 14f), dp(ctx, 10f))
        setOnClickListener { onClick() }
    }

    /** A slider row: label, live value, and a SeekBar mapped to [min,max]. */
    fun slider(
        ctx: Context, title: String, min: Float, max: Float, value: Float,
        fmt: (Float) -> String = { "%.2f".format(it) }, onChange: (Float) -> Unit
    ): LinearLayout {
        val row = vbox(ctx)
        row.setPadding(dp(ctx, 4f), dp(ctx, 6f), dp(ctx, 4f), dp(ctx, 2f))
        val head = hbox(ctx)
        val t = label(ctx, title, 13.5f)
        val v = label(ctx, fmt(value), 13f, SUB)
        head.addView(t, lp(0, WRAP, 1f)); head.addView(v)
        val sb = SeekBar(ctx)
        sb.max = 1000
        sb.progress = (((value - min) / (max - min)) * 1000).roundToInt().coerceIn(0, 1000)
        sb.progressTintList = ColorStateList.valueOf(ACCENT)
        sb.thumbTintList = ColorStateList.valueOf(ACCENT)
        sb.progressBackgroundTintList = ColorStateList.valueOf(0x33000000)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val f = min + (max - min) * p / 1000f
                v.text = fmt(f); onChange(f)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(head)
        row.addView(sb, lp(MATCH, dp(ctx, 34f)))
        return row
    }

    fun toggle(ctx: Context, title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = hbox(ctx)
        row.setPadding(dp(ctx, 4f), dp(ctx, 10f), dp(ctx, 4f), dp(ctx, 10f))
        val texts = vbox(ctx)
        texts.addView(label(ctx, title, 14f))
        if (desc != null) texts.addView(label(ctx, desc, 12f, SUB).apply { setPadding(0, dp(ctx, 2f), dp(ctx, 8f), 0) })
        val sw = Switch(ctx)
        sw.isChecked = checked
        sw.thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ACCENT, 0xFFBBBBBB.toInt()))
        sw.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0x882F7CF6.toInt(), 0x33000000))
        sw.setOnCheckedChangeListener { _, b -> onChange(b) }
        row.addView(texts, lp(0, WRAP, 1f))
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        return row
    }

    /** Row that shows the current choice and opens a picker dialog. */
    fun choice(ctx: Context, title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val row = hbox(ctx)
        row.setPadding(dp(ctx, 4f), dp(ctx, 12f), dp(ctx, 4f), dp(ctx, 12f))
        val t = label(ctx, title, 14f).apply { maxLines = 2 }
        val v = label(ctx, options.getOrElse(selected) { "" }, 13f, ACCENT, true).apply {
            gravity = Gravity.END; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        row.addView(t, lp(0, WRAP, 1.2f)); row.addView(v, lp(0, WRAP, 1f))
        var cur = selected
        row.background = ripple(ctx, rounded(Color.TRANSPARENT, dpf(ctx, 8f)))
        row.setOnClickListener {
            AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(options.toTypedArray(), cur) { d, which ->
                    cur = which; v.text = options[which]; onSelect(which); d.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        return row
    }

    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(DIV)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(ctx, 1f)).apply { topMargin = dp(ctx, 4f); bottomMargin = dp(ctx, 4f) }
    }

    fun scroll(ctx: Context, content: View): ScrollView = ScrollView(ctx).apply {
        isVerticalScrollBarEnabled = false
        addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
    }
}

/** Square icon button with selected / activated look. */
@SuppressLint("ViewConstructor")
class IconButton(ctx: Context, icon: String, sizeDp: Float = 44f, private val tint: Int = UI.ICON) : View(ctx) {
    private val d = IconDrawable(icon, tint)
    private val size = UI.dp(ctx, sizeDp)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var dotColor: Int? = null
        set(v) { field = v; invalidate() }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        isClickable = true
        background = UI.ripple(ctx, UI.rounded(Color.TRANSPARENT, UI.dpf(ctx, 10f)))
        contentDescription = icon
    }

    var enabledLook = true
        set(v) { field = v; d.color = if (v) tint else 0x40000000; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(size, size)

    override fun setSelected(selected: Boolean) { super.setSelected(selected); invalidate() }

    override fun onDraw(c: Canvas) {
        val r = UI.dpf(context, 10f)
        if (isSelected) {
            selPaint.color = 0x1F2F7CF6
            c.drawRoundRect(UI.dpf(context, 3f), UI.dpf(context, 3f), width - UI.dpf(context, 3f), height - UI.dpf(context, 3f), r, r, selPaint)
        }
        val pad = (width * 0.24f).toInt()
        d.color = if (isSelected) UI.ACCENT else if (enabledLook) tint else 0x40000000
        d.setBounds(pad, pad, width - pad, height - pad)
        d.draw(c)
        dotColor?.let {
            dotPaint.color = it
            val rr = UI.dpf(context, 3.6f)
            val cx = width - UI.dpf(context, 8f); val cy = height - UI.dpf(context, 8f)
            c.drawCircle(cx, cy, rr, dotPaint)
            ring.color = 0x33000000; ring.strokeWidth = UI.dpf(context, 1f)
            c.drawCircle(cx, cy, rr, ring)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = super.onTouchEvent(event)
}
