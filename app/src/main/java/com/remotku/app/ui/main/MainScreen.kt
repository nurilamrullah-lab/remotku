package com.remotku.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.remotku.app.AppContainer
import com.remotku.app.core.haptics.HapticType
import com.remotku.app.core.haptics.LocalHaptics
import com.remotku.app.domain.tv.TvConnectionState
import com.remotku.app.ui.ir.BrandSelectScreen
import com.remotku.app.ui.ir.CategoryGrid
import com.remotku.app.ui.ir.HomeScreen
import com.remotku.app.ui.ir.IrIntent
import com.remotku.app.ui.ir.IrRemoteScreen
import com.remotku.app.ui.ir.IrUiState
import com.remotku.app.ui.ir.IrViewModel
import com.remotku.app.ui.ir.PairingScreen
import com.remotku.app.ui.ir.Route
import com.remotku.app.ui.theme.RemotkuColors
import com.remotku.app.ui.tv.TvIntent
import com.remotku.app.ui.tv.TvScreen
import com.remotku.app.ui.tv.TvUiState
import com.remotku.app.ui.tv.TvViewModel
import kotlinx.coroutines.flow.merge

@Composable
fun MainScreen(container: AppContainer) {
    val tvViewModel: TvViewModel = viewModel(
        factory = viewModelFactory { initializer { TvViewModel(container.discovery, container.tvRemote) } },
    )
    val irViewModel: IrViewModel = viewModel(
        factory = viewModelFactory { initializer { IrViewModel(container.irCatalog, container.savedRemotes, container.irTransmitter) } },
    )
    val tvState by tvViewModel.state.collectAsStateWithLifecycle()
    val irState by irViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHaptics.current

    LaunchedEffect(Unit) {
        merge(tvViewModel.events, irViewModel.events).collect {
            haptics.perform(HapticType.Reject)
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }
    RemotkuApp(irState, tvState, irViewModel::onIntent, tvViewModel::onIntent, snackbar)
}

/** Stateless app shell, also used by screenshot tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotkuApp(
    irState: IrUiState,
    tvState: TvUiState,
    onIr: (IrIntent) -> Unit,
    onTv: (TvIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val route = irState.route
    val haptics = LocalHaptics.current
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = irState.routes.size > 1) { onIr(IrIntent.Back) }

    val (title, subtitle) = when (route) {
        Route.Home -> "Remotku" to "Remote universal IR & Smart TV"
        Route.AddController -> "Tambah remote" to null
        is Route.Brands -> "Pilih merek" to route.category.label
        is Route.Pairing -> "${route.brand} ${route.category.label}" to "Pencocokan kode"
        is Route.Remote -> (irState.active?.remote?.title ?: "Remote") to irState.active?.let { a ->
            "Kode ${a.remote.codeIndex}/${a.remote.codeCount}" + (a.codeSet?.model?.let { " · $it" } ?: "")
        }
    }

    Scaffold(
        containerColor = RemotkuColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = if (route == Route.Home) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RemotkuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = {
                    if (route != Route.Home) {
                        IconButton(onClick = { haptics.perform(HapticType.Tick); onIr(IrIntent.Back) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                },
                actions = {
                    if (route is Route.Remote) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus remote") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RemotkuColors.Background, scrolledContainerColor = RemotkuColors.Background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                Route.Home -> HomeScreen(irState.saved, irState.hasIrEmitter, smartTvStatus(tvState.connection), onIr)
                Route.AddController -> AddControllerScreen(irState, tvState, onIr, onTv)
                is Route.Brands -> irState.brandList?.let { BrandSelectScreen(it, onIr) }
                is Route.Pairing -> irState.pairing?.let { PairingScreen(it, irState.hasIrEmitter, onIr) }
                is Route.Remote -> irState.active?.let { IrRemoteScreen(it, irState.hasIrEmitter, onIr) }
            }
        }
    }

    val active = irState.active
    if (confirmDelete && active != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus remote?") },
            text = { Text("${active.remote.title} akan dihapus dari daftar remote.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onIr(IrIntent.DeleteRemote(active.remote.id)) }) { Text("Hapus", color = RemotkuColors.Power) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

private fun smartTvStatus(connection: TvConnectionState): String = when (connection) {
    is TvConnectionState.Connected -> "Terhubung ke ${connection.device.name}"
    is TvConnectionState.Connecting -> "Menghubungkan ${connection.device.name}…"
    else -> "Android TV / Google TV · auto-scan, ADB, pairing"
}

@Composable
private fun AddControllerScreen(irState: IrUiState, tvState: TvUiState, onIr: (IrIntent) -> Unit, onTv: (TvIntent) -> Unit) {
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = irState.addTab, containerColor = RemotkuColors.Background) {
            Tab(
                selected = irState.addTab == 0,
                onClick = { haptics.perform(HapticType.Tick); onIr(IrIntent.SelectAddTab(0)) },
                text = { Text("IR Remote") },
                icon = { Icon(Icons.Outlined.SettingsRemote, contentDescription = null) },
            )
            Tab(
                selected = irState.addTab == 1,
                onClick = { haptics.perform(HapticType.Tick); onIr(IrIntent.SelectAddTab(1)) },
                text = { Text("Smart TV (WiFi)") },
                icon = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
            )
        }
        if (irState.addTab == 0) {
            CategoryGrid(irState.hasIrEmitter) { onIr(IrIntent.OpenCategory(it)) }
        } else {
            TvConnectionBar(tvState.connection)
            Box(Modifier.weight(1f)) { TvScreen(tvState, onTv) }
        }
    }
}

@Composable
private fun TvConnectionBar(connection: TvConnectionState) {
    val (color, text) = when (connection) {
        TvConnectionState.Idle -> RemotkuColors.TextMuted to "Tidak terhubung ke TV"
        is TvConnectionState.Connecting -> RemotkuColors.Warning to "${connection.device.name}: ${connection.message}"
        is TvConnectionState.AwaitingPairingCode -> RemotkuColors.Warning to "Masukkan kode pairing dari ${connection.device.name}"
        is TvConnectionState.Connected -> RemotkuColors.Online to buildString {
            append("Terhubung: ${connection.device.name} · ${connection.device.host} · ${connection.mode.label}")
            connection.volume?.let { append(" · Vol ${it.level}/${it.max}${if (it.muted) " (bisu)" else ""}") }
        }
        is TvConnectionState.Failed -> RemotkuColors.Power to connection.message
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
