package app.jam.desktop.studio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * What a mouse over something clickable shows on the PC: a soft light over it (rounded), a little
 * stronger while pressed — the app's pages were made for fingers and showed nothing on hover.
 * The app's own indication on Windows (the theme, and its press-to-shrink buttons).
 */
object HoverIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0x4A414D

    private class Node(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private val light = Animatable(0f)
        private var hovered = 0
        private var pressed = 0

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { i ->
                    when (i) {
                        is HoverInteraction.Enter -> hovered++
                        is HoverInteraction.Exit -> hovered = (hovered - 1).coerceAtLeast(0)
                        is PressInteraction.Press -> pressed++
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                    }
                    val target = when { pressed > 0 -> 0.14f; hovered > 0 -> 0.08f; else -> 0f }
                    launch { light.animateTo(target, tween(if (target > light.value) 90 else 220)) { invalidateDraw() } }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val a = light.value
            if (a > 0.001f) {
                val r = minOf(10.dp.toPx(), size.minDimension / 2)
                drawRoundRect(Color.White.copy(alpha = a), cornerRadius = CornerRadius(r, r))
            }
        }
    }
}

/** The hand pointer (Jam's or Windows') over something clickable, for the app's own buttons. */
fun handPointer(): Modifier = Modifier.composed { Modifier.pointerHoverIcon(Cursors.handOr()) }
