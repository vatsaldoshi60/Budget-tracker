package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AccountType
import com.example.model.CategoryBudget
import com.example.model.FinancialAccount
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BudgetRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun accountsCol(uid: String) = userDoc(uid).collection("accounts")
    private fun transactionsCol(uid: String) = userDoc(uid).collection("transactions")
    private fun budgetsCol(uid: String) = userDoc(uid).collection("budgets")

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId(userId)
        val docRef = userDoc(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error -> handleStreamError(error, OperationType.GET, docRef.path, uid) }
        )
    }

    fun observeAccounts(userId: String): Flow<List<FinancialAccount>> = flow {
        val uid = requireUserId(userId)
        val col = accountsCol(uid)
        emitAll(
            col.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FinancialAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { !it.archived }
                        .sortedWith(
                            compareByDescending<FinancialAccount> { it.primaryAccount }
                                .thenBy { it.name.lowercase() }
                        )
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, col.path, uid) }
        )
    }

    fun observeTransactions(userId: String): Flow<List<LedgerTransaction>> = flow {
        val uid = requireUserId(userId)
        val col = transactionsCol(uid)
        emitAll(
            col.orderBy("dateEpochMs", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LedgerTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, col.path, uid) }
        )
    }

    fun observeCategoryBudgets(userId: String): Flow<List<CategoryBudget>> = flow {
        val uid = requireUserId(userId)
        val col = budgetsCol(uid)
        emitAll(
            col.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CategoryBudget::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.category.lowercase() }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, col.path, uid) }
        )
    }

    private fun handleStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            requireUserId(uid)
            val snapshot = userDoc(uid).get().await()
            if (!snapshot.exists()) {
                val safeName = user.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
                    ?: "Finova User"
                val safeEmail = (user.email ?: "").trim().take(120)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "currencySymbol" to "$",
                    "monthlyBudgetCents" to 250_000L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc(uid).set(payload).await()
            }
            val existingAccounts = accountsCol(uid).limit(1).get().await()
            if (existingAccounts.isEmpty) {
                val defaultRef = accountsCol(uid).document()
                val starterAccount = mapOf(
                    "userId" to uid,
                    "name" to "Main Wallet",
                    "type" to AccountType.CASH.name,
                    "balanceCents" to 0L,
                    "colorHex" to "#3B28CC",
                    "primaryAccount" to true,
                    "archived" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                defaultRef.set(starterAccount).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfilePreferences(
        userId: String,
        currencySymbol: String,
        monthlyBudgetCents: Long
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val safeSymbol = currencySymbol.trim().ifEmpty { "$" }.take(5)
            val safeBudget = monthlyBudgetCents.coerceIn(0L, 10_000_000_000L)
            userDoc(uid).update(
                mapOf(
                    "currencySymbol" to safeSymbol,
                    "monthlyBudgetCents" to safeBudget,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveAccount(
        userId: String,
        existingId: String?,
        name: String,
        type: AccountType,
        balanceCents: Long,
        colorHex: String,
        makePrimary: Boolean,
        currentAccounts: List<FinancialAccount>
    ): Result<Unit> {
        val path = if (existingId.isNullOrBlank()) "users/$userId/accounts" else "users/$userId/accounts/$existingId"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim().take(40)
            require(cleanName.isNotEmpty()) { "Account name cannot be empty" }
            val safeBalance = balanceCents.coerceIn(-10_000_000_000L, 10_000_000_000L)
            val safeHex = if (Regex("^#[0-9a-fA-F]{6,8}$").matches(colorHex)) colorHex else "#3B28CC"
            val isFirstAccount = existingId.isNullOrBlank() && currentAccounts.isEmpty()
            val finalPrimary = makePrimary || isFirstAccount

            val batch = db.batch()
            if (finalPrimary) {
                currentAccounts.filter { it.primaryAccount && it.id != existingId }.forEach { other ->
                    batch.update(
                        accountsCol(uid).document(other.id),
                        mapOf(
                            "primaryAccount" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            if (existingId.isNullOrBlank()) {
                val docRef = accountsCol(uid).document()
                batch.set(
                    docRef,
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "type" to type.name,
                        "balanceCents" to safeBalance,
                        "colorHex" to safeHex,
                        "primaryAccount" to finalPrimary,
                        "archived" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    accountsCol(uid).document(existingId),
                    mapOf(
                        "name" to cleanName,
                        "type" to type.name,
                        "balanceCents" to safeBalance,
                        "colorHex" to safeHex,
                        "primaryAccount" to finalPrimary,
                        "archived" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun setPrimaryAccount(
        userId: String,
        accountId: String,
        currentAccounts: List<FinancialAccount>
    ): Result<Unit> {
        val path = "users/$userId/accounts/$accountId"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            currentAccounts.forEach { acc ->
                val targetPrimary = acc.id == accountId
                if (acc.primaryAccount != targetPrimary) {
                    batch.update(
                        accountsCol(uid).document(acc.id),
                        mapOf(
                            "primaryAccount" to targetPrimary,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(
        userId: String,
        accountId: String,
        remainingAccounts: List<FinancialAccount>
    ): Result<Unit> {
        val path = "users/$userId/accounts/$accountId"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            batch.delete(accountsCol(uid).document(accountId))
            val nextCandidate = remainingAccounts.firstOrNull { it.id != accountId }
            if (nextCandidate != null && remainingAccounts.none { it.id != accountId && it.primaryAccount }) {
                batch.update(
                    accountsCol(uid).document(nextCandidate.id),
                    mapOf(
                        "primaryAccount" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addTransaction(
        userId: String,
        type: TransactionType,
        amountCents: Long,
        category: String,
        fromAccount: FinancialAccount,
        toAccount: FinancialAccount?,
        note: String,
        dateEpochMs: Long
    ): Result<Unit> {
        val path = "users/$userId/transactions"
        return try {
            val uid = requireUserId(userId)
            require(amountCents in 1L..10_000_000_000L) { "Amount must be greater than zero" }
            val cleanCategory = category.trim().ifEmpty { "Other" }.take(32)
            val cleanNote = note.trim().take(160)
            val safeDate = dateEpochMs.coerceAtLeast(1L)
            if (type == TransactionType.TRANSFER) {
                require(toAccount != null && toAccount.id != fromAccount.id) {
                    "Select a different destination account for transfers"
                }
            }

            val txRef = transactionsCol(uid).document()
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("type", type.name)
                put("amountCents", amountCents)
                put("category", if (type == TransactionType.TRANSFER) "Transfer" else cleanCategory)
                put("accountId", fromAccount.id)
                put("accountName", fromAccount.name.take(40))
                if (type == TransactionType.TRANSFER && toAccount != null) {
                    put("toAccountId", toAccount.id)
                    put("toAccountName", toAccount.name.take(40))
                }
                put("note", cleanNote)
                put("dateEpochMs", safeDate)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val batch = db.batch()
            batch.set(txRef, payload)

            val deltas = computeDeltas(type, amountCents, fromAccount.id, toAccount?.id, sign = 1)
            deltas.forEach { (accId, delta) ->
                if (delta != 0L) {
                    batch.update(
                        accountsCol(uid).document(accId),
                        mapOf(
                            "balanceCents" to FieldValue.increment(delta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        userId: String,
        oldTx: LedgerTransaction,
        newType: TransactionType,
        newAmountCents: Long,
        newCategory: String,
        newFromAccount: FinancialAccount,
        newToAccount: FinancialAccount?,
        newNote: String,
        newDateEpochMs: Long,
        existingAccountIds: Set<String>
    ): Result<Unit> {
        val path = "users/$userId/transactions/${oldTx.id}"
        return try {
            val uid = requireUserId(userId)
            require(oldTx.id.isNotBlank()) { "Transaction ID missing" }
            require(newAmountCents in 1L..10_000_000_000L) { "Amount must be greater than zero" }
            if (newType == TransactionType.TRANSFER) {
                require(newToAccount != null && newToAccount.id != newFromAccount.id) {
                    "Select a different destination account for transfers"
                }
            }

            val updatePayload = mutableMapOf<String, Any>(
                "type" to newType.name,
                "amountCents" to newAmountCents,
                "category" to (if (newType == TransactionType.TRANSFER) "Transfer" else newCategory.trim().ifEmpty { "Other" }.take(32)),
                "accountId" to newFromAccount.id,
                "accountName" to newFromAccount.name.take(40),
                "note" to newNote.trim().take(160),
                "dateEpochMs" to newDateEpochMs.coerceAtLeast(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newType == TransactionType.TRANSFER && newToAccount != null) {
                updatePayload["toAccountId"] = newToAccount.id
                updatePayload["toAccountName"] = newToAccount.name.take(40)
            } else {
                updatePayload["toAccountId"] = FieldValue.delete()
                updatePayload["toAccountName"] = FieldValue.delete()
            }

            val reversedOld = computeDeltas(
                oldTx.transactionType,
                oldTx.amountCents,
                oldTx.accountId,
                oldTx.toAccountId,
                sign = -1
            )
            val appliedNew = computeDeltas(
                newType,
                newAmountCents,
                newFromAccount.id,
                newToAccount?.id,
                sign = 1
            )

            val netDeltas = mutableMapOf<String, Long>()
            reversedOld.forEach { (id, d) -> netDeltas[id] = (netDeltas[id] ?: 0L) + d }
            appliedNew.forEach { (id, d) -> netDeltas[id] = (netDeltas[id] ?: 0L) + d }

            val batch = db.batch()
            batch.update(transactionsCol(uid).document(oldTx.id), updatePayload)
            netDeltas.forEach { (accId, delta) ->
                if (delta != 0L && accId in existingAccountIds) {
                    batch.update(
                        accountsCol(uid).document(accId),
                        mapOf(
                            "balanceCents" to FieldValue.increment(delta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(
        userId: String,
        tx: LedgerTransaction,
        existingAccountIds: Set<String>
    ): Result<Unit> {
        val path = "users/$userId/transactions/${tx.id}"
        return try {
            val uid = requireUserId(userId)
            require(tx.id.isNotBlank()) { "Transaction ID missing" }
            val batch = db.batch()
            batch.delete(transactionsCol(uid).document(tx.id))
            val reverseDeltas = computeDeltas(
                tx.transactionType,
                tx.amountCents,
                tx.accountId,
                tx.toAccountId,
                sign = -1
            )
            reverseDeltas.forEach { (accId, delta) ->
                if (delta != 0L && accId in existingAccountIds) {
                    batch.update(
                        accountsCol(uid).document(accId),
                        mapOf(
                            "balanceCents" to FieldValue.increment(delta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertCategoryBudget(
        userId: String,
        category: String,
        limitCents: Long
    ): Result<Unit> {
        val cleanCategory = category.trim().take(32)
        val docId = cleanCategory.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "category" }
        val path = "users/$userId/budgets/$docId"
        return try {
            val uid = requireUserId(userId)
            require(cleanCategory.isNotEmpty()) { "Category cannot be empty" }
            require(limitCents in 1L..10_000_000_000L) { "Budget limit must be positive" }
            val docRef = budgetsCol(uid).document(docId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "category" to cleanCategory,
                        "limitCents" to limitCents,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "category" to cleanCategory,
                        "limitCents" to limitCents,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCategoryBudget(userId: String, budgetId: String): Result<Unit> {
        val path = "users/$userId/budgets/$budgetId"
        return try {
            val uid = requireUserId(userId)
            budgetsCol(uid).document(budgetId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun computeDeltas(
        type: TransactionType,
        amountCents: Long,
        fromAccountId: String,
        toAccountId: String?,
        sign: Int
    ): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        when (type) {
            TransactionType.EXPENSE -> {
                map[fromAccountId] = -amountCents * sign
            }
            TransactionType.INCOME -> {
                map[fromAccountId] = amountCents * sign
            }
            TransactionType.TRANSFER -> {
                map[fromAccountId] = -amountCents * sign
                if (!toAccountId.isNullOrBlank()) {
                    map[toAccountId] = (map[toAccountId] ?: 0L) + (amountCents * sign)
                }
            }
        }
        return map
    }
}
