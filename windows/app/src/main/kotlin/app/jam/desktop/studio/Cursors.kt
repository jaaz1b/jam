package app.jam.desktop.studio

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

/**
 * Jam's own mouse pointers, in the theme's accent: a rounded arrow with a white rim and a soft
 * shadow, and for what can be clicked the same arrow with a ring at its tip. Made at the size
 * Windows asks for (scaled for the screen), again when the accent changes.
 */
object Cursors {
    private var made: Pair<Int, Pair<PointerIcon, PointerIcon>>? = null

    @Composable
    fun arrow(): PointerIcon = pair(Chrome.accent.toArgb()).first

    /** Jam's pointer when it is on (Theme › Pointer), else Windows' own. */
    @Composable
    fun handOr(): PointerIcon = if (Studio.pointer.value) hand() else PointerIcon.Hand
    @Composable
    fun arrowOr(): PointerIcon = if (Studio.pointer.value) arrow() else PointerIcon.Default

    @Composable
    fun hand(): PointerIcon = pair(Chrome.accent.toArgb()).second

    @Synchronized
    private fun pair(accent: Int): Pair<PointerIcon, PointerIcon> {
        made?.takeIf { it.first == accent }?.let { return it.second }
        val p = runCatching { PointerIcon(cursor(accent, ring = false, "jam-arrow")) to PointerIcon(cursor(accent, ring = true, "jam-hand")) }
            .getOrElse { PointerIcon.Default to PointerIcon.Hand }
        made = accent to p
        return p
    }

    private fun cursor(accent: Int, ring: Boolean, name: String): java.awt.Cursor {
        val tk = Toolkit.getDefaultToolkit()
        val best = tk.getBestCursorSize(48, 48).let { if (it.width <= 0) java.awt.Dimension(32, 32) else it }
        val size = best.width
        val s = size / 32f
        return tk.createCustomCursor(image(accent, ring, size), Point((3f * s).toInt(), (2f * s).toInt()), name)
    }

    /** The pointer's picture, [size] px square. */
    fun image(accent: Int, ring: Boolean, size: Int): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val s = size / 32f // the drawing is made for 32 px
        val arrow = Path2D.Float().apply {
            moveTo(3f * s, 2f * s)
            lineTo(3f * s, 22f * s)
            quadTo(3.2f * s, 23.6f * s, 4.8f * s, 22.6f * s)
            lineTo(9.6f * s, 18.4f * s)
            lineTo(13.2f * s, 26.2f * s)
            quadTo(13.9f * s, 27.3f * s, 15.1f * s, 26.8f * s)
            lineTo(17f * s, 25.9f * s)
            quadTo(18f * s, 25.3f * s, 17.5f * s, 24.2f * s)
            lineTo(14f * s, 16.6f * s)
            lineTo(20.6f * s, 16.2f * s)
            quadTo(22.2f * s, 15.9f * s, 21.2f * s, 14.6f * s)
            lineTo(4.9f * s, 1.3f * s)
            quadTo(3.2f * s, 0.4f * s, 3f * s, 2f * s)
            closePath()
        }
        // Shadow, then the white rim, then the accent body.
        g.translate(0.8f * s.toDouble(), 1.2f * s.toDouble())
        g.color = Color(0, 0, 0, 70)
        g.stroke = BasicStroke(3.4f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(arrow); g.fill(arrow)
        g.translate(-0.8f * s.toDouble(), -1.2f * s.toDouble())
        g.color = Color.WHITE
        g.stroke = BasicStroke(2.6f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(arrow)
        val a = Color(accent, true)
        g.paint = java.awt.GradientPaint(3f * s, 2f * s, a.brighter(), 16f * s, 26f * s, a)
        g.fill(arrow)
        if (ring) {
            g.color = Color.WHITE
            g.stroke = BasicStroke(2.2f * s)
            g.draw(Ellipse2D.Float(21f * s, 19f * s, 8.5f * s, 8.5f * s))
            g.color = a
            g.fill(Ellipse2D.Float(22.6f * s, 20.6f * s, 5.3f * s, 5.3f * s))
        }
        g.dispose()
        return img
    }
}
