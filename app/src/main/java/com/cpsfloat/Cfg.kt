package com.cpsfloat

import android.content.Context
import android.content.SharedPreferences

const val SLOTS = 2

object Modes {
    const val FULL = 0    // 全屏点击统计（Shizuku）
    const val REGION = 1  // 指定区域统计（Shizuku）
    const val COVER = 2   // 区域覆盖统计（无需 Shizuku，会拦截该区域点击）
}

data class SlotCfg(
    val enabled: Boolean,
    val mode: Int,
    val rx: Int, val ry: Int, val rw: Int, val rh: Int,   // 点击区域（屏幕像素）
    val x: Int, val y: Int,                                // CPS 显示位置
    val sizeSp: Int,
    val textColor: Int,
    val bgColor: Int,
    val radiusDp: Int,
    val template: String,
    val font: String,
    val bold: Boolean,
    val hideText: Boolean,
    val idleBgOnly: Boolean,
    val locked: Boolean
) {
    val hasRegion get() = rw > 0 && rh > 0
}

/** 所有设置改动即时写入 SharedPreferences，服务监听变化后自动同步，无需“保存并应用”。 */
object Cfg {
    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("cps_cfg", Context.MODE_PRIVATE)

    private fun k(i: Int, n: String) = "s${i}_$n"

    fun load(c: Context, i: Int): SlotCfg {
        val p = prefs(c)
        fun gi(n: String, d: Int) = p.getInt(k(i, n), d)
        fun gb(n: String, d: Boolean) = p.getBoolean(k(i, n), d)
        return SlotCfg(
            enabled = gb("enabled", false),
            mode = gi("mode", Modes.FULL),
            rx = gi("rx", 0), ry = gi("ry", 0), rw = gi("rw", 0), rh = gi("rh", 0),
            x = gi("x", 40), y = gi("y", 200 + i * 180),
            sizeSp = gi("size", 22),
            textColor = gi("textColor", 0xFFFFFFFF.toInt()),
            bgColor = gi("bgColor", 0xAA000000.toInt()),
            radiusDp = gi("radius", 12),
            template = p.getString(k(i, "template"), "{cps} CPS") ?: "{cps} CPS",
            font = p.getString(k(i, "font"), "sans-serif") ?: "sans-serif",
            bold = gb("bold", true),
            hideText = gb("hideText", false),
            idleBgOnly = gb("idleBgOnly", false),
            locked = gb("locked", false)
        )
    }

    fun putInt(c: Context, i: Int, n: String, v: Int) = prefs(c).edit().putInt(k(i, n), v).apply()
    fun putBool(c: Context, i: Int, n: String, v: Boolean) = prefs(c).edit().putBoolean(k(i, n), v).apply()
    fun putStr(c: Context, i: Int, n: String, v: String) = prefs(c).edit().putString(k(i, n), v).apply()

    fun putRegion(c: Context, i: Int, x: Int, y: Int, w: Int, h: Int) {
        prefs(c).edit()
            .putInt(k(i, "rx"), x).putInt(k(i, "ry"), y)
            .putInt(k(i, "rw"), w).putInt(k(i, "rh"), h).apply()
    }

    fun anyEnabled(c: Context) = (0 until SLOTS).any { load(c, it).enabled }
}
