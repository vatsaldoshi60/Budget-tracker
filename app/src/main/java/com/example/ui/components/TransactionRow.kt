package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CategoryCatalog
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.model.formatCents
import com.example.ui.theme.CobaltTransfer
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.IncomeTeal
import com.example.ui.theme.NumericBodyStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionRow(
    transaction: LedgerTransaction,
    currencySymbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val meta = remember(transaction.category, transaction.type) {
        if (transaction.transactionType == TransactionType.TRANSFER) {
            CategoryCatalog.transferMeta
        } else {
            CategoryCatalog.find(transaction.category)
        }
    }
    val timeFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }

    val amountText = when (transaction.transactionType) {
        TransactionType.EXPENSE -> "-${formatCents(transaction.amountCents, currencySymbol)}"
        TransactionType.INCOME -> "+${formatCents(transaction.amountCents, currencySymbol)}"
        TransactionType.TRANSFER -> formatCents(transaction.amountCents, currencySymbol)
    }
    val amountColor = when (transaction.transactionType) {
        TransactionType.EXPENSE -> CoralExpense
        TransactionType.INCOME -> IncomeTeal
        TransactionType.TRANSFER -> CobaltTransfer
    }

    val subtitle = buildString {
        if (transaction.transactionType == TransactionType.TRANSFER && !transaction.toAccountName.isNullOrBlank()) {
            append("${transaction.accountName} -> ${transaction.toAccountName}")
        } else {
            append(transaction.accountName)
        }
        if (transaction.note.isNotBlank()) {
            append(" • ${transaction.note}")
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("transaction_item_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(meta.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = meta.icon,
                    contentDescription = transaction.category,
                    tint = meta.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.category,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeFormatter.format(Date(transaction.dateEpochMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = amountText,
                style = NumericBodyStyle,
                color = amountColor,
                maxLines = 1
            )
        }
    }
}
