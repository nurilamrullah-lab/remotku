package com.remotku.app.data.tv.adb

import com.remotku.app.data.tv.TransportStatus
import com.remotku.app.data.tv.TvConnectionException
import com.remotku.app.data.tv.TvTransport
import com.remotku.app.domain.tv.KeyAction
import com.remotku.app.domain.tv.PointerSupport
import com.remotku.app.domain.tv.QuickApp
import com.remotku.app.domain.tv.TransportCapabilities
import dadb.AdbKeyPair
import dadb.AdbStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Fallback transport over ADB-over-network (port 5555) for Android boxes with network debugging on.
 * On first connect the TV asks the user to allow debugging for this phone's key.
 */
class AdbTransport(
    private val host: String,
    private val port: Int,
    private val keyDirectory: File,
) : TvTransport {

    private var dadb: Dadb? = null
    private var mouse: AdbStream? = null
    private val shellLock = Mutex()
    private val mouseLock = Any()

    private var screenWidth = 1920
    private var screenHeight = 1080
    private var cursorX = screenWidth / 2
    private var cursorY = screenHeight / 2

    override var capabilities = TransportCapabilities(PointerSupport.TAP_AT_CURSOR, nativeTextInput = false)
        private set

    private val _status = MutableStateFlow(TransportStatus())
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            val connection = Dadb.create(host, port, keyPair(), CONNECT_TIMEOUT_MS, 0)
            val probe = connection.shell("echo remotku")
            if (!probe.output.contains("remotku")) throw IOException("Respons ADB tidak valid")
            dadb = connection
            parseScreenSize(connection.shell("wm size").output)
            cursorX = screenWidth / 2
            cursorY = screenHeight / 2
            if (registerVirtualMouse(connection)) {
                capabilities = capabilities.copy(pointer = PointerSupport.VIRTUAL_MOUSE)
            }
        } catch (e: Exception) {
            close()
            throw TvConnectionException(
                "Gagal terhubung via ADB ke $host:$port. Pastikan 'Debug jaringan' aktif dan izinkan di layar TV.",
                e,
            )
        }
    }

    private fun keyPair(): AdbKeyPair {
        val priv = File(keyDirectory, "adbkey")
        val pub = File(keyDirectory, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) {
            keyDirectory.mkdirs()
            AdbKeyPair.generate(priv, pub)
        }
        return AdbKeyPair.read(priv, pub)
    }

    private fun parseScreenSize(output: String) {
        // "Physical size: 1920x1080" optionally followed by "Override size: ..."
        val match = Regex("""(\d+)x(\d+)""").findAll(output).lastOrNull() ?: return
        screenWidth = match.groupValues[1].toInt()
        screenHeight = match.groupValues[2].toInt()
    }

    /**
     * Android ships a `uinput` shell command that creates real input devices from JSON.
     * Registering a relative mouse makes the TV render its own pointer, like a USB mouse.
     */
    private fun registerVirtualMouse(connection: Dadb): Boolean = runCatching {
        if (connection.shell("ls /system/bin/uinput").exitCode != 0) return false
        val stream = connection.open("exec:uinput -")
        val register = """{"id":$MOUSE_ID,"command":"register","name":"Remotku Virtual Mouse",""" +
            """"vid":6353,"pid":11330,"bus":"usb","configuration":[""" +
            """{"type":"UI_SET_EVBIT","data":[$EV_KEY,$EV_REL]},""" +
            """{"type":"UI_SET_KEYBIT","data":[$BTN_LEFT,$BTN_RIGHT]},""" +
            """{"type":"UI_SET_RELBIT","data":[$REL_X,$REL_Y,$REL_WHEEL]}]}"""
        stream.sink.writeUtf8(register + "\n").flush()
        mouse = stream
        true
    }.getOrDefault(false)

    private fun injectMouse(vararg events: Int) {
        val stream = mouse ?: return
        val body = events.joinToString(",")
        synchronized(mouseLock) {
            try {
                stream.sink.writeUtf8("""{"id":$MOUSE_ID,"command":"inject","events":[$body]}""" + "\n").flush()
            } catch (e: IOException) {
                mouse = null
                capabilities = capabilities.copy(pointer = PointerSupport.TAP_AT_CURSOR)
            }
        }
    }

    private suspend fun shell(command: String): String = withContext(Dispatchers.IO) {
        val connection = dadb ?: throw TvConnectionException("Tidak terhubung")
        shellLock.withLock {
            try {
                connection.shell(command).allOutput
            } catch (e: IOException) {
                _status.update { it.copy(alive = false, error = e.message) }
                throw TvConnectionException("Koneksi ADB terputus", e)
            }
        }
    }

    override suspend fun sendKey(keyCode: Int, action: KeyAction) {
        when (action) {
            KeyAction.SHORT -> shell("input keyevent $keyCode")
            KeyAction.LONG_START -> shell("input keyevent --longpress $keyCode")
            KeyAction.LONG_END -> Unit
        }
    }

    override suspend fun sendText(text: String) {
        if (text.isEmpty()) return
        // `input text` treats %s as space; everything else is protected by single quotes.
        val escaped = text.replace("%", "\\%").replace(" ", "%s").replace("'", "'\\''")
        shell("input text '$escaped'")
    }

    override suspend fun launchApp(app: QuickApp) {
        val result = shell("monkey -p ${app.packageName} -c android.intent.category.LEANBACK_LAUNCHER 1")
        if (result.contains("No activities found", ignoreCase = true)) {
            shell("monkey -p ${app.packageName} -c android.intent.category.LAUNCHER 1")
        }
    }

    override suspend fun movePointer(dx: Int, dy: Int) {
        if (mouse != null) {
            withContext(Dispatchers.IO) { injectMouse(EV_REL, REL_X, dx, EV_REL, REL_Y, dy, EV_SYN, SYN_REPORT, 0) }
        } else {
            cursorX = (cursorX + dx).coerceIn(0, screenWidth - 1)
            cursorY = (cursorY + dy).coerceIn(0, screenHeight - 1)
        }
    }

    override suspend fun click() {
        if (mouse != null) {
            withContext(Dispatchers.IO) {
                injectMouse(EV_KEY, BTN_LEFT, 1, EV_SYN, SYN_REPORT, 0)
                injectMouse(EV_KEY, BTN_LEFT, 0, EV_SYN, SYN_REPORT, 0)
            }
        } else {
            shell("input tap $cursorX $cursorY")
        }
    }

    override suspend fun scroll(steps: Int) {
        if (steps == 0) return
        if (mouse != null) {
            withContext(Dispatchers.IO) { injectMouse(EV_REL, REL_WHEEL, -steps, EV_SYN, SYN_REPORT, 0) }
        } else {
            val x = screenWidth / 2
            val distance = (screenHeight / 4) * steps.coerceIn(-3, 3)
            val y = screenHeight / 2
            shell("input swipe $x ${y + distance / 2} $x ${y - distance / 2} 200")
        }
    }

    /** Normalized cursor (0..1) when the pointer is tracked on the phone side. */
    fun cursorFraction(): Pair<Float, Float> = cursorX.toFloat() / screenWidth to cursorY.toFloat() / screenHeight

    override fun close() {
        runCatching { mouse?.close() }
        mouse = null
        runCatching { dadb?.close() }
        dadb = null
        _status.update { it.copy(alive = false) }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000
        const val MOUSE_ID = 1

        // linux/input-event-codes.h
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_REL = 2
        const val SYN_REPORT = 0
        const val REL_X = 0
        const val REL_Y = 1
        const val REL_WHEEL = 8
        const val BTN_LEFT = 0x110
        const val BTN_RIGHT = 0x111
    }
}
