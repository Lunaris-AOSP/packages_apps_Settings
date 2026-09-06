/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.internal.os.PowerInsightFlowSample
import com.android.internal.os.PowerInsightHistoryBucket
import com.android.settings.R
import kotlin.math.abs
import kotlin.math.roundToInt

private const val GRID_LINES = 5

@Composable
fun PowerFlowChart(
    samples: List<PowerInsightFlowSample>,
    charging: Boolean,
    currentNow: Int,
    powerWatts: Float,
    voltage: Int,
    avgCurrent: Int,
    minCurrent: Int,
    maxCurrent: Int,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (charging) accents.charge else accents.drain
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatMilliamps(abs(currentNow)),
                    style = PowerInsightType.displayMetric,
                    color = accent,
                )
                Text(
                    text = "${formatWatts(powerWatts)} · ${voltage} mV",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InfoChip(
                text = if (samples.isEmpty()) {
                    stringResource(R.string.power_insight_samples_none)
                } else {
                    stringResource(R.string.power_insight_samples_count, samples.size)
                },
                color = accent,
                containerColor = accent.copy(alpha = 0.14f),
            )
        }

        Spacer(Modifier.height(20.dp))

        if (samples.isEmpty()) {
            EmptyChartSurface(
                message = stringResource(
                    if (charging) {
                        R.string.power_insight_chart_empty_charging
                    } else {
                        R.string.power_insight_chart_empty_discharging
                    },
                ),
                gridColor = gridColor,
            )
        } else {
            FlowTrace(
                samples = samples,
                accent = accent,
                gridColor = gridColor,
                live = live,
                charging = charging,
                avgCurrent = avgCurrent,
                minCurrent = minCurrent,
                maxCurrent = maxCurrent,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniStat(
                label = stringResource(R.string.power_insight_stat_average),
                value = formatMilliamps(abs(avgCurrent)),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(Modifier.height(28.dp), color = dividerColor)
            MiniStat(
                label = stringResource(R.string.power_insight_stat_minimum),
                value = formatMilliamps(abs(minCurrent)),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(Modifier.height(28.dp), color = dividerColor)
            MiniStat(
                label = stringResource(R.string.power_insight_stat_maximum),
                value = formatMilliamps(abs(maxCurrent)),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FlowTrace(
    samples: List<PowerInsightFlowSample>,
    accent: Color,
    gridColor: Color,
    live: Boolean,
    charging: Boolean,
    avgCurrent: Int,
    minCurrent: Int,
    maxCurrent: Int,
) {
    val magnitudes = remember(samples) { samples.map { abs(it.current).toFloat() } }
    val peak = remember(magnitudes) {
        (magnitudes.maxOrNull() ?: 0f).coerceAtLeast(100f) * 1.12f
    }

    val reveal = remember(charging) { Animatable(0f) }
    LaunchedEffect(charging) { reveal.animateTo(1f, PowerInsightMotion.spatialSlow()) }

    val pulse = if (live) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseValue",
        ).value
    } else {
        0.5f
    }

    val spoken = stringResource(
        if (charging) {
            R.string.power_insight_chart_a11y_charging
        } else {
            R.string.power_insight_chart_a11y_discharging
        },
        samples.size,
        abs(avgCurrent),
        abs(minCurrent),
        abs(maxCurrent),
    )

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .semantics { contentDescription = spoken },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                for (i in 0 until GRID_LINES) {
                    val y = h * i / (GRID_LINES - 1)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (i == GRID_LINES - 1) {
                            null
                        } else {
                            PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 6.dp.toPx()),
                            )
                        },
                    )
                }

                val visibleCount = (magnitudes.size * reveal.value)
                    .roundToInt()
                    .coerceIn(1, magnitudes.size)
                val visible = magnitudes.subList(0, visibleCount)

                val points = visible.mapIndexed { index, value ->
                    val x = if (magnitudes.size > 1) {
                        w * index / (magnitudes.size - 1).toFloat()
                    } else {
                        w / 2f
                    }
                    val y = h - (value / peak * h)
                    Offset(x, y.coerceIn(0f, h))
                }

                if (points.size == 1) {
                    drawCircle(accent, radius = 5.dp.toPx(), center = points.first())
                    return@Canvas
                }

                val line = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        val p1 = points[i - 1]
                        val p2 = points[i]
                        val midX = p1.x + (p2.x - p1.x) / 2f
                        cubicTo(midX, p1.y, midX, p2.y, p2.x, p2.y)
                    }
                }

                val fill = Path().apply {
                    addPath(line)
                    lineTo(points.last().x, h)
                    lineTo(points.first().x, h)
                    close()
                }
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                    ),
                )

                drawPath(
                    path = line,
                    color = accent,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )

                val last = points.last()
                drawCircle(accent.copy(alpha = 0.22f * pulse), radius = 12.dp.toPx(), center = last)
                drawCircle(accent, radius = 5.dp.toPx(), center = last)
            }

            Text(
                text = formatMilliamps(peak.roundToInt()),
                modifier = Modifier.align(Alignment.TopEnd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.power_insight_axis_oldest),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.power_insight_axis_now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyChartSurface(message: String, gridColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until GRID_LINES) {
                val y = size.height * i / (GRID_LINES - 1)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 6.dp.toPx()),
                    ),
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun HourlyDrainChart(
    buckets: List<PowerInsightHistoryBucket>,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return

    val drainColor = accents.drain
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val peak = remember(buckets) {
        buckets.maxOf { it.drainPercent }.coerceAtLeast(1)
    }
    val spoken = stringResource(
        R.string.power_insight_hourly_a11y,
        buckets.size,
        peak,
        formatHourLabel(buckets.maxByOrNull { it.drainPercent }?.hour ?: 0),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val fraction = (bucket.drainPercent.toFloat() / peak).coerceIn(0f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = PowerInsightMotion.spatialDefault(),
                    label = "bar${bucket.hour}",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val radius = 3.dp.toPx()
                        val barHeight = (size.height * animatedFraction)
                            .coerceAtLeast(if (bucket.drainPercent > 0) radius * 2 else 2.dp.toPx())
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(0f, 0f),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                        )
                        drawRoundRect(
                            color = drainColor,
                            topLeft = Offset(0f, size.height - barHeight),
                            size = androidx.compose.ui.geometry.Size(size.width, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatHourLabel(buckets.first().hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.power_insight_peak_percent, peak),
                style = MaterialTheme.typography.labelSmall,
                color = drainColor,
            )
            Text(
                text = formatHourLabel(buckets.last().hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
