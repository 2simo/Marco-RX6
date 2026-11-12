package com.marcorx6.library.data

import android.util.Log
import com.marcorx6.library.AppDiagnostics

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateQuerySnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
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
        val ratingCount: Long = 0,
        val downloads: Long = 0
    )

    private data class CachedSummary(
        val value: Summary,
        val cachedAt: Long
    )

    private data class SummaryCallback(
        val success: (Summary) -> Unit,
        val failure: (Exception) -> Unit
    )

    private val summaryLock = Any()
    private val summaryCache = LinkedHashMap<String, CachedSummary>()
    private val pendingSummaryCallbacks = mutableMapOf<String, MutableList<SummaryCallback>>()

    private companion object {
        const val SUMMARY_CACHE_TTL_MS = 5L * 60L * 1000L
        const val MAX_CACHED_SUMMARIES = 512
    }

    private fun gameId(game: Game): String {
        val stableGameKey = game.bloggerPostId.trim().takeIf { it.isNotBlank() }
            ?: game.sourcePostUrl.trim().takeIf { it.isNotBlank() }
            ?: game.url.trim().takeIf { it.isNotBlank() }
            ?: game.name.trim().lowercase()
        val identity = listOf(game.platform.trim().uppercase(), stableGameKey).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun gameDocument(game: Game) =
        firestore.collection("gameMetrics").document(gameId(game))

    private fun invalidateSummary(game: Game) {
        synchronized(summaryLock) {
            summaryCache.remove(gameId(game))
        }
    }

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
        }.addOnSuccessListener { recorded ->
            if (recorded) invalidateSummary(game)
        }
    }

    fun recordDownload(game: Game): Task<Boolean> {
        val user = auth.currentUser
            ?: return Tasks.forException(IllegalStateException("لا توجد جلسة مستخدم"))
        val download = gameDocument(game).collection("downloads").document(user.uid)
        return firestore.runTransaction { transaction ->
            val snapshot = transaction.get(download)
            if (snapshot.exists()) {
                false
            } else {
                transaction.set(download, mapOf(
                    "uid" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
                true
            }
        }.addOnSuccessListener { recorded ->
            if (recorded) invalidateSummary(game)
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
        ).addOnSuccessListener {
            invalidateSummary(game)
        }
    }

    /**
     * Reuses fresh summaries and coalesces simultaneous requests for the same game.
     * The three Firestore reads run in parallel instead of waiting for one another.
     */
    fun loadSummary(game: Game, callback: (Summary) -> Unit, onError: (Exception) -> Unit = {}) {
        val id = gameId(game)
        var cached: Summary? = null
        var shouldStartRequest = false

        synchronized(summaryLock) {
            val entry = summaryCache[id]
            if (entry != null && System.currentTimeMillis() - entry.cachedAt < SUMMARY_CACHE_TTL_MS) {
                cached = entry.value
            } else {
                val waiting = pendingSummaryCallbacks[id]
                if (waiting != null) {
                    waiting.add(SummaryCallback(callback, onError))
                } else {
                    pendingSummaryCallbacks[id] = mutableListOf(SummaryCallback(callback, onError))
                    shouldStartRequest = true
                }
            }
        }

        cached?.let {
            callback(it)
            return
        }
        if (!shouldStartRequest) return

        val root = gameDocument(game)
        val viewsTask = root.collection("views").count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER)
        val ratingsTask = root.collection("ratings").get()
        val downloadsTask = root.collection("downloads").count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER)

        // Keep each typed Task as the source of its own result. Indexing the
        // whenAllSuccess result list made this path vulnerable to an
        // ArrayIndexOutOfBoundsException when the aggregate contained fewer entries.
        Tasks.whenAll(viewsTask, ratingsTask, downloadsTask)
            .addOnSuccessListener {
                val views: AggregateQuerySnapshot = viewsTask.result
                val ratings: QuerySnapshot = ratingsTask.result
                val downloads: AggregateQuerySnapshot = downloadsTask.result
                val values = ratings.documents.mapNotNull { document ->
                    document.getLong("rating")?.takeIf { it in 1L..5L }
                }
                val summary = Summary(
                    views = views.count,
                    ratingAverage = if (values.isEmpty()) 0.0 else values.average(),
                    ratingCount = values.size.toLong(),
                    downloads = downloads.count
                )

                val callbacks = synchronized(summaryLock) {
                    summaryCache[id] = CachedSummary(summary, System.currentTimeMillis())
                    while (summaryCache.size > MAX_CACHED_SUMMARIES) {
                        val oldestKey = summaryCache.entries.minByOrNull { it.value.cachedAt }?.key
                            ?: break
                        summaryCache.remove(oldestKey)
                    }
                    pendingSummaryCallbacks.remove(id).orEmpty()
                }
                AppDiagnostics.log(
                    "DEBUG",
                    "GameEngagement",
                    "Engagement summary loaded views=${summary.views} ratings=${summary.ratingCount} downloads=${summary.downloads}"
                )
                callbacks.forEach { it.success(summary) }
            }
            .addOnFailureListener { error ->
                AppDiagnostics.log("WARN", "GameEngagement", "Engagement summary request failed", error)
                val callbacks = synchronized(summaryLock) {
                    pendingSummaryCallbacks.remove(id).orEmpty()
                }
                callbacks.forEach { it.failure(error) }
            }
    }

    data class UserRating(val game: Game, val rating: Int)

    /** Loads this account's ratings in one collection-group request, then maps them
     * back to the locally available game metadata without issuing one read per game.
     */
    fun loadMyRatings(
        games: List<Game>,
        onSuccess: (List<UserRating>) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            onSuccess(emptyList())
            return
        }
        loadRatingsForUser(user.uid, games, onSuccess, onFailure)
    }

    /** Loads a selected user's public ratings and maps them to known library metadata. */
    fun loadRatingsForUser(
        uid: String,
        games: List<Game>,
        onSuccess: (List<UserRating>) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        if (uid.isBlank()) {
            onSuccess(emptyList())
            return
        }
        val gamesById = games.associateBy(::gameId)
        firestore.collectionGroup("ratings")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val ratings = snapshot.documents.mapNotNull { document ->
                    val value = document.getLong("rating")?.toInt()?.takeIf { it in 1..5 }
                        ?: return@mapNotNull null
                    val parentGameId = document.reference.parent.parent?.id
                        ?: return@mapNotNull null
                    val game = gamesById[parentGameId] ?: return@mapNotNull null
                    UserRating(game, value)
                }.sortedBy { it.game.name.lowercase() }
                onSuccess(ratings)
            }
            .addOnFailureListener { error ->
                Log.e("GameEngagementRepository", "loadRatingsForUser failed for uid=$uid", error)
                onFailure(error)
            }
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
