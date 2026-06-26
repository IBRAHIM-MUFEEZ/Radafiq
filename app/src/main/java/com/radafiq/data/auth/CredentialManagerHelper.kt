package com.radafiq.data.auth

import android.accounts.AccountManager
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object CredentialManagerHelper {

    private const val WEB_CLIENT_ID =
        "1036438568871-h04rt7h9gqqcmfiodi3liku0bjll1gm7.apps.googleusercontent.com"

    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"

    /** Tracks the email of the last account authenticated via [signIn],
     * so [fetchDriveToken] can look up the correct Drive account on
     * multi-account devices. */
    var lastSignedInAccountEmail: String? = null
        private set

    data class SignInResult(
        val idToken: String,
        val displayName: String,
        val email: String,
        val photoUrl: String,
        val firebaseUid: String?
    )

    /** Signs in via Credential Manager, signs into Firebase Auth, returns combined result. */
    suspend fun signIn(context: Context): Result<SignInResult> = runCatching {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(context, request)
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val idToken = credential.idToken

        val firebaseUid = withContext(Dispatchers.IO) {
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await().user?.uid
        }

        SignInResult(
            idToken = idToken,
            displayName = credential.displayName ?: "",
            email = credential.id ?: "",
            photoUrl = credential.profilePictureUri?.toString() ?: "",
            firebaseUid = firebaseUid
        )
    }.also { result ->
        result.onSuccess { lastSignedInAccountEmail = it.email }
    }

    /** Fetches a Google Drive OAuth token. Used for user-initiated Drive ops.
     *  Prefers the account matching [accountEmail] or [lastSignedInAccountEmail]
     *  so the correct Drive account is used on multi-account devices. */
    @Suppress("DEPRECATION")
    suspend fun fetchDriveToken(context: Context, accountEmail: String? = null): String? = withContext(Dispatchers.IO) {
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType("com.google")
        // Try the explicit email first, then the last signed-in email, then the first account
        val preferredEmail = accountEmail ?: lastSignedInAccountEmail
        val googleAccount = if (preferredEmail != null) {
            accounts.firstOrNull { it.name.equals(preferredEmail, ignoreCase = true) }
                ?: accounts.firstOrNull()
        } else {
            accounts.firstOrNull()
        } ?: return@withContext null
        GoogleAuthUtil.getToken(context, googleAccount, DRIVE_SCOPE)
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }
}
