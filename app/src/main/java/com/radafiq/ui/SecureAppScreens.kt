package com.radafiq.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radafiq.data.profile.UserProfile

private val RecoveryQuestions = listOf(
    "What is your email ID?",
    "What was your first pet's name?",
    "What city were you born in?",
    "What is your mother's first name?"
)

@Composable
fun ProfileSetupScreen(
    profile: UserProfile?,
    onSave: (displayName: String, businessName: String, email: String, photoUrl: String) -> Unit,
    onSignInWithGoogle: (() -> Unit)? = null,
    googleSignInInProgress: Boolean = false,
    loginRestoreInProgress: Boolean = false,
    signInErrorMessage: String = ""
) {
    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var businessName by remember(profile?.businessName) { mutableStateOf(profile?.businessName.orEmpty()) }
    var email by remember(profile?.email) { mutableStateOf(profile?.email.orEmpty()) }
    var photoUrl by remember(profile?.photoUrl) { mutableStateOf(profile?.photoUrl.orEmpty()) }

    RadafiqBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            PageHeader(
                title = "Set Up Profile",
                subtitle = "Sign in with Google to connect your account, auto-fill your profile, and instantly restore your data from Google Drive."
            )

            // Google Sign-In card
            if (onSignInWithGoogle != null) {
                val isSignedIn = !profile?.email.isNullOrBlank()
                FlowCard(
                    accentColor = if (isSignedIn) MaterialTheme.colorScheme.secondary
                                  else MaterialTheme.colorScheme.primary
                ) {
                    if (isSignedIn) {
                        // Already signed in — show account info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Signed in with Google",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = profile?.email.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        // Not signed in — show sign-in prompt
                        Text(
                            text = if (loginRestoreInProgress) "Restoring your data..." else "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (loginRestoreInProgress) {
                                "Fetching your latest backup from Google Drive. This may take a moment."
                            } else {
                                "One tap to sign in, connect Google Drive, and restore your latest backup automatically."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (loginRestoreInProgress) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                            )
                        } else {
                            Button(
                                onClick = onSignInWithGoogle,
                                enabled = !googleSignInInProgress,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (googleSignInInProgress) "Signing in..." else "Continue with Google")
                            }
                            // Show sign-in error beneath the button so the user knows what went wrong
                            if (signInErrorMessage.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = signInErrorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            FlowCard(accentColor = MaterialTheme.colorScheme.secondary) {
                // Profile photo
                if (photoUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        coil.compose.AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "Profile details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business / Shop Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    // FIX-4: Show error when email format is invalid
                    isError = email.isNotBlank() &&
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches(),
                    supportingText = if (email.isNotBlank() &&
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                    ) {
                        { Text("Enter a valid email address") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onSave(displayName, businessName, email, photoUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = displayName.isNotBlank() && businessName.isNotBlank() &&
                        (email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
                ) {
                    Text("Save Profile")
                }
            }
        }
    }
}

@Composable
fun SecuritySetupScreen(
    biometricAvailable: Boolean,
    initialRecoveryQuestion: String = RecoveryQuestions.first(),
    onSave: (
        passcode: String,
        recoveryQuestion: String,
        recoveryAnswer: String,
        enableBiometric: Boolean
    ) -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var confirmPasscode by remember { mutableStateOf("") }
    val availableQuestions = remember(initialRecoveryQuestion) {
        (listOf(initialRecoveryQuestion).filter { it.isNotBlank() } + RecoveryQuestions).distinct()
    }
    var selectedRecoveryQuestion by remember(initialRecoveryQuestion) {
        mutableStateOf(availableQuestions.firstOrNull().orEmpty())
    }
    var recoveryAnswer by remember { mutableStateOf("") }
    var useBiometric by remember(biometricAvailable) { mutableStateOf(biometricAvailable) }

    val passcodesMatch = passcode.length == 6 && passcode == confirmPasscode
    val hasRecoveryDetails = selectedRecoveryQuestion.isNotBlank() && recoveryAnswer.trim().length >= 3

    RadafiqBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            PageHeader(
                title = "Protect the App",
                subtitle = "Add a passcode, choose a mandatory recovery question, and optionally enable fingerprint or face unlock for every launch."
            )

            FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it.filter(Char::isDigit).take(6) },
                    label = { Text("Create Passcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPasscode,
                    onValueChange = { confirmPasscode = it.filter(Char::isDigit).take(6) },
                    label = { Text("Confirm Passcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                RecoveryQuestionDropdown(
                    questions = availableQuestions,
                    selectedQuestion = selectedRecoveryQuestion,
                    onQuestionSelected = { selectedRecoveryQuestion = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = recoveryAnswer,
                    onValueChange = { recoveryAnswer = it },
                    label = { Text("Recovery Answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Forgot passcode recovery works only through this answer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use fingerprint / face unlock",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (biometricAvailable) {
                                "Biometric unlock is available on this device."
                            } else {
                                "Biometric unlock is not available on this device."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useBiometric && biometricAvailable,
                        onCheckedChange = { useBiometric = it },
                        enabled = biometricAvailable
                    )
                }

                if (passcode.isNotBlank() && !passcodesMatch) {
                    Text(
                        text = "Passcodes must match and contain exactly 6 digits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSave(
                            passcode,
                            selectedRecoveryQuestion,
                            recoveryAnswer,
                            useBiometric && biometricAvailable
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = passcodesMatch && hasRecoveryDetails
                ) {
                    Text("Save Security Setup")
                }
            }
        }
    }
}

@Composable
fun ChangePasscodeScreen(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    currentRecoveryQuestion: String,
    onSave: (
        currentPasscode: String,
        newPasscode: String,
        recoveryQuestion: String,
        recoveryAnswer: String,
        enableBiometric: Boolean
    ) -> Boolean
) {
    var currentPasscode by remember { mutableStateOf("") }
    var newPasscode by remember { mutableStateOf("") }
    var confirmPasscode by remember { mutableStateOf("") }
    val availableQuestions = remember(currentRecoveryQuestion) {
        (listOf(currentRecoveryQuestion).filter { it.isNotBlank() } + RecoveryQuestions).distinct()
    }
    var selectedRecoveryQuestion by remember(currentRecoveryQuestion) {
        mutableStateOf(availableQuestions.firstOrNull().orEmpty())
    }
    var recoveryAnswer by remember { mutableStateOf("") }
    var useBiometric by remember(biometricAvailable, biometricEnabled) {
        mutableStateOf(biometricEnabled && biometricAvailable)
    }
    var localError by remember { mutableStateOf("") }

    val passcodesMatch = newPasscode.length == 6 && newPasscode == confirmPasscode
    val canSave = currentPasscode.isNotBlank() &&
        passcodesMatch &&
        selectedRecoveryQuestion.isNotBlank() &&
        recoveryAnswer.trim().length >= 3

    RadafiqBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            PageHeader(
                title = "Change Passcode",
                subtitle = "Enter the existing passcode first, then save a new passcode and mandatory recovery question details."
            )

            FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
                OutlinedTextField(
                    value = currentPasscode,
                    onValueChange = {
                        currentPasscode = it.filter(Char::isDigit).take(6)
                        localError = ""
                    },
                    label = { Text("Existing Passcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPasscode,
                    onValueChange = {
                        newPasscode = it.filter(Char::isDigit).take(6)
                        localError = ""
                    },
                    label = { Text("New Passcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPasscode,
                    onValueChange = {
                        confirmPasscode = it.filter(Char::isDigit).take(6)
                        localError = ""
                    },
                    label = { Text("Confirm New Passcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                RecoveryQuestionDropdown(
                    questions = availableQuestions,
                    selectedQuestion = selectedRecoveryQuestion,
                    onQuestionSelected = {
                        selectedRecoveryQuestion = it
                        localError = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = recoveryAnswer,
                    onValueChange = {
                        recoveryAnswer = it
                        localError = ""
                    },
                    label = { Text("Recovery Answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Forgot passcode recovery works only from the lock screen by answering this question.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use fingerprint / face unlock",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (biometricAvailable) {
                                "Biometric unlock is available on this device."
                            } else {
                                "Biometric unlock is not available on this device."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useBiometric && biometricAvailable,
                        onCheckedChange = { useBiometric = it },
                        enabled = biometricAvailable
                    )
                }

                if (newPasscode.isNotBlank() && !passcodesMatch) {
                    Text(
                        text = "New passcodes must match and contain exactly 6 digits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                if (localError.isNotBlank()) {
                    Text(
                        text = localError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val updated = onSave(
                            currentPasscode,
                            newPasscode,
                            selectedRecoveryQuestion,
                            recoveryAnswer,
                            useBiometric && biometricAvailable
                        )
                        if (!updated) {
                            localError = "Existing passcode is incorrect."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave
                ) {
                    Text("Update Passcode")
                }
            }
        }
    }
}

@Composable
fun AppLockScreen(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    recoveryQuestion: String,
    errorMessage: String,
    onUnlockWithPasscode: (String) -> Boolean,
    onUnlockWithBiometric: (() -> Unit)?,
    onBiometricFailed: (() -> Unit)? = null,
    onResetWithRecovery: ((recoveryAnswer: String, newPasscode: String, enableBiometric: Boolean) -> Boolean)? = null
) {
    var passcode by remember { mutableStateOf("") }
    var showRecoveryFlow by remember(recoveryQuestion) { mutableStateOf(false) }
    var recoveryAnswer by remember { mutableStateOf("") }
    var newPasscode by remember { mutableStateOf("") }
    var confirmNewPasscode by remember { mutableStateOf("") }
    var useBiometric by remember(biometricAvailable, biometricEnabled) {
        mutableStateOf(biometricAvailable && biometricEnabled)
    }
    var localError by remember { mutableStateOf("") }
    // false = biometric prompt shown first; true = PIN entry shown (after biometric dismissed/failed)
    var showPinEntry by remember { mutableStateOf(false) }
    val recoveryAvailable = recoveryQuestion.isNotBlank() && onResetWithRecovery != null
    val recoveryPasscodesMatch = newPasscode.length == 6 && newPasscode == confirmNewPasscode
    val canUseBiometric = biometricEnabled && biometricAvailable && onUnlockWithBiometric != null
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Auto-trigger biometric on first composition
    LaunchedEffect(Unit) {
        if (canUseBiometric) {
            onUnlockWithBiometric?.invoke()
        } else {
            showPinEntry = true
        }
    }

    // When parent signals biometric failed, switch to PIN
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotBlank() && !showPinEntry) {
            showPinEntry = true
        }
    }

    RadafiqBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .clickable(
                    enabled = showPinEntry && !showRecoveryFlow,
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Radafiq logo from assets
            coil.compose.AsyncImage(
                model = "file:///android_asset/logo-Photoroom.png",
                contentDescription = "Radafiq Logo",
                modifier = Modifier
                    .size(112.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Radafiq Locked",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (showRecoveryFlow) {
                    "Answer your saved recovery question to reset the passcode."
                } else if (!showPinEntry && canUseBiometric) {
                    "Verify with biometrics to continue."
                } else {
                    "Enter your passcode to continue."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            FlowCard(
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showRecoveryFlow) {
                    Text(
                        text = recoveryQuestion,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = recoveryAnswer,
                        onValueChange = {
                            recoveryAnswer = it
                            localError = ""
                        },
                        label = { Text("Recovery Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPasscode,
                        onValueChange = {
                            newPasscode = it.filter(Char::isDigit).take(6)
                            localError = ""
                        },
                        label = { Text("New Passcode") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmNewPasscode,
                        onValueChange = {
                            confirmNewPasscode = it.filter(Char::isDigit).take(6)
                            localError = ""
                        },
                        label = { Text("Confirm New Passcode") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (biometricAvailable) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use fingerprint / face unlock",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Enable biometric unlock after the reset completes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useBiometric,
                                onCheckedChange = { useBiometric = it }
                            )
                        }
                    }

                    if (newPasscode.isNotBlank() && !recoveryPasscodesMatch) {
                        Text(
                            text = "New passcodes must match and contain exactly 6 digits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val reset = onResetWithRecovery?.invoke(
                                recoveryAnswer,
                                newPasscode,
                                useBiometric && biometricAvailable
                            ) == true
                            if (!reset) {
                                localError = "Recovery answer is incorrect."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = recoveryAnswer.trim().length >= 3 && recoveryPasscodesMatch
                    ) {
                        Text("Reset Passcode")
                    }
                    TextButton(
                        onClick = {
                            showRecoveryFlow = false
                            localError = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to Unlock")
                    }
                } else {
                    if (!showPinEntry && canUseBiometric) {
                        // Biometric pending — show fingerprint icon + option to use PIN instead
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric unlock",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                    ) {
                                        passcode = ""
                                        localError = ""
                                        onUnlockWithBiometric?.invoke()
                                    },
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                showPinEntry = true
                                localError = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use PIN instead")
                        }
                    } else {
                        // PIN entry
                        val focusRequester = remember { FocusRequester() }

                        LaunchedEffect(showPinEntry) {
                            if (showPinEntry) {
                                try {
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                } catch (_: Exception) {}
                            }
                        }

                        LaunchedEffect(passcode) {
                            if (passcode.length == 6) {
                                val unlocked = onUnlockWithPasscode(passcode)
                                if (!unlocked && passcode.length == 6) {
                                    // FIX-2: Show lockout message if the account is locked out
                                    localError = "Incorrect passcode."
                                    passcode = ""
                                }
                            }
                        }

                        // Invisible BasicTextField — 1.dp so it stays in layout tree, alpha 0 so it's hidden
                        BasicTextField(
                            value = passcode,
                            onValueChange = {
                                passcode = it.filter { c -> c.isDigit() }.take(6)
                                localError = ""
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .size(1.dp)
                                .alpha(0f)
                                .focusRequester(focusRequester)
                        )

                        // Clickable bullet dots — tap to re-focus and show keyboard
                        // Tap anywhere outside dots hides keyboard
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) {
                                    keyboardController?.hide()
                                }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(6) { index ->
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp)
                                            .size(16.dp)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                            ) {
                                                try {
                                                    focusRequester.requestFocus()
                                                    keyboardController?.show()
                                                } catch (_: Exception) {}
                                            }
                                            .background(
                                                color = if (index < passcode.length)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }

                        if (canUseBiometric) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Use biometrics",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                        ) {
                                            passcode = ""
                                            localError = ""
                                            onUnlockWithBiometric?.invoke()
                                        },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (recoveryAvailable) {
                        TextButton(
                            onClick = {
                                showRecoveryFlow = true
                                localError = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Forgot Passcode?")
                        }
                    }
                }

                val message = localError.ifBlank {
                    if (showRecoveryFlow) "" else errorMessage
                }
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryQuestionDropdown(
    questions: List<String>,
    selectedQuestion: String,
    onQuestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedQuestion,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Recovery Question") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            questions.forEach { question ->
                DropdownMenuItem(
                    text = { Text(question) },
                    onClick = {
                        expanded = false
                        onQuestionSelected(question)
                    }
                )
            }
        }
    }
}
