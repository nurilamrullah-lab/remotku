package com.remotku.app.data.tv.atv

import com.remotku.app.core.proto.ProtoFraming
import com.remotku.app.core.proto.ProtoMessage
import com.remotku.app.core.proto.ProtoWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

open class PairingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Rejected locally (format or checksum byte) before anything is sent, so the session stays usable. */
class InvalidPairingCodeException(message: String) : PairingException(message)

/**
 * Client side of the Google "polo" pairing protocol (port 6467).
 * After [start] the TV shows a 6 character hex code that must be passed to [finish].
 */
class AtvPairingSession internal constructor(
    private val socket: SSLSocket,
    private val certificates: CertificateManager,
) : Closeable {

    private val input = socket.inputStream
    private val output = socket.outputStream

    fun start(clientName: String) {
        send {
            message(F_PAIRING_REQUEST) {
                string(1, SERVICE_NAME)
                string(2, clientName)
            }
        }
        expect(F_PAIRING_REQUEST_ACK)
        send {
            message(F_OPTIONS) {
                message(1) { int(1, ENCODING_HEXADECIMAL); int(2, CODE_LENGTH) }
                int(3, ROLE_INPUT)
            }
        }
        expect(F_OPTIONS)
        send {
            message(F_CONFIGURATION) {
                message(1) { int(1, ENCODING_HEXADECIMAL); int(2, CODE_LENGTH) }
                int(2, ROLE_INPUT)
            }
        }
        expect(F_CONFIGURATION_ACK)
    }

    fun finish(code: String) {
        val secret = computeSecret(
            code = code,
            clientKey = certificates.clientPublicKey,
            serverKey = (socket.session.peerCertificates.first() as X509Certificate).publicKey as RSAPublicKey,
        )
        send { message(F_SECRET) { bytes(1, secret) } }
        expect(F_SECRET_ACK)
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun send(block: ProtoWriter.() -> Unit) {
        val payload = ProtoWriter()
            .int(1, PROTOCOL_VERSION)
            .int(2, STATUS_OK)
            .apply(block)
            .toByteArray()
        ProtoFraming.write(output, payload)
    }

    private fun expect(field: Int): ProtoMessage {
        val message = ProtoMessage.parse(ProtoFraming.read(input))
        when (val status = message.int(2)) {
            STATUS_OK -> Unit
            STATUS_BAD_SECRET -> throw PairingException("Kode pairing salah")
            STATUS_BAD_CONFIGURATION -> throw PairingException("TV menolak konfigurasi pairing")
            else -> throw PairingException("TV mengembalikan status $status")
        }
        if (!message.has(field)) {
            throw PairingException("Balasan pairing tidak terduga: field ${message.fieldNumbers}")
        }
        return message
    }

    companion object {
        private const val SERVICE_NAME = "atvremote"
        private const val PROTOCOL_VERSION = 2
        private const val STATUS_OK = 200
        private const val STATUS_BAD_CONFIGURATION = 401
        private const val STATUS_BAD_SECRET = 402
        private const val ENCODING_HEXADECIMAL = 3
        private const val ROLE_INPUT = 1
        const val CODE_LENGTH = 6

        private const val F_PAIRING_REQUEST = 10
        private const val F_PAIRING_REQUEST_ACK = 11
        private const val F_OPTIONS = 20
        private const val F_CONFIGURATION = 30
        private const val F_CONFIGURATION_ACK = 31
        private const val F_SECRET = 40
        private const val F_SECRET_ACK = 41

        /**
         * secret = SHA-256(clientModulus | clientExponent | serverModulus | serverExponent | code[2..6]).
         * The first code byte is a checksum: it must equal the first byte of the hash.
         */
        fun computeSecret(code: String, clientKey: RSAPublicKey, serverKey: RSAPublicKey): ByteArray {
            val normalized = code.trim().uppercase()
            if (normalized.length != CODE_LENGTH || normalized.any { it !in "0123456789ABCDEF" }) {
                throw InvalidPairingCodeException("Kode harus 6 karakter heksadesimal (0-9, A-F)")
            }
            val digest = MessageDigest.getInstance("SHA-256").apply {
                update(clientKey.modulus.toUnsignedBytes())
                update(clientKey.publicExponent.toUnsignedBytes())
                update(serverKey.modulus.toUnsignedBytes())
                update(serverKey.publicExponent.toUnsignedBytes())
                update(normalized.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            }.digest()
            if (digest[0] != normalized.substring(0, 2).toInt(16).toByte()) {
                throw InvalidPairingCodeException("Kode pairing salah, periksa lagi kode di layar TV")
            }
            return digest
        }

        private fun BigInteger.toUnsignedBytes(): ByteArray {
            val bytes = toByteArray()
            return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        }
    }
}

class AtvPairingClient(private val certificates: CertificateManager) {

    suspend fun open(host: String, port: Int): AtvPairingSession = withContext(Dispatchers.IO) {
        val socket = certificates.sslContext().socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            socket.startHandshake()
            AtvPairingSession(socket, certificates)
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
