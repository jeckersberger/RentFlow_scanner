package com.rentflow.scanner.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rentflow.scanner.domain.model.EquipmentStatus
import com.rentflow.scanner.ui.theme.*

@Composable
fun StatusBadge(status: EquipmentStatus) {
    val (color, label) = when (status) {
        EquipmentStatus.AVAILABLE -> Success to "Verfügbar"
        EquipmentStatus.CHECKED_OUT -> Warning to "Ausgecheckt"
        EquipmentStatus.IN_MAINTENANCE -> Cyan to "Wartung"
        EquipmentStatus.DAMAGED -> Error to "Beschädigt"
        EquipmentStatus.RETIRED -> TextSecondary to "Ausgemustert"
    }
    Badge(containerColor = color) { Text(label, color = Color.White) }
}
