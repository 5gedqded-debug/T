package com.example.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * Иконка батареи с процентом и значком молнии справа:
 *  - зарядка идёт  -> молния
 *  - зарядки нет   -> перечёркнутая молния
 */
class BatteryView(context: Context) : View(context) {

    private var level = 0
    private var charging = false
    private val d = resources.displayMetrics.density

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 1.5f * d
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 9f * d; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val slash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.RED; strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
    }

    fun setState(level: Int, charging: Boolean) {
        this.level = level.coerceIn(0, 100)
        this.charging = charging
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Фон-плашка
        c.drawRoundRect(RectF(0f, 0f, w, h), 10 * d, 10 * d, bg)

        // Корпус батареи
        val bx = 6 * d
        val by = h / 2 - 8 * d
        val bw = 34 * d
        val bh = 16 * d
        c.drawRoundRect(RectF(bx, by, bx + bw, by + bh), 3 * d, 3 * d, stroke)
        c.drawRect(bx + bw, by + 5 * d, bx + bw + 2.5f * d, by + bh - 5 * d,
            fill.apply { color = Color.WHITE })

        // Заливка уровня
        fill.color = when {
            level <= 15 -> Color.RED
            level <= 30 -> Color.rgb(255, 170, 0)
            else -> Color.rgb(60, 200, 90)
        }
        val inner = 2.5f * d
        val fw = (bw - 2 * inner) * level / 100f
        c.drawRect(bx + inner, by + inner, bx + inner + fw, by + bh - inner, fill)

        // Процент по центру батареи
        val ty = by + bh / 2 - (text.descent() + text.ascent()) / 2
        c.drawText("$level", bx + bw / 2, ty, text)

        // Молния справа
        val cx = w - 14 * d
        val cy = h / 2
        val s = 9 * d
        val p = Path().apply {
            moveTo(cx + 0.2f * s, cy - s)
            lineTo(cx - 0.7f * s, cy + 0.1f * s)
            lineTo(cx - 0.05f * s, cy + 0.1f * s)
            lineTo(cx - 0.3f * s, cy + s)
            lineTo(cx + 0.7f * s, cy - 0.2f * s)
            lineTo(cx + 0.05f * s, cy - 0.2f * s)
            close()
        }
        bolt.color = if (charging) Color.YELLOW else Color.LTGRAY
        c.drawPath(p, bolt)

        if (!charging) {
            c.drawLine(cx - s, cy + s, cx + s, cy - s, slash) // перечёркивание
        }
    }
}
