package com.marcorx6.library.data.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Stores only non-secret account/profile metadata in the signed-in user's own Firestore path.
 * Passwords and OAuth tokens are never written to Firestore.
 */
internal class FirebaseUserDocumentRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun userDocument(uid: String) =
        firestore.collection(USERS_COLLECTION).document(uid)

    private fun publicProfileDocument(uid: String) =
        firestore.collection(PUBLIC_PROFILES_COLLECTION).document(uid)

    fun syncAuthUser(user: FirebaseUser): Task<Void> {
        val document = userDocument(user.uid)
        return document.get().continueWithTask { task ->
            if (!task.isSuccessful) {
                throw task.exception ?: IllegalStateException("تعذر قراءة ملف المستخدم")
            }

            val data = mutableMapOf<String, Any>(
                "uid" to user.uid,
                "isAnonymous" to user.isAnonymous,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            user.displayName?.takeIf(String::isNotBlank)?.let { data["displayName"] = it }
            user.email?.takeIf(String::isNotBlank)?.let { data["email"] = it }
            user.photoUrl?.toString()?.takeIf(String::isNotBlank)?.let { data["photoUrl"] = it }

            if (task.result?.exists() != true) {
                data["createdAt"] = FieldValue.serverTimestamp()
            }
            document.set(data, SetOptions.merge())
        }
    }

    fun loadUser(uid: String): Task<DocumentSnapshot> =
        userDocument(uid).get()

    /** A public profile is fetched by exact UID; Firestore rules enforce owner visibility. */
    fun loadPublicProfile(uid: String): Task<DocumentSnapshot> =
        publicProfileDocument(uid.trim()).get()

    fun saveProfile(
        uid: String,
        displayName: String?,
        bio: String?,
        photoUrl: String?,
        photoData: String? = null,
        publicProfile: Boolean? = null,
        showFavorites: Boolean? = null,
        favoriteGames: List<Map<String, Any>>? = null
    ): Task<Void> {
        val data = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        displayName?.trim()?.takeIf(String::isNotBlank)?.let { data["displayName"] = it }
        bio?.trim()?.let { data["bio"] = it }
        photoUrl?.trim()?.takeIf(String::isNotBlank)?.let { data["photoUrl"] = it }
        photoData?.takeIf(String::isNotBlank)?.let { data["photoData"] = it }
        val privateSave = userDocument(uid).set(data, SetOptions.merge())
        if (publicProfile == null) return privateSave

        return privateSave.continueWithTask { task ->
            if (!task.isSuccessful) {
                throw task.exception ?: IllegalStateException("تعذر حفظ بيانات الحساب")
            }
            if (!publicProfile) {
                publicProfileDocument(uid).delete()
            } else {
                val publicData = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "displayName" to (displayName?.trim()?.take(40) ?: "MARCO User"),
                    "showFavorites" to (showFavorites ?: true)
                )
                bio?.trim()?.takeIf(String::isNotBlank)?.let { publicData["bio"] = it.take(160) }
                if (showFavorites == false) {
                    publicData["favoriteGames"] = FieldValue.delete()
                } else {
                    favoriteGames?.let { publicData["favoriteGames"] = it.take(30) }
                }
                publicProfileDocument(uid).set(publicData, SetOptions.merge())
            }
        }
    }

    /** Deletes the known user-owned settings document before removing the parent profile. */
    fun deleteUserData(uid: String): Task<Void> =
        userDocument(uid)
            .collection(SETTINGS_COLLECTION)
            .document(PREFERENCES_DOCUMENT)
            .delete()
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("تعذر حذف إعدادات الحساب")
                }
                publicProfileDocument(uid).delete().continueWithTask { publicDelete ->
                    if (!publicDelete.isSuccessful) {
                        throw publicDelete.exception ?: IllegalStateException("تعذر حذف الملف العام")
                    }
                    userDocument(uid).delete()
                }
            }

    fun saveSettings(uid: String, values: Map<String, Any>): Task<Void> =
        userDocument(uid)
            .collection(SETTINGS_COLLECTION)
            .document(PREFERENCES_DOCUMENT)
            .set(
                mapOf(
                    "values" to values,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

    private companion object {
        const val USERS_COLLECTION = "users"
        const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
        const val SETTINGS_COLLECTION = "settings"
        const val PREFERENCES_DOCUMENT = "preferences"
    }
}
