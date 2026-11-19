package com.remotku.app.data.ir.protocol

enum class IrProtocol(val label: String) {
    NEC("NEC 38 kHz"),
    NEC_EXT("NEC Extended 38 kHz"),
    SAMSUNG("Samsung32 38 kHz"),
    RC5("Philips RC-5 36 kHz"),
    RC6("Philips RC-6 36 kHz"),
    SIRC12("Sony SIRC-12 40 kHz"),
    SIRC15("Sony SIRC-15 40 kHz"),
    SIRC20("Sony SIRC-20 40 kHz"),
    SHARP("Sharp 38 kHz"),
}

/** Encoders for stateless (one code per button) remotes. Address/command use protocol-level values. */
object IrEncoders {

    fun encode(protocol: IrProtocol, address: Int, command: Int, toggle: Boolean = false): IrSignal = when (protocol) {
        IrProtocol.NEC -> nec(address and 0xFF, command, extended = false)
        IrProtocol.NEC_EXT -> nec(address and 0xFFFF, command, extended = true)
        IrProtocol.SAMSUNG -> samsung(address, command)
        IrProtocol.RC5 -> rc5(address, command, toggle)
        IrProtocol.RC6 -> rc6(address, command, toggle)
        IrProtocol.SIRC12 -> sirc(address, command, addressBits = 5)
        IrProtocol.SIRC15 -> sirc(address, command, addressBits = 8)
        IrProtocol.SIRC20 -> sirc(address, command, addressBits = 13)
        IrProtocol.SHARP -> sharp(address, command)
    }

    // NEC: 9 ms / 4.5 ms leader, 562.5 µs pulse distance, address + ~address (or 16-bit address) + command + ~command.
    private const val NEC_FREQ = 38_000
    private const val NEC_UNIT = 562
    private const val NEC_FRAME = 108_000

    fun nec(address: Int, command: Int, extended: Boolean = address > 0xFF): IrSignal {
        val low = address and 0xFF
        val high = if (extended) (address shr 8) and 0xFF else low.inv() and 0xFF
        val cmd = command and 0xFF
        val b = PulseBuilder().mark(9_000).space(4_500)
            .bytes(intArrayOf(low, high, cmd, cmd.inv() and 0xFF), NEC_UNIT, NEC_UNIT * 3, NEC_UNIT)
            .mark(NEC_UNIT)
        b.space(NEC_FRAME - b.totalMicros)
        // One NEC repeat frame makes receivers that debounce the first frame react reliably.
        b.mark(9_000).space(2_250).mark(NEC_UNIT)
        return b.build(NEC_FREQ)
    }

    // Samsung32: like NEC but with a 4.5/4.5 ms leader and the address byte sent twice.
    fun samsung(address: Int, command: Int): IrSignal {
        val addr = address and 0xFF
        val cmd = command and 0xFF
        val b = PulseBuilder().mark(4_500).space(4_500)
            .bytes(intArrayOf(addr, addr, cmd, cmd.inv() and 0xFF), 560, 1_690, 560)
            .mark(560)
        b.space(NEC_FRAME - b.totalMicros)
        return b.build(NEC_FREQ)
    }

    // RC-5: Manchester code, 889 µs half-bit, MSB first; "1" = space→mark. 2nd start bit carries inverted command bit 6.
    fun rc5(address: Int, command: Int, toggle: Boolean): IrSignal {
        val unit = 889
        val bits = ArrayList<Int>(14)
        bits += 1
        bits += if (command and 0x40 == 0) 1 else 0
        bits += if (toggle) 1 else 0
        for (i in 4 downTo 0) bits += (address shr i) and 1
        for (i in 5 downTo 0) bits += (command shr i) and 1
        val b = PulseBuilder()
        bits.forEach { bit ->
            if (bit == 1) b.space(unit).mark(unit) else b.mark(unit).space(unit)
        }
        b.space(114_000 - b.totalMicros)
        return b.build(36_000)
    }

    // RC-6 mode 0: 444 µs unit, leader 6T/2T, start "1", mode 000, double-width toggle, 8-bit address + command.
    fun rc6(address: Int, command: Int, toggle: Boolean): IrSignal {
        val t = 444
        val b = PulseBuilder().mark(6 * t).space(2 * t)
        fun bit(value: Int, width: Int = t) {
            if (value == 1) b.mark(width).space(width) else b.space(width).mark(width)
        }
        bit(1)
        repeat(3) { bit(0) }
        bit(if (toggle) 1 else 0, 2 * t)
        for (i in 7 downTo 0) bit((address shr i) and 1)
        for (i in 7 downTo 0) bit((command shr i) and 1)
        b.space(2_666 * 6)
        return b.build(36_000)
    }

    // Sony SIRC: 2.4 ms header, 600 µs space, mark 1200 (1) / 600 (0), LSB first, repeated 3x at a 45 ms period.
    fun sirc(address: Int, command: Int, addressBits: Int): IrSignal {
        val b = PulseBuilder()
        repeat(3) {
            val start = b.totalMicros
            b.mark(2_400).space(600)
            val data = (command.toLong() and 0x7F) or ((address.toLong() and ((1L shl addressBits) - 1)) shl 7)
            for (i in 0 until 7 + addressBits) {
                val one = (data ushr i) and 1L == 1L
                b.mark(if (one) 1_200 else 600).space(600)
            }
            b.space(45_000 - (b.totalMicros - start))
        }
        return b.build(40_000)
    }

    // Sharp: 5-bit address + 8-bit command + expansion + check, sent again with inverted command/expansion/check.
    fun sharp(address: Int, command: Int): IrSignal {
        val b = PulseBuilder()
        fun frame(cmd: Int, expansion: Int, check: Int) {
            val data = (address.toLong() and 0x1F) or ((cmd.toLong() and 0xFF) shl 5) or
                (expansion.toLong() shl 13) or (check.toLong() shl 14)
            b.bits(data, 15, 320, 1_680, 680, lsbFirst = true).mark(320)
        }
        frame(command, 1, 0)
        b.space(40_000)
        frame(command.inv() and 0xFF, 0, 1)
        b.space(40_000)
        return b.build(38_000)
    }
}
