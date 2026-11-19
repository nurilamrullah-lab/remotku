package com.remotku.app.data.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.remotku.app.data.ir.ac.AcChange
import com.remotku.app.data.ir.ac.AcEncoder
import com.remotku.app.data.ir.ac.AcState
import com.remotku.app.data.ir.protocol.IrEncoders
import com.remotku.app.data.ir.protocol.IrProtocol
import com.remotku.app.data.ir.protocol.IrSignal
import com.remotku.app.domain.ir.IrCategory
import com.remotku.app.domain.ir.IrKey
import com.remotku.app.domain.ir.IrProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class IrDatabaseJson(
    val version: Int,
    val codeSets: Map<String, CodeSetJson>,
    val profiles: List<ProfileJson>,
)

@Serializable
private data class CodeSetJson(
    val protocol: String,
    val address: String,
    val verified: Boolean = false,
    val codes: Map<String, String>,
)

@Serializable
private data class ProfileJson(
    val id: String,
    val category: String,
    val brand: String,
    val model: String,
    val codeSet: String? = null,
    val protocol: String? = null,
)

/** Built-in device profiles shipped as `assets/ir_profiles.json`. */
class IrProfileRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("ir", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var cache: List<IrProfile>? = null

    suspend fun profiles(): List<IrProfile> = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            IrDatabaseParser.parse(text)
        }.also { cache = it }
    }

    var selectedProfileId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    private companion object {
        const val ASSET = "ir_profiles.json"
        const val KEY_SELECTED = "selected_profile"
    }
}

object IrDatabaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<IrProfile> {
        val db = json.decodeFromString<IrDatabaseJson>(text)
        return db.profiles.mapNotNull { p ->
            val category = runCatching { IrCategory.valueOf(p.category) }.getOrNull() ?: return@mapNotNull null
            val set = p.codeSet?.let { db.codeSets[it] }
            if (set != null) {
                IrProfile(
                    id = p.id,
                    category = category,
                    brand = p.brand,
                    model = p.model,
                    protocol = set.protocol,
                    address = parseHex(set.address),
                    codes = set.codes.mapNotNull { (k, v) ->
                        runCatching { IrKey.valueOf(k) to parseHex(v) }.getOrNull()
                    }.toMap(),
                    verified = set.verified,
                )
            } else {
                IrProfile(id = p.id, category = category, brand = p.brand, model = p.model, protocol = p.protocol ?: return@mapNotNull null)
            }
        }
    }

    private fun parseHex(value: String): Int = value.removePrefix("0x").removePrefix("0X").toInt(16)
}

/** Wraps [ConsumerIrManager]; transmissions are serialized because each call blocks for the burst length. */
class IrTransmitter(context: Context) {

    private val manager = context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val mutex = Mutex()
    private val toggles = HashMap<String, Boolean>()

    val hasIrEmitter: Boolean = runCatching { manager?.hasIrEmitter() == true }.getOrDefault(false)

    fun encodeKey(profile: IrProfile, key: IrKey): IrSignal? {
        val command = profile.codes[key] ?: return null
        val protocol = runCatching { IrProtocol.valueOf(profile.protocol) }.getOrNull() ?: return null
        // RC-5/RC-6 receivers ignore repeats unless the toggle bit flips between presses.
        val toggle = toggles[profile.id]?.not() ?: false
        toggles[profile.id] = toggle
        return IrEncoders.encode(protocol, profile.address, command, toggle)
    }

    fun encodeAc(profile: IrProfile, state: AcState, change: AcChange): IrSignal? =
        AcEncoder.forProtocol(profile.protocol)?.encode(state, change)

    suspend fun transmit(signal: IrSignal): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val ir = manager
                check(ir != null && hasIrEmitter) { "HP ini tidak memiliki IR blaster" }
                ir.transmit(signal.frequencyHz, signal.pattern)
            }
        }
    }
}
