package com.nill.keyboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class ClipManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("nk_clips", Context.MODE_PRIVATE)
    private val KEY = "list"

    fun add(text: String) {
        if (text.isBlank() || text.length > 5000) return
        val list = getAll().toMutableList()
        list.remove(text)
        list.add(0, text)
        if (list.size > 50) list.subList(50, list.size).clear()
        val arr = JSONArray(); list.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun getAll(): List<String> {
        return try {
            val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun clear() { prefs.edit().remove(KEY).apply() }
}
