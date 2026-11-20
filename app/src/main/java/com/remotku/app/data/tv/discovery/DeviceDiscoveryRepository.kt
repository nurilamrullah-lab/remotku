package com.remotku.app.data.tv.discovery

import com.remotku.app.domain.tv.DiscoverySource
import com.remotku.app.domain.tv.TvDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

sealed interface DiscoveryUpdate {
    data class DeviceFound(val device: TvDevice) : DiscoveryUpdate
    data class ScanProgress(val fraction: Float) : DiscoveryUpdate
    data class Warning(val message: String) : DiscoveryUpdate
}

/** Combines mDNS (ATV Remote + Cast) with the subnet port sweep into a single update stream. */
class DeviceDiscoveryRepository(
    private val nsd: NsdDiscovery,
    private val scanner: SubnetScanner,
) {

    fun localNetwork(): LocalNetwork? = scanner.localNetwork()

    fun discover(includeSubnetScan: Boolean = true): Flow<DiscoveryUpdate> {
        val atv = nsd.discover(NsdDiscovery.SERVICE_ATV_REMOTE)
            .map { s ->
                DiscoveryUpdate.DeviceFound(
                    TvDevice(
                        host = s.host,
                        name = s.serviceName,
                        model = s.model,
                        atvPort = s.port,
                        sources = setOf(DiscoverySource.MDNS_ATV_REMOTE),
                    ),
                ) as DiscoveryUpdate
            }
            .catch { emit(DiscoveryUpdate.Warning(it.message ?: "mDNS Android TV gagal")) }

        val cast = nsd.discover(NsdDiscovery.SERVICE_GOOGLE_CAST)
            .map { s ->
                DiscoveryUpdate.DeviceFound(
                    TvDevice(
                        host = s.host,
                        name = s.friendlyName ?: TvDevice.genericName(s.host),
                        model = s.model,
                        castPort = s.port,
                        sources = setOf(DiscoverySource.MDNS_CAST),
                    ),
                ) as DiscoveryUpdate
            }
            .catch { emit(DiscoveryUpdate.Warning(it.message ?: "mDNS Cast gagal")) }

        val sweep = if (includeSubnetScan) {
            scanner.scan(SCAN_PORTS)
                .mapNotNull { event ->
                    when (event) {
                        is ScanEvent.Progress -> DiscoveryUpdate.ScanProgress(event.scanned.toFloat() / event.total)
                        is ScanEvent.Found -> {
                            val ports = event.openPorts
                            if (TvDevice.DEFAULT_REMOTE_PORT !in ports &&
                                TvDevice.DEFAULT_PAIRING_PORT !in ports &&
                                TvDevice.DEFAULT_ADB_PORT !in ports
                            ) {
                                null // Cast-only hosts (speakers, etc.) are reported by mDNS with a proper name.
                            } else {
                                DiscoveryUpdate.DeviceFound(deviceFromPorts(event.host, ports, DiscoverySource.SUBNET_SCAN))
                            }
                        }
                    }
                }
                .catch { emit(DiscoveryUpdate.Warning(it.message ?: "Pemindaian subnet gagal")) }
        } else {
            emptyFlow()
        }
        return merge(atv, cast, sweep)
    }

    /** Probes a manually entered address so the device list can show what it supports. */
    suspend fun probe(host: String, name: String?): TvDevice {
        val ports = scanner.openPorts(host, SCAN_PORTS, timeoutMs = 1_200)
        val device = deviceFromPorts(host, ports, DiscoverySource.MANUAL)
        return if (name.isNullOrBlank()) device else device.copy(name = name)
    }

    private fun deviceFromPorts(host: String, ports: Set<Int>, source: DiscoverySource): TvDevice {
        val hasAtv = TvDevice.DEFAULT_REMOTE_PORT in ports || TvDevice.DEFAULT_PAIRING_PORT in ports
        return TvDevice(
            host = host,
            name = TvDevice.genericName(host),
            atvPort = if (hasAtv) TvDevice.DEFAULT_REMOTE_PORT else null,
            adbPort = TvDevice.DEFAULT_ADB_PORT.takeIf { it in ports },
            castPort = TvDevice.DEFAULT_CAST_PORT.takeIf { it in ports },
            sources = setOf(source),
            isOnline = ports.isNotEmpty(),
        )
    }

    companion object {
        val SCAN_PORTS = listOf(
            TvDevice.DEFAULT_REMOTE_PORT,
            TvDevice.DEFAULT_PAIRING_PORT,
            TvDevice.DEFAULT_ADB_PORT,
            TvDevice.DEFAULT_CAST_PORT,
        )
    }
}
