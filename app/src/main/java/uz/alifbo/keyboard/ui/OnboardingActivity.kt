package uz.alifbo.keyboard.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import uz.alifbo.keyboard.data.Prefs
import uz.alifbo.keyboard.data.Recents
import uz.alifbo.keyboard.ime.Theme

/**
 * Birinchi ishga tushirish ko'rsatmasi: 1) klaviaturani yoqish, 2) tanlash, 3) uslub, 4) qo'shimcha imkoniyatlar.
 * Bosqichlar bajarilgach, belgi (✓) qo'yiladi; "Tayyor" bosilsa, bu sahifa boshqa chiqmaydi.
 */
class OnboardingActivity : Activity() {

    companion object {
        /** Klaviatura yoqilgan va tanlangan boʻlsa — ko'rsatma kerak emas. */
        fun isReady(ctx: Context): Boolean = isEnabled(ctx) && isCurrent(ctx)

        fun isEnabled(ctx: Context): Boolean {
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            return imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
        }

        fun isCurrent(ctx: Context): Boolean =
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(ctx.packageName) == true
    }

    private lateinit var prefs: Prefs
    private lateinit var step1: TextView
    private lateinit var step2: TextView
    private lateinit var done: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
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

        root.addView(text("Alifbo klaviaturasiga xush kelibsiz", 24f, dp(4)))
        root.addView(text("Uch qadamda tayyor bo‘ladi.", 15f, dp(16)))

        step1 = text("", 17f, dp(4))
        root.addView(step1)
        root.addView(button("Sozlamalarni ochish") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        root.addView(text("Ro‘yxatda «Alifbo» ni yoqing va ogohlantirishda «OK» ni bosing.", 13f, dp(12)))

        step2 = text("", 17f, dp(4))
        root.addView(step2)
        root.addView(button("Klaviaturani tanlash") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        root.addView(text("3. Uslubni tanlang", 17f, dp(4)).apply { setPadding(0, dp(20), 0, dp(4)) })
        root.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            for (st in Theme.STYLES) addView(RadioButton(context).apply { id = 3000 + st.id; text = st.name })
            check(3000 + prefs.stylePreset)
            setOnCheckedChangeListener { _, id ->
                val st = Theme.STYLES[id - 3000]
                prefs.applyStylePreset(st.id, st.radius, st.gap, st.textScale, st.shadow, st.heightPct)
            }
        })
        root.addView(text("Keyinroq Sozlamalarda burchak, masofa, rang va boshqalarni o‘zgartirish mumkin.", 13f, dp(8)))

        root.addView(text("4. Qo‘shimcha (ixtiyoriy)", 17f, dp(4)).apply { setPadding(0, dp(20), 0, dp(4)) })
        root.addView(check("Keyingi so‘z bashorati", prefs.nextWord) { prefs.nextWord = it })
        root.addView(check("Aralash tilda yozish (lotinda inglizcha, kirillda ruscha tavsiyalar)", prefs.mixedLang) { prefs.mixedLang = it })
        root.addView(check("Oxirgi nusxalangan matnni tavsiya qilish", prefs.recentClipChip) { prefs.recentClipChip = it })
        root.addView(check("Oxirgi skrinshotni tavsiya qilish (rasmlarga kirish ruxsati so‘raladi)", prefs.recentScreenshotChip) { on ->
            prefs.recentScreenshotChip = on
            if (on && !Recents.hasImageAccess(this)) {
                val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
                else android.Manifest.permission.READ_EXTERNAL_STORAGE
                requestPermissions(arrayOf(perm), 2)
            }
        })

        root.addView(text("Shu yerda sinab ko‘ring", 15f, dp(4)).apply { setPadding(0, dp(20), 0, dp(4)) })
        root.addView(EditText(this).apply { hint = "Matn yozing…"; minLines = 2 })

        done = button("Tayyor") {
            prefs.onboarded = true
            finish()
        }
        root.addView(done, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
    }

    override fun onResume() {
        super.onResume()
        val e = isEnabled(this)
        val c = isCurrent(this)
        step1.text = (if (e) "✓ " else "1. ") + "Klaviaturani yoqing"
        step2.text = (if (c) "✓ " else "2. ") + "Klaviaturani tanlang"
        done.text = if (e && c) "Tayyor" else "Keyinroq"
    }

    private fun text(t: String, size: Float, bottom: Int) = TextView(this).apply {
        text = t
        textSize = size
        setPadding(0, dp(4), 0, bottom)
    }

    private fun check(label: String, value: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = label
        isChecked = value
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(0xFF1A5FE0.toInt())
        }
        setTextColor(0xFFFFFFFF.toInt())
        setOnClickListener { onClick() }
    }
}
