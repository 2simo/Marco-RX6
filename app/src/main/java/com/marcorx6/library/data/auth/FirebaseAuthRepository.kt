package com.marcorx6.library.data.auth

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Firebase Authentication boundary for app accounts.
 *
 * This is deliberately separate from the legacy Google OAuth client used by Blogger and Drive.
 */
internal class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun ensureGuestSession(): Task<AuthResult>? =
        if (auth.currentUser == null) auth.signInAnonymously() else null

    /**
     * Upgrades an anonymous Firebase session in place when possible, preserving its UID
     * and therefore access to user-owned Firestore documents.
     */
    fun createEmailAccount(email: String, password: String): Task<AuthResult> {
        val normalizedEmail = email.trim()
        val credential = EmailAuthProvider.getCredential(normalizedEmail, password)
        val user = auth.currentUser
        return when {
            user?.isAnonymous == true -> user.linkWithCredential(credential)
            user == null -> auth.createUserWithEmailAndPassword(normalizedEmail, password)
            else -> Tasks.forException(
                IllegalStateException("سجّل الخروج من الحساب الحالي قبل إنشاء حساب جديد")
            )
        }
    }

    fun signInWithEmail(email: String, password: String): Task<AuthResult> =
        auth.signInWithEmailAndPassword(email.trim(), password)

    /** Link a Google ID token to the current guest account without changing its UID. */
    fun linkGoogleToCurrentUser(idToken: String): Task<AuthResult> {
        val user = auth.currentUser
            ?: return Tasks.forException(IllegalStateException("لا يوجد حساب حالي لربط Google به"))
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return user.linkWithCredential(credential)
    }

    /** Sign in to an existing Firebase Google account. */
    fun signInWithGoogle(idToken: String): Task<AuthResult> =
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))

    fun sendEmailVerification(): Task<Void>? =
        auth.currentUser?.takeIf { !it.isAnonymous && !it.isEmailVerified }?.sendEmailVerification()

    fun sendPasswordReset(email: String): Task<Void> =
        auth.sendPasswordResetEmail(email.trim())

    fun deleteCurrentUser(): Task<Void> = auth.currentUser?.delete()
        ?: Tasks.forException(IllegalStateException("لا يوجد حساب لحذفه"))

    fun signOut() = auth.signOut()

    fun addAuthStateListener(listener: FirebaseAuth.AuthStateListener) =
        auth.addAuthStateListener(listener)

    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) =
        auth.removeAuthStateListener(listener)
}
