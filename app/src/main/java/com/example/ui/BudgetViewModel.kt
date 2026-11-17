package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BudgetRepository
import com.example.model.AccountType
import com.example.model.BudgetWorkspace
import com.example.model.FinancialAccount
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(
    private val repository: BudgetRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val workspaceState: StateFlow<UiState<BudgetWorkspace>> = combine(
        repository.observeProfile(currentUserId),
        repository.observeAccounts(currentUserId),
        repository.observeTransactions(currentUserId),
        repository.observeCategoryBudgets(currentUserId)
    ) { profile, accounts, transactions, budgets ->
        val effectiveProfile = profile ?: UserProfile(
            userId = currentUserId,
            displayName = Firebase.auth.currentUser?.displayName ?: "Finova User",
            email = Firebase.auth.currentUser?.email ?: ""
        )
        UiState.Success(
            BudgetWorkspace(
                profile = effectiveProfile,
                accounts = accounts,
                transactions = transactions,
                categoryBudgets = budgets
            )
        ) as UiState<BudgetWorkspace>
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing budget workspace", rootError)
        emit(UiState.Error(rootError.message ?: "Unable to sync your budget data."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                if (user.uid == currentUserId) {
                    repository.ensureUserProfile(user)
                }
            }
        }
    }

    fun addTransaction(
        type: TransactionType,
        amountCents: Long,
        category: String,
        fromAccount: FinancialAccount,
        toAccount: FinancialAccount?,
        note: String,
        dateEpochMs: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addTransaction(
                userId = currentUserId,
                type = type,
                amountCents = amountCents,
                category = category,
                fromAccount = fromAccount,
                toAccount = toAccount,
                note = note,
                dateEpochMs = dateEpochMs
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Logged ${type.label.lowercase()}")
                    onComplete()
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Could not save transaction")
                }
            )
        }
    }

    fun updateTransaction(
        oldTx: LedgerTransaction,
        newType: TransactionType,
        newAmountCents: Long,
        newCategory: String,
        newFromAccount: FinancialAccount,
        newToAccount: FinancialAccount?,
        newNote: String,
        newDateEpochMs: Long,
        existingAccountIds: Set<String>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateTransaction(
                userId = currentUserId,
                oldTx = oldTx,
                newType = newType,
                newAmountCents = newAmountCents,
                newCategory = newCategory,
                newFromAccount = newFromAccount,
                newToAccount = newToAccount,
                newNote = newNote,
                newDateEpochMs = newDateEpochMs,
                existingAccountIds = existingAccountIds
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Transaction updated")
                    onComplete()
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to update transaction")
                }
            )
        }
    }

    fun deleteTransaction(
        tx: LedgerTransaction,
        existingAccountIds: Set<String>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteTransaction(currentUserId, tx, existingAccountIds)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Transaction deleted")
                    onComplete()
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to delete transaction")
                }
            )
        }
    }

    fun saveAccount(
        existingId: String?,
        name: String,
        type: AccountType,
        balanceCents: Long,
        colorHex: String,
        makePrimary: Boolean,
        currentAccounts: List<FinancialAccount>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveAccount(
                userId = currentUserId,
                existingId = existingId,
                name = name,
                type = type,
                balanceCents = balanceCents,
                colorHex = colorHex,
                makePrimary = makePrimary,
                currentAccounts = currentAccounts
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit(if (existingId == null) "Account added" else "Account updated")
                    onComplete()
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to save account")
                }
            )
        }
    }

    fun setPrimaryAccount(accountId: String, currentAccounts: List<FinancialAccount>) {
        viewModelScope.launch {
            val result = repository.setPrimaryAccount(currentUserId, accountId, currentAccounts)
            result.fold(
                onSuccess = { _events.tryEmit("Default account updated") },
                onFailure = { err -> _events.tryEmit(err.localizedMessage ?: "Could not update default account") }
            )
        }
    }

    fun deleteAccount(accountId: String, currentAccounts: List<FinancialAccount>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteAccount(currentUserId, accountId, currentAccounts)
            result.fold(
                onSuccess = {
                    _events.tryEmit("Account removed")
                    onComplete()
                },
                onFailure = { err -> _events.tryEmit(err.localizedMessage ?: "Failed to remove account") }
            )
        }
    }

    fun saveCategoryBudget(category: String, limitCents: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.upsertCategoryBudget(currentUserId, category, limitCents)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Budget saved for $category")
                    onComplete()
                },
                onFailure = { err -> _events.tryEmit(err.localizedMessage ?: "Failed to save budget") }
            )
        }
    }

    fun deleteCategoryBudget(budgetId: String) {
        viewModelScope.launch {
            val result = repository.deleteCategoryBudget(currentUserId, budgetId)
            result.fold(
                onSuccess = { _events.tryEmit("Category budget removed") },
                onFailure = { err -> _events.tryEmit(err.localizedMessage ?: "Failed to remove budget") }
            )
        }
    }

    fun updateProfilePreferences(currencySymbol: String, monthlyBudgetCents: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateProfilePreferences(currentUserId, currencySymbol, monthlyBudgetCents)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Preferences updated")
                    onComplete()
                },
                onFailure = { err -> _events.tryEmit(err.localizedMessage ?: "Failed to update preferences") }
            )
        }
    }

    fun notifyMessage(msg: String) {
        _events.tryEmit(msg)
    }

    private companion object {
        const val TAG = "BudgetVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
