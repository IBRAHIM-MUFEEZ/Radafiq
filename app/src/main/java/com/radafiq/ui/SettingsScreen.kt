package com.radafiq.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radafiq.data.profile.UserProfile
import com.radafiq.data.security.AppSecurityState
import com.radafiq.data.settings.AppSettingsState
import com.radafiq.data.settings.AppThemeMode

@Composable
fun SettingsContent(
    settingsState: AppSettingsState,
    profile: UserProfile?,
    securityState: AppSecurityState,
    lockedAccountIds: Set<String>,
    backupStatusMessage: String,
    isBackupOperationInProgress: Boolean,
    lastDriveBackupTime: String?,
    lastDriveRestoreTime: String?,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onAccountSelectionChanged: (String, Boolean) -> Unit,
    onLockEnabledChanged: (Boolean) -> Unit,
    onBiometricEnabledChanged: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onOpenSecuritySetup: () -> Unit,
    onBackupToDrive: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
    isDriveOperationInProgress: Boolean,
    driveBackupStatusMessage: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backupStatusColor = when {
        backupStatusMessage.contains("failed", ignoreCase = true) ||
            backupStatusMessage.contains("error", ignoreCase = true) ||
            backupStatusMessage.contains("unable", ignoreCase = true) -> MaterialTheme.colorScheme.error

        else -> MaterialTheme.colorScheme.primary
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                title = "Settings",
                subtitle = "Manage profile, security, local backups, and the account configuration used across the app."
            )
        }

        item {
                    FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = profile?.displayName?.ifBlank { "Profile not set up" } ?: "Profile not set up",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = profile?.businessName?.ifBlank { "Add your business details" }
                                ?: "Add your business details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onEditProfile,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text("Edit Profile")
                        }
                    }
                }

                item {
                    FlowCard(accentColor = MaterialTheme.colorScheme.secondary) {
                        Text(
                            text = "Security",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        SecurityToggleRow(
                            title = "App lock",
                            subtitle = if (securityState.hasPasscode) {
                                "Require a passcode when the app is reopened."
                            } else {
                                "Create a passcode to enable app lock."
                            },
                            checked = securityState.lockEnabled,
                            enabled = securityState.hasPasscode,
                            onCheckedChange = onLockEnabledChanged
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SecurityToggleRow(
                            title = "Fingerprint / Face unlock",
                            subtitle = if (securityState.hasPasscode) {
                                "Allow biometric unlock in addition to the passcode."
                            } else {
                                "Biometrics become available after you create a passcode."
                            },
                            checked = securityState.biometricEnabled,
                            enabled = securityState.hasPasscode,
                            onCheckedChange = onBiometricEnabledChanged
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onOpenSecuritySetup,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(if (securityState.hasPasscode) "Change Passcode" else "Set Passcode")
                        }

                        Text(
                            text = if (securityState.hasRecoveryQuestion) {
                                "Forgot passcode works only through your saved recovery question: ${securityState.recoveryQuestion}"
                            } else {
                                "Add a mandatory recovery question when you set or change your passcode. Forgot-passcode recovery stays unavailable until then."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                item {
                    FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            text = "Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Export your profile, settings, and ledger data to a JSON file, then import it anytime on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (backupStatusMessage.isNotBlank()) {
                            Text(
                                text = backupStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = backupStatusColor,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ResponsiveTwoPane(
                            first = { itemModifier ->
                                Button(
                                    onClick = onBackupToDrive,
                                    enabled = !isBackupOperationInProgress,
                                    modifier = itemModifier
                                ) {
                                    Text(if (isBackupOperationInProgress) "Please Wait" else "Export Backup")
                                }
                            },
                            second = { itemModifier ->
                                OutlinedButton(
                                    onClick = onRestoreFromDrive,
                                    enabled = !isBackupOperationInProgress,
                                    modifier = itemModifier
                                ) {
                                    Text(if (isBackupOperationInProgress) "Please Wait" else "Import Backup")
                                }
                            }
                        )
                    }
                }

                item {
                    FlowCard(accentColor = MaterialTheme.colorScheme.secondary) {
                        Text(
                            text = "Google Drive Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your data is automatically backed up to Google Drive after every change. You can also back up or restore manually.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AccentValueRow(
                            label = "Latest backup",
                            value = lastDriveBackupTime ?: "Not yet",
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AccentValueRow(
                            label = "Latest restore",
                            value = lastDriveRestoreTime ?: "Not yet",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (driveBackupStatusMessage.isNotBlank()) {
                            val driveStatusColor = if (
                                driveBackupStatusMessage.contains("failed", ignoreCase = true) ||
                                driveBackupStatusMessage.contains("error", ignoreCase = true)
                            ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            Text(
                                text = driveBackupStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = driveStatusColor,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ResponsiveTwoPane(
                            first = { itemModifier ->
                                Button(
                                    onClick = onDriveBackup,
                                    enabled = !isDriveOperationInProgress,
                                    modifier = itemModifier
                                ) {
                                    Text(if (isDriveOperationInProgress) "Please Wait" else "Backup to Drive")
                                }
                            },
                            second = { itemModifier ->
                                OutlinedButton(
                                    onClick = onDriveRestore,
                                    enabled = !isDriveOperationInProgress,
                                    modifier = itemModifier
                                ) {
                                    Text(if (isDriveOperationInProgress) "Please Wait" else "Restore from Drive")
                                }
                            }
                        )
                    }
                }

                item {
                    FlowCard(accentColor = MaterialTheme.colorScheme.secondary) {
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ResponsiveTwoPane(
                            first = { itemModifier ->
                                ThemeModeButton(
                                    label = AppThemeMode.LIGHT.label,
                                    selected = settingsState.themeMode == AppThemeMode.LIGHT,
                                    onClick = { onThemeModeSelected(AppThemeMode.LIGHT) },
                                    modifier = itemModifier
                                )
                            },
                            second = { itemModifier ->
                                ThemeModeButton(
                                    label = AppThemeMode.DARK.label,
                                    selected = settingsState.themeMode == AppThemeMode.DARK,
                                    onClick = { onThemeModeSelected(AppThemeMode.DARK) },
                                    modifier = itemModifier
                                )
                            }
                        )
                    }
                }

                item {
                    var showLogoutConfirm by remember { mutableStateOf(false) }

                    FlowCard(accentColor = MaterialTheme.colorScheme.error) {
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sign out and return to the profile setup screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showLogoutConfirm = true },
                            modifier = Modifier.wrapContentWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        ) {
                            Text("Sign Out")
                        }
                    }

                    if (showLogoutConfirm) {
                        AlertDialog(
                            onDismissRequest = { showLogoutConfirm = false },
                            title = { Text("Sign out?") },
                            text = { Text("You'll be taken back to the profile setup screen. Your data stays saved.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showLogoutConfirm = false
                                        onLogout()
                                    }
                                ) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLogoutConfirm = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsState: AppSettingsState,
    profile: UserProfile?,
    securityState: AppSecurityState,
    lockedAccountIds: Set<String>,
    backupStatusMessage: String,
    isBackupOperationInProgress: Boolean,
    lastDriveBackupTime: String?,
    lastDriveRestoreTime: String?,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onAccountSelectionChanged: (String, Boolean) -> Unit,
    onLockEnabledChanged: (Boolean) -> Unit,
    onBiometricEnabledChanged: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onOpenSecuritySetup: () -> Unit,
    onBackupToDrive: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
    isDriveOperationInProgress: Boolean,
    driveBackupStatusMessage: String,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    RadafiqBackground {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { paddingValues ->
            SettingsContent(
                settingsState = settingsState,
                profile = profile,
                securityState = securityState,
                lockedAccountIds = lockedAccountIds,
                backupStatusMessage = backupStatusMessage,
                isBackupOperationInProgress = isBackupOperationInProgress,
                lastDriveBackupTime = lastDriveBackupTime,
                lastDriveRestoreTime = lastDriveRestoreTime,
                onThemeModeSelected = onThemeModeSelected,
                onAccountSelectionChanged = onAccountSelectionChanged,
                onLockEnabledChanged = onLockEnabledChanged,
                onBiometricEnabledChanged = onBiometricEnabledChanged,
                onEditProfile = onEditProfile,
                onOpenSecuritySetup = onOpenSecuritySetup,
                onBackupToDrive = onBackupToDrive,
                onRestoreFromDrive = onRestoreFromDrive,
                onDriveBackup = onDriveBackup,
                onDriveRestore = onDriveRestore,
                isDriveOperationInProgress = isDriveOperationInProgress,
                driveBackupStatusMessage = driveBackupStatusMessage,
                onLogout = onLogout,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun SecurityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(22.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun ThemeModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        label = "theme-mode-container"
    )
    val contentColor = animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "theme-mode-content"
    )
    val borderColor = animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
        },
        label = "theme-mode-border"
    )

    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor.value,
            contentColor = contentColor.value
        ),
        border = BorderStroke(1.dp, borderColor.value),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(label)
    }
}
