package com.remotku.app.ui.ir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remotku.app.domain.ir.IrCategory
import com.remotku.app.domain.ir.SavedRemote
import com.remotku.app.ui.theme.RemotkuColors

private val CardShape = RoundedCornerShape(20.dp)

fun Modifier.card(shape: androidx.compose.ui.graphics.Shape = CardShape): Modifier =
    clip(shape).background(RemotkuColors.Surface).border(1.dp, RemotkuColors.Outline, shape)

/** Landing screen: saved remotes plus the Smart TV entry point. */
@Composable
fun HomeScreen(
    saved: List<SavedRemote>,
    hasIrEmitter: Boolean,
    smartTvStatus: String,
    onIntent: (IrIntent) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SavedRemote?>(null) }
    var pendingRename by remember { mutableStateOf<SavedRemote?>(null) }
    pendingRename?.let { remote ->
        RenameRemoteDialog(remote, onDismiss = { pendingRename = null }) { name ->
            onIntent(IrIntent.RenameRemote(remote.id, name))
            pendingRename = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!hasIrEmitter) {
                item { NoticeCard("HP ini tidak memiliki IR blaster. Remote IR tetap bisa disimpan, tetapi sinyal tidak dapat dipancarkan. Gunakan Smart TV (WiFi) untuk TV Android.") }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().card().clickable { onIntent(IrIntent.OpenAdd(tab = 1)) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(RemotkuColors.PrimarySoft),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Wifi, null, tint = RemotkuColors.Primary) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Smart TV (WiFi)", style = MaterialTheme.typography.titleMedium)
                        Text(smartTvStatus, style = MaterialTheme.typography.bodySmall, color = RemotkuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = RemotkuColors.TextMuted)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Remote saya", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (saved.isNotEmpty()) Text("${saved.size} remote", style = MaterialTheme.typography.labelMedium, color = RemotkuColors.TextMuted)
                }
            }
            if (saved.isEmpty()) {
                item { EmptyRemotes { onIntent(IrIntent.OpenAdd()) } }
            }
            items(saved, key = { it.id }) { remote ->
                Row(
                    Modifier.fillMaxWidth().card().clickable { onIntent(IrIntent.OpenRemote(remote.id)) }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(remote.category)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(remote.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (remote.customName != null) "${remote.defaultTitle} · " else "") + "IR · Kode ${remote.codeIndex} dari ${remote.codeCount}",
                            style = MaterialTheme.typography.bodySmall, color = RemotkuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { pendingRename = remote }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Ubah nama ${remote.title}", tint = RemotkuColors.TextMuted)
                    }
                    IconButton(onClick = { pendingDelete = remote }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus ${remote.title}", tint = RemotkuColors.TextMuted)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onIntent(IrIntent.OpenAdd()) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Tambah remote") },
            containerColor = RemotkuColors.Primary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    pendingDelete?.let { remote ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus remote?") },
            text = { Text("${remote.title} akan dihapus dari daftar remote.") },
            confirmButton = {
                TextButton(onClick = { onIntent(IrIntent.DeleteRemote(remote.id)); pendingDelete = null }) {
                    Text("Hapus", color = RemotkuColors.Power)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } },
        )
    }
}

@Composable
fun RenameRemoteDialog(remote: SavedRemote, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(remote.id) { mutableStateOf(remote.customName.orEmpty()) }
    val save = { onSave(name.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah nama remote") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    placeholder = { Text(remote.defaultTitle) },
                    label = { Text("Nama remote") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Contoh: TV Ruang Tamu, AC Ruang Kerja, Kipas Kamar Tidur. Kosongkan untuk memakai \"${remote.defaultTitle}\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = RemotkuColors.TextMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = save) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private const val MAX_NAME_LENGTH = 40

@Composable
private fun EmptyRemotes(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().card().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(RemotkuColors.PrimarySoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.SettingsRemote, null, tint = RemotkuColors.Primary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Belum ada remote", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pilih jenis perangkat dan merek, lalu uji tombol Power untuk menemukan kode yang cocok.",
            style = MaterialTheme.typography.bodyMedium,
            color = RemotkuColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Button(onClick = onAdd) { Text("Tambah remote") }
    }
}

@Composable
fun NoticeCard(text: String, color: Color = RemotkuColors.Warning) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = RemotkuColors.Text)
    }
}

/** "IR Remote" tab of Add Controller: device category grid. */
@Composable
fun CategoryGrid(hasIrEmitter: Boolean, onPick: (IrCategory) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!hasIrEmitter) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                NoticeCard("IR blaster tidak terdeteksi. Anda tetap bisa menelusuri database, tetapi tes tombol tidak akan memancarkan sinyal.")
            }
        }
        items(IrCategory.entries, key = { it.name }) { category ->
            Column(
                Modifier.card(RoundedCornerShape(18.dp)).clickable { onPick(category) }.padding(vertical = 18.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CategoryBadge(category, size = 48.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    category.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                )
            }
        }
    }
}
