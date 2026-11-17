package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BudgetWorkspace
import com.example.model.CategoryCatalog
import com.example.model.FinancialAccount
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.model.formatCents
import com.example.model.formatCompactCents
import com.example.model.parseHexColor
import com.example.ui.components.MonthlySpendingChartCard
import com.example.ui.components.TransactionRow
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BrandDeep
import com.example.ui.theme.BrandGlow
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.CobaltTransfer
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.IncomeTeal
import com.example.ui.theme.NumericBodyStyle
import com.example.ui.theme.NumericHeroStyle
import com.example.ui.theme.NumericTitleStyle
import java.util.Calendar

@Composable
fun HomeScreen(
    workspace: BudgetWorkspace,
    onOpenQuickLog: (TransactionType) -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
    onOpenAddAccount: () -> Unit,
    onOpenAccountsTab: () -> Unit,
    onOpenActivityTab: () -> Unit,
    onOpenInsightsTab: () -> Unit = {},
    onOpenExportProfile: () -> Unit
) {
    val symbol = workspace.profile.currencySymbol

    val (monthIncome, monthExpense) = remember(workspace.transactions) {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val itemCal = Calendar.getInstance()
        var inc = 0L
        var exp = 0L
        workspace.transactions.forEach { tx ->
            itemCal.timeInMillis = tx.dateEpochMs
            if (itemCal.get(Calendar.YEAR) == year && itemCal.get(Calendar.MONTH) == month) {
                when (tx.transactionType) {
                    TransactionType.INCOME -> inc += tx.amountCents
                    TransactionType.EXPENSE -> exp += tx.amountCents
                    TransactionType.TRANSFER -> Unit
                }
            }
        }
        inc to exp
    }

    val netWorth = remember(workspace.accounts) {
        workspace.accounts.sumOf { it.balanceCents }
    }

    val budgetLimit = workspace.profile.monthlyBudgetCents
    val rawProgress = if (budgetLimit > 0L) (monthExpense.toFloat() / budgetLimit.toFloat()) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "budgetProgress"
    )

    val daysRemaining = remember {
        val cal = Calendar.getInstance()
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = cal.get(Calendar.DAY_OF_MONTH)
        (maxDay - today + 1).coerceAtLeast(1)
    }
    val remainingBudget = (budgetLimit - monthExpense).coerceAtLeast(0L)
    val safePerDay = remainingBudget / daysRemaining

    val categorySpendMap = remember(workspace.transactions) {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val itemCal = Calendar.getInstance()
        workspace.transactions
            .filter { tx ->
                itemCal.timeInMillis = tx.dateEpochMs
                tx.transactionType == TransactionType.EXPENSE &&
                    itemCal.get(Calendar.YEAR) == year &&
                    itemCal.get(Calendar.MONTH) == month
            }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
    }

    val recentTransactions = remember(workspace.transactions) {
        workspace.transactions.take(6)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Greeting & Profile / Export Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hello, ${workspace.profile.displayName.substringBefore(" ")}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Here's your live ledger",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            tonalElevation = 2.dp
                        ) {
                            IconButton(
                                onClick = onOpenExportProfile,
                                modifier = Modifier.testTag("home_export_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "Export and settings",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        val initial = workspace.profile.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "F"
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenExportProfile)
                                .testTag("home_profile_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = initial,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Hero Net Balance & Monthly Budget Ring/Progress Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(BrandDeep, BrandPrimary)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Total Balance",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatCents(netWorth, symbol),
                                style = NumericHeroStyle,
                                color = Color.White
                            )

                            Spacer(Modifier.height(16.dp))

                            // Income vs Expense mini pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HeroStatPill(
                                    icon = Icons.Rounded.ArrowDownward,
                                    label = "In this month",
                                    value = formatCompactCents(monthIncome, symbol),
                                    tint = BrandGlow,
                                    modifier = Modifier.weight(1f)
                                )
                                HeroStatPill(
                                    icon = Icons.Rounded.ArrowUpward,
                                    label = "Out this month",
                                    value = formatCompactCents(monthExpense, symbol),
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (budgetLimit > 0L) {
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Monthly Budget (${(rawProgress * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = "${formatCompactCents(monthExpense, symbol)} / ${formatCompactCents(budgetLimit, symbol)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    color = when {
                                        rawProgress > 1f -> Color(0xFFF87171)
                                        rawProgress >= 0.8f -> Color(0xFFFBBF24)
                                        else -> BrandGlow
                                    },
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (monthExpense > budgetLimit) {
                                        "Over monthly target by ${formatCompactCents(monthExpense - budgetLimit, symbol)}"
                                    } else {
                                        "Safe pace: ${formatCompactCents(safePerDay, symbol)}/day for $daysRemaining days left"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                }
            }

            // Minimal-input Quick Action Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionButton(
                        icon = Icons.Rounded.ArrowUpward,
                        label = "Expense",
                        accent = CoralExpense,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_expense"),
                        onClick = { onOpenQuickLog(TransactionType.EXPENSE) }
                    )
                    QuickActionButton(
                        icon = Icons.Rounded.ArrowDownward,
                        label = "Income",
                        accent = IncomeTeal,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_income"),
                        onClick = { onOpenQuickLog(TransactionType.INCOME) }
                    )
                    QuickActionButton(
                        icon = Icons.Rounded.SwapHoriz,
                        label = "Transfer",
                        accent = CobaltTransfer,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_transfer"),
                        onClick = { onOpenQuickLog(TransactionType.TRANSFER) }
                    )
                }
            }

            // Accounts Strip with inline "+ Add Account"
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Accounts",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onOpenAccountsTab) {
                        Text("Manage")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    workspace.accounts.forEach { account ->
                        MiniAccountCard(
                            account = account,
                            currencySymbol = symbol,
                            onClick = onOpenAccountsTab
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .width(128.dp)
                            .height(92.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onOpenAddAccount)
                            .testTag("home_add_account_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add account",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Add Account",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Monthly Spending Overview Chart by Category
            item {
                MonthlySpendingChartCard(
                    categorySpendMap = categorySpendMap,
                    totalMonthExpense = monthExpense,
                    currencySymbol = symbol,
                    onOpenInsights = onOpenInsightsTab,
                    onQuickLogExpense = { onOpenQuickLog(TransactionType.EXPENSE) }
                )
            }

            // Category Budgets Preview (if configured)
            if (workspace.categoryBudgets.isNotEmpty()) {
                item {
                    Text(
                        text = "Category Caps",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        workspace.categoryBudgets.take(3).forEach { budget ->
                            val spent = categorySpendMap[budget.category] ?: 0L
                            val ratio = (spent.toFloat() / budget.limitCents.toFloat()).coerceAtLeast(0f)
                            val catMeta = CategoryCatalog.find(budget.category)
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = budget.category,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = "${formatCompactCents(spent, symbol)} / ${formatCompactCents(budget.limitCents, symbol)}",
                                            style = NumericBodyStyle,
                                            color = if (ratio > 1f) CoralExpense else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { ratio.coerceIn(0f, 1f) },
                                        color = when {
                                            ratio > 1f -> CoralExpense
                                            ratio >= 0.8f -> AmberAccent
                                            else -> catMeta.color
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        strokeCap = StrokeCap.Round,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Activity Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (workspace.transactions.isNotEmpty()) {
                        TextButton(onClick = onOpenActivityTab) {
                            Text("See all")
                        }
                    }
                }
            }

            if (recentTransactions.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "No transactions yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tap + below to log your first expense or income in 2 taps.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentTransactions, key = { it.id }) { tx ->
                    TransactionRow(
                        transaction = tx,
                        currencySymbol = symbol,
                        onClick = { onEditTransaction(tx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1
                )
                Text(
                    text = value,
                    style = NumericBodyStyle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = modifier
            .height(54.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MiniAccountCard(
    account: FinancialAccount,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val accent = parseHexColor(account.colorHex)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier
            .width(154.dp)
            .height(92.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("home_account_card_${account.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = account.accountType.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatCompactCents(account.balanceCents, currencySymbol),
                style = NumericTitleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
