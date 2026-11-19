package com.remotku.app.domain.ir

import kotlinx.serialization.Serializable

/** Device categories, each backed by `assets/ir/<asset>.json`. */
enum class IrCategory(val label: String, val deviceNoun: String, val asset: String) {
    TV("TV", "TV", "tv"),
    STB("STB / Satelit", "STB / receiver", "stb"),
    AC("AC", "AC", "ac"),
    PROJECTOR("Proyektor", "proyektor", "projector"),
    OTT("OTT-Box", "Android box", "ott"),
    DVD("DVD / Blu-ray", "DVD player", "dvd"),
    FAN("Kipas Angin", "kipas angin", "fan"),
    ROBOT("Robot Cleaner", "robot cleaner", "robot"),
    PURIFIER("Air Purifier", "air purifier / humidifier", "purifier"),
    AUDIO("Audio / Soundbar", "audio / soundbar", "audio"),
}

enum class IrKey(val label: String) {
    POWER("Power"), POWER_ON("On"), POWER_OFF("Off"), INPUT("Input"), MUTE("Mute"),
    VOL_UP("Vol +"), VOL_DOWN("Vol −"), CH_UP("CH +"), CH_DOWN("CH −"),
    UP("Atas"), DOWN("Bawah"), LEFT("Kiri"), RIGHT("Kanan"), OK("OK"),
    BACK("Back"), HOME("Home"), MENU("Menu"), EXIT("Exit"), INFO("Info"), EPG("EPG"),
    NUM_1("1"), NUM_2("2"), NUM_3("3"), NUM_4("4"), NUM_5("5"),
    NUM_6("6"), NUM_7("7"), NUM_8("8"), NUM_9("9"), NUM_0("0"),
    PLAY("Play"), PAUSE("Pause"), PLAY_PAUSE("Play/Pause"), STOP("Stop"),
    REWIND("Rewind"), FAST_FORWARD("Forward"), PREV("Prev"), NEXT("Next"),
    NETFLIX("Netflix"), YOUTUBE("YouTube"),
    TEMP_UP("Suhu +"), TEMP_DOWN("Suhu −"), MODE("Mode"), FAN_SPEED("Kecepatan"),
    SPEED_UP("Kencang"), SPEED_DOWN("Pelan"), SWING("Swing"), TIMER("Timer"), SLEEP("Sleep"),
    LIGHT("Lampu"), TURBO("Turbo"), COOL("Cool"), DRY("Dry"), HEAT("Heat"), AUTO("Auto"),
    START("Mulai/Jeda"), DOCK("Pulang"), SPOT("Spot"), EDGE("Tepi"),
    ;

    companion object {
        val DIGITS = listOf(NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9, NUM_0)

        /** Keys the pairing wizard tries, in order, to see whether a code set works. */
        val TEST_ORDER = listOf(POWER, POWER_ON, START, POWER_OFF)
    }
}

/**
 * One candidate remote for a brand. [codes] holds database code strings, decoded only when sent.
 * A set with [acProtocol] uses the stateful AC encoder instead of per-key codes.
 */
data class IrCodeSet(
    val id: String,
    val model: String,
    val codes: Map<IrKey, String> = emptyMap(),
    val universal: Boolean = false,
    val acProtocol: String? = null,
) {
    val isStatefulAc: Boolean get() = acProtocol != null
    val testKey: IrKey? get() = IrKey.TEST_ORDER.firstOrNull { it in codes }
}

data class IrBrand(
    val name: String,
    /** 1-based position in the "Popular brands" section, or null. */
    val popularRank: Int?,
    val codeSetIds: List<String>,
) {
    val indexLetter: Char get() = name.first().uppercaseChar().let { if (it in 'A'..'Z') it else '#' }
}

data class IrCatalog(val category: IrCategory, val brands: List<IrBrand>, val codeSets: Map<String, IrCodeSet>) {
    fun codeSetsFor(brand: IrBrand): List<IrCodeSet> = brand.codeSetIds.mapNotNull { codeSets[it] }
    val popular: List<IrBrand> get() = brands.filter { it.popularRank != null }.sortedBy { it.popularRank }

    /** Live search over brand names and the model names of their code sets. */
    fun search(query: String): List<IrBrand> {
        val q = query.trim()
        if (q.isEmpty()) return brands
        return brands.filter { b ->
            b.name.contains(q, ignoreCase = true) ||
                b.codeSetIds.any { codeSets[it]?.model?.contains(q, ignoreCase = true) == true }
        }.sortedBy { if (it.name.startsWith(q, ignoreCase = true)) 0 else 1 }
    }
}

/** A remote the user confirmed in the pairing wizard. */
@Serializable
data class SavedRemote(
    val id: String,
    val category: IrCategory,
    val brand: String,
    val codeSetId: String,
    val codeIndex: Int,
    val codeCount: Int,
    val createdAt: Long,
) {
    val title: String get() = "$brand ${category.label}"
}
