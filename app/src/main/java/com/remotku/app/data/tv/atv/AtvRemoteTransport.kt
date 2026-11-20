package com.remotku.app.data.tv.atv

import android.os.Build
import android.view.KeyEvent
import com.remotku.app.core.proto.ProtoFraming
import com.remotku.app.core.proto.ProtoMessage
import com.remotku.app.core.proto.ProtoWriter
import com.remotku.app.data.tv.NotPairedException
import com.remotku.app.data.tv.TransportStatus
import com.remotku.app.data.tv.TvConnectionException
import com.remotku.app.data.tv.TvTransport
import com.remotku.app.domain.tv.KeyAction
import com.remotku.app.domain.tv.PointerSupport
import com.remotku.app.domain.tv.QuickApp
import com.remotku.app.domain.tv.TransportCapabilities
import com.remotku.app.domain.tv.VolumeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.SocketException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/**
 * Android TV Remote protocol v2 (the protocol used by the Google TV app) on port 6466.
 * The TV drives the session: it sends configure / set-active / ping messages that must be answered.
 */
class AtvRemoteTransport(
    private val host: String,
    private val port: Int,
    private val certificates: CertificateManager,
    private val scope: CoroutineScope,
) : TvTransport {

    override val capabilities = TransportCapabilities(
        pointer = PointerSupport.GESTURE_DPAD,
        nativeTextInput = true,
    )

    private val _status = MutableStateFlow(TransportStatus())
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private var socket: SSLSocket? = null
    private var output: OutputStream? = null
    private var readerJob: Job? = null
    private val writeLock = Any()
    private val ready = CompletableDeferred<Unit>()

    private var activeFeatures = DEFAULT_FEATURES
    private var imeCounter = 0
    private var imeFieldCounter = 0
    private var hasImeField = false

    suspend fun connect() {
        withContext(Dispatchers.IO) {
            val s = certificates.sslContext().socketFactory.createSocket() as SSLSocket
            try {
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                // The TV pings every few seconds; silence beyond this means the link is dead.
                s.soTimeout = READ_TIMEOUT_MS
                s.startHandshake()
            } catch (e: SSLException) {
                runCatching { s.close() }
                throw NotPairedException(e)
            } catch (e: IOException) {
                runCatching { s.close() }
                throw TvConnectionException("Tidak dapat terhubung ke $host:$port (${e.message})", e)
            }
            socket = s
            output = s.outputStream
            readerJob = scope.launch(Dispatchers.IO) { readLoop(s) }
        }
        try {
            withTimeout(HANDSHAKE_TIMEOUT_MS) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            close()
            throw TvConnectionException("TV tidak merespons protokol remote", e)
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun readLoop(s: SSLSocket) {
        try {
            val input = s.inputStream
            while (!s.isClosed) {
                handle(ProtoMessage.parse(ProtoFraming.read(input)))
            }
        } catch (e: Exception) {
            if (!ready.isCompleted) {
                // TLS 1.3 reports a rejected client certificate on the first read rather than in the handshake.
                val notPaired = e is SSLException || e is EOFException || e is SocketException
                ready.completeExceptionally(if (notPaired) NotPairedException(e) else e)
            }
            _status.update { it.copy(alive = false, error = e.message) }
        }
    }

    private fun handle(msg: ProtoMessage) {
        when {
            msg.has(F_CONFIGURE) -> {
                val supported = msg.message(F_CONFIGURE)?.int(1) ?: 0
                if (supported != 0) activeFeatures = DEFAULT_FEATURES and supported
                send {
                    message(F_CONFIGURE) {
                        int(1, activeFeatures)
                        message(2) {
                            string(1, Build.MODEL ?: "Android")
                            string(2, Build.MANUFACTURER ?: "Android")
                            int(3, 1)
                            string(4, "1")
                            string(5, "atvremote")
                            string(6, "1.0.0")
                        }
                    }
                }
            }
            msg.has(F_SET_ACTIVE) -> {
                send { message(F_SET_ACTIVE) { int(1, activeFeatures) } }
                ready.complete(Unit)
            }
            msg.has(F_PING_REQUEST) -> {
                val value = msg.message(F_PING_REQUEST)?.int(1) ?: 0
                send { message(F_PING_RESPONSE) { int(1, value) } }
            }
            msg.has(F_IME_KEY_INJECT) -> {
                val inject = msg.message(F_IME_KEY_INJECT)
                val app = inject?.message(1)
                imeCounter = app?.int(1) ?: imeCounter
                imeFieldCounter = inject?.message(2)?.int(1) ?: imeFieldCounter
                hasImeField = true
                app?.string(12)?.let { pkg -> _status.update { it.copy(currentApp = pkg) } }
            }
            msg.has(F_IME_BATCH_EDIT) -> {
                val edit = msg.message(F_IME_BATCH_EDIT)
                if (imeCounter == 0) imeCounter = edit?.int(1) ?: 0
                if (imeFieldCounter == 0) imeFieldCounter = edit?.int(2) ?: 0
                hasImeField = true
            }
            msg.has(F_IME_SHOW_REQUEST) -> {
                msg.message(F_IME_SHOW_REQUEST)?.message(2)?.let {
                    imeFieldCounter = it.int(1)
                    hasImeField = true
                }
            }
            msg.has(F_START) -> ready.complete(Unit)
            msg.has(F_SET_VOLUME) -> {
                val v = msg.message(F_SET_VOLUME) ?: return
                _status.update { it.copy(volume = VolumeInfo(level = v.int(7), max = v.int(6), muted = v.bool(8))) }
            }
        }
    }

    private fun send(block: ProtoWriter.() -> Unit) {
        val out = output ?: throw TvConnectionException("Tidak terhubung")
        val payload = ProtoWriter().apply(block).toByteArray()
        synchronized(writeLock) { ProtoFraming.write(out, payload) }
    }

    private suspend fun sendAsync(block: ProtoWriter.() -> Unit) = withContext(Dispatchers.IO) {
        try {
            send(block)
        } catch (e: IOException) {
            _status.update { it.copy(alive = false, error = e.message) }
            throw TvConnectionException("Koneksi ke TV terputus", e)
        }
    }

    override suspend fun sendKey(keyCode: Int, action: KeyAction) {
        val direction = when (action) {
            KeyAction.SHORT -> DIRECTION_SHORT
            KeyAction.LONG_START -> DIRECTION_START_LONG
            KeyAction.LONG_END -> DIRECTION_END_LONG
        }
        sendAsync { message(F_KEY_INJECT) { int(1, keyCode); int(2, direction) } }
    }

    override suspend fun sendText(text: String) {
        if (text.isEmpty()) return
        if (hasImeField && (activeFeatures and FEATURE_IME) != 0) {
            sendAsync {
                message(F_IME_BATCH_EDIT) {
                    int(1, imeCounter)
                    int(2, imeFieldCounter)
                    message(3) {
                        int(1, 1)
                        message(2) { int(1, text.length); int(2, text.length); string(3, text) }
                    }
                }
            }
        } else {
            // No focused IME field reported yet: type the characters as key presses.
            text.forEach { ch -> keyCodeForChar(ch)?.let { sendKey(it) } }
        }
    }

    override suspend fun launchApp(app: QuickApp) {
        sendAsync { message(F_APP_LINK_LAUNCH) { string(1, app.appLink) } }
    }

    override fun close() {
        readerJob?.cancel()
        runCatching { socket?.close() }
        socket = null
        output = null
        _status.update { it.copy(alive = false) }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val HANDSHAKE_TIMEOUT_MS = 8_000L

        private const val FEATURE_PING = 1
        private const val FEATURE_KEY = 1 shl 1
        private const val FEATURE_IME = 1 shl 2
        private const val FEATURE_POWER = 1 shl 5
        private const val FEATURE_VOLUME = 1 shl 6
        private const val FEATURE_APP_LINK = 1 shl 9
        private const val DEFAULT_FEATURES =
            FEATURE_PING or FEATURE_KEY or FEATURE_IME or FEATURE_POWER or FEATURE_VOLUME or FEATURE_APP_LINK

        private const val F_CONFIGURE = 1
        private const val F_SET_ACTIVE = 2
        private const val F_PING_REQUEST = 8
        private const val F_PING_RESPONSE = 9
        private const val F_KEY_INJECT = 10
        private const val F_IME_KEY_INJECT = 20
        private const val F_IME_BATCH_EDIT = 21
        private const val F_IME_SHOW_REQUEST = 22
        private const val F_START = 40
        private const val F_SET_VOLUME = 50
        private const val F_APP_LINK_LAUNCH = 90

        private const val DIRECTION_START_LONG = 1
        private const val DIRECTION_END_LONG = 2
        private const val DIRECTION_SHORT = 3

        fun keyCodeForChar(ch: Char): Int? = when (ch.lowercaseChar()) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (ch.lowercaseChar() - 'a')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (ch - '0')
            ' ' -> KeyEvent.KEYCODE_SPACE
            '.' -> KeyEvent.KEYCODE_PERIOD
            ',' -> KeyEvent.KEYCODE_COMMA
            '-' -> KeyEvent.KEYCODE_MINUS
            '@' -> KeyEvent.KEYCODE_AT
            '/' -> KeyEvent.KEYCODE_SLASH
            '\n' -> KeyEvent.KEYCODE_ENTER
            else -> null
        }
    }
}
