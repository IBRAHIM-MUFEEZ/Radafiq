package com.radafiq.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

object LocalIdentityRepository {
    private const val PREFERENCES_NAME = "Radafiq_local_identity"
    private const val KEY_USER_ID = "local_user_id"

    @Volatile
    private var cachedUserId: String? = null

    @Volatile
    private var _prefs: SharedPreferences? = null

    /** Pre-warm the encrypted prefs cache from a background coroutine. */
    fun warmUp(context: Context) {
        if (cachedUserId != null) return // already warm
        val prefs = securePreferencesCached(context)
        cachedUserId = prefs.getString(KEY_USER_ID, null)
    }

    // FIX-16: Use EncryptedSharedPreferences to protect the user ID (Firestore path key)
    private fun securePreferencesCached(context: Context): SharedPreferences {
        _prefs?.let { return it }
        return synchronized(this) {
            _prefs ?: run {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val prefs = runCatching {
                    EncryptedSharedPreferences.create(
                        context.applicationContext,
                        PREFERENCES_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                }.getOrElse {
                    // Fallback to plain prefs if keystore is unavailable
                    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                }
                _prefs = prefs
                prefs
            }
        }
    }

    /**
     * Returns the current userId.
     * Priority: Firebase Auth UID → cached local ID → generate device UUID.
     *
     * Firebase Auth UID is used when the user is signed in with Google via Firebase Auth.
     * This is the preferred path — it enables proper Firestore security rules.
     */
    fun userId(context: Context = FirebaseApp.getInstance().applicationContext): String {
        // 1. Firebase Auth UID — most secure, use if available
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
        if (!firebaseUid.isNullOrBlank()) {
            // Keep local cache in sync so Firestore listeners use the right path
            if (cachedUserId != firebaseUid) {
                synchronized(this) {
                    securePreferencesCached(context.applicationContext)
                        .edit()
                        .putString(KEY_USER_ID, firebaseUid)
                        .apply()
                    cachedUserId = firebaseUid
                }
            }
            return firebaseUid
        }

        // 2. Cached local ID (device UUID or legacy google_ email ID)
        cachedUserId?.let { return it }
        return synchronized(this) {
            cachedUserId ?: loadOrCreateUserId(context.applicationContext).also { cachedUserId = it }
        }
    }

    /**
     * Called after Firebase Auth sign-in succeeds.
     * Stores the Firebase UID as the canonical identity.
     * Also migrates legacy google_ email IDs to the real Firebase UID.
     */
    fun setIdentityFromFirebaseUid(
        uid: String,
        context: Context = FirebaseApp.getInstance().applicationContext
    ) {
        if (uid.isBlank()) return
        synchronized(this) {
            securePreferencesCached(context.applicationContext)
                .edit()
                .putString(KEY_USER_ID, uid)
                .apply()
            cachedUserId = uid
        }
    }

    /**
     * Legacy: kept for backward compatibility during migration.
     * New code should use setIdentityFromFirebaseUid instead.
     */
    fun setIdentityFromEmail(
        email: String,
        context: Context = FirebaseApp.getInstance().applicationContext
    ) {
        if (email.isBlank()) return
        // Only use email-based ID if Firebase Auth is not available
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
        if (!firebaseUid.isNullOrBlank()) {
            setIdentityFromFirebaseUid(firebaseUid, context)
            return
        }
        val emailId = "google_${email.trim().lowercase()}"
        synchronized(this) {
            securePreferencesCached(context.applicationContext)
                .edit()
                .putString(KEY_USER_ID, emailId)
                .apply()
            cachedUserId = emailId
        }
    }

    /**
     * Clears the persisted identity and signs out of Firebase Auth.
     * Called on logout.
     */
    fun resetIdentity(context: Context = FirebaseApp.getInstance().applicationContext) {
        synchronized(this) {
            FirebaseAuth.getInstance().signOut()
            val newId = "device_${UUID.randomUUID()}"
            securePreferencesCached(context.applicationContext)
                .edit()
                .putString(KEY_USER_ID, newId)
                .apply()
            cachedUserId = newId
        }
    }

    private fun loadOrCreateUserId(context: Context): String {
        val preferences = securePreferencesCached(context)
        val existingUserId = preferences.getString(KEY_USER_ID, null)
        if (!existingUserId.isNullOrBlank()) {
            return existingUserId
        }
        val generatedUserId = "device_${UUID.randomUUID()}"
        preferences.edit()
            .putString(KEY_USER_ID, generatedUserId)
            .apply()
        return generatedUserId
    }
}
