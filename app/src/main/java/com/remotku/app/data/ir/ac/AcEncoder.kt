package com.remotku.app.data.ir.ac

import com.remotku.app.data.ir.protocol.IrSignal
import com.remotku.app.data.ir.protocol.PulseBuilder

enum class AcMode(val label: String) { AUTO("Auto"), COOL("Cool"), DRY("Dry"), FAN("Fan"), HEAT("Heat") }

enum class AcFan(val label: String) { AUTO("Auto"), LOW("Low"), MEDIUM("Med"), HIGH("High") }

data class AcState(
    val power: Boolean = false,
    val mode: AcMode = AcMode.COOL,
    val temperature: Int = 24,
    val fan: AcFan = AcFan.AUTO,
    val swing: Boolean = false,
)

/** Which control changed; stateless-ish protocols (LG) send dedicated codes for some actions. */
enum class AcChange { POWER, MODE, TEMPERATURE, FAN, SWING }

/**
 * Air conditioner remotes are stateful: every press transmits the complete desired state.
 * Byte layouts follow the community-documented formats used by IRremoteESP8266.
 */
sealed class AcEncoder(val minTemp: Int, val maxTemp: Int, val supportedModes: List<AcMode> = AcMode.entries) {
    abstract fun encode(state: AcState, change: AcChange): IrSignal

    protected fun temp(state: AcState) = state.temperature.coerceIn(minTemp, maxTemp)

    /** Gree YB1F2 / YAW1F: 8 bytes in two 4-byte blocks with a 3-bit "010" connector. */
    data object Gree : AcEncoder(16, 30) {
        override fun encode(state: AcState, change: AcChange): IrSignal {
            val mode = when (state.mode) { AcMode.AUTO -> 0; AcMode.COOL -> 1; AcMode.DRY -> 2; AcMode.FAN -> 3; AcMode.HEAT -> 4 }
            val fan = when (state.fan) { AcFan.AUTO -> 0; AcFan.LOW -> 1; AcFan.MEDIUM -> 2; AcFan.HIGH -> 3 }
            val b = intArrayOf(0x00, 0x09, 0x20, 0x50, 0x00, 0x20, 0x00, 0x00)
            b[0] = mode or (if (state.power) 0x08 else 0) or (fan shl 4) or (if (state.swing) 0x40 else 0)
            b[1] = (temp(state) - 16) and 0x0F
            b[2] = 0x20 // light on
            b[4] = if (state.swing) 0x01 else 0x00
            b[7] = checksum(b) shl 4
            val p = PulseBuilder().mark(9_000).space(4_500)
                .bytes(b.copyOfRange(0, 4), MARK, ONE, ZERO)
                .bits(0b010, 3, MARK, ONE, ZERO, lsbFirst = false)
                .mark(MARK).space(19_980)
                .bytes(b.copyOfRange(4, 8), MARK, ONE, ZERO)
                .mark(MARK).space(19_980)
            return p.build(38_000)
        }

        fun checksum(b: IntArray): Int {
            var sum = 10
            for (i in 0 until 4) sum += b[i] and 0x0F
            for (i in 4 until 7) sum += b[i] shr 4
            return sum and 0x0F
        }

        private const val MARK = 620
        private const val ONE = 1_600
        private const val ZERO = 540
    }

    /** Daikin ARC4xx (280-bit): 5-bit preamble then 3 frames of 8, 8 and 19 bytes, each with a sum checksum. */
    data object Daikin : AcEncoder(18, 32) {
        override fun encode(state: AcState, change: AcChange): IrSignal {
            val s1 = intArrayOf(0x11, 0xDA, 0x27, 0x00, 0xC5, 0x00, 0x00, 0x00)
            val s2 = intArrayOf(0x11, 0xDA, 0x27, 0x00, 0x42, 0x00, 0x00, 0x00)
            val s3 = intArrayOf(
                0x11, 0xDA, 0x27, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x06, 0x60, 0x00, 0x00, 0xC0, 0x00, 0x00, 0x00,
            )
            val mode = when (state.mode) { AcMode.AUTO -> 0; AcMode.DRY -> 2; AcMode.COOL -> 3; AcMode.HEAT -> 4; AcMode.FAN -> 6 }
            s3[5] = (mode shl 4) or 0x08 or (if (state.power) 1 else 0)
            s3[6] = (if (state.mode == AcMode.FAN) 25 else temp(state)) shl 1
            val fan = when (state.fan) { AcFan.AUTO -> 0xA; AcFan.LOW -> 3; AcFan.MEDIUM -> 5; AcFan.HIGH -> 7 }
            s3[8] = (fan shl 4) or (if (state.swing) 0x0F else 0x00)
            listOf(s1, s2, s3).forEach { it[it.lastIndex] = it.dropLast(1).sum() and 0xFF }

            val p = PulseBuilder()
            p.bits(0, 5, MARK, ONE, ZERO, lsbFirst = true).mark(MARK).space(ZERO + GAP)
            listOf(s1, s2, s3).forEach { section ->
                p.mark(3_650).space(1_623).bytes(section, MARK, ONE, ZERO).mark(MARK).space(GAP)
            }
            return p.build(38_000)
        }

        private const val MARK = 428
        private const val ONE = 1_280
        private const val ZERO = 428
        private const val GAP = 29_000
    }

    /** Panasonic (A75C/CKP family): constant 8-byte frame + 19-byte state frame, checksum seeded with 0xF4. */
    data object Panasonic : AcEncoder(16, 30) {
        override fun encode(state: AcState, change: AcChange): IrSignal {
            val f1 = intArrayOf(0x02, 0x20, 0xE0, 0x04, 0x00, 0x00, 0x00, 0x06)
            val f2 = intArrayOf(
                0x02, 0x20, 0xE0, 0x04, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00,
                0x00, 0x0E, 0xE0, 0x00, 0x00, 0x89, 0x00, 0x00, 0x00,
            )
            val mode = when (state.mode) { AcMode.AUTO -> 0; AcMode.DRY -> 2; AcMode.COOL -> 3; AcMode.HEAT -> 4; AcMode.FAN -> 6 }
            f2[5] = (mode shl 4) or 0x08 or (if (state.power) 1 else 0)
            f2[6] = (temp(state) shl 1) and 0x3E
            val fan = when (state.fan) { AcFan.AUTO -> 0xA; AcFan.LOW -> 3; AcFan.MEDIUM -> 5; AcFan.HIGH -> 7 }
            f2[8] = (fan shl 4) or (if (state.swing) 0x0F else 0x03)
            f2[18] = (0xF4 + f2.dropLast(1).sum()) and 0xFF

            val p = PulseBuilder()
            listOf(f1, f2).forEach { frame ->
                p.mark(3_456).space(1_728).bytes(frame, MARK, ONE, ZERO).mark(MARK).space(10_000)
            }
            return p.build(36_700)
        }

        private const val MARK = 432
        private const val ONE = 1_296
        private const val ZERO = 432
    }

    /** LG (28-bit, MSB first): 0x88 signature, nibble-sum checksum; power-off and swing are fixed commands. */
    data object Lg : AcEncoder(18, 30, listOf(AcMode.AUTO, AcMode.COOL, AcMode.DRY, AcMode.FAN, AcMode.HEAT)) {
        override fun encode(state: AcState, change: AcChange): IrSignal {
            val code = when {
                !state.power -> 0x88C0051L
                change == AcChange.SWING -> if (state.swing) 0x8813149L else 0x881315AL
                else -> {
                    val mode = when (state.mode) { AcMode.COOL -> 0; AcMode.DRY -> 1; AcMode.FAN -> 2; AcMode.AUTO -> 3; AcMode.HEAT -> 4 }
                    val fan = when (state.fan) { AcFan.LOW -> 0; AcFan.MEDIUM -> 2; AcFan.HIGH -> 4; AcFan.AUTO -> 5 }
                    // Turning on uses 0x0 in the power nibble, adjusting a running unit uses 0x8.
                    val powerNibble = if (change == AcChange.POWER) 0x0 else 0x8
                    val body = (0x88L shl 20) or (powerNibble.toLong() shl 16) or (mode.toLong() shl 12) or
                        ((temp(state) - 15).toLong() shl 8) or (fan.toLong() shl 4)
                    body or checksum(body)
                }
            }
            val p = PulseBuilder().mark(8_500).space(4_250)
                .bits(code, 28, 550, 1_600, 550, lsbFirst = false)
                .mark(550).space(50_000)
            return p.build(38_000)
        }

        fun checksum(body: Long): Long {
            var sum = 0L
            var v = body ushr 4
            repeat(6) { sum += v and 0xF; v = v ushr 4 }
            return sum and 0xF
        }
    }

    /** Sharp A907/A705 (13 bytes): XOR-of-nibbles checksum in the top nibble of the last byte. */
    data object Sharp : AcEncoder(15, 30, listOf(AcMode.AUTO, AcMode.COOL, AcMode.DRY, AcMode.HEAT, AcMode.FAN)) {
        override fun encode(state: AcState, change: AcChange): IrSignal {
            val b = intArrayOf(0xAA, 0x5A, 0xCF, 0x10, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0xE0, 0x01)
            b[4] = (if (state.mode == AcMode.FAN) 0 else temp(state) - 15) and 0x0F
            val powerBits = when {
                !state.power -> 0x2
                change == AcChange.POWER -> 0x1
                else -> 0x3
            }
            b[5] = powerBits shl 4
            val mode = when (state.mode) { AcMode.AUTO, AcMode.FAN -> 0; AcMode.HEAT -> 1; AcMode.COOL -> 2; AcMode.DRY -> 3 }
            val fan = when (state.fan) { AcFan.AUTO -> 2; AcFan.LOW -> 4; AcFan.MEDIUM -> 3; AcFan.HIGH -> 5 }
            b[6] = mode or (fan shl 4)
            b[8] = if (state.swing) 0x0F else 0x08
            b[12] = (b[12] and 0x0F) or (checksum(b) shl 4)
            val p = PulseBuilder().mark(3_800).space(1_900)
                .bytes(b, 470, 1_400, 500)
                .mark(470).space(100_000)
            return p.build(38_000)
        }

        fun checksum(b: IntArray): Int {
            var x = 0
            for (i in 0 until 12) x = x xor (b[i] and 0x0F) xor (b[i] shr 4)
            return (x xor (b[12] and 0x0F)) and 0x0F
        }
    }

    /** AUX (YKR-family, Electra-style 13 bytes): byte-sum checksum in the last byte. */
    data object Aux : AcEncoder(16, 32) {
        override fun encode(state: AcState, change: AcChange): IrSignal {
            val b = IntArray(13)
            b[0] = 0xC3
            b[1] = ((temp(state) - 8) shl 3) or (if (state.swing) 0b000 else 0b111)
            b[2] = 0xE0
            val fan = when (state.fan) { AcFan.AUTO -> 0b101; AcFan.LOW -> 0b011; AcFan.MEDIUM -> 0b010; AcFan.HIGH -> 0b001 }
            b[4] = fan shl 5
            val mode = when (state.mode) { AcMode.AUTO -> 0; AcMode.COOL -> 1; AcMode.DRY -> 2; AcMode.HEAT -> 4; AcMode.FAN -> 6 }
            b[6] = mode shl 5
            b[9] = if (state.power) 0x20 else 0x00
            b[11] = when (change) {
                AcChange.POWER -> 0x05
                AcChange.MODE -> 0x06
                AcChange.TEMPERATURE -> 0x00
                AcChange.FAN -> 0x04
                AcChange.SWING -> 0x02
            }
            b[12] = b.take(12).sum() and 0xFF
            val p = PulseBuilder().mark(9_166).space(4_470)
                .bytes(b, 646, 1_647, 547)
                .mark(646).space(40_000)
            return p.build(38_000)
        }
    }

    companion object {
        fun forProtocol(id: String): AcEncoder? = when (id.uppercase()) {
            "AC_GREE" -> Gree
            "AC_DAIKIN" -> Daikin
            "AC_PANASONIC" -> Panasonic
            "AC_LG" -> Lg
            "AC_SHARP" -> Sharp
            "AC_AUX" -> Aux
            else -> null
        }
    }
}
