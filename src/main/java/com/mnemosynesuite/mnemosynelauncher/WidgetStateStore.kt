package com.mnemosynesuite.mnemosynelauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WidgetState(
    val appWidgetId: Int,
    val cellX: Int,
    val cellY: Int,
    val cellW: Int,
    val cellH: Int
)

data class ShortcutState(
    val packageName: String,
    val cellX: Int,
    val cellY: Int
)

object WidgetStateStore {
    private const val PREFS       = "widget_layout"
    private const val KEY_WIDGETS = "widgets"
    private const val KEY_SHORTCUTS = "shortcuts"

    fun save(context: Context, states: List<WidgetState>) {
        val arr = JSONArray()
        for (w in states) arr.put(JSONObject().apply {
            put("id",    w.appWidgetId)
            put("cellX", w.cellX)
            put("cellY", w.cellY)
            put("cellW", w.cellW)
            put("cellH", w.cellH)
        })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_WIDGETS, arr.toString()).apply()
    }

    fun load(context: Context): List<WidgetState> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_WIDGETS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                WidgetState(o.getInt("id"), o.getInt("cellX"), o.getInt("cellY"),
                    o.getInt("cellW"), o.getInt("cellH"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveShortcuts(context: Context, shortcuts: List<ShortcutState>) {
        val arr = JSONArray()
        for (s in shortcuts) arr.put(JSONObject().apply {
            put("pkg",   s.packageName)
            put("cellX", s.cellX)
            put("cellY", s.cellY)
        })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SHORTCUTS, arr.toString()).apply()
    }

    fun loadShortcuts(context: Context): List<ShortcutState> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SHORTCUTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ShortcutState(o.getString("pkg"), o.getInt("cellX"), o.getInt("cellY"))
            }
        }.getOrDefault(emptyList())
    }
}