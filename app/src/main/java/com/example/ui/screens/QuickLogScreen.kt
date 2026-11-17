package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CategoryCatalog
import com.example.model.FinancialAccount
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.model.formatCompactCents
import com.example.model.parseDecimalStringToCents
import com.example.ui.theme.CobaltTransfer
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.IncomeTeal
import com.example.ui.theme.NumericHeroStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QuickLogScreen(
    accounts: List<FinancialAccount>,
    currencySymbol: String,
    existingTransaction: LedgerTransaction? = null,
    initialType: TransactionType = TransactionType.EXPENSE,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        type: TransactionType,
        amountCents: Long,
        category: String,
        fromAccount: FinancialAccount,
        toAccount: FinancialAccount?,
        note: String,
        dateEpochMs: Long
    ) -> Unit,
    onDelete: ((LedgerTransaction) -> Unit)? = null
) {
    BackHandler(onBack = onDismiss)

    val haptics = LocalHapticFeedback.current
    val resolvedInitialType = existingTransaction?.transactionType ?: initialType
    var txType by remember { mutableStateOf(resolvedInitialType) }

    val initialAmountStr = remember(existingTransaction) {
        if (existingTransaction != null && existingTransaction.amountCents > 0L) {
            val whole = existingTransaction.amountCents / 100
            val cents = existingTransaction.amountCents % 100
            if (cents == 0L) whole.toString() else String.format(Locale.US, "%d.%02d", whole, cents)
        } else ""
    }
    var amountInput by remember { mutableStateOf(initialAmountStr) }
    var pulseKey by remember { mutableStateOf(false) }

    val amountScale by animateFloatAsState(
        targetValue = if (pulseKey) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "amountPulse"
    )
    LaunchedEffect(amountInput) {
        if (amountInput.isNotEmpty()) {
            pulseKey = true
            delay(70)
            pulseKey = false
        }
    }

    val categoriesForType = remember(txType) {
        when (txType) {
            TransactionType.EXPENSE -> CategoryCatalog.expenseCategories
            TransactionType.INCOME -> CategoryCatalog.incomeCategories
            TransactionType.TRANSFER -> listOf(CategoryCatalog.transferMeta)
        }
    }

    var selectedCategory by remember(existingTransaction, resolvedInitialType) {
        mutableStateOf(
            existingTransaction?.category
                ?: when (resolvedInitialType) {
                    TransactionType.EXPENSE -> CategoryCatalog.expenseCategories.first().name
                    TransactionType.INCOME -> CategoryCatalog.incomeCategories.first().name
                    TransactionType.TRANSFER -> "Transfer"
                }
        )
    }

    val defaultFrom = remember(accounts, existingTransaction) {
        accounts.firstOrNull { it.id == existingTransaction?.accountId }
            ?: accounts.firstOrNull { it.primaryAccount }
            ?: accounts.firstOrNull()
    }
    var fromAccount by remember(accounts) { mutableStateOf(defaultFrom) }

    val defaultTo = remember(accounts, existingTransaction, fromAccount) {
        accounts.firstOrNull { it.id == existingTransaction?.toAccountId }
            ?: accounts.firstOrNull { it.id != fromAccount?.id }
    }
    var toAccount by remember(accounts) { mutableStateOf(defaultTo) }

    var showFromAccountMenu by remember { mutableStateOf(false) }
    var showToAccountMenu by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(existingTransaction?.note.orEmpty()) }
    var showNoteField by remember { mutableStateOf(!existingTransaction?.note.isNullOrBlank()) }
    var dateEpochMs by remember { mutableLongStateOf(existingTransaction?.dateEpochMs ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amountCents = parseDecimalStringToCents(amountInput)
    val canSave = !isSaving &&
        amountCents > 0L &&
        fromAccount != null &&
        (txType != TransactionType.TRANSFER || (toAccount != null && toAccount?.id != fromAccount?.id))

    val accentColor = when (txType) {
        TransactionType.EXPENSE -> CoralExpense
        TransactionType.INCOME -> IncomeTeal
        TransactionType.TRANSFER -> CobaltTransfer
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.US) }
    val dateLabel = remember(dateEpochMs) {
        val now = Calendar.getInstance()
        val chosen = Calendar.getInstance().apply { timeInMillis = dateEpochMs }
        val isToday = now.get(Calendar.YEAR) == chosen.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == chosen.get(Calendar.DAY_OF_YEAR)
        if (isToday) "Today" else dateFormatter.format(Date(dateEpochMs))
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Minimalist Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("quick_log_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close quick log"
                        )
                    }

                    // Compact Segmented Type Pill
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = CircleShape
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            TransactionType.entries.forEach { typeOption ->
                                val selected = txType == typeOption
                                val pillColor = when {
                                    !selected -> Color.Transparent
                                    typeOption == TransactionType.EXPENSE -> CoralExpense
                                    typeOption == TransactionType.INCOME -> IncomeTeal
                                    else -> CobaltTransfer
                                }
                                Surface(
                                    color = pillColor,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            txType = typeOption
                                            selectedCategory = when (typeOption) {
                                                TransactionType.EXPENSE -> CategoryCatalog.expenseCategories.first().name
                                                TransactionType.INCOME -> CategoryCatalog.incomeCategories.first().name
                                                TransactionType.TRANSFER -> "Transfer"
                                            }
                                            if (typeOption == TransactionType.TRANSFER &&
                                                (toAccount == null || toAccount?.id == fromAccount?.id)
                                            ) {
                                                toAccount = accounts.firstOrNull { it.id != fromAccount?.id }
                                            }
                                        }
                                        .testTag("tx_type_${typeOption.name.lowercase()}")
                                ) {
                                    Text(
                                        text = typeOption.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (existingTransaction != null && onDelete != null) {
                        IconButton(
                            onClick = { onDelete(existingTransaction) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_transaction_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete transaction",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }

                // 2. Hero Minimalist Amount + Compact Context Pills
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val amountFontSize = when {
                        amountInput.length > 8 -> 34.sp
                        amountInput.length > 6 -> 42.sp
                        else -> 52.sp
                    }
                    Text(
                        text = "$currencySymbol${if (amountInput.isEmpty()) "0" else amountInput}",
                        style = NumericHeroStyle.copy(fontSize = amountFontSize, lineHeight = amountFontSize * 1.15f),
                        color = if (amountInput.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        } else {
                            accentColor
                        },
                        maxLines = 1,
                        modifier = Modifier.scale(amountScale)
                    )

                    Spacer(Modifier.height(14.dp))

                    // Sleek Account + Date + Note Context Pill Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Source Account Dropdown Pill
                        Box {
                            CompactContextPill(
                                icon = Icons.Rounded.AccountBalanceWallet,
                                label = fromAccount?.name ?: "No Account",
                                trailingArrow = true,
                                highlighted = false,
                                onClick = { if (accounts.isNotEmpty()) showFromAccountMenu = true },
                                modifier = Modifier.testTag("quick_log_from_account_pill")
                            )
                            DropdownMenu(
                                expanded = showFromAccountMenu,
                                onDismissRequest = { showFromAccountMenu = false }
                            ) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${acc.name} (${formatCompactCents(acc.balanceCents, currencySymbol)})")
                                        },
                                        leadingIcon = if (fromAccount?.id == acc.id) {
                                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                                        } else null,
                                        onClick = {
                                            fromAccount = acc
                                            if (toAccount?.id == acc.id) {
                                                toAccount = accounts.firstOrNull { it.id != acc.id }
                                            }
                                            showFromAccountMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Destination Account Pill (Transfers only)
                        if (txType == TransactionType.TRANSFER) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "to",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            val destCandidates = accounts.filter { it.id != fromAccount?.id }
                            Box {
                                CompactContextPill(
                                    icon = Icons.Rounded.AccountBalanceWallet,
                                    label = toAccount?.name ?: "Select Dest",
                                    trailingArrow = true,
                                    highlighted = toAccount != null,
                                    onClick = { if (destCandidates.isNotEmpty()) showToAccountMenu = true },
                                    modifier = Modifier.testTag("quick_log_to_account_pill")
                                )
                                DropdownMenu(
                                    expanded = showToAccountMenu,
                                    onDismissRequest = { showToAccountMenu = false }
                                ) {
                                    destCandidates.forEach { acc ->
                                        DropdownMenuItem(
                                            text = {
                                                Text("${acc.name} (${formatCompactCents(acc.balanceCents, currencySymbol)})")
                                            },
                                            leadingIcon = if (toAccount?.id == acc.id) {
                                                { Icon(Icons.Rounded.Check, contentDescription = null) }
                                            } else null,
                                            onClick = {
                                                toAccount = acc
                                                showToAccountMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Date Pill
                        CompactContextPill(
                            icon = Icons.Rounded.CalendarToday,
                            label = dateLabel,
                            trailingArrow = false,
                            highlighted = dateLabel != "Today",
                            onClick = { showDatePicker = true },
                            modifier = Modifier.testTag("quick_log_date_pill")
                        )

                        // Note Pill
                        CompactContextPill(
                            icon = Icons.Rounded.EditNote,
                            label = if (note.isBlank()) "Note" else note.take(14),
                            trailingArrow = false,
                            highlighted = showNoteField || note.isNotBlank(),
                            onClick = { showNoteField = !showNoteField },
                            modifier = Modifier.testTag("quick_log_note_pill")
                        )
                    }

                    AnimatedVisibility(
                        visible = showNoteField,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { if (it.length <= 160) note = it },
                            placeholder = { Text("Add a short note...") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .testTag("tx_note_input")
                        )
                    }
                }

                // 3. One-Tap Category Selector + Bottom Thumb-Zone Keypad & Save
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Minimalist Horizontal Category Selector
                    if (txType != TransactionType.TRANSFER) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoriesForType.forEach { meta ->
                                val selected = selectedCategory.equals(meta.name, ignoreCase = true)
                                Surface(
                                    color = if (selected) meta.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(16.dp),
                                    tonalElevation = if (selected) 0.dp else 1.dp,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .border(
                                            width = if (selected) 1.5.dp else 0.dp,
                                            color = if (selected) meta.color else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedCategory = meta.name
                                        }
                                        .testTag("category_chip_${meta.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = meta.icon,
                                            contentDescription = null,
                                            tint = meta.color,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            text = meta.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick Increment Chips (+5, +10, +25, +50, Clear)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(500L to "+5", 1000L to "+10", 2500L to "+25", 5000L to "+50").forEach { (delta, numLabel) ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val updated = (amountCents + delta).coerceAtMost(999_999_99L)
                                        val w = updated / 100
                                        val c = updated % 100
                                        amountInput = if (c == 0L) w.toString() else String.format(Locale.US, "%d.%02d", w, c)
                                    }
                                    .testTag("quick_chip_$delta")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = numLabel.replace("+", "+$currencySymbol"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = CircleShape,
                            modifier = Modifier
                                .height(38.dp)
                                .width(52.dp)
                                .clip(CircleShape)
                                .clickable(enabled = amountInput.isNotEmpty()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    amountInput = ""
                                }
                                .testTag("quick_clear_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "C",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Minimalist Tactile 4x3 Numeric Keypad
                    val keypadRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf(".", "0", "DEL")
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        keypadRows.forEach { rowKeys ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowKeys.forEach { key ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(18.dp),
                                        tonalElevation = 1.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    amountInput = applyMinimalKeypadPress(amountInput, key)
                                                },
                                                onLongClick = {
                                                    if (key == "DEL") {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        amountInput = ""
                                                    }
                                                }
                                            )
                                            .testTag("keypad_$key")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (key == "DEL") {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                                    contentDescription = "Backspace",
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            } else {
                                                Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Primary Save Action Button
                    Button(
                        onClick = {
                            val from = fromAccount ?: return@Button
                            onSave(
                                txType,
                                amountCents,
                                selectedCategory,
                                from,
                                if (txType == TransactionType.TRANSFER) toAccount else null,
                                note,
                                dateEpochMs
                            )
                        },
                        enabled = canSave,
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("save_transaction_button")
                    ) {
                        val buttonText = when {
                            isSaving -> "Saving..."
                            amountCents <= 0L -> "Enter amount"
                            existingTransaction != null -> "Update ${formatCompactCents(amountCents, currencySymbol)}"
                            else -> "Add ${txType.label} · ${formatCompactCents(amountCents, currencySymbol)}"
                        }
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateEpochMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { dateEpochMs = it }
                        showDatePicker = false
                    }
                ) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun CompactContextPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    trailingArrow: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = CircleShape,
        tonalElevation = 1.dp,
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingArrow) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun applyMinimalKeypadPress(current: String, key: String): String {
    return when (key) {
        "DEL" -> if (current.isNotEmpty()) current.dropLast(1) else ""
        "." -> when {
            current.contains('.') -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        else -> {
            val dotIndex = current.indexOf('.')
            when {
                dotIndex >= 0 && current.length - dotIndex > 2 -> current
                current == "0" -> key
                current.length >= 9 -> current
                else -> current + key
            }
        }
    }
}
