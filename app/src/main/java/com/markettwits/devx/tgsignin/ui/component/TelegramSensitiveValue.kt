package com.markettwits.devx.tgsignin.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun TelegramSensitiveValue(
    value: String,
    showValueDescription: String,
    hideValueDescription: String,
    modifier: Modifier = Modifier
) {
    var revealed by rememberSaveable(value) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Badge,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (revealed) {
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            } else {
                SensitiveValueParticles(
                    value = value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clearAndSetSemantics { }
                )
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    revealed = !revealed
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (revealed) hideValueDescription else showValueDescription
                )
            }
        }
    }
}

@Composable
private fun SensitiveValueParticles(
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val particles = remember(value) { createParticles(value) }
    val transition = rememberInfiniteTransition(label = "sensitive value particles")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = LinearEasing)
        ),
        label = "sensitive value particle progress"
    )

    Canvas(modifier) {
        particles.forEach { particle ->
            val wave = sin((progress + particle.phase) * 2f * PI.toFloat())
            val pulse = ((wave + 1f) / 2f)
            drawCircle(
                color = color.copy(alpha = 0.32f + pulse * 0.62f),
                radius = particle.radiusDp.dp.toPx(),
                center = Offset(
                    x = particle.x * size.width,
                    y = (particle.y + wave * particle.verticalDrift).coerceIn(
                        0.08f,
                        0.92f
                    ) * size.height
                )
            )
        }
    }
}

private fun createParticles(value: String): List<SensitiveParticle> {
    val seed = value.fold(17) { result, character -> result * 31 + character.code }
    fun fraction(index: Int, multiplier: Int, offset: Int): Float {
        val mixed = (seed.toLong() + index.toLong() * multiplier + offset).and(0x7fffffff)
        return (mixed % 1_000L) / 1_000f
    }
    return List(SENSITIVE_PARTICLE_COUNT) { index ->
        SensitiveParticle(
            x = 0.02f + fraction(index, 47, 11) * 0.96f,
            y = 0.18f + fraction(index, 71, 29) * 0.64f,
            radiusDp = 0.8f + fraction(index, 37, 43) * 1.25f,
            phase = fraction(index, 53, 67),
            verticalDrift = 0.025f + fraction(index, 31, 79) * 0.075f
        )
    }
}

private data class SensitiveParticle(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val phase: Float,
    val verticalDrift: Float
)

private const val SENSITIVE_PARTICLE_COUNT = 84
