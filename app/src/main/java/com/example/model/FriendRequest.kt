package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FriendRequest(
  @DocumentId val id: String = "",
  val senderId: String = "",
  val senderName: String = "",
  val senderEmail: String = "",
  val receiverId: String = "",
  val receiverEmail: String = "",
  val status: String = "pending",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
