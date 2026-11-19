package com.remotku.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remotku.app.core.haptics.HapticType
import com.remotku.app.core.haptics.LocalHaptics
import com.remotku.app.ui.theme.RemotkuColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Remote key with haptic feedback on press-down (not release), which is what makes a
 * virtual remote feel responsive. [repeatOnHold] auto-repeats for volume/D-Pad style keys.
 */
@Composable
fun RemoteButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    containerColor: Color = RemotkuColors.Surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    haptic: HapticType = HapticType.Click,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    val alpha = if (enabled) 1f else 0.35f

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (pressed) RemotkuColors.SurfaceStrong else containerColor.copy(alpha = containerColor.alpha * alpha))
            .border(1.dp, RemotkuColors.Outline.copy(alpha = alpha), shape)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                onClick { if (enabled) currentOnClick(); enabled }
            }
            .pointerInput(enabled, repeatOnHold, onLongPress != null) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.perform(haptic)
                        if (repeatOnHold) {
                            var repeated = false
                            val job = scope.launch {
                                delay(420)
                                while (isActive) {
                                    repeated = true
                                    currentOnClick()
                                    haptics.perform(HapticType.Tick)
                                    delay(110)
                                }
                            }
                            val released = tryAwaitRelease()
                            job.cancel()
                            if (released && !repeated) currentOnClick()
                        } else {
                            tryAwaitRelease()
                        }
                        pressed = false
                    },
                    onTap = if (repeatOnHold) null else { _ -> currentOnClick() },
                    onLongPress = if (!repeatOnHold && onLongPress != null) {
                        { _ ->
                            haptics.perform(HapticType.Heavy)
                            currentOnLongPress?.invoke()
                        }
                    } else {
                        null
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor.copy(alpha = alpha),
        ) { content() }
    }
}

@Composable
fun IconRemoteButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    containerColor: Color = RemotkuColors.Surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    haptic: HapticType = HapticType.Click,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    showLabel: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteButton(
            onClick = onClick,
            contentDescription = label,
            modifier = Modifier.size(size),
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            haptic = haptic,
            enabled = enabled,
            repeatOnHold = repeatOnHold,
        ) { Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.42f)) }
        if (showLabel) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

enum class DPadDirection { UP, DOWN, LEFT, RIGHT, CENTER }

/** Circular D-Pad: four arc keys around a central OK key, all with repeat-on-hold except OK. */
@Composable
fun DPad(
    onPress: (DPadDirection) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 250.dp,
    enabled: (DPadDirection) -> Boolean = { true },
) {
    val keySize = size * 0.30f
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(RemotkuColors.Surface)
            .border(1.dp, RemotkuColors.Outline, CircleShape),
    ) {
        @Composable
        fun Arrow(direction: DPadDirection, icon: ImageVector, align: Alignment) {
            RemoteButton(
                onClick = { onPress(direction) },
                contentDescription = direction.name,
                modifier = Modifier.align(align).padding(8.dp).size(keySize),
                shape = CircleShape,
                containerColor = Color.Transparent,
                haptic = HapticType.Click,
                repeatOnHold = true,
                enabled = enabled(direction),
            ) { Icon(icon, contentDescription = null, modifier = Modifier.size(keySize * 0.6f)) }
        }
        Arrow(DPadDirection.UP, Icons.Filled.KeyboardArrowUp, Alignment.TopCenter)
        Arrow(DPadDirection.DOWN, Icons.Filled.KeyboardArrowDown, Alignment.BottomCenter)
        Arrow(DPadDirection.LEFT, Icons.AutoMirrored.Filled.KeyboardArrowLeft, Alignment.CenterStart)
        Arrow(DPadDirection.RIGHT, Icons.AutoMirrored.Filled.KeyboardArrowRight, Alignment.CenterEnd)
        RemoteButton(
            onClick = { onPress(DPadDirection.CENTER) },
            contentDescription = "OK",
            modifier = Modifier.align(Alignment.Center).size(size * 0.40f),
            shape = CircleShape,
            containerColor = RemotkuColors.PrimarySoft,
            contentColor = RemotkuColors.Primary,
            haptic = HapticType.Heavy,
            enabled = enabled(DPadDirection.CENTER),
        ) { Text("OK", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
    }
}

/**
 * Touch surface. In [gestureMode] drags are quantized into D-Pad steps (with a haptic tick per
 * step); otherwise raw deltas are reported for a pointer. Taps map to OK / click.
 */
@Composable
fun Trackpad(
    gestureMode: Boolean,
    onStep: (DPadDirection) -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    onTap: () -> Unit,
    onScroll: (steps: Int) -> Unit,
    modifier: Modifier = Modifier,
    cursor: Pair<Float, Float>? = null,
) {
    val haptics = LocalHaptics.current
    val currentOnStep by rememberUpdatedState(onStep)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(RemotkuColors.Surface)
            .border(1.dp, RemotkuColors.Outline, shape),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(end = 44.dp)
                .pointerInput(gestureMode) {
                    detectTapGestures(onTap = {
                        haptics.perform(HapticType.Click)
                        currentOnTap()
                    })
                }
                .pointerInput(gestureMode) {
                    val stepPx = 56.dp.toPx()
                    var acc = Offset.Zero
                    detectDragGestures(
                        onDragStart = { acc = Offset.Zero },
                        onDrag = { change, drag ->
                            change.consume()
                            if (gestureMode) {
                                acc += drag
                                while (abs(acc.x) >= stepPx || abs(acc.y) >= stepPx) {
                                    val horizontal = abs(acc.x) > abs(acc.y)
                                    val dir = if (horizontal) {
                                        if (acc.x > 0) DPadDirection.RIGHT else DPadDirection.LEFT
                                    } else {
                                        if (acc.y > 0) DPadDirection.DOWN else DPadDirection.UP
                                    }
                                    acc = if (horizontal) {
                                        Offset(acc.x - stepPx * kotlin.math.sign(acc.x), 0f)
                                    } else {
                                        Offset(0f, acc.y - stepPx * kotlin.math.sign(acc.y))
                                    }
                                    haptics.perform(HapticType.Tick)
                                    currentOnStep(dir)
                                }
                            } else {
                                currentOnMove(drag.x, drag.y)
                            }
                        },
                    )
                },
        ) {
            Text(
                if (gestureMode) "Geser untuk navigasi · ketuk = OK" else "Geser untuk menggerakkan pointer · ketuk = klik",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
            if (cursor != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(width = 96.dp, height = 54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(RemotkuColors.Text)
                        .border(1.dp, RemotkuColors.Outline, RoundedCornerShape(6.dp)),
                ) {
                    Box(
                        Modifier
                            .padding(start = (cursor.first * 90).dp, top = (cursor.second * 48).dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(RemotkuColors.Primary),
                    )
                }
            }
        }
        // Scroll strip on the right edge.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(vertical = 16.dp, horizontal = 8.dp)
                .fillMaxHeight()
                .width(28.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RemotkuColors.SurfaceMuted)
                .pointerInput(Unit) {
                    val stepPx = 40.dp.toPx()
                    var acc = 0f
                    detectDragGestures(onDragStart = { acc = 0f }) { change, drag ->
                        change.consume()
                        acc += drag.y
                        val steps = (acc / stepPx).toInt()
                        if (steps != 0) {
                            acc -= steps * stepPx
                            haptics.perform(HapticType.Tick)
                            currentOnScroll(-steps)
                        }
                    }
                },
        ) {
            Text("⇅", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
