package com.remotku.app.ui.ir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BorderOuter
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotku.app.core.haptics.HapticType
import com.remotku.app.data.ir.ac.AcFan
import com.remotku.app.domain.ir.IrCategory
import com.remotku.app.domain.ir.IrCodeSet
import com.remotku.app.domain.ir.IrKey
import com.remotku.app.ui.components.DPad
import com.remotku.app.ui.components.DPadDirection
import com.remotku.app.ui.components.IconRemoteButton
import com.remotku.app.ui.components.RemoteButton
import com.remotku.app.ui.theme.RemotkuColors

private val MEDIA_CATEGORIES = setOf(IrCategory.TV, IrCategory.STB, IrCategory.OTT, IrCategory.DVD, IrCategory.PROJECTOR, IrCategory.AUDIO)

private val KEY_ICONS: Map<IrKey, ImageVector> = mapOf(
    IrKey.POWER to Icons.Filled.PowerSettingsNew, IrKey.POWER_ON to Icons.Filled.PowerSettingsNew,
    IrKey.POWER_OFF to Icons.Filled.PowerSettingsNew, IrKey.INPUT to Icons.AutoMirrored.Filled.Input,
    IrKey.MUTE to Icons.AutoMirrored.Filled.VolumeOff, IrKey.INFO to Icons.Filled.Info,
    IrKey.BACK to Icons.AutoMirrored.Filled.ArrowBack, IrKey.HOME to Icons.Filled.Home, IrKey.MENU to Icons.Filled.Menu,
    IrKey.EXIT to Icons.Filled.Close, IrKey.PLAY to Icons.Filled.PlayArrow, IrKey.PAUSE to Icons.Filled.Pause,
    IrKey.PLAY_PAUSE to Icons.Outlined.PlayCircle, IrKey.STOP to Icons.Filled.Stop, IrKey.REWIND to Icons.Filled.FastRewind,
    IrKey.FAST_FORWARD to Icons.Filled.FastForward, IrKey.PREV to Icons.Filled.SkipPrevious, IrKey.NEXT to Icons.Filled.SkipNext,
    IrKey.TEMP_UP to Icons.Filled.Add, IrKey.TEMP_DOWN to Icons.Filled.Remove, IrKey.MODE to Icons.Outlined.Tune,
    IrKey.FAN_SPEED to Icons.Outlined.Air, IrKey.SPEED_UP to Icons.Filled.KeyboardArrowUp, IrKey.SPEED_DOWN to Icons.Filled.KeyboardArrowDown,
    IrKey.SWING to Icons.Outlined.Loop, IrKey.TIMER to Icons.Outlined.Timer, IrKey.SLEEP to Icons.Outlined.Bedtime,
    IrKey.LIGHT to Icons.Outlined.Lightbulb, IrKey.TURBO to Icons.Outlined.FlashOn, IrKey.COOL to Icons.Outlined.AcUnit,
    IrKey.DRY to Icons.Outlined.WaterDrop, IrKey.HEAT to Icons.Outlined.LocalFireDepartment, IrKey.AUTO to Icons.Outlined.Autorenew,
    IrKey.START to Icons.Filled.PlayArrow, IrKey.DOCK to Icons.Outlined.Home, IrKey.SPOT to Icons.Outlined.CenterFocusStrong,
    IrKey.EDGE to Icons.Outlined.BorderOuter,
)

@Composable
fun IrRemoteScreen(active: ActiveRemote, hasIrEmitter: Boolean, onIntent: (IrIntent) -> Unit) {
    val set = active.codeSet
    if (set == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!hasIrEmitter) NoticeCard("HP ini tidak memiliki IR blaster, sinyal tidak dapat dipancarkan.", RemotkuColors.Power)
        StatusLine(set, active.lastSignal)
        when {
            set.isStatefulAc -> AcRemote(active, onIntent)
            active.remote.category in MEDIA_CATEGORIES -> MediaRemote(set, onIntent)
            else -> ApplianceRemote(set, active.remote.category, onIntent)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatusLine(set: IrCodeSet, lastSignal: String?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RemotkuColors.Surface)
            .border(1.dp, RemotkuColors.Outline, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (lastSignal != null) RemotkuColors.Online else RemotkuColors.Outline))
        Spacer(Modifier.width(10.dp))
        Text(
            lastSignal?.let { "Terkirim: $it" } ?: "Model ${set.model}",
            style = MaterialTheme.typography.bodySmall, color = RemotkuColors.TextMuted, maxLines = 1,
        )
    }
}

@Composable
private fun KeyButton(
    set: IrCodeSet,
    key: IrKey,
    onIntent: (IrIntent) -> Unit,
    icon: ImageVector? = KEY_ICONS[key],
    haptic: HapticType = HapticType.Click,
    color: Color = RemotkuColors.Surface,
    tint: Color = RemotkuColors.Text,
    size: androidx.compose.ui.unit.Dp = 60.dp,
) {
    if (icon != null) {
        IconRemoteButton(
            icon, key.label, { onIntent(IrIntent.Press(key)) }, enabled = key in set.codes,
            containerColor = color, contentColor = tint, haptic = haptic, showLabel = true, size = size,
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteButton(
                onClick = { onIntent(IrIntent.Press(key)) }, contentDescription = key.label,
                modifier = Modifier.size(size), shape = CircleShape, enabled = key in set.codes, containerColor = color, contentColor = tint,
            ) { Text(key.label.take(3), fontWeight = FontWeight.SemiBold) }
        }
    }
}

private val MEDIA_LAYOUT_KEYS: Set<IrKey> = setOf(
    IrKey.POWER, IrKey.INPUT, IrKey.MUTE, IrKey.INFO, IrKey.UP, IrKey.DOWN, IrKey.LEFT, IrKey.RIGHT, IrKey.OK,
    IrKey.BACK, IrKey.HOME, IrKey.MENU, IrKey.EXIT, IrKey.VOL_UP, IrKey.VOL_DOWN, IrKey.CH_UP, IrKey.CH_DOWN, IrKey.EPG,
) + IrKey.DIGITS
private val MEDIA_TRANSPORT = listOf(IrKey.REWIND, IrKey.PLAY, IrKey.PAUSE, IrKey.PLAY_PAUSE, IrKey.STOP, IrKey.FAST_FORWARD, IrKey.PREV, IrKey.NEXT)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaRemote(set: IrCodeSet, onIntent: (IrIntent) -> Unit) {
    val has: (IrKey) -> Boolean = { it in set.codes }
    val press: (IrKey) -> Unit = { onIntent(IrIntent.Press(it)) }
    fun dirKey(dir: DPadDirection) = when (dir) {
        DPadDirection.UP -> IrKey.UP
        DPadDirection.DOWN -> IrKey.DOWN
        DPadDirection.LEFT -> IrKey.LEFT
        DPadDirection.RIGHT -> IrKey.RIGHT
        DPadDirection.CENTER -> IrKey.OK
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KeyButton(set, IrKey.POWER, onIntent, haptic = HapticType.Heavy, color = RemotkuColors.Power.copy(alpha = 0.10f), tint = RemotkuColors.Power)
            KeyButton(set, IrKey.INPUT, onIntent)
            KeyButton(set, IrKey.MUTE, onIntent)
            KeyButton(set, IrKey.INFO, onIntent)
        }
        DPad(onPress = { press(dirKey(it)) }, size = 230.dp, enabled = { has(dirKey(it)) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KeyButton(set, IrKey.BACK, onIntent)
            KeyButton(set, IrKey.HOME, onIntent)
            KeyButton(set, IrKey.MENU, onIntent)
            KeyButton(set, IrKey.EXIT, onIntent)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IrRocker("VOL", Icons.Filled.Add, Icons.Filled.Remove, has(IrKey.VOL_UP), { press(IrKey.VOL_UP) }, { press(IrKey.VOL_DOWN) })
            RemoteButton(
                onClick = { press(IrKey.EPG) }, contentDescription = "EPG",
                modifier = Modifier.size(width = 84.dp, height = 48.dp), enabled = has(IrKey.EPG),
            ) { Text("EPG", fontWeight = FontWeight.SemiBold) }
            IrRocker("CH", Icons.Filled.KeyboardArrowUp, Icons.Filled.KeyboardArrowDown, has(IrKey.CH_UP), { press(IrKey.CH_UP) }, { press(IrKey.CH_DOWN) })
        }
        val transport = MEDIA_TRANSPORT.filter(has)
        if (transport.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { transport.forEach { KeyButton(set, it, onIntent, size = 54.dp) } }
        }
        if (IrKey.DIGITS.any(has)) {
            FlowRow(
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IrKey.DIGITS.forEach { d ->
                    RemoteButton(
                        onClick = { press(d) }, contentDescription = d.label,
                        modifier = Modifier.size(width = 84.dp, height = 48.dp), enabled = has(d),
                    ) { Text(d.label, style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        val extras = set.codes.keys.filter { it !in MEDIA_LAYOUT_KEYS && it !in MEDIA_TRANSPORT }.sortedBy { it.ordinal }
        if (extras.isNotEmpty()) KeyGrid(set, extras, onIntent)
    }
}

/** Fans, purifiers, robot cleaners and captured AC remotes: big power keys plus every other available key. */
@Composable
private fun ApplianceRemote(set: IrCodeSet, category: IrCategory, onIntent: (IrIntent) -> Unit) {
    val powerKeys = listOf(IrKey.POWER, IrKey.START, IrKey.POWER_ON, IrKey.POWER_OFF).filter { it in set.codes }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.fillMaxWidth().card().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CategoryBadge(category, size = 56.dp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                powerKeys.forEach { key ->
                    KeyButton(
                        set, key, onIntent, haptic = HapticType.Heavy, size = 76.dp,
                        color = if (key == IrKey.POWER_ON || key == IrKey.START) RemotkuColors.Online.copy(alpha = 0.10f) else RemotkuColors.Power.copy(alpha = 0.10f),
                        tint = if (key == IrKey.POWER_ON || key == IrKey.START) RemotkuColors.Online else RemotkuColors.Power,
                    )
                }
            }
        }
        val hasDirections = listOf(IrKey.UP, IrKey.DOWN, IrKey.LEFT, IrKey.RIGHT).any { it in set.codes }
        if (category == IrCategory.ROBOT && hasDirections) {
            DPad(
                onPress = { dir ->
                    when (dir) {
                        DPadDirection.UP -> IrKey.UP
                        DPadDirection.DOWN -> IrKey.DOWN
                        DPadDirection.LEFT -> IrKey.LEFT
                        DPadDirection.RIGHT -> IrKey.RIGHT
                        DPadDirection.CENTER -> IrKey.START
                    }.let { onIntent(IrIntent.Press(it)) }
                },
                size = 210.dp,
            )
        }
        val rest = set.codes.keys.filter { it !in powerKeys && !(category == IrCategory.ROBOT && it in setOf(IrKey.UP, IrKey.DOWN, IrKey.LEFT, IrKey.RIGHT)) }
            .sortedBy { it.ordinal }
        if (rest.isNotEmpty()) KeyGrid(set, rest, onIntent)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyGrid(set: IrCodeSet, keys: List<IrKey>, onIntent: (IrIntent) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        maxItemsInEachRow = 4,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        keys.forEach { key -> Box(Modifier.width(76.dp), contentAlignment = Alignment.Center) { KeyButton(set, key, onIntent) } }
        repeat((4 - keys.size % 4) % 4) { Box(Modifier.width(76.dp)) }
    }
}

@Composable
private fun IrRocker(label: String, up: ImageVector, down: ImageVector, enabled: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    val shape = RoundedCornerShape(36.dp)
    Column(
        Modifier.width(76.dp).background(RemotkuColors.Surface, shape).border(1.dp, RemotkuColors.Outline, shape).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteButton(onUp, "$label naik", Modifier.size(56.dp), shape = CircleShape, containerColor = Color.Transparent, repeatOnHold = true, enabled = enabled) { Icon(up, null) }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RemoteButton(onDown, "$label turun", Modifier.size(56.dp), shape = CircleShape, containerColor = Color.Transparent, repeatOnHold = true, enabled = enabled) { Icon(down, null) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcRemote(active: ActiveRemote, onIntent: (IrIntent) -> Unit) {
    val ac = active.acState
    val encoder = active.acEncoder ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier.fillMaxWidth().card(RoundedCornerShape(24.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (ac.power) "${ac.temperature}°C" else "OFF",
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = if (ac.power) RemotkuColors.Text else RemotkuColors.TextMuted,
            )
            Text(
                "${ac.mode.label} · Kipas ${ac.fan.label} · Swing ${if (ac.swing) "On" else "Off"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconRemoteButton(Icons.Filled.Remove, "Suhu turun", { onIntent(IrIntent.AcChangeTemp(-1)) }, size = 64.dp, repeatOnHold = true)
            IconRemoteButton(
                Icons.Filled.PowerSettingsNew, if (ac.power) "Matikan" else "Nyalakan", { onIntent(IrIntent.AcTogglePower) },
                size = 84.dp, haptic = HapticType.Heavy,
                containerColor = if (ac.power) RemotkuColors.Online.copy(alpha = 0.12f) else RemotkuColors.Power.copy(alpha = 0.10f),
                contentColor = if (ac.power) RemotkuColors.Online else RemotkuColors.Power,
            )
            IconRemoteButton(Icons.Filled.Add, "Suhu naik", { onIntent(IrIntent.AcChangeTemp(1)) }, size = 64.dp, repeatOnHold = true)
        }
        Text("Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            encoder.supportedModes.forEach { mode ->
                FilterChip(selected = ac.mode == mode, onClick = { onIntent(IrIntent.AcSetMode(mode)) }, label = { Text(mode.label) })
            }
        }
        Text("Kecepatan kipas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            AcFan.entries.forEach { fan ->
                FilterChip(selected = ac.fan == fan, onClick = { onIntent(IrIntent.AcSetFan(fan)) }, label = { Text(fan.label) })
            }
        }
        IconRemoteButton(
            Icons.Filled.SwapVert, "Swing", { onIntent(IrIntent.AcToggleSwing) },
            size = 56.dp, showLabel = true,
            containerColor = if (ac.swing) RemotkuColors.PrimarySoft else RemotkuColors.Surface,
            contentColor = if (ac.swing) RemotkuColors.Primary else RemotkuColors.Text,
        )
        Text(
            "Rentang suhu ${encoder.minTemp}–${encoder.maxTemp}°C. Setiap tombol mengirim seluruh status AC.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
