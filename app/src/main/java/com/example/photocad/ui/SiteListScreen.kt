// список объектов

package com.example.photocad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.photocad.data.*
import com.example.photocad.ui.components.DashedAddCard
import com.example.photocad.ui.components.ObjectCard

@Composable
fun SiteListScreen(db: AppDatabase, onOpenSite: (Long) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<Site?>(null) }
    val summaries by db.siteDao().getAllSummaries().collectAsState(initial = emptyList())

    when {
        creating -> CreateObjectScreen(db = db, editing = null, onBack = { creating = false }, onSaved = { creating = false })
        editingSite != null -> CreateObjectScreen(
            db = db, editing = editingSite,
            onBack = { editingSite = null },
            onSaved = { editingSite = null }
        )
        else -> Column(Modifier.fillMaxSize()) {
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp))
                        }
                        Text("PhotoCAD", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                    IconButton(
                        onClick = { creating = true },
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить объект", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Мои объекты", style = MaterialTheme.typography.titleMedium)
                Text("${summaries.size} объекта", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(summaries, key = { it.id }) { summary ->
                    ObjectCard(
                        summary = summary,
                        onClick = { onOpenSite(summary.id) },
                        onEdit = {
                            editingSite = Site(summary.id, summary.name, summary.address, summary.description)
                        }
                    )
                }
                item {
                    DashedAddCard(label = "Добавить объект", onClick = { creating = true })
                }
            }
        }
    }
}
