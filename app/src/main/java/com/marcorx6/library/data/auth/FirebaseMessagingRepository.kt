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

    /**
     * Creates the conversation document before attaching a message listener.
     * Firestore rules use this parent document to authorize message reads.
     */
    fun ensureConversation(firstUid: String, secondUid: String): Task<Void> {
        if (firstUid.isBlank() || secondUid.isBlank() || firstUid == secondUid) {
            return Tasks.forException(IllegalArgumentException("معرّف المستخدم غير صالح"))
        }
        val participants = listOf(firstUid, secondUid).sorted()
        val conversation = firestore.collection(CONVERSATIONS)
            .document(conversationId(firstUid, secondUid))
        return canMessage(firstUid, secondUid).continueWithTask { allowedTask ->
            if (!allowedTask.isSuccessful) {
                throw allowedTask.exception ?: IllegalStateException("تعذر التحقق من صلاحية المراسلة")
            }
            if (allowedTask.result != true) {
                throw IllegalStateException("يمكنك المراسلة فقط عندما يتابع كل منكما الآخر")
            }
            firestore.runTransaction { transaction ->
                val existing = transaction.get(conversation)
                if (!existing.exists()) {
                    transaction.set(conversation, mapOf(
                        "participants" to participants,
                        "participantA" to participants[0],
                        "participantB" to participants[1],
                        "lastMessage" to "",
                        "lastSenderUid" to firstUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                } else {
                    val storedParticipants = (existing.get("participants") as? List<*>)
                        ?.filterIsInstance<String>()
                    if (storedParticipants?.size == 2 &&
                        storedParticipants.contains(firstUid) &&
                        storedParticipants.contains(secondUid)
                    ) {
                        if (existing.getString("participantA") != storedParticipants[0] ||
                            existing.getString("participantB") != storedParticipants[1]
                        ) {
                            transaction.update(conversation, mapOf(
                                "participantA" to storedParticipants[0],
                                "participantB" to storedParticipants[1]
                            ))
                        }
                    } else {
                        throw IllegalStateException("بيانات المشاركين في المحادثة غير صالحة")
                    }
                }
                true
            }.continueWith { transactionTask ->
                if (!transactionTask.isSuccessful) {
                    throw transactionTask.exception ?: IllegalStateException("تعذر إنشاء المحادثة")
                }
                null
            }
        }
    }

    fun sendMessage(
        senderUid: String, recipientUid: String, text: String,
        replyToMessageId: String? = null, replyToText: String? = null, replyToSenderUid: String? = null
    ): Task<Void> {
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
                val storedParticipants = (conversationTask.result.get("participants") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.takeIf { it.size == 2 && it.contains(senderUid) && it.contains(recipientUid) }
                    ?: participants
                val conversationData = mutableMapOf<String, Any>(
                    "participants" to storedParticipants,
                    "participantA" to storedParticipants[0],
                    "participantB" to storedParticipants[1],
                    "lastMessage" to body.take(180),
                    "lastSenderUid" to senderUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!conversationTask.result.exists()) {
                    conversationData["createdAt"] = FieldValue.serverTimestamp()
                }
                conversationData["deletedFor"] = FieldValue.arrayRemove(senderUid, recipientUid)
                batch.set(conversation, conversationData, SetOptions.merge())
                val messageData = mutableMapOf<String, Any>(
                    "senderUid" to senderUid,
                    "text" to body,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "readBy" to listOf(senderUid)
                )
                if (!replyToMessageId.isNullOrBlank() && !replyToText.isNullOrBlank() &&
                    !replyToSenderUid.isNullOrBlank() &&
                    (replyToSenderUid == senderUid || replyToSenderUid == recipientUid)
                ) {
                    messageData["replyToMessageId"] = replyToMessageId
                    messageData["replyToText"] = replyToText.take(180)
                    messageData["replyToSenderUid"] = replyToSenderUid
                }
                batch.set(message, messageData)
                val notification = firestore.collection("users")
                    .document(recipientUid).collection("notifications").document()
                batch.set(notification, mapOf(
                    "type" to "message",
                    "actorUid" to senderUid,
                    "conversationId" to id,
                    "messageId" to message.id,
                    "title" to "رسالة جديدة",
                    "body" to body.take(120),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "read" to false,
                    "pushStatus" to "pending"
                ))
                batch.commit()
            }
        }
    }


    fun hideConversation(currentUid: String, otherUid: String): Task<Void> {
        if (currentUid.isBlank() || otherUid.isBlank() || currentUid == otherUid) {
            return Tasks.forException(IllegalArgumentException("معرّف المحادثة غير صالح"))
        }
        return firestore.collection(CONVERSATIONS)
            .document(conversationId(currentUid, otherUid))
            .update("deletedFor", FieldValue.arrayUnion(currentUid))
    }

    fun deleteMessage(currentUid: String, otherUid: String, messageId: String): Task<Void> {
        if (currentUid.isBlank() || otherUid.isBlank() || messageId.isBlank()) {
            return Tasks.forException(IllegalArgumentException("الرسالة غير صالحة"))
        }
        val conversation = firestore.collection(CONVERSATIONS)
            .document(conversationId(currentUid, otherUid))
        val message = conversation.collection(MESSAGES).document(messageId)
        val batch = firestore.batch()
        batch.delete(message)
        batch.update(conversation, mapOf(
            "lastMessage" to "تم حذف رسالة",
            "lastSenderUid" to currentUid,
            "updatedAt" to FieldValue.serverTimestamp()
        ))
        return batch.commit()
    }

    fun markMessagesRead(
        readerUid: String,
        messages: List<DocumentSnapshot>
    ): Task<Void> {
        val unread = messages.filter { message ->
            message.getString("senderUid") != readerUid &&
                !((message.get("readBy") as? List<*>)?.contains(readerUid) ?: false)
        }
        if (unread.isEmpty()) return Tasks.forResult<Void>(null)
        val batch = firestore.batch()
        unread.forEach { message ->
            batch.update(
                message.reference,
                mapOf(
                    "readBy" to FieldValue.arrayUnion(readerUid),
                    "readAt" to FieldValue.serverTimestamp()
                )
            )
        }
        return batch.commit()
    }

    fun listenInbox(
        uid: String,
        onChange: (List<DocumentSnapshot>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val firstDocuments = linkedMapOf<String, DocumentSnapshot>()
        val secondDocuments = linkedMapOf<String, DocumentSnapshot>()
        val lock = Any()
        fun handleSnapshot(firstField: Boolean, snapshot: com.google.firebase.firestore.QuerySnapshot?, error: Exception?) {
            if (error != null) {
                onError(error)
                return
            }
            val combined = synchronized(lock) {
                val target = if (firstField) firstDocuments else secondDocuments
                target.clear()
                snapshot?.documents.orEmpty().forEach { target[it.id] = it }
                (firstDocuments.values + secondDocuments.values)
                    .distinctBy { it.id }
                    .sortedByDescending { it.getTimestamp("updatedAt")?.toDate()?.time ?: 0L }
            }
            onChange(combined)
        }
        val firstListener = firestore.collection(CONVERSATIONS)
            .whereEqualTo("participantA", uid)
            .addSnapshotListener { snapshot, error -> handleSnapshot(true, snapshot, error) }
        val secondListener = firestore.collection(CONVERSATIONS)
            .whereEqualTo("participantB", uid)
            .addSnapshotListener { snapshot, error -> handleSnapshot(false, snapshot, error) }
        return object : ListenerRegistration {
            override fun remove() {
                firstListener.remove()
                secondListener.remove()
            }
        }
    }

    fun listenMessages(
        conversationId: String,
        onChange: (List<DocumentSnapshot>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection(CONVERSATIONS).document(conversationId)
            .collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error)
                else onChange(snapshot?.documents.orEmpty()?.reversed().orEmpty())
            }
    }

    fun otherParticipant(snapshot: DocumentSnapshot, currentUid: String): String? =
        (snapshot.get("participants") as? List<*>)
            ?.filterIsInstance<String>()
            ?.firstOrNull { it != currentUid }

    companion object {
        const val MAX_MESSAGE_LENGTH = 4000
        private const val CONVERSATIONS = "conversations"
        private const val MESSAGES = "messages"
    }
}
