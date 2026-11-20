package com.remotku.app.data.tv.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

sealed interface ScanEvent {
    data class Found(val host: String, val openPorts: Set<Int>) : ScanEvent
    data class Progress(val scanned: Int, val total: Int) : ScanEvent
}

data class LocalNetwork(val address: Inet4Address, val prefixLength: Int, val isWifiOrEthernet: Boolean)

/** TCP connect sweep of the local subnet, used when mDNS is blocked or the TV doesn't advertise. */
class SubnetScanner(context: Context) {

    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun localNetwork(): LocalNetwork? {
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network)
        val isLan = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val link = connectivity.getLinkProperties(network)?.linkAddresses?.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress
        } ?: return null
        return LocalNetwork(link.address as Inet4Address, link.prefixLength, isLan)
    }

    fun scan(ports: List<Int>, timeoutMs: Int = 350): Flow<ScanEvent> = channelFlow {
        val network = localNetwork() ?: throw IllegalStateException("Tidak terhubung ke jaringan WiFi/LAN")
        val hosts = hostsOf(network.address, network.prefixLength)
        val scanned = AtomicInteger()
        val semaphore = Semaphore(PARALLELISM)
        hosts.forEach { host ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    val open = openPorts(host, ports, timeoutMs)
                    if (open.isNotEmpty()) send(ScanEvent.Found(host, open))
                    send(ScanEvent.Progress(scanned.incrementAndGet(), hosts.size))
                }
            }
        }
    }

    suspend fun openPorts(host: String, ports: List<Int>, timeoutMs: Int): Set<Int> = coroutineScope {
        ports.map { port -> async(Dispatchers.IO) { port.takeIf { isOpen(host, it, timeoutMs) } } }
            .awaitAll()
            .filterNotNull()
            .toSet()
    }

    private suspend fun isOpen(host: String, port: Int, timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val PARALLELISM = 48

        /** Hosts of the subnet excluding [self]; networks larger than /24 are narrowed to our /24. */
        fun hostsOf(self: Inet4Address, prefixLength: Int): List<String> {
            val prefix = prefixLength.coerceIn(24, 30)
            val ip = self.address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val networkAddress = ip and mask
            val broadcast = networkAddress or (mask.inv() and 0xFFFFFFFFL)
            return ((networkAddress + 1) until broadcast)
                .filter { it != ip }
                .map { v -> "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}" }
        }
    }
}
