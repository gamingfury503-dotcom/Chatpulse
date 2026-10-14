package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.FriendRequest
import com.example.model.UserProfile
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  // --- Profile Operations ---

  fun observeUserProfile(userId: String): Flow<UserProfile?> {
    val docRef = db.collection("users").document(userId)
    return docRef.snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .catch { e ->
        if (e is Exception) handleFirestoreError(e, OperationType.GET, docRef.path)
        throw e
      }
  }

  suspend fun getUserProfile(userId: String): UserProfile? {
    val docRef = db.collection("users").document(userId)
    return try {
      val snapshot = docRef.get().await()
      if (snapshot.exists()) {
        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else {
        null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      null
    }
  }

  suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        // Update
        val existingCreated = existing.getTimestamp("createdAt")
        val data = mapOf(
          "userId" to uid,
          "displayName" to profile.displayName,
          "email" to profile.email,
          "photoUrl" to profile.photoUrl,
          "statusMessage" to profile.statusMessage,
          "friendCode" to profile.friendCode,
          "isOnline" to profile.isOnline,
          "lastSeen" to FieldValue.serverTimestamp(),
          "createdAt" to (existingCreated ?: FieldValue.serverTimestamp()),
          "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        docRef.set(data).await()
      } else {
        // Create
        val data = mapOf(
          "userId" to uid,
          "displayName" to profile.displayName,
          "email" to profile.email,
          "photoUrl" to profile.photoUrl,
          "statusMessage" to profile.statusMessage,
          "friendCode" to profile.friendCode,
          "isOnline" to profile.isOnline,
          "lastSeen" to FieldValue.serverTimestamp(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        docRef.set(data).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun setOnlinePresence(isOnline: Boolean) {
    val uid = auth.currentUser?.uid ?: return
    val docRef = db.collection("users").document(uid)
    try {
      docRef.update(
        "isOnline", isOnline,
        "lastSeen", FieldValue.serverTimestamp(),
        "updatedAt", FieldValue.serverTimestamp()
      ).await()
    } catch (e: Exception) {
      Log.w("ChatRepo", "Failed to update online presence: ${e.message}")
    }
  }

  suspend fun searchUserByQuery(queryText: String): List<UserProfile> {
    val cleanQuery = queryText.trim()
    if (cleanQuery.isBlank()) return emptyList()

    val currentUid = requireUserId()
    return try {
      val list = mutableListOf<UserProfile>()
      // Search by friendCode
      val friendCodeSnap = db.collection("users")
        .whereEqualTo("friendCode", cleanQuery.uppercase())
        .get().await()
      for (doc in friendCodeSnap.documents) {
        val user = doc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (user != null && user.userId != currentUid && list.none { it.userId == user.userId }) {
          list.add(user)
        }
      }

      // Search by email
      val emailSnap = db.collection("users")
        .whereEqualTo("email", cleanQuery.lowercase())
        .get().await()
      for (doc in emailSnap.documents) {
        val user = doc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (user != null && user.userId != currentUid && list.none { it.userId == user.userId }) {
          list.add(user)
        }
      }
      list
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "users")
      emptyList()
    }
  }

  // --- Friend Request Operations ---

  fun observeIncomingFriendRequests(userId: String): Flow<List<FriendRequest>> {
    return db.collection("friend_requests")
      .whereEqualTo("receiverId", userId)
      .whereEqualTo("status", "pending")
      .snapshots()
      .map { it.toObjects(FriendRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { e ->
        if (e is Exception) handleFirestoreError(e, OperationType.LIST, "friend_requests")
        throw e
      }
  }

  fun observeOutgoingFriendRequests(userId: String): Flow<List<FriendRequest>> {
    return db.collection("friend_requests")
      .whereEqualTo("senderId", userId)
      .snapshots()
      .map { it.toObjects(FriendRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { e ->
        if (e is Exception) handleFirestoreError(e, OperationType.LIST, "friend_requests")
        throw e
      }
  }

  suspend fun sendFriendRequest(
    sender: UserProfile,
    receiver: UserProfile
  ): Result<String> {
    val uid = requireUserId()
    if (receiver.userId == uid) {
      return Result.failure(IllegalArgumentException("You cannot send a friend request to yourself."))
    }

    // Check if already sent
    val existing = db.collection("friend_requests")
      .whereEqualTo("senderId", uid)
      .whereEqualTo("receiverId", receiver.userId)
      .whereEqualTo("status", "pending")
      .get().await()

    if (!existing.isEmpty) {
      return Result.failure(IllegalStateException("Friend request already pending."))
    }

    val requestId = UUID.randomUUID().toString()
    val docRef = db.collection("friend_requests").document(requestId)
    return try {
      val data = mapOf(
        "senderId" to uid,
        "senderName" to sender.displayName,
        "senderEmail" to sender.email,
        "receiverId" to receiver.userId,
        "receiverEmail" to receiver.email,
        "status" to "pending",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(data).await()
      Result.success(requestId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun respondToFriendRequest(
    requestId: String,
    accept: Boolean,
    currentProfile: UserProfile
  ): Result<Unit> {
    requireUserId()
    val docRef = db.collection("friend_requests").document(requestId)
    return try {
      val snap = docRef.get().await()
      if (!snap.exists()) return Result.failure(IllegalArgumentException("Request not found"))
      val req = snap.toObject(FriendRequest::class.java)
        ?: return Result.failure(IllegalStateException("Could not parse request"))

      val newStatus = if (accept) "accepted" else "rejected"
      docRef.update(
        "status", newStatus,
        "updatedAt", FieldValue.serverTimestamp()
      ).await()

      if (accept) {
        // Automatically start or get direct conversation
        val otherUser = getUserProfile(req.senderId)
        if (otherUser != null) {
          getOrCreateDirectConversation(currentProfile, otherUser)
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  // --- Conversation Operations ---

  fun observeConversations(userId: String): Flow<List<Conversation>> {
    return db.collection("conversations")
      .whereArrayContains("memberIds", userId)
      .snapshots()
      .map { it.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { e ->
        if (e is Exception) handleFirestoreError(e, OperationType.LIST, "conversations")
        throw e
      }
  }

  suspend fun getOrCreateDirectConversation(
    user1: UserProfile,
    user2: UserProfile
  ): String {
    requireUserId()
    // Deterministic 1-on-1 ID
    val sortedUids = listOf(user1.userId, user2.userId).sorted()
    val convId = "direct_${sortedUids[0]}_${sortedUids[1]}"
    val docRef = db.collection("conversations").document(convId)

    try {
      val existing = docRef.get().await()
      if (!existing.exists()) {
        val namesMap = mapOf(
          user1.userId to user1.displayName,
          user2.userId to user2.displayName
        )
        val data = mapOf(
          "memberIds" to sortedUids,
          "memberNames" to namesMap,
          "lastMessage" to "Conversation started",
          "lastMessageSenderId" to user1.userId,
          "lastMessageTimestamp" to FieldValue.serverTimestamp(),
          "isGroup" to false,
          "groupTitle" to null,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        docRef.set(data).await()
      }
      return convId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun createGroupConversation(
    creator: UserProfile,
    otherMembers: List<UserProfile>,
    groupTitle: String
  ): Result<String> {
    requireUserId()
    val convId = "group_${UUID.randomUUID()}"
    val docRef = db.collection("conversations").document(convId)
    val allMembers = (listOf(creator) + otherMembers).distinctBy { it.userId }
    val memberIds = allMembers.map { it.userId }
    val namesMap = allMembers.associate { it.userId to it.displayName }

    return try {
      val data = mapOf(
        "memberIds" to memberIds,
        "memberNames" to namesMap,
        "lastMessage" to "Group \"$groupTitle\" created",
        "lastMessageSenderId" to creator.userId,
        "lastMessageTimestamp" to FieldValue.serverTimestamp(),
        "isGroup" to true,
        "groupTitle" to groupTitle,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      ).filterValues { it != null }
      docRef.set(data).await()
      Result.success(convId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  // --- Message Operations ---

  fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
    val uid = requireUserId()
    return db.collection("conversations").document(conversationId)
      .collection("messages")
      .whereArrayContains("memberIds", uid)
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .snapshots()
      .map { it.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { e ->
        if (e is Exception) handleFirestoreError(e, OperationType.LIST, "conversations/$conversationId/messages")
        throw e
      }
  }

  suspend fun sendMessage(
    conversationId: String,
    sender: UserProfile,
    text: String,
    memberIds: List<String>
  ): Result<String> {
    val uid = requireUserId()
    val msgId = "msg_${UUID.randomUUID()}"
    val convDoc = db.collection("conversations").document(conversationId)
    val msgDoc = convDoc.collection("messages").document(msgId)

    return try {
      val msgData = mapOf(
        "conversationId" to conversationId,
        "senderId" to uid,
        "senderName" to sender.displayName,
        "text" to text.trim(),
        "memberIds" to memberIds,
        "reactions" to null,
        "createdAt" to FieldValue.serverTimestamp()
      ).filterValues { it != null }

      msgDoc.set(msgData).await()

      // Update parent conversation snippet
      convDoc.update(
        "lastMessage", text.trim(),
        "lastMessageSenderId", uid,
        "lastMessageTimestamp", FieldValue.serverTimestamp(),
        "updatedAt", FieldValue.serverTimestamp()
      ).await()

      Result.success(msgId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgDoc.path)
      Result.failure(e)
    }
  }

  suspend fun addMessageReaction(
    conversationId: String,
    messageId: String,
    emoji: String
  ): Result<Unit> {
    requireUserId()
    val msgDoc = db.collection("conversations").document(conversationId)
      .collection("messages").document(messageId)

    return try {
      msgDoc.update("reactions", emoji).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, msgDoc.path)
      Result.failure(e)
    }
  }
}
