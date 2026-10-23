package uz.alifbo.keyboard.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import uz.alifbo.keyboard.data.ClipStore
import uz.alifbo.keyboard.data.PackStore
import uz.alifbo.keyboard.data.Predictor
import uz.alifbo.keyboard.data.Prefs
import uz.alifbo.keyboard.data.RemoteSync
import uz.alifbo.keyboard.ime.Theme
import uz.alifbo.keyboard.update.Updater
import java.text.DateFormat
import java.util.Date

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var packsBox: LinearLayout
    private lateinit var syncInfo: TextView
    private lateinit var updateInfo: TextView
    private lateinit var updateBtn: Button
    private lateinit var updateCheckBtn: Button
    private var release: Updater.Release? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val scroll = ScrollView(this).apply {
            addView(root)
            clipToPadding = false
            setOnApplyWindowInsetsListener { v, ins ->
                val top: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= 30) {
                    val s = ins.getInsets(WindowInsets.Type.systemBars())
                    top = s.top
                    bottom = s.bottom
                } else {
                    @Suppress("DEPRECATION")
                    top = ins.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    bottom = ins.systemWindowInsetBottom
                }
                v.setPadding(0, top, 0, bottom)
                ins
            }
        }
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        root.addView(title("Alifbo Klaviatura"))
        root.addView(body("Yangi oʻzbek lotin alifbosi (Ş Ç Ö Ğ ʼ), eski lotin, kirill va boshqa tillar. Alifbo oʻzgarsa, ilova oʻzi yangilanadi."))

        // ---- Ilova yangilanishi (GitHub Releases) ----
        updateInfo = body("Versiya ${Updater.installedVersion(this)}")
        root.addView(updateInfo)
        updateBtn = button("Yangilash") { startInstall() }.apply { visibility = android.view.View.GONE }
        root.addView(updateBtn)
        updateCheckBtn = button("Yangilanishni tekshirish") { checkUpdate(true) }
        root.addView(updateCheckBtn)
        root.addView(CheckBox(this).apply {
            text = "Yangi versiya chiqsa, bildirishnoma yuborish"
            isChecked = prefs.autoUpdateCheck
            setOnCheckedChangeListener { _, on -> prefs.autoUpdateCheck = on }
        })

        status = body("")
        root.addView(status)
        root.addView(button("1. Klaviaturani yoqish") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("2. Klaviaturani tanlash") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(EditText(this).apply {
            hint = "Shu yerda sinab koʻring"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        })

        root.addView(section("Tillar (alifbolar)"))
        packsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(packsBox)

        root.addView(section("Klaviatura"))
        root.addView(CheckBox(this).apply {
            text = "Tugma bosilganda tebranish"
            isChecked = prefs.haptic
            setOnCheckedChangeListener { _, on -> prefs.haptic = on }
        })
        root.addView(CheckBox(this).apply {
            text = "Tugma bosilganda harfni tepada koʻrsatish"
            isChecked = prefs.keyPreview
            setOnCheckedChangeListener { _, on -> prefs.keyPreview = on }
        })
        root.addView(CheckBox(this).apply {
            text = "Surib yozish (harflar ustidan barmoqni surib soʻz yozish)"
            isChecked = prefs.swipe
            setOnCheckedChangeListener { _, on -> prefs.swipe = on }
        })
        root.addView(CheckBox(this).apply {
            text = "Tugma bosilganda tovush"
            isChecked = prefs.sound
            setOnCheckedChangeListener { _, on -> prefs.sound = on }
        })
        root.addView(body("Tovush balandligi (media ovozi bilan)"))
        root.addView(SeekBar(this).apply {
            max = 95
            progress = prefs.soundVolume - 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) prefs.soundVolume = 5 + p
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
        root.addView(CheckBox(this).apply {
            text = "Ikki marta boʻsh joy — nuqta va boʻsh joy"
            isChecked = prefs.doubleSpacePeriod
            setOnCheckedChangeListener { _, on -> prefs.doubleSpacePeriod = on }
        })
        root.addView(CheckBox(this).apply {
            text = "Doimiy raqamlar qatori"
            isChecked = prefs.numberRow
            setOnCheckedChangeListener { _, on -> prefs.numberRow = on }
        })
        root.addView(CheckBox(this).apply {
            text = "Soʻz tavsiyalari (yozgan soʻzlaringizdan oʻrganadi, faqat shu telefonda)"
            isChecked = prefs.suggestions
            setOnCheckedChangeListener { _, on -> prefs.suggestions = on }
        })
        root.addView(button("Oʻrganilgan soʻzlarni oʻchirish") {
            Predictor.clear(this)
            Toast.makeText(this, "Oʻchirildi", Toast.LENGTH_SHORT).show()
        })
        root.addView(button("Bufer tarixini oʻchirish") {
            ClipStore.clearAll(this)
            Toast.makeText(this, "Oʻchirildi", Toast.LENGTH_SHORT).show()
        })
        root.addView(section("Koʻrinish"))
        root.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            val names = listOf("Tizim", "Och", "Toʻq")
            for ((i, n) in names.withIndex()) {
                addView(RadioButton(context).apply {
                    id = 1000 + i
                    text = n
                    setPadding(0, 0, dp(16), 0)
                })
            }
            check(1000 + prefs.theme)
            setOnCheckedChangeListener { _, id -> prefs.theme = id - 1000 }
        })
        root.addView(body("Urgʻu rangi"))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val dots = ArrayList<android.view.View>()
            fun paint() {
                for ((i, d) in dots.withIndex()) {
                    val col = Theme.ACCENTS[i].let { if (it == 0) 0xFF1A5FE0.toInt() else it }
                    d.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(col)
                        if (prefs.accent == Theme.ACCENTS[i]) setStroke(dp(3), 0xFF888888.toInt())
                    }
                }
            }
            for (a in Theme.ACCENTS) {
                val d = android.view.View(context)
                dots.add(d)
                d.setOnClickListener {
                    prefs.accent = a
                    paint()
                }
                addView(d, LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(0, dp(4), dp(12), dp(4)) })
            }
            paint()
        })
        root.addView(body("Balandlik"))
        root.addView(SeekBar(this).apply {
            max = 50
            progress = prefs.heightPercent - 80
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) prefs.heightPercent = 80 + p
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })

        root.addView(section("Yangilanishlar"))
        syncInfo = body("")
        root.addView(syncInfo)
        val urlField = EditText(this).apply {
            setText(prefs.registryUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://.../registry.json"
            textSize = 13f
        }
        root.addView(urlField)
        root.addView(button("Manzilni saqlash") {
            prefs.registryUrl = urlField.text.toString()
            renderSync()
        })
        val syncBtn = button("Hozir yangilash") {}
        syncBtn.setOnClickListener {
            syncBtn.isEnabled = false
            syncInfo.text = "Yangilanmoqda…"
            Thread {
                RemoteSync.syncBlocking(applicationContext)
                runOnUiThread {
                    syncBtn.isEnabled = true
                    renderPacks()
                    renderSync()
                }
            }.start()
        }
        root.addView(syncBtn)

        if (prefs.autoUpdateCheck) checkUpdate(false)
    }

    private fun checkUpdate(manual: Boolean) {
        if (manual) {
            updateInfo.text = "Tekshirilmoqda…"
            updateCheckBtn.isEnabled = false
        }
        Thread {
            var rel: Updater.Release? = null
            var err: String? = null
            try {
                rel = Updater.checkNewer(applicationContext)
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            }
            runOnUiThread {
                updateCheckBtn.isEnabled = true
                release = rel
                prefs.updateVersion = rel?.version ?: ""
                val cur = Updater.installedVersion(this)
                when {
                    rel != null -> {
                        val notes = rel.notes.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") }.orEmpty()
                        updateInfo.text = "Yangi versiya bor: v${rel.version} (hozir v$cur)" +
                            if (notes.isNotEmpty()) "\n$notes" else ""
                        updateBtn.visibility = android.view.View.VISIBLE
                        updateBtn.isEnabled = true
                        updateBtn.text = "Yuklab olish va oʻrnatish (v${rel.version})"
                    }
                    err != null -> {
                        if (manual) updateInfo.text = "Versiya v$cur. Tekshirib boʻlmadi: $err"
                    }
                    else -> {
                        updateBtn.visibility = android.view.View.GONE
                        updateInfo.text = "Versiya v$cur — eng yangisi ✓"
                    }
                }
            }
        }.start()
    }

    private fun startInstall() {
        val rel = release ?: return
        if (!Updater.canInstall(this)) {
            Toast.makeText(this, "Alifbo uchun «Notanish ilovalarni oʻrnatish» ruxsatini yoqing, keyin yana bosing", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        updateBtn.isEnabled = false
        updateInfo.text = "Yuklanmoqda… 0%"
        Thread {
            val err = Updater.downloadAndInstall(applicationContext, rel) { pct ->
                runOnUiThread { updateInfo.text = "Yuklanmoqda… $pct%" }
            }
            runOnUiThread {
                if (err != null) {
                    updateInfo.text = "Yangilanmadi: $err"
                    updateBtn.isEnabled = true
                } else {
                    updateInfo.text = "Yuklandi. Oʻrnatishni tasdiqlang…"
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderPacks()
        renderSync()
    }

    private fun renderStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith(packageName) == true
        status.text = when {
            current -> "Holat: yoqilgan va tanlangan ✓"
            enabled -> "Holat: yoqilgan, lekin hozir tanlanmagan — 2-tugmani bosing"
            else -> "Holat: hali yoqilmagan — 1-tugmani bosing"
        }
    }

    private fun renderPacks() {
        packsBox.removeAllViews()
        val all = PackStore.all(this)
        val enabled = prefs.enabledIds.toSet()
        for (p in all) {
            val cb = CheckBox(this)
            val eff = if (p.effective != null) " · ${p.effective} dan" else ""
            cb.text = "${p.name}  (v${p.version}$eff)"
            cb.isChecked = p.id in enabled
            cb.setOnCheckedChangeListener { _, on ->
                val cur = prefs.enabledIds.toMutableList()
                if (on) {
                    if (p.id !in cur) cur.add(p.id)
                } else {
                    cur.remove(p.id)
                }
                val ordered = all.map { it.id }.filter { it in cur }
                if (ordered.isEmpty()) {
                    cb.isChecked = true // kamida bitta til qolishi kerak
                } else {
                    prefs.enabledIds = ordered
                }
            }
            packsBox.addView(cb)
        }
    }

    private fun renderSync() {
        val t = prefs.lastSyncMs
        syncInfo.text = if (t == 0L) {
            "Hali tekshirilmagan. Ilova har 6 soatda va klaviatura ochilganda oʻzi tekshiradi."
        } else {
            "Oxirgi tekshiruv: ${DateFormat.getDateTimeInstance().format(Date(t))}\n${prefs.lastSyncInfo}"
        }
    }

    // ---- kichik UI yordamchilari ----

    private fun title(t: String) = TextView(this).apply {
        text = t
        textSize = 24f
        setPadding(0, 0, 0, dp(8))
    }

    private fun section(t: String) = TextView(this).apply {
        text = t
        textSize = 17f
        setPadding(0, dp(24), 0, dp(6))
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }
}
