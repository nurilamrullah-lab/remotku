package com.remotku.app.data.tv

import com.remotku.app.data.local.TvDeviceDao
import com.remotku.app.data.local.TvDeviceEntity
import com.remotku.app.data.tv.adb.AdbTransport
import com.remotku.app.data.tv.atv.AtvPairingClient
import com.remotku.app.data.tv.atv.AtvPairingSession
import com.remotku.app.data.tv.atv.AtvRemoteTransport
import com.remotku.app.data.tv.atv.CertificateManager
import com.remotku.app.data.tv.atv.InvalidPairingCodeException
import com.remotku.app.domain.tv.KeyAction
import com.remotku.app.domain.tv.QuickApp
import com.remotku.app.domain.tv.TransportMode
import com.remotku.app.domain.tv.TvConnectionState
import com.remotku.app.domain.tv.TvDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single source of truth for the active TV session: picks a transport, runs pairing when the
 * TV doesn't know us yet, and exposes the connection as [state].
 */
class TvRemoteRepository(
    private val certificates: CertificateManager,
    private val adbKeyDirectory: File,
    private val dao: TvDeviceDao,
    private val scope: CoroutineScope,
    private val clientName: String,
) {
    private val _state = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    val state: StateFlow<TvConnectionState> = _state.asStateFlow()

    val savedDevices: Flow<List<TvDevice>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    private val pairingClient = AtvPairingClient(certificates)
    private val connectMutex = Mutex()
    private var transport: TvTransport? = null
    private var pairing: AtvPairingSession? = null
    private var statusJob: Job? = null

    suspend fun connect(device: TvDevice, mode: TransportMode = TransportMode.AUTO) = connectMutex.withLock {
        closeCurrent()
        val useAtv = when (mode) {
            TransportMode.ATV_REMOTE -> true
            TransportMode.ADB -> false
            TransportMode.AUTO -> device.supportsAtvRemote || !device.supportsAdb
        }
        if (useAtv) {
            try {
                connectAtv(device)
            } catch (e: NotPairedException) {
                startPairing(device)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mode == TransportMode.AUTO && device.supportsAdb) {
                    connectAdb(device)
                } else {
                    _state.value = TvConnectionState.Failed(device, e.message ?: "Gagal terhubung")
                }
            }
        } else {
            connectAdb(device)
        }
    }

    private suspend fun connectAtv(device: TvDevice) {
        _state.value = TvConnectionState.Connecting(device, "Menghubungkan Android TV Remote…")
        val port = device.atvPort ?: TvDevice.DEFAULT_REMOTE_PORT
        val atv = AtvRemoteTransport(device.host, port, certificates, scope)
        atv.connect()
        onConnected(device.copy(atvPort = port, isPaired = true), atv, TransportMode.ATV_REMOTE)
    }

    private suspend fun connectAdb(device: TvDevice) {
        _state.value = TvConnectionState.Connecting(device, "Menghubungkan ADB… setujui izin debugging di TV")
        val port = device.adbPort ?: TvDevice.DEFAULT_ADB_PORT
        val adb = AdbTransport(device.host, port, adbKeyDirectory)
        try {
            adb.connect()
            onConnected(device.copy(adbPort = port), adb, TransportMode.ADB)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = TvConnectionState.Failed(device, e.message ?: "Gagal terhubung via ADB")
        }
    }

    private suspend fun startPairing(device: TvDevice) {
        _state.value = TvConnectionState.Connecting(device, "Memulai pairing… lihat kode di layar TV")
        try {
            val session = pairingClient.open(device.host, device.pairingPort)
            withContext(Dispatchers.IO) { session.start(clientName) }
            pairing = session
            _state.value = TvConnectionState.AwaitingPairingCode(device)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = TvConnectionState.Failed(device, "Pairing gagal: ${e.message}")
        }
    }

    suspend fun submitPairingCode(code: String) = connectMutex.withLock {
        val current = _state.value as? TvConnectionState.AwaitingPairingCode ?: return@withLock
        val session = pairing ?: return@withLock
        _state.value = TvConnectionState.Connecting(current.device, "Memverifikasi kode…")
        try {
            withContext(Dispatchers.IO) { session.finish(code) }
            session.close()
            pairing = null
            saveDevice(current.device.copy(isPaired = true), TransportMode.ATV_REMOTE)
            connectAtv(current.device.copy(isPaired = true))
        } catch (e: InvalidPairingCodeException) {
            _state.value = TvConnectionState.AwaitingPairingCode(current.device, e.message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            session.close()
            pairing = null
            _state.value = TvConnectionState.Failed(current.device, e.message ?: "Pairing gagal")
        }
    }

    fun cancelPairing() {
        pairing?.close()
        pairing = null
        _state.value = TvConnectionState.Idle
    }

    private suspend fun onConnected(device: TvDevice, newTransport: TvTransport, mode: TransportMode) {
        transport = newTransport
        _state.value = TvConnectionState.Connected(device, mode, newTransport.capabilities)
        saveDevice(device, mode)
        statusJob = scope.launch {
            newTransport.status.collect { status ->
                val current = _state.value
                if (current !is TvConnectionState.Connected || transport !== newTransport) return@collect
                if (!status.alive) {
                    transport = null
                    _state.value = TvConnectionState.Failed(device, "Koneksi terputus${status.error?.let { ": $it" } ?: ""}")
                } else {
                    _state.value = current.copy(
                        volume = status.volume,
                        currentApp = status.currentApp,
                        capabilities = newTransport.capabilities,
                    )
                }
            }
        }
    }

    private suspend fun saveDevice(device: TvDevice, mode: TransportMode) {
        val existing = dao.find(device.host)
        dao.upsert(
            TvDeviceEntity(
                host = device.host,
                name = if (TvDevice.isGenericName(device.name) && existing != null) existing.name else device.name,
                model = device.model ?: existing?.model,
                atvPort = device.atvPort ?: existing?.atvPort,
                adbPort = device.adbPort ?: existing?.adbPort,
                isPaired = device.isPaired || existing?.isPaired == true,
                lastMode = mode.name,
                lastConnectedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun forget(device: TvDevice) = dao.delete(device.host)

    fun disconnect() {
        closeCurrent()
        _state.value = TvConnectionState.Idle
    }

    private fun closeCurrent() {
        statusJob?.cancel()
        statusJob = null
        transport?.close()
        transport = null
        pairing?.close()
        pairing = null
    }

    private suspend fun <T> withTransport(block: suspend (TvTransport) -> T): Result<T> {
        val t = transport ?: return Result.failure(TvConnectionException("Belum terhubung ke TV"))
        return try {
            Result.success(block(t))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendKey(keyCode: Int, action: KeyAction = KeyAction.SHORT) = withTransport { it.sendKey(keyCode, action) }
    suspend fun sendText(text: String) = withTransport { it.sendText(text) }
    suspend fun launchApp(app: QuickApp) = withTransport { it.launchApp(app) }
    suspend fun movePointer(dx: Int, dy: Int) = withTransport { it.movePointer(dx, dy) }
    suspend fun click() = withTransport { it.click() }
    suspend fun scroll(steps: Int) = withTransport { it.scroll(steps) }

    fun cursorFraction(): Pair<Float, Float>? = (transport as? AdbTransport)?.cursorFraction()
}
