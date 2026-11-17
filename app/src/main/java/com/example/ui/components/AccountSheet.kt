package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.AccountColorPalette
import com.example.model.AccountType
import com.example.model.FinancialAccount
import com.example.model.parseDecimalStringToCents
import com.example.model.parseHexColor
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    existingAccount: FinancialAccount? = null,
    currencySymbol: String,
    isSaving: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: AccountType,
        balanceCents: Long,
        colorHex: String,
        makePrimary: Boolean
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existingAccount?.name.orEmpty()) }
    var selectedType by remember { mutableStateOf(existingAccount?.accountType ?: AccountType.CHECKING) }
    var balanceText by remember {
        mutableStateOf(
            if (existingAccount != null) {
                String.format(Locale.US, "%.2f", existingAccount.balanceCents / 100.0)
            } else ""
        )
    }
    var selectedHex by remember { mutableStateOf(existingAccount?.colorHex ?: AccountColorPalette.first()) }
    var makePrimary by remember { mutableStateOf(existingAccount?.primaryAccount ?: false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (existingAccount == null) "Add Account" else "Edit Account",
                    style = MaterialTheme.typography.titleLarge
                )
                if (existingAccount != null && canDelete && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete account",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                label = { Text("Account Name (e.g., Main Bank)") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_name_input")
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Account Type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccountType.entries.forEach { accType ->
                    val selected = selectedType == accType
                    FilterChip(
                        selected = selected,
                        onClick = { selectedType = accType },
                        label = { Text(accType.label) },
                        leadingIcon = {
                            Icon(accType.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.testTag("account_type_${accType.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = balanceText,
                onValueChange = { input ->
                    if (input.matches(Regex("^-?\\d*\\.?\\d{0,2}$"))) {
                        balanceText = input
                    }
                },
                label = { Text(if (existingAccount == null) "Starting Balance ($currencySymbol)" else "Current Balance ($currencySymbol)") },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_balance_input")
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Card Color",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountColorPalette.forEach { hex ->
                    val color = parseHexColor(hex)
                    val selected = selectedHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected color",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Primary Quick-Log Account",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Pre-selected automatically when adding expenses",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = makePrimary,
                    onCheckedChange = { makePrimary = it },
                    modifier = Modifier.testTag("account_primary_switch")
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val balanceCents = if (balanceText.trim() == "-" || balanceText.isBlank()) {
                        0L
                    } else {
                        parseDecimalStringToCents(balanceText)
                    }
                    onSave(name.trim(), selectedType, balanceCents, selectedHex, makePrimary)
                },
                enabled = !isSaving && name.trim().isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_account_button")
            ) {
                Text(
                    text = if (isSaving) "Saving..." else if (existingAccount == null) "Create Account" else "Save Changes",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
