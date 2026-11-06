package uz.alifbo.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import uz.alifbo.keyboard.R
import uz.alifbo.keyboard.data.ShortcutLogic
import uz.alifbo.keyboard.data.Shortcuts
import uz.alifbo.keyboard.data.TextTransforms
import java.util.Calendar

/** Tezkor amallar: sana/vaqt, matnni o'zgartirish (KATTA, Bosh Harf...), tayyor javoblar va qisqartmalar. */
class QuickPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onQuickText(text: String)
        fun onQuickTransform(kind: Int)
        fun onBack()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val body = LinearLayout(context).apply { orientation = VERTICAL }

    companion object {
        /** Tayyor javoblar (qisqartmalar bilan birga ko'rsatiladi). */
        val REPLIES = listOf("Rahmat!", "Hozir yozaman", "Yaxshi, tushundim", "Band, keyinroq yozaman", "Kechirasiz, kechikdim", "Xayr, ko‘rishguncha!")
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.col(context, R.color.kb_bg))
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("ABC", Theme.accent(context), 14f).apply { setOnClickListener { listener?.onBack() } }, LayoutParams(dp(64), dp(40)))
        header.addView(text("Tezkor amallar", Theme.col(context, R.color.kb_hint), 13f), LayoutParams(0, dp(40), 1f))
        addView(header, LayoutParams(MATCH_PARENT, dp(40)))
        val sv = ScrollView(context)
        sv.addView(body)
        addView(sv, LayoutParams(MATCH_PARENT, 0, 1f))
    }

    /** Panel ochilganda: sana/vaqt qiymatlari va qisqartmalar ro'yxati yangilanadi. */
    fun show() {
        body.removeAllViews()
        val now = Calendar.getInstance()
        val date = ShortcutLogic.expandVars("{sana}", now)
        val time = ShortcutLogic.expandVars("{vaqt}", now)
        val week = ShortcutLogic.expandVars("{hafta}", now)

        section("Sana va vaqt")
        row(
            chip(date) { listener?.onQuickText(date) },
            chip(time) { listener?.onQuickText(time) },
            chip("$date $time") { listener?.onQuickText("$date $time") },
            chip(week) { listener?.onQuickText(week) }
        )

        section("Matnni o‘zgartirish (belgilangan matn, yo‘q bo‘lsa — hammasi)")
        row(
            chip("KATTA") { listener?.onQuickTransform(TextTransforms.UPPER) },
            chip("kichik") { listener?.onQuickTransform(TextTransforms.LOWER) },
            chip("Bosh Harf") { listener?.onQuickTransform(TextTransforms.TITLE) },
            chip("Gap boshi") { listener?.onQuickTransform(TextTransforms.SENTENCE) }
        )
        row(chip("Bo‘shliqlarni tuzatish") { listener?.onQuickTransform(TextTransforms.TIDY) })

        section("Tayyor javoblar")
        for (r in REPLIES) wide(r) { listener?.onQuickText(r) }

        val sc = Shortcuts.list(context)
        if (sc.isNotEmpty()) {
            section("Sizning qisqartmalaringiz")
            for (s in sc.sortedBy { it.abbr }) {
                val full = ShortcutLogic.expandVars(s.text, now)
                wide("${s.abbr}  →  $full") { listener?.onQuickText(full) }
            }
        }
    }

    private fun text(t: String, color: Int, size: Float) = TextView(context).apply {
        text = t
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(color)
    }

    private fun section(t: String) {
        body.addView(TextView(context).apply {
            text = t
            textSize = 12f
            setTextColor(Theme.col(context, R.color.kb_hint))
            setPadding(dp(12), dp(10), dp(12), dp(4))
        }, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun box() = GradientDrawable().apply {
        setColor(Theme.col(context, R.color.kb_key))
        cornerRadius = dp(10).toFloat()
    }

    private fun chip(t: String, onClick: () -> Unit) = TextView(context).apply {
        text = t
        textSize = 13f
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(Theme.col(context, R.color.kb_text))
        background = box()
        setOnClickListener { onClick() }
    }

    private fun row(vararg chips: TextView) {
        val r = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (c in chips) r.addView(c, LayoutParams(0, dp(44), 1f).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) })
        body.addView(r, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(8), 0, dp(8), 0) })
    }

    private fun wide(t: String, onClick: () -> Unit) {
        val v = TextView(context).apply {
            text = t
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Theme.col(context, R.color.kb_text))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = box()
            setOnClickListener { onClick() }
        }
        body.addView(v, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(8), dp(3), dp(8), dp(3)) })
    }
}
