package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CategoryCatalog
import com.example.model.formatCompactCents
import com.example.ui.theme.NumericBodyStyle
import com.example.ui.theme.NumericTitleStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HomeCategorySlice(
    val category: String,
    val amountCents: Long,
    val color: Color,
    val ratio: Float
)

private enum class OverviewChartMode {
    DONUT, BARS
}

@Composable
fun MonthlySpendingChartCard(
    categorySpendMap: Map<String, Long>,
    totalMonthExpense: Long,
    currencySymbol: String,
    onOpenInsights: () -> Unit,
    onQuickLogExpense: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chartMode by remember { mutableStateOf(OverviewChartMode.DONUT) }
    var selectedCategory by remember(categorySpendMap) { mutableStateOf<String?>(null) }

    val currentMonthLabel = remember {
        SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
    }

    val slices = remember(categorySpendMap, totalMonthExpense) {
        if (totalMonthExpense <= 0L || categorySpendMap.isEmpty()) {
            emptyList()
        } else {
            val sorted = categorySpendMap.entries.sortedByDescending { it.value }
            val top = sorted.take(5)
            val otherSum = sorted.drop(5).sumOf { it.value }
            val combined = if (otherSum > 0L) {
                top.map { it.key to it.value } + ("Other" to otherSum)
            } else {
                top.map { it.key to it.value }
            }
            combined.map { (cat, sum) ->
                HomeCategorySlice(
                    category = cat,
                    amountCents = sum,
                    color = CategoryCatalog.find(cat).color,
                    ratio = (sum.toFloat() / totalMonthExpense.toFloat()).coerceIn(0f, 1f)
                )
            }
        }
    }

    val activeSlice = slices.firstOrNull { it.category == selectedCategory }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_spending_overview_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly Spending",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$currentMonthLabel by category",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (slices.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = CircleShape
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
                                ChartModeButton(
                                    icon = Icons.Rounded.DonutLarge,
                                    description = "Donut chart view",
                                    selected = chartMode == OverviewChartMode.DONUT,
                                    onClick = { chartMode = OverviewChartMode.DONUT },
                                    modifier = Modifier.testTag("chart_mode_donut")
                                )
                                ChartModeButton(
                                    icon = Icons.Rounded.BarChart,
                                    description = "Bar chart view",
                                    selected = chartMode == OverviewChartMode.BARS,
                                    onClick = { chartMode = OverviewChartMode.BARS },
                                    modifier = Modifier.testTag("chart_mode_bars")
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = onOpenInsights,
                        modifier = Modifier.testTag("home_chart_insights_button")
                    ) {
                        Text("Insights")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (slices.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PieChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No expenses logged this month",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Your category breakdown appears automatically once you add an expense.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onQuickLogExpense) {
                        Text("Log an Expense")
                    }
                }
            } else {
                AnimatedContent(
                    targetState = chartMode,
                    label = "spendingChartMode"
                ) { mode ->
                    when (mode) {
                        OverviewChartMode.DONUT -> {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val compact = maxWidth < 360.dp
                                val chartBlock: @Composable () -> Unit = {
                                    Box(
                                        modifier = Modifier.size(144.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val sweepProgress by animateFloatAsState(
                                            targetValue = 1f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                                            label = "donutSweep"
                                        )
                                        Canvas(modifier = Modifier.size(136.dp)) {
                                            val defaultStroke = 20.dp.toPx()
                                            val activeStroke = 25.dp.toPx()
                                            var startAngle = -90f
                                            slices.forEach { slice ->
                                                val isSelected = selectedCategory == slice.category
                                                val sweep = (slice.ratio * 360f * sweepProgress).coerceAtLeast(3f)
                                                val alpha = if (selectedCategory == null || isSelected) 1f else 0.35f
                                                drawArc(
                                                    color = slice.color.copy(alpha = alpha),
                                                    startAngle = startAngle,
                                                    sweepAngle = (sweep - 2.5f).coerceAtLeast(1f),
                                                    useCenter = false,
                                                    style = Stroke(
                                                        width = if (isSelected) activeStroke else defaultStroke,
                                                        cap = StrokeCap.Butt
                                                    )
                                                )
                                                startAngle += sweep
                                            }
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        ) {
                                            Text(
                                                text = activeSlice?.category ?: "Total",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatCompactCents(
                                                    activeSlice?.amountCents ?: totalMonthExpense,
                                                    currencySymbol
                                                ),
                                                style = NumericTitleStyle,
                                                maxLines = 1
                                            )
                                            if (activeSlice != null) {
                                                Text(
                                                    text = "${(activeSlice.ratio * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = activeSlice.color,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                val legendBlock: @Composable (Modifier) -> Unit = { mod ->
                                    Column(
                                        modifier = mod,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        slices.forEach { slice ->
                                            val isSelected = selectedCategory == slice.category
                                            Surface(
                                                color = if (isSelected) {
                                                    slice.color.copy(alpha = 0.12f)
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        selectedCategory = if (isSelected) null else slice.category
                                                    }
                                                    .testTag("home_chart_slice_${slice.category.lowercase()}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(9.dp)
                                                                .clip(CircleShape)
                                                                .background(slice.color)
                                                        )
                                                        Spacer(Modifier.width(7.dp))
                                                        Text(
                                                            text = slice.category,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "${(slice.ratio * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (compact) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        chartBlock()
                                        legendBlock(Modifier.fillMaxWidth())
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        chartBlock()
                                        legendBlock(Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        OverviewChartMode.BARS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                slices.forEach { slice ->
                                    val animatedRatio by animateFloatAsState(
                                        targetValue = slice.ratio,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                                        label = "barRatio_${slice.category}"
                                    )
                                    val meta = CategoryCatalog.find(slice.category)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = meta.icon,
                                                    contentDescription = null,
                                                    tint = slice.color,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = slice.category,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "${(slice.ratio * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = formatCompactCents(slice.amountCents, currencySymbol),
                                                style = NumericBodyStyle
                                            )
                                        }
                                        Spacer(Modifier.height(5.dp))
                                        LinearProgressIndicator(
                                            progress = { animatedRatio },
                                            color = slice.color,
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
}

@Composable
private fun ChartModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shape = CircleShape,
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
