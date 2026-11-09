package com.marcorx6.library.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest

/** Stores each registered user's FCM token in a private, per-account device document. */
internal class FirebasePushTokenRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    fun syncCurrentToken() {
        val user = auth.currentUser ?: return
        if (user.isAnonymous) return
        messaging.token
            .addOnSuccessListener { token -> saveTokenForUser(token, user.uid) }
            .addOnFailureListener { error ->
                android.util.Log.w("MarcoFCM", "Unable to retrieve FCM token", error)
            }
    }

    fun saveToken(token: String) {
        if (token.isBlank()) return
        val user = auth.currentUser ?: return
        if (user.isAnonymous) return
        saveTokenForUser(token, user.uid)
    }

    private fun saveTokenForUser(token: String, uid: String) {
        if (token.isBlank() || uid.isBlank()) return
        // A token request may finish after an account switch; keep it bound to the
        // account that initiated the request.
        val documentId = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        firestore.collection("users").document(uid)
            .collection("devices").document(documentId)
            .set(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { error ->
                android.util.Log.w("MarcoFCM", "Unable to save FCM device token", error)
            }
    }
}
