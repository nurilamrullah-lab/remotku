package com.remotku.app.data.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.remotku.app.data.ir.ac.AcChange
import com.remotku.app.data.ir.ac.AcEncoder
import com.remotku.app.data.ir.ac.AcState
import com.remotku.app.data.ir.protocol.IrEncoders
import com.remotku.app.data.ir.protocol.IrSignal
import com.remotku.app.domain.ir.IrBrand
import com.remotku.app.domain.ir.IrCatalog
import com.remotku.app.domain.ir.IrCategory
import com.remotku.app.domain.ir.IrCodeSet
import com.remotku.app.domain.ir.IrKey
import com.remotku.app.domain.ir.SavedRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
private data class CatalogJson(
    val version: Int,
    val category: String,
    val brands: List<BrandJson>,
    val codeSets: Map<String, CodeSetJson>,
)

@Serializable
private data class BrandJson(val name: String, val sets: List<String>, val popular: Int? = null)

@Serializable
private data class CodeSetJson(val model: String, val keys: Map<String, String>, val universal: Boolean = false)

object IrDatabaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Code set ids starting with this prefix name a stateful [AcEncoder] instead of a stored set. */
    const val AC_PREFIX = "@"

    fun parse(text: String): IrCatalog {
        val db = json.decodeFromString<CatalogJson>(text)
        val category = IrCategory.valueOf(db.category)
        val sets = HashMap<String, IrCodeSet>(db.codeSets.size * 2)
        db.codeSets.forEach { (id, set) ->
            val codes = set.keys.mapNotNull { (k, v) -> runCatching { IrKey.valueOf(k) to v }.getOrNull() }.toMap()
            sets[id] = IrCodeSet(id, set.model, codes, set.universal)
        }
        db.brands.flatMap { it.sets }.filter { it.startsWith(AC_PREFIX) }.distinct().forEach { id ->
            val protocol = id.removePrefix(AC_PREFIX)
            if (AcEncoder.forProtocol(protocol) != null) {
                sets[id] = IrCodeSet(id, "Protokol ${protocol.removePrefix("AC_").lowercase().replaceFirstChar { it.uppercase() }} (lengkap)", acProtocol = protocol)
            }
        }
        val brands = db.brands.map { b -> IrBrand(b.name, b.popular, b.sets.filter { it in sets }) }
            .filter { it.codeSetIds.isNotEmpty() }
        return IrCatalog(category, brands, sets)
    }
}

/** Loads one category's brand catalog on demand from `assets/ir/<category>.json`. */
class IrCatalogRepository(private val context: Context) {
    private val mutex = Mutex()
    private val cache = HashMap<IrCategory, IrCatalog>()

    suspend fun catalog(category: IrCategory): IrCatalog = mutex.withLock {
        cache[category] ?: withContext(Dispatchers.IO) {
            val text = context.assets.open("ir/${category.asset}.json").bufferedReader().use { it.readText() }
            IrDatabaseParser.parse(text)
        }.also { cache[category] = it }
    }
}

/** Remotes confirmed through the pairing wizard, persisted as JSON in shared preferences. */
class SavedRemoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("ir", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(SavedRemote.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private val _remotes = MutableStateFlow(load())
    val remotes: StateFlow<List<SavedRemote>> = _remotes.asStateFlow()

    private fun load(): List<SavedRemote> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    fun add(remote: SavedRemote) = update { listOf(remote) + it.filterNot { r -> r.id == remote.id } }

    fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

    fun rename(id: String, name: String?) = update { list ->
        list.map { if (it.id == id) it.copy(customName = name?.trim()?.takeIf(String::isNotEmpty)) else it }
    }

    private fun update(transform: (List<SavedRemote>) -> List<SavedRemote>) {
        _remotes.update(transform)
        prefs.edit().putString(KEY, json.encodeToString(serializer, _remotes.value)).apply()
    }

    private companion object {
        const val KEY = "saved_remotes_v2"
    }
}

/** Wraps [ConsumerIrManager]; transmissions are serialized because each call blocks for the burst length. */
class IrTransmitter(context: Context) {

    private val manager = context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val mutex = Mutex()
    private val toggles = HashMap<String, Boolean>()

    val hasIrEmitter: Boolean = runCatching { manager?.hasIrEmitter() == true }.getOrDefault(false)

    fun encodeKey(set: IrCodeSet, key: IrKey): IrSignal? {
        val code = set.codes[key] ?: return null
        // RC-5/RC-6 receivers ignore repeats unless the toggle bit flips between presses.
        val toggle = toggles[set.id]?.not() ?: false
        toggles[set.id] = toggle
        return IrEncoders.decode(code, toggle)
    }

    fun encodeAc(set: IrCodeSet, state: AcState, change: AcChange): IrSignal? =
        set.acProtocol?.let { AcEncoder.forProtocol(it) }?.encode(state, change)

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
