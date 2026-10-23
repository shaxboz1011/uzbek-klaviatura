package uz.alifbo.keyboard.ime

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import uz.alifbo.keyboard.R
import uz.alifbo.keyboard.data.EmojiData

class EmojiPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmoji(e: String)
        fun onBack()
        fun onDelete()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val grid = GridView(context)
    private val tabs = LinearLayout(context)
    private val tabViews = ArrayList<TextView>()
    private var recents: List<String> = emptyList()
    private var current: List<String> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = current.size
        override fun getItem(position: Int): Any = current[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 26f
                layoutParams = AbsListView.LayoutParams(MATCH_PARENT, dp(44))
            }
            tv.text = current[position]
            return tv
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.kb_bg))

        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = dp(44)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            current.getOrNull(pos)?.let { listener?.onEmoji(it) }
        }
        addView(grid, LayoutParams(MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(label("ABC", 15f) { listener?.onBack() }, LayoutParams(dp(56), dp(44)))
        val hs = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }
        bar.addView(hs, LayoutParams(0, dp(44), 1f))
        bar.addView(label("⌫", 20f) { listener?.onDelete() }, LayoutParams(dp(56), dp(44)))
        addView(bar, LayoutParams(MATCH_PARENT, dp(44)))

        addTab("🕘", 0)
        for ((i, c) in EmojiData.categories.withIndex()) addTab(c.first, i + 1)
    }

    private fun label(t: String, size: Float, onClick: () -> Unit) = TextView(context).apply {
        text = t
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(context.getColor(R.color.kb_text))
        setOnClickListener { onClick() }
    }

    private fun addTab(symbol: String, index: Int) {
        val tv = label(symbol, 20f) { select(index) }
        tv.minWidth = dp(44)
        tabs.addView(tv, LayoutParams(dp(44), dp(44)))
        tabViews.add(tv)
    }

    private fun select(i: Int) {
        current = if (i == 0) recents else EmojiData.categories[i - 1].second
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        for ((k, tv) in tabViews.withIndex()) tv.alpha = if (k == i) 1f else 0.45f
    }

    /** Panel ochilganda chaqiriladi. */
    fun show(recent: List<String>) {
        recents = recent
        select(if (recent.isEmpty()) 1 else 0)
    }
}
