package com.mostadiag.app

import android.content.Context
import android.graphics.*
import android.view.View

/** Original, density-independent line icons. No font glyphs or remote assets. */
class NeonIcon(context: Context, private val kind: String, var tint: Int, private val glow: Boolean = false) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.65f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 28f
        canvas.save(); canvas.translate((width - 24 * scale) / 2, (height - 24 * scale) / 2); canvas.scale(scale, scale)
        p.color = tint
        if (glow) p.setShadowLayer(3f, 0f, 0f, tint)
        fun path(vararg xy: Float) {
            val line = Path().apply { moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1]) }
            canvas.drawPath(line, p)
        }
        fun line(x: Float, y: Float, a: Float, b: Float) = canvas.drawLine(x, y, a, b, p)
        when (kind) {
            "home" -> { path(3f, 11f, 12f, 3f, 21f, 11f); path(5f, 10f, 5f, 21f, 10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f, 19f, 21f, 19f, 10f) }
            "car" -> { path(3f, 12f, 6f, 5f, 18f, 5f, 21f, 12f, 21f, 19f, 3f, 19f, 3f, 12f, 21f, 12f); line(6f, 15f, 8f, 15f); line(16f, 15f, 18f, 15f); line(6f, 19f, 6f, 21f); line(18f, 19f, 18f, 21f) }
            "bars" -> { line(5f, 20f, 5f, 13f); line(12f, 20f, 12f, 4f); line(19f, 20f, 19f, 9f); p.strokeWidth = 3f; line(5f, 20f, 5f, 13f); line(12f, 20f, 12f, 4f); line(19f, 20f, 19f, 9f); p.strokeWidth = 1.65f }
            "report" -> { canvas.drawRoundRect(5f, 3f, 19f, 21f, 2f, 2f, p); line(9f, 8f, 15f, 8f); line(9f, 12f, 15f, 12f); line(9f, 16f, 13f, 16f) }
            "settings" -> { canvas.drawCircle(12f, 12f, 6f, p); canvas.drawCircle(12f, 12f, 2f, p); for (i in 0..7) { canvas.save(); canvas.rotate(i * 45f, 12f, 12f); line(12f, 3f, 12f, 6f); canvas.restore() } }
            "bluetooth" -> { path(7f, 7f, 17f, 17f, 12f, 22f, 12f, 2f, 17f, 7f, 7f, 17f) }
            "engine" -> { path(4f, 9f, 7f, 9f, 9f, 6f, 17f, 6f, 19f, 10f, 22f, 10f, 22f, 18f, 18f, 18f, 16f, 21f, 8f, 21f, 5f, 17f, 4f, 17f, 4f, 9f); line(2f, 10f, 2f, 16f); line(10f, 3f, 16f, 3f); line(13f, 3f, 13f, 6f); path(8f, 13f, 11f, 13f, 13f, 10f, 15f, 16f, 17f, 13f, 20f, 13f) }
            "gauge" -> { canvas.drawArc(3f, 3f, 21f, 21f, 140f, 260f, false, p); line(12f, 12f, 17f, 7f); canvas.drawCircle(12f, 12f, 1f, p); line(5f, 7f, 7f, 9f); line(12f, 3f, 12f, 5f); line(5f, 17f, 7f, 16f) }
            "temperature" -> { canvas.drawRoundRect(9f, 2f, 15f, 17f, 3f, 3f, p); canvas.drawCircle(12f, 18f, 4f, p); line(12f, 9f, 12f, 18f); line(18f, 6f, 21f, 6f); line(18f, 10f, 20f, 10f) }
            "battery" -> { canvas.drawRoundRect(3f, 6f, 21f, 21f, 2f, 2f, p); line(7f, 3f, 7f, 6f); line(17f, 3f, 17f, 6f); line(6f, 13f, 10f, 13f); line(16f, 11f, 16f, 15f); line(14f, 13f, 18f, 13f) }
            "arrow" -> { path(9f, 6f, 15f, 12f, 9f, 18f) }
            "share" -> { path(12f, 16f, 12f, 2f, 7f, 7f); path(12f, 2f, 17f, 7f); path(5f, 12f, 5f, 21f, 19f, 21f, 19f, 12f) }
            "shield" -> { path(12f, 2f, 21f, 6f, 20f, 14f, 17f, 19f, 12f, 22f, 7f, 19f, 4f, 14f, 3f, 6f, 12f, 2f); path(8f, 12f, 11f, 15f, 16f, 9f) }
            else -> { path(3f, 12f, 7f, 12f, 10f, 5f, 14f, 20f, 17f, 12f, 21f, 12f) }
        }
        p.clearShadowLayer(); canvas.restore()
    }
}
