package com.chandu.gatesystem.ai2027.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chandu.gatesystem.ai2027.ui.theme.Hunter

/** Screen frame: dark particle background, system-style top bar and a scrollable or custom body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HunterScaffold(
    title: String, onBack: (() -> Unit)? = null, animate: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}, bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    ParticleBackground(animate = animate) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Hunter.Cyan) },
                    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Hunter.Cyan) } },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Hunter.Void.copy(alpha = 0.85f))
                )
            },
            bottomBar = bottomBar,
            content = content
        )
    }
}

/** Scrollable column body with the standard padding. */
@Composable
fun ScreenColumn(pad: PaddingValues, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content
    )
}

/** "System window": bordered glowing panel with a bracketed title. */
@Composable
fun SystemPanel(title: String? = null, modifier: Modifier = Modifier, accent: Color = Hunter.Cyan, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val base = modifier.fillMaxWidth().clip(shape)
        .background(Brush.verticalGradient(listOf(Hunter.PanelHi.copy(alpha = 0.92f), Hunter.Panel.copy(alpha = 0.95f))))
        .border(BorderStroke(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.9f), Hunter.Violet.copy(alpha = 0.5f)))), shape)
    Column((if (onClick != null) base.clickable(onClick = onClick) else base).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text("[ ${title.uppercase()} ]", style = MaterialTheme.typography.labelMedium, color = accent)
        content()
    }
}

@Composable
fun GlowBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Hunter.Cyan, height: Int = 10) {
    val f = fraction.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).background(Hunter.Line.copy(alpha = 0.55f))) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
    }
}

@Composable
fun Chip(text: String, color: Color = Hunter.Cyan, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, selected: Boolean = true) {
    val m = modifier.clip(RoundedCornerShape(50)).border(1.dp, color.copy(alpha = if (selected) 0.9f else 0.35f), RoundedCornerShape(50))
        .background(color.copy(alpha = if (selected) 0.18f else 0.04f))
    Text(text, modifier = (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = if (selected) color else Hunter.TextDim)
}

@Composable
fun HunterButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Hunter.Cyan, outlined: Boolean = false) {
    if (outlined) OutlinedButton(onClick, modifier, enabled = enabled, border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.9f else 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) { Text(text, style = MaterialTheme.typography.labelLarge) }
    else Button(onClick, modifier, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF00121F))) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

enum class NoteKind(val color: Color) { INFO(Hunter.Cyan), WARN(Hunter.Orange), ERROR(Hunter.Red), OK(Hunter.Green) }

@Composable
fun Note(text: String, kind: NoteKind = NoteKind.INFO, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(kind.color.copy(alpha = 0.10f)).border(1.dp, kind.color.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(10.dp),
        style = MaterialTheme.typography.bodySmall, color = Hunter.Text)
}

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = Hunter.Text) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Hunter.TextDim, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, color: Color = Hunter.Cyan) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Hunter.PanelHi.copy(alpha = 0.8f)).border(1.dp, Hunter.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Hunter.TextDim, textAlign = TextAlign.Center)
    }
}

@Composable fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, color = Hunter.Text) }
@Composable fun Dim(text: String, modifier: Modifier = Modifier) { Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = Hunter.TextDim) }
@Composable fun Body(text: String, modifier: Modifier = Modifier, color: Color = Hunter.Text) { Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = color) }

@Composable
fun Loading(text: String = "Loading…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(color = Hunter.Cyan); Dim(text) }
    }
}
