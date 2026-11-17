package com.example.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.BudgetWorkspace
import com.example.model.FinancialAccount
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.model.formatCents
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExportPeriod(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    ALL_TIME("All Time")
}

object BudgetExporter {
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)

    fun filterByPeriod(
        transactions: List<LedgerTransaction>,
        period: ExportPeriod,
        referenceTimeMs: Long = System.currentTimeMillis()
    ): List<LedgerTransaction> {
        if (period == ExportPeriod.ALL_TIME) return transactions
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMs }
        val targetYear: Int
        val targetMonth: Int
        if (period == ExportPeriod.THIS_MONTH) {
            targetYear = cal.get(Calendar.YEAR)
            targetMonth = cal.get(Calendar.MONTH)
        } else {
            cal.add(Calendar.MONTH, -1)
            targetYear = cal.get(Calendar.YEAR)
            targetMonth = cal.get(Calendar.MONTH)
        }
        val itemCal = Calendar.getInstance()
        return transactions.filter { tx ->
            itemCal.timeInMillis = tx.dateEpochMs
            itemCal.get(Calendar.YEAR) == targetYear && itemCal.get(Calendar.MONTH) == targetMonth
        }
    }

    fun suggestedFileName(prefix: String, period: ExportPeriod, ext: String): String {
        val slug = period.name.lowercase(Locale.US)
        val stamp = fileStampFormat.format(Date())
        return "${prefix}_${slug}_$stamp.$ext"
    }

    fun suggestedFilteredFileName(prefix: String, ext: String): String {
        val stamp = fileStampFormat.format(Date())
        return "${prefix}_filtered_$stamp.$ext"
    }

    fun buildCsv(
        transactions: List<LedgerTransaction>,
        currencySymbol: String,
        includeUtf8Bom: Boolean = true
    ): String {
        val sb = StringBuilder()
        if (includeUtf8Bom) {
            sb.append('\uFEFF')
        }
        sb.appendLine("Date,Type,Category,Amount,SignedAmount,Currency,FromAccount,ToAccount,Note")
        transactions.sortedByDescending { it.dateEpochMs }.forEach { tx ->
            val dateStr = isoDateFormat.format(Date(tx.dateEpochMs))
            val amountStr = String.format(Locale.US, "%.2f", tx.amountCents / 100.0)
            val signedStr = when (tx.transactionType) {
                TransactionType.EXPENSE -> String.format(Locale.US, "-%.2f", tx.amountCents / 100.0)
                TransactionType.INCOME -> String.format(Locale.US, "%.2f", tx.amountCents / 100.0)
                TransactionType.TRANSFER -> amountStr
            }
            sb.append(csvEscape(dateStr)).append(',')
                .append(csvEscape(tx.type)).append(',')
                .append(csvEscape(tx.category)).append(',')
                .append(amountStr).append(',')
                .append(signedStr).append(',')
                .append(csvEscape(currencySymbol)).append(',')
                .append(csvEscape(tx.accountName)).append(',')
                .append(csvEscape(tx.toAccountName.orEmpty())).append(',')
                .append(csvEscape(tx.note))
                .append('\n')
        }
        return sb.toString()
    }

    fun buildJson(
        workspace: BudgetWorkspace,
        transactions: List<LedgerTransaction>,
        period: ExportPeriod
    ): String {
        val root = JSONObject()
        root.put("app", "Finova")
        root.put("exportedAt", isoDateFormat.format(Date()))
        root.put("period", period.label)
        root.put("currencySymbol", workspace.profile.currencySymbol)
        root.put("monthlyBudgetCents", workspace.profile.monthlyBudgetCents)

        val accountsArr = JSONArray()
        workspace.accounts.forEach { acc ->
            accountsArr.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("name", acc.name)
                    put("type", acc.type)
                    put("balanceCents", acc.balanceCents)
                    put("primaryAccount", acc.primaryAccount)
                }
            )
        }
        root.put("accounts", accountsArr)

        val budgetsArr = JSONArray()
        workspace.categoryBudgets.forEach { b ->
            budgetsArr.put(
                JSONObject().apply {
                    put("category", b.category)
                    put("limitCents", b.limitCents)
                }
            )
        }
        root.put("categoryBudgets", budgetsArr)

        val txArr = JSONArray()
        transactions.sortedByDescending { it.dateEpochMs }.forEach { tx ->
            txArr.put(
                JSONObject().apply {
                    put("id", tx.id)
                    put("type", tx.type)
                    put("amountCents", tx.amountCents)
                    put("category", tx.category)
                    put("accountId", tx.accountId)
                    put("accountName", tx.accountName)
                    if (!tx.toAccountId.isNullOrBlank()) {
                        put("toAccountId", tx.toAccountId)
                        put("toAccountName", tx.toAccountName)
                    }
                    put("note", tx.note)
                    put("dateIso", isoDateFormat.format(Date(tx.dateEpochMs)))
                    put("dateEpochMs", tx.dateEpochMs)
                }
            )
        }
        root.put("transactions", txArr)
        return root.toString(2)
    }

    fun buildTextSummary(
        workspace: BudgetWorkspace,
        transactions: List<LedgerTransaction>,
        period: ExportPeriod
    ): String {
        val symbol = workspace.profile.currencySymbol
        val income = transactions.filter { it.transactionType == TransactionType.INCOME }
            .sumOf { it.amountCents }
        val expense = transactions.filter { it.transactionType == TransactionType.EXPENSE }
            .sumOf { it.amountCents }
        val netWorth = workspace.accounts.sumOf { it.balanceCents }

        val topCategories = transactions
            .filter { it.transactionType == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
            .entries
            .sortedByDescending { it.value }
            .take(5)

        return buildString {
            appendLine("Finova Summary (${period.label})")
            appendLine("===============================")
            appendLine("Net Balance across ${workspace.accounts.size} accounts: ${formatCents(netWorth, symbol)}")
            appendLine("Total Income:  ${formatCents(income, symbol)}")
            appendLine("Total Expense: ${formatCents(expense, symbol)}")
            appendLine("Net Cash Flow: ${formatCents(income - expense, symbol, showPlus = true)}")
            if (topCategories.isNotEmpty()) {
                appendLine()
                appendLine("Top Spending Categories:")
                topCategories.forEach { (cat, sum) ->
                    appendLine(" - $cat: ${formatCents(sum, symbol)}")
                }
            }
            if (workspace.accounts.isNotEmpty()) {
                appendLine()
                appendLine("Accounts:")
                workspace.accounts.forEach { acc: FinancialAccount ->
                    appendLine(" - ${acc.name} (${acc.accountType.label}): ${formatCents(acc.balanceCents, symbol)}")
                }
            }
        }
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: return Result.failure(IllegalStateException("Unable to open output stream"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareFile(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String,
        chooserTitle: String
    ): Result<Unit> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = File(exportDir, fileName)
            outFile.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                clipData = ClipData.newRawUri(fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sharePlainText(context: Context, text: String, title: String): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            context.startActivity(Intent.createChooser(intent, title))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun csvEscape(value: String): String {
        val sanitized = value.replace("\r", " ").replace("\n", " ")
        return if (sanitized.contains(',') || sanitized.contains('"')) {
            "\"${sanitized.replace("\"", "\"\"")}\""
        } else {
            sanitized
        }
    }
}
