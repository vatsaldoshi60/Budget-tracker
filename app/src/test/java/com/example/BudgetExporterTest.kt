package com.example

import com.example.export.BudgetExporter
import com.example.export.ExportPeriod
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BudgetExporterTest {
    @Test
    fun buildCsv_formatsSignedAmountsAndEscapesCommasAndQuotes() {
        val tx1 = LedgerTransaction(
            id = "tx1",
            type = TransactionType.EXPENSE.name,
            amountCents = 1450L,
            category = "Food",
            accountId = "acc1",
            accountName = "Main, Bank",
            note = "Lunch \"special\"",
            dateEpochMs = 1_760_000_000_000L
        )
        val tx2 = LedgerTransaction(
            id = "tx2",
            type = TransactionType.INCOME.name,
            amountCents = 200000L,
            category = "Salary",
            accountId = "acc1",
            accountName = "Main, Bank",
            note = "Payday",
            dateEpochMs = 1_760_100_000_000L
        )

        val csv = BudgetExporter.buildCsv(
            transactions = listOf(tx1, tx2),
            currencySymbol = "$",
            includeUtf8Bom = false
        )
        val lines = csv.trim().lines()

        assertEquals(3, lines.size)
        assertEquals("Date,Type,Category,Amount,SignedAmount,Currency,FromAccount,ToAccount,Note", lines[0])
        assertTrue(lines[1].contains(",INCOME,Salary,2000.00,2000.00,$,\"Main, Bank\",,Payday"))
        assertTrue(lines[2].contains(",EXPENSE,Food,14.50,-14.50,$,\"Main, Bank\",,\"Lunch \"\"special\"\"\""))
    }

    @Test
    fun filterByPeriod_filtersThisMonthAndLastMonthCorrectly() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 12, 0, 0)
        }
        val lastMonth = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
        }
        val older = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 5, 12, 0, 0)
        }

        val txs = listOf(
            LedgerTransaction(id = "nov", dateEpochMs = now.timeInMillis),
            LedgerTransaction(id = "oct", dateEpochMs = lastMonth.timeInMillis),
            LedgerTransaction(id = "aug", dateEpochMs = older.timeInMillis)
        )

        assertEquals(listOf("nov"), BudgetExporter.filterByPeriod(txs, ExportPeriod.THIS_MONTH, now.timeInMillis).map { it.id })
        assertEquals(listOf("oct"), BudgetExporter.filterByPeriod(txs, ExportPeriod.LAST_MONTH, now.timeInMillis).map { it.id })
        assertEquals(3, BudgetExporter.filterByPeriod(txs, ExportPeriod.ALL_TIME, now.timeInMillis).size)
    }
}
