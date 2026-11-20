package com.remotku.app.data.tv

import com.remotku.app.domain.tv.KeyAction
import com.remotku.app.domain.tv.QuickApp
import com.remotku.app.domain.tv.TransportCapabilities
import com.remotku.app.domain.tv.VolumeInfo
import kotlinx.coroutines.flow.StateFlow

data class TransportStatus(
    val alive: Boolean = true,
    val volume: VolumeInfo? = null,
    val currentApp: String? = null,
    val error: String? = null,
)

/** A live control channel to a TV. Key codes are Android `KeyEvent.KEYCODE_*` values. */
interface TvTransport {
    val capabilities: TransportCapabilities
    val status: StateFlow<TransportStatus>

    suspend fun sendKey(keyCode: Int, action: KeyAction = KeyAction.SHORT)
    suspend fun sendText(text: String)
    suspend fun launchApp(app: QuickApp)

    /** Relative pointer movement in TV pixels. Only meaningful for pointer-capable transports. */
    suspend fun movePointer(dx: Int, dy: Int) {}
    suspend fun click() {}
    suspend fun scroll(steps: Int) {}

    fun close()
}

class TvConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The TV rejected our client certificate: a pairing round is required first. */
class NotPairedException(cause: Throwable? = null) : Exception("Perangkat belum dipasangkan", cause)
