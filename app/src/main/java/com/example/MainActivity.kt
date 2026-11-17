package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.BudgetRepository
import com.example.model.CategoryBudget
import com.example.model.FinancialAccount
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.model.UiState
import com.example.ui.BudgetViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.example.ui.components.AccountSheet
import com.example.ui.components.CategoryBudgetSheet
import com.example.ui.components.ExportAndProfileSheet
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.QuickLogScreen
import com.example.ui.theme.FinovaTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class FinovaTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    ACTIVITY("activity", "Activity", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    ACCOUNTS("accounts", "Accounts", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    INSIGHTS("insights", "Insights", Icons.Rounded.PieChart, Icons.Outlined.PieChart)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinovaTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            FinovaWorkspaceScreen(currentUserId = user.uid)
        }
    }
}

@Composable
private fun FinovaWorkspaceScreen(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: BudgetViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BudgetViewModel(BudgetRepository(db), currentUserId)
            }
        }
    )

    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var currentTab by remember { mutableStateOf(FinovaTab.HOME) }
    var showQuickLogSheet by remember { mutableStateOf(false) }
    var quickLogInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<FinancialAccount?>(null) }
    var showCategoryBudgetSheet by remember { mutableStateOf(false) }
    var editingCategoryBudget by remember { mutableStateOf<CategoryBudget?>(null) }
    var showExportProfileSheet by remember { mutableStateOf(false) }

    if (currentTab != FinovaTab.HOME) {
        BackHandler {
            currentTab = FinovaTab.HOME
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (workspaceState is UiState.Success) {
                    FloatingActionButton(
                        onClick = {
                            editingTransaction = null
                            quickLogInitialType = TransactionType.EXPENSE
                            showQuickLogSheet = true
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(58.dp)
                            .testTag("fab_quick_log")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Quick log transaction",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        FinovaTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(12.dp))
                        FinovaTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (val state = workspaceState) {
                        UiState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Syncing your ledger...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Sync interrupted",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        signOut(credentialManager, scope)
                                    }
                                ) {
                                    Text("Switch Account")
                                }
                            }
                        }

                        is UiState.Success -> {
                            val workspace = state.data
                            when (currentTab) {
                                FinovaTab.HOME -> HomeScreen(
                                    workspace = workspace,
                                    onOpenQuickLog = { type ->
                                        editingTransaction = null
                                        quickLogInitialType = type
                                        showQuickLogSheet = true
                                    },
                                    onEditTransaction = { tx ->
                                        editingTransaction = tx
                                        quickLogInitialType = tx.transactionType
                                        showQuickLogSheet = true
                                    },
                                    onOpenAddAccount = {
                                        editingAccount = null
                                        showAccountSheet = true
                                    },
                                    onOpenAccountsTab = { currentTab = FinovaTab.ACCOUNTS },
                                    onOpenActivityTab = { currentTab = FinovaTab.ACTIVITY },
                                    onOpenInsightsTab = { currentTab = FinovaTab.INSIGHTS },
                                    onOpenExportProfile = { showExportProfileSheet = true }
                                )

                                FinovaTab.ACTIVITY -> ActivityScreen(
                                    workspace = workspace,
                                    onEditTransaction = { tx ->
                                        editingTransaction = tx
                                        quickLogInitialType = tx.transactionType
                                        showQuickLogSheet = true
                                    },
                                    onOpenExportSheet = { showExportProfileSheet = true },
                                    onExportFeedback = { msg -> viewModel.notifyMessage(msg) }
                                )

                                FinovaTab.ACCOUNTS -> AccountsScreen(
                                    workspace = workspace,
                                    onAddAccount = {
                                        editingAccount = null
                                        showAccountSheet = true
                                    },
                                    onEditAccount = { acc ->
                                        editingAccount = acc
                                        showAccountSheet = true
                                    },
                                    onSetPrimary = { acc ->
                                        viewModel.setPrimaryAccount(acc.id, workspace.accounts)
                                    },
                                    onStartTransfer = {
                                        editingTransaction = null
                                        quickLogInitialType = TransactionType.TRANSFER
                                        showQuickLogSheet = true
                                    }
                                )

                                FinovaTab.INSIGHTS -> InsightsScreen(
                                    workspace = workspace,
                                    onAddCategoryBudget = {
                                        editingCategoryBudget = null
                                        showCategoryBudgetSheet = true
                                    },
                                    onEditCategoryBudget = { budget ->
                                        editingCategoryBudget = budget
                                        showCategoryBudgetSheet = true
                                    }
                                )
                            }

                            if (showAccountSheet) {
                                AccountSheet(
                                    existingAccount = editingAccount,
                                    currencySymbol = workspace.profile.currencySymbol,
                                    isSaving = isSaving,
                                    canDelete = workspace.accounts.size > 1,
                                    onDismiss = {
                                        showAccountSheet = false
                                        editingAccount = null
                                    },
                                    onSave = { name, type, balanceCents, colorHex, makePrimary ->
                                        viewModel.saveAccount(
                                            existingId = editingAccount?.id,
                                            name = name,
                                            type = type,
                                            balanceCents = balanceCents,
                                            colorHex = colorHex,
                                            makePrimary = makePrimary,
                                            currentAccounts = workspace.accounts,
                                            onComplete = {
                                                showAccountSheet = false
                                                editingAccount = null
                                            }
                                        )
                                    },
                                    onDelete = editingAccount?.let { acc ->
                                        {
                                            viewModel.deleteAccount(
                                                accountId = acc.id,
                                                currentAccounts = workspace.accounts,
                                                onComplete = {
                                                    showAccountSheet = false
                                                    editingAccount = null
                                                }
                                            )
                                        }
                                    }
                                )
                            }

                            if (showCategoryBudgetSheet) {
                                CategoryBudgetSheet(
                                    existingBudget = editingCategoryBudget,
                                    currencySymbol = workspace.profile.currencySymbol,
                                    isSaving = isSaving,
                                    onDismiss = {
                                        showCategoryBudgetSheet = false
                                        editingCategoryBudget = null
                                    },
                                    onSave = { category, limitCents ->
                                        viewModel.saveCategoryBudget(
                                            category = category,
                                            limitCents = limitCents,
                                            onComplete = {
                                                showCategoryBudgetSheet = false
                                                editingCategoryBudget = null
                                            }
                                        )
                                    },
                                    onDelete = editingCategoryBudget?.let { b ->
                                        {
                                            viewModel.deleteCategoryBudget(b.id)
                                            showCategoryBudgetSheet = false
                                            editingCategoryBudget = null
                                        }
                                    }
                                )
                            }

                            if (showExportProfileSheet) {
                                ExportAndProfileSheet(
                                    workspace = workspace,
                                    isSaving = isSaving,
                                    onDismiss = { showExportProfileSheet = false },
                                    onUpdatePreferences = { sym, budgetCents ->
                                        viewModel.updateProfilePreferences(
                                            currencySymbol = sym,
                                            monthlyBudgetCents = budgetCents,
                                            onComplete = { showExportProfileSheet = false }
                                        )
                                    },
                                    onExportFeedback = { msg -> viewModel.notifyMessage(msg) },
                                    onSignOut = {
                                        showExportProfileSheet = false
                                        signOut(credentialManager, scope)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        val activeWorkspace = (workspaceState as? UiState.Success)?.data
        AnimatedVisibility(
            visible = showQuickLogSheet && activeWorkspace != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            if (activeWorkspace != null) {
                key(editingTransaction?.id ?: quickLogInitialType.name) {
                    QuickLogScreen(
                        accounts = activeWorkspace.accounts,
                        currencySymbol = activeWorkspace.profile.currencySymbol,
                        existingTransaction = editingTransaction,
                        initialType = quickLogInitialType,
                        isSaving = isSaving,
                        onDismiss = {
                            showQuickLogSheet = false
                            editingTransaction = null
                        },
                        onSave = { type, amountCents, category, fromAcc, toAcc, note, dateMs ->
                            val currentEdit = editingTransaction
                            if (currentEdit == null) {
                                viewModel.addTransaction(
                                    type = type,
                                    amountCents = amountCents,
                                    category = category,
                                    fromAccount = fromAcc,
                                    toAccount = toAcc,
                                    note = note,
                                    dateEpochMs = dateMs,
                                    onComplete = {
                                        showQuickLogSheet = false
                                    }
                                )
                            } else {
                                viewModel.updateTransaction(
                                    oldTx = currentEdit,
                                    newType = type,
                                    newAmountCents = amountCents,
                                    newCategory = category,
                                    newFromAccount = fromAcc,
                                    newToAccount = toAcc,
                                    newNote = note,
                                    newDateEpochMs = dateMs,
                                    existingAccountIds = activeWorkspace.accounts.map { it.id }.toSet(),
                                    onComplete = {
                                        showQuickLogSheet = false
                                        editingTransaction = null
                                    }
                                )
                            }
                        },
                        onDelete = editingTransaction?.let { txToDelete ->
                            {
                                viewModel.deleteTransaction(
                                    tx = txToDelete,
                                    existingAccountIds = activeWorkspace.accounts.map { it.id }.toSet(),
                                    onComplete = {
                                        showQuickLogSheet = false
                                        editingTransaction = null
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
