/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

@Immutable
class SettingsGroupScope internal constructor(
    private val shape: Shape,
) {
    @Composable
    fun custom(content: @Composable (Shape) -> Unit) {
        content(shape)
    }

    @Composable
    fun toggle(
        title: String,
        summary: String? = null,
        checked: Boolean,
        enabled: Boolean = true,
        onToggle: (Boolean) -> Unit,
    ) = custom { shape ->
        GroupRow(
            shape = shape,
            enabled = enabled,
            onClick = { onToggle(!checked) },
            title = title,
            summary = summary,
            toggleState = checked,
            trailing = {
                PowerInsightSwitch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                )
            },
        )
    }

    @Composable
    fun action(
        title: String,
        summary: String? = null,
        enabled: Boolean = true,
        titleColor: Color? = null,
        trailingIcon: ImageVector? = null,
        onClick: () -> Unit,
    ) = custom { shape ->
        val trailing: (@Composable () -> Unit)? = trailingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        GroupRow(
            shape = shape,
            enabled = enabled,
            onClick = onClick,
            title = title,
            summary = summary,
            titleColor = titleColor,
            trailing = trailing,
        )
    }
}

@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable SettingsGroupScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = PowerInsightType.groupHeader,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.clip(PowerInsightShapes.group),
        ) {
            SettingsGroupScope(PowerInsightShapes.groupRow).content()
        }
    }
}

@Composable
private fun GroupRow(
    shape: Shape,
    enabled: Boolean,
    onClick: () -> Unit,
    title: String,
    summary: String?,
    titleColor: Color? = null,
    toggleState: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = PowerInsightMotion.effectsDefault(),
        label = "rowContentAlpha",
    )

    val rowSemantics = if (toggleState == null) {
        Modifier
    } else {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(toggleState == true)
        }
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .then(rowSemantics),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides LocalContentColor.current.copy(alpha = contentAlpha)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        color = (titleColor ?: MaterialTheme.colorScheme.onSurface)
                            .copy(alpha = contentAlpha),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                supportingContent = summary?.let {
                    {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = contentAlpha),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                trailingContent = trailing,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
fun PowerInsightSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        thumbContent = {
            Crossfade(targetState = checked, label = "switchThumbIcon") { isChecked ->
                Icon(
                    imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            checkedIconColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
fun ExpressiveIconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    active: Boolean = true,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val cornerPercent by animateFloatAsState(
        targetValue = if (active) 50f else 32f,
        animationSpec = PowerInsightMotion.spatialDefault(),
        label = "badgeCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.92f,
        animationSpec = PowerInsightMotion.spatialDefault(),
        label = "badgeScale",
    )
    val animatedContainer by animateColorAsState(
        targetValue = containerColor,
        animationSpec = PowerInsightMotion.effectsDefault(),
        label = "badgeContainer",
    )
    Box(
        modifier = modifier
            .size(size * scale)
            .clip(RoundedCornerShape(percent = cornerPercent.toInt()))
            .background(animatedContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.54f),
        )
    }
}

@Composable
fun SectionBadge(
    icon: ImageVector,
    tint: Color,
    size: Dp = 36.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionBadge(icon = icon, tint = iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = PowerInsightType.sectionTitle)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    subValue: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (icon != null) {
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(text = value, style = PowerInsightType.metric, color = valueColor)
            if (subValue != null) {
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = PowerInsightShapes.tile,
            color = container,
            modifier = modifier,
            content = { body() },
        )
    } else {
        Surface(
            shape = PowerInsightShapes.tile,
            color = container,
            modifier = modifier,
            content = { body() },
        )
    }
}

@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun InfoChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(PowerInsightShapes.chip)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
fun WavyMeter(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 22.dp,
    strokeWidth: Dp = 4.dp,
    contentDescription: String? = null,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = PowerInsightMotion.spatialSlow(),
        label = "wavyProgress",
    )
    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "wavyPhase")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wavyPhaseValue",
        ).value
    } else {
        0f
    }

    val description = contentDescription
    val semantics = if (description != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = description }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(semantics)) {
        val stroke = strokeWidth.toPx()
        val amp = if (animate) amplitude.toPx() else 0f
        val waveLen = wavelength.toPx().coerceAtLeast(1f)
        val centerY = size.height / 2f
        val gap = stroke * 1.5f
        val activeWidth = size.width * animatedProgress

        if (activeWidth > stroke) {
            val path = Path()
            var x = 0f
            var first = true
            while (x <= activeWidth) {
                val y = centerY + amp * sin((x / waveLen) * 2f * Math.PI.toFloat() + phase)
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += 2f
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        val trackStart = (activeWidth + gap).coerceAtMost(size.width)
        if (trackStart < size.width) {
            drawLine(
                color = trackColor,
                start = Offset(trackStart, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun ShareBar(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 6.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = PowerInsightMotion.spatialDefault(),
        label = "shareBar",
    )
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val centerY = size.height / 2f
        val gap = stroke
        val activeWidth = size.width * animated

        if (activeWidth > 0f) {
            drawLine(
                color = color,
                start = Offset(0f, centerY),
                end = Offset(activeWidth.coerceAtLeast(stroke / 2f), centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        val trackStart = (activeWidth + gap).coerceAtMost(size.width)
        if (trackStart < size.width) {
            drawLine(
                color = trackColor,
                start = Offset(trackStart, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionBadge(
            icon = icon,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 56.dp,
        )
        Text(
            text = title,
            style = PowerInsightType.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private val appIconCache = LruCache<String, ImageBitmap>(128)

@Composable
fun rememberAppIcon(packageName: String?, size: Dp = 44.dp): ImageBitmap? {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val initial = packageName?.let { appIconCache.get(it) }
    val state = produceState(initialValue = initial, packageName, sizePx) {
        val pkg = packageName
        if (pkg.isNullOrEmpty()) {
            value = null
            return@produceState
        }
        appIconCache.get(pkg)?.let {
            value = it
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(pkg).toImageBitmap(sizePx)
            }.getOrNull()
        }
        if (loaded != null) appIconCache.put(pkg, loaded)
        value = loaded
    }
    return state.value
}

private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap.asImageBitmap()
}

@Composable
fun AppIcon(
    packageName: String?,
    label: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberAppIcon(packageName, size)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
    }
}
