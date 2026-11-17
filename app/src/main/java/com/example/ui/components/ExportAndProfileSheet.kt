package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.export.BudgetExporter
import com.example.export.ExportPeriod
import com.example.model.BudgetWorkspace
import com.example.model.SupportedCurrencies
import com.example.model.parseDecimalStringToCents
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportAndProfileSheet(
    workspace: BudgetWorkspace,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onUpdatePreferences: (currencySymbol: String, monthlyBudgetCents: Long) -> Unit,
    onExportFeedback: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedPeriod by remember { mutableStateOf(ExportPeriod.THIS_MONTH) }
    var selectedCurrency by remember { mutableStateOf(workspace.profile.currencySymbol) }
    var budgetText by remember {
        mutableStateOf(
            String.format(Locale.US, "%.0f", workspace.profile.monthlyBudgetCents / 100.0)
        )
    }

    val filteredTx = remember(workspace.transactions, selectedPeriod) {
        BudgetExporter.filterByPeriod(workspace.transactions, selectedPeriod)
    }

    val csvSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val csv = BudgetExporter.buildCsv(filteredTx, workspace.profile.currencySymbol)
            BudgetExporter.writeToUri(context, uri, csv).fold(
                onSuccess = { onExportFeedback("Saved ${filteredTx.size} transactions as CSV") },
                onFailure = { onExportFeedback("Export failed: ${it.localizedMessage}") }
            )
        }
    }

    val jsonSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = BudgetExporter.buildJson(workspace, filteredTx, selectedPeriod)
            BudgetExporter.writeToUri(context, uri, json).fold(
                onSuccess = { onExportFeedback("Saved full JSON backup") },
                onFailure = { onExportFeedback("JSON export failed: ${it.localizedMessage}") }
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Profile Header
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val initial = workspace.profile.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "F"
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = workspace.profile.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (workspace.profile.email.isNotBlank()) {
                            Text(
                                text = workspace.profile.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = CircleShape,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign out",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sign out")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Export Section
            Text(
                text = "Export Data (${filteredTx.size} transactions)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ExportPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label) },
                        modifier = Modifier.testTag("export_period_${period.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExportActionTile(
                    icon = Icons.Rounded.Download,
                    title = "Save CSV",
                    subtitle = "Excel / Sheets",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_save_csv_button"),
                    onClick = {
                        if (filteredTx.isEmpty()) {
                            onExportFeedback("No transactions in ${selectedPeriod.label}")
                        } else {
                            csvSaveLauncher.launch(
                                BudgetExporter.suggestedFileName("finova", selectedPeriod, "csv")
                            )
                        }
                    }
                )
                ExportActionTile(
                    icon = Icons.Rounded.Share,
                    title = "Share CSV",
                    subtitle = "WhatsApp / Mail",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_share_csv_button"),
                    onClick = {
                        if (filteredTx.isEmpty()) {
                            onExportFeedback("No transactions in ${selectedPeriod.label}")
                        } else {
                            val csv = BudgetExporter.buildCsv(filteredTx, workspace.profile.currencySymbol)
                            val name = BudgetExporter.suggestedFileName("finova", selectedPeriod, "csv")
                            BudgetExporter.shareFile(context, name, "text/csv", csv, "Share Budget CSV")
                                .onFailure { onExportFeedback("Could not share CSV") }
                        }
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExportActionTile(
                    icon = Icons.Rounded.Code,
                    title = "Save JSON",
                    subtitle = "Full backup",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_save_json_button"),
                    onClick = {
                        jsonSaveLauncher.launch(
                            BudgetExporter.suggestedFileName("finova_backup", selectedPeriod, "json")
                        )
                    }
                )
                ExportActionTile(
                    icon = Icons.Rounded.Description,
                    title = "Share Summary",
                    subtitle = "Readable report",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_share_summary_button"),
                    onClick = {
                        val report = BudgetExporter.buildTextSummary(workspace, filteredTx, selectedPeriod)
                        BudgetExporter.sharePlainText(context, report, "Finova ${selectedPeriod.label} Summary")
                            .onFailure { onExportFeedback("Could not share summary") }
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Preferences Section
            Text(
                text = "Budget & Currency",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                SupportedCurrencies.forEach { symbol ->
                    FilterChip(
                        selected = selectedCurrency == symbol,
                        onClick = { selectedCurrency = symbol },
                        label = { Text(symbol, style = MaterialTheme.typography.titleMedium) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = budgetText,
                onValueChange = { input ->
                    if (input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        budgetText = input
                    }
                },
                label = { Text("Monthly Spending Target ($selectedCurrency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_budget_input")
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    val budgetCents = parseDecimalStringToCents(budgetText)
                    onUpdatePreferences(selectedCurrency, budgetCents)
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_preferences_button")
            ) {
                Text(if (isSaving) "Saving..." else "Save Preferences")
            }
        }
    }
}

@Composable
private fun ExportActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
