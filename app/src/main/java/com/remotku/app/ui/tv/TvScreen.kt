package com.remotku.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.remotku.app.core.haptics.HapticType
import com.remotku.app.core.haptics.LocalHaptics
import com.remotku.app.domain.tv.TransportMode
import com.remotku.app.domain.tv.TvConnectionState
import com.remotku.app.domain.tv.TvDevice
import com.remotku.app.ui.theme.RemotkuColors

@Composable
fun TvScreen(state: TvUiState, onIntent: (TvIntent) -> Unit) {
    when (val connection = state.connection) {
        is TvConnectionState.Connected -> TvRemoteScreen(connection, state.cursor, onIntent)
        else -> DeviceListScreen(state, onIntent)
    }
    val connection = state.connection
    if (connection is TvConnectionState.AwaitingPairingCode) {
        PairingDialog(connection, onIntent)
    }
}

@Composable
private fun DeviceListScreen(state: TvUiState, onIntent: (TvIntent) -> Unit) {
    var showManual by remember { mutableStateOf(false) }
    var modePicker by remember { mutableStateOf<TvDevice?>(null) }
    val haptics = LocalHaptics.current
    val connection = state.connection

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Perangkat di jaringan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    state.networkLabel?.let { "Subnet $it · mDNS + port 6466/6467/5555" } ?: "Tidak ada jaringan WiFi/LAN",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                haptics.perform(HapticType.Click)
                onIntent(if (state.isScanning) TvIntent.StopScan else TvIntent.StartScan)
            }) {
                if (state.isScanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = "Pindai ulang")
            }
            IconButton(onClick = { haptics.perform(HapticType.Click); showManual = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah IP manual")
            }
        }
        if (state.isScanning) {
            LinearProgressIndicator(progress = { state.scanProgress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
            Spacer(Modifier.height(8.dp))
        }
        if (connection is TvConnectionState.Connecting) {
            InfoCard(connection.message, RemotkuColors.Warning, busy = true)
        } else if (connection is TvConnectionState.Failed) {
            InfoCard(connection.message, RemotkuColors.Power)
        }
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            if (state.devices.isEmpty()) {
                item {
                    Text(
                        if (state.isScanning) "Mencari Android TV / Google TV / Android Box…"
                        else "Belum ada perangkat. Pastikan TV menyala dan berada di WiFi yang sama, atau tambahkan IP manual.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(state.devices, key = { it.host }) { device ->
                DeviceRow(
                    device = device,
                    busy = (connection as? TvConnectionState.Connecting)?.device?.host == device.host,
                    onClick = {
                        haptics.perform(HapticType.Click)
                        onIntent(TvIntent.Connect(device))
                    },
                    onLongClick = { modePicker = device },
                    onForget = { onIntent(TvIntent.Forget(device)) },
                )
            }
            item {
                Text(
                    "Tips: tekan lama ikon TV untuk memilih mode koneksi (Android TV Remote / ADB).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp).navigationBarsPadding(),
                )
            }
        }
    }

    if (showManual) {
        ManualIpDialog(
            onDismiss = { showManual = false },
            onConnect = { host, name, mode ->
                showManual = false
                onIntent(TvIntent.AddManual(host, name, mode))
            },
        )
    }
    modePicker?.let { device ->
        AlertDialog(
            onDismissRequest = { modePicker = null },
            title = { Text("Mode koneksi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransportMode.entries.forEach { mode ->
                        OutlinedButton(
                            onClick = {
                                modePicker = null
                                onIntent(TvIntent.Connect(device, mode))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(mode.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { modePicker = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun InfoCard(message: String, color: androidx.compose.ui.graphics.Color, busy: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
            Spacer(Modifier.width(10.dp))
        }
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DeviceRow(
    device: TvDevice,
    busy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onForget: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RemotkuColors.Surface1)
            .border(1.dp, RemotkuColors.Outline, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(RemotkuColors.Surface3),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Tv, contentDescription = null) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                listOfNotNull(device.host, device.model).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                Badge(if (device.isOnline) "Online" else "Offline", if (device.isOnline) RemotkuColors.Online else RemotkuColors.TextMuted)
                if (device.supportsAtvRemote) Badge("ATV Remote", RemotkuColors.Primary)
                if (device.supportsAdb) Badge("ADB", RemotkuColors.Warning)
                if (device.castPort != null) Badge("Cast", RemotkuColors.TextMuted)
                if (device.isPaired) Badge("Terpasang", RemotkuColors.Online)
            }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (device.isSaved) {
            IconButton(onClick = onForget) { Icon(Icons.Filled.Delete, contentDescription = "Lupakan") }
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ManualIpDialog(onDismiss: () -> Unit, onConnect: (String, String?, TransportMode) -> Unit) {
    var host by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TransportMode.AUTO) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah IP manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Untuk TV di subnet lain atau yang tidak terdeteksi otomatis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Alamat IP (mis. 192.168.1.20)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama (opsional)") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransportMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(if (m == TransportMode.ATV_REMOTE) "ATV" else m.label.substringBefore(" ")) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConnect(host, name.ifBlank { null }, mode) }, enabled = host.isNotBlank()) { Text("Hubungkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun PairingDialog(state: TvConnectionState.AwaitingPairingCode, onIntent: (TvIntent) -> Unit) {
    var code by remember { mutableStateOf("") }
    val haptics = LocalHaptics.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Pairing dengan ${state.device.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Masukkan 6 karakter kode yang tampil di layar TV.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it) } },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { haptics.perform(HapticType.Confirm); onIntent(TvIntent.SubmitPairingCode(code)) },
                enabled = code.length == 6,
            ) { Text("Pasangkan") }
        },
        dismissButton = { TextButton(onClick = { onIntent(TvIntent.CancelPairing) }) { Text("Batal") } },
    )
}
