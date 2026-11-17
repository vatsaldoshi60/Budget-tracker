package com.example.model

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Finova User",
    val email: String = "",
    val currencySymbol: String = "$",
    val monthlyBudgetCents: Long = 250_000L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class AccountType(val label: String, val icon: ImageVector) {
    CHECKING("Checking", Icons.Rounded.AccountBalance),
    CASH("Cash Wallet", Icons.Rounded.AccountBalanceWallet),
    SAVINGS("Savings", Icons.Rounded.Savings),
    CARD("Credit Card", Icons.Rounded.CreditCard),
    INVESTMENT("Investment", Icons.Rounded.TrendingUp);

    companion object {
        fun fromRaw(raw: String): AccountType =
            entries.firstOrNull { it.name == raw } ?: CHECKING
    }
}

@Keep
@IgnoreExtraProperties
data class FinancialAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val type: String = AccountType.CHECKING.name,
    val balanceCents: Long = 0L,
    val colorHex: String = "#3B28CC",
    val primaryAccount: Boolean = false,
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val accountType: AccountType
        get() = AccountType.fromRaw(type)
}

enum class TransactionType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer");

    companion object {
        fun fromRaw(raw: String): TransactionType =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

@Keep
@IgnoreExtraProperties
data class LedgerTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val amountCents: Long = 0L,
    val category: String = "Food",
    val accountId: String = "",
    val accountName: String = "",
    val toAccountId: String? = null,
    val toAccountName: String? = null,
    val note: String = "",
    val dateEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val transactionType: TransactionType
        get() = TransactionType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class CategoryBudget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "",
    val limitCents: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class BudgetWorkspace(
    val profile: UserProfile,
    val accounts: List<FinancialAccount>,
    val transactions: List<LedgerTransaction>,
    val categoryBudgets: List<CategoryBudget>
)

data class CategoryMeta(
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val forIncome: Boolean = false
)

object CategoryCatalog {
    val expenseCategories = listOf(
        CategoryMeta("Food", Icons.Rounded.Restaurant, Color(0xFFEA580C)),
        CategoryMeta("Coffee", Icons.Rounded.Coffee, Color(0xFF92400E)),
        CategoryMeta("Groceries", Icons.Rounded.LocalGroceryStore, Color(0xFF16A34A)),
        CategoryMeta("Transport", Icons.Rounded.DirectionsCar, Color(0xFF2563EB)),
        CategoryMeta("Bills", Icons.Rounded.Home, Color(0xFF7C3AED)),
        CategoryMeta("Shopping", Icons.Rounded.ShoppingBag, Color(0xFFDB2777)),
        CategoryMeta("Health", Icons.Rounded.Favorite, Color(0xFFDC2626)),
        CategoryMeta("Fun", Icons.Rounded.Movie, Color(0xFFD97706)),
        CategoryMeta("Travel", Icons.Rounded.Flight, Color(0xFF0891B2))
    )

    val incomeCategories = listOf(
        CategoryMeta("Salary", Icons.Rounded.Work, Color(0xFF059669), forIncome = true),
        CategoryMeta("Freelance", Icons.Rounded.TrendingUp, Color(0xFF0D9488), forIncome = true),
        CategoryMeta("Gift", Icons.Rounded.CardGiftcard, Color(0xFF9333EA), forIncome = true),
        CategoryMeta("Refund", Icons.Rounded.AttachMoney, Color(0xFF2563EB), forIncome = true)
    )

    val transferMeta = CategoryMeta("Transfer", Icons.Rounded.SwapHoriz, Color(0xFF2563EB))

    fun find(category: String): CategoryMeta {
        if (category.equals("Transfer", ignoreCase = true)) return transferMeta
        return (expenseCategories + incomeCategories)
            .firstOrNull { it.name.equals(category, ignoreCase = true) }
            ?: CategoryMeta(category, Icons.Rounded.AttachMoney, Color(0xFF475569))
    }
}

val AccountColorPalette = listOf(
    "#3B28CC",
    "#2563EB",
    "#7C3AED",
    "#D97706",
    "#DC4A3D",
    "#0891B2",
    "#334155"
)

val SupportedCurrencies = listOf("$", "€", "£", "₹", "¥")

fun formatCents(cents: Long, symbol: String = "$", showPlus: Boolean = false): String {
    val isNeg = cents < 0
    val absVal = abs(cents) / 100.0
    val formatted = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(absVal)
    return when {
        isNeg -> "-$symbol$formatted"
        showPlus && cents > 0 -> "+$symbol$formatted"
        else -> "$symbol$formatted"
    }
}

fun formatCompactCents(cents: Long, symbol: String = "$"): String {
    val isNeg = cents < 0
    val absCents = abs(cents)
    val whole = absCents / 100
    val rem = absCents % 100
    val formatted = if (rem == 0L) {
        NumberFormat.getNumberInstance(Locale.US).format(whole)
    } else {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(absCents / 100.0)
    }
    return if (isNeg) "-$symbol$formatted" else "$symbol$formatted"
}

fun parseDecimalStringToCents(raw: String): Long {
    val cleaned = raw.trim().replace(",", "")
    if (cleaned.isEmpty() || cleaned == ".") return 0L
    val parsed = cleaned.toDoubleOrNull() ?: return 0L
    return (parsed * 100.0).roundToLong()
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF3B28CC)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
