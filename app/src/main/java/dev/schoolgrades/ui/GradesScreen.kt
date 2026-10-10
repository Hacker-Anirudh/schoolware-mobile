@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.schoolgrades.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.schoolgrades.Subject
import dev.schoolgrades.Test
import dev.schoolgrades.UiState
import dev.schoolgrades.calculateOverallAverage
import dev.schoolgrades.formatNumber
import java.io.File

enum class GradesTab { Punten, Rapporten }

@Composable
fun GradesScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onDownloadReport: suspend (Long) -> File,
) {
    var selectedTab by rememberSaveable { mutableStateOf(GradesTab.Punten) }
    var viewingPdfId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewingPdfName by rememberSaveable { mutableStateOf("") }
    val openPdfId = viewingPdfId
    if (openPdfId != null) {
        PdfViewerScreen(
            documentId = openPdfId,
            filename = viewingPdfName.ifBlank { "Rapport" },
            download = onDownloadReport,
            onDismiss = { viewingPdfId = null },
        )
        return
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(if (selectedTab == GradesTab.Punten) "Puntenboek" else "PDF Rapporten")
                        if (state.studierichting.isNotBlank() && selectedTab == GradesTab.Punten) {
                            Text(
                                state.studierichting,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = { TextButton(onClick = onSignOut) { Text("Uitloggen") } },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == GradesTab.Punten,
                    onClick = { selectedTab = GradesTab.Punten },
                    icon = { Text("📊", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("Punten") },
                )
                NavigationBarItem(
                    selected = selectedTab == GradesTab.Rapporten,
                    onClick = { selectedTab = GradesTab.Rapporten },
                    icon = { Text("📄", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("Rapporten") },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (selectedTab) {
                GradesTab.Punten -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.error?.let { msg -> item { ErrorCard(msg, onRefresh) } }
                        val overallAvg = calculateOverallAverage(state.subjects)
                        if (overallAvg != null) {
                            item { OverallAverageCard(overallAvg) }
                        }
                        if (state.subjects.isEmpty() && !state.refreshing && state.error == null) {
                            item {
                                Text(
                                    "Nog geen punten beschikbaar. Trek om te hernieuwen!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(state.subjects, key = { it.name }) { SubjectCard(it) }
                    }
                }
                GradesTab.Rapporten -> {
                    ReportsScreen(state, onRefresh) { id, name ->
                        viewingPdfId = id
                        viewingPdfName = name
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallAverageCard(averageRatio: Double) {
    val percentage = averageRatio * 100
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "Algemeen Gemiddelde",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Gewogen gemiddelde van alle vakken",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                "${formatNumber(percentage)}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun SubjectCard(subject: Subject) {
    var expanded by rememberSaveable(subject.name) { mutableStateOf(false) }
    ElevatedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        subject.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            subject.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Gewicht: ${formatNumber(subject.subjectWeight)}",
                            style = MaterialTheme.typography.bodySmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (subject.teacher.isNotBlank()) {
                        Text(
                            subject.teacher,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val pending = if (subject.pending > 0) " · ${subject.pending} leeg" else ""
                    Text(
                        "${subject.graded} evaluaties$pending",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subject.weightedAveragePercentageLabel?.let { label ->
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (expanded) {
                Spacer(Modifier.size(8.dp))
                subject.tests.forEachIndexed { i, test ->
                    if (i > 0) HorizontalDivider()
                    TestRow(test)
                }
            }
        }
    }
}

@Composable
private fun TestRow(test: Test) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(test.title, style = MaterialTheme.typography.bodyLarge)
                val meta = listOfNotNull(
                    test.date.take(10),
                    if (test.isTask) "Taak" else "Toets",
                    test.period.ifBlank { null },
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(test.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        val score = test.score
        if (score != null && test.max > 0) {
            val ratio = (score / test.max).toFloat().coerceIn(0f, 1f)
            LinearWavyProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = if (ratio < 0.5f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (test.comment.isNotBlank()) {
            Text(
                test.comment,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}
