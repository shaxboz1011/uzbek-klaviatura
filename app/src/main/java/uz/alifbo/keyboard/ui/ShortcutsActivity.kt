package uz.alifbo.keyboard.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import uz.alifbo.keyboard.data.ShortcutLogic
import uz.alifbo.keyboard.data.Shortcuts

/** Matn qisqartmalarini boshqarish: ro'yxat, qo'shish, o'chirish, namunalar. */
class ShortcutsActivity : Activity() {

    private lateinit var listBox: LinearLayout
    private lateinit var abbr: EditText
    private lateinit var text: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply {
            addView(root)
            clipToPadding = false
            setOnApplyWindowInsetsListener { v, ins ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val s = ins.getInsets(WindowInsets.Type.systemBars())
                    v.setPadding(0, s.top, 0, s.bottom)
                }
                ins
            }
        })

        root.addView(label("Matn qisqartmalari", 24f))
        root.addView(label(
            "Qisqartmani yozib boʻsh joy bosing — toʻliq matn chiqadi. Bekor qilish: darrov ⌫.\n" +
                "Matn ichida {sana}, {vaqt}, {hafta} ishlatish mumkin (kiritilgan paytdagi sana, vaqt va hafta kuni).",
            14f
        ))

        root.addView(label("Yangi qisqartma", 17f).apply { setPadding(0, dp(20), 0, dp(4)) })
        abbr = EditText(this).apply {
            hint = "Qisqartma (masalan: mnm)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        text = EditText(this).apply {
            hint = "Toʻliq matn (masalan: Menga ham kerak)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        root.addView(abbr)
        root.addView(text)
        root.addView(button("Qoʻshish") { add() })
        root.addView(button("Namunalarni qoʻshish") {
            for (s in ShortcutLogic.SAMPLES) Shortcuts.put(this, s.abbr, s.text)
            render()
        })

        root.addView(label("Mavjud qisqartmalar", 17f).apply { setPadding(0, dp(20), 0, dp(4)) })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)
        render()
    }

    private fun add() {
        val a = ShortcutLogic.normalizeAbbr(abbr.text.toString())
        when {
            a == null -> Toast.makeText(this, "Qisqartma 1–16 ta harfdan iborat boʻlsin (raqam va boʻsh joysiz)", Toast.LENGTH_LONG).show()
            !Shortcuts.put(this, a, text.text.toString()) -> Toast.makeText(this, "Matn boʻsh yoki juda uzun, yoki joy toʻlgan", Toast.LENGTH_LONG).show()
            else -> {
                abbr.setText("")
                text.setText("")
                render()
            }
        }
    }

    private fun render() {
        listBox.removeAllViews()
        val items = Shortcuts.list(this)
        if (items.isEmpty()) {
            listBox.addView(label("Hozircha qisqartma yoʻq.", 14f))
            return
        }
        for (s in items.sortedBy { it.abbr }) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                this.text = "${s.abbr}  →  ${s.text}"
                textSize = 15f
                setPadding(0, dp(6), dp(8), dp(6))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                this.text = "Oʻchirish"
                isAllCaps = false
                setOnClickListener {
                    Shortcuts.remove(this@ShortcutsActivity, s.abbr)
                    render()
                }
            })
            listBox.addView(row)
        }
    }

    private fun label(t: String, size: Float) = TextView(this).apply {
        this.text = t
        textSize = size
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        this.text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }
}
