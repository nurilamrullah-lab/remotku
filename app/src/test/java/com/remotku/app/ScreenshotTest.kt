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
import com.remotku.app.domain.ir.SavedRemote
import com.remotku.app.ui.ir.ActiveRemote
import com.remotku.app.ui.ir.BrandListState
import com.remotku.app.ui.ir.PairingState
import com.remotku.app.ui.ir.Route
import com.remotku.app.ui.main.RemotkuApp
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
                    Box(Modifier.fillMaxSize().background(RemotkuColors.Background).padding(top = 8.dp)) { content() }
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

    private val tvCatalog by lazy { loadCatalog(IrCategory.TV) }
    private val acCatalog by lazy { loadCatalog(IrCategory.AC) }
    private val fanCatalog by lazy { loadCatalog(IrCategory.FAN) }

    private fun saved(category: IrCategory, brand: String, set: String, index: Int, count: Int) =
        SavedRemote("$brand-$category", category, brand, set, index, count, 0L)

    private fun app(ir: IrUiState): @Composable () -> Unit = { RemotkuApp(ir, TvUiState(), {}, {}) }


    @Test
    fun home() = shot("3_home", app(
        IrUiState(
            hasIrEmitter = true,
            saved = listOf(
                saved(IrCategory.TV, "Samsung", "tv_1", 1, 24),
                saved(IrCategory.AC, "Daikin", "@AC_DAIKIN", 1, 11),
                saved(IrCategory.STB, "Matrix Apple", "stb_dvb_generic_a", 2, 10),
                saved(IrCategory.FAN, "Miyako", "fan_1", 3, 8),
            ),
        ),
    ))

    @Test
    fun addController() = shot("4_add_controller", app(IrUiState(hasIrEmitter = true, routes = listOf(Route.Home, Route.AddController))))

    @Test
    fun brandSelect() = shot("5_brand_select", app(
        IrUiState(
            hasIrEmitter = true,
            routes = listOf(Route.Home, Route.AddController, Route.Brands(IrCategory.TV)),
            brandList = BrandListState(IrCategory.TV, tvCatalog),
        ),
    ))

    @Test
    fun brandSearch() = shot("6_brand_search", app(
        IrUiState(
            hasIrEmitter = true,
            routes = listOf(Route.Home, Route.AddController, Route.Brands(IrCategory.TV)),
            brandList = BrandListState(IrCategory.TV, tvCatalog, query = "aquos"),
        ),
    ))

    @Test
    fun pairing() = shot("7_pairing", app(
        run {
            val brand = tvCatalog.brands.first { it.name == "Polytron" }
            IrUiState(
                hasIrEmitter = true,
                routes = listOf(Route.Home, Route.Brands(IrCategory.TV), Route.Pairing(IrCategory.TV, brand.name)),
                pairing = PairingState(IrCategory.TV, brand.name, tvCatalog.codeSetsFor(brand), index = 1, tested = true, loading = false),
            )
        },
    ))

    @Test
    fun irTvRemote() = shot("8_ir_tv_remote", app(
        run {
            val brand = tvCatalog.brands.first { it.name == "Samsung" }
            val set = tvCatalog.codeSetsFor(brand).first()
            val remote = saved(IrCategory.TV, "Samsung", set.id, 1, brand.codeSetIds.size)
            IrUiState(
                hasIrEmitter = true, routes = listOf(Route.Home, Route.Remote(remote.id)),
                active = ActiveRemote(remote, set, lastSignal = "Samsung · Power · 38 kHz"),
            )
        },
    ))

    @Test
    fun irAcRemote() = shot("9_ir_ac_remote", app(
        run {
            val set = acCatalog.codeSets.getValue("@AC_DAIKIN")
            val remote = saved(IrCategory.AC, "Daikin", set.id, 1, 11)
            IrUiState(
                hasIrEmitter = true, routes = listOf(Route.Home, Route.Remote(remote.id)),
                active = ActiveRemote(remote, set, AcState(power = true, mode = AcMode.COOL, temperature = 24, fan = AcFan.MEDIUM, swing = true)),
            )
        },
    ))

    @Test
    fun irFanRemote() = shot("10_ir_fan_remote", app(
        run {
            val brand = fanCatalog.brands.first { it.name == "Dyson" }
            val set = fanCatalog.codeSetsFor(brand).maxBy { it.codes.size }
            val remote = saved(IrCategory.FAN, brand.name, set.id, 1, brand.codeSetIds.size)
            IrUiState(hasIrEmitter = true, routes = listOf(Route.Home, Route.Remote(remote.id)), active = ActiveRemote(remote, set))
        },
    ))
}
