package com.cluehaven.puzzlegames.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity

val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalGameFeedback = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun PuzzlePage(padding: PaddingValues, title: String, back: () -> Unit,
    footer: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        if (maxHeight >= 620.dp && LocalDensity.current.fontScale <= 1.3f) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                GameHeader(title, back)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                Column(Modifier.padding(top = 12.dp, bottom = 16.dp)) { footer() }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GameHeader(title, back)
                content()
                footer()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun ToolButton(label: String, symbol: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, enabled = enabled, modifier = modifier,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionSymbol(symbol, if (enabled) color else color.copy(alpha = .3f))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = if (enabled) color else color.copy(alpha = .3f))
        }
    }
}

@Composable
fun GameHeader(title: String, back: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = back, modifier = Modifier.align(Alignment.CenterStart)
            .semantics { contentDescription = "Back" }) {
            val color = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(20.dp)) {
                val path = Path().apply {
                    moveTo(size.width * .65f, size.height * .2f)
                    lineTo(size.width * .35f, size.height * .5f)
                    lineTo(size.width * .65f, size.height * .8f)
                }
                drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusPill(label: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)) {
        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun CompletionCard(title: String, detail: String, action: String, onNext: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("✓  $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Composable
fun NavigationSymbol(id: String, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(23.dp)) {
        val w = size.width
        val h = size.height
        when (id) {
            "home" -> {
                val roof = Path().apply { moveTo(0f, h * .42f); lineTo(w / 2, 0f); lineTo(w, h * .42f)
                    lineTo(w * .87f, h * .42f); lineTo(w * .87f, h); lineTo(w * .62f, h)
                    lineTo(w * .62f, h * .65f); lineTo(w * .38f, h * .65f); lineTo(w * .38f, h)
                    lineTo(w * .13f, h); lineTo(w * .13f, h * .42f); close() }
                drawPath(roof, color)
            }
            "progress" -> repeat(4) { bar ->
                val top = h * (0.65f - bar * .18f)
                drawLine(color, Offset(w * (bar + .5f) / 4, h), Offset(w * (bar + .5f) / 4, top), w / 7)
            }
            else -> {
                drawCircle(color, w * .31f, style = Stroke(3.dp.toPx()))
                repeat(8) { tooth ->
                    val angle = tooth * Math.PI / 4
                    val vector = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                    drawLine(color, center + vector * (w * .29f), center + vector * (w * .48f), 3.dp.toPx())
                }
            }
        }
    }
}

@Composable
fun PuzzleAction(label: String, symbol: String, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val text = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp), color = color, contentColor = text,
        border = androidx.compose.foundation.BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            ActionSymbol(symbol, if (enabled) text else text.copy(alpha = .35f))
            Text(label, style = MaterialTheme.typography.labelLarge,
                color = if (enabled) text else text.copy(alpha = .35f))
        }
    }
}

@Composable
fun ActionSymbol(symbol: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        when (symbol) {
            "hint" -> {
                drawCircle(color, w * .26f, Offset(w * .5f, h * .35f), style = Stroke(1.5.dp.toPx()))
                drawLine(color, Offset(w * .35f, h * .58f), Offset(w * .42f, h * .82f), 1.5.dp.toPx())
                drawLine(color, Offset(w * .65f, h * .58f), Offset(w * .58f, h * .82f), 1.5.dp.toPx())
                drawLine(color, Offset(w * .42f, h * .92f), Offset(w * .58f, h * .92f), 1.5.dp.toPx())
            }
            "undo", "new" -> {
                drawArc(color, -70f, 280f, false, Offset(w * .2f, h * .2f),
                    androidx.compose.ui.geometry.Size(w * .65f, h * .65f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                val arrow = Path().apply {
                    moveTo(w * .2f, h * .08f); lineTo(w * .12f, h * .35f); lineTo(w * .4f, h * .35f)
                }
                drawPath(arrow, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
            "erase" -> {
                val eraser = Path().apply {
                    moveTo(w * .12f, h * .58f); lineTo(w * .54f, h * .12f)
                    lineTo(w * .88f, h * .45f); lineTo(w * .48f, h * .88f)
                    lineTo(w * .36f, h * .88f); close()
                }
                drawPath(eraser, color, style = Stroke(1.4.dp.toPx()))
                drawLine(color, Offset(w * .1f, h * .94f), Offset(w * .9f, h * .94f), 1.4.dp.toPx())
            }
            "notes" -> {
                val pen = Path().apply {
                    moveTo(w * .16f, h * .84f); lineTo(w * .25f, h * .57f)
                    lineTo(w * .73f, h * .09f); lineTo(w * .91f, h * .27f)
                    lineTo(w * .43f, h * .75f); close()
                }
                drawPath(pen, color, style = Stroke(1.4.dp.toPx()))
            }
            else -> {
                drawLine(color, Offset(w * .25f, h * .5f), Offset(w * .75f, h * .5f), 1.5.dp.toPx())
                drawLine(color, Offset(w * .5f, h * .25f), Offset(w * .5f, h * .75f), 1.5.dp.toPx())
            }
        }
    }
}
