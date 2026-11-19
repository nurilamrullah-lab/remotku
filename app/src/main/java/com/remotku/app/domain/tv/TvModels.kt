package com.remotku.app.domain.tv

enum class DiscoverySource { MDNS_ATV_REMOTE, MDNS_CAST, SUBNET_SCAN, MANUAL, SAVED }

enum class TransportMode(val label: String) {
    AUTO("Otomatis"),
    ATV_REMOTE("Android TV Remote"),
    ADB("ADB (Debug Jaringan)"),
}

data class TvDevice(
    val host: String,
    val name: String,
    val model: String? = null,
    val atvPort: Int? = null,
    val pairingPort: Int = DEFAULT_PAIRING_PORT,
    val adbPort: Int? = null,
    val castPort: Int? = null,
    val sources: Set<DiscoverySource> = emptySet(),
    val isPaired: Boolean = false,
    val isOnline: Boolean = true,
    val isSaved: Boolean = false,
) {
    val supportsAtvRemote: Boolean get() = atvPort != null
    val supportsAdb: Boolean get() = adbPort != null

    /** Merges information about the same host discovered through different channels. */
    fun mergedWith(other: TvDevice): TvDevice = copy(
        name = if (isGenericName(name) && !isGenericName(other.name)) other.name else name,
        model = model ?: other.model,
        atvPort = atvPort ?: other.atvPort,
        adbPort = adbPort ?: other.adbPort,
        castPort = castPort ?: other.castPort,
        sources = sources + other.sources,
        isPaired = isPaired || other.isPaired,
        isOnline = isOnline || other.isOnline,
        isSaved = isSaved || other.isSaved,
    )

    companion object {
        const val DEFAULT_REMOTE_PORT = 6466
        const val DEFAULT_PAIRING_PORT = 6467
        const val DEFAULT_ADB_PORT = 5555
        const val DEFAULT_CAST_PORT = 8009

        private const val GENERIC_PREFIX = "Perangkat "

        fun genericName(host: String) = "$GENERIC_PREFIX$host"
        fun isGenericName(name: String) = name.startsWith(GENERIC_PREFIX)
    }
}

data class VolumeInfo(val level: Int, val max: Int, val muted: Boolean)

enum class PointerSupport {
    /** No pointer: trackpad gestures are translated into D-Pad presses. */
    GESTURE_DPAD,

    /** A real virtual mouse is registered on the TV (ADB `uinput`). */
    VIRTUAL_MOUSE,

    /** Cursor is tracked on the phone; taps are injected at that position (ADB `input tap`). */
    TAP_AT_CURSOR,
}

data class TransportCapabilities(
    val pointer: PointerSupport,
    val nativeTextInput: Boolean,
)

sealed interface TvConnectionState {
    data object Idle : TvConnectionState
    data class Connecting(val device: TvDevice, val message: String) : TvConnectionState
    data class AwaitingPairingCode(val device: TvDevice, val error: String? = null) : TvConnectionState
    data class Connected(
        val device: TvDevice,
        val mode: TransportMode,
        val capabilities: TransportCapabilities,
        val volume: VolumeInfo? = null,
        val currentApp: String? = null,
    ) : TvConnectionState

    data class Failed(val device: TvDevice?, val message: String) : TvConnectionState
}

enum class KeyAction { SHORT, LONG_START, LONG_END }

enum class QuickApp(
    val label: String,
    /** Deep link understood by the Android TV Remote v2 `app_link` launch request. */
    val appLink: String,
    /** Package launched through ADB. */
    val packageName: String,
    val brandColor: Long,
) {
    YOUTUBE("YouTube", "https://www.youtube.com", "com.google.android.youtube.tv", 0xFFFF0033),
    NETFLIX("Netflix", "https://www.netflix.com/title", "com.netflix.ninja", 0xFFE50914),
    VIDIO("Vidio", "market://launch?id=com.vidio.android.tv", "com.vidio.android.tv", 0xFFEE2A7B),
    PRIME_VIDEO("Prime Video", "https://app.primevideo.com", "com.amazon.amazonvideo.livingroom", 0xFF00A8E1),
    PLAY_STORE("Play Store", "market://launch?id=com.android.vending", "com.android.vending", 0xFF01875F),
}
