package com.remotku.app

import android.app.Application
import android.content.Context
import android.os.Build
import com.remotku.app.data.ir.IrProfileRepository
import com.remotku.app.data.ir.IrTransmitter
import com.remotku.app.data.local.RemotkuDatabase
import com.remotku.app.data.tv.TvRemoteRepository
import com.remotku.app.data.tv.atv.CertificateManager
import com.remotku.app.data.tv.discovery.DeviceDiscoveryRepository
import com.remotku.app.data.tv.discovery.NsdDiscovery
import com.remotku.app.data.tv.discovery.SubnetScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Manual dependency graph; everything here lives as long as the process. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = RemotkuDatabase.create(context)
    private val certificates = CertificateManager(File(context.filesDir, "atv"))

    val discovery = DeviceDiscoveryRepository(NsdDiscovery(context), SubnetScanner(context))
    val tvRemote = TvRemoteRepository(
        certificates = certificates,
        adbKeyDirectory = File(context.filesDir, "adb"),
        dao = database.tvDeviceDao(),
        scope = appScope,
        clientName = "Remotku (${Build.MODEL})",
    )
    val irProfiles = IrProfileRepository(context)
    val irTransmitter = IrTransmitter(context)
}

class RemotkuApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
