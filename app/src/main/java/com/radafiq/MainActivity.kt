package com.radafiq

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.radafiq.data.auth.GoogleSignInHelper
import com.radafiq.data.auth.LocalIdentityRepository
import com.radafiq.data.backup.BackupJsonSerializer
import com.radafiq.data.backup.DriveBackupRepository
import com.radafiq.data.backup.FileBackupRepository
import com.radafiq.data.models.CardSummary
import com.radafiq.data.models.hasLedgerActivity
import com.radafiq.data.profile.UserProfileRepository
import com.radafiq.data.security.AppSecurityRepository
import com.radafiq.data.settings.AppSettingsRepository
import com.radafiq.security.BiometricAuthManager
import com.radafiq.ui.AccountDetailScreen
import com.radafiq.ui.AddPaymentScreen
import com.radafiq.ui.AddTransactionScreen
import com.radafiq.ui.AppLockScreen
import com.radafiq.ui.ChangePasscodeScreen
import com.radafiq.ui.CustomerDetailScreen
import com.radafiq.ui.CustomerSavingsScreen
import com.radafiq.ui.RadafiqBackground
import com.radafiq.ui.RadafiqTheme
import com.radafiq.ui.DashboardScreen
import com.radafiq.ui.ProfileSetupScreen
import com.radafiq.ui.SecuritySetupScreen
import com.radafiq.ui.SettingsScreen
import com.radafiq.viewmodel.MainViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : FragmentActivity() {
    private var externalDocumentFlowInProgress = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        requestNotificationPermissionIfNeeded()

        setContent {
            AppRoot()
        }
    }

    // FIX-6: Re-apply FLAG_SECURE on every resume so it survives theme changes and
    // activity recreation on some OEM ROMs.
    override fun onResume() {
        super.onResume()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    @Composable
    private fun AppRoot() {
        val settingsRepository = remember { AppSettingsRepository(applicationContext) }
        val securityRepository = remember { AppSecurityRepository(applicationContext) }
        val profileRepository = remember { UserProfileRepository() }
        val fileBackupRepository = remember { FileBackupRepository(applicationContext) }
        val mainViewModel: MainViewModel = viewModel()
        val biometricAuthManager = remember { BiometricAuthManager() }
        val navController = rememberNavController()

        // Wire auto-backup — runs 10s after any data change if user is signed in to Google
        LaunchedEffect(mainViewModel) {
            mainViewModel.initAutoBackup(
                context = applicationContext,
                profileRepo = profileRepository,
                settingsRepo = settingsRepository,
                securityRepo = securityRepository
            )
        }
        val coroutineScope = rememberCoroutineScope()

        val settingsState by settingsRepository.settings.collectAsState()
        val securityState by securityRepository.state.collectAsState()
        val profileState by profileRepository.state.collectAsState()
        val cards by mainViewModel.cards.collectAsState()

        var backupStatusMessage by rememberSaveable { mutableStateOf("") }
        var backupOperationInProgress by rememberSaveable { mutableStateOf(false) }
        var lockErrorMessage by rememberSaveable { mutableStateOf("") }

        // Google Drive backup state
        var driveStatusMessage by rememberSaveable { mutableStateOf("") }
        var driveOperationInProgress by rememberSaveable { mutableStateOf(false) }
        // Tracks what action to perform after sign-in: "backup" or "restore"
        var pendingDriveAction by rememberSaveable { mutableStateOf("") }
        val driveBackupRepository = remember { DriveBackupRepository() }

        // Google Sign-In for profile setup
        var profileGoogleSignInInProgress by rememberSaveable { mutableStateOf(false) }
        var pendingProfileCallback by remember { mutableStateOf<((String, String, String, String) -> Unit)?>(null) }
        // Shows a "Restoring from Drive..." overlay after sign-in
        var loginRestoreInProgress by rememberSaveable { mutableStateOf(false) }

        val profileGoogleSignInLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val name = account.displayName.orEmpty()
                val email = account.email.orEmpty()
                val photo = account.photoUrl?.toString().orEmpty()
                // BUG-04: Validate that email is non-null/non-blank before proceeding
                if (email.isBlank()) {
                    android.util.Log.w("MainActivity", "Google Sign-In returned account with blank email — aborting")
                    profileGoogleSignInInProgress = false
                    return@rememberLauncherForActivityResult
                }
                if (email.isNotBlank()) {
                    loginRestoreInProgress = true
                    profileGoogleSignInInProgress = false
                    coroutineScope.launch {
                        // 1. Sign into Firebase Auth — get real UID
                        val firebaseUid = GoogleSignInHelper.signInToFirebase(account)
                        if (firebaseUid != null) {
                            LocalIdentityRepository.setIdentityFromFirebaseUid(firebaseUid, applicationContext)
                        } else {
                            // Fallback: Firebase Auth unavailable, use email-based ID
                            LocalIdentityRepository.setIdentityFromEmail(email, applicationContext)
                        }

                        // 2. Reinitialize data layer with new identity
                        settingsRepository.reloadForCurrentUser()
                        mainViewModel.reinitialize()
                        profileRepository.observeCurrentUserProfile()

                        // 3. Save profile (callback runs after identity is set)
                        pendingProfileCallback?.invoke(name, "", email, photo)
                        pendingProfileCallback = null

                        // 4. Restore from Drive
                        runCatching {
                            val token = GoogleSignInHelper.fetchAccessToken(applicationContext, account)
                            val json = withContext(Dispatchers.IO) {
                                driveBackupRepository.downloadLatestBackup(token).getOrThrow()
                            }
                            mainViewModel.restoreFromJson(
                                json = json,
                                profileRepo = profileRepository,
                                settingsRepo = settingsRepository,
                                securityRepo = securityRepository
                            )
                        }
                        // Errors silently ignored — first login, no backup yet is fine
                        loginRestoreInProgress = false
                    }
                } else {
                    profileGoogleSignInInProgress = false
                    pendingProfileCallback = null
                }
            } catch (e: ApiException) {
                profileGoogleSignInInProgress = false
                loginRestoreInProgress = false
                pendingProfileCallback = null
                android.util.Log.w("SignIn", "Google Sign-In failed: code ${e.statusCode}")
                driveStatusMessage = "Sign-in failed (code ${e.statusCode}). Please try again."
            }
        }
        val biometricAvailable = remember { biometricAuthManager.canAuthenticate(this@MainActivity) }
        val lockedAccountIds = remember(cards) {
            cards.filter(CardSummary::hasLedgerActivity).mapTo(linkedSetOf()) { it.id }
        }

        fun extractNestedMap(value: Any?): Map<String, Any?> {
            return (value as? Map<*, *>)?.entries
                ?.associate { entry -> entry.key.toString() to entry.value }
                .orEmpty()
        }

        fun defaultBackupFileName(): String {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            return "radafiq_backup_$timestamp.json"
        }

        suspend fun restoreBackupJson(
            backupJson: String,
            keepUnlocked: Boolean
        ) {
            val payload = withContext(Dispatchers.IO) { BackupJsonSerializer.fromJson(backupJson) }
            val profileMap = payload.profile.filterValues { it != null }.mapValues { it.value as Any }
            // Use suspend restoreBackup so all Firestore writes complete before returning
            mainViewModel.restoreBackup(payload)
            profileRepository.restoreProfileMapAsync(profileMap)
            settingsRepository.restoreSettings(extractNestedMap(payload.settings["app"]))
            securityRepository.restoreSettings(extractNestedMap(payload.settings["security"]))
            if (keepUnlocked) securityRepository.unlock()
        }

        // Auto-restore from Drive after Google Sign-In is now handled directly
        // in profileGoogleSignInLauncher via mainViewModel.restoreFromJson()

        fun exportBackupToFile(uri: Uri) {
            coroutineScope.launch {
                backupOperationInProgress = true
                backupStatusMessage = "Exporting backup..."
                try {
                    backupStatusMessage = runCatching {
                        withContext(Dispatchers.IO) {
                            val backupPayload = mainViewModel.exportBackup(
                                profile = profileRepository.exportProfileMap(),
                                settings = mapOf(
                                    "app" to settingsRepository.exportSettings(),
                                    "security" to securityRepository.exportSettings()
                                )
                            )
                            val backupJson = BackupJsonSerializer.toJson(backupPayload)
                            fileBackupRepository.writeBackup(uri, backupJson).getOrThrow()
                        }
                        "Backup exported to file."
                    }.getOrElse { throwable ->
                        "Backup export failed: ${throwable.localizedMessage ?: "unknown error"}"
                    }
                } finally {
                    backupOperationInProgress = false
                }
            }
        }

        fun importBackupFromFile(uri: Uri) {
            coroutineScope.launch {
                val keepUnlocked = securityState.isUnlocked
                backupOperationInProgress = true
                backupStatusMessage = "Importing backup..."
                try {
                    backupStatusMessage = runCatching {
                        val backupJson = fileBackupRepository.readBackup(uri).getOrThrow()
                        restoreBackupJson(
                            backupJson = backupJson,
                            keepUnlocked = keepUnlocked
                        )
                        "Backup restored from file."
                    }.getOrElse { throwable ->
                        "Backup import failed: ${throwable.localizedMessage ?: "unknown error"}"
                    }
                } finally {
                    backupOperationInProgress = false
                }
            }
        }

        // ── Google Drive helpers ──────────────────────────────────────────────

        suspend fun performDriveBackup(account: GoogleSignInAccount) {
            driveOperationInProgress = true
            driveStatusMessage = "Uploading to Google Drive..."
            mainViewModel.updateDriveOperationMessage("Backing up to Google Drive...")
            try {
                driveStatusMessage = runCatching {
                    withTimeout(60_000L) {
                        val token = GoogleSignInHelper.fetchAccessToken(applicationContext, account)
                        withContext(Dispatchers.IO) {
                            val payload = mainViewModel.exportBackup(
                                profile = profileRepository.exportProfileMap(),
                                settings = mapOf(
                                    "app" to settingsRepository.exportSettings(),
                                    "security" to securityRepository.exportSettings()
                                )
                            )
                            val json = BackupJsonSerializer.toJson(payload)
                            driveBackupRepository.uploadBackup(token, json).getOrThrow()
                        }
                    }
                    mainViewModel.recordDriveBackupCompleted()
                    "Backup uploaded to Google Drive."
                }.getOrElse { "Drive backup failed: ${it.localizedMessage ?: "unknown error"}" }
            } finally {
                driveOperationInProgress = false
                mainViewModel.finishDriveOperation()
            }
        }

        suspend fun performDriveRestore(account: GoogleSignInAccount) {
            val keepUnlocked = securityState.isUnlocked
            driveOperationInProgress = true
            driveStatusMessage = "Downloading from Google Drive..."
            mainViewModel.updateDriveOperationMessage("Restoring from Google Drive...")
            try {
                // Step 1: download JSON with a timeout — this is the network-bound part
                val json = runCatching {
                    withTimeout(60_000L) {
                        val token = GoogleSignInHelper.fetchAccessToken(applicationContext, account)
                        withContext(Dispatchers.IO) {
                            driveBackupRepository.downloadLatestBackup(token).getOrThrow()
                        }
                    }
                }.getOrElse { e ->
                    driveStatusMessage = "Drive restore failed: ${e.localizedMessage ?: "download error"}"
                    return
                }

                // Step 2: restore into Firestore — no timeout, let it complete fully
                driveStatusMessage = "Restoring data..."
                mainViewModel.updateDriveOperationMessage("Applying restored Drive data...")
                runCatching {
                    mainViewModel.suppressNextBackups(3)
                    restoreBackupJson(json, keepUnlocked)
                }.onFailure { e ->
                    driveStatusMessage = "Drive restore failed: ${e.localizedMessage ?: "restore error"}"
                    return
                }

                mainViewModel.recordDriveRestoreCompleted()
                driveStatusMessage = "Backup restored from Google Drive."
            } finally {
                driveOperationInProgress = false
                mainViewModel.finishDriveOperation()
            }
        }

        val googleSignInLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val requestedDriveAction = pendingDriveAction
            coroutineScope.launch {
                try {
                    val account = task.getResult(ApiException::class.java)
                    when (requestedDriveAction) {
                        "backup" -> performDriveBackup(account)
                        "restore" -> performDriveRestore(account)
                        else -> {
                            driveOperationInProgress = false
                            mainViewModel.finishDriveOperation()
                        }
                    }
                } catch (e: ApiException) {
                    driveStatusMessage = "Google Sign-In failed (code ${e.statusCode})."
                    driveOperationInProgress = false
                    mainViewModel.finishDriveOperation()
                } finally {
                    pendingDriveAction = ""
                }
            }
        }

        fun launchDriveAction(action: String) {
            val existingDriveMessage = mainViewModel.driveOperationMessage.value
            if (driveOperationInProgress || pendingDriveAction.isNotBlank() || !existingDriveMessage.isNullOrBlank()) {
                if (!existingDriveMessage.isNullOrBlank()) {
                    driveStatusMessage = existingDriveMessage
                }
                return
            }
            pendingDriveAction = action
            driveOperationInProgress = true
            driveStatusMessage = if (action == "backup") {
                "Preparing Google Drive backup..."
            } else {
                "Preparing Google Drive restore..."
            }
            mainViewModel.beginDriveOperation(
                if (action == "backup") {
                    "Preparing Google Drive backup..."
                } else {
                    "Preparing Google Drive restore..."
                }
            )
            val cached = GoogleSignInHelper.getSignedInAccount(applicationContext)
            if (cached != null) {
                coroutineScope.launch {
                    try {
                        when (action) {
                            "backup" -> performDriveBackup(cached)
                            "restore" -> performDriveRestore(cached)
                            else -> {
                                driveOperationInProgress = false
                                mainViewModel.finishDriveOperation()
                            }
                        }
                    } finally {
                        pendingDriveAction = ""
                    }
                }
            } else {
                // Sign out first to force account picker and avoid stale state
                GoogleSignInHelper.buildClient(applicationContext).signOut().addOnCompleteListener {
                    googleSignInLauncher.launch(GoogleSignInHelper.signInIntent(applicationContext))
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────

        val exportBackupLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            externalDocumentFlowInProgress = false
            if (uri != null) {
                exportBackupToFile(uri)
            } else {
                backupStatusMessage = "Backup export cancelled."
            }
        }

        val importBackupLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            externalDocumentFlowInProgress = false
            if (uri != null) {
                importBackupFromFile(uri)
            } else {
                backupStatusMessage = "Backup import cancelled."
            }
        }

        LaunchedEffect(Unit) {
            profileRepository.observeCurrentUserProfile()
        }

        DisposableEffect(
            securityState.lockEnabled,
            securityState.hasPasscode
        ) {
            var stopTimestamp = 0L

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        // Record when app went to background
                        stopTimestamp = System.currentTimeMillis()
                    }
                    Lifecycle.Event.ON_START -> {
                        // App came back — lock if it was backgrounded for more than 1.5 minutes.
                        // This covers: device lock/sleep, home button, recent apps, app killed.
                        // It does NOT fire on in-app navigation (tab switches, screen changes).
                        val elapsed = System.currentTimeMillis() - stopTimestamp
                        if (
                            stopTimestamp > 0 &&
                            elapsed > 90_000L && // 1.5 minutes inactivity threshold
                            securityState.lockEnabled &&
                            securityState.hasPasscode &&
                            !externalDocumentFlowInProgress
                        ) {
                            securityRepository.lock()
                        }
                        stopTimestamp = 0L
                    }
                    else -> {}
                }
            }

            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
            }
        }

        val profile = profileState.profile
        val needsProfileSetup = profileState.isLoading.not() && profile?.isProfileComplete != true
        val needsSecuritySetup = profileState.isLoading.not() &&
            !loginRestoreInProgress &&
            profile?.isProfileComplete == true &&
            !securityState.hasPasscode
        val requiresUnlock = profileState.isLoading.not() &&
            profile?.isProfileComplete == true &&
            securityState.lockEnabled &&
            securityState.hasPasscode &&
            !securityState.isUnlocked

        RadafiqTheme(themeMode = settingsState.themeMode) {
            when {
                profileState.isLoading -> {
                    RadafiqBackground {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                needsProfileSetup -> {
                    ProfileSetupScreen(
                        profile = profile,
                        onSave = { displayName, businessName, email, photoUrl ->
                            coroutineScope.launch {
                                profileRepository.saveProfile(displayName, businessName, email, photoUrl)
                            }
                        },
                        onSignInWithGoogle = {
                            profileGoogleSignInInProgress = true
                            pendingProfileCallback = { name, _, email, photo ->
                                coroutineScope.launch {
                                    profileRepository.saveProfile(name, profile?.businessName.orEmpty(), email, photo)
                                }
                            }
                            GoogleSignInHelper.buildClient(applicationContext).signOut()
                                .addOnCompleteListener {
                                    profileGoogleSignInLauncher.launch(
                                        GoogleSignInHelper.signInIntent(applicationContext)
                                    )
                                }
                        },
                        googleSignInInProgress = profileGoogleSignInInProgress || loginRestoreInProgress,
                        loginRestoreInProgress = loginRestoreInProgress
                    )
                }

                needsSecuritySetup -> {
                    SecuritySetupScreen(
                        biometricAvailable = biometricAvailable,
                        initialRecoveryQuestion = profile?.email?.takeIf { it.isNotBlank() }?.let {
                            "What is your email ID?"
                        }.orEmpty(),
                        onSave = { passcode, recoveryQuestion, recoveryAnswer, enableBiometric ->
                            securityRepository.setPasscode(
                                passcode = passcode,
                                recoveryQuestion = recoveryQuestion,
                                recoveryAnswer = recoveryAnswer
                            )
                            securityRepository.setBiometricEnabled(enableBiometric)
                            securityRepository.unlock()
                        }
                    )
                }

                requiresUnlock -> {
                    AppLockScreen(
                        biometricAvailable = biometricAvailable,
                        biometricEnabled = securityState.biometricEnabled && biometricAvailable,
                        recoveryQuestion = securityState.recoveryQuestion,
                        errorMessage = lockErrorMessage,
                        onUnlockWithPasscode = { passcode ->
                            val remainingMs = securityRepository.lockoutRemainingMs()
                            if (remainingMs > 0) {
                                val seconds = (remainingMs / 1000).coerceAtLeast(1)
                                lockErrorMessage = "Too many attempts. Try again in ${seconds}s."
                                false
                            } else {
                                val unlocked = securityRepository.verifyPasscode(passcode)
                                lockErrorMessage = if (unlocked) {
                                    ""
                                } else {
                                    val attempts = securityRepository.failedAttemptCount()
                                    when {
                                        attempts >= 10 -> "Too many attempts. Locked for 10 minutes."
                                        attempts >= 7  -> "Too many attempts. Locked for 2 minutes."
                                        attempts >= 5  -> "Too many attempts. Locked for 30 seconds."
                                        else           -> "Incorrect passcode."
                                    }
                                }
                                unlocked
                            }
                        },
                        onUnlockWithBiometric = if (securityState.biometricEnabled && biometricAvailable) {
                            {
                                biometricAuthManager.authenticate(
                                    activity = this@MainActivity,
                                    title = "Unlock Radafiq",
                                    subtitle = "Verify with fingerprint, face unlock, or device credential.",
                                    onSuccess = {
                                        lockErrorMessage = ""
                                        securityRepository.unlock()
                                    },
                                    onFailure = { message ->
                                        lockErrorMessage = message
                                    }
                                )
                            }
                        } else {
                            null
                        },
                        onResetWithRecovery = if (securityState.hasRecoveryQuestion) {
                            { recoveryAnswer, newPasscode, enableBiometric ->
                                val reset = securityRepository.resetPasscodeWithRecovery(
                                    recoveryAnswer = recoveryAnswer,
                                    newPasscode = newPasscode
                                )
                                if (reset) {
                                    securityRepository.setBiometricEnabled(enableBiometric)
                                    securityRepository.unlock()
                                    lockErrorMessage = ""
                                } else {
                                    lockErrorMessage = "Recovery answer is incorrect."
                                }
                                reset
                            }
                        } else {
                            null
                        }
                    )
                }

                else -> {
                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(
                                navController = navController,
                                selectedAccountIds = settingsState.selectedAccountIds,
                                profileName = profile?.displayName.orEmpty(),
                                photoUrl = profile?.photoUrl.orEmpty(),
                                vm = mainViewModel,
                                settingsState = settingsState,
                                profile = profile,
                                securityState = securityState,
                                lockedAccountIds = lockedAccountIds,
                                backupStatusMessage = backupStatusMessage,
                                isBackupOperationInProgress = backupOperationInProgress,
                                lastDriveBackupTime = settingsState.lastDriveBackupTime,
                                lastDriveRestoreTime = settingsState.lastDriveRestoreTime,
                                onThemeModeSelected = settingsRepository::setThemeMode,
                                onAccountSelectionChanged = settingsRepository::setAccountSelected,
                                onLockEnabledChanged = securityRepository::setLockEnabled,
                                onBiometricEnabledChanged = securityRepository::setBiometricEnabled,
                                onEditProfile = { navController.navigate("profile") },
                                onOpenSecuritySetup = { navController.navigate("securitySetup") },
                                onBackupToDrive = {
                                    backupStatusMessage = "Choose where to save your backup."
                                    externalDocumentFlowInProgress = true
                                    runCatching {
                                        exportBackupLauncher.launch(defaultBackupFileName())
                                    }.onFailure { throwable ->
                                        externalDocumentFlowInProgress = false
                                        backupStatusMessage = "Unable to open export dialog: ${throwable.localizedMessage ?: "unknown error"}"
                                    }
                                },
                                onRestoreFromDrive = {
                                    backupStatusMessage = "Select a backup file to import."
                                    externalDocumentFlowInProgress = true
                                    runCatching {
                                        importBackupLauncher.launch(
                                            arrayOf(
                                                "application/json",
                                                "application/octet-stream",
                                                "text/plain",
                                                "*/*"
                                            )
                                        )
                                    }.onFailure { throwable ->
                                        externalDocumentFlowInProgress = false
                                        backupStatusMessage = "Unable to open import dialog: ${throwable.localizedMessage ?: "unknown error"}"
                                    }
                                },
                                onDriveBackup = { launchDriveAction("backup") },
                                onDriveRestore = { launchDriveAction("restore") },
                                isDriveOperationInProgress = driveOperationInProgress,
                                driveBackupStatusMessage = driveStatusMessage,
                                onLogout = {
                                    coroutineScope.launch {
                                        profileRepository.signOut()
                                        GoogleSignInHelper.signOut(applicationContext)
                                        LocalIdentityRepository.resetIdentity(applicationContext)
                                        settingsRepository.reloadForCurrentUser()
                                        mainViewModel.reinitialize()
                                        profileRepository.observeCurrentUserProfile()
                                        navController.popBackStack()
                                    }
                                },
                                onOpenCustomer = { customerId ->
                                    navController.navigate("customerDetail/$customerId")
                                },
                                onOpenAccount = { accountId ->
                                    navController.navigate("accountDetail/$accountId")
                                }
                            )
                        }

                        composable("addTransaction") {
                            AddTransactionScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onCustomerAdded = { customerId ->
                                    navController.navigate("customerDetail/$customerId") {
                                        popUpTo("addTransaction") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("addPayment") {
                            AddPaymentScreen(
                                selectedAccountIds = settingsState.selectedAccountIds
                            ) {
                                navController.popBackStack()
                            }
                        }

                        composable("customerDetail/{customerId}") { backStackEntry ->
                            val customerId = backStackEntry.arguments?.getString("customerId").orEmpty()
                            CustomerDetailScreen(
                                customerId = customerId,
                                selectedAccountIds = settingsState.selectedAccountIds,
                                vm = mainViewModel,
                                onBack = { navController.popBackStack() },
                                onOpenSavings = { id -> navController.navigate("customerSavings/$id") }
                            )
                        }

                        composable("customerSavings/{customerId}") { backStackEntry ->
                            val customerId = backStackEntry.arguments?.getString("customerId").orEmpty()
                            CustomerSavingsScreen(
                                customerId = customerId,
                                vm = mainViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("accountDetail/{accountId}") { backStackEntry ->
                            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
                            AccountDetailScreen(
                                accountId = accountId,
                                vm = mainViewModel,
                                onBack = { navController.popBackStack() },
                                onOpenCustomer = { customerId ->
                                    navController.navigate("customerDetail/$customerId")
                                }
                            )
                        }

                        composable("profile") {
                            ProfileSetupScreen(
                                profile = profile,
                                onSave = { displayName, businessName, email, photoUrl ->
                                    coroutineScope.launch {
                                        profileRepository.saveProfile(displayName, businessName, email, photoUrl)
                                        navController.popBackStack()
                                    }
                                },
                                onSignInWithGoogle = {
                                    profileGoogleSignInInProgress = true
                                    pendingProfileCallback = { name, _, email, photo ->
                                        coroutineScope.launch {
                                            profileRepository.saveProfile(name, profile?.businessName.orEmpty(), email, photo)
                                        }
                                    }
                                    GoogleSignInHelper.buildClient(applicationContext).signOut()
                                        .addOnCompleteListener {
                                            profileGoogleSignInLauncher.launch(
                                                GoogleSignInHelper.signInIntent(applicationContext)
                                            )
                                        }
                                },
                                googleSignInInProgress = profileGoogleSignInInProgress || loginRestoreInProgress,
                                loginRestoreInProgress = loginRestoreInProgress
                            )
                        }

                        composable("securitySetup") {
                            if (securityState.hasPasscode) {
                                ChangePasscodeScreen(
                                    biometricAvailable = biometricAvailable,
                                    biometricEnabled = securityState.biometricEnabled,
                                    currentRecoveryQuestion = securityState.recoveryQuestion,
                                    onSave = { currentPasscode, newPasscode, recoveryQuestion, recoveryAnswer, enableBiometric ->
                                        val updated = securityRepository.updatePasscode(
                                            currentPasscode = currentPasscode,
                                            newPasscode = newPasscode,
                                            recoveryQuestion = recoveryQuestion,
                                            recoveryAnswer = recoveryAnswer
                                        )
                                        if (updated) {
                                            securityRepository.setBiometricEnabled(enableBiometric)
                                            securityRepository.unlock()
                                            navController.popBackStack()
                                        }
                                        updated
                                    }
                                )
                            } else {
                                SecuritySetupScreen(
                                    biometricAvailable = biometricAvailable,
                                    initialRecoveryQuestion = profile?.email?.takeIf { it.isNotBlank() }?.let {
                                        "What is your email ID?"
                                    }.orEmpty(),
                                    onSave = { passcode, recoveryQuestion, recoveryAnswer, enableBiometric ->
                                        securityRepository.setPasscode(
                                            passcode = passcode,
                                            recoveryQuestion = recoveryQuestion,
                                            recoveryAnswer = recoveryAnswer
                                        )
                                        securityRepository.setBiometricEnabled(enableBiometric)
                                        securityRepository.unlock()
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }

                        composable("settings") {
                            SettingsScreen(
                                settingsState = settingsState,
                                profile = profile,
                                securityState = securityState,
                                lockedAccountIds = lockedAccountIds,
                                backupStatusMessage = backupStatusMessage,
                                isBackupOperationInProgress = backupOperationInProgress,
                                lastDriveBackupTime = settingsState.lastDriveBackupTime,
                                lastDriveRestoreTime = settingsState.lastDriveRestoreTime,
                                onThemeModeSelected = settingsRepository::setThemeMode,
                                onAccountSelectionChanged = settingsRepository::setAccountSelected,
                                onLockEnabledChanged = securityRepository::setLockEnabled,
                                onBiometricEnabledChanged = securityRepository::setBiometricEnabled,
                                onEditProfile = { navController.navigate("profile") },
                                onOpenSecuritySetup = { navController.navigate("securitySetup") },
                                onBackupToDrive = {
                                    backupStatusMessage = "Choose where to save your backup."
                                    externalDocumentFlowInProgress = true
                                    runCatching {
                                        exportBackupLauncher.launch(defaultBackupFileName())
                                    }.onFailure { throwable ->
                                        externalDocumentFlowInProgress = false
                                        backupStatusMessage = "Unable to open export dialog: ${throwable.localizedMessage ?: "unknown error"}"
                                    }
                                },
                                onRestoreFromDrive = {
                                    backupStatusMessage = "Select a backup file to import."
                                    externalDocumentFlowInProgress = true
                                    runCatching {
                                        importBackupLauncher.launch(
                                            arrayOf(
                                                "application/json",
                                                "application/octet-stream",
                                                "text/plain",
                                                "*/*"
                                            )
                                        )
                                    }.onFailure { throwable ->
                                        externalDocumentFlowInProgress = false
                                        backupStatusMessage = "Unable to open import dialog: ${throwable.localizedMessage ?: "unknown error"}"
                                    }
                                },
                                onDriveBackup = { launchDriveAction("backup") },
                                onDriveRestore = { launchDriveAction("restore") },
                                isDriveOperationInProgress = driveOperationInProgress,
                                driveBackupStatusMessage = driveStatusMessage,
                                onLogout = {
                                    coroutineScope.launch {
                                        profileRepository.signOut()
                                        GoogleSignInHelper.signOut(applicationContext)
                                        LocalIdentityRepository.resetIdentity(applicationContext)
                                        settingsRepository.reloadForCurrentUser()
                                        mainViewModel.reinitialize()
                                        profileRepository.observeCurrentUserProfile()
                                        navController.popBackStack()
                                    }
                                }
                            ) {
                                navController.popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

// hasLedgerActivity is defined as an extension on CardSummary in Models.kt
