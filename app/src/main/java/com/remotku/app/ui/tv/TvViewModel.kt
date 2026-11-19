package com.remotku.app.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotku.app.data.tv.TvRemoteRepository
import com.remotku.app.data.tv.discovery.DeviceDiscoveryRepository
import com.remotku.app.data.tv.discovery.DiscoveryUpdate
import com.remotku.app.domain.tv.DiscoverySource
import com.remotku.app.domain.tv.KeyAction
import com.remotku.app.domain.tv.QuickApp
import com.remotku.app.domain.tv.TransportMode
import com.remotku.app.domain.tv.TvConnectionState
import com.remotku.app.domain.tv.TvDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class TvUiState(
    val devices: List<TvDevice> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val networkLabel: String? = null,
    val connection: TvConnectionState = TvConnectionState.Idle,
    val cursor: Pair<Float, Float>? = null,
)

sealed interface TvIntent {
    data object StartScan : TvIntent
    data object StopScan : TvIntent
    data class Connect(val device: TvDevice, val mode: TransportMode = TransportMode.AUTO) : TvIntent
    data class AddManual(val host: String, val name: String?, val mode: TransportMode) : TvIntent
    data class Forget(val device: TvDevice) : TvIntent
    data class SubmitPairingCode(val code: String) : TvIntent
    data object CancelPairing : TvIntent
    data object Disconnect : TvIntent
    data class Key(val keyCode: Int, val action: KeyAction = KeyAction.SHORT) : TvIntent
    data class SendText(val text: String) : TvIntent
    data class LaunchApp(val app: QuickApp) : TvIntent
    data class PointerMove(val dx: Int, val dy: Int) : TvIntent
    data object PointerClick : TvIntent
    data class Scroll(val steps: Int) : TvIntent
}

class TvViewModel(
    private val discovery: DeviceDiscoveryRepository,
    private val remote: TvRemoteRepository,
) : ViewModel() {

    private val discovered = MutableStateFlow<Map<String, TvDevice>>(emptyMap())
    private val scan = MutableStateFlow(Triple(false, 0f, null as String?))
    private val cursor = MutableStateFlow<Pair<Float, Float>?>(null)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = messages.receiveAsFlow()

    val state: StateFlow<TvUiState> = combine(
        discovered, remote.savedDevices, scan, remote.state, cursor,
    ) { found, saved, (scanning, progress, network), connection, cursorPos ->
        val merged = LinkedHashMap<String, TvDevice>()
        found.values.forEach { merged[it.host] = it }
        saved.forEach { s -> merged[s.host] = merged[s.host]?.mergedWith(s) ?: s }
        TvUiState(
            devices = merged.values.sortedWith(compareByDescending<TvDevice> { it.isOnline }.thenBy { it.name }),
            isScanning = scanning,
            scanProgress = progress,
            networkLabel = network,
            connection = connection,
            cursor = cursorPos,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvUiState())

    private var scanJob: Job? = null

    init {
        onIntent(TvIntent.StartScan)
    }

    fun onIntent(intent: TvIntent) {
        when (intent) {
            TvIntent.StartScan -> startScan()
            TvIntent.StopScan -> stopScan()
            is TvIntent.Connect -> viewModelScope.launch {
                stopScan()
                remote.connect(intent.device, intent.mode)
                refreshCursor()
            }
            is TvIntent.AddManual -> viewModelScope.launch {
                val host = intent.host.trim()
                if (!IPV4.matches(host)) {
                    messages.send("Alamat IP tidak valid")
                    return@launch
                }
                val device = discovery.probe(host, intent.name)
                discovered.update { it + (host to device) }
                if (!device.isOnline) messages.send("Port 6466/5555 tidak terbuka, mencoba tetap terhubung…")
                remote.connect(device.copy(atvPort = device.atvPort ?: TvDevice.DEFAULT_REMOTE_PORT), intent.mode)
                refreshCursor()
            }
            is TvIntent.Forget -> viewModelScope.launch { remote.forget(intent.device) }
            is TvIntent.SubmitPairingCode -> viewModelScope.launch { remote.submitPairingCode(intent.code) }
            TvIntent.CancelPairing -> remote.cancelPairing()
            TvIntent.Disconnect -> remote.disconnect()
            is TvIntent.Key -> run { remote.sendKey(intent.keyCode, intent.action) }
            is TvIntent.SendText -> run { remote.sendText(intent.text) }
            is TvIntent.LaunchApp -> run { remote.launchApp(intent.app) }
            is TvIntent.PointerMove -> run { remote.movePointer(intent.dx, intent.dy).also { refreshCursor() } }
            TvIntent.PointerClick -> run { remote.click() }
            is TvIntent.Scroll -> run { remote.scroll(intent.steps) }
        }
    }

    private fun run(block: suspend () -> Result<*>) {
        viewModelScope.launch {
            block().onFailure { messages.send(it.message ?: "Perintah gagal dikirim") }
        }
    }

    private fun refreshCursor() {
        cursor.value = remote.cursorFraction()
    }

    private fun startScan() {
        scanJob?.cancel()
        val network = discovery.localNetwork()
        scan.value = Triple(true, 0f, network?.let { "${it.address.hostAddress}/${it.prefixLength}" })
        if (network == null) {
            scan.value = Triple(false, 0f, null)
            viewModelScope.launch { messages.send("Hubungkan HP ke WiFi yang sama dengan TV") }
            return
        }
        // Previous results stay listed but count as offline until seen again.
        discovered.update { map -> map.mapValues { it.value.copy(isOnline = false) } }
        scanJob = viewModelScope.launch {
            withTimeoutOrNull(SCAN_DURATION_MS) {
                discovery.discover().collect { update ->
                    when (update) {
                        is DiscoveryUpdate.DeviceFound -> discovered.update { map ->
                            val d = update.device.copy(isOnline = true)
                            val existing = map[d.host]
                            val merged = if (existing == null) {
                                d
                            } else {
                                existing.copy(isOnline = true).mergedWith(d).let {
                                    // A fresh mDNS name beats an older generic one.
                                    if (DiscoverySource.MDNS_ATV_REMOTE in d.sources) it.copy(name = d.name) else it
                                }
                            }
                            map + (d.host to merged)
                        }
                        is DiscoveryUpdate.ScanProgress -> scan.update { it.copy(second = update.fraction) }
                        is DiscoveryUpdate.Warning -> messages.send(update.message)
                    }
                }
            }
            scan.update { it.copy(first = false, second = 1f) }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scan.update { it.copy(first = false) }
    }

    private companion object {
        const val SCAN_DURATION_MS = 15_000L
        val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")
    }
}
