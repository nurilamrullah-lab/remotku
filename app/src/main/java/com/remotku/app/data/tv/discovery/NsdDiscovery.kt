package com.remotku.app.data.tv.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address
import kotlin.coroutines.resume

data class ResolvedService(
    val serviceType: String,
    val serviceName: String,
    val host: String,
    val port: Int,
    val friendlyName: String?,
    val model: String?,
)

/** DNS-SD browsing through [NsdManager] (multicast lock held while browsing). */
class NsdDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)

    // Before API 34 NsdManager can only resolve one service at a time.
    private val resolveMutex = Mutex()

    fun discover(serviceType: String): Flow<ResolvedService> = callbackFlow {
        val lock = wifi?.createMulticastLock("remotku-mdns")?.apply {
            setReferenceCounted(true)
            acquire()
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                launch { resolve(serviceType, serviceInfo)?.let { trySend(it) } }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("mDNS gagal dimulai ($errorCode)"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { lock?.release() }
        }
    }

    private suspend fun resolve(type: String, info: NsdServiceInfo): ResolvedService? = resolveMutex.withLock {
        repeat(RESOLVE_ATTEMPTS) {
            resolveOnce(type, info)?.let { return it }
            delay(250)
        }
        null
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveOnce(type: String, info: NsdServiceInfo): ResolvedService? =
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val address = serviceInfo.host
                        val result = if (address is Inet4Address) {
                            val attrs = serviceInfo.attributes.orEmpty()
                            ResolvedService(
                                serviceType = type,
                                serviceName = serviceInfo.serviceName,
                                host = address.hostAddress.orEmpty(),
                                port = serviceInfo.port,
                                friendlyName = attrs["fn"]?.toString(Charsets.UTF_8),
                                model = attrs["md"]?.toString(Charsets.UTF_8),
                            )
                        } else {
                            null
                        }
                        if (cont.isActive) cont.resume(result)
                    }
                },
            )
        }

    companion object {
        const val SERVICE_ATV_REMOTE = "_androidtvremote2._tcp."
        const val SERVICE_GOOGLE_CAST = "_googlecast._tcp."
        private const val RESOLVE_ATTEMPTS = 3
    }
}
