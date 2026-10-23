package uz.alifbo.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import uz.alifbo.keyboard.R
import uz.alifbo.keyboard.data.DefaultLayers
import uz.alifbo.keyboard.data.KeySpec
import uz.alifbo.keyboard.data.LayoutPack
import java.util.Locale

/**
 * Canvas'da chizadigan, tez ishlaydigan klaviatura ko'rinishi.
 * Ko'p barmoqli yozish, backspace takrori, uzoq bosganda qo'shimcha belgilar,
 * bo'sh joy ustida surib kursorni boshqarish, doimiy raqamlar qatori, bir qo'l rejimi.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onLangNext()
        fun onLangPicker()
        fun onCursor(dir: Int)
    }

    var listener: Listener? = null

    private class KeyRect(val spec: KeySpec, val rect: RectF, val row: Int)

    private class Ptr(val id: Int) {
        var key: KeyRect? = null
        var x = 0f
        var anchor = 0f
        var alts: List<String>? = null
        var stripLeft = 0f
        var stripTop = 0f
        var stripH = 0f
        var cellW = 0f
        var altIndex = 0
        var consumed = false
        var longRun: Runnable? = null
        var repRun: Runnable? = null
    }

    // ---- holat ----
    private var pack: LayoutPack? = null
    private var symbols = false
    private var numberRow = true
    private var oneHanded = 0 // 0 - o'chiq, 1 - chap, 2 - o'ng
    private var shift = 0 // 0 - o'chiq, 1 - bir marta, 2 - doimiy (caps lock)
    private var lastShiftTap = 0L
    private var enterLabel: String? = null
    private var haptic = true
    private var heightScale = 1f
    private var keyPreview = true
    private var bottomInset = 0
    private var rows: List<List<KeySpec>> = emptyList()
    private val keys = ArrayList<KeyRect>()
    private val ptrs = SparseArray<Ptr>()

    // ---- chizish ----
    private val density = resources.displayMetrics.density
    private val tmp = RectF()
    private val path = Path()
    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintKey = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val paintHint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val paintIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var colBg = 0
    private var colKey = 0
    private var colSpecial = 0
    private var colPressed = 0
    private var colText = 0
    private var colHint = 0
    private var colAccent = 0
    private var colPopup = 0

    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()

    init {
        reloadColors()
        paintIcon.strokeWidth = dp(1.8f)
        setOnApplyWindowInsetsListener { _, insets ->
            val b = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                0
            }
            if (b != bottomInset) {
                bottomInset = b
                requestLayout()
            }
            insets
        }
    }

    private fun dp(v: Float) = v * density

    fun reloadColors() {
        colBg = context.getColor(R.color.kb_bg)
        colKey = context.getColor(R.color.kb_key)
        colSpecial = context.getColor(R.color.kb_key_special)
        colPressed = context.getColor(R.color.kb_key_pressed)
        colText = context.getColor(R.color.kb_text)
        colHint = context.getColor(R.color.kb_hint)
        colAccent = context.getColor(R.color.kb_accent)
        colPopup = context.getColor(R.color.kb_popup)
        invalidate()
    }

    // ---------------------------------------------------------------- ochiq API

    fun setPack(p: LayoutPack) {
        pack = p
        symbols = false
        refreshRows()
    }

    fun setSymbols(on: Boolean) {
        if (symbols == on) return
        symbols = on
        refreshRows()
    }

    fun setNumberRow(on: Boolean) {
        if (numberRow == on) return
        numberRow = on
        refreshRows()
    }

    fun setOneHanded(mode: Int) {
        if (oneHanded == mode) return
        oneHanded = mode
        rebuild()
        invalidate()
    }

    fun setEnterLabel(s: String?) {
        if (enterLabel != s) {
            enterLabel = s
            invalidate()
        }
    }

    fun applyPrefs(haptic: Boolean, heightPercent: Int, keyPreview: Boolean = true) {
        this.haptic = haptic
        if (this.keyPreview != keyPreview) {
            this.keyPreview = keyPreview
            invalidate()
        }
        val hs = heightPercent / 100f
        if (hs != heightScale) {
            heightScale = hs
            requestLayout()
        }
    }

    /** Avto-bosh harf: cheksiz shift (caps lock) ga tegmaydi. */
    fun setAutoShift(on: Boolean) {
        if (shift == 2) return
        val n = if (on) 1 else 0
        if (n != shift) {
            shift = n
            invalidate()
        }
    }

    // ---------------------------------------------------------------- joylashuv

    private fun refreshRows() {
        val p = pack
        rows = when {
            p == null -> emptyList()
            symbols -> DefaultLayers.symbols
            else -> {
                val letters = p.rows + listOf(p.bottom)
                if (numberRow) {
                    var maxSum = 0f
                    for (r in p.rows) {
                        val s = sumW(r)
                        if (s > maxSum) maxSum = s
                    }
                    if (maxSum <= 0f) maxSum = 10f
                    val digits = "1234567890".map { KeySpec(it.toString(), maxSum / 10f, emptyList(), null) }
                    listOf(digits) + letters
                } else {
                    letters
                }
            }
        }
        cancelAll()
        requestLayout()
        rebuild()
        invalidate()
    }

    /** Raqamlar qatori yoqiq boʻlsa, harf ustidagi raqam-belgi va uning uzoq bosishi kerak emas. */
    private fun altsOf(s: KeySpec): List<String> =
        if (numberRow && s.hint != null) s.alternates.filter { it != s.hint } else s.alternates

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val n = rows.size.coerceAtLeast(4)
        val h = (n * dp(52f) * heightScale + dp(10f) + bottomInset).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        keys.clear()
        val w = width
        val h = height
        if (w <= 0 || h <= 0 || rows.isEmpty()) return

        val padH = dp(3f)
        val padTop = dp(6f)
        val padBottom = dp(4f)

        var maxSum = 0f
        for (r in rows) {
            if (r.any { it.label == "@space" }) continue
            val s = sumW(r)
            if (s > maxSum) maxSum = s
        }
        if (maxSum <= 0f) maxSum = 10f

        val fullW = w - 2 * padH
        val availW = if (oneHanded != 0) fullW * 0.82f else fullW
        val baseX = when (oneHanded) {
            2 -> padH + (fullW - availW)
            else -> padH
        }
        val unit = availW / maxSum
        val rowH = (h - padTop - padBottom - bottomInset) / rows.size

        for ((ri, r) in rows.withIndex()) {
            val ws = FloatArray(r.size) { r[it].weight }
            var sum = ws.sum()
            val si = r.indexOfFirst { it.label == "@space" }
            if (si >= 0 && sum < maxSum) {
                ws[si] += maxSum - sum
                sum = maxSum
            }
            val scale = if (sum > maxSum) maxSum / sum else 1f
            var x = baseX + (availW - sum * scale * unit) / 2f
            val top = padTop + ri * rowH
            for ((i, k) in r.withIndex()) {
                val kw = ws[i] * unit * scale
                keys.add(KeyRect(k, RectF(x, top, x + kw, top + rowH), ri))
                x += kw
            }
        }
    }

    private fun sumW(r: List<KeySpec>): Float {
        var s = 0f
        for (k in r) s += k.weight
        return s
    }

    // ---------------------------------------------------------------- chizish

    private val caseLocale: Locale get() = pack?.locale ?: Locale.getDefault()

    override fun onDraw(c: Canvas) {
        paintBg.color = colBg
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

        val g = dp(3f)
        val radius = dp(9f)
        for (k in keys) {
            val r = k.rect
            tmp.set(r.left + g, r.top + g, r.right - g, r.bottom - g)
            val lbl = k.spec.label
            val isTok = k.spec.isToken

            var bg = if (isTok && lbl != "@space") colSpecial else colKey
            var fg = colText
            if (lbl == "@enter") {
                bg = colSpecial
            }
            if (lbl == "@shift") {
                fg = colAccent
                if (shift != 0) {
                    bg = colAccent
                    fg = 0xFFFFFFFF.toInt()
                }
            }
            if (isKeyPressed(k)) bg = colPressed
            paintKey.color = bg
            c.drawRoundRect(tmp, radius, radius, paintKey)

            val cx = tmp.centerX()
            val cy = tmp.centerY()
            val rowH = tmp.height()
            when (lbl) {
                "@shift" -> {
                    drawIcon(c, lbl, cx, cy, rowH * 0.2f, fg, shift != 0)
                    if (shift == 2) {
                        paintIcon.color = fg
                        paintIcon.style = Paint.Style.STROKE
                        c.drawLine(cx - rowH * 0.1f, cy + rowH * 0.3f, cx + rowH * 0.1f, cy + rowH * 0.3f, paintIcon)
                    }
                }
                "@del", "@lang" -> drawIcon(c, lbl, cx, cy, rowH * 0.2f, fg, false)
                "@enter" -> {
                    val t = enterLabel
                    if (t == null) {
                        drawIcon(c, lbl, cx, cy, rowH * 0.2f, fg, false)
                    } else {
                        paintText.color = fg
                        paintText.textSize = rowH * 0.3f
                        drawCentered(c, t, cx, cy, paintText)
                    }
                }
                "@sym" -> {
                    paintText.color = fg
                    paintText.textSize = rowH * 0.3f
                    drawCentered(c, "!#1", cx, cy, paintText)
                }
                "@abc" -> {
                    paintText.color = fg
                    paintText.textSize = rowH * 0.3f
                    drawCentered(c, "ABC", cx, cy, paintText)
                }
                "@space" -> {
                    paintText.color = colText
                    paintText.textSize = rowH * 0.28f
                    drawCentered(c, fitText(spaceLabel(), tmp.width() - dp(16f), rowH), cx, cy, paintText)
                }
                else -> {
                    val t = if (shift != 0 && !symbols) lbl.uppercase(caseLocale) else lbl
                    paintText.color = fg
                    paintText.textSize = rowH * (if (t.length > 1) 0.3f else 0.46f)
                    drawCentered(c, t, cx, cy, paintText)
                    val hint = k.spec.hint
                    if (hint != null && !numberRow) {
                        paintHint.color = colHint
                        paintHint.textSize = rowH * 0.2f
                        c.drawText(hint, tmp.right - dp(4f), tmp.top + rowH * 0.26f, paintHint)
                    }
                }
            }
        }

        // Tugma ustida chiqadigan harf (Samsung kabi)
        if (keyPreview) {
            for (i in 0 until ptrs.size()) {
                val p = ptrs.valueAt(i)
                val k = p.key ?: continue
                if (p.alts != null || k.spec.isToken || k.row == 0) continue
                val t = if (shift != 0 && !symbols) k.spec.label.uppercase(caseLocale) else k.spec.label
                val r = k.rect
                val bw = maxOf(r.width(), dp(48f))
                val bh = r.height() * 1.1f
                val left = (r.centerX() - bw / 2f).coerceIn(0f, width - bw)
                val top = r.top - bh - dp(2f)
                tmp.set(left, top, left + bw, top + bh)
                paintKey.color = colPopup
                c.drawRoundRect(tmp, radius, radius, paintKey)
                paintText.color = colText
                paintText.textSize = bh * 0.5f
                drawCentered(c, t, tmp.centerX(), tmp.centerY(), paintText)
            }
        }

        // Uzoq bosganda chiqadigan qo'shimcha belgilar
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            val a = p.alts ?: continue
            val n = a.size
            tmp.set(p.stripLeft, p.stripTop, p.stripLeft + p.cellW * n, p.stripTop + p.stripH)
            paintKey.color = colPopup
            c.drawRoundRect(tmp, radius, radius, paintKey)
            for (j in 0 until n) {
                val l = p.stripLeft + p.cellW * j
                if (j == p.altIndex) {
                    tmp.set(l + g, p.stripTop + g, l + p.cellW - g, p.stripTop + p.stripH - g)
                    paintKey.color = colAccent
                    c.drawRoundRect(tmp, radius, radius, paintKey)
                    paintText.color = 0xFFFFFFFF.toInt()
                } else {
                    paintText.color = colText
                }
                paintText.textSize = p.stripH * 0.4f
                drawCentered(c, a[j], l + p.cellW / 2f, p.stripTop + p.stripH / 2f, paintText)
            }
        }
    }

    private fun spaceLabel(): String = pack?.name ?: ""

    /** Matn tugmaga sigʻmasa — avval kichraytiradi, keyin qisqa nomga oʻtadi, oxirida "…" bilan kesadi. */
    private fun fitText(t: String, maxW: Float, rowH: Float): String {
        if (maxW <= 0f) return ""
        val minSize = rowH * 0.2f
        var text = t
        while (true) {
            while (paintText.textSize > minSize && paintText.measureText(text) > maxW) {
                paintText.textSize -= dp(0.5f)
            }
            if (paintText.measureText(text) <= maxW) return text
            val short = pack?.short
            if (short != null && text != short) {
                text = short
                paintText.textSize = rowH * 0.28f
                continue
            }
            var n = text.length
            while (n > 1 && paintText.measureText(text.substring(0, n) + "…") > maxW) n--
            return text.substring(0, n) + "…"
        }
    }

    private fun drawCentered(c: Canvas, t: String, cx: Float, cy: Float, p: Paint) {
        c.drawText(t, cx, cy - (p.ascent() + p.descent()) / 2f, p)
    }

    private fun drawIcon(c: Canvas, label: String, cx: Float, cy: Float, s: Float, color: Int, filled: Boolean) {
        paintIcon.color = color
        paintIcon.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        path.reset()
        when (label) {
            "@shift" -> {
                path.moveTo(cx, cy - s)
                path.lineTo(cx + s, cy)
                path.lineTo(cx + s * 0.45f, cy)
                path.lineTo(cx + s * 0.45f, cy + s)
                path.lineTo(cx - s * 0.45f, cy + s)
                path.lineTo(cx - s * 0.45f, cy)
                path.lineTo(cx - s, cy)
                path.close()
                c.drawPath(path, paintIcon)
            }
            "@del" -> {
                path.moveTo(cx - s, cy)
                path.lineTo(cx - s * 0.4f, cy - s * 0.8f)
                path.lineTo(cx + s, cy - s * 0.8f)
                path.lineTo(cx + s, cy + s * 0.8f)
                path.lineTo(cx - s * 0.4f, cy + s * 0.8f)
                path.close()
                c.drawPath(path, paintIcon)
                c.drawLine(cx - s * 0.1f, cy - s * 0.35f, cx + s * 0.55f, cy + s * 0.35f, paintIcon)
                c.drawLine(cx + s * 0.55f, cy - s * 0.35f, cx - s * 0.1f, cy + s * 0.35f, paintIcon)
            }
            "@enter" -> {
                c.drawLine(cx + s, cy - s * 0.6f, cx + s, cy + s * 0.2f, paintIcon)
                c.drawLine(cx + s, cy + s * 0.2f, cx - s, cy + s * 0.2f, paintIcon)
                c.drawLine(cx - s, cy + s * 0.2f, cx - s * 0.35f, cy - s * 0.35f, paintIcon)
                c.drawLine(cx - s, cy + s * 0.2f, cx - s * 0.35f, cy + s * 0.75f, paintIcon)
            }
            "@lang" -> {
                c.drawCircle(cx, cy, s, paintIcon)
                c.drawOval(cx - s * 0.45f, cy - s, cx + s * 0.45f, cy + s, paintIcon)
                c.drawLine(cx - s, cy, cx + s, cy, paintIcon)
            }
        }
    }

    private fun isKeyPressed(k: KeyRect): Boolean {
        for (i in 0 until ptrs.size()) if (ptrs.valueAt(i).key === k) return true
        return false
    }

    // ---------------------------------------------------------------- teginish

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                up(e.getPointerId(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun hit(x: Float, y: Float): KeyRect? {
        if (keys.isEmpty()) return null
        val cy = y.coerceIn(keys.first().rect.top, keys.last().rect.bottom - 1f)
        var best: KeyRect? = null
        var bd = Float.MAX_VALUE
        for (k in keys) {
            val r = k.rect
            if (cy < r.top || cy >= r.bottom) continue
            val d = when {
                x < r.left -> r.left - x
                x > r.right -> x - r.right
                else -> 0f
            }
            if (d < bd) {
                bd = d
                best = k
            }
        }
        return best
    }

    private fun down(id: Int, x: Float, y: Float) {
        val k = hit(x, y) ?: return
        val p = Ptr(id)
        p.key = k
        p.x = x
        p.anchor = x
        ptrs.put(id, p)
        if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        arm(p, k)
        invalidate()
    }

    /** Tugmaga qarab uzoq bosish / takrorlashni ishga tushiradi. */
    private fun arm(p: Ptr, k: KeyRect) {
        disarm(p)
        val lbl = k.spec.label
        if (lbl == "@del") {
            p.consumed = true
            listener?.onDelete()
            val rep = object : Runnable {
                override fun run() {
                    if (ptrs.get(p.id) !== p) return
                    listener?.onDelete()
                    postDelayed(this, 45L)
                }
            }
            p.repRun = rep
            postDelayed(rep, longPressMs)
        } else if (lbl == "@lang" || altsOf(k.spec).isNotEmpty()) {
            val lp = Runnable { onLongPress(p) }
            p.longRun = lp
            postDelayed(lp, longPressMs)
        }
    }

    private fun disarm(p: Ptr) {
        p.longRun?.let { removeCallbacks(it) }
        p.repRun?.let { removeCallbacks(it) }
        p.longRun = null
        p.repRun = null
    }

    private fun onLongPress(p: Ptr) {
        if (ptrs.get(p.id) !== p) return
        val k = p.key ?: return
        if (k.spec.label == "@lang") {
            p.consumed = true
            listener?.onLangPicker()
            return
        }
        val raw = altsOf(k.spec)
        if (raw.isEmpty()) return

        val locale = caseLocale
        val alts = if (shift != 0) raw.map { it.uppercase(locale) } else raw
        val n = alts.size
        var cell = maxOf(k.rect.width(), dp(42f))
        if (cell * n > width) cell = width.toFloat() / n
        var left = k.rect.centerX() - cell / 2f
        if (left + cell * n > width) left = width - cell * n
        if (left < 0f) left = 0f

        p.cellW = cell
        p.stripLeft = left
        p.stripH = k.rect.height()
        // Birinchi qatorda tepaga joy yo'q — o'sha qatorning o'zi ustidan chiqaramiz.
        p.stripTop = if (k.row > 0) k.rect.top - k.rect.height() else k.rect.top
        p.alts = alts
        p.altIndex = altIndexAt(p, p.x)
        if (haptic) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun altIndexAt(p: Ptr, x: Float): Int {
        val n = p.alts?.size ?: return 0
        return ((x - p.stripLeft) / p.cellW).toInt().coerceIn(0, n - 1)
    }

    private fun move(id: Int, x: Float, y: Float) {
        val p = ptrs.get(id) ?: return
        p.x = x
        if (p.alts != null) {
            val idx = altIndexAt(p, x)
            if (idx != p.altIndex) {
                p.altIndex = idx
                invalidate()
            }
            return
        }
        val cur = p.key ?: return

        // Bo'sh joy ustida surish — kursorni siljitish
        if (cur.spec.label == "@space") {
            val step = dp(16f)
            var dx = x - p.anchor
            while (dx >= step) {
                listener?.onCursor(1)
                p.anchor += step
                dx -= step
                p.consumed = true
                if (haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            while (dx <= -step) {
                listener?.onCursor(-1)
                p.anchor -= step
                dx += step
                p.consumed = true
                if (haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            return
        }

        if (x >= cur.rect.left && x < cur.rect.right && y >= cur.rect.top && y < cur.rect.bottom) return
        val nk = hit(x, y)
        if (nk !== cur) {
            disarm(p)
            p.key = nk
            p.anchor = x
            p.consumed = false
            if (nk != null && nk.spec.label != "@del") arm(p, nk)
            invalidate()
        }
    }

    private fun up(id: Int) {
        val p = ptrs.get(id) ?: return
        ptrs.remove(id)
        disarm(p)
        val alts = p.alts
        val k = p.key
        if (alts != null) {
            commit(alts[p.altIndex.coerceIn(0, alts.size - 1)])
        } else if (k != null && !p.consumed) {
            fire(k.spec)
        }
        invalidate()
    }

    private fun cancelAll() {
        for (i in 0 until ptrs.size()) disarm(ptrs.valueAt(i))
        ptrs.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------- amallar

    private fun fire(s: KeySpec) {
        when (s.label) {
            "@shift" -> toggleShift()
            "@del" -> listener?.onDelete()
            "@sym" -> setSymbols(true)
            "@abc" -> setSymbols(false)
            "@lang" -> listener?.onLangNext()
            "@space" -> commit(" ")
            "@enter" -> {
                if (shift == 1) {
                    shift = 0
                    invalidate()
                }
                listener?.onEnter()
            }
            else -> commit(if (shift != 0 && !symbols) s.label.uppercase(caseLocale) else s.label)
        }
    }

    private fun commit(text: String) {
        if (shift == 1) {
            shift = 0
            invalidate()
        }
        listener?.onText(text)
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when (shift) {
            2 -> 0
            1 -> if (now - lastShiftTap < 350L) 2 else 0
            else -> 1
        }
        lastShiftTap = now
        invalidate()
    }
}
