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
    RC5X("Philips RC-5X 36 kHz"),
    KASEIKYO("Kaseikyo / Panasonic 37 kHz"),
    RCA("RCA 38 kHz"),
    PIONEER("Pioneer 40 kHz"),
}

/**
 * Encoders for stateless (one code per button) remotes. Address/command follow Flipper Zero's
 * protocol semantics so codes from Flipper-IRDB replay exactly (e.g. NEC_EXT carries a 16-bit command).
 */
object IrEncoders {

    fun encode(protocol: IrProtocol, address: Int, command: Int, toggle: Boolean = false): IrSignal = when (protocol) {
        IrProtocol.NEC -> nec(address and 0xFF, command, extended = false)
        IrProtocol.NEC_EXT -> necExt(address and 0xFFFF, command and 0xFFFF)
        IrProtocol.SAMSUNG -> samsung(address, command)
        IrProtocol.RC5 -> rc5(address, command and 0x3F, toggle)
        IrProtocol.RC5X -> rc5(address, (command and 0x3F) or 0x40, toggle)
        IrProtocol.RC6 -> rc6(address, command, toggle)
        IrProtocol.SIRC12 -> sirc(address, command, addressBits = 5)
        IrProtocol.SIRC15 -> sirc(address, command, addressBits = 8)
        IrProtocol.SIRC20 -> sirc(address, command, addressBits = 13)
        IrProtocol.SHARP -> sharp(address, command)
        IrProtocol.KASEIKYO -> kaseikyo(address, command)
        IrProtocol.RCA -> rca(address, command)
        IrProtocol.PIONEER -> pioneer(address, command)
    }

    /**
     * Decodes a database code: `PROTOCOL:addressHex:commandHex` or `RAW:frequency:mark space mark …`.
     * Returns null for malformed or unsupported entries.
     */
    fun decode(code: String, toggle: Boolean = false): IrSignal? = runCatching {
        val parts = code.split(':', limit = 3)
        if (parts[0] == "RAW") {
            val pattern = parts[2].split(' ').map { it.toInt() }.toIntArray()
            require(pattern.isNotEmpty() && pattern.all { it > 0 })
            IrSignal(parts[1].toInt(), pattern)
        } else {
            encode(IrProtocol.valueOf(parts[0]), parts[1].toInt(16), parts[2].toInt(16), toggle)
        }
    }.getOrNull()

    fun necExt(address: Int, command: Int): IrSignal {
        val b = PulseBuilder().mark(9_000).space(4_500)
            .bytes(intArrayOf(address and 0xFF, address shr 8, command and 0xFF, command shr 8), NEC_UNIT, NEC_UNIT * 3, NEC_UNIT)
            .mark(NEC_UNIT)
        b.space(NEC_FRAME - b.totalMicros)
        b.mark(9_000).space(2_250).mark(NEC_UNIT)
        return b.build(NEC_FREQ)
    }

    // Kaseikyo (Panasonic & co.): 48 bits LSB-first. Address packs id(2) | vendor(16) | genre1(4) | genre2(4).
    fun kaseikyo(address: Int, command: Int): IrSignal {
        val id = (address ushr 24) and 3
        val vendor = (address ushr 8) and 0xFFFF
        val genre1 = (address ushr 4) and 0xF
        val genre2 = address and 0xF
        val d = IntArray(6)
        d[0] = vendor and 0xFF
        d[1] = vendor ushr 8
        val parity = (d[0] xor d[1]).let { (it and 0xF) xor (it ushr 4) }
        d[2] = (parity and 0xF) or (genre1 shl 4)
        d[3] = genre2 or ((command and 0xF) shl 4)
        d[4] = (id shl 6) or ((command ushr 4) and 0x3F)
        d[5] = d[2] xor d[3] xor d[4]
        val unit = 432
        val b = PulseBuilder().mark(8 * unit).space(4 * unit).bytes(d, unit, 3 * unit, unit).mark(unit)
        b.space(74_000)
        return b.build(37_000)
    }

    // RCA: 4-bit address + 8-bit command followed by their complements, 24 bits LSB-first (Flipper layout).
    fun rca(address: Int, command: Int): IrSignal {
        val a = address and 0xF
        val c = command and 0xFF
        val data = a.toLong() or (c.toLong() shl 4) or ((a.inv() and 0xF).toLong() shl 12) or ((c.inv() and 0xFF).toLong() shl 16)
        val b = PulseBuilder().mark(4_000).space(4_000).bits(data, 24, 500, 2_000, 1_000, lsbFirst = true).mark(500)
        b.space(8_000)
        return b.build(38_000)
    }

    // Pioneer: NEC-style frame at 40 kHz with a 8.5/4.2 ms leader, always sent twice.
    fun pioneer(address: Int, command: Int): IrSignal {
        val a = address and 0xFF
        val c = command and 0xFF
        val b = PulseBuilder()
        repeat(2) {
            b.mark(8_500).space(4_225)
                .bytes(intArrayOf(a, a.inv() and 0xFF, c, c.inv() and 0xFF), 500, 1_500, 500)
                .mark(500).space(26_000)
        }
        return b.build(40_000)
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
