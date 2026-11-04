package com.marcorx6.library.data.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * Creates a Firebase guest session for first-run users and mirrors basic account metadata.
 * The app remains usable offline if Firebase cannot be reached.
 */
internal class FirebaseSessionCoordinator(
    private val authRepository: FirebaseAuthRepository = FirebaseAuthRepository(),
    private val userDocuments: FirebaseUserDocumentRepository = FirebaseUserDocumentRepository()
) {
    private var authListener: FirebaseAuth.AuthStateListener? = null

    fun start() {
        if (authListener == null) {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                auth.currentUser?.let(::syncUserDocument)
            }
            authListener = listener
            authRepository.addAuthStateListener(listener)
        }
        ensureGuestSession()
        authRepository.currentUser?.let(::syncUserDocument)
    }

    fun ensureGuestSession() {
        authRepository.ensureGuestSession()
            ?.addOnFailureListener { error ->
                Log.w(TAG, "Firebase guest session unavailable; local/offline mode remains available", error)
            }
    }

    fun stop() {
        authListener?.let(authRepository::removeAuthStateListener)
        authListener = null
    }

    private fun syncUserDocument(user: com.google.firebase.auth.FirebaseUser) {
        userDocuments.syncAuthUser(user)
            .addOnFailureListener { error ->
                // A Firestore permission denial is recoverable after the owner publishes the
                // per-user security rules. Never block the local library on cloud sync.
                Log.w(TAG, "Could not sync Firebase user metadata", error)
            }
    }

    private companion object {
        const val TAG = "MarcoFirebaseSession"
    }
}
