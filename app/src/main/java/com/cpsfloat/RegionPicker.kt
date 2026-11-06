package com.cpsfloat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 点击区域选择悬浮窗：悬浮窗本身的位置/大小就是点击区域。
 * 拖动中间移动，拖动左上/右下角缩放，点“完成”后保存到该窗口的区域设置并自动消失。
 */
class RegionPicker(
    private val ctx: Context,
    private val slot: Int,
    private val onFinish: () -> Unit
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val params: WindowManager.LayoutParams
    private val root: FrameLayout
    private val info: TextView
    private var screenW = 0
    private var screenH = 0
    private val minSize = ctx.dp(80)

    private class Handle(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            p.color = Color.WHITE; canvas.drawCircle(r, r, r, p)
            p.color = Pal.ACCENT; canvas.drawCircle(r, r, r - context.dp(4), p)
        }
    }

    init {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        screenW = dm.widthPixels; screenH = dm.heightPixels

        val cfg = Cfg.load(ctx, slot)
        val w0 = if (cfg.hasRegion) cfg.rw else (screenW * 0.5f).toInt()
        val h0 = if (cfg.hasRegion) cfg.rh else (screenH * 0.3f).toInt()
        val x0 = if (cfg.hasRegion) cfg.rx else (screenW - w0) / 2
        val y0 = if (cfg.hasRegion) cfg.ry else (screenH - h0) / 2

        params = WindowManager.LayoutParams(
            w0, h0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = x0; y = y0
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        root = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                setColor(0x445B6CFF)
                setStroke(ctx.dp(3), Pal.ACCENT)
                cornerRadius = ctx.dp(10).toFloat()
            }
        }

        val center = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        center.addView(TextView(ctx).apply {
            text = "窗口 ${slot + 1} · 点击区域"
            setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        })
        info = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 12f
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        center.addView(info)
        center.addView(TextView(ctx).apply {
            text = "拖动移动 · 拖角点缩放"
            setTextColor(0xCCFFFFFF.toInt()); textSize = 12f
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        })
        center.addView(Ui.button(ctx, "完成") { finish() },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = ctx.dp(10) })
        root.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        val hs = ctx.dp(30)
        val br = Handle(ctx)
        val tl = Handle(ctx)
        root.addView(br, FrameLayout.LayoutParams(hs, hs, Gravity.BOTTOM or Gravity.END))
        root.addView(tl, FrameLayout.LayoutParams(hs, hs, Gravity.TOP or Gravity.START))

        attachMove(root)
        attachResize(br, topLeft = false)
        attachResize(tl, topLeft = true)
        updateInfo()
    }

    private fun updateInfo() {
        info.text = "${params.width} × ${params.height}   @ (${params.x}, ${params.y})"
    }

    private fun apply() {
        params.width = params.width.coerceIn(minSize, screenW)
        params.height = params.height.coerceIn(minSize, screenH)
        params.x = params.x.coerceIn(0, screenW - params.width)
        params.y = params.y.coerceIn(0, screenH - params.height)
        try { wm.updateViewLayout(root, params) } catch (_: Throwable) { }
        updateInfo()
    }

    private fun attachMove(v: View) {
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = params.x; sy = params.y }
                MotionEvent.ACTION_MOVE -> {
                    params.x = sx + (e.rawX - dx).toInt()
                    params.y = sy + (e.rawY - dy).toInt()
                    apply()
                }
            }
            true
        }
    }

    private fun attachResize(v: View, topLeft: Boolean) {
        var dx = 0f; var dy = 0f
        var sx = 0; var sy = 0; var sw = 0; var sh = 0
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX; dy = e.rawY
                    sx = params.x; sy = params.y; sw = params.width; sh = params.height
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - dx).toInt()
                    val my = (e.rawY - dy).toInt()
                    if (topLeft) {
                        val nw = (sw - mx).coerceAtLeast(minSize)
                        val nh = (sh - my).coerceAtLeast(minSize)
                        params.x = sx + (sw - nw); params.y = sy + (sh - nh)
                        params.width = nw; params.height = nh
                    } else {
                        params.width = (sw + mx).coerceAtLeast(minSize)
                        params.height = (sh + my).coerceAtLeast(minSize)
                    }
                    apply()
                }
            }
            true
        }
    }

    fun show() {
        try { wm.addView(root, params) } catch (_: Throwable) { onFinish() }
    }

    fun dismiss() {
        try { wm.removeView(root) } catch (_: Throwable) { }
    }

    private fun finish() {
        Cfg.putRegion(ctx, slot, params.x, params.y, params.width, params.height)
        dismiss()
        onFinish()
    }
}
