package com.marcorx6.library.data.auth

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale

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
        username: String? = null,
        publicProfile: Boolean? = null,
        showFavorites: Boolean? = null,
        favoriteGames: List<Map<String, Any>>? = null
    ): Task<Void> {
        if (username != null) {
            return saveProfileWithUsername(
                uid, username, displayName, bio, photoUrl, photoData,
                publicProfile, showFavorites, favoriteGames
            )
        }
        val data = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        displayName?.trim()?.takeIf(String::isNotBlank)?.let { data["displayName"] = it }
        bio?.trim()?.let { data["bio"] = it }
        photoUrl?.trim()?.takeIf(String::isNotBlank)?.let { data["photoUrl"] = it }
        photoData?.takeIf(String::isNotBlank)?.let { data["photoData"] = it }
        publicProfile?.let { data["publicProfile"] = it }
        showFavorites?.let { data["showFavorites"] = it }
        val batch = firestore.batch()
        batch.set(userDocument(uid), data, SetOptions.merge())
        if (publicProfile != null) {
            if (!publicProfile) {
                batch.delete(publicProfileDocument(uid))
            } else {
                val publicAvatar = photoData?.takeIf(String::isNotBlank)
                if (publicAvatar != null && publicAvatar.length > MAX_PUBLIC_AVATAR_CHARS) {
                    return Tasks.forException<Void>(
                        IllegalArgumentException("الصورة العامة كبيرة جداً؛ اختر صورة أصغر.")
                    )
                }
                val publicData = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "displayName" to (displayName?.trim()?.take(40) ?: "MARCO User"),
                    "showFavorites" to (showFavorites ?: true)
                )
                publicData["bio"] = bio?.trim()?.take(160).orEmpty()
                photoUrl?.trim()?.takeIf(String::isNotBlank)?.let { publicData["photoUrl"] = it }
                if (publicAvatar != null) {
                    publicData["photoData"] = publicAvatar
                } else {
                    publicData["photoData"] = FieldValue.delete()
                }
                if (showFavorites == false) {
                    publicData["favoriteGames"] = FieldValue.delete()
                } else {
                    favoriteGames?.let { publicData["favoriteGames"] = it.take(30) }
                }
                batch.set(publicProfileDocument(uid), publicData, SetOptions.merge())
            }
        }
        return batch.commit()
    }


    
    private fun usernameKey(value: String): String =
        value.trim().removePrefix("@").lowercase(Locale.ROOT)

    fun loadUidByUsername(username: String): Task<String?> {
        val key = usernameKey(username)
        if (!USERNAME_PATTERN.matches(key)) return Tasks.forResult(null)
        return firestore.collection(USERNAMES_COLLECTION).document(key).get()
            .continueWith { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("تعذر البحث عن اسم المستخدم")
                task.result.getString("uid")
            }
    }

    private fun saveProfileWithUsername(
        uid: String,
        requestedUsername: String,
        displayName: String?,
        bio: String?,
        photoUrl: String?,
        photoData: String?,
        publicProfile: Boolean?,
        showFavorites: Boolean?,
        favoriteGames: List<Map<String, Any>>?
    ): Task<Void> {
        val key = usernameKey(requestedUsername)
        if (!USERNAME_PATTERN.matches(key)) {
            return Tasks.forException(IllegalArgumentException("اسم المستخدم يجب أن يتكون من 3 إلى 20 حرفاً إنجليزياً أو رقماً أو _"))
        }
        val cleanUsername = requestedUsername.trim().removePrefix("@")
        val userRef = userDocument(uid)
        val claimRef = firestore.collection(USERNAMES_COLLECTION).document(key)
        return firestore.runTransaction { transaction ->
            val privateSnapshot = transaction.get(userRef)
            val newClaim = transaction.get(claimRef)
            val previousKey = privateSnapshot.getString("username")?.let(::usernameKey)
            val previousClaimRef = previousKey?.takeIf { it != key }
                ?.let { firestore.collection(USERNAMES_COLLECTION).document(it) }
            val previousClaim = previousClaimRef?.let(transaction::get)
            val claimedBy = newClaim.getString("uid")
            if (newClaim.exists() && claimedBy != uid) {
                throw IllegalStateException("اسم المستخدم مستخدم بالفعل. اختر اسماً آخر.")
            }

            val privateData = mutableMapOf<String, Any>(
                "username" to cleanUsername,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            displayName?.trim()?.takeIf(String::isNotBlank)?.let { privateData["displayName"] = it }
            bio?.trim()?.let { privateData["bio"] = it }
            photoUrl?.trim()?.takeIf(String::isNotBlank)?.let { privateData["photoUrl"] = it }
            photoData?.takeIf(String::isNotBlank)?.let { privateData["photoData"] = it }
            publicProfile?.let { privateData["publicProfile"] = it }
            showFavorites?.let { privateData["showFavorites"] = it }

            val isPublic = publicProfile ?: (privateSnapshot.getBoolean("publicProfile") == true)
            if (isPublic && photoData != null && photoData.length > MAX_PUBLIC_AVATAR_CHARS) {
                throw IllegalArgumentException("الصورة العامة كبيرة جداً؛ اختر صورة أصغر.")
            }

            if (!newClaim.exists()) {
                transaction.set(claimRef, mapOf(
                    "uid" to uid,
                    "username" to cleanUsername,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            }
            if (previousClaimRef != null && previousClaim?.getString("uid") == uid) {
                transaction.delete(previousClaimRef)
            }
            transaction.set(userRef, privateData, SetOptions.merge())

            val publicRef = publicProfileDocument(uid)
            if (publicProfile == false) {
                transaction.delete(publicRef)
            } else if (isPublic) {
                val publicData = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "username" to cleanUsername,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "displayName" to (displayName?.trim()?.take(40)
                        ?: privateSnapshot.getString("displayName") ?: "MARCO User"),
                    "showFavorites" to (showFavorites
                        ?: privateSnapshot.getBoolean("showFavorites") ?: true)
                )
                publicData["bio"] = bio?.trim()?.take(160)
                    ?: privateSnapshot.getString("bio").orEmpty()
                val resolvedPhotoUrl = photoUrl?.trim()?.takeIf(String::isNotBlank)
                    ?: privateSnapshot.getString("photoUrl")
                resolvedPhotoUrl?.let { publicData["photoUrl"] = it }
                val resolvedAvatar = photoData?.takeIf(String::isNotBlank)
                    ?: privateSnapshot.getString("photoData")
                if (resolvedAvatar != null) publicData["photoData"] = resolvedAvatar
                if ((showFavorites ?: privateSnapshot.getBoolean("showFavorites")) == false) {
                    publicData["favoriteGames"] = FieldValue.delete()
                } else {
                    favoriteGames?.let { publicData["favoriteGames"] = it.take(30) }
                }
                transaction.set(publicRef, publicData, SetOptions.merge())
            }
            null
        }.continueWithTask { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("تعذر حجز اسم المستخدم")
            Tasks.forResult<Void>(null)
        }
    }

    /** Follow edges are stored once under the follower UID; counts are derived from Firestore. */
    fun setFollowing(followerUid: String, targetUid: String, follow: Boolean): Task<Void> {
        if (followerUid.isBlank() || targetUid.isBlank() || followerUid == targetUid) {
            return Tasks.forException(IllegalArgumentException("معرّف المتابعة غير صالح"))
        }
        val edge = firestore.collection("userFollows")
            .document(followerUid)
            .collection("following")
            .document(targetUid)
        return if (follow) {
            edge.set(mapOf(
                "uid" to followerUid,
                "targetUid" to targetUid,
                "createdAt" to FieldValue.serverTimestamp()
            ))
        } else edge.delete()
    }

    fun isFollowing(followerUid: String, targetUid: String): Task<Boolean> {
        if (followerUid.isBlank() || targetUid.isBlank() || followerUid == targetUid) {
            return Tasks.forResult(false)
        }
        return firestore.collection("userFollows")
            .document(followerUid)
            .collection("following")
            .document(targetUid)
            .get()
            .continueWith { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("تعذر تحميل حالة المتابعة")
                task.result.exists()
            }
    }

    /** Returns follower count first, then following count, both computed server-side. */
    fun loadFollowCounts(uid: String): Task<Pair<Long, Long>> {
        if (uid.isBlank()) return Tasks.forResult(0L to 0L)
        val followers = firestore.collectionGroup("following")
            .whereEqualTo("targetUid", uid)
            .count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER)
        val following = firestore.collection("userFollows")
            .document(uid)
            .collection("following")
            .count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER)
        return Tasks.whenAllSuccess<com.google.firebase.firestore.AggregateQuerySnapshot>(followers, following)
            .continueWith { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("تعذر تحميل إحصائيات المتابعة")
                val values = task.result
                values[0].count to values[1].count
            }
    }

    /** Refreshes cached public counts from server aggregates, only for the profile owner. */
    fun refreshPublicFollowCounts(uid: String): Task<Void> {
        if (uid.isBlank()) return Tasks.forResult(null)
        return loadUser(uid).continueWithTask { userTask ->
            if (!userTask.isSuccessful) {
                throw userTask.exception ?: IllegalStateException("تعذر قراءة إعدادات الملف")
            }
            if (userTask.result.getBoolean("publicProfile") != true) {
                return@continueWithTask Tasks.forResult<Void>(null)
            }
            loadFollowCounts(uid).continueWithTask { countsTask ->
                if (!countsTask.isSuccessful) {
                    throw countsTask.exception ?: IllegalStateException("تعذر تحديث إحصائيات المتابعة")
                }
                loadPublicProfile(uid).continueWithTask { profileTask ->
                    if (!profileTask.isSuccessful) {
                        throw profileTask.exception ?: IllegalStateException("تعذر قراءة الملف العام")
                    }
                    if (!profileTask.result.exists()) {
                        return@continueWithTask Tasks.forResult<Void>(null)
                    }
                    val counts = countsTask.result
                    publicProfileDocument(uid).update(
                        mapOf(
                            "followerCount" to counts.first,
                            "followingCount" to counts.second,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
        }
    }

    /** Lists follower UIDs for the signed-in account only; Firestore rules enforce this boundary. */
    fun loadFollowerIds(uid: String): Task<List<String>> {
        if (uid.isBlank()) return Tasks.forResult(emptyList())
        return firestore.collectionGroup("following")
            .whereEqualTo("targetUid", uid)
            .get()
            .continueWith { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("تعذر تحميل المتابعين")
                task.result.documents.mapNotNull { it.getString("uid") }.distinct()
            }
    }

    /** Lists accounts followed by the signed-in account only. */
    fun loadFollowingIds(uid: String): Task<List<String>> {
        if (uid.isBlank()) return Tasks.forResult(emptyList())
        return firestore.collection("userFollows").document(uid)
            .collection("following").get()
            .continueWith { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("تعذر تحميل قائمة المتابعة")
                task.result.documents.mapNotNull { it.getString("targetUid") }.distinct()
            }
    }

    /** Refreshes the public favorite preview only when it differs from the device library. */
    fun syncPublicFavorites(
        uid: String,
        displayName: String?,
        bio: String?,
        photoUrl: String?,
        photoData: String?,
        favoriteGames: List<Map<String, Any>>
    ): Task<Void> {
        return loadUser(uid).continueWithTask { privateTask ->
            if (!privateTask.isSuccessful) {
                throw privateTask.exception ?: IllegalStateException("تعذر قراءة إعدادات الملف")
            }
            val privateProfile = privateTask.result
            if (privateProfile.getBoolean("publicProfile") != true) {
                return@continueWithTask Tasks.forResult<Void>(null)
            }
            loadPublicProfile(uid).continueWithTask { publicTask ->
                if (!publicTask.isSuccessful) {
                    throw publicTask.exception ?: IllegalStateException("تعذر قراءة الملف العام")
                }
                val existing = publicTask.result
                val normalized = favoriteGames.take(30)
                val privateName = privateProfile.getString("displayName") ?: displayName
                val privateBio = privateProfile.getString("bio") ?: bio
                val privatePhotoUrl = photoUrl ?: privateProfile.getString("photoUrl")
                val privatePhotoData = privateProfile.getString("photoData") ?: photoData
                val favoritesMatch = if (privateProfile.getBoolean("showFavorites") == false) {
                    !existing.contains("favoriteGames")
                } else {
                    (existing.get("favoriteGames") as? List<*>) == normalized
                }
                val needsSync =
                    !favoritesMatch ||
                    existing.getString("displayName") != privateName ||
                    existing.getString("bio").orEmpty() != privateBio.orEmpty() ||
                    existing.getString("photoUrl") != privatePhotoUrl ||
                    existing.getString("photoData") != privatePhotoData
                if (!needsSync) {
                    return@continueWithTask Tasks.forResult<Void>(null)
                }
                saveProfile(
                    uid = uid,
                    displayName = privateName,
                    bio = privateBio,
                    photoUrl = privatePhotoUrl,
                    photoData = privatePhotoData,
                    publicProfile = true,
                    showFavorites = privateProfile.getBoolean("showFavorites") != false,
                    favoriteGames = normalized
                )
            }
        }
    }

    private fun deleteFollowEdges(uid: String): Task<Void> {
        val outgoing = firestore.collection("userFollows")
            .document(uid)
            .collection("following")
            .get()
        val incoming = firestore.collectionGroup("following")
            .whereEqualTo("targetUid", uid)
            .get()
        return Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(outgoing, incoming)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("تعذر تحميل علاقات المتابعة")
                }
                val documents = task.result.flatMap { it.documents }.distinctBy { it.reference.path }
                documents.chunked(450).fold(Tasks.forResult<Void>(null)) { previous, chunk ->
                    previous.continueWithTask { previousTask ->
                        if (!previousTask.isSuccessful) {
                            throw previousTask.exception ?: IllegalStateException("تعذر حذف علاقات المتابعة")
                        }
                        val batch = firestore.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit()
                    }
                }
            }
    }

    private fun deleteOwnedRatings(uid: String): Task<Void> {
        return firestore.collectionGroup("ratings")
            .whereEqualTo("uid", uid)
            .get()
            .continueWithTask { queryTask ->
                if (!queryTask.isSuccessful) {
                    throw queryTask.exception ?: IllegalStateException("تعذر تحميل تقييمات الحساب")
                }
                queryTask.result.documents.chunked(450).fold(Tasks.forResult<Void>(null)) { previous, chunk ->
                    previous.continueWithTask { previousTask ->
                        if (!previousTask.isSuccessful) {
                            throw previousTask.exception ?: IllegalStateException("تعذر حذف تقييمات الحساب")
                        }
                        val batch = firestore.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit()
                    }
                }
            }
    }

    /** Remove social edges and profile documents before the Firebase Auth account is deleted. */
    fun deleteUserData(uid: String): Task<Void> =
        userDocument(uid)
            .collection(SETTINGS_COLLECTION)
            .document(PREFERENCES_DOCUMENT)
            .delete()
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("تعذر حذف إعدادات الحساب")
                }
                deleteFollowEdges(uid)
            }
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("تعذر حذف علاقات المتابعة")
                }
                deleteOwnedRatings(uid)
            }
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("تعذر حذف تقييمات الحساب")
                }
                publicProfileDocument(uid).delete()
            }
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("تعذر حذف الملف العام")
                }
                loadUser(uid).continueWithTask { profileTask ->
                    if (!profileTask.isSuccessful) {
                        throw profileTask.exception ?: IllegalStateException("تعذر قراءة اسم المستخدم")
                    }
                    val key = profileTask.result.getString("username")?.let(::usernameKey)
                    if (key.isNullOrBlank()) Tasks.forResult<Void>(null)
                    else firestore.collection(USERNAMES_COLLECTION).document(key).delete()
                }
            }
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("تعذر تحرير اسم المستخدم")
                }
                userDocument(uid).delete()
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
        const val MAX_PUBLIC_AVATAR_CHARS = 180_000
        const val USERS_COLLECTION = "users"
        const val USERNAMES_COLLECTION = "usernames"
        val USERNAME_PATTERN = Regex("[a-z0-9][a-z0-9_]{2,19}")
        const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
        const val SETTINGS_COLLECTION = "settings"
        const val PREFERENCES_DOCUMENT = "preferences"
    }
}
