package com.remotku.app.ui.ir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotku.app.core.haptics.HapticType
import com.remotku.app.data.ir.ac.AcFan
import com.remotku.app.domain.ir.IrCategory
import com.remotku.app.domain.ir.IrKey
import com.remotku.app.domain.ir.IrProfile
import com.remotku.app.ui.components.DPad
import com.remotku.app.ui.components.DPadDirection
import com.remotku.app.ui.components.IconRemoteButton
import com.remotku.app.ui.components.RemoteButton
import com.remotku.app.ui.theme.RemotkuColors

@Composable
fun IrScreen(state: IrUiState, onIntent: (IrIntent) -> Unit) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!state.hasIrEmitter) {
            Text(
                "HP ini tidak memiliki IR blaster. Profil tetap bisa dijelajahi, tetapi sinyal tidak dapat dipancarkan.",
                style = MaterialTheme.typography.bodySmall,
                color = RemotkuColors.Power,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(RemotkuColors.Power.copy(alpha = 0.12f))
                    .padding(12.dp),
            )
        }
        ChipRow(IrCategory.entries, state.category, { it.label }) { onIntent(IrIntent.SelectCategory(it)) }
        ChipRow(state.brands, state.selectedBrand, { it }) { onIntent(IrIntent.SelectBrand(it)) }
        if (state.variants.size > 1) {
            ChipRow(state.variants, state.selectedProfile, { it.model }) { onIntent(IrIntent.SelectProfile(it)) }
        }
        state.selectedProfile?.let { profile ->
            ProfileInfo(profile, state.lastSignal)
            Box(Modifier.padding(horizontal = 20.dp)) {
                if (profile.isAc) AcRemote(state, onIntent) else ButtonRemote(profile, onIntent)
            }
        }
    }
}

@Composable
private fun <T> ChipRow(items: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(items, key = { label(it) }) { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item)) })
        }
    }
}

@Composable
private fun ProfileInfo(profile: IrProfile, lastSignal: String?) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RemotkuColors.Surface1)
            .padding(12.dp),
    ) {
        Text("${profile.brand} · ${profile.model}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Protokol ${profile.protocol.replace('_', ' ')}" +
                if (profile.isAc) " · stateful" else " · alamat 0x%X".format(profile.address),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!profile.verified && !profile.isAc) {
            Text(
                "Kode generik — jika perangkat tidak merespons, coba varian kode lain.",
                style = MaterialTheme.typography.bodySmall,
                color = RemotkuColors.Warning,
            )
        }
        lastSignal?.let {
            Text("Terkirim: $it", style = MaterialTheme.typography.labelSmall, color = RemotkuColors.Online)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonRemote(profile: IrProfile, onIntent: (IrIntent) -> Unit) {
    val has: (IrKey) -> Boolean = { it in profile.codes }
    val press: (IrKey) -> Unit = { onIntent(IrIntent.Press(it)) }

    @Composable
    fun Key(key: IrKey, icon: ImageVector, haptic: HapticType = HapticType.Click, color: Color = RemotkuColors.Surface2, tint: Color = Color.White) =
        IconRemoteButton(icon, key.label, { press(key) }, enabled = has(key), containerColor = color, contentColor = tint, haptic = haptic, showLabel = true)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Key(IrKey.POWER, Icons.Filled.PowerSettingsNew, HapticType.Heavy, RemotkuColors.Power.copy(alpha = 0.18f), RemotkuColors.Power)
            Key(IrKey.INPUT, Icons.AutoMirrored.Filled.Input)
            Key(IrKey.MUTE, Icons.AutoMirrored.Filled.VolumeOff)
            Key(IrKey.INFO, Icons.Filled.Info)
        }
        DPad(
            onPress = { dir ->
                press(
                    when (dir) {
                        DPadDirection.UP -> IrKey.UP
                        DPadDirection.DOWN -> IrKey.DOWN
                        DPadDirection.LEFT -> IrKey.LEFT
                        DPadDirection.RIGHT -> IrKey.RIGHT
                        DPadDirection.CENTER -> IrKey.OK
                    },
                )
            },
            size = 230.dp,
            enabled = { dir ->
                has(
                    when (dir) {
                        DPadDirection.UP -> IrKey.UP
                        DPadDirection.DOWN -> IrKey.DOWN
                        DPadDirection.LEFT -> IrKey.LEFT
                        DPadDirection.RIGHT -> IrKey.RIGHT
                        DPadDirection.CENTER -> IrKey.OK
                    },
                )
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Key(IrKey.BACK, Icons.AutoMirrored.Filled.ArrowBack)
            Key(IrKey.HOME, Icons.Filled.Home)
            Key(IrKey.MENU, Icons.Filled.Menu)
            Key(IrKey.EXIT, Icons.Filled.Close)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IrRocker("VOL", Icons.Filled.Add, Icons.Filled.Remove, has(IrKey.VOL_UP), { press(IrKey.VOL_UP) }, { press(IrKey.VOL_DOWN) })
            RemoteButton(
                onClick = { press(IrKey.EPG) }, contentDescription = "EPG",
                modifier = Modifier.size(width = 84.dp, height = 48.dp), enabled = has(IrKey.EPG),
            ) { Text("EPG", fontWeight = FontWeight.SemiBold) }
            IrRocker("CH", Icons.Filled.KeyboardArrowUp, Icons.Filled.KeyboardArrowDown, has(IrKey.CH_UP), { press(IrKey.CH_UP) }, { press(IrKey.CH_DOWN) })
        }
        val digits = listOf(IrKey.NUM_1, IrKey.NUM_2, IrKey.NUM_3, IrKey.NUM_4, IrKey.NUM_5, IrKey.NUM_6, IrKey.NUM_7, IrKey.NUM_8, IrKey.NUM_9, IrKey.NUM_0)
        if (digits.any(has)) {
            FlowRow(
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                digits.forEach { d ->
                    RemoteButton(
                        onClick = { press(d) }, contentDescription = d.label,
                        modifier = Modifier.size(width = 84.dp, height = 48.dp), enabled = has(d),
                    ) { Text(d.label, style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IrRocker(label: String, up: ImageVector, down: ImageVector, enabled: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    val shape = RoundedCornerShape(36.dp)
    Column(
        Modifier.width(76.dp).background(RemotkuColors.Surface1, shape).border(1.dp, RemotkuColors.Outline, shape).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteButton(onUp, "$label naik", Modifier.size(56.dp), shape = CircleShape, containerColor = Color.Transparent, repeatOnHold = true, enabled = enabled) { Icon(up, null) }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RemoteButton(onDown, "$label turun", Modifier.size(56.dp), shape = CircleShape, containerColor = Color.Transparent, repeatOnHold = true, enabled = enabled) { Icon(down, null) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcRemote(state: IrUiState, onIntent: (IrIntent) -> Unit) {
    val ac = state.acState
    val encoder = state.acEncoder ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Display
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(RemotkuColors.Surface1)
                .border(1.dp, RemotkuColors.Outline, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (ac.power) "${ac.temperature}°C" else "OFF",
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = if (ac.power) Color.White else RemotkuColors.TextMuted,
            )
            Text(
                "${ac.mode.label} · Kipas ${ac.fan.label} · Swing ${if (ac.swing) "On" else "Off"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconRemoteButton(Icons.Filled.Remove, "Suhu turun", { onIntent(IrIntent.AcChangeTemp(-1)) }, size = 64.dp, repeatOnHold = true)
            IconRemoteButton(
                Icons.Filled.PowerSettingsNew, if (ac.power) "Matikan" else "Nyalakan", { onIntent(IrIntent.AcTogglePower) },
                size = 84.dp, haptic = HapticType.Heavy,
                containerColor = if (ac.power) RemotkuColors.Online.copy(alpha = 0.2f) else RemotkuColors.Power.copy(alpha = 0.18f),
                contentColor = if (ac.power) RemotkuColors.Online else RemotkuColors.Power,
            )
            IconRemoteButton(Icons.Filled.Add, "Suhu naik", { onIntent(IrIntent.AcChangeTemp(1)) }, size = 64.dp, repeatOnHold = true)
        }
        Text("Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            encoder.supportedModes.forEach { mode ->
                FilterChip(selected = ac.mode == mode, onClick = { onIntent(IrIntent.AcSetMode(mode)) }, label = { Text(mode.label) })
            }
        }
        Text("Kecepatan kipas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            AcFan.entries.forEach { fan ->
                FilterChip(selected = ac.fan == fan, onClick = { onIntent(IrIntent.AcSetFan(fan)) }, label = { Text(fan.label) })
            }
        }
        IconRemoteButton(
            Icons.Filled.SwapVert, "Swing", { onIntent(IrIntent.AcToggleSwing) },
            size = 56.dp, showLabel = true,
            containerColor = if (ac.swing) RemotkuColors.PrimaryDim else RemotkuColors.Surface2,
            contentColor = if (ac.swing) RemotkuColors.Primary else Color.White,
        )
        Text(
            "Rentang suhu ${encoder.minTemp}–${encoder.maxTemp}°C. Setiap tombol mengirim seluruh status AC.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
