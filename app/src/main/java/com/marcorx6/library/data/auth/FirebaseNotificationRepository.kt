package com.marcorx6.library.data.auth

import com.marcorx6.library.data.Game
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * In-app notification inbox. Notifications are written alongside the event that
 * caused them, and every query is scoped to the signed-in user's own subcollection.
 */
internal class FirebaseNotificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun listen(
        uid: String,
        onChange: (List<DocumentSnapshot>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        if (uid.isBlank()) {
            onError(IllegalArgumentException("معرّف الحساب غير صالح"))
            return EmptyListener
        }
        return firestore.collection("users").document(uid)
            .collection("notifications")
            // Do not orderBy createdAt on the server: legacy or externally-created
            // notifications without that field are otherwise silently omitted.
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else {
                    val ordered = snapshot?.documents.orEmpty().sortedByDescending { item ->
                        item.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    }
                    onChange(ordered)
                }
            }
    }

    fun addNewGameNotifications(
        uid: String,
        newGames: List<Game>
    ): Task<Void> {
        if (uid.isBlank() || newGames.isEmpty()) return Tasks.forResult(null)
        val batch = firestore.batch()
        newGames.take(30).forEach { game ->
            val name = game.name
            val platform = game.platform
            val ref = firestore.collection("users").document(uid)
                .collection("notifications").document()
            batch.set(ref, mapOf(
                "type" to "game",
                "actorUid" to uid,
                "gameName" to name.take(120),
                "platform" to platform.take(30),
                "coverUrl" to game.coverUrl.take(2000),
                "title" to "لعبة جديدة في المكتبة",
                "body" to "$name • $platform",
                "createdAt" to FieldValue.serverTimestamp(),
                "read" to false,
                "pushStatus" to "pending"
            ))
        }
        return batch.commit()
    }

    /** Deletes every notification in the signed-in user's inbox, in safe Firestore batches. */
    fun deleteAll(uid: String): Task<Void> {
        if (uid.isBlank()) {
            return Tasks.forException(IllegalArgumentException("معرّف الحساب غير صالح"))
        }
        return firestore.collection("users").document(uid)
            .collection("notifications")
            .get()
            .continueWithTask { queryTask ->
                if (!queryTask.isSuccessful) {
                    return@continueWithTask Tasks.forException(
                        queryTask.exception ?: IllegalStateException("تعذر تحميل الإشعارات")
                    )
                }
                val documents = queryTask.result?.documents.orEmpty()
                val commits = documents.chunked(400).map { chunk ->
                    firestore.batch().also { batch ->
                        chunk.forEach { document -> batch.delete(document.reference) }
                    }.commit()
                }
                Tasks.whenAll(commits)
            }
    }

    fun markRead(uid: String, notification: DocumentSnapshot): Task<Void> {
        if (uid.isBlank() || notification.reference.parent.parent?.id != uid) {
            return Tasks.forException(IllegalArgumentException("إشعار غير صالح"))
        }
        if (notification.getBoolean("read") == true) return Tasks.forResult(null)
        return notification.reference.update(
            mapOf("read" to true, "readAt" to FieldValue.serverTimestamp())
        )
    }

    fun unreadCount(items: List<DocumentSnapshot>): Int =
        items.count { it.getBoolean("read") != true }

    private object EmptyListener : ListenerRegistration {
        override fun remove() = Unit
    }
}
