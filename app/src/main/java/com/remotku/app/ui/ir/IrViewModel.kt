package com.remotku.app.ui.ir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotku.app.data.ir.IrProfileRepository
import com.remotku.app.data.ir.IrTransmitter
import com.remotku.app.data.ir.ac.AcChange
import com.remotku.app.data.ir.ac.AcEncoder
import com.remotku.app.data.ir.ac.AcFan
import com.remotku.app.data.ir.ac.AcMode
import com.remotku.app.data.ir.ac.AcState
import com.remotku.app.data.ir.protocol.IrSignal
import com.remotku.app.domain.ir.IrCategory
import com.remotku.app.domain.ir.IrKey
import com.remotku.app.domain.ir.IrProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IrUiState(
    val hasIrEmitter: Boolean = false,
    val loading: Boolean = true,
    val profiles: List<IrProfile> = emptyList(),
    val category: IrCategory = IrCategory.TV,
    val selectedBrand: String? = null,
    val selectedProfile: IrProfile? = null,
    val acState: AcState = AcState(),
    val lastSignal: String? = null,
) {
    val brands: List<String> get() = profiles.filter { it.category == category }.map { it.brand }.distinct()
    val variants: List<IrProfile> get() = profiles.filter { it.category == category && it.brand == selectedBrand }
    val acEncoder: AcEncoder? get() = selectedProfile?.takeIf { it.isAc }?.let { AcEncoder.forProtocol(it.protocol) }
}

sealed interface IrIntent {
    data class SelectCategory(val category: IrCategory) : IrIntent
    data class SelectBrand(val brand: String) : IrIntent
    data class SelectProfile(val profile: IrProfile) : IrIntent
    data class Press(val key: IrKey) : IrIntent
    data object AcTogglePower : IrIntent
    data class AcSetMode(val mode: AcMode) : IrIntent
    data class AcChangeTemp(val delta: Int) : IrIntent
    data class AcSetFan(val fan: AcFan) : IrIntent
    data object AcToggleSwing : IrIntent
}

class IrViewModel(
    private val repository: IrProfileRepository,
    private val transmitter: IrTransmitter,
) : ViewModel() {

    private val _state = MutableStateFlow(IrUiState(hasIrEmitter = transmitter.hasIrEmitter))
    val state: StateFlow<IrUiState> = _state.asStateFlow()

    private val messages = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val profiles = runCatching { repository.profiles() }.getOrElse {
                messages.send("Database IR gagal dimuat: ${it.message}")
                emptyList()
            }
            val saved = profiles.firstOrNull { it.id == repository.selectedProfileId }
            val initial = saved ?: profiles.firstOrNull { it.category == IrCategory.TV }
            _state.update {
                it.copy(
                    loading = false,
                    profiles = profiles,
                    category = initial?.category ?: IrCategory.TV,
                    selectedBrand = initial?.brand,
                    selectedProfile = initial,
                )
            }
        }
    }

    fun onIntent(intent: IrIntent) {
        when (intent) {
            is IrIntent.SelectCategory -> _state.update { s ->
                val first = s.profiles.firstOrNull { it.category == intent.category }
                s.copy(category = intent.category, selectedBrand = first?.brand, selectedProfile = first).also { select(first) }
            }
            is IrIntent.SelectBrand -> _state.update { s ->
                val first = s.profiles.firstOrNull { it.category == s.category && it.brand == intent.brand }
                s.copy(selectedBrand = intent.brand, selectedProfile = first).also { select(first) }
            }
            is IrIntent.SelectProfile -> {
                _state.update { it.copy(selectedProfile = intent.profile) }
                select(intent.profile)
            }
            is IrIntent.Press -> {
                val profile = _state.value.selectedProfile ?: return
                val signal = transmitter.encodeKey(profile, intent.key)
                if (signal == null) {
                    viewModelScope.launch { messages.send("Tombol ${intent.key.label} tidak tersedia di profil ini") }
                } else {
                    send(signal, "${profile.brand} · ${intent.key.label}")
                }
            }
            IrIntent.AcTogglePower -> updateAc(AcChange.POWER) { it.copy(power = !it.power) }
            is IrIntent.AcSetMode -> updateAc(AcChange.MODE) { it.copy(mode = intent.mode, power = true) }
            is IrIntent.AcChangeTemp -> updateAc(AcChange.TEMPERATURE) { it.copy(temperature = it.temperature + intent.delta, power = true) }
            is IrIntent.AcSetFan -> updateAc(AcChange.FAN) { it.copy(fan = intent.fan, power = true) }
            IrIntent.AcToggleSwing -> updateAc(AcChange.SWING) { it.copy(swing = !it.swing, power = true) }
        }
    }

    private fun select(profile: IrProfile?) {
        repository.selectedProfileId = profile?.id
    }

    private fun updateAc(change: AcChange, transform: (AcState) -> AcState) {
        val current = _state.value
        val profile = current.selectedProfile ?: return
        val encoder = current.acEncoder ?: return
        val next = transform(current.acState).let {
            it.copy(temperature = it.temperature.coerceIn(encoder.minTemp, encoder.maxTemp))
        }
        _state.update { it.copy(acState = next) }
        val signal = transmitter.encodeAc(profile, next, change) ?: return
        send(signal, "${profile.brand} AC · ${if (next.power) "${next.mode.label} ${next.temperature}°C" else "Off"}")
    }

    private fun send(signal: IrSignal, label: String) {
        viewModelScope.launch {
            transmitter.transmit(signal)
                .onSuccess { _state.update { it.copy(lastSignal = "$label · ${signal.frequencyHz / 1000} kHz") } }
                .onFailure { messages.send(it.message ?: "Gagal mengirim IR") }
        }
    }
}
