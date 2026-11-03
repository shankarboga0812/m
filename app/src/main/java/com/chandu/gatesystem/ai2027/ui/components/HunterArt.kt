package com.chandu.gatesystem.ai2027.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chandu.gatesystem.ai2027.domain.Rank
import com.chandu.gatesystem.ai2027.ui.theme.Hunter
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * All artwork is drawn in code (Canvas): original shapes only, no third-party or copyrighted assets.
 */

private class Mote(val x: Float, val speed: Float, val size: Float, val phase: Float, val hue: Int)

/** Slow rising energy motes plus a faint grid; [animate]=false (reduce motion) draws a still frame. */
@Composable
fun ParticleBackground(modifier: Modifier = Modifier, animate: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val motes = remember { val r = Random(7); List(42) { Mote(r.nextFloat(), 0.2f + r.nextFloat() * 0.8f, 1.2f + r.nextFloat() * 2.6f, r.nextFloat(), r.nextInt(3)) } }
    val t = if (animate) {
        val inf = rememberInfiniteTransition(label = "motes")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "t").value
    } else 0.3f
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Hunter.Void, Hunter.Deep, Hunter.Void)))
            val step = 56.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(Hunter.Line.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Hunter.Line.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            for (m in motes) {
                val p = (t * m.speed + m.phase) % 1f
                val cx = m.x * size.width + sin((p * 2 * PI + m.phase * 6).toDouble()).toFloat() * 14f
                val cy = size.height * (1f - p)
                val c = when (m.hue) { 0 -> Hunter.Cyan; 1 -> Hunter.Violet; else -> Hunter.Blue }
                drawCircle(c.copy(alpha = 0.10f), m.size.dp.toPx() * 3f, Offset(cx, cy))
                drawCircle(c.copy(alpha = 0.55f * (1f - p * 0.6f)), m.size.dp.toPx() * 0.7f, Offset(cx, cy))
            }
        }
        content()
    }
}

/** A glowing dungeon gate (concentric rings with rotating arcs) with a hooded hunter silhouette in front. */
@Composable
fun GateEmblem(size: Dp = 180.dp, animate: Boolean = true, modifier: Modifier = Modifier) {
    val rot = if (animate) rememberInfiniteTransition(label = "gate").animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "rot").value else 20f
    val pulse = if (animate) rememberInfiniteTransition(label = "pulse").animateFloat(0.6f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "p").value else 0.8f
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2); val r = this.size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(Hunter.Violet.copy(alpha = 0.55f * pulse), Hunter.Blue.copy(alpha = 0.16f), Color.Transparent), c, r), r, c)
        drawCircle(Hunter.Cyan.copy(alpha = 0.9f), r * 0.86f, c, style = Stroke(3f))
        drawCircle(Hunter.Violet.copy(alpha = 0.8f), r * 0.72f, c, style = Stroke(2f))
        for (i in 0 until 3) {
            val start = rot + i * 120f
            drawArc(Hunter.Cyan, start, 70f, false, Offset(c.x - r * 0.93f, c.y - r * 0.93f), androidx.compose.ui.geometry.Size(r * 1.86f, r * 1.86f), style = Stroke(5f))
            drawArc(Hunter.Gold.copy(alpha = 0.9f), -start, 38f, false, Offset(c.x - r * 0.62f, c.y - r * 0.62f), androidx.compose.ui.geometry.Size(r * 1.24f, r * 1.24f), style = Stroke(4f))
        }
        drawHunter(c, r * 0.9f)
    }
}

private fun DrawScope.drawHunter(c: Offset, h: Float) {
    val body = Path().apply {
        moveTo(c.x, c.y - h * 0.62f)
        cubicTo(c.x + h * 0.22f, c.y - h * 0.58f, c.x + h * 0.26f, c.y - h * 0.30f, c.x + h * 0.20f, c.y - h * 0.18f)
        cubicTo(c.x + h * 0.42f, c.y - h * 0.10f, c.x + h * 0.52f, c.y + h * 0.20f, c.x + h * 0.56f, c.y + h * 0.62f)
        lineTo(c.x - h * 0.56f, c.y + h * 0.62f)
        cubicTo(c.x - h * 0.52f, c.y + h * 0.20f, c.x - h * 0.42f, c.y - h * 0.10f, c.x - h * 0.20f, c.y - h * 0.18f)
        cubicTo(c.x - h * 0.26f, c.y - h * 0.30f, c.x - h * 0.22f, c.y - h * 0.58f, c.x, c.y - h * 0.62f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFF0A0F24), Color.Black), c.y - h * 0.6f, c.y + h * 0.6f))
    drawPath(body, Hunter.Cyan.copy(alpha = 0.45f), style = Stroke(2f))
    drawCircle(Hunter.Cyan, h * 0.025f, Offset(c.x - h * 0.07f, c.y - h * 0.40f))
    drawCircle(Hunter.Cyan, h * 0.025f, Offset(c.x + h * 0.07f, c.y - h * 0.40f))
}

/** Hexagon rank badge with the rank letter; locked badges are dimmed. */
@Composable
fun RankBadge(rank: Rank, size: Dp = 72.dp, locked: Boolean = false, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val col = when (rank) {
        Rank.E -> Hunter.TextDim; Rank.D -> Hunter.Green; Rank.C -> Hunter.Cyan; Rank.B -> Hunter.Blue
        Rank.A -> Hunter.Violet; Rank.S -> Hunter.Gold; Rank.NATIONAL -> Hunter.Red
    }.let { if (locked) it.copy(alpha = 0.35f) else it }
    val letter = if (rank == Rank.NATIONAL) "N" else rank.name
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2); val r = this.size.minDimension / 2 * 0.92f
        val hex = Path().apply { for (i in 0 until 6) { val a = (PI / 3 * i - PI / 2).toFloat(); val x = c.x + r * cos(a); val y = c.y + r * sin(a); if (i == 0) moveTo(x, y) else lineTo(x, y) }; close() }
        drawPath(hex, Brush.radialGradient(listOf(col.copy(alpha = 0.30f), Hunter.Panel), c, r))
        drawPath(hex, col, style = Stroke(4f))
        val layout = tm.measure(letter, TextStyle(color = col, fontSize = (this.size.minDimension / 2.6f / density).sp, fontWeight = FontWeight.Black))
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
    }
}

/** Ring progress used for XP-to-next-level and daily completion. */
@Composable
fun RingProgress(fraction: Float, size: Dp = 84.dp, color: Color = Hunter.Cyan, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 8.dp.toPx()
            val tl = Offset(s / 2, s / 2); val sz = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
            drawArc(Hunter.Line, 0f, 360f, false, tl, sz, style = Stroke(s))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, sz, style = Stroke(s, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        content()
    }
}
