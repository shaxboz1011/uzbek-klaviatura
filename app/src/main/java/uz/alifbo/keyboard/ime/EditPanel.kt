package uz.alifbo.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.TextView
import uz.alifbo.keyboard.R

/**
 * Matn tahrirlash paneli (Samsung "Matn tahrirlash" kabi): kursor strelkalari (bosib tursa — takrorlanadi), "Belgilash" rejimi,
 * soʻz boʻyicha/boshiga/oxiriga oʻtish, hammasini belgilash, kesish, nusxa, qoʻyish, bekor qilish/qaytarish.
 */
class EditPanel(context: Context) : LinearLayout(context) {

    companion object {
        const val SELECT_ALL = 0
        const val CUT = 1
        const val COPY = 2
        const val PASTE = 3
        const val UNDO = 4
        const val REDO = 5
    }

    interface Listener {
        /** Kursor/belgilash tugmasi: keyCode (KeyEvent.KEYCODE_*), ctrl — soʻz boʻyicha, shift — belgilash. */
        fun onEditKey(keyCode: Int, ctrl: Boolean, shift: Boolean)
        fun onEditAction(id: Int)
        fun onBack()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val handler = Handler(Looper.getMainLooper())
    private var selecting = false
    private lateinit var selectBtn: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.col(context, R.color.kb_bg))

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("ABC", Theme.accent(context), 14f).apply { setOnClickListener { listener?.onBack() } }, LayoutParams(dp(64), dp(40)))
        header.addView(label("Matn tahrirlash", Theme.col(context, R.color.kb_hint), 13f).apply { gravity = Gravity.CENTER_VERTICAL }, LayoutParams(0, dp(40), 1f))
        addView(header, LayoutParams(MATCH_PARENT, dp(40)))

        val body = LinearLayout(context).apply { orientation = HORIZONTAL; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        addView(body, LayoutParams(MATCH_PARENT, 0, 1f))

        // Chap: kursor krestovinasi
        val pad = LinearLayout(context).apply { orientation = VERTICAL }
        pad.addView(row(spacer(), repeatKey("▲", KeyEvent.KEYCODE_DPAD_UP), spacer()), rowLp())
        pad.addView(row(repeatKey("◀", KeyEvent.KEYCODE_DPAD_LEFT), selectToggle(), repeatKey("▶", KeyEvent.KEYCODE_DPAD_RIGHT)), rowLp())
        pad.addView(row(spacer(), repeatKey("▼", KeyEvent.KEYCODE_DPAD_DOWN), spacer()), rowLp())
        body.addView(pad, LayoutParams(0, MATCH_PARENT, 1f))

        // Oʻng: amallar
        val actions = LinearLayout(context).apply { orientation = VERTICAL }
        actions.addView(row(
            tile("Hammasi") { listener?.onEditAction(SELECT_ALL) },
            tile("Kesish") { listener?.onEditAction(CUT) },
            tile("Nusxa") { listener?.onEditAction(COPY) },
            tile("Qoʻyish") { listener?.onEditAction(PASTE) }
        ), rowLp())
        actions.addView(row(
            tile("Boshiga") { key(KeyEvent.KEYCODE_MOVE_HOME) },
            tile("Oxiriga") { key(KeyEvent.KEYCODE_MOVE_END) },
            tile("◀ soʻz") { key(KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true) },
            tile("soʻz ▶") { key(KeyEvent.KEYCODE_DPAD_RIGHT, ctrl = true) }
        ), rowLp())
        actions.addView(row(
            tile("Bekor") { listener?.onEditAction(UNDO) },
            tile("Qaytarish") { listener?.onEditAction(REDO) },
            tile("⌫") { key(KeyEvent.KEYCODE_DEL) },
            spacer()
        ), rowLp())
        body.addView(actions, LayoutParams(0, MATCH_PARENT, 1.6f))
    }

    private fun rowLp() = LayoutParams(MATCH_PARENT, 0, 1f)

    private fun row(vararg v: View) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        for (x in v) addView(x, LayoutParams(0, MATCH_PARENT, 1f))
    }

    private fun spacer() = View(context)

    private fun label(t: String, color: Int, size: Float) = TextView(context).apply {
        text = t
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(color)
    }

    private fun box(fill: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(10).toFloat()
    }

    private fun tile(t: String, onClick: () -> Unit) = label(t, Theme.col(context, R.color.kb_text), 13f).apply {
        background = box(Theme.col(context, R.color.kb_key))
        setOnClickListener { onClick() }
        layoutParams = LayoutParams(0, MATCH_PARENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    }

    private fun key(code: Int, ctrl: Boolean = false) = listener?.onEditKey(code, ctrl, selecting)

    private fun selectToggle(): TextView {
        selectBtn = label("Belgila", Theme.col(context, R.color.kb_text), 12f).apply {
            background = box(Theme.col(context, R.color.kb_key_special))
            layoutParams = LayoutParams(0, MATCH_PARENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            setOnClickListener {
                selecting = !selecting
                paintSelect()
            }
        }
        return selectBtn
    }

    private fun paintSelect() {
        selectBtn.background = box(if (selecting) Theme.accent(context) else Theme.col(context, R.color.kb_key_special))
        selectBtn.setTextColor(if (selecting) 0xFFFFFFFF.toInt() else Theme.col(context, R.color.kb_text))
    }

    /** Bosganda bir qadam, bosib tursa — tez takrorlanadi. */
    private fun repeatKey(t: String, code: Int): TextView {
        val v = label(t, Theme.col(context, R.color.kb_text), 20f)
        v.background = box(Theme.col(context, R.color.kb_key))
        v.layoutParams = LayoutParams(0, MATCH_PARENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        val rep = object : Runnable {
            override fun run() {
                key(code)
                handler.postDelayed(this, 60L)
            }
        }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    key(code)
                    handler.postDelayed(rep, 350L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(rep)
            }
            true
        }
        return v
    }

    /** Panel ochilganda: belgilash rejimi oʻchiq holatda boshlanadi. */
    fun reset() {
        selecting = false
        paintSelect()
    }
}
