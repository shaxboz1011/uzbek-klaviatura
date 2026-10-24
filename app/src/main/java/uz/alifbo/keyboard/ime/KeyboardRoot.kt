package uz.alifbo.keyboard.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Klaviatura oynasi: tepada asboblar/tavsiyalar qatori, pastda klaviatura yoki emoji/bufer/tarjima/asboblar paneli. */
class KeyboardRoot(context: Context) : LinearLayout(context) {

    companion object {
        const val KEYBOARD = 0
        const val EMOJI = 1
        const val CLIP = 2
        const val MORE = 3
        const val TRANSLATE = 4
        const val CONVERT = 5
    }

    val strip = StripView(context)
    val keyboard = KeyboardView(context)
    val emoji = EmojiPanel(context)
    val clip = ClipPanel(context)
    val more = MorePanel(context)
    val translate = TranslatePanel(context)
    val convert = ConvertPanel(context)
    private val body = FrameLayout(context)

    init {
        orientation = VERTICAL
        addView(strip, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(body, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        // Klaviatura balandlikni belgilaydi; panellar shu balandlikda uning ustida ochiladi.
        body.addView(keyboard, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(emoji, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        body.addView(clip, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        body.addView(more, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        body.addView(translate, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        body.addView(convert, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        emoji.visibility = View.GONE
        clip.visibility = View.GONE
        more.visibility = View.GONE
        translate.visibility = View.GONE
        convert.visibility = View.GONE
    }

    fun showPanel(which: Int) {
        keyboard.visibility = if (which == KEYBOARD) View.VISIBLE else View.INVISIBLE
        emoji.visibility = if (which == EMOJI) View.VISIBLE else View.GONE
        clip.visibility = if (which == CLIP) View.VISIBLE else View.GONE
        more.visibility = if (which == MORE) View.VISIBLE else View.GONE
        translate.visibility = if (which == TRANSLATE) View.VISIBLE else View.GONE
        convert.visibility = if (which == CONVERT) View.VISIBLE else View.GONE
    }

    fun reloadColors() {
        keyboard.reloadColors()
        strip.reloadColors()
    }
}
