package com.remotku.app.data.ir.protocol

/** A raw IR burst for `ConsumerIrManager.transmit`: alternating mark/space durations in microseconds. */
class IrSignal(val frequencyHz: Int, val pattern: IntArray) {
    val durationMicros: Long get() = pattern.sumOf { it.toLong() }
    override fun toString() = "IrSignal(${frequencyHz}Hz, ${pattern.size} pulses, ${durationMicros}µs)"
}

/**
 * Builds a mark/space pattern, merging consecutive pulses of the same level (needed for
 * Manchester-coded protocols) and dropping a leading space, since patterns must start with a mark.
 */
class PulseBuilder {
    private val pulses = ArrayList<Int>(128)

    private val lastIsMark: Boolean get() = pulses.size % 2 == 1

    fun mark(micros: Int) = add(true, micros)
    fun space(micros: Int) = add(false, micros)

    private fun add(isMark: Boolean, micros: Int): PulseBuilder = apply {
        if (micros <= 0) return@apply
        when {
            pulses.isEmpty() -> if (isMark) pulses.add(micros)
            isMark == lastIsMark -> pulses[pulses.lastIndex] += micros
            else -> pulses.add(micros)
        }
    }

    val totalMicros: Int get() = pulses.sum()

    /** Pulse-distance encoding: every bit is a mark followed by a short (0) or long (1) space. */
    fun bits(value: Long, count: Int, mark: Int, oneSpace: Int, zeroSpace: Int, lsbFirst: Boolean) = apply {
        for (i in 0 until count) {
            val shift = if (lsbFirst) i else count - 1 - i
            val bit = (value ushr shift) and 1L
            mark(mark)
            space(if (bit == 1L) oneSpace else zeroSpace)
        }
    }

    fun bytes(data: IntArray, mark: Int, oneSpace: Int, zeroSpace: Int, lsbFirst: Boolean = true) = apply {
        data.forEach { bits(it.toLong() and 0xFF, 8, mark, oneSpace, zeroSpace, lsbFirst) }
    }

    fun build(frequencyHz: Int): IrSignal = IrSignal(frequencyHz, pulses.toIntArray())
}
