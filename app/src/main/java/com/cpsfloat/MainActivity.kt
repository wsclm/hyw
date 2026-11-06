package com.cpsfloat

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.File

class MainActivity : Activity() {

    private var cur = 0
    private lateinit var content: LinearLayout
    private lateinit var permBox: LinearLayout
    private val REQ_FONT = 21
    private val REQ_SHIZUKU = 22

    private val shizukuResult = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { buildPerm(); anySync() }
    }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { buildPerm() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Pal.BG); isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(40))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "CPS 悬浮窗"; setTextColor(Pal.TEXT); textSize = 28f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, 0, 0)
        })
        root.addView(Ui.sub(this, "所有设置修改后即时生效").apply { setPadding(dp(4), dp(2), 0, dp(14)) })

        permBox = Ui.card(this)
        root.addView(permBox, lp(-1, -2))

        root.addView(Ui.segmented(this, listOf("窗口 1", "窗口 2"), cur) { cur = it; buildSlot() },
            lp(-1, -2, 14, this))

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, lp(-1, -2, 14, this))

        setContentView(scroll)
        buildSlot()

        try {
            Shizuku.addRequestPermissionResultListener(shizukuResult)
            Shizuku.addBinderReceivedListenerSticky(binderListener)
        } catch (_: Throwable) { }
    }

    override fun onResume() {
        super.onResume()
        buildPerm()
        if (::content.isInitialized) buildSlot()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuResult)
            Shizuku.removeBinderReceivedListener(binderListener)
        } catch (_: Throwable) { }
        super.onDestroy()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------------- 权限卡片 ----------------
    private fun statusRow(name: String, status: String, ok: Boolean, action: String?, onClick: (() -> Unit)?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.label(this, name))
        col.addView(TextView(this).apply {
            text = status; textSize = 12.5f; setTextColor(if (ok) 0xFF2EA66B.toInt() else Pal.SUB)
        })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        if (action != null && onClick != null) row.addView(Ui.button(this, action, false) { onClick() })
        return row
    }

    private fun buildPerm() {
        permBox.removeAllViews()
        val canDraw = Settings.canDrawOverlays(this)
        permBox.addView(Ui.title(this, "权限"))
        permBox.addView(Ui.divider(this))
        permBox.addView(statusRow("悬浮窗权限", if (canDraw) "已授权" else "未授权", canDraw,
            if (canDraw) null else "去授权") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        if (!canDraw && Sh.ready()) {
            permBox.addView(Ui.button(this, "用 Shizuku 授予悬浮窗权限", true) {
                Thread {
                    try {
                        Sh.run("appops set $packageName SYSTEM_ALERT_WINDOW allow")
                        runOnUiThread { buildPerm(); toast("已通过 Shizuku 授权") }
                    } catch (e: Throwable) {
                        runOnUiThread { toast("失败: ${e.message}") }
                    }
                }.start()
            }, lp(-1, -2, 8, this))
        }
        permBox.addView(Ui.divider(this))
        val alive = Sh.alive()
        val ready = Sh.ready()
        permBox.addView(statusRow(
            "Shizuku（全屏/区域统计需要）",
            when { ready -> "已授权"; alive -> "未授权"; else -> "未运行，请先启动 Shizuku 应用" },
            ready, if (alive && !ready) "申请授权" else null
        ) {
            try { Shizuku.requestPermission(REQ_SHIZUKU) } catch (e: Throwable) { toast("Shizuku 不可用: ${e.message}") }
        })
    }

    // ---------------- 窗口设置 ----------------
    private fun anySync() { if (Cfg.anyEnabled(this) && Settings.canDrawOverlays(this)) FloatService.sync(this) }

    private fun buildSlot() {
        content.removeAllViews()
        val i = cur
        val c = Cfg.load(this, i)

        // 启用 + 模式
        val a = Ui.card(this)
        a.addView(Ui.switchRow(this, "启用窗口 ${i + 1}", "开启后立即显示 CPS 悬浮窗", c.enabled) { on ->
            if (on && !Settings.canDrawOverlays(this)) {
                toast("请先授予悬浮窗权限"); buildSlot(); return@switchRow
            }
            Cfg.putBool(this, i, "enabled", on)
            if (on) anySync()
        })
        a.addView(Ui.divider(this))
        a.addView(Ui.label(this, "统计方式"))
        a.addView(Ui.segmented(this, listOf("全屏", "指定区域", "区域覆盖"), c.mode) { m ->
            Cfg.putInt(this, i, "mode", m)
            if (m != Modes.FULL && !Cfg.load(this, i).hasRegion) {
                buildSlot(); pickRegion(i)
            } else buildSlot()
            anySync()
        }, lp(-1, -2, 8, this))
        a.addView(Ui.sub(this, when (c.mode) {
            Modes.FULL -> "统计整个屏幕的点击（需要 Shizuku）"
            Modes.REGION -> "只统计所选区域内的点击，点击仍会传给下层应用（需要 Shizuku）"
            else -> "在区域上盖一层透明窗口统计，该区域点击会被拦截（无需 Shizuku）"
        }).apply { setPadding(0, dp(8), 0, 0) })
        if (c.mode != Modes.FULL) {
            a.addView(Ui.divider(this))
            val regionText = if (c.hasRegion) "${c.rw} × ${c.rh}  @ (${c.rx}, ${c.ry})" else "未设置"
            a.addView(Ui.navRow(this, "点击区域", regionText) { pickRegion(i) })
            a.addView(Ui.sub(this, "点击后会弹出悬浮窗：拖动移动、拖角点缩放，点“完成”即自动保存并关闭"))
        }
        content.addView(a, lp(-1, -2))

        // 预览
        val pv = Ui.card(this)
        pv.addView(Ui.title(this, "外观"))
        val preview = TextView(this).apply { gravity = Gravity.CENTER }
        val previewBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(this@MainActivity, 0xFF5A5F73.toInt(), 14)
            setPadding(dp(8), dp(18), dp(8), dp(18))
            addView(preview)
        }
        pv.addView(previewBox, lp(-1, -2, 10, this))
        fun refreshPreview() {
            val x = Cfg.load(this, i)
            preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, x.sizeSp.toFloat())
            preview.setTextColor(x.textColor)
            preview.typeface = Fonts.typeface(x.font, x.bold)
            preview.setPadding(dp(12), dp(6), dp(12), dp(6))
            preview.background = rounded(this, x.bgColor, x.radiusDp)
            preview.text = if (x.hideText) " " else x.template.replace("{cps}", "12")
        }
        refreshPreview()

        pv.addView(Ui.divider(this))
        pv.addView(Ui.label(this, "显示文本"))
        val et = EditText(this).apply {
            setText(c.template); isSingleLine = true
            setTextColor(Pal.TEXT); textSize = 15f
            background = rounded(this@MainActivity, Pal.FIELD, 10)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    Cfg.putStr(this@MainActivity, i, "template", s.toString()); refreshPreview()
                }
            })
        }
        pv.addView(et, lp(-1, -2, 6, this))
        pv.addView(Ui.sub(this, "{cps} 会替换为数值").apply { setPadding(0, dp(4), 0, 0) })

        pv.addView(Ui.divider(this))
        pv.addView(Ui.navRow(this, "字体", Fonts.label(c.font)) { fontDialog(i) { buildSlot() } })
        pv.addView(Ui.switchRow(this, "粗体", null, c.bold) { Cfg.putBool(this, i, "bold", it); refreshPreview() })

        pv.addView(Ui.divider(this))
        pv.addView(Ui.sliderRow(this, "字号", "sp", 8, 120, 300, c.sizeSp) {
            Cfg.putInt(this, i, "size", it); refreshPreview()
        })
        pv.addView(Ui.sliderRow(this, "圆角", "dp", 0, 40, 100, c.radiusDp) {
            Cfg.putInt(this, i, "radius", it); refreshPreview()
        }, lp(-1, -2, 12, this))

        pv.addView(Ui.divider(this))
        pv.addView(Ui.colorRow(this, "文字颜色", c.textColor) { Cfg.putInt(this, i, "textColor", it); refreshPreview() })
        pv.addView(Ui.colorRow(this, "背景颜色", c.bgColor) { Cfg.putInt(this, i, "bgColor", it); refreshPreview() })
        content.addView(pv, lp(-1, -2, 12, this))

        // 行为
        val b = Ui.card(this)
        b.addView(Ui.title(this, "行为"))
        b.addView(Ui.divider(this))
        b.addView(Ui.switchRow(this, "隐藏文字", "只显示背景", c.hideText) { Cfg.putBool(this, i, "hideText", it); refreshPreview() })
        b.addView(Ui.switchRow(this, "未点击时只显示背景", null, c.idleBgOnly) { Cfg.putBool(this, i, "idleBgOnly", it) },
            lp(-1, -2, 12, this))
        b.addView(Ui.switchRow(this, "锁定位置", "锁定后无法拖动，且触摸可穿透显示窗口", c.locked) { Cfg.putBool(this, i, "locked", it) },
            lp(-1, -2, 12, this))
        b.addView(Ui.divider(this))
        b.addView(Ui.sub(this, "拖动悬浮窗即可移动位置，位置会自动保存"))
        b.addView(Ui.button(this, "重置位置", false) {
            Cfg.putInt(this, i, "x", 40); Cfg.putInt(this, i, "y", 200 + i * 180); toast("已重置位置")
        }, lp(-1, -2, 10, this))
        content.addView(b, lp(-1, -2, 12, this))

        content.addView(Ui.button(this, "停止全部悬浮窗", false) {
            for (k in 0 until SLOTS) Cfg.putBool(this, k, "enabled", false)
            buildSlot()
        }, lp(-1, -2, 14, this))
    }

    private fun pickRegion(slot: Int) {
        if (!Settings.canDrawOverlays(this)) { toast("请先授予悬浮窗权限"); return }
        FloatService.pick(this, slot)
        moveTaskToBack(true)
    }

    // ---------------- 字体 ----------------
    private fun fontDialog(slot: Int, done: () -> Unit) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val cfg = Cfg.load(this, slot)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(14))
        }
        box.addView(Ui.title(this, "选择字体"))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((name, key) in Fonts.presets) {
            val sel = cfg.font == key
            list.addView(TextView(this).apply {
                text = "$name   12 CPS"
                textSize = 18f
                typeface = Fonts.typeface(key, cfg.bold)
                setTextColor(if (sel) Pal.ACCENT else Pal.TEXT)
                background = if (sel) rounded(this@MainActivity, Pal.ACCENT_SOFT, 12) else null
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener { Cfg.putStr(this@MainActivity, slot, "font", key); dlg.dismiss(); done() }
            })
        }
        val sc = ScrollView(this).apply { addView(list) }
        box.addView(sc, lp(-1, dp(340), 10, this))
        box.addView(Ui.button(this, "导入字体文件 (.ttf / .otf)", false) {
            val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
            pendingFontSlot = slot; pendingDone = done; dlg.dismiss()
            startActivityForResult(pickIntent, REQ_FONT)
        }, lp(-1, -2, 10, this))
        dlg.setContentView(box)
        dlg.window?.apply {
            setBackgroundDrawable(rounded(this@MainActivity, Pal.CARD, 24))
            setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dlg.show()
    }

    private var pendingFontSlot = 0
    private var pendingDone: (() -> Unit)? = null

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FONT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val dir = File(filesDir, "fonts").apply { mkdirs() }
            val f = File(dir, "font_${pendingFontSlot}_${System.currentTimeMillis()}.ttf")
            contentResolver.openInputStream(uri)?.use { ins -> f.outputStream().use { ins.copyTo(it) } }
            Typeface.createFromFile(f) // 校验字体是否有效
            dir.listFiles()?.filter { it.name.startsWith("font_${pendingFontSlot}_") && it != f }?.forEach { it.delete() }
            Cfg.putStr(this, pendingFontSlot, "font", "file:${f.absolutePath}")
            pendingDone?.invoke()
        } catch (e: Throwable) {
            toast("字体文件无效")
        }
    }
}
