package dev.schoolgrades.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.schoolgrades.ReportPdfRow
import dev.schoolgrades.UiState

@Composable
fun ReportsScreen(state: UiState, onRefresh: () -> Unit, onOpen: (id: Long, name: String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
        if (state.reportPdfs.isEmpty() && !state.refreshing) {
            item {
                Text(
                    "Geen PDF rapporten beschikbaar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.reportPdfs, key = { it.id }) { pdf ->
            val formattedName = formatReportFilename(pdf.filename)
            ReportPdfCard(pdf, formattedName) { onOpen(pdf.id, formattedName) }
        }
    }
}

@Composable
private fun ReportPdfCard(pdf: ReportPdfRow, displayName: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    displayName.ifBlank { "Rapport #${pdf.id}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (pdf.date.isNotBlank()) {
                    Spacer(Modifier.padding(2.dp))
                    Text(
                        "Aangemaakt op: ${pdf.date.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onClick) {
                Text("👁️", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

fun formatReportFilename(rawFilename: String): String {
    var name = rawFilename.trim()
    name = name.replace(Regex("-\\d{4}\\.pdf$", RegexOption.IGNORE_CASE), ".pdf")
    name = name.replace(Regex("^Rapport-[A-Z]+-", RegexOption.IGNORE_CASE), "Rapport-")
    return name.ifBlank { "Rapport.pdf" }
}
