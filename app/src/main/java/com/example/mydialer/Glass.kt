package com.example.mydialer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared color and surface tokens for the refreshed MyDialer visual system. */
object G {
    val Background = Color(0xFF080D12)
    val Surface = Color(0xFF111820)
    val SurfaceElevated = Color(0xFF17222C)
    val SurfaceSelected = Color(0xFF24313B)
    val Accent = Color(0xFFB9F36B)
    val Green = Accent
    val Red = Color(0xFFFF637D)
    val Blue = Color(0xFF8DB8FF)
    val TextPrimary = Color(0xFFF4F7F9)
    val TextSecondary = Color(0xFF9AA8B6)
    val Border = Color.White.copy(alpha = 0.075f)
    val Divider = Color.White.copy(alpha = 0.065f)
}

/** Legacy modifier name retained so the current feature code remains source-compatible. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(G.SurfaceElevated.copy(alpha = (0.96f * strength).coerceIn(0.78f, 1f)))
    .border(1.dp, G.Border, shape)

/** Restrained charcoal background with a quiet ambient highlight instead of heavy glass glows. */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(G.Background)) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.width * 0.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF243747).copy(alpha = 0.23f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.06f),
                    radius = radius
                ),
                radius = radius,
                center = Offset(size.width * 0.9f, size.height * 0.06f)
            )
        }
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCircle(
    size: Dp = 72.dp,
    tint: Color? = null,
    solid: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val click = rememberUpdatedState(onClick)
    val long = rememberUpdatedState(onLongClick)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val shape = CircleShape
    val baseColor = when {
        tint != null -> tint
        solid -> G.Accent
        else -> G.SurfaceElevated
    }
    Box(
        Modifier.size(size)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(
                when {
                    pressed && tint == null && !solid -> G.SurfaceSelected
                    pressed -> baseColor.copy(alpha = 0.82f)
                    else -> baseColor
                }
            )
            .border(1.dp, if (solid || tint != null) Color.White.copy(alpha = 0.07f) else G.Border, shape)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { click.value() },
                onLongClick = if (onLongClick != null) ({ long.value?.invoke() }) else null
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    val palette = listOf(
        Color(0xFF355D56), Color(0xFF55477A), Color(0xFF76533D),
        Color(0xFF315B7B), Color(0xFF6B435A), Color(0xFF526442)
    )
    val bg = palette[Math.floorMod(name.hashCode(), palette.size)]
    val initial = name.trim().firstOrNull()?.takeIf { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(bg, bg.copy(alpha = 0.72f)))),
        contentAlignment = Alignment.Center
    ) {
        if (initial != null) {
            Text(
                initial,
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(size * 0.52f))
        }
    }
}
