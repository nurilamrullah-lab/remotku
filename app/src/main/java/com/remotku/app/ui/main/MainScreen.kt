package com.remotku.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.remotku.app.ui.ir.IrScreen
import com.remotku.app.ui.ir.IrUiState
import com.remotku.app.ui.ir.IrViewModel
import com.remotku.app.ui.theme.RemotkuColors
import com.remotku.app.ui.tv.TvScreen
import com.remotku.app.ui.tv.TvViewModel
import kotlinx.coroutines.flow.merge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(container: AppContainer) {
    val tvViewModel: TvViewModel = viewModel(
        factory = viewModelFactory { initializer { TvViewModel(container.discovery, container.tvRemote) } },
    )
    val irViewModel: IrViewModel = viewModel(
        factory = viewModelFactory { initializer { IrViewModel(container.irProfiles, container.irTransmitter) } },
    )
    val tvState by tvViewModel.state.collectAsStateWithLifecycle()
    val irState by irViewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHaptics.current

    LaunchedEffect(Unit) {
        merge(tvViewModel.events, irViewModel.events).collect {
            haptics.perform(HapticType.Reject)
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Remotku", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                ConnectionStatusBar(tab, tvState.connection, irState)
                PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    Tab(
                        selected = tab == 0,
                        onClick = { haptics.perform(HapticType.Tick); tab = 0 },
                        text = { Text("Smart TV (WiFi)") },
                        icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { haptics.perform(HapticType.Tick); tab = 1 },
                        text = { Text("Inframerah (IR)") },
                        icon = { Icon(Icons.Filled.SettingsRemote, contentDescription = null) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> TvScreen(tvState, tvViewModel::onIntent)
                else -> IrScreen(irState, irViewModel::onIntent)
            }
        }
    }
}

@Composable
private fun ConnectionStatusBar(tab: Int, connection: TvConnectionState, ir: IrUiState) {
    val (color, text) = if (tab == 0) {
        when (connection) {
            TvConnectionState.Idle -> RemotkuColors.TextMuted to "Tidak terhubung ke TV"
            is TvConnectionState.Connecting -> RemotkuColors.Warning to "${connection.device.name}: ${connection.message}"
            is TvConnectionState.AwaitingPairingCode -> RemotkuColors.Warning to "Masukkan kode pairing dari ${connection.device.name}"
            is TvConnectionState.Connected -> RemotkuColors.Online to buildString {
                append("Terhubung: ${connection.device.name} · ${connection.device.host} · ${connection.mode.label}")
                connection.volume?.let { append(" · Vol ${it.level}/${it.max}${if (it.muted) " (bisu)" else ""}") }
            }
            is TvConnectionState.Failed -> RemotkuColors.Power to connection.message
        }
    } else {
        val profile = ir.selectedProfile
        when {
            !ir.hasIrEmitter -> RemotkuColors.Power to "IR blaster tidak terdeteksi di HP ini"
            profile == null -> RemotkuColors.Warning to "IR blaster siap · pilih perangkat"
            else -> RemotkuColors.Online to "IR blaster siap · ${profile.brand} ${profile.model}"
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(RemotkuColors.Surface1)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
