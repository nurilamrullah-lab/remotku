package com.remotku.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import com.github.takahirom.roborazzi.captureRoboImage
import com.remotku.app.core.haptics.LocalHaptics
import com.remotku.app.core.haptics.rememberHaptics
import com.remotku.app.data.ir.IrDatabaseParser
import com.remotku.app.data.ir.ac.AcFan
import com.remotku.app.data.ir.ac.AcMode
import com.remotku.app.data.ir.ac.AcState
import com.remotku.app.domain.ir.IrCategory
import com.remotku.app.domain.tv.DiscoverySource
import com.remotku.app.domain.tv.PointerSupport
import com.remotku.app.domain.tv.TransportCapabilities
import com.remotku.app.domain.tv.TransportMode
import com.remotku.app.domain.tv.TvConnectionState
import com.remotku.app.domain.tv.TvDevice
import com.remotku.app.domain.tv.VolumeInfo
import com.remotku.app.ui.ir.IrScreen
import com.remotku.app.ui.ir.IrUiState
import com.remotku.app.ui.theme.RemotkuColors
import com.remotku.app.ui.theme.RemotkuTheme
import com.remotku.app.ui.tv.TvScreen
import com.remotku.app.ui.tv.TvUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders key screens to PNG (build/outputs/roborazzi) as visual checks of the Compose UI. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val tv = TvDevice(
        host = "192.168.1.23", name = "Living Room TV", model = "Chromecast HD",
        atvPort = 6466, castPort = 8009, sources = setOf(DiscoverySource.MDNS_ATV_REMOTE), isPaired = true,
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            RemotkuTheme {
                CompositionLocalProvider(LocalHaptics provides rememberHaptics()) {
                    Box(Modifier.fillMaxSize().background(RemotkuColors.Black).padding(top = 8.dp)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun deviceList() = shot("1_device_list") {
        TvScreen(
            TvUiState(
                devices = listOf(
                    tv,
                    TvDevice(host = "192.168.1.40", name = "Perangkat 192.168.1.40", adbPort = 5555, atvPort = 6466, sources = setOf(DiscoverySource.SUBNET_SCAN)),
                    TvDevice(host = "192.168.1.51", name = "Kamar - Mi Box S", atvPort = 6466, isOnline = false, isSaved = true),
                ),
                isScanning = true, scanProgress = 0.62f, networkLabel = "192.168.1.8/24",
            ),
        ) {}
    }

    @Test
    fun remoteDpad() = shot("2_remote_dpad") {
        TvScreen(
            TvUiState(
                connection = TvConnectionState.Connected(
                    tv, TransportMode.ATV_REMOTE, TransportCapabilities(PointerSupport.GESTURE_DPAD, true),
                    volume = VolumeInfo(12, 100, false),
                ),
            ),
        ) {}
    }

    private val profiles by lazy {
        IrDatabaseParser.parse(listOf("src/main/assets/ir_profiles.json", "app/src/main/assets/ir_profiles.json").map(::File).first { it.exists() }.readText())
    }

    @Test
    fun irTv() = shot("4_ir_tv") {
        val p = profiles.first { it.id == "tv_samsung" }
        IrScreen(IrUiState(hasIrEmitter = true, loading = false, profiles = profiles, category = IrCategory.TV, selectedBrand = p.brand, selectedProfile = p, lastSignal = "Samsung · Power · 38 kHz")) {}
    }

    @Test
    fun irStb() = shot("5_ir_stb") {
        val p = profiles.first { it.id == "stb_matrix_merah_a" }
        IrScreen(IrUiState(hasIrEmitter = true, loading = false, profiles = profiles, category = IrCategory.STB, selectedBrand = p.brand, selectedProfile = p)) {}
    }

    @Test
    fun irAc() = shot("6_ir_ac") {
        val p = profiles.first { it.id == "ac_daikin" }
        IrScreen(
            IrUiState(
                hasIrEmitter = true, loading = false, profiles = profiles, category = IrCategory.AC, selectedBrand = p.brand, selectedProfile = p,
                acState = AcState(power = true, mode = AcMode.COOL, temperature = 24, fan = AcFan.MEDIUM, swing = true),
            ),
        ) {}
    }
}
