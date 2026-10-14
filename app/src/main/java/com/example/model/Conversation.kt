package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Conversation(
  @DocumentId val id: String = "",
  val memberIds: List<String> = emptyList(),
  val memberNames: Map<String, String> = emptyMap(),
  val lastMessage: String = "",
  val lastMessageSenderId: String = "",
  @ServerTimestamp val lastMessageTimestamp: Timestamp? = null,
  val isGroup: Boolean = false,
  val groupTitle: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
