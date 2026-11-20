package com.remotku.app.ui.ir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotku.app.data.ir.IrCatalogRepository
import com.remotku.app.data.ir.IrTransmitter
import com.remotku.app.data.ir.SavedRemoteStore
import com.remotku.app.data.ir.ac.AcChange
import com.remotku.app.data.ir.ac.AcEncoder
import com.remotku.app.data.ir.ac.AcFan
import com.remotku.app.data.ir.ac.AcMode
import com.remotku.app.data.ir.ac.AcState
import com.remotku.app.data.ir.protocol.IrSignal
import com.remotku.app.domain.ir.IrBrand
import com.remotku.app.domain.ir.IrCatalog
import com.remotku.app.domain.ir.IrCategory
import com.remotku.app.domain.ir.IrCodeSet
import com.remotku.app.domain.ir.IrKey
import com.remotku.app.domain.ir.SavedRemote
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface Route {
    data object Home : Route
    data object AddController : Route
    data class Brands(val category: IrCategory) : Route
    data class Pairing(val category: IrCategory, val brand: String) : Route
    data class Remote(val remoteId: String) : Route
}

data class BrandListState(
    val category: IrCategory,
    val catalog: IrCatalog? = null,
    val query: String = "",
) {
    val loading: Boolean get() = catalog == null
    val results: List<IrBrand> get() = catalog?.search(query).orEmpty()
}

data class PairingState(
    val category: IrCategory,
    val brand: String,
    val codeSets: List<IrCodeSet> = emptyList(),
    val index: Int = 0,
    /** Whether the current code has been sent at least once, which unlocks the Yes/No question. */
    val tested: Boolean = false,
    val exhausted: Boolean = false,
    val loading: Boolean = true,
) {
    val current: IrCodeSet? get() = codeSets.getOrNull(index)
}

data class ActiveRemote(
    val remote: SavedRemote,
    val codeSet: IrCodeSet? = null,
    val acState: AcState = AcState(),
    val lastSignal: String? = null,
) {
    val acEncoder: AcEncoder? get() = codeSet?.acProtocol?.let { AcEncoder.forProtocol(it) }
}

data class IrUiState(
    val hasIrEmitter: Boolean = false,
    val routes: List<Route> = listOf(Route.Home),
    val addTab: Int = 0,
    val saved: List<SavedRemote> = emptyList(),
    val brandList: BrandListState? = null,
    val pairing: PairingState? = null,
    val active: ActiveRemote? = null,
) {
    val route: Route get() = routes.last()
}

sealed interface IrIntent {
    data object Back : IrIntent
    data class OpenAdd(val tab: Int = 0) : IrIntent
    data class SelectAddTab(val tab: Int) : IrIntent
    data class OpenCategory(val category: IrCategory) : IrIntent
    data class Search(val query: String) : IrIntent
    data class PickBrand(val brand: IrBrand) : IrIntent
    data object PairingTest : IrIntent
    data class PairingAnswer(val works: Boolean) : IrIntent
    data object PairingRestart : IrIntent
    data class OpenRemote(val id: String) : IrIntent
    data class DeleteRemote(val id: String) : IrIntent
    data class RenameRemote(val id: String, val name: String) : IrIntent
    data class Press(val key: IrKey) : IrIntent
    data object AcTogglePower : IrIntent
    data class AcSetMode(val mode: AcMode) : IrIntent
    data class AcChangeTemp(val delta: Int) : IrIntent
    data class AcSetFan(val fan: AcFan) : IrIntent
    data object AcToggleSwing : IrIntent
}

class IrViewModel(
    private val catalogs: IrCatalogRepository,
    private val store: SavedRemoteStore,
    private val transmitter: IrTransmitter,
) : ViewModel() {

    private val _state = MutableStateFlow(IrUiState(hasIrEmitter = transmitter.hasIrEmitter, saved = store.remotes.value))
    val state: StateFlow<IrUiState> = _state.asStateFlow()

    private val messages = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = messages.receiveAsFlow()

    init {
        viewModelScope.launch { store.remotes.collect { list -> _state.update { it.copy(saved = list) } } }
    }

    private fun push(route: Route) = _state.update { it.copy(routes = it.routes + route) }

    fun onIntent(intent: IrIntent) {
        when (intent) {
            IrIntent.Back -> _state.update { if (it.routes.size > 1) it.copy(routes = it.routes.dropLast(1)) else it }
            is IrIntent.OpenAdd -> _state.update { it.copy(routes = it.routes + Route.AddController, addTab = intent.tab) }
            is IrIntent.SelectAddTab -> _state.update { it.copy(addTab = intent.tab) }
            is IrIntent.OpenCategory -> openCategory(intent.category)
            is IrIntent.Search -> _state.update { s -> s.copy(brandList = s.brandList?.copy(query = intent.query)) }
            is IrIntent.PickBrand -> pickBrand(intent.brand)
            IrIntent.PairingTest -> pairingTest()
            is IrIntent.PairingAnswer -> pairingAnswer(intent.works)
            IrIntent.PairingRestart -> _state.update { s -> s.copy(pairing = s.pairing?.copy(index = 0, tested = false, exhausted = false)) }
            is IrIntent.OpenRemote -> openRemote(intent.id)
            is IrIntent.DeleteRemote -> {
                store.remove(intent.id)
                _state.update { s -> s.copy(routes = s.routes.filterNot { it == Route.Remote(intent.id) }.ifEmpty { listOf(Route.Home) }) }
            }
            is IrIntent.Press -> press(intent.key)
            is IrIntent.RenameRemote -> {
                store.rename(intent.id, intent.name)
                val renamed = store.remotes.value.firstOrNull { it.id == intent.id }
                _state.update { s -> if (s.active?.remote?.id == intent.id && renamed != null) s.copy(active = s.active.copy(remote = renamed)) else s }
            }
            IrIntent.AcTogglePower -> updateAc(AcChange.POWER) { it.copy(power = !it.power) }
            is IrIntent.AcSetMode -> updateAc(AcChange.MODE) { it.copy(mode = intent.mode, power = true) }
            is IrIntent.AcChangeTemp -> updateAc(AcChange.TEMPERATURE) { it.copy(temperature = it.temperature + intent.delta, power = true) }
            is IrIntent.AcSetFan -> updateAc(AcChange.FAN) { it.copy(fan = intent.fan, power = true) }
            IrIntent.AcToggleSwing -> updateAc(AcChange.SWING) { it.copy(swing = !it.swing, power = true) }
        }
    }

    private fun openCategory(category: IrCategory) {
        _state.update { it.copy(brandList = BrandListState(category), routes = it.routes + Route.Brands(category)) }
        viewModelScope.launch {
            val catalog = loadCatalog(category) ?: return@launch
            _state.update { s -> if (s.brandList?.category == category) s.copy(brandList = s.brandList.copy(catalog = catalog)) else s }
        }
    }

    private fun pickBrand(brand: IrBrand) {
        val catalog = _state.value.brandList?.catalog ?: return
        val sets = catalog.codeSetsFor(brand).filter { it.isStatefulAc || it.testKey != null }
        _state.update {
            it.copy(
                pairing = PairingState(catalog.category, brand.name, sets, loading = false),
                routes = it.routes + Route.Pairing(catalog.category, brand.name),
            )
        }
    }

    private fun pairingTest() {
        val pairing = _state.value.pairing ?: return
        val set = pairing.current ?: return
        val signal = if (set.isStatefulAc) {
            transmitter.encodeAc(set, AcState(power = true), AcChange.POWER)
        } else {
            set.testKey?.let { transmitter.encodeKey(set, it) }
        }
        _state.update { s -> s.copy(pairing = s.pairing?.copy(tested = true)) }
        if (signal != null) send(signal, "Tes kode ${pairing.index + 1}") else viewModelScope.launch { messages.send("Kode ini rusak, lanjut ke berikutnya") }
    }

    private fun pairingAnswer(works: Boolean) {
        val pairing = _state.value.pairing ?: return
        val set = pairing.current ?: return
        if (!works) {
            _state.update { s ->
                val next = pairing.index + 1
                s.copy(
                    pairing = if (next < pairing.codeSets.size) pairing.copy(index = next, tested = false) else pairing.copy(exhausted = true),
                )
            }
            return
        }
        val remote = SavedRemote(
            id = UUID.randomUUID().toString(),
            category = pairing.category,
            brand = pairing.brand,
            codeSetId = set.id,
            codeIndex = pairing.index + 1,
            codeCount = pairing.codeSets.size,
            createdAt = System.currentTimeMillis(),
        )
        store.add(remote)
        _state.update {
            it.copy(
                routes = listOf(Route.Home, Route.Remote(remote.id)),
                active = ActiveRemote(remote, set),
                pairing = null,
                brandList = null,
            )
        }
        viewModelScope.launch { messages.send("Remote ${remote.title} tersimpan") }
    }

    private fun openRemote(id: String) {
        val remote = _state.value.saved.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(active = ActiveRemote(remote), routes = it.routes + Route.Remote(id)) }
        viewModelScope.launch {
            val set = loadCatalog(remote.category)?.codeSets?.get(remote.codeSetId)
            if (set == null) messages.send("Kode remote tidak ditemukan di database")
            _state.update { s -> if (s.active?.remote?.id == id) s.copy(active = s.active.copy(codeSet = set)) else s }
        }
    }

    private suspend fun loadCatalog(category: IrCategory): IrCatalog? = runCatching { catalogs.catalog(category) }
        .onFailure { messages.send("Database IR gagal dimuat: ${it.message}") }
        .getOrNull()

    private fun press(key: IrKey) {
        val active = _state.value.active ?: return
        val set = active.codeSet ?: return
        val signal = transmitter.encodeKey(set, key)
        if (signal == null) {
            viewModelScope.launch { messages.send("Tombol ${key.label} tidak tersedia di remote ini") }
        } else {
            send(signal, "${active.remote.brand} · ${key.label}")
        }
    }

    private fun updateAc(change: AcChange, transform: (AcState) -> AcState) {
        val active = _state.value.active ?: return
        val set = active.codeSet ?: return
        val encoder = active.acEncoder ?: return
        val next = transform(active.acState).let { it.copy(temperature = it.temperature.coerceIn(encoder.minTemp, encoder.maxTemp)) }
        _state.update { s -> s.copy(active = s.active?.copy(acState = next)) }
        val signal = transmitter.encodeAc(set, next, change) ?: return
        send(signal, "${active.remote.brand} AC · ${if (next.power) "${next.mode.label} ${next.temperature}°C" else "Off"}")
    }

    private fun send(signal: IrSignal, label: String) {
        viewModelScope.launch {
            transmitter.transmit(signal)
                .onSuccess { _state.update { s -> s.copy(active = s.active?.copy(lastSignal = "$label · ${signal.frequencyHz / 1000} kHz")) } }
                .onFailure { messages.send(it.message ?: "Gagal mengirim IR") }
        }
    }
}
