package uz.alifbo.keyboard.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import uz.alifbo.keyboard.data.AlphabetTools
import uz.alifbo.keyboard.data.Prefs
import uz.alifbo.keyboard.data.TessOcr
import uz.alifbo.keyboard.data.Translator

/**
 * Kameradan (yoki galereyadan) matnni oʻqish. Hammasi telefonning oʻzida bajariladi — rasm internetga chiqmaydi.
 * Natija VoiceBridge orqali klaviaturaga qaytadi (klaviatura qayta ochilganda maydonga qoʻyiladi).
 */
class ScanActivity : ComponentActivity() {

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    private lateinit var previewView: PreviewView
    private lateinit var cameraLayer: FrameLayout
    private lateinit var resultLayer: LinearLayout
    private lateinit var resultText: EditText
    private lateinit var busy: ProgressBar
    private lateinit var hint: TextView
    private val modeChips = ArrayList<TextView>()
    private val layoutChips = ArrayList<TextView>()
    private val scriptChips = ArrayList<TextView>()
    private lateinit var resultTitle: TextView

    /** Oxirgi surat (qaddi rost): yozuv turi almashtirilsa, qayta suratga olmasdan qayta oʻqiladi. */
    private var lastBitmap: Bitmap? = null
    private var scriptMode = 0 // 0 - lotin (ML Kit), 1 - kirill (oʻzbek), 2 - kirill (rus) (Tesseract)

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var torch = false

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Aniqlangan xom natija. */
    private var rawLines: List<String> = emptyList()
    private var rawBlocks: List<String> = emptyList()
    private var alphabet = 0 // 0 - asl, 1 - 1995 (oʻ, gʻ), 2 - yangi (ö, ğ, ş, ç)
    private var paragraphs = true
    private var packTag = ""
    private var translated = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) recognizeUri(uri)
    }
    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else hint.text = "Kameraga ruxsat berilmadi. Galereyadan rasm tanlashingiz mumkin."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packTag = intent.getStringExtra("pack") ?: ""
        scriptMode = when {
            packTag.startsWith("uz-cyrl") -> 1
            packTag.startsWith("ru") -> 2
            else -> 0
        }
        alphabet = when (packTag) {
            "uz-latn-2026" -> 2
            "uz-latn-1995" -> 1
            else -> 0
        }
        setContentView(buildUi())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            // yopilgan
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // --- kamera qatlami ---
        cameraLayer = FrameLayout(this)
        previewView = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        cameraLayer.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(28), dp(8), dp(8))
            setBackgroundColor(0x66000000)
        }
        top.addView(iconBtn("✕") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        hint = TextView(this).apply {
            text = "Matnni ramkaga oling va tugmani bosing"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        top.addView(hint, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        top.addView(iconBtn("🔦") {
            torch = !torch
            camera?.cameraControl?.enableTorch(torch)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        cameraLayer.addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        val bottom = FrameLayout(this).apply { setBackgroundColor(0x66000000) }
        val shutter = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(4), 0xFF9AA0A6.toInt())
            }
            setOnClickListener { capture() }
        }
        bottom.addView(shutter, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER).apply { setMargins(0, dp(16), 0, dp(24)) })
        bottom.addView(iconBtn("🖼") { pickImage.launch("image/*") },
            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER_VERTICAL or Gravity.START).apply { leftMargin = dp(24) })
        cameraLayer.addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(cameraLayer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        busy = ProgressBar(this).apply { visibility = View.GONE }

        // --- natija qatlami ---
        resultLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF17191D.toInt())
            setPadding(dp(12), dp(36), dp(12), dp(12))
            visibility = View.GONE
        }
        resultTitle = label("Aniqlangan matn (tahrirlash mumkin)", 13f, 0xFF9AA0A6.toInt())
        resultLayer.addView(resultTitle)

        val sv = ScrollView(this)
        resultText = EditText(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.TOP
            setBackgroundColor(0xFF2A2C30.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minLines = 6
        }
        sv.addView(resultText, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        resultLayer.addView(sv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { setMargins(0, dp(6), 0, dp(8)) })

        resultLayer.addView(label("Matn yozuvi (rasmda)", 12f, 0xFF9AA0A6.toInt()))
        val row0 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, n) in listOf("Lotin", "Kirill (oʻzbek)", "Kirill (rus)").withIndex()) {
            val c = chip(n) {
                if (scriptMode != i) {
                    scriptMode = i
                    if (lastBitmap != null) runOcr() else paintChips()
                }
            }
            scriptChips.add(c)
            row0.addView(c, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)).apply { setMargins(0, dp(4), dp(6), dp(4)) })
        }
        resultLayer.addView(row0)
        resultLayer.addView(label("Natija yozuvi", 12f, 0xFF9AA0A6.toInt()))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, n) in listOf("Asl holicha", "Oʻ, Gʻ (1995)", "Yangi alifbo (Ö Ğ Ş Ç)").withIndex()) {
            val c = chip(n) { alphabet = i; refreshResult() }
            modeChips.add(c)
            row1.addView(c, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)).apply { setMargins(0, dp(4), dp(6), dp(4)) })
        }
        resultLayer.addView(row1)
        resultLayer.addView(label("Qatorlar", 12f, 0xFF9AA0A6.toInt()))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, n) in listOf("Abzatslarga birlashtirish", "Qatorlar boricha").withIndex()) {
            val c = chip(n) { paragraphs = (i == 0); refreshResult() }
            layoutChips.add(c)
            row2.addView(c, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)).apply { setMargins(0, dp(4), dp(6), dp(4)) })
        }
        resultLayer.addView(row2)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(pill("Qayta", false) { showCamera() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, dp(10), dp(3), 0) })
        actions.addView(pill("Nusxa", false) { copy() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(3), dp(10), dp(3), 0) })
        actions.addView(pill("Tarjima", false) { chooseTranslation() }, LinearLayout.LayoutParams(0, dp(46), 1.1f).apply { setMargins(dp(3), dp(10), dp(3), 0) })
        actions.addView(pill("Qoʻyish", true) { insert() }, LinearLayout.LayoutParams(0, dp(46), 1.3f).apply { setMargins(dp(3), dp(10), 0, 0) })
        resultLayer.addView(actions)
        root.addView(resultLayer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(busy, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))

        return root
    }

    private fun iconBtn(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setOnClickListener { onClick() }
    }

    private fun label(t: String, size: Float, color: Int) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        setPadding(0, dp(6), 0, 0)
    }

    private fun chip(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { onClick() }
    }

    private fun pill(t: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(23).toFloat()
            setColor(if (primary) 0xFF4A6CF7.toInt() else 0xFF3A3D42.toInt())
        }
        setOnClickListener { onClick() }
    }

    private fun paintChips() {
        for ((i, c) in modeChips.withIndex()) styleChip(c, i == alphabet)
        for ((i, c) in layoutChips.withIndex()) styleChip(c, (i == 0) == paragraphs)
        for ((i, c) in scriptChips.withIndex()) styleChip(c, i == scriptMode)
    }

    private fun styleChip(c: TextView, on: Boolean) {
        c.setTextColor(Color.WHITE)
        c.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (on) 0xFF4A6CF7.toInt() else 0xFF3A3D42.toInt())
        }
    }

    // ---------------------------------------------------------------- kamera

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture
            } catch (e: Exception) {
                hint.text = "Kamerani ochib boʻlmadi. Galereyadan rasm tanlang."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        val cap = imageCapture ?: run {
            Toast.makeText(this, "Kamera tayyor emas", Toast.LENGTH_SHORT).show()
            return
        }
        busy.visibility = View.VISIBLE
        cap.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    processBitmap(upright(image.toBitmap(), image.imageInfo.rotationDegrees))
                } catch (e: Exception) {
                    busy.visibility = View.GONE
                    Toast.makeText(this@ScanActivity, "Suratni oʻqib boʻlmadi", Toast.LENGTH_SHORT).show()
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                busy.visibility = View.GONE
                Toast.makeText(this@ScanActivity, "Suratga olib boʻlmadi", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun recognizeUri(uri: Uri) {
        busy.visibility = View.VISIBLE
        Thread {
            val bmp = try {
                decodeUri(uri)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (bmp == null) {
                    busy.visibility = View.GONE
                    Toast.makeText(this, "Rasmni ochib boʻlmadi", Toast.LENGTH_SHORT).show()
                } else {
                    processBitmap(bmp)
                }
            }
        }.start()
    }

    /** Suratni aylantiradi (deg) va juda katta boʻlsa kichraytiradi. */
    private fun upright(src: Bitmap, deg: Int): Bitmap {
        val m = Matrix()
        if (deg != 0) m.postRotate(deg.toFloat())
        val side = maxOf(src.width, src.height)
        if (side > 2400) {
            val k = 2400f / side
            m.postScale(k, k)
        }
        return if (m.isIdentity) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun decodeUri(uri: Uri): Bitmap? {
        val cr = contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } ?: 1
        } catch (e: Exception) {
            1
        }
        val deg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        return upright(bmp, deg)
    }

    // ---------------------------------------------------------------- OCR

    private fun processBitmap(bmp: Bitmap) {
        lastBitmap = bmp
        runOcr()
    }

    /** Tanlangan yozuv turiga qarab: lotin — ML Kit, kirill — Tesseract (til paketi kerak boʻlsa yuklanadi). */
    private fun runOcr() {
        val bmp = lastBitmap ?: return
        busy.visibility = View.VISIBLE
        if (scriptMode == 0) {
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { text -> onRecognized(text) }
                .addOnFailureListener {
                    Toast.makeText(this, "Matnni aniqlab boʻlmadi", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener { busy.visibility = View.GONE }
            return
        }
        val lang = if (scriptMode == 1) TessOcr.UZ_CYRL else TessOcr.RUS
        Thread {
            var err: String? = null
            if (!TessOcr.hasLang(this, lang)) {
                err = TessOcr.download(this, lang) { p -> runOnUiThread { resultTitle.text = "Til paketi yuklanmoqda… $p%" } }
            }
            val text = if (err == null) {
                try {
                    TessOcr.recognize(this, bmp, lang)
                } catch (e: Throwable) {
                    err = "Matnni aniqlab boʻlmadi"
                    ""
                }
            } else {
                ""
            }
            runOnUiThread {
                busy.visibility = View.GONE
                resultTitle.text = "Aniqlangan matn (tahrirlash mumkin)"
                if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show() else onTessText(text)
            }
        }.start()
    }

    private fun onTessText(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "Matn topilmadi. Yozuv turini (Lotin/Kirill) tekshiring.", Toast.LENGTH_LONG).show()
            return
        }
        rawLines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        rawBlocks = text.split(Regex("\\n\\s*\\n")).map { AlphabetTools.reflow(it.lines()) }.filter { it.isNotBlank() }
        cameraLayer.visibility = View.GONE
        resultLayer.visibility = View.VISIBLE
        refreshResult()
    }

    private fun onRecognized(text: Text) {
        if (text.text.isBlank()) {
            Toast.makeText(this, "Matn topilmadi. Yaxshiroq yoritib, yaqinroq oling (yozuv kirill boʻlsa — «Kirill» ni tanlang).", Toast.LENGTH_LONG).show()
            return
        }
        rawLines = text.textBlocks.flatMap { b -> b.lines.map { it.text } }
        rawBlocks = text.textBlocks.map { b -> AlphabetTools.reflow(b.lines.map { it.text }) }
        cameraLayer.visibility = View.GONE
        resultLayer.visibility = View.VISIBLE
        refreshResult()
    }

    // ---------------------------------------------------------------- tarjima

    private fun sourceLang(): String = when {
        packTag.startsWith("ru") -> "ru"
        packTag.startsWith("en") -> "en"
        packTag.startsWith("tr") -> "tr"
        else -> "uz"
    }

    private fun chooseTranslation() {
        val text = resultText.text.toString()
        if (text.isBlank()) return
        val prefs = Prefs(this)
        if (!prefs.translateConsent) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Tarjima")
                .setMessage("Tarjima uchun matn tashqi xizmatga (mymemory.translated.net) yuboriladi. Davom etasizmi?")
                .setPositiveButton("Roziman") { _, _ ->
                    prefs.translateConsent = true
                    chooseTranslation()
                }
                .setNegativeButton("Bekor", null)
                .show()
            return
        }
        val langs = Translator.LANGS
        android.app.AlertDialog.Builder(this)
            .setTitle("Qaysi tilga?")
            .setItems(langs.map { it.name }.toTypedArray()) { _, i ->
                val to = langs[i].code
                prefs.translateTo = to
                runTranslation(text, sourceLang(), to)
            }
            .show()
    }

    private fun runTranslation(text: String, from: String, to: String) {
        busy.visibility = View.VISIBLE
        Translator.translate(text, from, to) { out, err ->
            busy.visibility = View.GONE
            if (out != null) {
                translated = true
                resultText.setText(out)
            } else {
                Toast.makeText(this, err ?: "Tarjima qilib boʻlmadi", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshResult() {
        var t = if (paragraphs) rawBlocks.joinToString("\n\n") else rawLines.joinToString("\n")
        t = when {
            scriptMode == 2 -> t // rus tili: oʻgirilmaydi
            scriptMode == 1 -> when (alphabet) {
                1 -> AlphabetTools.normalize(AlphabetTools.cyrToLat(t))
                2 -> AlphabetTools.toNew(AlphabetTools.cyrToLat(t))
                else -> t
            }
            alphabet == 1 -> AlphabetTools.normalize(t)
            alphabet == 2 -> AlphabetTools.toNew(t)
            else -> t
        }
        resultText.setText(t)
        paintChips()
    }

    private fun showCamera() {
        resultLayer.visibility = View.GONE
        cameraLayer.visibility = View.VISIBLE
    }

    private fun copy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("skaner", resultText.text.toString()))
        Toast.makeText(this, "Nusxalandi", Toast.LENGTH_SHORT).show()
    }

    private fun insert() {
        val t = resultText.text.toString()
        if (t.isNotBlank()) VoiceBridge.pending = t
        finish()
    }
}
