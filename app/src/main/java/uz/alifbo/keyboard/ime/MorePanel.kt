package uz.alifbo.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import uz.alifbo.keyboard.R

/** Asboblar qatoridagi «…» menyusi: matn tahrirlash, bir qoʻl rejimi, mavzu, tovush va boshqalar. */
class MorePanel(context: Context) : LinearLayout(context) {

    companion object {
        const val SELECT_ALL = 0
        const val COPY = 1
        const val CUT = 2
        const val PASTE = 3
        const val CURSOR_LEFT = 4
        const val CURSOR_RIGHT = 5
        const val CLEAR = 6
        const val ONEHAND = 7
        const val THEME = 8
        const val SOUND = 9
        const val SETTINGS = 10
        const val HIDE = 11
        const val CONVERT = 12
    }

    interface Listener {
        fun onMore(id: Int)
        fun onBack()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }

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
            setOnClickListener { listener?.onBack() }
        }, LayoutParams(dp(64), dp(40)))
        header.addView(TextView(context).apply {
            text = "Asboblar"
            textSize = 13f
            setTextColor(context.getColor(R.color.kb_hint))
        }, LayoutParams(0, dp(40), 1f))
        addView(header, LayoutParams(MATCH_PARENT, dp(40)))

        val sv = ScrollView(context)
        sv.addView(grid)
        addView(sv, LayoutParams(MATCH_PARENT, 0, 1f))
    }

    /** Panel ochilganda: holat yozuvlari yangilanadi. */
    fun show(oneHand: Int, theme: Int, sound: Boolean) {
        grid.removeAllViews()
        val oh = when (oneHand) { 1 -> "chap"; 2 -> "oʻng"; else -> "oʻchiq" }
        val th = when (theme) { 1 -> "och"; 2 -> "toʻq"; else -> "tizim" }
        val tiles = listOf(
            Tile(SELECT_ALL, "✅", "Hammasini tanlash"),
            Tile(COPY, "📄", "Nusxa"),
            Tile(CUT, "✂️", "Kesish"),
            Tile(PASTE, "📥", "Qoʻyish"),
            Tile(CURSOR_LEFT, "◀️", "Kursor ←"),
            Tile(CURSOR_RIGHT, "▶️", "Kursor →"),
            Tile(CLEAR, "🗑️", "Hammasini oʻchirish"),
            Tile(CONVERT, "🔤", "Alifbo oʻgiruvchi"),
            Tile(ONEHAND, "📱", "Bir qoʻl: $oh"),
            Tile(THEME, "🎨", "Mavzu: $th"),
            Tile(SOUND, if (sound) "🔊" else "🔈", "Tovush: " + if (sound) "yoniq" else "oʻchiq"),
            Tile(SETTINGS, "⚙️", "Sozlamalar"),
            Tile(HIDE, "⌨️", "Yopish")
        )
        val perRow = 4
        for (chunk in tiles.chunked(perRow)) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (t in chunk) row.addView(tile(t), LayoutParams(0, dp(66), 1f).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) })
            repeat(perRow - chunk.size) { row.addView(android.view.View(context), LayoutParams(0, dp(66), 1f)) }
            grid.addView(row, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
    }

    private class Tile(val id: Int, val icon: String, val label: String)

    private fun tile(t: Tile): LinearLayout {
        val v = LinearLayout(context)
        v.orientation = VERTICAL
        v.gravity = Gravity.CENTER
        v.setPadding(dp(2), dp(4), dp(2), dp(4))
        v.background = GradientDrawable().apply {
            setColor(context.getColor(R.color.kb_key))
            cornerRadius = dp(12).toFloat()
        }
        v.addView(TextView(context).apply {
            text = t.icon
            textSize = 20f
            gravity = Gravity.CENTER
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        v.addView(TextView(context).apply {
            text = t.label
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 2
            setTextColor(context.getColor(R.color.kb_text))
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        v.setOnClickListener { listener?.onMore(t.id) }
        return v
    }
}
