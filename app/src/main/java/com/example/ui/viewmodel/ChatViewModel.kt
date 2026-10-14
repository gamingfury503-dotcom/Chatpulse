package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.FriendRequest
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

class ChatViewModel(
  private val repository: ChatRepository,
  val currentUserId: String
) : ViewModel() {

  private val _currentProfile = MutableStateFlow<UserProfile?>(null)
  val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

  val conversations: StateFlow<List<Conversation>> = repository.observeConversations(currentUserId)
    .catch { e ->
      Log.w(TAG, "Error observing conversations", e)
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val incomingRequests: StateFlow<List<FriendRequest>> = repository.observeIncomingFriendRequests(currentUserId)
    .catch { e ->
      Log.w(TAG, "Error observing incoming requests", e)
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val outgoingRequests: StateFlow<List<FriendRequest>> = repository.observeOutgoingFriendRequests(currentUserId)
    .catch { e ->
      Log.w(TAG, "Error observing outgoing requests", e)
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private val _activeConversation = MutableStateFlow<Conversation?>(null)
  val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private var messagesJob: Job? = null

  private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
  val searchResults: StateFlow<List<UserProfile>> = _searchResults.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  init {
    loadOrInitUserProfile()
  }

  fun clearSnackbar() {
    _snackbarMessage.value = null
  }

  fun showMessage(msg: String) {
    _snackbarMessage.value = msg
  }

  private fun loadOrInitUserProfile() {
    viewModelScope.launch {
      val authUser = Firebase.auth.currentUser ?: return@launch
      val existing = repository.getUserProfile(currentUserId)
      if (existing != null) {
        _currentProfile.value = existing
        // Mark online
        repository.setOnlinePresence(true)
      } else {
        // Initial setup for new user
        val generatedCode = "CP" + UUID.randomUUID().toString().replace("-", "").take(5).uppercase()
        val displayName = authUser.displayName ?: authUser.email?.substringBefore("@") ?: "User"
        val newProfile = UserProfile(
          userId = currentUserId,
          displayName = displayName,
          email = authUser.email ?: "",
          photoUrl = authUser.photoUrl?.toString(),
          statusMessage = "Hey there! I am using ChatPulse.",
          friendCode = generatedCode,
          isOnline = true
        )
        repository.saveUserProfile(newProfile)
        _currentProfile.value = newProfile
      }

      // Continuously observe current user profile updates
      repository.observeUserProfile(currentUserId).collect { updated ->
        if (updated != null) {
          _currentProfile.value = updated
        }
      }
    }
  }

  fun setOnlinePresence(isOnline: Boolean) {
    viewModelScope.launch {
      repository.setOnlinePresence(isOnline)
      _currentProfile.value = _currentProfile.value?.copy(isOnline = isOnline)
    }
  }

  fun updateStatusMessage(status: String) {
    viewModelScope.launch {
      val profile = _currentProfile.value ?: return@launch
      val updated = profile.copy(statusMessage = status)
      val result = repository.saveUserProfile(updated)
      if (result.isSuccess) {
        _currentProfile.value = updated
        _snackbarMessage.value = "Status message updated!"
      } else {
        _snackbarMessage.value = "Failed to update status."
      }
    }
  }

  fun updateDisplayName(name: String) {
    viewModelScope.launch {
      val profile = _currentProfile.value ?: return@launch
      val updated = profile.copy(displayName = name.trim())
      val result = repository.saveUserProfile(updated)
      if (result.isSuccess) {
        _currentProfile.value = updated
        _snackbarMessage.value = "Name updated!"
      }
    }
  }

  // --- Conversations ---

  fun openConversation(conversation: Conversation) {
    _activeConversation.value = conversation
    messagesJob?.cancel()
    messagesJob = viewModelScope.launch {
      repository.observeMessages(conversation.id)
        .catch { e ->
          Log.w(TAG, "Error observing messages for ${conversation.id}", e)
          emit(emptyList())
        }
        .collect { list ->
          _messages.value = list
        }
    }
  }

  fun closeConversation() {
    _activeConversation.value = null
    messagesJob?.cancel()
    _messages.value = emptyList()
  }

  fun sendMessage(text: String) {
    val conv = _activeConversation.value ?: return
    val profile = _currentProfile.value ?: return
    val cleanText = text.trim()
    if (cleanText.isBlank()) return

    viewModelScope.launch {
      val result = repository.sendMessage(
        conversationId = conv.id,
        sender = profile,
        text = cleanText,
        memberIds = conv.memberIds
      )
      if (result.isFailure) {
        _snackbarMessage.value = "Failed to send message: ${result.exceptionOrNull()?.message}"
      }
    }
  }

  fun addReaction(messageId: String, emoji: String) {
    val conv = _activeConversation.value ?: return
    viewModelScope.launch {
      repository.addMessageReaction(conv.id, messageId, emoji)
    }
  }

  // --- Friends & Direct Chat ---

  fun searchUsers(query: String) {
    val clean = query.trim()
    if (clean.isBlank()) {
      _searchResults.value = emptyList()
      return
    }
    viewModelScope.launch {
      _isSearching.value = true
      val results = repository.searchUserByQuery(clean)
      _searchResults.value = results
      _isSearching.value = false
    }
  }

  fun clearSearchResults() {
    _searchResults.value = emptyList()
  }

  fun sendFriendRequest(targetUser: UserProfile) {
    val myProfile = _currentProfile.value ?: return
    viewModelScope.launch {
      val result = repository.sendFriendRequest(myProfile, targetUser)
      if (result.isSuccess) {
        _snackbarMessage.value = "Friend request sent to ${targetUser.displayName}!"
      } else {
        _snackbarMessage.value = result.exceptionOrNull()?.message ?: "Could not send friend request"
      }
    }
  }

  fun respondFriendRequest(request: FriendRequest, accept: Boolean) {
    val myProfile = _currentProfile.value ?: return
    viewModelScope.launch {
      val result = repository.respondToFriendRequest(request.id, accept, myProfile)
      if (result.isSuccess) {
        _snackbarMessage.value = if (accept) {
          "Accepted! You are now friends with ${request.senderName}."
        } else {
          "Declined friend request."
        }
      } else {
        _snackbarMessage.value = "Failed to respond: ${result.exceptionOrNull()?.message}"
      }
    }
  }

  fun startDirectChatWithFriend(friendProfile: UserProfile) {
    val myProfile = _currentProfile.value ?: return
    viewModelScope.launch {
      try {
        val convId = repository.getOrCreateDirectConversation(myProfile, friendProfile)
        val conv = Conversation(
          id = convId,
          memberIds = listOf(myProfile.userId, friendProfile.userId),
          memberNames = mapOf(myProfile.userId to myProfile.displayName, friendProfile.userId to friendProfile.displayName),
          lastMessage = "Conversation started"
        )
        openConversation(conv)
      } catch (e: Exception) {
        _snackbarMessage.value = "Could not open chat: ${e.message}"
      }
    }
  }

  fun createGroupChat(groupTitle: String, selectedFriends: List<UserProfile>) {
    val myProfile = _currentProfile.value ?: return
    if (groupTitle.isBlank()) {
      _snackbarMessage.value = "Please enter a group title."
      return
    }
    if (selectedFriends.isEmpty()) {
      _snackbarMessage.value = "Please select at least one friend."
      return
    }

    viewModelScope.launch {
      val result = repository.createGroupConversation(myProfile, selectedFriends, groupTitle.trim())
      if (result.isSuccess) {
        val convId = result.getOrThrow()
        val allMembers = listOf(myProfile) + selectedFriends
        val conv = Conversation(
          id = convId,
          memberIds = allMembers.map { it.userId },
          memberNames = allMembers.associate { it.userId to it.displayName },
          isGroup = true,
          groupTitle = groupTitle.trim(),
          lastMessage = "Group created"
        )
        openConversation(conv)
        _snackbarMessage.value = "Group \"$groupTitle\" created!"
      } else {
        _snackbarMessage.value = "Failed to create group: ${result.exceptionOrNull()?.message}"
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    messagesJob?.cancel()
  }

  companion object {
    private const val TAG = "ChatViewModel"
  }
}
