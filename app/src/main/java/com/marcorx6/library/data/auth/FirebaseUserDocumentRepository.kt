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

    fun saveProfile(
        uid: String,
        displayName: String?,
        bio: String?,
        photoUrl: String?
    ): Task<Void> {
        val data = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        displayName?.trim()?.takeIf(String::isNotBlank)?.let { data["displayName"] = it }
        bio?.trim()?.let { data["bio"] = it }
        photoUrl?.trim()?.takeIf(String::isNotBlank)?.let { data["photoUrl"] = it }
        return userDocument(uid).set(data, SetOptions.merge())
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
        const val SETTINGS_COLLECTION = "settings"
        const val PREFERENCES_DOCUMENT = "preferences"
    }
}
