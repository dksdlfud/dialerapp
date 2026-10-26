package com.example.mydialer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object G {
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF453A)
    val TextSecondary = Color.White.copy(alpha = 0.6f)
}

/** 반투명 유리 질감: 위쪽이 밝은 그라데이션 + 빛 반사 테두리 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.20f * strength), Color.White.copy(alpha = 0.08f * strength))
        )
    )
    .border(
        0.8.dp,
        Brush.linearGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.05f), Color.White.copy(0.30f))),
        shape
    )

/** 짙은 그라데이션 위에 은은한 컬러 글로우 → 유리 뒤로 색이 비쳐 보임 */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0A0E27), Color(0xFF1B1245), Color(0xFF0B2B4A)))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            glow(Color(0xFF8B5CF6), 0.12f, 0.10f, 0.95f, 0.60f)
            glow(Color(0xFF22D3EE), 0.98f, 0.45f, 0.85f, 0.40f)
            glow(Color(0xFFEC4899), 0.18f, 0.88f, 0.85f, 0.35f)
        }
        content()
    }
}

private fun DrawScope.glow(color: Color, fx: Float, fy: Float, fr: Float, a: Float) {
    val c = Offset(size.width * fx, size.height * fy)
    val r = size.width * fr
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center = c, radius = r),
        radius = r, center = c
    )
}

@Composable
fun GlassCircle(
    size: Dp = 76.dp,
    tint: Color? = null,
    solid: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val start = Modifier.alpha(if (enabled) 1f else 0.4f).size(size)
    val base = when {
        tint != null -> start.clip(CircleShape).background(tint)
            .border(0.8.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        solid -> start.clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
        else -> start.glass(CircleShape)
    }
    Box(
        base.background(if (pressed) Color.White.copy(alpha = 0.28f) else Color.Transparent)
            .pointerInput(enabled, onLongClick != null) {
                if (enabled) detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { click() },
                    onLongPress = if (onLongClick != null) { _: Offset -> long?.invoke() } else null
                )
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    val initial = name.trim().firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString()
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF9AA5B8), Color(0xFF5B6478)))),
        contentAlignment = Alignment.Center
    ) {
        if (initial != null) Text(initial, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium)
        else Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(size * 0.55f))
    }
}
