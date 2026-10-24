package uz.alifbo.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import uz.alifbo.keyboard.R
import uz.alifbo.keyboard.data.AlphabetTools

/** Alifbo oʻgiruvchi: belgilangan (yoki maydondagi) matnni kirill / eski lotin / yangi alifboga oʻgirib joyiga qoʻyadi. */
class ConvertPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onConvertBack()
        /** Matnni oʻgirib joyiga yozadi. target: AlphabetTools.TO_*. */
        fun onConvertApply(target: Int)
        /** Oʻgiriladigan matn (belgilangan boʻlsa shu, boʻlmasa maydondagi hammasi). */
        fun onConvertSource(): String?
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val preview = TextView(context)
    private val after = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.kb_bg))

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = "ABC"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Theme.accent(context))
            setOnClickListener { listener?.onConvertBack() }
        }, LayoutParams(dp(64), dp(40)))
        header.addView(TextView(context).apply {
            text = "Alifbo oʻgiruvchi — belgilangan matn (yoki maydondagi hammasi)"
            textSize = 12f
            setTextColor(context.getColor(R.color.kb_hint))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(0, dp(40), 1f))
        addView(header, LayoutParams(MATCH_PARENT, dp(40)))

        preview.apply {
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(context.getColor(R.color.kb_hint))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = card()
        }
        addView(preview, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(8), dp(2), dp(8), dp(4)) })

        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        row.addView(btn("Кирилл") { apply(AlphabetTools.TO_CYRILLIC) }, LayoutParams(0, dp(52), 1f).apply { setMargins(dp(8), dp(4), dp(4), dp(4)) })
        row.addView(btn("Oʻ Gʻ (1995)") { apply(AlphabetTools.TO_OLD_LATIN) }, LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        row.addView(btn("Ö Ğ Ş Ç (yangi)") { apply(AlphabetTools.TO_NEW_LATIN) }, LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), dp(4), dp(8), dp(4)) })
        addView(row, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        after.apply {
            textSize = 12f
            setTextColor(context.getColor(R.color.kb_hint))
            setPadding(dp(12), dp(2), dp(12), dp(2))
        }
        addView(after, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun card() = GradientDrawable().apply {
        setColor(context.getColor(R.color.kb_key))
        cornerRadius = dp(10).toFloat()
    }

    private fun btn(t: String, onClick: () -> Unit) = TextView(context).apply {
        text = t
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply {
            setColor(Theme.accent(context))
            cornerRadius = dp(14).toFloat()
        }
        setOnClickListener { onClick() }
    }

    /** Panel ochilganda chaqiriladi. */
    fun open() {
        val s = listener?.onConvertSource()
        preview.text = if (s.isNullOrBlank()) "Matn yoʻq: avval yozing yoki belgilang." else s
        after.text = ""
    }

    private fun apply(target: Int) {
        val s = listener?.onConvertSource()
        if (s.isNullOrBlank()) return
        listener?.onConvertApply(target)
    }
}
