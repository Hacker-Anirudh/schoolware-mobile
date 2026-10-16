@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.schoolgrades.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.schoolgrades.AgendaPoint
import dev.schoolgrades.Subject
import dev.schoolgrades.Test
import dev.schoolgrades.UiState
import dev.schoolgrades.calculateOverallAverage
import dev.schoolgrades.calculateOverallMedian
import dev.schoolgrades.formatNumber
import java.io.File

enum class GradesTab { Punten, Todo, Rapporten }

@Composable
fun GradesScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onDownloadReport: suspend (Long) -> File,
) {
    var selectedTab by rememberSaveable { mutableStateOf(GradesTab.Punten) }
    var showingInfo by rememberSaveable { mutableStateOf(false) }
    var viewingPdfId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewingPdfName by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = showingInfo) { showingInfo = false }
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
                navigationIcon = {
                    if (!showingInfo) {
                        TextButton(onClick = { showingInfo = true }) {
                            Text("Over")
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            if (showingInfo) "Informatie" else when (selectedTab) {
                                GradesTab.Punten -> "Puntenboek"
                                GradesTab.Todo -> "Taken & toetsen"
                                GradesTab.Rapporten -> "PDF Rapporten"
                            }
                        )
                        if (!showingInfo && state.studierichting.isNotBlank() && (selectedTab == GradesTab.Punten)) {
                            Text(
                                state.studierichting,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleBiometric(!state.biometricEnabled) }) {
                        Icon(
                            imageVector = if (state.biometricEnabled) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            contentDescription = if (state.biometricEnabled) "Biometrie uitschakelen" else "Biometrie inschakelen",
                            tint = if (state.biometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onSignOut) { Text("Uitloggen") }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            if (!showingInfo) {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == GradesTab.Punten,
                    onClick = { selectedTab = GradesTab.Punten },
                    icon = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                    label = { Text("Punten") },
                )
                NavigationBarItem(
                    selected = selectedTab == GradesTab.Todo,
                    onClick = { selectedTab = GradesTab.Todo },
                    icon = { Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null) },
                    label = { Text("To-Do") },
                )
                NavigationBarItem(
                    selected = selectedTab == GradesTab.Rapporten,
                    onClick = { selectedTab = GradesTab.Rapporten },
                    icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    label = { Text("Rapporten") },
                )
            }
            }
        },
    ) { padding ->
        if (showingInfo) {
            InfoScreen(Modifier.fillMaxSize().padding(padding))
        } else {
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
                                item { OverallAverageCard(overallAvg, calculateOverallMedian(state.subjects)) }
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
                    GradesTab.Todo -> {
                        TodoScreen(state, onRefresh)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("Schoolware++", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Text(
                "Een geoptimaliseerde versie van Schoolware (HHH) voor smartphones. Extra mediaan-functie toegevoegd!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { InfoSection("Privacy", "Het app verbindt zichzelf direct met Microsoft en de API van Schoolware. Uitloggen wist alles.") }
        item { InfoSection("Licentie", "Dit applicatie wordt vrijgegeven onder the GNU GPL v3 licentie.")}
        item {
            Text(
                "Bekijk de broncode op GitHub",
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/Hacker-Anirudh/schoolware-mobile")
                },
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
        }
        item {
            Text(
                "(c) Anirudh Menon 2026. Alle rechten voorbehouden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoSection(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodoScreen(state: UiState, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.todoError?.let { message -> item { ErrorCard(message, onRefresh) } }
        if (state.todoItems.isEmpty() && !state.refreshing && state.todoError == null) {
            item {
                Text(
                    "Geen aankomende taken of toetsen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.todoItems, key = { it.id }) { point -> TodoCard(point) }
    }
}

@Composable
private fun TodoCard(point: AgendaPoint) {
    val typeLabel = if (point.type == 1001) "Taak" else "Toets"
    val dateLabel = point.startsAt.take(16).replace('T', ' ')
    val comment = androidx.core.text.HtmlCompat.fromHtml(
        point.comment,
        androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT,
    ).toString().trim()

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(typeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(point.title, style = MaterialTheme.typography.titleMedium)
            if (point.subject.isNotBlank()) {
                Text(point.subject, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (comment.isNotBlank()) {
                Text(comment, style = MaterialTheme.typography.bodySmall, maxLines = 4)
            }
        }
    }
}

@Composable
private fun OverallAverageCard(averageRatio: Double, medianRatio: Double?) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                "Algemeen Percentage",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Gewogen studentgemiddelde en klasmediaan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Jij", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${formatNumber(averageRatio * 100)}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                medianRatio?.let { median ->
                    Column(Modifier.weight(1f)) {
                        Text("Mediaan", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${formatNumber(median * 100)}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
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
                val averageLabel = subject.weightedAveragePercentageLabel
                val medianLabel = subject.medianAveragePercentageLabel
                if (averageLabel != null || medianLabel != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        averageLabel?.let { label ->
                            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        medianLabel?.let { label ->
                            Text("M $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
        test.median?.let { median ->
            val medianLabel = if (test.max > 0) {
                "${formatNumber(median * test.max)} / ${formatNumber(test.max)} (${formatNumber(median * 100)}%)"
            } else {
                "${formatNumber(median * 100)}%"
            }
            Text(
                "Mediaan: $medianLabel",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
