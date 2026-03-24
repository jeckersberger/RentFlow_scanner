package com.rentflow.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.ui.theme.Warning

@Composable
fun EquipmentCard(
    equipment: Equipment,
    onClick: (() -> Unit)? = null,
    adHoc: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(equipment.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (adHoc) {
                        Text(
                            "SPONTAN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Warning,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Warning.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    StatusBadge(equipment.status)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Barcode: ${equipment.barcode}", style = MaterialTheme.typography.bodyMedium)
            Text("Kategorie: ${equipment.category}", style = MaterialTheme.typography.bodyMedium)
            equipment.location?.let {
                Text("Standort: $it", style = MaterialTheme.typography.bodyMedium)
            }
            equipment.projectName?.let {
                Text("Projekt: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
