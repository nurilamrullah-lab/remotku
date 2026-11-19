package com.remotku.app.data.tv.atv

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Owns the client identity (RSA key + self-signed certificate) that the TV remembers after pairing.
 * It must stay stable across launches, otherwise every TV would need to be paired again.
 */
class CertificateManager(private val directory: File) {

    private val keyFile = File(directory, "atv_client_key.der")
    private val certFile = File(directory, "atv_client_cert.der")

    @Volatile
    private var identity: Pair<PrivateKey, X509Certificate>? = null

    @Synchronized
    fun identity(): Pair<PrivateKey, X509Certificate> {
        identity?.let { return it }
        val loaded = if (keyFile.exists() && certFile.exists()) runCatching { load() }.getOrNull() else null
        val result = loaded ?: generate().also(::save)
        identity = result
        return result
    }

    val clientPublicKey: RSAPublicKey get() = identity().second.publicKey as RSAPublicKey

    fun sslContext(): SSLContext {
        val (key, cert) = identity()
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry(ALIAS, key, PASSWORD, arrayOf(cert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, PASSWORD)
        }
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf(AcceptTvCertificates), SecureRandom())
        }
    }

    private fun generate(): Pair<PrivateKey, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=atvremote, O=Remotku, C=ID")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(63, SecureRandom()).add(BigInteger.ONE),
            Date(now - TimeUnit.DAYS.toMillis(1)),
            Date(now + TimeUnit.DAYS.toMillis(365L * 20)),
            subject,
            keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return keyPair.private to certificate
    }

    private fun save(identity: Pair<PrivateKey, X509Certificate>) {
        directory.mkdirs()
        keyFile.writeBytes(identity.first.encoded)
        certFile.writeBytes(identity.second.encoded)
    }

    private fun load(): Pair<PrivateKey, X509Certificate> {
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val cert = certFile.inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        return key to cert
    }

    /**
     * Android TVs present self-signed certificates, so there is no CA to validate against.
     * Trust is established by the on-screen pairing code, which binds both public keys.
     */
    private object AcceptTvCertificates : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val ALIAS = "remotku"
        val PASSWORD = "remotku".toCharArray()
    }
}
