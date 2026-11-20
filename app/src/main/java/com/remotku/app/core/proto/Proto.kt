package com.remotku.app.core.proto

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal protobuf encoder covering the wire types used by the Android TV Remote v2
 * (polo pairing + remotemessage) protocol: varint (0) and length-delimited (2).
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Long): ProtoWriter = apply {
        tag(field, WIRE_VARINT)
        writeVarint(out, value)
    }

    fun int(field: Int, value: Int): ProtoWriter = int(field, value.toLong())

    fun bool(field: Int, value: Boolean): ProtoWriter = int(field, if (value) 1L else 0L)

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        tag(field, WIRE_LENGTH_DELIMITED)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, block: ProtoWriter.() -> Unit): ProtoWriter =
        bytes(field, ProtoWriter().apply(block).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5

        fun writeVarint(out: OutputStream, value: Long) {
            var v = value
            while (true) {
                if ((v and 0x7FL.inv()) == 0L) {
                    out.write(v.toInt())
                    return
                }
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }
    }
}

/** Parsed protobuf message; values are kept as raw varints (Long) or length-delimited payloads. */
class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {

    val fieldNumbers: Set<Int> get() = fields.keys

    fun has(field: Int): Boolean = fields.containsKey(field)

    fun long(field: Int, default: Long = 0L): Long = (fields[field]?.lastOrNull() as? Long) ?: default

    fun int(field: Int, default: Int = 0): Int = long(field, default.toLong()).toInt()

    fun bool(field: Int): Boolean = long(field) != 0L

    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray

    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)

    fun message(field: Int): ProtoMessage? = bytes(field)?.let(::parse)

    fun messages(field: Int): List<ProtoMessage> =
        fields[field].orEmpty().filterIsInstance<ByteArray>().map(::parse)

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val result = LinkedHashMap<Int, MutableList<Any>>()
            var pos = 0
            fun readVarint(): Long {
                var shift = 0
                var value = 0L
                while (true) {
                    if (pos >= data.size) throw IOException("Truncated varint")
                    val b = data[pos++].toInt() and 0xFF
                    value = value or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return value
                    shift += 7
                    if (shift > 63) throw IOException("Malformed varint")
                }
            }
            while (pos < data.size) {
                val tag = readVarint()
                val field = (tag ushr 3).toInt()
                when ((tag and 0x7).toInt()) {
                    ProtoWriter.WIRE_VARINT -> result.getOrPut(field) { mutableListOf() }.add(readVarint())
                    ProtoWriter.WIRE_FIXED64 -> pos += 8
                    ProtoWriter.WIRE_LENGTH_DELIMITED -> {
                        val len = readVarint().toInt()
                        if (len < 0 || pos + len > data.size) throw IOException("Truncated field $field")
                        result.getOrPut(field) { mutableListOf() }.add(data.copyOfRange(pos, pos + len))
                        pos += len
                    }
                    ProtoWriter.WIRE_FIXED32 -> pos += 4
                    else -> throw IOException("Unsupported wire type in field $field")
                }
            }
            return ProtoMessage(result)
        }
    }
}

/** Messages on both ATV remote ports are prefixed with their varint-encoded length. */
object ProtoFraming {
    private const val MAX_MESSAGE_SIZE = 1 shl 20

    fun write(out: OutputStream, payload: ByteArray) {
        val buffer = ByteArrayOutputStream(payload.size + 5)
        ProtoWriter.writeVarint(buffer, payload.size.toLong())
        buffer.write(payload)
        // Single write keeps the frame in one TLS record.
        out.write(buffer.toByteArray())
        out.flush()
    }

    fun read(input: InputStream): ByteArray {
        var shift = 0
        var length = 0L
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Connection closed by peer")
            length = length or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 35) throw IOException("Malformed frame length")
        }
        if (length > MAX_MESSAGE_SIZE) throw IOException("Frame too large: $length")
        val payload = ByteArray(length.toInt())
        var read = 0
        while (read < payload.size) {
            val n = input.read(payload, read, payload.size - read)
            if (n < 0) throw EOFException("Connection closed by peer")
            read += n
        }
        return payload
    }
}
