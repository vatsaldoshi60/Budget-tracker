package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.CategoryBudget
import com.example.model.CategoryCatalog
import com.example.model.parseDecimalStringToCents
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBudgetSheet(
    existingBudget: CategoryBudget? = null,
    currencySymbol: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (category: String, limitCents: Long) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember {
        mutableStateOf(existingBudget?.category ?: CategoryCatalog.expenseCategories.first().name)
    }
    var limitText by remember {
        mutableStateOf(
            if (existingBudget != null) {
                String.format(Locale.US, "%.0f", existingBudget.limitCents / 100.0)
            } else "300"
        )
    }

    val limitCents = parseDecimalStringToCents(limitText)

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
                    text = if (existingBudget == null) "Set Category Budget" else "Edit ${existingBudget.category} Cap",
                    style = MaterialTheme.typography.titleLarge
                )
                if (existingBudget != null && onDelete != null) {
                    IconButton(
                        onClick = { onDelete(existingBudget.id) },
                        modifier = Modifier.testTag("delete_category_budget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete category budget",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (existingBudget == null) {
                Text(
                    text = "Category",
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
                    CategoryCatalog.expenseCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.name,
                            onClick = { selectedCategory = cat.name },
                            label = { Text(cat.name) },
                            leadingIcon = {
                                Icon(cat.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = cat.color)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = limitText,
                onValueChange = { input ->
                    if (input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        limitText = input
                    }
                },
                label = { Text("Monthly Limit ($currencySymbol)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_budget_limit_input")
            )

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf("100", "250", "500", "1000").forEach { preset ->
                    FilterChip(
                        selected = limitText == preset,
                        onClick = { limitText = preset },
                        label = { Text("$currencySymbol$preset") }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = { onSave(selectedCategory, limitCents) },
                enabled = !isSaving && limitCents > 0L,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_category_budget_button")
            ) {
                Text(if (isSaving) "Saving..." else "Save Budget Cap")
            }
        }
    }
}
