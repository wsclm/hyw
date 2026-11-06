package com.cpsfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class FloatService : Service() {

    companion object {
        const val ACTION_SYNC = "com.cpsfloat.SYNC"
        const val ACTION_PICK = "com.cpsfloat.PICK"
        const val ACTION_STOP = "com.cpsfloat.STOP"
        const val EXTRA_SLOT = "slot"
        private const val CHANNEL = "cps_overlay"

        fun sync(c: Context) {
            val i = Intent(c, FloatService::class.java).setAction(ACTION_SYNC)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun pick(c: Context, slot: Int) {
            val i = Intent(c, FloatService::class.java).setAction(ACTION_PICK).putExtra(EXTRA_SLOT, slot)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
    }

    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())
    private val views = arrayOfNulls<SlotView>(SLOTS)
    private var picker: RegionPicker? = null
    private var touch: TouchSource? = null
    private var warnedShizuku = false

    private val refreshRun = Runnable { refresh() }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        h.removeCallbacks(refreshRun); h.postDelayed(refreshRun, 30)
    }
    private val binderListener = Shizuku.OnBinderReceivedListener { h.post { warnedShizuku = false; refresh() } }
    private val ticker = object : Runnable {
        override fun run() {
            views.forEach { it?.tick() }
            h.postDelayed(this, 100)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startFg()
        Cfg.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        try { Shizuku.addBinderReceivedListenerSticky(binderListener) } catch (_: Throwable) { }
        h.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                for (i in 0 until SLOTS) Cfg.putBool(this, i, "enabled", false)
                closePicker()
                refresh()
            }
            ACTION_PICK -> {
                closePicker()
                val slot = intent.getIntExtra(EXTRA_SLOT, 0).coerceIn(0, SLOTS - 1)
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                } else {
                    picker = RegionPicker(this, slot) { picker = null; refresh() }.also { it.show() }
                }
                refresh()
            }
            else -> refresh()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        Cfg.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        try { Shizuku.removeBinderReceivedListener(binderListener) } catch (_: Throwable) { }
        touch?.stop()
        closePicker()
        views.forEach { it?.remove() }
        super.onDestroy()
    }

    private fun closePicker() { picker?.dismiss(); picker = null }

    private fun refresh() {
        val cfgs = (0 until SLOTS).map { Cfg.load(this, it) }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf(); return
        }
        for (i in 0 until SLOTS) {
            if (cfgs[i].enabled) {
                val v = views[i] ?: SlotView(this, wm, i).also { views[i] = it }
                v.apply(cfgs[i])
            } else {
                views[i]?.remove(); views[i] = null
            }
        }
        val needShizuku = cfgs.any { it.enabled && it.mode != Modes.COVER }
        if (needShizuku) {
            if (Sh.ready()) {
                if (touch == null) touch = TouchSource(this) { x, y -> h.post { onRawDown(x, y) } }
                touch?.start()
            } else {
                touch?.stop(); touch = null
                if (!warnedShizuku) {
                    warnedShizuku = true
                    Toast.makeText(this, "全屏/区域统计需要先启动并授权 Shizuku", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            touch?.stop(); touch = null
        }
        if (cfgs.none { it.enabled } && picker == null) stopSelf()
    }

    private fun onRawDown(x: Int, y: Int) {
        // 点在任意 CPS 显示窗口上（拖动它）不计入点击
        if (views.any { it?.inDisplay(x, y) == true }) return
        views.forEach { it?.onRawDown(x, y) }
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "CPS 悬浮窗", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, FloatService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("CPS 悬浮窗运行中")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }
}

/** 一个 CPS 显示窗口（可拖动）+ 可选的区域覆盖层。 */
class SlotView(private val ctx: Context, private val wm: WindowManager, private val idx: Int) {
    private var cfg: SlotCfg = Cfg.load(ctx, idx)
    private val times = ArrayList<Long>()

    private var tv: TextView? = null
    private var tvLp: WindowManager.LayoutParams? = null
    private var cover: View? = null
    private var coverLp: WindowManager.LayoutParams? = null
    private var lastText: String? = null
    private var lastFont: String? = null
    private var lastBold: Boolean? = null

    private val screen: IntArray
        get() {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            return intArrayOf(dm.widthPixels, dm.heightPixels)
        }

    private fun baseFlags(touchable: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun newLp(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        baseFlags(touchable), PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    fun apply(c: SlotCfg) {
        cfg = c
        applyCover()
        applyDisplay()
    }

    // ---------- 区域覆盖层（模式 2） ----------
    private fun applyCover() {
        val want = cfg.mode == Modes.COVER && cfg.hasRegion
        if (!want) {
            cover?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
            cover = null; coverLp = null
            return
        }
        var created = false
        if (cover == null) {
            val v = View(ctx)
            v.setBackgroundColor(0x14000000)
            v.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) click()
                true
            }
            val l = newLp(cfg.rw, cfg.rh, true)
            l.x = cfg.rx; l.y = cfg.ry
            try { wm.addView(v, l); cover = v; coverLp = l; created = true } catch (_: Throwable) { }
        } else {
            coverLp?.let { l ->
                l.x = cfg.rx; l.y = cfg.ry; l.width = cfg.rw; l.height = cfg.rh
                try { wm.updateViewLayout(cover, l) } catch (_: Throwable) { }
            }
        }
        // 覆盖层新建后会盖在显示窗口上面，重新添加显示窗口让它回到最上层
        if (created) {
            tv?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
            tv = null; tvLp = null
        }
    }

    // ---------- CPS 显示窗口 ----------
    private fun applyDisplay() {
        if (tv == null) {
            val t = TextView(ctx).apply { gravity = Gravity.CENTER }
            val l = newLp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, !cfg.locked)
            l.x = cfg.x; l.y = cfg.y
            attachDrag(t, l)
            try { wm.addView(t, l); tv = t; tvLp = l } catch (_: Throwable) { return }
            lastText = null; lastFont = null; lastBold = null
        }
        val t = tv ?: return
        val l = tvLp ?: return

        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, cfg.sizeSp.toFloat())
        t.setTextColor(cfg.textColor)
        t.setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
        t.background = GradientDrawable().apply {
            setColor(cfg.bgColor); cornerRadius = ctx.dp(cfg.radiusDp).toFloat()
        }
        if (cfg.font != lastFont || cfg.bold != lastBold) {
            t.typeface = Fonts.typeface(cfg.font, cfg.bold)
            lastFont = cfg.font; lastBold = cfg.bold
        }
        t.minWidth = ctx.dp(24)

        val touchable = !cfg.locked
        val has = (l.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0
        if (touchable != has) {
            l.flags = if (touchable) l.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else l.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        l.x = cfg.x; l.y = cfg.y
        lastText = null
        updateText()
        try { wm.updateViewLayout(t, l) } catch (_: Throwable) { }
    }

    private fun attachDrag(t: TextView, l: WindowManager.LayoutParams) {
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
        t.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = l.x; sy = l.y }
                MotionEvent.ACTION_MOVE -> {
                    val s = screen
                    l.x = (sx + (e.rawX - dx).toInt()).coerceIn(0, (s[0] - t.width).coerceAtLeast(0))
                    l.y = (sy + (e.rawY - dy).toInt()).coerceIn(0, (s[1] - t.height).coerceAtLeast(0))
                    try { wm.updateViewLayout(t, l) } catch (_: Throwable) { }
                }
                MotionEvent.ACTION_UP -> {
                    Cfg.putInt(ctx, idx, "x", l.x); Cfg.putInt(ctx, idx, "y", l.y)
                }
            }
            true
        }
    }

    // ---------- 计数 ----------
    fun inDisplay(x: Int, y: Int): Boolean {
        val t = tv ?: return false
        val l = tvLp ?: return false
        return x >= l.x && x <= l.x + t.width && y >= l.y && y <= l.y + t.height
    }

    fun onRawDown(x: Int, y: Int) {
        when (cfg.mode) {
            Modes.FULL -> click()
            Modes.REGION -> if (cfg.hasRegion &&
                x >= cfg.rx && x < cfg.rx + cfg.rw && y >= cfg.ry && y < cfg.ry + cfg.rh) click()
        }
    }

    private fun click() {
        times.add(SystemClock.uptimeMillis())
        updateText()
    }

    fun tick() = updateText()

    private fun updateText() {
        val t = tv ?: return
        val now = SystemClock.uptimeMillis()
        while (times.isNotEmpty() && now - times[0] > 1000) times.removeAt(0)
        val n = times.size
        val s = when {
            cfg.hideText -> ""
            cfg.idleBgOnly && n == 0 -> ""
            else -> cfg.template.replace("{cps}", n.toString())
        }
        if (s != lastText) {
            lastText = s
            t.text = s
        }
    }

    fun remove() {
        tv?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        cover?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        tv = null; cover = null; tvLp = null; coverLp = null
    }
}
