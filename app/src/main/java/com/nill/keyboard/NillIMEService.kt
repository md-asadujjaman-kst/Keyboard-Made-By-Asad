package com.nill.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class NillIMEService : InputMethodService() {

    companion object {
        const val PREFS = "nill_kb_prefs"
        const val PREF_DARK = "dark_theme"
        const val PREF_GROQ = "groq_key"
        const val PREF_LOG = "keylogger_on"
    }

    enum class Mode { ENG, BN, BANGLISH, EMOJI }

    private var mode = Mode.ENG
    private var shifted = false
    private val banglishBuf = StringBuilder()
    private lateinit var prefs: SharedPreferences
    private lateinit var logMgr: KeyLogManager
    private lateinit var clipMgr: ClipManager
    private var mediaRec: MediaRecorder? = null
    private var recording = false
    private var rootLayout: LinearLayout? = null

    private var bgCol = 0; private var keyCol = 0; private var spCol = 0
    private var txtCol = 0; private var accCol = 0

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        logMgr = KeyLogManager(this)
        clipMgr = ClipManager(this)
    }

    override fun onCreateInputView(): View {
        loadTheme()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bgCol)
        rootLayout = root
        rebuild()
        return root
    }

    override fun onWindowShown() {
        super.onWindowShown()
        loadTheme()
        rebuild()
    }

    private fun loadTheme() {
        val dark = prefs.getBoolean(PREF_DARK, false)
        bgCol = if (dark) Color.parseColor("#1C1C1E") else Color.parseColor("#D1D3D4")
        keyCol = if (dark) Color.parseColor("#3A3A3C") else Color.WHITE
        spCol = if (dark) Color.parseColor("#636366") else Color.parseColor("#AEB4BD")
        txtCol = if (dark) Color.WHITE else Color.parseColor("#1C1C1E")
        accCol = Color.parseColor("#007AFF")
    }

    private fun rebuild() {
        val root = rootLayout ?: return
        root.removeAllViews()
        root.setBackgroundColor(bgCol)
        root.addView(modeBar())
        when (mode) {
            Mode.ENG, Mode.BANGLISH -> qwerty(root)
            Mode.BN -> bangla(root)
            Mode.EMOJI -> emojiGrid(root)
        }
        if (mode == Mode.BANGLISH) root.addView(banglishBar())
    }

    // ── MODE BAR ──────────────────────────────────────────────

    private fun modeBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(bgCol)
        bar.setPadding(dp(4), dp(4), dp(4), 0)
        val labels = listOf("ENG", "বাং", "AI", "😊")
        val modes = listOf(Mode.ENG, Mode.BN, Mode.BANGLISH, Mode.EMOJI)
        for (i in labels.indices) {
            val active = mode == modes[i]
            val tv = TextView(this)
            tv.text = labels[i]
            tv.textSize = 12f
            tv.gravity = Gravity.CENTER
            tv.setTextColor(if (active) Color.WHITE else txtCol)
            tv.background = round(if (active) accCol else spCol, 6)
            val lp = LinearLayout.LayoutParams(0, dp(34), 1f)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            tv.layoutParams = lp
            val m = modes[i]
            tv.setOnClickListener {
                mode = m
                banglishBuf.clear()
                shifted = false
                rebuild()
            }
            bar.addView(tv)
        }
        return bar
    }

    // ── QWERTY ────────────────────────────────────────────────

    private fun qwerty(root: LinearLayout) {
        val r1 = listOf("q","w","e","r","t","y","u","i","o","p")
        val r2 = listOf("a","s","d","f","g","h","j","k","l")
        val r3 = listOf("z","x","c","v","b","n","m")

        root.addView(row {
            for (k in r1) addView(letterKey(k))
            addView(spKey("⌫", 1.5f) { del() })
        })
        root.addView(row {
            addView(spacer(0.3f))
            for (k in r2) addView(letterKey(k))
            addView(spKey("↵", 1.7f) { enter() })
        })
        root.addView(row {
            addView(spKey(if (shifted) "⬆" else "⇧", 1.5f) { shifted = !shifted; rebuild() })
            for (k in r3) addView(letterKey(k))
            addView(spKey(",", 0.8f) { type(",") })
            addView(spKey(".", 0.8f) { type(".") })
        })
        root.addView(row {
            for (n in listOf("1","2","3","4","5","6","7","8","9","0")) {
                val tv = key(n, 1f)
                tv.setOnClickListener { type(n) }
                addView(tv)
            }
        })
        root.addView(row {
            for (s in listOf("!","@","#","$","%","?","&","*","(",")")) {
                val tv = key(s, 1f)
                tv.setOnClickListener { type(s) }
                addView(tv)
            }
        })
        root.addView(bottomRow())
    }

    private fun letterKey(k: String): TextView {
        val disp = if (shifted) k.uppercase() else k
        val tv = key(disp, 1f)
        tv.setOnClickListener {
            tv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            type(if (shifted) k.uppercase() else k)
            if (shifted) { shifted = false; rebuild() }
        }
        return tv
    }

    // ── BANGLA ────────────────────────────────────────────────

    private fun bangla(root: LinearLayout) {
        val vowels = listOf("অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ")
        val matras = listOf("া","ি","ী","ু","ূ","ৃ","ে","ৈ","ো","ৌ","্")
        val c1 = listOf("ক","খ","গ","ঘ","ঙ","চ","ছ","জ","ঝ","ঞ","⌫")
        val c2 = listOf("ট","ঠ","ড","ঢ","ণ","ত","থ","দ","ধ","ন","↵")
        val c3 = listOf("প","ফ","ব","ভ","ম","য","র","ল","শ","ষ","স")
        val c4 = listOf("হ","ড়","ঢ়","য়","ৎ","ং","ঃ","ঁ","।","?","!")
        val nums = listOf("০","১","২","৩","৪","৫","৬","৭","৮","৯")

        for (rowData in listOf(vowels, matras, c1, c2, c3, c4)) {
            root.addView(row {
                for (ch in rowData) {
                    when (ch) {
                        "⌫" -> addView(spKey(ch, 1f) { del() })
                        "↵" -> addView(spKey(ch, 1f) { enter() })
                        else -> {
                            val tv = key(ch, 1f)
                            tv.setOnClickListener { type(ch) }
                            addView(tv)
                        }
                    }
                }
            })
        }
        root.addView(row {
            for (n in nums) {
                val tv = key(n, 1f)
                tv.setOnClickListener { type(n) }
                addView(tv)
            }
        })
        root.addView(bottomRow())
    }

    // ── BANGLISH BAR ──────────────────────────────────────────

    private fun banglishBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(8), dp(6), dp(8), dp(6))
        bar.setBackgroundColor(if (prefs.getBoolean(PREF_DARK, false)) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7"))

        val hint = TextView(this)
        hint.text = if (banglishBuf.isNotEmpty()) "\"$banglishBuf\" → convert" else "Type English → AI converts to Bangla"
        hint.textSize = 12f
        hint.setTextColor(if (prefs.getBoolean(PREF_DARK, false)) Color.LTGRAY else Color.DKGRAY)
        val hlp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        hint.layoutParams = hlp

        val btn = TextView(this)
        btn.text = "Convert"
        btn.textSize = 12f
        btn.setTextColor(Color.WHITE)
        btn.background = round(accCol, 8)
        btn.setPadding(dp(10), dp(6), dp(10), dp(6))
        btn.setOnClickListener { convertBanglish() }

        bar.addView(hint)
        bar.addView(btn)
        return bar
    }

    // ── EMOJI ─────────────────────────────────────────────────

    private fun emojiGrid(root: LinearLayout) {
        val sv = ScrollView(this)
        val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220))
        sv.layoutParams = lp
        val grid = GridLayout(this)
        grid.columnCount = 9
        grid.setPadding(dp(4), dp(4), dp(4), dp(4))
        for (e in emojis()) {
            val tv = TextView(this)
            tv.text = e
            tv.textSize = 22f
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(4), dp(4), dp(4), dp(4))
            tv.setOnClickListener { type(e) }
            grid.addView(tv)
        }
        sv.addView(grid)
        root.addView(sv)
        root.addView(bottomRow())
    }

    // ── BOTTOM ROW ────────────────────────────────────────────

    private fun bottomRow(): LinearLayout {
        return row {
            addView(spKey("⌨", 1f) {
                mode = when (mode) {
                    Mode.ENG -> Mode.BN
                    Mode.BN -> Mode.BANGLISH
                    Mode.BANGLISH -> Mode.EMOJI
                    Mode.EMOJI -> Mode.ENG
                }
                banglishBuf.clear()
                rebuild()
            })
            val sp = key("Space", 3f)
            sp.setOnClickListener { type(" ") }
            addView(sp)
            addView(spKey(if (recording) "⏹" else "🎤", 1f) { toggleVoice() })
            addView(spKey("↵", 1f) { enter() })
        }
    }

    // ── KEY BUILDERS ──────────────────────────────────────────

    private fun row(block: LinearLayout.() -> Unit): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.setPadding(dp(3), dp(2), dp(3), dp(2))
        r.block()
        return r
    }

    private fun key(label: String, flex: Float, special: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = if (label.length > 3) 11f else if (label.length > 1) 13f else 17f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(txtCol)
        tv.background = round(if (special) spCol else keyCol, 6)
        tv.isHapticFeedbackEnabled = true
        val lp = LinearLayout.LayoutParams(0, dp(44), flex)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        tv.layoutParams = lp
        return tv
    }

    private fun spKey(label: String, flex: Float, action: () -> Unit): TextView {
        val tv = key(label, flex, true)
        tv.setOnClickListener {
            tv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
        }
        return tv
    }

    private fun spacer(flex: Float): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(0, dp(44), flex)
        return v
    }

    private fun round(color: Int, r: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = r * resources.displayMetrics.density
        d.setColor(color)
        return d
    }

    // ── ACTIONS ───────────────────────────────────────────────

    private fun type(ch: String) {
        currentInputConnection?.commitText(ch, 1)
        if (mode == Mode.BANGLISH && ch != " ") banglishBuf.append(ch)
        else if (ch == " " && mode == Mode.BANGLISH) banglishBuf.append(" ")
        if (prefs.getBoolean(PREF_LOG, false)) logMgr.log(ch)
    }

    private fun del() {
        currentInputConnection?.deleteSurroundingText(1, 0)
        if (mode == Mode.BANGLISH && banglishBuf.isNotEmpty())
            banglishBuf.deleteCharAt(banglishBuf.length - 1)
    }

    private fun enter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    // ── BANGLISH AI ───────────────────────────────────────────

    private fun convertBanglish() {
        val text = banglishBuf.toString().trim()
        if (text.isEmpty()) { Toast.makeText(this, "Type something first", Toast.LENGTH_SHORT).show(); return }
        val key = prefs.getString(PREF_GROQ, "") ?: ""
        if (key.isEmpty()) { Toast.makeText(this, "Add Groq API key in Settings", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "Converting...", Toast.LENGTH_SHORT).show()
        GroqClient(key).banglish(text) { result ->
            Handler(Looper.getMainLooper()).post {
                currentInputConnection?.deleteSurroundingText(text.length, 0)
                currentInputConnection?.commitText(result, 1)
                banglishBuf.clear()
            }
        }
    }

    // ── VOICE ─────────────────────────────────────────────────

    private fun toggleVoice() {
        if (recording) stopVoice() else startVoice()
    }

    private fun startVoice() {
        try {
            val f = java.io.File(cacheDir, "nk_rec.m4a")
            if (f.exists()) f.delete()
            mediaRec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
                       else @Suppress("DEPRECATION") MediaRecorder()
            mediaRec!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRec!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRec!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRec!!.setAudioSamplingRate(16000)
            mediaRec!!.setOutputFile(f.absolutePath)
            mediaRec!!.prepare()
            mediaRec!!.start()
            recording = true
            rebuild()
            Toast.makeText(this, "Recording... tap stop when done", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Mic error: allow permission in app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoice() {
        try {
            mediaRec?.stop(); mediaRec?.release(); mediaRec = null
            recording = false; rebuild()
            val key = prefs.getString(PREF_GROQ, "") ?: ""
            if (key.isEmpty()) { Toast.makeText(this, "Add Groq key in Settings", Toast.LENGTH_SHORT).show(); return }
            val f = java.io.File(cacheDir, "nk_rec.m4a")
            if (!f.exists() || f.length() < 500) { Toast.makeText(this, "Too short, try again", Toast.LENGTH_SHORT).show(); return }
            val lang = if (mode == Mode.BN || mode == Mode.BANGLISH) "bn" else "en"
            Toast.makeText(this, "Processing voice...", Toast.LENGTH_SHORT).show()
            GroqClient(key).transcribe(f, lang) { text ->
                Handler(Looper.getMainLooper()).post {
                    if (text.isNotEmpty()) currentInputConnection?.commitText(text, 1)
                    else Toast.makeText(this, "Could not recognize", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) { mediaRec = null; recording = false; rebuild() }
    }

    // ── EMOJI LIST ────────────────────────────────────────────

    private fun emojis() = listOf(
        "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩",
        "😘","😚","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔","🤨","😐","😑","😶",
        "😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧",
        "🥵","🥶","🥴","😵","🤯","🤠","😎","🤓","🧐","😕","😟","🙁","☹️","😮","😯","😲",
        "😳","🥺","😦","😧","😨","😰","😥","😢","😭","😱","😤","😡","😠","🤬","😈","👿",
        "👋","🤚","✋","✌️","🤞","👌","👍","👎","✊","👏","🙏","💪","❤️","🧡","💛","💚",
        "💙","💜","🖤","💔","💕","💞","💓","💗","💖","🔥","⭐","✨","⚡","❄️","🌈","☀️",
        "🎉","🎊","🎈","🎁","🏆","🎵","🎶","📱","💻","✈️","🚀","🚗","🏠","🍕","🍔","🎂",
        "☕","⚽","🏀","🎮","🎲","💯","✅","❌","⚠️","🔑","💡","🔔","💰","📞","📧","🇧🇩"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
