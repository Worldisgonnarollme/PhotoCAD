package com.example.photocad.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.photocad.data.SiteSummary

@Composable
fun ObjectCard(
    summary: SiteSummary,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onExport: (() -> Unit)? = null,
    onSiteReport: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(summary.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                if (summary.address.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        Text(summary.address, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                    }
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountLabel(summary.blueprintCount, "чертежа")
                    CountLabel(summary.pointCount, "точек")
                }
            }
            if (onExport != null || onSiteReport != null) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Действия с объектом", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Изменить") }, onClick = { menuOpen = false; onEdit() })
                        onSiteReport?.let { action -> DropdownMenuItem(text = { Text("Отчёт по объекту") }, onClick = { menuOpen = false; action() }) }
                        onExport?.let { action -> DropdownMenuItem(text = { Text("Экспорт объекта") }, onClick = { menuOpen = false; action() }) }
                    }
                }
            } else IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Изменить объект", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun CountLabel(count: Int, label: String) {
    Row {
        Text("$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(" $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
