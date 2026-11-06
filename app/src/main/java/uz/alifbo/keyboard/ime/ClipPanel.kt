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
import uz.alifbo.keyboard.data.ClipKinds
import uz.alifbo.keyboard.data.ClipStore

class ClipPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onPaste(text: String)
        fun onTogglePin(text: String)
        fun onClearClips()
        fun onBack()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private var all: List<ClipStore.Item> = emptyList()
    private var access = true
    private var kind = ClipKinds.ALL
    private val tabs = LinearLayout(context).apply { orientation = HORIZONTAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.col(context, R.color.kb_bg))

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(btn("ABC") { listener?.onBack() }, LayoutParams(dp(64), dp(44)))
        header.addView(TextView(context).apply {
            text = "Bufer  ·  bosing — qoʻyish, uzoq bosing — qadash"
            textSize = 12f
            setTextColor(Theme.col(context, R.color.kb_hint))
            gravity = Gravity.CENTER_VERTICAL
        }, LayoutParams(0, dp(44), 1f))
        header.addView(btn("Tozalash") { listener?.onClearClips() }, LayoutParams(WRAP_CONTENT, dp(44)))
        addView(header, LayoutParams(MATCH_PARENT, dp(44)))

        for ((k, name) in listOf(ClipKinds.ALL to "Hammasi", ClipKinds.LINK to "Havola", ClipKinds.NUMBER to "Raqam", ClipKinds.PINNED to "Qadalgan")) {
            tabs.addView(TextView(context).apply {
                text = name
                textSize = 13f
                gravity = Gravity.CENTER
                tag = k
                setOnClickListener {
                    kind = k
                    render()
                }
            }, LayoutParams(0, dp(34), 1f).apply { setMargins(dp(4), 0, dp(4), dp(4)) })
        }
        addView(tabs, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(4), 0, dp(4), 0) })

        val sv = ScrollView(context)
        sv.addView(list)
        addView(sv, LayoutParams(MATCH_PARENT, 0, 1f))
    }

    private fun btn(t: String, onClick: () -> Unit) = TextView(context).apply {
        text = t
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(Theme.accent(context))
        setOnClickListener { onClick() }
    }

    fun show(items: List<ClipStore.Item>, access: Boolean = true) {
        all = items
        this.access = access
        render()
    }

    private fun render() {
        for (i in 0 until tabs.childCount) {
            val t = tabs.getChildAt(i) as TextView
            val on = t.tag == kind
            t.setTextColor(if (on) 0xFFFFFFFF.toInt() else Theme.col(context, R.color.kb_text))
            t.background = GradientDrawable().apply {
                setColor(if (on) Theme.accent(context) else Theme.col(context, R.color.kb_key))
                cornerRadius = dp(17).toFloat()
            }
        }
        val items = all.filter { ClipKinds.matches(kind, it) }
        list.removeAllViews()
        if (items.isEmpty() && all.isNotEmpty()) {
            list.addView(TextView(context).apply {
                text = "Bu turda yozuv yoʻq."
                textSize = 14f
                setTextColor(Theme.col(context, R.color.kb_hint))
                setPadding(dp(16), dp(24), dp(16), dp(16))
            })
            return
        }
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = if (access) {
                    "Hozircha nusxa yoʻq. Biror matnni nusxalasangiz, shu yerda paydo boʻladi."
                } else {
                    "Tizim buferga ruxsat bermadi. Sozlamalarda Alifbo klaviaturasi tanlanganini tekshiring " +
                        "(Sozlamalar → Umumiy boshqaruv → Klaviatura roʻyxati)."
                }
                textSize = 14f
                setTextColor(Theme.col(context, R.color.kb_hint))
                setPadding(dp(16), dp(24), dp(16), dp(16))
            })
            return
        }
        for (item in items) {
            val tv = TextView(context).apply {
                text = if (item.pinned) "📌 " + item.text else item.text
                textSize = 15f
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(Theme.col(context, R.color.kb_text))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Theme.col(context, R.color.kb_key))
                    cornerRadius = dp(10).toFloat()
                }
                setOnClickListener { listener?.onPaste(item.text) }
                setOnLongClickListener {
                    listener?.onTogglePin(item.text)
                    true
                }
            }
            val lp = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            lp.setMargins(dp(8), dp(3), dp(8), dp(3))
            list.addView(tv, lp)
        }
    }
}
