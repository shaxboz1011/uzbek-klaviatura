package uz.alifbo.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import uz.alifbo.keyboard.R
import uz.alifbo.keyboard.data.Prefs
import uz.alifbo.keyboard.data.Translator

/**
 * Tezkor tarjima paneli: maydondagi (yoki belgilangan) matnni tanlangan tilga tarjima qiladi,
 * keyin uni almashtiradi, qoʻshadi yoki nusxalaydi.
 */
class TranslatePanel(context: Context) : LinearLayout(context) {

    /** Maydondan olingan matn. before/after — kursordan oldingi/keyingi belgilar soni (belgilanmagan boʻlsa). */
    class Source(val text: String, val selected: Boolean, val before: Int, val after: Int)

    interface Listener {
        fun onTranslateBack()
        fun onTranslateSource(): Source?
        fun onTranslateReplace(result: String, src: Source)
        fun onTranslateInsert(result: String)
        fun onTranslateCopy(result: String)
    }

    var listener: Listener? = null

    private val prefs = Prefs(context)
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun accent() = Theme.accent(context)

    private var from = "uz"
    private var to = prefs.translateTo
    private var secret = false
    private var src: Source? = null
    private var result: String? = null

    private val fromChip = chip("") { cycleFrom() }
    private val chips = LinearLayout(context)
    private val sourceView = TextView(context)
    private val resultView = TextView(context)
    private val consentBox = LinearLayout(context)
    private val actions = LinearLayout(context)
    private val chipViews = ArrayList<Pair<String, TextView>>()

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.kb_bg))

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(flat("ABC") { listener?.onTranslateBack() }, LayoutParams(dp(52), dp(44)))
        header.addView(fromChip, LayoutParams(WRAP_CONTENT, dp(32)))
        header.addView(flat("⇄") { swap() }, LayoutParams(dp(40), dp(44)))
        val hs = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        for (l in Translator.LANGS) {
            val c = chip(l.name) { selectTo(l.code) }
            chips.addView(c, LayoutParams(WRAP_CONTENT, dp(32)).apply { setMargins(dp(3), 0, dp(3), 0) })
            chipViews.add(l.code to c)
        }
        header.addView(hs, LayoutParams(0, dp(44), 1f))
        header.addView(flat("↻") { refresh() }, LayoutParams(dp(44), dp(44)))
        addView(header, LayoutParams(MATCH_PARENT, dp(44)))

        sourceView.apply {
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(context.getColor(R.color.kb_hint))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = card()
        }
        addView(sourceView, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(8), dp(2), dp(8), dp(4)) })

        val body = android.widget.FrameLayout(context)
        resultView.apply {
            textSize = 17f
            setTextColor(context.getColor(R.color.kb_text))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val sv = ScrollView(context).apply {
            background = card()
            addView(resultView)
        }
        body.addView(sv, android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        consentBox.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            background = card()
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
            addView(TextView(context).apply {
                text = "Tarjima uchun matn tashqi xizmatga (mymemory.translated.net) yuboriladi. " +
                    "Parol, email va havola maydonlarida ishlamaydi."
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.kb_text))
            })
            addView(pill("Roziman", true) {
                prefs.translateConsent = true
                refresh()
            }, LayoutParams(WRAP_CONTENT, dp(36)).apply { topMargin = dp(8) })
        }
        body.addView(consentBox, android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(body, LayoutParams(MATCH_PARENT, 0, 1f).apply { setMargins(dp(8), 0, dp(8), dp(4)) })

        actions.orientation = HORIZONTAL
        actions.addView(pill("Almashtirish", true) {
            val r = result
            val s = src
            if (r != null && s != null) listener?.onTranslateReplace(r, s)
        }, LayoutParams(0, dp(40), 1.3f).apply { setMargins(dp(8), 0, dp(4), dp(4)) })
        actions.addView(pill("Qoʻshish", false) {
            result?.let { listener?.onTranslateInsert(it) }
        }, LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), 0, dp(4), dp(4)) })
        actions.addView(pill("Nusxa", false) {
            result?.let { listener?.onTranslateCopy(it) }
        }, LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), 0, dp(8), dp(4)) })
        addView(actions, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    // ---------------------------------------------------------------- koʻrinish yordamchilari

    private fun card() = GradientDrawable().apply {
        setColor(context.getColor(R.color.kb_key))
        cornerRadius = dp(10).toFloat()
    }

    private fun flat(t: String, onClick: () -> Unit) = TextView(context).apply {
        text = t
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(accent())
        setOnClickListener { onClick() }
    }

    private fun chip(t: String, onClick: () -> Unit) = TextView(context).apply {
        text = t
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { onClick() }
    }

    private fun pill(t: String, primary: Boolean, onClick: () -> Unit) = TextView(context).apply {
        text = t
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else context.getColor(R.color.kb_text))
        background = GradientDrawable().apply {
            setColor(if (primary) accent() else context.getColor(R.color.kb_key_special))
            cornerRadius = dp(20).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun paintChip(c: TextView, on: Boolean) {
        c.setTextColor(if (on) 0xFFFFFFFF.toInt() else context.getColor(R.color.kb_text))
        c.background = GradientDrawable().apply {
            setColor(if (on) accent() else context.getColor(R.color.kb_key_special))
            cornerRadius = dp(16).toFloat()
        }
    }

    private fun updateChips() {
        paintChip(fromChip, false)
        fromChip.text = Translator.nameOf(from) + " ▾"
        for ((code, v) in chipViews) paintChip(v, code == to)
    }

    private fun setResult(text: String?, error: Boolean = false) {
        result = if (error) null else text
        resultView.text = text ?: ""
        resultView.setTextColor(context.getColor(if (error || text == null) R.color.kb_hint else R.color.kb_text))
        val ok = !error && text != null
        actions.alpha = if (ok) 1f else 0.4f
        for (i in 0 until actions.childCount) actions.getChildAt(i).isEnabled = ok
    }

    // ---------------------------------------------------------------- amallar

    /** Panel ochilganda: fromCode — joriy klaviatura tili. */
    fun open(fromCode: String, secretField: Boolean) {
        from = if (Translator.LANGS.any { it.code == fromCode }) fromCode else "uz"
        to = prefs.translateTo
        if (to == from) to = Translator.LANGS.first { it.code != from }.code
        secret = secretField
        refresh()
    }

    private fun refresh() {
        updateChips()
        if (secret) {
            consentBox.visibility = View.GONE
            sourceView.text = ""
            setResult("Bu maydonda (parol, email, havola) tarjima oʻchirilgan.", true)
            return
        }
        if (!prefs.translateConsent) {
            consentBox.visibility = View.VISIBLE
            sourceView.text = ""
            setResult(null)
            return
        }
        consentBox.visibility = View.GONE
        val s = listener?.onTranslateSource()
        src = s
        if (s == null || s.text.isBlank()) {
            sourceView.text = ""
            setResult("Matn yozing yoki belgilang, keyin shu yerga qayting.", true)
            return
        }
        sourceView.text = s.text
        run(s.text)
    }

    private fun run(text: String) {
        setResult("Tarjima qilinmoqda…", true)
        Translator.translate(text, from, to) { out, err ->
            if (out != null) setResult(out) else setResult(err ?: "Xato", true)
        }
    }

    private fun rerun() {
        val s = src
        if (secret || s == null || s.text.isBlank() || !prefs.translateConsent) return
        run(s.text)
    }

    private fun selectTo(code: String) {
        to = code
        prefs.translateTo = code
        updateChips()
        rerun()
    }

    private fun cycleFrom() {
        val l = Translator.LANGS
        var i = l.indexOfFirst { it.code == from }
        do {
            i = (i + 1) % l.size
        } while (l[i].code == to)
        from = l[i].code
        updateChips()
        rerun()
    }

    private fun swap() {
        val f = from
        from = to
        to = f
        prefs.translateTo = to
        updateChips()
        rerun()
    }
}
