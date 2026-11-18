package com.xperience.camera.data

import android.content.Context
import com.xperience.camera.camera.CamSettings
import org.json.JSONObject

/** Simpanan tetapan + slot Memory Recall MR1/MR2/MR3. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("xperience", Context.MODE_PRIVATE)

    fun load(): CamSettings = try {
        sp.getString("settings", null)?.let { CamSettings.fromJson(JSONObject(it)) } ?: CamSettings()
    } catch (_: Exception) { CamSettings() }

    fun save(s: CamSettings) { sp.edit().putString("settings", s.toJson().toString()).apply() }

    fun mr(slot: Int): CamSettings? = try {
        sp.getString("mr$slot", null)?.let { CamSettings.fromJson(JSONObject(it)) }
    } catch (_: Exception) { null }

    fun saveMr(slot: Int, s: CamSettings) {
        val base = if (s.mode == com.xperience.camera.camera.ShootMode.MR) s.copy(mode = s.recallMode) else s
        sp.edit().putString("mr$slot", base.toJson().toString()).apply()
    }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }
}
