package com.remotku.app.ui.ir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remotku.app.core.haptics.HapticType
import com.remotku.app.domain.ir.IrKey
import com.remotku.app.ui.components.IconRemoteButton
import com.remotku.app.ui.theme.RemotkuColors

/** Smart pairing: send the test key for code set N, ask whether the device reacted, advance or save. */
@Composable
fun PairingScreen(state: PairingState, hasIrEmitter: Boolean, onIntent: (IrIntent) -> Unit) {
    val total = state.codeSets.size
    val noun = state.category.deviceNoun
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (total == 0) {
            NoticeCard("Belum ada kode untuk merek ini. Pilih merek lain atau coba merek dengan pabrikan yang sama.")
            return@Column
        }
        if (state.exhausted) {
            Spacer(Modifier.height(40.dp))
            CategoryBadge(state.category, size = 72.dp)
            Spacer(Modifier.height(20.dp))
            Text("Semua $total kode sudah dicoba", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "Pastikan IR blaster di ujung atas HP mengarah lurus ke $noun dari jarak 1–3 meter, lalu ulangi. Merek lain dengan pabrikan sama (OEM) juga sering cocok.",
                style = MaterialTheme.typography.bodyMedium, color = RemotkuColors.TextMuted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(onClick = { onIntent(IrIntent.PairingRestart) }) {
                Icon(Icons.Filled.RestartAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Ulangi dari kode 1")
            }
            TextButton(onClick = { onIntent(IrIntent.Back) }) { Text("Pilih merek lain") }
            return@Column
        }
        val set = state.current ?: return@Column
        val keyLabel = if (set.isStatefulAc) "Power" else set.testKey?.label ?: IrKey.POWER.label

        // Progress
        Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kode ${state.index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(" / $total", style = MaterialTheme.typography.titleMedium, color = RemotkuColors.TextMuted, modifier = Modifier.weight(1f))
                if (set.universal) Pill("Universal")
                if (set.isStatefulAc) Pill("Lengkap")
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (state.index + 1f) / total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                trackColor = RemotkuColors.SurfaceMuted,
            )
            Text(set.model, style = MaterialTheme.typography.bodySmall, color = RemotkuColors.TextMuted, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(28.dp))
        CategoryBadge(state.category, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Arahkan HP ke $noun dan tekan tombol $keyLabel.",
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        )
        Text(
            if (set.isStatefulAc) "AC akan menyala dalam mode Cool 24°C." else "Perhatikan apakah $noun menyala/mati atau berkedip.",
            style = MaterialTheme.typography.bodyMedium, color = RemotkuColors.TextMuted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
        IconRemoteButton(
            Icons.Filled.PowerSettingsNew, "Kirim $keyLabel", { onIntent(IrIntent.PairingTest) },
            size = 96.dp, haptic = HapticType.Heavy,
            containerColor = RemotkuColors.Power.copy(alpha = 0.10f), contentColor = RemotkuColors.Power,
        )
        Text(
            if (state.tested) "Tekan lagi untuk mengirim ulang" else "Ketuk untuk menguji",
            style = MaterialTheme.typography.labelMedium, color = RemotkuColors.TextMuted, modifier = Modifier.padding(top = 10.dp),
        )
        if (!hasIrEmitter) {
            Spacer(Modifier.height(16.dp))
            NoticeCard("IR blaster tidak terdeteksi, sinyal tidak akan terkirim.", RemotkuColors.Power)
        }
        Spacer(Modifier.height(28.dp))

        if (state.tested) {
            Column(Modifier.fillMaxWidth().card().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Apakah $noun merespons?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onIntent(IrIntent.PairingAnswer(false)) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Tidak")
                    }
                    Button(
                        onClick = { onIntent(IrIntent.PairingAnswer(true)) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RemotkuColors.Primary),
                    ) { Text("Ya, simpan") }
                }
            }
        } else {
            TextButton(onClick = { onIntent(IrIntent.PairingAnswer(false)) }) { Text("Lewati kode ini") }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(RemotkuColors.PrimarySoft).padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = RemotkuColors.Primary) }
}
