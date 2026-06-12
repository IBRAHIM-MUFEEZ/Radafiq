package com.radafiq.data.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleSignInHelper {

    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private val OAUTH_SCOPE = "oauth2:$DRIVE_APPDATA_SCOPE"

    // Web client ID (client_type 3) from google-services.json
    private const val WEB_CLIENT_ID =
        "1036438568871-h04rt7h9gqqcmfiodi3liku0bjll1gm7.apps.googleusercontent.com"

    /**
     * Builds a sign-in client.
     *
     * [requestIdToken] controls whether a Firebase-Auth-compatible ID token is
     * requested alongside the sign-in.  This requires the calling app's signing
     * certificate to be registered in Firebase Console (SHA-1).
     *
     * - Release/debug builds: SHA-1 must be registered so Firebase Auth receives
     *   the same UID that owns the user's Firestore data.
     *
     * Default is true so release behaviour is unchanged.
     */
    fun buildClient(context: Context, requestIdToken: Boolean = true): GoogleSignInClient {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
        if (requestIdToken) {
            builder.requestIdToken(WEB_CLIENT_ID)
        }
        return GoogleSignIn.getClient(context, builder.build())
    }

    /**
     * Returns a sign-in Intent.  Tries with ID-token first; if the package's
     * signing certificate is not registered in Firebase Console the caller will
     * receive DEVELOPER_ERROR (code 10), which [MainActivity] shows as a
     * Firebase configuration error.
     */
    fun signInIntent(context: Context, requestIdToken: Boolean = true): Intent =
        buildClient(context, requestIdToken).signInIntent

    /** Returns the cached account only if the drive.appdata scope is already granted. */
    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, Scope(DRIVE_APPDATA_SCOPE))) account else null
    }

    /**
     * Returns the last signed-in account regardless of scope check.
     * Use for auto-backup where scope was already granted during sign-in.
     */
    fun getLastSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Signs into Firebase Auth using the Google ID token from the signed-in account.
     * Returns the Firebase UID on success, null on failure.
     *
     * Call this after a successful Google Sign-In result before doing anything else.
     * Returns null if no ID token is present (sign-in was done without requestIdToken).
     */
    suspend fun signInToFirebase(account: GoogleSignInAccount): String? {
        val idToken = account.idToken
        if (idToken.isNullOrBlank()) {
            android.util.Log.w("FirebaseAuth", "No ID token — Firebase Auth sign-in cannot continue.")
            return null
        }
        return runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = FirebaseAuth.getInstance().signInWithCredential(credential).await()
            val uid = result.user?.uid
            android.util.Log.d("FirebaseAuth", "Signed in — UID: $uid")
            uid
        }.onFailure { e ->
            android.util.Log.w("FirebaseAuth", "Firebase sign-in failed: ${e.localizedMessage}", e)
        }.getOrNull()
    }

    /**
     * Fetches a fresh OAuth Bearer token for the Drive appdata scope.
     * Must be called from a coroutine — runs on IO dispatcher internally.
     */
    suspend fun fetchAccessToken(context: Context, account: GoogleSignInAccount): String =
        withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(
                context,
                account.account ?: error("No Google account attached"),
                OAUTH_SCOPE
            )
        }

    fun signOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
        buildClient(context, requestIdToken = false).signOut()
    }
}
