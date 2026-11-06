package com.cpsfloat

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*

object Pal {
    const val BG = 0xFFF4F5FA.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val ACCENT = 0xFF5B6CFF.toInt()
    const val ACCENT_SOFT = 0xFFE8EBFF.toInt()
    const val TEXT = 0xFF1D2030.toInt()
    const val SUB = 0xFF8A90A6.toInt()
    const val FIELD = 0xFFF0F2FA.toInt()
    const val OFF = 0xFFCDD1E3.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(c: Context, color: Int, radiusDp: Int): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = c.dp(radiusDp).toFloat() }

fun lp(w: Int, h: Int, top: Int = 0, ctx: Context? = null): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(w, h).apply { if (ctx != null) topMargin = ctx.dp(top) }

object Ui {
    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(c, Pal.CARD, 20)
        setPadding(c.dp(18), c.dp(16), c.dp(18), c.dp(16))
        elevation = c.dp(1).toFloat()
    }

    fun title(c: Context, t: String) = TextView(c).apply {
        text = t; setTextColor(Pal.TEXT); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
    }

    fun sub(c: Context, t: String) = TextView(c).apply {
        text = t; setTextColor(Pal.SUB); textSize = 12.5f
    }

    fun label(c: Context, t: String) = TextView(c).apply {
        text = t; setTextColor(Pal.TEXT); textSize = 14.5f
    }

    fun divider(c: Context) = View(c).apply {
        setBackgroundColor(0xFFEEF0F7.toInt())
        layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, 1, 12, c).apply { bottomMargin = c.dp(12) }
    }

    fun button(c: Context, t: String, filled: Boolean = true, onClick: () -> Unit) = TextView(c).apply {
        text = t; textSize = 14.5f; gravity = Gravity.CENTER
        setTextColor(if (filled) Color.WHITE else Pal.ACCENT)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(c, if (filled) Pal.ACCENT else Pal.ACCENT_SOFT, 14)
        setPadding(c.dp(16), c.dp(11), c.dp(16), c.dp(11))
        setOnClickListener { onClick() }
    }

    fun tintSwitch(s: Switch) {
        val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        s.trackTintList = ColorStateList(st, intArrayOf(Pal.ACCENT, Pal.OFF))
        s.thumbTintList = ColorStateList(st, intArrayOf(Color.WHITE, Color.WHITE))
    }

    fun switchRow(c: Context, t: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(c, t))
        if (desc != null) col.addView(sub(c, desc))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(c).apply { isChecked = checked; tintSwitch(this) }
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        return row
    }

    /** 分段选择器，返回的 View 的 tag 里存放 select(i) 函数。 */
    fun segmented(c: Context, labels: List<String>, sel: Int, onSel: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(c, Pal.FIELD, 14)
            setPadding(c.dp(4), c.dp(4), c.dp(4), c.dp(4))
        }
        val items = labels.mapIndexed { i, t ->
            TextView(c).apply {
                text = t; gravity = Gravity.CENTER; textSize = 13.5f
                setPadding(c.dp(6), c.dp(9), c.dp(6), c.dp(9))
            }
        }
        fun paint(s: Int) {
            items.forEachIndexed { i, tv ->
                if (i == s) {
                    tv.background = rounded(c, Pal.CARD, 11); tv.setTextColor(Pal.ACCENT); tv.typeface = Typeface.DEFAULT_BOLD
                } else {
                    tv.background = null; tv.setTextColor(Pal.SUB); tv.typeface = Typeface.DEFAULT
                }
            }
        }
        items.forEachIndexed { i, tv ->
            tv.setOnClickListener { paint(i); onSel(i) }
            box.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        paint(sel)
        return box
    }

    /** 拖动条 + 旁边的数值（点击即可直接输入数字）。 */
    fun sliderRow(
        c: Context, name: String, unit: String,
        min: Int, max: Int, hardMax: Int, init: Int, onChange: (Int) -> Unit
    ): View {
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(label(c, name))
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        var cur = init.coerceIn(min, hardMax)
        var internal = false

        val bar = SeekBar(c).apply {
            this.max = max - min
            progress = cur.coerceIn(min, max) - min
            progressTintList = ColorStateList.valueOf(Pal.ACCENT)
            thumbTintList = ColorStateList.valueOf(Pal.ACCENT)
        }
        val et = EditText(c).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(cur.toString())
            setTextColor(Pal.TEXT); textSize = 15f; gravity = Gravity.CENTER
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = rounded(c, Pal.FIELD, 10)
            setPadding(c.dp(6), c.dp(7), c.dp(6), c.dp(7))
        }
        val u = TextView(c).apply { text = unit; setTextColor(Pal.SUB); textSize = 13f }

        fun commit(v: Int) {
            if (v == cur) return
            cur = v; onChange(v)
        }
        fun fixText() {
            val v = (et.text.toString().toIntOrNull() ?: cur).coerceIn(min, hardMax)
            internal = true
            et.setText(v.toString()); bar.progress = v.coerceIn(min, max) - min
            internal = false
            commit(v)
        }

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = p + min
                internal = true; et.setText(v.toString()); internal = false
                commit(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (internal) return
                val v = s.toString().toIntOrNull() ?: return
                if (v < min || v > hardMax) return
                internal = true; bar.progress = v.coerceIn(min, max) - min; internal = false
                commit(v)
            }
        })
        et.setOnEditorActionListener { v, a, _ ->
            if (a == EditorInfo.IME_ACTION_DONE) {
                fixText(); v.clearFocus()
                (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }
        et.setOnFocusChangeListener { _, f -> if (!f) fixText() }

        row.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(et, LinearLayout.LayoutParams(c.dp(62), ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = c.dp(8) })
        row.addView(u, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = c.dp(4) })
        root.addView(row)
        return root
    }

    /** 颜色行：点击弹出调色板，确定后才应用。 */
    fun colorRow(c: Context, t: String, color: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, c.dp(4), 0, c.dp(4))
        }
        row.addView(label(c, t), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwatchView(c).also { it.color = color }
        row.addView(sw, LinearLayout.LayoutParams(c.dp(44), c.dp(30)))
        row.setOnClickListener {
            ColorDialog.show(c, t, sw.color) { picked -> sw.color = picked; onPick(picked) }
        }
        return row
    }

    fun navRow(c: Context, t: String, value: String, onClick: () -> Unit): View {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, c.dp(8), 0, c.dp(8))
        }
        row.addView(label(c, t), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(c).apply { text = "$value  ›"; setTextColor(Pal.ACCENT); textSize = 14f })
        row.setOnClickListener { onClick() }
        return row
    }
}

fun drawChecker(canvas: Canvas, w: Float, h: Float, cell: Float) {
    val p = Paint()
    var y = 0f; var r = 0
    while (y < h) {
        var x = 0f; var cI = r
        while (x < w) {
            p.color = if (cI % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFD9DCE8.toInt()
            canvas.drawRect(x, y, minOf(x + cell, w), minOf(y + cell, h), p)
            x += cell; cI++
        }
        y += cell; r++
    }
}

class SwatchView(c: Context) : View(c) {
    var color: Int = Color.WHITE
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = c.dp(1).toFloat(); this.color = 0x33000000
    }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        val r = context.dp(8).toFloat()
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        path.reset(); path.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save(); canvas.clipPath(path)
        drawChecker(canvas, width.toFloat(), height.toFloat(), context.dp(6).toFloat())
        paint.color = color
        canvas.drawRect(rect, paint)
        canvas.restore()
        rect.inset(0.5f, 0.5f)
        canvas.drawRoundRect(rect, r, r, border)
    }
}

/** 饱和度/明度方块 */
private class SvView(c: Context, val st: ColorState) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val rr = context.dp(12).toFloat()
        path.reset(); path.addRoundRect(RectF(0f, 0f, w, h), rr, rr, Path.Direction.CW)
        canvas.save(); canvas.clipPath(path)
        val pure = Color.HSVToColor(floatArrayOf(st.hsv[0], 1f, 1f))
        val sat = LinearGradient(0f, 0f, w, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        val value = LinearGradient(0f, 0f, 0f, h, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
        paint.shader = ComposeShader(sat, value, PorterDuff.Mode.MULTIPLY)
        canvas.drawRect(0f, 0f, w, h, paint)
        canvas.restore()
        val cx = st.hsv[1] * w; val cy = (1f - st.hsv[2]) * h
        ring.strokeWidth = context.dp(3).toFloat(); ring.color = Color.WHITE
        canvas.drawCircle(cx, cy, context.dp(10).toFloat(), ring)
        ring.strokeWidth = context.dp(1).toFloat(); ring.color = 0x66000000
        canvas.drawCircle(cx, cy, context.dp(11).toFloat(), ring)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        st.hsv[1] = (e.x / width).coerceIn(0f, 1f)
        st.hsv[2] = 1f - (e.y / height).coerceIn(0f, 1f)
        st.changed(this)
        return true
    }
}

/** kind 0 = 色相条，1 = 透明度条 */
private class BarView(c: Context, val st: ColorState, val kind: Int) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val rr = h / 2f
        path.reset(); path.addRoundRect(RectF(0f, 0f, w, h), rr, rr, Path.Direction.CW)
        canvas.save(); canvas.clipPath(path)
        if (kind == 0) {
            val cols = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
            paint.shader = LinearGradient(0f, 0f, w, 0f, cols, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
        } else {
            drawChecker(canvas, w, h, context.dp(6).toFloat())
            val rgb = Color.HSVToColor(st.hsv) and 0x00FFFFFF
            paint.shader = LinearGradient(0f, 0f, w, 0f, rgb, rgb or (0xFF shl 24), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        paint.shader = null
        canvas.restore()
        val frac = if (kind == 0) st.hsv[0] / 360f else st.alpha / 255f
        val cx = (rr + frac * (w - 2 * rr))
        ring.strokeWidth = context.dp(3).toFloat(); ring.color = Color.WHITE
        canvas.drawCircle(cx, h / 2f, rr - context.dp(2), ring)
        ring.strokeWidth = context.dp(1).toFloat(); ring.color = 0x66000000
        canvas.drawCircle(cx, h / 2f, rr, ring)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val rr = height / 2f
        val f = ((e.x - rr) / (width - 2 * rr)).coerceIn(0f, 1f)
        if (kind == 0) st.hsv[0] = f * 360f else st.alpha = Math.round(f * 255f)
        st.changed(this)
        return true
    }
}

private class ColorState {
    val hsv = FloatArray(3)
    var alpha = 255
    var listener: ((View?) -> Unit)? = null
    fun set(color: Int) { Color.colorToHSV(color, hsv); alpha = Color.alpha(color) }
    fun color(): Int = Color.HSVToColor(alpha, hsv)
    fun changed(src: View?) { listener?.invoke(src) }
}

object ColorDialog {
    fun show(c: Context, title: String, initial: Int, onOk: (Int) -> Unit) {
        val st = ColorState().also { it.set(initial) }
        val dlg = Dialog(c)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(c.dp(20), c.dp(18), c.dp(20), c.dp(14))
        }
        root.addView(Ui.title(c, "调色板 · $title"))

        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val preview = SwatchView(c)
        top.addView(preview, LinearLayout.LayoutParams(c.dp(56), c.dp(40)))
        val hex = EditText(c).apply {
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setTextColor(Pal.TEXT); textSize = 15f
            background = rounded(c, Pal.FIELD, 10)
            setPadding(c.dp(12), c.dp(9), c.dp(12), c.dp(9))
            typeface = Typeface.MONOSPACE
        }
        top.addView(hex, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = c.dp(12) })
        root.addView(top, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 14, c))

        val sv = SvView(c, st)
        val hue = BarView(c, st, 0)
        val alpha = BarView(c, st, 1)
        root.addView(sv, lp(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(190), 14, c))
        root.addView(hue, lp(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(26), 14, c))
        root.addView(alpha, lp(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(26), 12, c))

        var internal = false
        fun hexOf(col: Int) = String.format("#%08X", col)
        fun refresh(src: View?) {
            preview.color = st.color()
            sv.invalidate(); hue.invalidate(); alpha.invalidate()
            if (src !== hex) { internal = true; hex.setText(hexOf(st.color())); internal = false }
        }
        st.listener = { refresh(it) }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (internal) return
                val t = s.toString().trim().removePrefix("#")
                if (t.length != 6 && t.length != 8) return
                try {
                    st.set(Color.parseColor("#$t")); refresh(hex)
                } catch (e: IllegalArgumentException) { }
            }
        })
        refresh(null)

        val btns = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        btns.addView(Ui.button(c, "取消", false) { dlg.dismiss() })
        btns.addView(Ui.button(c, "确定") { onOk(st.color()); dlg.dismiss() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = c.dp(10) })
        root.addView(btns, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 16, c))

        dlg.setContentView(root)
        dlg.window?.apply {
            setBackgroundDrawable(rounded(c, Pal.CARD, 24))
            setLayout((c.resources.displayMetrics.widthPixels * 0.9f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dlg.show()
    }
}
