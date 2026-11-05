package uz.alifbo.keyboard.ime

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.alifbo.keyboard.TestFiles
import uz.alifbo.keyboard.data.PackParser
import uz.alifbo.keyboard.data.Prefs
import java.io.File
import java.io.FileOutputStream

/**
 * Klaviaturani haqiqiy chizadi (Robolectric NATIVE grafika) va PNG saqlaydi: app/build/screenshots.
 * CI da artifact sifatida yuklanadi — koʻrinishni telefonsiz solishtirish uchun.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class KeyboardShotsTest {

    private fun render(name: String, styleId: Int, mode: Int, setup: (KeyboardRoot) -> Unit = {}) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = Prefs(app)
        val st = Theme.STYLES[styleId]
        prefs.applyStylePreset(st.id, st.radius, st.gap, st.textScale, st.shadow, st.heightPct)
        prefs.theme = mode
        val root = KeyboardRoot(Theme.themed(app, mode))
        val pack = PackParser.parse(TestFiles.repoFile("registry/packs/uz-latn-2026.json").readText())
        root.keyboard.setPack(pack)
        root.keyboard.applyStyle(prefs)
        root.keyboard.applyPrefs(true, prefs.heightPercent, true, false)
        root.strip.setToolsMask(prefs.toolsMask)
        root.reloadColors()
        setup(root)
        val w = 1080
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val bmp = Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bmp.width == w && bmp.height > 400)
    }

    @Test
    fun shots() {
        val names = listOf("samsung", "ios", "redmi")
        for ((i, n) in names.withIndex()) {
            render("$n-light", i, 1)
            render("$n-dark", i, 2)
            render("$n-dark-suggest", i, 2) { it.strip.setSuggestions(listOf("salom", "salomat", "salomatlik")) }
            render("$n-light-chips", i, 1) {
                it.strip.setChips(listOf(StripView.Chip("Assalomu alaykum, qalaysiz?"), StripView.Chip("Skrinshot", null)))
            }
        }
    }
}
