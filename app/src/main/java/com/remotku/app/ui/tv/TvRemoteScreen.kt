package com.remotku.app.ui.tv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.remotku.app.core.haptics.HapticType
import com.remotku.app.domain.tv.PointerSupport
import com.remotku.app.domain.tv.QuickApp
import com.remotku.app.domain.tv.TvConnectionState
import com.remotku.app.ui.components.DPad
import com.remotku.app.ui.components.DPadDirection
import com.remotku.app.ui.components.IconRemoteButton
import com.remotku.app.ui.components.RemoteButton
import com.remotku.app.ui.components.Trackpad
import com.remotku.app.ui.theme.RemotkuColors
import kotlin.math.roundToInt

private fun DPadDirection.keyCode() = when (this) {
    DPadDirection.UP -> KeyEvent.KEYCODE_DPAD_UP
    DPadDirection.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
    DPadDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
    DPadDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
    DPadDirection.CENTER -> KeyEvent.KEYCODE_DPAD_CENTER
}

@Composable
fun TvRemoteScreen(
    connection: TvConnectionState.Connected,
    cursor: Pair<Float, Float>?,
    onIntent: (TvIntent) -> Unit,
) {
    var trackpadMode by rememberSaveable { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    val key: (Int) -> Unit = { onIntent(TvIntent.Key(it)) }
    val pointer = connection.capabilities.pointer

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Top row: power, input, settings, disconnect
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconRemoteButton(
                Icons.Filled.PowerSettingsNew, "Power", { key(KeyEvent.KEYCODE_POWER) },
                containerColor = RemotkuColors.Power.copy(alpha = 0.18f), contentColor = RemotkuColors.Power,
                haptic = HapticType.Heavy, showLabel = true,
            )
            IconRemoteButton(Icons.AutoMirrored.Filled.Input, "Input", { key(KeyEvent.KEYCODE_TV_INPUT) }, showLabel = true)
            IconRemoteButton(Icons.Filled.Settings, "Setelan", { key(KeyEvent.KEYCODE_SETTINGS) }, showLabel = true)
            IconRemoteButton(Icons.Filled.Close, "Putuskan", { onIntent(TvIntent.Disconnect) }, showLabel = true)
        }

        // Navigation mode switch
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !trackpadMode, onClick = { trackpadMode = false },
                label = { Text("D-Pad") }, leadingIcon = { Icon(Icons.Filled.Gamepad, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = trackpadMode, onClick = { trackpadMode = true },
                label = { Text(if (pointer == PointerSupport.GESTURE_DPAD) "Touchpad" else "Mouse") },
                leadingIcon = { Icon(Icons.Filled.Mouse, null, Modifier.size(18.dp)) },
            )
        }

        if (trackpadMode) {
            var sensitivity by remember { mutableFloatStateOf(2.2f) }
            Trackpad(
                gestureMode = pointer == PointerSupport.GESTURE_DPAD,
                onStep = { key(it.keyCode()) },
                onMove = { dx, dy ->
                    val mx = (dx * sensitivity).roundToInt()
                    val my = (dy * sensitivity).roundToInt()
                    if (mx != 0 || my != 0) onIntent(TvIntent.PointerMove(mx, my))
                },
                onTap = {
                    if (pointer == PointerSupport.GESTURE_DPAD) key(KeyEvent.KEYCODE_DPAD_CENTER)
                    else onIntent(TvIntent.PointerClick)
                },
                onScroll = { steps ->
                    if (pointer == PointerSupport.GESTURE_DPAD) {
                        repeat(kotlin.math.abs(steps)) {
                            key(if (steps > 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
                        }
                    } else {
                        onIntent(TvIntent.Scroll(steps))
                    }
                },
                cursor = cursor.takeIf { pointer == PointerSupport.TAP_AT_CURSOR },
                modifier = Modifier.fillMaxWidth().height(270.dp),
            )
            if (pointer != PointerSupport.GESTURE_DPAD) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kecepatan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    listOf(1.2f, 2.2f, 3.5f).forEachIndexed { i, s ->
                        FilterChip(
                            selected = sensitivity == s,
                            onClick = { sensitivity = s },
                            label = { Text(listOf("Pelan", "Normal", "Cepat")[i]) },
                            modifier = Modifier.padding(horizontal = 3.dp),
                        )
                    }
                }
            }
        } else {
            DPad(onPress = { key(it.keyCode()) })
        }

        // Back / Home / Menu
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconRemoteButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", { key(KeyEvent.KEYCODE_BACK) }, showLabel = true)
            IconRemoteButton(Icons.Filled.Home, "Home", { key(KeyEvent.KEYCODE_HOME) }, showLabel = true)
            IconRemoteButton(Icons.Filled.Menu, "Menu", { key(KeyEvent.KEYCODE_MENU) }, showLabel = true)
        }

        // Volume rocker | mute + keyboard | channel rocker
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Rocker(
                "VOL", Icons.Filled.Add, Icons.Filled.Remove,
                onUp = { key(KeyEvent.KEYCODE_VOLUME_UP) }, onDown = { key(KeyEvent.KEYCODE_VOLUME_DOWN) },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                IconRemoteButton(Icons.AutoMirrored.Filled.VolumeOff, "Mute", { key(KeyEvent.KEYCODE_VOLUME_MUTE) })
                IconRemoteButton(
                    Icons.Filled.Keyboard, "Keyboard", { showKeyboard = true },
                    containerColor = RemotkuColors.PrimaryDim, contentColor = RemotkuColors.Primary,
                )
            }
            Rocker(
                "CH", Icons.Filled.KeyboardArrowUp, Icons.Filled.KeyboardArrowDown,
                onUp = { key(KeyEvent.KEYCODE_CHANNEL_UP) }, onDown = { key(KeyEvent.KEYCODE_CHANNEL_DOWN) },
            )
        }

        QuickApps(onLaunch = { onIntent(TvIntent.LaunchApp(it)) })
        Spacer(Modifier.height(8.dp))
    }

    if (showKeyboard) {
        KeyboardDialog(
            onDismiss = { showKeyboard = false },
            onSend = { onIntent(TvIntent.SendText(it)) },
            onKey = key,
        )
    }
}

@Composable
private fun Rocker(label: String, upIcon: ImageVector, downIcon: ImageVector, onUp: () -> Unit, onDown: () -> Unit) {
    val shape = RoundedCornerShape(36.dp)
    Column(
        Modifier
            .width(76.dp)
            .background(RemotkuColors.Surface1, shape)
            .border(1.dp, RemotkuColors.Outline, shape)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteButton(onUp, "$label naik", Modifier.size(60.dp), shape = CircleShape, containerColor = Color.Transparent, repeatOnHold = true) {
            Icon(upIcon, null)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RemoteButton(onDown, "$label turun", Modifier.size(60.dp), shape = CircleShape, containerColor = Color.Transparent, repeatOnHold = true) {
            Icon(downIcon, null)
        }
    }
}

@Composable
private fun QuickApps(onLaunch: (QuickApp) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Aplikasi cepat", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        QuickApp.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { app ->
                    RemoteButton(
                        onClick = { onLaunch(app) },
                        contentDescription = "Buka ${app.label}",
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        containerColor = Color(app.brandColor).copy(alpha = 0.22f),
                        contentColor = Color.White,
                    ) { Text(app.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun KeyboardDialog(onDismiss: () -> Unit, onSend: (String) -> Unit, onKey: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ketik ke TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Fokuskan kolom pencarian di TV, lalu ketik di sini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("mis. film action terbaru") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (text.isNotEmpty()) onSend(text)
                        text = ""
                    }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onKey(KeyEvent.KEYCODE_DEL) }) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hapus")
                    }
                    OutlinedButton(onClick = { onKey(KeyEvent.KEYCODE_ENTER) }) { Text("Enter") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSend(text); text = "" }, enabled = text.isNotEmpty()) { Text("Kirim") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
