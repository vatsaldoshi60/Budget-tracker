package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.BudgetWorkspace
import com.example.model.CategoryBudget
import com.example.model.CategoryCatalog
import com.example.model.TransactionType
import com.example.model.formatCompactCents
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.IncomeTeal
import com.example.ui.theme.NumericBodyStyle
import com.example.ui.theme.NumericTitleStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class CategorySlice(
    val category: String,
    val amountCents: Long,
    val color: Color,
    val ratio: Float
)

private data class MonthlyTrendPoint(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long
)

@Composable
fun InsightsScreen(
    workspace: BudgetWorkspace,
    onAddCategoryBudget: () -> Unit,
    onEditCategoryBudget: (CategoryBudget) -> Unit
) {
    val symbol = workspace.profile.currencySymbol
    var monthOffset by remember { mutableIntStateOf(0) }

    val selectedCal = remember(monthOffset) {
        Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
    }
    val monthTitle = remember(selectedCal) {
        SimpleDateFormat("MMMM yyyy", Locale.US).format(selectedCal.time)
    }

    val monthTx = remember(workspace.transactions, monthOffset) {
        val targetYear = selectedCal.get(Calendar.YEAR)
        val targetMonth = selectedCal.get(Calendar.MONTH)
        val itemCal = Calendar.getInstance()
        workspace.transactions.filter { tx ->
            itemCal.timeInMillis = tx.dateEpochMs
            itemCal.get(Calendar.YEAR) == targetYear && itemCal.get(Calendar.MONTH) == targetMonth
        }
    }

    val totalMonthExpense = remember(monthTx) {
        monthTx.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amountCents }
    }
    val totalMonthIncome = remember(monthTx) {
        monthTx.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amountCents }
    }

    val slices = remember(monthTx, totalMonthExpense) {
        if (totalMonthExpense <= 0L) emptyList()
        else {
            monthTx.filter { it.transactionType == TransactionType.EXPENSE }
                .groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amountCents } }
                .entries
                .sortedByDescending { it.value }
                .map { (cat, sum) ->
                    CategorySlice(
                        category = cat,
                        amountCents = sum,
                        color = CategoryCatalog.find(cat).color,
                        ratio = sum.toFloat() / totalMonthExpense.toFloat()
                    )
                }
        }
    }

    val trendPoints = remember(workspace.transactions) {
        val labelFormat = SimpleDateFormat("MMM", Locale.US)
        val itemCal = Calendar.getInstance()
        (5 downTo 0).map { back ->
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -back) }
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            var inc = 0L
            var exp = 0L
            workspace.transactions.forEach { tx ->
                itemCal.timeInMillis = tx.dateEpochMs
                if (itemCal.get(Calendar.YEAR) == y && itemCal.get(Calendar.MONTH) == m) {
                    when (tx.transactionType) {
                        TransactionType.INCOME -> inc += tx.amountCents
                        TransactionType.EXPENSE -> exp += tx.amountCents
                        TransactionType.TRANSFER -> Unit
                    }
                }
            }
            MonthlyTrendPoint(labelFormat.format(cal.time), inc, exp)
        }
    }

    val categorySpendMap = remember(slices) {
        slices.associate { it.category to it.amountCents }
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
            // Header + Month Stepper
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Insights",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                        tonalElevation = 1.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { monthOffset-- }) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                            }
                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.labelLarge
                            )
                            IconButton(
                                onClick = { if (monthOffset < 0) monthOffset++ },
                                enabled = monthOffset < 0
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                            }
                        }
                    }
                }
            }

            // Cash Flow Summary Pills
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatCompactCents(totalMonthIncome, symbol),
                                style = NumericTitleStyle,
                                color = IncomeTeal
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Expenses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatCompactCents(totalMonthExpense, symbol),
                                style = NumericTitleStyle,
                                color = CoralExpense
                            )
                        }
                    }
                }
            }

            // Spending Donut Chart Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Spending Breakdown",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(14.dp))
                        if (slices.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PieChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No expenses in $monthTitle",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(164.dp)) {
                                    val strokeWidth = 26.dp.toPx()
                                    var startAngle = -90f
                                    slices.forEach { slice ->
                                        val sweep = (slice.ratio * 360f).coerceAtLeast(3f)
                                        drawArc(
                                            color = slice.color,
                                            startAngle = startAngle,
                                            sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                                            useCenter = false,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                        )
                                        startAngle += sweep
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Spent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatCompactCents(totalMonthExpense, symbol),
                                        style = NumericTitleStyle
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                slices.forEach { slice ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(slice.color)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "${slice.category} (${(slice.ratio * 100).toInt()}%)",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Text(
                                            text = formatCompactCents(slice.amountCents, symbol),
                                            style = NumericBodyStyle
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6-Month Cash Flow Bar Chart
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("6-Month Trend", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LegendDot(IncomeTeal, "In")
                                LegendDot(CoralExpense, "Out")
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        val maxVal = trendPoints.maxOf { maxOf(it.incomeCents, it.expenseCents) }.coerceAtLeast(1L)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val groupWidth = size.width / trendPoints.size
                            val barWidth = (groupWidth * 0.24f).coerceAtMost(22.dp.toPx())
                            val gap = 4.dp.toPx()
                            trendPoints.forEachIndexed { idx, pt ->
                                val centerX = groupWidth * idx + groupWidth / 2f
                                val incH = (pt.incomeCents.toFloat() / maxVal.toFloat()) * (size.height - 8.dp.toPx())
                                val expH = (pt.expenseCents.toFloat() / maxVal.toFloat()) * (size.height - 8.dp.toPx())
                                drawRoundRect(
                                    color = IncomeTeal,
                                    topLeft = Offset(centerX - barWidth - gap / 2f, size.height - incH.coerceAtLeast(4f)),
                                    size = Size(barWidth, incH.coerceAtLeast(4f)),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                                drawRoundRect(
                                    color = CoralExpense,
                                    topLeft = Offset(centerX + gap / 2f, size.height - expH.coerceAtLeast(4f)),
                                    size = Size(barWidth, expH.coerceAtLeast(4f)),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            trendPoints.forEach { pt ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = pt.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Category Budgets Manager
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category Budgets",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = onAddCategoryBudget,
                        modifier = Modifier.testTag("add_category_budget_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Cap")
                    }
                }

                if (workspace.categoryBudgets.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Set spending limits for Food, Coffee, or Shopping to get early warnings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onAddCategoryBudget) {
                                Text("Set First Budget Cap")
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        workspace.categoryBudgets.forEach { budget ->
                            val spent = categorySpendMap[budget.category] ?: 0L
                            val ratio = spent.toFloat() / budget.limitCents.toFloat()
                            val meta = CategoryCatalog.find(budget.category)
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onEditCategoryBudget(budget) }
                                    .testTag("category_budget_card_${budget.id}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(meta.icon, contentDescription = null, tint = meta.color, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(budget.category, style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text(
                                            text = "${formatCompactCents(spent, symbol)} / ${formatCompactCents(budget.limitCents, symbol)}",
                                            style = NumericBodyStyle,
                                            color = if (ratio > 1f) CoralExpense else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { ratio.coerceIn(0f, 1f) },
                                        color = when {
                                            ratio > 1f -> CoralExpense
                                            ratio >= 0.8f -> AmberAccent
                                            else -> meta.color
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        strokeCap = StrokeCap.Round,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
