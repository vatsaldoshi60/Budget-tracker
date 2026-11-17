package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.export.BudgetExporter
import com.example.model.BudgetWorkspace
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.model.formatCompactCents
import com.example.ui.components.TransactionRow
import com.example.ui.theme.NumericBodyStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(
    workspace: BudgetWorkspace,
    onEditTransaction: (LedgerTransaction) -> Unit,
    onOpenExportSheet: () -> Unit,
    onExportFeedback: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<TransactionType?>(null) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    val filtered = remember(workspace.transactions, searchQuery, selectedType, selectedAccountId) {
        val q = searchQuery.trim().lowercase()
        workspace.transactions.filter { tx ->
            val typeOk = selectedType == null || tx.transactionType == selectedType
            val accountOk = selectedAccountId == null ||
                tx.accountId == selectedAccountId ||
                tx.toAccountId == selectedAccountId
            val queryOk = q.isEmpty() ||
                tx.category.lowercase().contains(q) ||
                tx.note.lowercase().contains(q) ||
                tx.accountName.lowercase().contains(q)
            typeOk && accountOk && queryOk
        }
    }

    val saveFilteredCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val csv = BudgetExporter.buildCsv(filtered, workspace.profile.currencySymbol)
            BudgetExporter.writeToUri(context, uri, csv).fold(
                onSuccess = { onExportFeedback("Saved ${filtered.size} filtered transactions as CSV") },
                onFailure = { onExportFeedback("CSV export failed: ${it.localizedMessage}") }
            )
        }
    }

    val groupedByDay = remember(filtered) {
        val formatter = SimpleDateFormat("EEE, MMM d", Locale.US)
        val todayCal = Calendar.getInstance()
        val yestCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val txCal = Calendar.getInstance()

        filtered.groupBy { tx ->
            txCal.timeInMillis = tx.dateEpochMs
            when {
                txCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    txCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) -> "Today"
                txCal.get(Calendar.YEAR) == yestCal.get(Calendar.YEAR) &&
                    txCal.get(Calendar.DAY_OF_YEAR) == yestCal.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
                else -> formatter.format(Date(tx.dateEpochMs))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
        ) {
            // Header with direct CSV Export menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${filtered.size} transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    FilledTonalButton(
                        onClick = { showExportMenu = true },
                        shape = CircleShape,
                        modifier = Modifier.testTag("activity_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Export CSV",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Export CSV")
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Download Filtered CSV (${filtered.size})") },
                            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                            enabled = filtered.isNotEmpty(),
                            onClick = {
                                showExportMenu = false
                                saveFilteredCsvLauncher.launch(
                                    BudgetExporter.suggestedFilteredFileName("finova", "csv")
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Filtered CSV (${filtered.size})") },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            enabled = filtered.isNotEmpty(),
                            onClick = {
                                showExportMenu = false
                                val csv = BudgetExporter.buildCsv(filtered, workspace.profile.currencySymbol)
                                val fileName = BudgetExporter.suggestedFilteredFileName("finova", "csv")
                                BudgetExporter.shareFile(
                                    context = context,
                                    fileName = fileName,
                                    mimeType = "text/csv",
                                    content = csv,
                                    chooserTitle = "Share Activity CSV"
                                ).onFailure {
                                    onExportFeedback("Could not share CSV")
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("All Export & Backup Options") },
                            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                            onClick = {
                                showExportMenu = false
                                onOpenExportSheet()
                            }
                        )
                    }
                }
            }

            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search category, note, or account") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("activity_search_input")
            )

            // Type & Account Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { selectedType = null },
                    label = { Text("All Types") }
                )
                TransactionType.entries.forEach { t ->
                    FilterChip(
                        selected = selectedType == t,
                        onClick = { selectedType = if (selectedType == t) null else t },
                        label = { Text(t.label) }
                    )
                }
                workspace.accounts.forEach { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = {
                            selectedAccountId = if (selectedAccountId == acc.id) null else acc.id
                        },
                        label = { Text(acc.name) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (groupedByDay.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "No matching activity",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Adjust filters or tap + to record a transaction.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedByDay.forEach { (dateLabel, itemsForDay) ->
                        item(key = "header_$dateLabel") {
                            val dayNet = itemsForDay.sumOf { tx ->
                                when (tx.transactionType) {
                                    TransactionType.INCOME -> tx.amountCents
                                    TransactionType.EXPENSE -> -tx.amountCents
                                    TransactionType.TRANSFER -> 0L
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (dayNet != 0L) {
                                    Text(
                                        text = formatCompactCents(dayNet, workspace.profile.currencySymbol),
                                        style = NumericBodyStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        items(itemsForDay, key = { it.id }) { tx ->
                            TransactionRow(
                                transaction = tx,
                                currencySymbol = workspace.profile.currencySymbol,
                                onClick = { onEditTransaction(tx) }
                            )
                        }
                    }
                }
            }
        }
    }
}
