package com.remotku.app.domain.ir

enum class IrCategory(val label: String) { TV("TV"), STB("STB DVB-T2"), AC("AC") }

enum class IrKey(val label: String) {
    POWER("Power"), INPUT("Input"), MUTE("Mute"),
    VOL_UP("Vol +"), VOL_DOWN("Vol −"), CH_UP("CH +"), CH_DOWN("CH −"),
    UP("Atas"), DOWN("Bawah"), LEFT("Kiri"), RIGHT("Kanan"), OK("OK"),
    BACK("Back"), HOME("Home"), MENU("Menu"), EXIT("Exit"), INFO("Info"), EPG("EPG"),
    NUM_1("1"), NUM_2("2"), NUM_3("3"), NUM_4("4"), NUM_5("5"),
    NUM_6("6"), NUM_7("7"), NUM_8("8"), NUM_9("9"), NUM_0("0"),
}

data class IrProfile(
    val id: String,
    val category: IrCategory,
    val brand: String,
    val model: String,
    /** `IrProtocol` name for button remotes or `AC_*` encoder id for air conditioners. */
    val protocol: String,
    val address: Int = 0,
    val codes: Map<IrKey, Int> = emptyMap(),
    val verified: Boolean = true,
) {
    val isAc: Boolean get() = category == IrCategory.AC
}
