package com.marcorx6.library.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.security.MessageDigest

/**
 * Public game engagement data. Views are unique per Firebase UID (including a stable
 * anonymous-install UID); ratings are accepted only from non-anonymous accounts.
 */
internal class GameEngagementRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    data class Summary(
        val views: Long = 0,
        val ratingAverage: Double = 0.0,
        val ratingCount: Long = 0
    )

    private fun gameId(game: Game): String {
        val identity = listOf(game.platform.trim().uppercase(), game.name.trim().lowercase(),
            game.url.trim()).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun gameDocument(game: Game) =
        firestore.collection("gameMetrics").document(gameId(game))

    fun recordViewOnce(game: Game): Task<Boolean> {
        val user = auth.currentUser
            ?: return Tasks.forException(IllegalStateException("لا توجد جلسة مستخدم"))
        val view = gameDocument(game).collection("views").document(user.uid)
        return firestore.runTransaction { transaction ->
            val snapshot = transaction.get(view)
            if (snapshot.exists()) {
                false
            } else {
                transaction.set(view, mapOf(
                    "uid" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
                true
            }
        }
    }

    fun submitRating(game: Game, rating: Int): Task<Void> {
        val user = auth.currentUser
            ?: return Tasks.forException(IllegalStateException("سجّل الدخول أولاً"))
        if (user.isAnonymous) {
            return Tasks.forException(IllegalStateException("التقييم متاح للمستخدمين المسجلين فقط"))
        }
        if (rating !in 1..5) {
            return Tasks.forException(IllegalArgumentException("التقييم يجب أن يكون بين نجمة و5 نجوم"))
        }
        return gameDocument(game).collection("ratings").document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "rating" to rating,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
    }

    fun loadSummary(game: Game, callback: (Summary) -> Unit, onError: (Exception) -> Unit = {}) {
        val root = gameDocument(game)
        root.collection("views").count().get(com.google.firebase.firestore.AggregateSource.SERVER)
            .addOnSuccessListener { views ->
                root.collection("ratings").get()
                    .addOnSuccessListener { ratings ->
                        val values = ratings.documents.mapNotNull { document ->
                            document.getLong("rating")?.takeIf { it in 1L..5L }
                        }
                        val average = if (values.isEmpty()) 0.0 else values.average()
                        callback(Summary(views.count, average, values.size.toLong()))
                    }
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }

    fun loadMyRating(game: Game, callback: (Int?) -> Unit) {
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            callback(null)
            return
        }
        gameDocument(game).collection("ratings").document(user.uid).get()
            .addOnSuccessListener { callback(it.getLong("rating")?.toInt()) }
            .addOnFailureListener { callback(null) }
    }

    fun isRegisteredUser(): Boolean = auth.currentUser?.isAnonymous == false
}
