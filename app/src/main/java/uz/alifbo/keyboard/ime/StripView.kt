package uz.alifbo.keyboard.ime

import android.content.Context
import android.graphics.Canvas
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
    }

    companion object {
        const val EMOJI = 0
        const val TRANSLATE = 1
        const val CLIP = 2
        const val VOICE = 3
        const val SETTINGS = 4
        const val MORE = 5
        private const val TOOL_COUNT = 6
    }

    var listener: Listener? = null

    private var suggestions: List<String> = emptyList()
    private var tools = intArrayOf(EMOJI, TRANSLATE, CLIP, VOICE, SETTINGS, MORE)
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

    init {
        reloadColors()
        paint.strokeWidth = 1.8f * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
    }

    fun reloadColors() {
        colBg = context.getColor(R.color.kb_bg)
        colText = context.getColor(R.color.kb_text)
        colHint = context.getColor(R.color.kb_hint)
        colPressed = context.getColor(R.color.kb_key_pressed)
        colAccent = Theme.accent(context)
        invalidate()
    }

    /** Koʻrinadigan asboblar (bit niqobi: bit i — i-asbob). «…» har doim bor. */
    fun setToolsMask(mask: Int) {
        val m = mask or (1 shl MORE)
        val t = (0 until TOOL_COUNT).filter { m and (1 shl it) != 0 }.toIntArray()
        if (!t.contentEquals(tools)) {
            tools = t
            pressed = -1
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

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (44 * density).toInt())
    }

    private fun cellCount() = if (suggestions.isNotEmpty()) suggestions.size else tools.size

    private fun cellAt(x: Float): Int {
        val n = cellCount()
        return (x / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)
    }

    override fun onDraw(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = colBg
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val n = cellCount()
        val cw = width.toFloat() / n
        val cy = height / 2f

        if (pressed in 0 until n) {
            paint.color = colPressed
            rect.set(pressed * cw + 4 * density, 4 * density, (pressed + 1) * cw - 4 * density, height - 4 * density)
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

        for (i in 0 until n) {
            drawTool(c, tools[i], i * cw + cw / 2f, cy, 9f * density)
        }
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
