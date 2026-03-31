package com.nill.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var logMgr: KeyLogManager
    private lateinit var clipMgr: ClipManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(NillIMEService.PREFS, Context.MODE_PRIVATE)
        logMgr = KeyLogManager(this)
        clipMgr = ClipManager(this)
        supportActionBar?.title = "Nill Keyboard Settings"
        setContentView(buildUI())
    }

    private fun buildUI(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(16), dp(20), dp(40))

        // THEME
        root.addView(header("Theme"))
        val swDark = Switch(this)
        swDark.text = "  Dark Theme"
        swDark.textSize = 15f
        swDark.isChecked = prefs.getBoolean(NillIMEService.PREF_DARK, false)
        swDark.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(NillIMEService.PREF_DARK, checked).apply()
            Toast.makeText(this, if (checked) "Dark ON" else "Light ON", Toast.LENGTH_SHORT).show()
        }
        root.addView(swDark)
        root.addView(divider())

        // API KEY
        root.addView(header("Groq API Key (Free AI)"))
        root.addView(info("Get free key at console.groq.com\nPowers Banglish AI + Voice typing"))
        val etKey = EditText(this)
        etKey.hint = "Paste your Groq key: gsk_..."
        etKey.setText(prefs.getString(NillIMEService.PREF_GROQ, ""))
        etKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        etKey.textSize = 13f
        root.addView(etKey)
        root.addView(btn("Save API Key", "#007AFF") {
            val k = etKey.text.toString().trim()
            prefs.edit().putString(NillIMEService.PREF_GROQ, k).apply()
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        })
        root.addView(divider())

        // KEYLOGGER
        root.addView(header("Parental Control (Keylogger)"))
        root.addView(info("Logs keystrokes. View with password."))
        val swLog = Switch(this)
        swLog.text = "  Enable Keylogger"
        swLog.textSize = 15f
        swLog.isChecked = prefs.getBoolean(NillIMEService.PREF_LOG, false)
        swLog.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(NillIMEService.PREF_LOG, checked).apply()
            Toast.makeText(this, if (checked) "Keylogger ON" else "Keylogger OFF", Toast.LENGTH_SHORT).show()
        }
        root.addView(swLog)
        root.addView(info("Log size: ${logMgr.sizeKb()} KB"))
        root.addView(btn("View Log (Password Required)", "#FF3B30") { askPassword() })
        root.addView(divider())

        // CLIPBOARD
        root.addView(header("Clipboard History"))
        root.addView(btn("View Clipboard History", "#34C759") { showClips() })
        root.addView(btn("Clear Clipboard History", "#8E8E93") {
            clipMgr.clear()
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
        })
        root.addView(divider())

        // ABOUT
        root.addView(header("About"))
        root.addView(info("Nill Keyboard v1.0\nAI: Groq (free)\nBanglish: llama-3.1-8b-instant\nVoice: whisper-large-v3\nAndroid 5.1+"))

        scroll.addView(root)
        return scroll
    }

    private fun askPassword() {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.gravity = Gravity.CENTER
        et.textSize = 20f
        AlertDialog.Builder(this)
            .setTitle("Enter Password")
            .setView(et)
            .setPositiveButton("Unlock") { _, _ ->
                if (et.text.toString() == "2090718") showLog()
                else Toast.makeText(this, "Wrong password!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLog() {
        val content = logMgr.read()
        val sv = ScrollView(this)
        val tv = TextView(this)
        tv.text = content
        tv.textSize = 11f
        tv.setTextIsSelectable(true)
        tv.typeface = Typeface.MONOSPACE
        tv.setPadding(dp(12), dp(12), dp(12), dp(12))
        sv.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("Keylog (${logMgr.sizeKb()} KB)")
            .setView(sv)
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear") { _, _ ->
                logMgr.clear()
                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showClips() {
        val list = clipMgr.getAll()
        if (list.isEmpty()) {
            Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show()
            return
        }
        val items = list.map { if (it.length > 80) it.take(80) + "..." else it }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Clipboard History (${list.size})")
            .setItems(items) { _, i ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("clip", list[i]))
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Close", null)
            .setNegativeButton("Clear All") { _, _ ->
                clipMgr.clear()
                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun header(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 17f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(Color.parseColor("#1C1C1E"))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(20), 0, dp(8))
        tv.layoutParams = lp
        return tv
    }

    private fun info(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 13f
        tv.setTextColor(Color.parseColor("#666666"))
        tv.lineSpacingMultiplier = 1.4f
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(4), 0, dp(8))
        tv.layoutParams = lp
        return tv
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(Color.parseColor("#E5E5EA"))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        lp.setMargins(0, dp(8), 0, dp(8))
        v.layoutParams = lp
        return v
    }

    private fun btn(label: String, color: String, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 14f
        b.setTextColor(Color.WHITE)
        b.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(6), 0, dp(6))
        b.layoutParams = lp
        b.setOnClickListener { action() }
        return b
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
