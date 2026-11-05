package uz.alifbo.keyboard.ime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import uz.alifbo.keyboard.R

/**
 * Klaviatura tepasidagi qator: matn yozilmayotganda asboblar (emoji, bufer, ovoz, bir qo'l, sozlamalar, yopish),
 * so'z yozilayotganda esa so'z tavsiyalari.
 */
class StripView(context: Context) : View(context) {

    interface Listener {
        fun onTool(id: Int)
        fun onSuggestion(text: String)
        fun onChip(index: Int)
        fun onChipsDismiss()
    }

    /** Boʻsh maydonda koʻrsatiladigan "eng oxirgi" taklif: nusxalangan matn yoki skrinshot (thumb). */
    class Chip(val text: String, val thumb: Bitmap? = null)

    companion object {
        const val EMOJI = 0
        const val TRANSLATE = 1
        const val CLIP = 2
        const val VOICE = 3
        const val SETTINGS = 4
        const val MORE = 5
        const val SCAN = 6
        const val EDIT = 7

        /** "Yangi" deb belgilanadigan asboblar (toʻq sariq nuqta, ochilgach yoʻqoladi). */
        const val NEW_MASK = (1 shl SCAN) or (1 shl EDIT) or (1 shl MORE)

        /** Qatordagi koʻrinish tartibi (id lar oʻzgarmaydi). */
        private val ORDER = intArrayOf(EMOJI, TRANSLATE, SCAN, EDIT, CLIP, VOICE, SETTINGS, MORE)
    }

    var listener: Listener? = null

    private var suggestions: List<String> = emptyList()
    private var chips: List<Chip> = emptyList()
    private var tools = ORDER.copyOf()
    private var pressed = -1

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private var colBg = 0
    private var colText = 0
    private var colHint = 0
    private var colPressed = 0
    private var colAccent = 0
    private var colChip = 0
    private var badges = 0

    init {
        reloadColors()
        paint.strokeWidth = 1.8f * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
    }

    fun reloadColors() {
        colBg = Theme.col(context, R.color.kb_bg)
        colText = Theme.col(context, R.color.kb_text)
        colHint = Theme.col(context, R.color.kb_hint)
        colPressed = Theme.col(context, R.color.kb_key_pressed)
        colAccent = Theme.accent(context)
        colChip = Theme.col(context, R.color.kb_chip)
        invalidate()
    }

    /** Koʻrinadigan asboblar (bit niqobi: bit i — i-asbob). «…» har doim bor. */
    fun setToolsMask(mask: Int) {
        val m = mask or (1 shl MORE)
        val t = ORDER.filter { m and (1 shl it) != 0 }.toIntArray()
        if (!t.contentEquals(tools)) {
            tools = t
            pressed = -1
            invalidate()
        }
    }

    /** Toʻq sariq "yangi" nuqtasi koʻrinadigan asboblar (bit niqobi). */
    fun setBadges(mask: Int) {
        if (mask != badges) {
            badges = mask
            invalidate()
        }
    }

    fun setSuggestions(list: List<String>) {
        if (list != suggestions) {
            suggestions = list
            pressed = -1
            invalidate()
        }
    }

    fun setChips(list: List<Chip>) {
        if (list.size != chips.size || list.indices.any { list[it].text != chips[it].text || list[it].thumb !== chips[it].thumb }) {
            chips = list
            pressed = -1
            invalidate()
        }
    }

    private fun chipMode() = suggestions.isEmpty() && chips.isNotEmpty()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (48 * density).toInt())
    }

    private fun cellCount() = when {
        suggestions.isNotEmpty() -> suggestions.size
        chipMode() -> chips.size + 1 // oxirgisi — yopish (✕)
        else -> tools.size
    }

    /** Katak chegaralari (n + 1 ta qiymat). Tavsiya va asboblarda teng, chiplarda matn kengroq, ✕ tor. */
    private fun bounds(): FloatArray {
        val n = cellCount()
        val w = FloatArray(n) { 1f }
        if (chipMode()) {
            for (i in chips.indices) w[i] = if (chips[i].thumb != null) 1.4f else 3f
            w[n - 1] = 0.7f
        }
        val sum = w.sum()
        val b = FloatArray(n + 1)
        for (i in 0 until n) b[i + 1] = b[i] + width.toFloat() * w[i] / sum
        return b
    }

    private fun cellAt(x: Float): Int {
        val b = bounds()
        for (i in 0 until b.size - 1) if (x < b[i + 1]) return i
        return b.size - 2
    }

    override fun onDraw(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = colBg
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val n = cellCount()
        val b = bounds()
        val cw = width.toFloat() / n
        val cy = height / 2f

        if (chipMode()) {
            drawChips(c, b, cy)
            return
        }

        val toolMode = suggestions.isEmpty()
        if (pressed in 0 until n && !toolMode) {
            paint.color = colPressed
            rect.set(b[pressed] + 4 * density, 4 * density, b[pressed + 1] - 4 * density, height - 4 * density)
            c.drawRoundRect(rect, 10 * density, 10 * density, paint)
        }

        if (suggestions.isNotEmpty()) {
            text.textSize = 17f * density
            for (i in 0 until n) {
                text.color = colText
                text.typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                c.drawText(suggestions[i], i * cw + cw / 2f, cy - (text.ascent() + text.descent()) / 2f, text)
                if (i > 0) {
                    paint.color = colHint
                    paint.style = Paint.Style.STROKE
                    c.drawLine(i * cw, cy - 10 * density, i * cw, cy + 10 * density, paint)
                }
            }
            return
        }

        // Samsung kabi: har bir asbob doira ichida
        val r = minOf(cw / 2f - 7 * density, height * 0.36f)
        for (i in 0 until n) {
            val cx = i * cw + cw / 2f
            paint.style = Paint.Style.FILL
            paint.color = if (pressed == i) colPressed else colChip
            c.drawCircle(cx, cy, r, paint)
            drawTool(c, tools[i], cx, cy, r * 0.5f)
            if (badges and (1 shl tools[i]) != 0) {
                paint.style = Paint.Style.FILL
                paint.color = 0xFFFF6A00.toInt()
                c.drawCircle(cx + r * 0.75f, cy - r * 0.75f, 3.2f * density, paint)
            }
        }
    }

    private fun drawChips(c: Canvas, b: FloatArray, cy: Float) {
        val pad = 4 * density
        for (i in chips.indices) {
            val ch = chips[i]
            rect.set(b[i] + pad, pad, b[i + 1] - pad, height - pad)
            paint.style = Paint.Style.FILL
            paint.color = if (pressed == i) colAccent and 0x00FFFFFF or 0x55000000 else colPressed
            c.drawRoundRect(rect, 12 * density, 12 * density, paint)
            val thumb = ch.thumb
            if (thumb != null) {
                val clip = Path().apply { addRoundRect(rect, 12 * density, 12 * density, Path.Direction.CW) }
                c.save()
                c.clipPath(clip)
                val sc = maxOf(rect.width() / thumb.width, rect.height() / thumb.height)
                val dw = thumb.width * sc
                val dh = thumb.height * sc
                val l = rect.left + (rect.width() - dw) / 2f
                val t = rect.top + (rect.height() - dh) / 2f
                c.drawBitmap(thumb, null, android.graphics.RectF(l, t, l + dw, t + dh), null)
                c.restore()
            } else {
                text.textSize = 15f * density
                text.color = colText
                text.typeface = Typeface.DEFAULT
                val avail = rect.width() - 20 * density
                val one = ch.text.replace('\n', ' ')
                val cut = text.breakText(one, true, avail, null)
                val shown = if (cut < one.length) one.substring(0, maxOf(0, cut - 1)) + "…" else one
                c.drawText(shown, rect.centerX(), cy - (text.ascent() + text.descent()) / 2f, text)
            }
        }
        val last = chips.size
        val cx = (b[last] + b[last + 1]) / 2f
        val s = 6 * density
        paint.style = Paint.Style.STROKE
        paint.color = if (pressed == last) colText else colHint
        c.drawLine(cx - s, cy - s, cx + s, cy + s, paint)
        c.drawLine(cx - s, cy + s, cx + s, cy - s, paint)
    }

    private fun drawTool(c: Canvas, id: Int, cx: Float, cy: Float, s: Float) {
        paint.style = Paint.Style.STROKE
        paint.color = colText
        when (id) {
            EMOJI -> {
                c.drawCircle(cx, cy, s, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(cx - s * 0.35f, cy - s * 0.25f, s * 0.1f, paint)
                c.drawCircle(cx + s * 0.35f, cy - s * 0.25f, s * 0.1f, paint)
                paint.style = Paint.Style.STROKE
                rect.set(cx - s * 0.55f, cy - s * 0.35f, cx + s * 0.55f, cy + s * 0.6f)
                c.drawArc(rect, 20f, 140f, false, paint)
            }
            CLIP -> {
                rect.set(cx - s * 0.75f, cy - s * 0.85f, cx + s * 0.75f, cy + s)
                c.drawRoundRect(rect, s * 0.2f, s * 0.2f, paint)
                rect.set(cx - s * 0.35f, cy - s * 1.1f, cx + s * 0.35f, cy - s * 0.6f)
                c.drawRoundRect(rect, s * 0.15f, s * 0.15f, paint)
                c.drawLine(cx - s * 0.4f, cy, cx + s * 0.4f, cy, paint)
                c.drawLine(cx - s * 0.4f, cy + s * 0.4f, cx + s * 0.2f, cy + s * 0.4f, paint)
            }
            VOICE -> {
                rect.set(cx - s * 0.35f, cy - s, cx + s * 0.35f, cy + s * 0.25f)
                c.drawRoundRect(rect, s * 0.35f, s * 0.35f, paint)
                rect.set(cx - s * 0.7f, cy - s * 0.5f, cx + s * 0.7f, cy + s * 0.6f)
                c.drawArc(rect, 0f, 180f, false, paint)
                c.drawLine(cx, cy + s * 0.6f, cx, cy + s, paint)
            }
            TRANSLATE -> {
                rect.set(cx - s, cy - s * 0.85f, cx + s, cy + s * 0.85f)
                c.drawRoundRect(rect, s * 0.3f, s * 0.3f, paint)
                paint.style = Paint.Style.FILL
                text.color = colText
                text.textSize = s * 1.05f
                c.drawText("A", cx - s * 0.38f, cy - s * 0.05f, text)
                text.textSize = s * 0.85f
                c.drawText("文", cx + s * 0.38f, cy + s * 0.62f, text)
                paint.style = Paint.Style.STROKE
            }
            SETTINGS -> {
                c.drawCircle(cx, cy, s * 0.4f, paint)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val cs = Math.cos(a).toFloat()
                    val sn = Math.sin(a).toFloat()
                    c.drawLine(cx + cs * s * 0.7f, cy + sn * s * 0.7f, cx + cs * s, cy + sn * s, paint)
                }
                c.drawCircle(cx, cy, s * 0.7f, paint)
            }
            SCAN -> {
                rect.set(cx - s, cy - s * 0.6f, cx + s, cy + s * 0.85f)
                c.drawRoundRect(rect, s * 0.25f, s * 0.25f, paint)
                rect.set(cx - s * 0.4f, cy - s * 0.95f, cx + s * 0.4f, cy - s * 0.6f)
                c.drawRoundRect(rect, s * 0.12f, s * 0.12f, paint)
                c.drawCircle(cx, cy + s * 0.12f, s * 0.38f, paint)
            }
            EDIT -> {
                // toʻrt tomonga strelkalar (kursor boshqaruvi)
                val a = s * 1.05f
                val t = s * 0.32f
                c.drawLine(cx - a, cy, cx - a + t, cy - t, paint)
                c.drawLine(cx - a, cy, cx - a + t, cy + t, paint)
                c.drawLine(cx + a, cy, cx + a - t, cy - t, paint)
                c.drawLine(cx + a, cy, cx + a - t, cy + t, paint)
                c.drawLine(cx, cy - a, cx - t, cy - a + t, paint)
                c.drawLine(cx, cy - a, cx + t, cy - a + t, paint)
                c.drawLine(cx, cy + a, cx - t, cy + a - t, paint)
                c.drawLine(cx, cy + a, cx + t, cy + a - t, paint)
            }
            MORE -> {
                paint.style = Paint.Style.FILL
                for (d in -1..1) c.drawCircle(cx + d * s * 0.7f, cy, s * 0.14f, paint)
                paint.style = Paint.Style.STROKE
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = cellAt(e.x)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val c = cellAt(e.x)
                if (pressed != -1 && c != pressed) {
                    pressed = -1
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val c = cellAt(e.x)
                val was = pressed
                pressed = -1
                invalidate()
                if (was == c) {
                    if (suggestions.isNotEmpty()) {
                        if (c < suggestions.size) listener?.onSuggestion(suggestions[c])
                    } else if (chipMode()) {
                        if (c < chips.size) listener?.onChip(c) else listener?.onChipsDismiss()
                    } else {
                        listener?.onTool(tools[c])
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                invalidate()
            }
        }
        return true
    }
}
