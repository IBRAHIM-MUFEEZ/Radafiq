package com.radafiq

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.radafiq.data.auth.CredentialManagerHelper
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
import com.radafiq.ui.ShimmerLoadingScreen
import com.radafiq.viewmodel.MainViewModel
import com.radafiq.data.ConnectivityMonitor
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : FragmentActivity() {
    private var externalDocumentFlowInProgress = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            AppRoot()
        }
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
        val activityContext = LocalContext.current

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

        // Connectivity monitor for offline banner
        val connectivityMonitor = remember { ConnectivityMonitor(applicationContext) }
        val isOnline by connectivityMonitor.isOnline.collectAsState(initial = true)

        var backupStatusMessage by rememberSaveable { mutableStateOf("") }
        var backupOperationInProgress by rememberSaveable { mutableStateOf(false) }
        var lockErrorMessage by rememberSaveable { mutableStateOf("") }
        // Tracks whether the user has unlocked in this session — survives activity
        // recreation (e.g. during long-screenshot save) so the lock screen isn't
        // shown again until the app is intentionally background-locked.
        var sessionUnlocked by rememberSaveable { mutableStateOf(false) }
        var pendingExplicitLock by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(securityState.isUnlocked) {
            if (securityState.isUnlocked) {
                sessionUnlocked = true
                pendingExplicitLock = false
            }
        }

        val onLock: () -> Unit = {
            securityRepository.lock()
            sessionUnlocked = false
            pendingExplicitLock = true
        }

        // Google Drive backup state
        var driveStatusMessage by rememberSaveable { mutableStateOf("") }
        var driveOperationInProgress by rememberSaveable { mutableStateOf(false) }
        // Tracks what action to perform after sign-in: "backup" or "restore"
        var pendingDriveAction by rememberSaveable { mutableStateOf("") }
        val driveBackupRepository = remember { DriveBackupRepository() }

        // Google Sign-In for profile setup
        var profileGoogleSignInInProgress by remember { mutableStateOf(false) }
        var pendingProfileCallback by remember { mutableStateOf<((String, String, String, String) -> Unit)?>(null) }
        // Shows a "Restoring from Drive..." overlay after sign-in
        var loginRestoreInProgress by rememberSaveable { mutableStateOf(false) }
        // Error message shown on the profile setup screen (e.g. sign-in failed)
        var profileSignInErrorMessage by remember { mutableStateOf("") }
        suspend fun signInWithGoogle(onSuccess: suspend (CredentialManagerHelper.SignInResult) -> Unit) {
            profileGoogleSignInInProgress = true
            profileSignInErrorMessage = ""
            val result = CredentialManagerHelper.signIn(activityContext)
            result.onSuccess { signInResult ->
                val email = signInResult.email
                if (email.isBlank()) {
                    android.util.Log.w("MainActivity", "Credential Manager returned blank email")
                    profileGoogleSignInInProgress = false
                    profileSignInErrorMessage = "Sign-in returned no email address. Please try again."
                    return@onSuccess
                }
                profileSignInErrorMessage = ""
                loginRestoreInProgress = true
                profileGoogleSignInInProgress = false

                val firebaseUid = signInResult.firebaseUid
                if (!firebaseUid.isNullOrBlank()) {
                    LocalIdentityRepository.setIdentityFromFirebaseUid(firebaseUid, applicationContext)
                } else {
                    profileSignInErrorMessage =
                        "Google sign-in could not connect to Firebase. Please install a build signed with a registered SHA-1 certificate."
                    pendingProfileCallback = null
                    loginRestoreInProgress = false
                    return@onSuccess
                }

                settingsRepository.reloadForCurrentUser()
                mainViewModel.reinitialize()
                profileRepository.observeCurrentUserProfile()

                onSuccess(signInResult)
                loginRestoreInProgress = false
            }.onFailure { e ->
                profileGoogleSignInInProgress = false
                loginRestoreInProgress = false
                pendingProfileCallback = null
                val message = e.localizedMessage.orEmpty()
                profileSignInErrorMessage = when {
                    message.contains("CANCELLED", ignoreCase = true) ||
                    message.contains("CANCELED", ignoreCase = true) -> ""
                    message.contains("INTERRUPTED", ignoreCase = true) -> ""
                    message.contains("NETWORK", ignoreCase = true) ||
                    message.contains("TIMEOUT", ignoreCase = true) ||
                    message.contains("TIME_OUT", ignoreCase = true) ->
                        "Network error. Check your internet connection and try again."
                    else -> "Sign-in failed: ${message.ifBlank { "unknown error" }}"
                }
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

        // Auto-restore from Drive is handled in signInWithGoogle() via mainViewModel.restoreFromJson()

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

        // â”€â”€ Google Drive helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        suspend fun performDriveBackup(token: String) {
            driveOperationInProgress = true
            driveStatusMessage = "Uploading to Google Drive..."
            mainViewModel.updateDriveOperationMessage("Backing up to Google Drive...")
            try {
                driveStatusMessage = runCatching {
                    withTimeout(60_000L) {
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

        suspend fun performDriveRestore(token: String) {
            val keepUnlocked = securityState.isUnlocked
            driveOperationInProgress = true
            driveStatusMessage = "Downloading from Google Drive..."
            mainViewModel.updateDriveOperationMessage("Restoring from Google Drive...")
            try {
                // Step 1: download JSON with a timeout â€” this is the network-bound part
                val json = runCatching {
                    withTimeout(60_000L) {
                        withContext(Dispatchers.IO) {
                            driveBackupRepository.downloadLatestBackup(token).getOrThrow()
                        }
                    }
                }.getOrElse { e ->
                    driveStatusMessage = "Drive restore failed: ${e.localizedMessage ?: "download error"}"
                    return
                }

                // Step 2: restore into Firestore â€” no timeout, let it complete fully
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

        fun launchDriveAction(action: String) {
            if (driveOperationInProgress || pendingDriveAction.isNotBlank()) {
                return
            }
            pendingDriveAction = action
            driveOperationInProgress = true
            val msg = if (action == "backup") "Preparing Google Drive backup..." else "Preparing Google Drive restore..."
            driveStatusMessage = msg
            mainViewModel.beginDriveOperation(msg)
            coroutineScope.launch {
                try {
                    val token = CredentialManagerHelper.fetchDriveToken(applicationContext)
                    if (token != null) {
                        when (action) {
                            "backup" -> performDriveBackup(token)
                            "restore" -> performDriveRestore(token)
                        }
                    } else {
                        // No Google account on device — try signing in interactively
                        val signInResult = CredentialManagerHelper.signIn(activityContext)
                        signInResult.onSuccess { result ->
                            if (!result.firebaseUid.isNullOrBlank()) {
                                LocalIdentityRepository.setIdentityFromFirebaseUid(result.firebaseUid, applicationContext)
                                settingsRepository.reloadForCurrentUser()
                                mainViewModel.reinitialize()
                                profileRepository.observeCurrentUserProfile()
                            }
                            val driveToken = CredentialManagerHelper.fetchDriveToken(applicationContext, result.email)
                            if (driveToken != null) {
                                when (action) {
                                    "backup" -> performDriveBackup(driveToken)
                                    "restore" -> performDriveRestore(driveToken)
                                }
                            } else {
                                driveStatusMessage = "Could not obtain Drive access. Please sign in with Google again."
                            }
                        }.onFailure { e ->
                            driveStatusMessage = "Sign-in cancelled or failed: ${e.localizedMessage ?: "unknown error"}"
                        }
                    }
                } finally {
                    pendingDriveAction = ""
                    driveOperationInProgress = false
                    mainViewModel.finishDriveOperation()
                }
            }
        }

        // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
                        // App came back â€” lock if it was backgrounded for more than 1.5 minutes.
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
                            sessionUnlocked = false
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
            securityState.hasPasscode &&
            (pendingExplicitLock || (securityState.lockEnabled && (!sessionUnlocked || !securityState.isUnlocked)))

        // Snackbar state for showing transient messages
        val snackbarHostState = remember { SnackbarHostState() }

        // Function to show snackbar messages
        val showSnackbar = { message: String ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
        }

        RadafiqTheme(themeMode = settingsState.themeMode) {
            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState
                    )
                }
            ) { padding ->
                // Persistent offline banner at the top
                if (!isOnline) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "You're offline. Some features may be unavailable.",
                            color = MaterialTheme.colorScheme.onError,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                var loadingStartTime by remember { mutableStateOf(0L) }
                var animationReady by remember { mutableStateOf(true) }

                LaunchedEffect(profileState.isLoading) {
                    if (profileState.isLoading) {
                        loadingStartTime = System.nanoTime()
                        animationReady = false
                    }
                }

                LaunchedEffect(profileState.isLoading) {
                    if (!profileState.isLoading && loadingStartTime > 0L) {
                        val elapsedNs = System.nanoTime() - loadingStartTime
                        val animationDurationNs = 600_000_000L // 600ms minimum — just enough to feel intentional
                        if (elapsedNs < animationDurationNs) {
                            delay((animationDurationNs - elapsedNs) / 1_000_000)
                        }
                        animationReady = true
                    }
                }

                val screenKey = when {
                    profileState.isLoading || !animationReady -> "loading"
                    needsProfileSetup -> "profileSetup"
                    needsSecuritySetup -> "securitySetup"
                    requiresUnlock -> "lock"
                    else -> "main"
                }

                AnimatedContent(
                    targetState = screenKey,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    label = "rootTransition"
                ) { currentScreen ->
                    when (currentScreen) {
                        "loading" -> {
                            RadafiqBackground {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(padding)
                                        .consumeWindowInsets(insets = WindowInsets.systemBars),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ShimmerLoadingScreen()
                                }
                            }
                        }

                "profileSetup" -> {
                    ProfileSetupScreen(
                        profile = profile,
                        onSave = { displayName, businessName, email, photoUrl ->
                            coroutineScope.launch {
                                try {
                                    profileRepository.saveProfile(displayName, businessName, email, photoUrl)
                                } catch (e: Exception) {
                                    android.util.Log.e("SignIn", "Profile save failed", e)
                                }
                            }
                        },
                            onSignInWithGoogle = {
                            coroutineScope.launch {
                                pendingProfileCallback = { name, _, email, photo ->
                                    coroutineScope.launch {
                                        try {
                                            profileRepository.saveProfile(name, profile?.businessName.orEmpty(), email, photo)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SignIn", "Profile save after sign-in failed", e)
                                        }
                                    }
                                }
                                signInWithGoogle { signInResult ->
                                    // Restore from Drive after sign-in
                                    val token = CredentialManagerHelper.fetchDriveToken(applicationContext, signInResult.email)
                                    if (token != null) {
                                        val restoreResult = runCatching {
                                            withTimeout(60_000L) {
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
                                        }
                                        restoreResult.onFailure { e ->
                                            val message = e.localizedMessage.orEmpty()
                                            if (!message.contains("No Google Drive backup", ignoreCase = true)) {
                                                profileSignInErrorMessage =
                                                    "Signed in, but Drive restore failed: ${message.ifBlank { "network timeout" }}"
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        googleSignInInProgress = profileGoogleSignInInProgress || loginRestoreInProgress,
                        loginRestoreInProgress = loginRestoreInProgress,
                        signInErrorMessage = profileSignInErrorMessage
                    )
                }

                "securitySetup" -> {
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

                "lock" -> {
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

                "main" -> {
                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable(
                            "dashboard",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(200)) }
                        ) {
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
                                        CredentialManagerHelper.signOut()
                                        LocalIdentityRepository.resetIdentity(applicationContext)
                                        settingsRepository.reloadForCurrentUser()
                                        mainViewModel.reinitialize()
                                        profileRepository.observeCurrentUserProfile()
                                        navController.popBackStack("dashboard", false)
                                    }
                                },
                                onLock = onLock,
                                onOpenCustomer = { customerId ->
                                    navController.navigate("customerDetail/$customerId")
                                },
                                onOpenAccount = { accountId ->
                                    navController.navigate("accountDetail/$accountId")
                                }
                            )
                        }

                        composable(
                            "addTransaction",
                            enterTransition = { fadeIn(tween(300)) + slideInVertically(tween(300)) { it } },
                            exitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(200)) { it } },
                            popEnterTransition = { fadeIn(tween(300)) + slideInVertically(tween(300)) { -it } },
                            popExitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it } }
                        ) {
                            AddTransactionScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onCustomerAdded = { customerId ->
                                    navController.navigate("customerDetail/$customerId") {
                                        popUpTo("addTransaction") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            "addPayment",
                            enterTransition = { fadeIn(tween(300)) + slideInVertically(tween(300)) { it } },
                            exitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(200)) { it } },
                            popEnterTransition = { fadeIn(tween(300)) + slideInVertically(tween(300)) { -it } },
                            popExitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it } }
                        ) {
                            AddPaymentScreen(
                                selectedAccountIds = settingsState.selectedAccountIds,
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(
                            "customerDetail/{customerId}",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(200)) }
                        ) { backStackEntry ->
                            val customerId = backStackEntry.arguments?.getString("customerId").orEmpty()
                            CustomerDetailScreen(
                                customerId = customerId,
                                selectedAccountIds = settingsState.selectedAccountIds,
                                vm = mainViewModel,
                                onBack = { navController.popBackStack() },
                                onOpenSavings = { id -> navController.navigate("customerSavings/$id") },
                                snackbarHostState = snackbarHostState
                            )
                        }

                        composable(
                            "customerSavings/{customerId}",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(200)) }
                        ) { backStackEntry ->
                            val customerId = backStackEntry.arguments?.getString("customerId").orEmpty()
                            CustomerSavingsScreen(
                                customerId = customerId,
                                vm = mainViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            "accountDetail/{accountId}",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(200)) }
                        ) { backStackEntry ->
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

                        composable(
                            "profile",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(200)) }
                        ) {
                            ProfileSetupScreen(
                                profile = profile,
                                onSave = { displayName, businessName, email, photoUrl ->
                                    coroutineScope.launch {
                                        try {
                                            profileRepository.saveProfile(displayName, businessName, email, photoUrl)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SignIn", "Profile save from profile screen failed", e)
                                        }
                                        navController.popBackStack()
                                    }
                                },
                                onSignInWithGoogle = {
                                    coroutineScope.launch {
                                        pendingProfileCallback = { name, _, email, photo ->
                                            coroutineScope.launch {
                                                try {
                                                    profileRepository.saveProfile(name, profile?.businessName.orEmpty(), email, photo)
                                                } catch (e: Exception) {
                                                    android.util.Log.e("SignIn", "Profile save after sign-in failed", e)
                                                }
                                            }
                                        }
                                        signInWithGoogle { signInResult ->
                                            // Restore from Drive after sign-in (same as login flow)
                                            val token = CredentialManagerHelper.fetchDriveToken(applicationContext, signInResult.email)
                                            if (token != null) {
                                                runCatching {
                                                    withTimeout(60_000L) {
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
                                                }.onFailure { e ->
                                                    val message = e.localizedMessage.orEmpty()
                                                    if (!message.contains("No Google Drive backup", ignoreCase = true)) {
                                                        android.util.Log.w("ProfileSignIn", "Drive restore after profile sign-in failed: $message")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                googleSignInInProgress = profileGoogleSignInInProgress || loginRestoreInProgress,
                                loginRestoreInProgress = loginRestoreInProgress,
                                signInErrorMessage = profileSignInErrorMessage
                            )
                        }

                        composable(
                            "securitySetup",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(200)) }
                        ) {
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

                        composable(
                            "settings",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(200)) }
                        ) {
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
                                        CredentialManagerHelper.signOut()
                                        LocalIdentityRepository.resetIdentity(applicationContext)
                                        settingsRepository.reloadForCurrentUser()
                                        mainViewModel.reinitialize()
                                        profileRepository.observeCurrentUserProfile()
                                        navController.popBackStack("dashboard", false)
                                    }
                                },
                                onLock = onLock
                            ) {
                                navController.popBackStack("dashboard", false)
                            }
                        }
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
