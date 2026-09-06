/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import java.util.Locale

@Immutable
data class PowerInsightAccents(
    val charge: Color,
    val chargeContainer: Color,
    val drain: Color,
    val drainContainer: Color,
    val sleep: Color,
    val sleepContainer: Color,
    val awake: Color,
    val awakeContainer: Color,
    val health: Color,
    val healthContainer: Color,
)

private val LightAccents = PowerInsightAccents(
    charge = Color(0xFF1B6B3A),
    chargeContainer = Color(0xFFB8F2C8),
    drain = Color(0xFFA4342A),
    drainContainer = Color(0xFFFFDAD5),
    sleep = Color(0xFF52398F),
    sleepContainer = Color(0xFFE7DEFF),
    awake = Color(0xFF7A5900),
    awakeContainer = Color(0xFFFFDF9B),
    health = Color(0xFF9C3F5D),
    healthContainer = Color(0xFFFFD9E2),
)

private val DarkAccents = PowerInsightAccents(
    charge = Color(0xFF7DD69B),
    chargeContainer = Color(0xFF00522A),
    drain = Color(0xFFFFB4A8),
    drainContainer = Color(0xFF83201A),
    sleep = Color(0xFFCBBEFF),
    sleepContainer = Color(0xFF3A2075),
    awake = Color(0xFFEFC048),
    awakeContainer = Color(0xFF5C4200),
    health = Color(0xFFFFB0C8),
    healthContainer = Color(0xFF7D2947),
)

val LocalPowerInsightAccents = staticCompositionLocalOf { LightAccents }

val accents: PowerInsightAccents
    @Composable
    @ReadOnlyComposable
    get() = LocalPowerInsightAccents.current

@Composable
fun PowerInsightAccentTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPowerInsightAccents provides if (dark) DarkAccents else LightAccents,
        content = content,
    )
}

object PowerInsightMotion {
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 700f)

    fun <T> spatialDefault(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 380f)

    fun <T> spatialSlow(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 200f)

    fun <T> effectsFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)

    fun <T> effectsDefault(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)
}

object PowerInsightType {
    val displayMetric: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )

    val metric: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.25).sp,
        )

    val tileMetric: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
        )

    val sectionTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
        )

    val groupHeader: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
        )
}

private val GroupOuterCorner = 22.dp
private val GroupInnerCorner = 6.dp

fun groupedRowShape(index: Int, count: Int): Shape {
    if (count <= 1) return RoundedCornerShape(GroupOuterCorner)
    return when (index) {
        0 -> RoundedCornerShape(
            topStart = GroupOuterCorner, topEnd = GroupOuterCorner,
            bottomStart = GroupInnerCorner, bottomEnd = GroupInnerCorner,
        )
        count - 1 -> RoundedCornerShape(
            topStart = GroupInnerCorner, topEnd = GroupInnerCorner,
            bottomStart = GroupOuterCorner, bottomEnd = GroupOuterCorner,
        )
        else -> RoundedCornerShape(GroupInnerCorner)
    }
}

object PowerInsightShapes {
    val hero = RoundedCornerShape(28.dp)
    val card = RoundedCornerShape(24.dp)
    val tile = RoundedCornerShape(20.dp)
    val chip = RoundedCornerShape(12.dp)

    val group = RoundedCornerShape(GroupOuterCorner)

    val groupRow = RoundedCornerShape(GroupInnerCorner)
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%dh %02dm", hours, minutes % 60)
    } else {
        String.format(Locale.getDefault(), "%dm %02ds", minutes, totalSeconds % 60)
    }
}

fun formatDurationPrecise(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60
    return when {
        hours > 0 -> String.format(Locale.getDefault(), "%dh %dm %ds", hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.getDefault(), "%dm %ds", minutes, seconds)
        else -> String.format(Locale.getDefault(), "%ds", seconds)
    }
}

fun formatDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    val format = java.text.SimpleDateFormat("h:mm a, d MMM", Locale.getDefault())
    return format.format(java.util.Date(timestamp))
}

fun formatHourLabel(hour: Int): String =
    String.format(Locale.getDefault(), "%02d:00", hour)

fun formatPercent(value: Float, decimals: Int = 1): String =
    String.format(Locale.getDefault(), "%.${decimals}f%%", value)

fun formatRate(percentPerHour: Float): String =
    String.format(Locale.getDefault(), "%.1f%%/h", percentPerHour)

fun formatMah(mah: Int): String =
    String.format(Locale.getDefault(), "%d mAh", mah)

fun formatMahPrecise(mah: Double): String =
    String.format(Locale.getDefault(), "%.1f mAh", mah)

fun formatMilliamps(ma: Int): String =
    String.format(Locale.getDefault(), "%d mA", ma)

fun formatWatts(watts: Float): String =
    String.format(Locale.getDefault(), "%.2f W", watts)

private const val CYCLE_BUDGET = 800f
private const val CYCLE_MAX_PENALTY = 35f
private const val CAPACITY_WEIGHT = 0.7f
private const val CYCLE_WEIGHT = 0.3f

@Immutable
data class BatteryHealthBreakdown(
    val capacityHealth: Float,
    val cycleHealth: Float,
    val weightedHealth: Float,
)

fun batteryHealthBreakdown(capacityHealth: Float, cycleCount: Int): BatteryHealthBreakdown {
    val penalty = ((cycleCount / CYCLE_BUDGET) * CYCLE_MAX_PENALTY).coerceIn(0f, CYCLE_MAX_PENALTY)
    val cycleHealth = (100f - penalty).coerceIn(0f, 100f)
    val weighted = ((capacityHealth * CAPACITY_WEIGHT) + (cycleHealth * CYCLE_WEIGHT))
        .coerceIn(0f, 100f)
    return BatteryHealthBreakdown(
        capacityHealth = capacityHealth.coerceIn(0f, 100f),
        cycleHealth = cycleHealth,
        weightedHealth = weighted,
    )
}
