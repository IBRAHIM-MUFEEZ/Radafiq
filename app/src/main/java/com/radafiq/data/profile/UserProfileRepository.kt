package com.radafiq.data.profile

import com.radafiq.data.auth.LocalIdentityRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val businessName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val isProfileComplete: Boolean = false
)

data class UserProfileState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null
)

class UserProfileRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val _state = MutableStateFlow(UserProfileState(isLoading = true))
    val state: StateFlow<UserProfileState> = _state.asStateFlow()

    private var registration: ListenerRegistration? = null

    fun observeCurrentUserProfile() {
        registration?.remove()
        val userId = LocalIdentityRepository.userId()

        _state.value = UserProfileState(isLoading = true)
        registration = profileDocument(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.w("UserProfile", "Profile listener error — proceeding with default profile", error)
                _state.value = UserProfileState(
                    isLoading = false,
                    profile = UserProfile(uid = userId)
                )
                return@addSnapshotListener
            }
            val profile = if (snapshot == null || !snapshot.exists()) {
                UserProfile(uid = userId)
            } else {
                UserProfile(
                    uid = userId,
                    displayName = snapshot.getString("displayName").orEmpty(),
                    businessName = snapshot.getString("businessName").orEmpty(),
                    email = snapshot.getString("email").orEmpty(),
                    photoUrl = snapshot.getString("photoUrl").orEmpty(),
                    isProfileComplete = snapshot.getBoolean("isProfileComplete") == true
                )
            }

            _state.value = UserProfileState(
                isLoading = false,
                profile = profile
            )
        }
    }

    fun signOut() {
        // Identity reset is handled by LocalIdentityRepository.resetIdentity() in MainActivity.
        // Stop the current Firestore listener so we don't receive stale data after logout.
        registration?.remove()
        registration = null
        _state.value = UserProfileState(isLoading = true)
    }
    suspend fun saveProfile(
        displayName: String,
        businessName: String,
        email: String,
        photoUrl: String = ""
    ) {
        // FIX-4: Validate email format before writing to Firestore
        val trimmedEmail = email.trim()
        if (trimmedEmail.isNotBlank() &&
            !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()
        ) {
            android.util.Log.w("UserProfile", "saveProfile called with invalid email: $trimmedEmail — skipping email field")
        }
        val userId = LocalIdentityRepository.userId()
        profileDocument(userId)
            .set(
                mapOf(
                    "uid" to userId,
                    "phoneNumber" to FieldValue.delete(),
                    "displayName" to displayName.trim(),
                    "businessName" to businessName.trim(),
                    "email" to trimmedEmail,
                    "photoUrl" to photoUrl.trim(),
                    "isProfileComplete" to true
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun exportProfileMap(): Map<String, Any> {
        val snapshot = profileDocument(LocalIdentityRepository.userId()).get().await()
        return snapshot.data.orEmpty()
            .filterKeys { it != "phoneNumber" }
    }

    suspend fun restoreProfileMap(data: Map<String, Any>) {
        restoreProfileMapAsync(data).await()
    }

    fun restoreProfileMapAsync(data: Map<String, Any>): Task<Void> {
        val sanitizedData = data
            .filterKeys { it != "phoneNumber" }
            .toMutableMap<String, Any>()
        sanitizedData["phoneNumber"] = FieldValue.delete()

        return profileDocument(LocalIdentityRepository.userId())
            .set(sanitizedData, SetOptions.merge())
    }

    private fun profileDocument(uid: String) = db.collection("users")
        .document(uid)
        .collection("profile")
        .document("main")
}
