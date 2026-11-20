package com.remotku.app.ui.ir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WindPower
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remotku.app.domain.ir.IrCategory

val IrCategory.icon: ImageVector
    get() = when (this) {
        IrCategory.TV -> Icons.Outlined.Tv
        IrCategory.STB -> Icons.Outlined.SettingsInputAntenna
        IrCategory.AC -> Icons.Outlined.AcUnit
        IrCategory.PROJECTOR -> Icons.Outlined.Videocam
        IrCategory.OTT -> Icons.Outlined.Cast
        IrCategory.DVD -> Icons.Outlined.Album
        IrCategory.FAN -> Icons.Outlined.WindPower
        IrCategory.ROBOT -> Icons.Outlined.CleaningServices
        IrCategory.PURIFIER -> Icons.Outlined.WaterDrop
        IrCategory.AUDIO -> Icons.Outlined.Speaker
    }

/** Soft per-category tint so the grid reads at a glance without becoming loud. */
val IrCategory.tint: Color
    get() = when (this) {
        IrCategory.TV -> Color(0xFF4F46E5)
        IrCategory.STB -> Color(0xFF0EA5E9)
        IrCategory.AC -> Color(0xFF06B6D4)
        IrCategory.PROJECTOR -> Color(0xFF8B5CF6)
        IrCategory.OTT -> Color(0xFF2563EB)
        IrCategory.DVD -> Color(0xFFDB2777)
        IrCategory.FAN -> Color(0xFF10B981)
        IrCategory.ROBOT -> Color(0xFFF59E0B)
        IrCategory.PURIFIER -> Color(0xFF14B8A6)
        IrCategory.AUDIO -> Color(0xFFEF4444)
    }

@Composable
fun CategoryBadge(category: IrCategory, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(category.tint.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = null, tint = category.tint, modifier = Modifier.size(size * 0.52f))
    }
}
