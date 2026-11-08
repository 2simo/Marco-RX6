package com.marcorx6.library.data.auth

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest

/**
 * Private one-to-one messaging. Firestore rules independently enforce mutual-follow
 * authorization; client-side checks are only for a friendly UI.
 */
internal class FirebaseMessagingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun conversationId(firstUid: String, secondUid: String): String {
        require(firstUid.isNotBlank() && secondUid.isNotBlank() && firstUid != secondUid)
        val pair = listOf(firstUid, secondUid).sorted().joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(pair.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun canMessage(firstUid: String, secondUid: String): Task<Boolean> {
        if (firstUid.isBlank() || secondUid.isBlank() || firstUid == secondUid) {
            return Tasks.forResult(false)
        }
        val firstFollows = firestore.collection("userFollows").document(firstUid)
            .collection("following").document(secondUid).get()
        val secondFollows = firestore.collection("userFollows").document(secondUid)
            .collection("following").document(firstUid).get()
        return Tasks.whenAllSuccess<DocumentSnapshot>(firstFollows, secondFollows)
            .continueWith { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("تعذر التحقق من المتابعة المتبادلة")
                task.result.size == 2 && task.result.all { it.exists() }
            }
    }

    fun sendMessage(senderUid: String, recipientUid: String, text: String): Task<Void> {
        val body = text.trim()
        if (senderUid.isBlank() || recipientUid.isBlank() || senderUid == recipientUid ||
            body.isEmpty() || body.length > MAX_MESSAGE_LENGTH
        ) {
            return Tasks.forException(IllegalArgumentException("الرسالة غير صالحة أو تتجاوز الحد المسموح"))
        }
        val id = conversationId(senderUid, recipientUid)
        val conversation = firestore.collection(CONVERSATIONS).document(id)
        val message = conversation.collection(MESSAGES).document()
        val participants = listOf(senderUid, recipientUid).sorted()
        return canMessage(senderUid, recipientUid).continueWithTask { allowedTask ->
            if (!allowedTask.isSuccessful) {
                throw allowedTask.exception ?: IllegalStateException("تعذر التحقق من صلاحية المراسلة")
            }
            if (allowedTask.result != true) {
                throw IllegalStateException("يمكنك المراسلة فقط عندما يتابع كل منكما الآخر")
            }
            conversation.get().continueWithTask { conversationTask ->
                if (!conversationTask.isSuccessful) {
                    throw conversationTask.exception ?: IllegalStateException("تعذر فتح المحادثة")
                }
                val batch = firestore.batch()
                val conversationData = mutableMapOf<String, Any>(
                    "participants" to participants,
                    "lastMessage" to body.take(180),
                    "lastSenderUid" to senderUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!conversationTask.result.exists()) {
                    conversationData["createdAt"] = FieldValue.serverTimestamp()
                }
                batch.set(conversation, conversationData, SetOptions.merge())
                batch.set(message, mapOf(
                    "senderUid" to senderUid,
                    "text" to body,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
                batch.commit()
            }
        }
    }

    fun listenInbox(
        uid: String,
        onChange: (List<DocumentSnapshot>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection(CONVERSATIONS)
            .whereArrayContains("participants", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error)
                else onChange(snapshot?.documents.orEmpty())
            }
    }

    fun listenMessages(
        conversationId: String,
        onChange: (List<DocumentSnapshot>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection(CONVERSATIONS).document(conversationId)
            .collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error)
                else onChange(snapshot?.documents.orEmpty())
            }
    }

    fun otherParticipant(snapshot: DocumentSnapshot, currentUid: String): String? =
        snapshot.get("participants") as? List<*>
            ?.filterIsInstance<String>()
            ?.firstOrNull { it != currentUid }

    companion object {
        const val MAX_MESSAGE_LENGTH = 4000
        private const val CONVERSATIONS = "conversations"
        private const val MESSAGES = "messages"
    }
}
