package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.model.UserProfile
import com.example.ui.auth.signOut
import com.example.ui.components.UserAvatar
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  viewModel: ChatViewModel,
  onSignedOut: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  val currentProfile by viewModel.currentProfile.collectAsState()
  val conversations by viewModel.conversations.collectAsState()
  val incomingRequests by viewModel.incomingRequests.collectAsState()
  val outgoingRequests by viewModel.outgoingRequests.collectAsState()
  val activeConversation by viewModel.activeConversation.collectAsState()
  val messages by viewModel.messages.collectAsState()
  val searchResults by viewModel.searchResults.collectAsState()
  val isSearching by viewModel.isSearching.collectAsState()
  val snackbarMessage by viewModel.snackbarMessage.collectAsState()

  var selectedTab by remember { mutableIntStateOf(0) }
  var showNewChatDialog by remember { mutableStateOf(false) }

  // Extract friends from conversations memberNames
  val friendsList = remember(conversations, viewModel.currentUserId) {
    val map = mutableMapOf<String, UserProfile>()
    conversations.forEach { conv ->
      if (!conv.isGroup) {
        conv.memberNames.forEach { (uid, name) ->
          if (uid != viewModel.currentUserId && !map.containsKey(uid)) {
            map[uid] = UserProfile(
              userId = uid,
              displayName = name,
              isOnline = true,
              statusMessage = "Friend"
            )
          }
        }
      }
    }
    map.values.toList()
  }

  // Handle snackbar messages
  LaunchedEffect(snackbarMessage) {
    snackbarMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearSnackbar()
    }
  }

  // If a chat conversation is actively opened, show full-screen ChatDetailScreen
  if (activeConversation != null) {
    ChatDetailScreen(
      conversation = activeConversation!!,
      currentUserId = viewModel.currentUserId,
      messages = messages,
      onSendMessage = { text -> viewModel.sendMessage(text) },
      onAddReaction = { msgId, emoji -> viewModel.addReaction(msgId, emoji) },
      onBack = { viewModel.closeConversation() }
    )
  } else {
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Chat,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(18.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "ChatPulse",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
            }
          },
          actions = {
            // Online status indicator chip
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(end = 12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(if (currentProfile?.isOnline == true) OnlineGreen else OfflineGray)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (currentProfile?.isOnline == true) "Online" else "Offline",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        )
      },
      bottomBar = {
        NavigationBar(
          containerColor = MaterialTheme.colorScheme.surface
        ) {
          NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            icon = {
              Icon(
                if (selectedTab == 0) Icons.Default.Chat else Icons.Outlined.Chat,
                contentDescription = "Chats"
              )
            },
            label = { Text("Chats") },
            modifier = Modifier.testTag("nav_chats_tab")
          )

          NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            icon = {
              BadgedBox(
                badge = {
                  if (incomingRequests.isNotEmpty()) {
                    Badge { Text("${incomingRequests.size}") }
                  }
                }
              ) {
                Icon(
                  if (selectedTab == 1) Icons.Default.Group else Icons.Outlined.Group,
                  contentDescription = "Friends"
                )
              }
            },
            label = { Text("Friends") },
            modifier = Modifier.testTag("nav_friends_tab")
          )

          NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            icon = {
              Icon(
                if (selectedTab == 2) Icons.Default.Person else Icons.Outlined.Person,
                contentDescription = "Profile"
              )
            },
            label = { Text("Profile") },
            modifier = Modifier.testTag("nav_profile_tab")
          )
        }
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      modifier = modifier.fillMaxSize()
    ) { paddingValues ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        when (selectedTab) {
          0 -> ChatsListTab(
            currentUserId = viewModel.currentUserId,
            conversations = conversations,
            onOpenConversation = { conv -> viewModel.openConversation(conv) },
            onStartNewChat = { showNewChatDialog = true }
          )
          1 -> FriendsTab(
            currentProfile = currentProfile,
            incomingRequests = incomingRequests,
            outgoingRequests = outgoingRequests,
            friendsList = friendsList,
            searchResults = searchResults,
            isSearching = isSearching,
            onSearch = { q -> viewModel.searchUsers(q) },
            onClearSearch = { viewModel.clearSearchResults() },
            onSendRequest = { user -> viewModel.sendFriendRequest(user) },
            onRespondRequest = { req, accept -> viewModel.respondFriendRequest(req, accept) },
            onMessageFriend = { friend -> viewModel.startDirectChatWithFriend(friend) },
            onShowSnackbar = { msg -> viewModel.showMessage(msg) }
          )
          2 -> ProfileTab(
            profile = currentProfile,
            onUpdateStatus = { newStatus -> viewModel.updateStatusMessage(newStatus) },
            onUpdateDisplayName = { newName -> viewModel.updateDisplayName(newName) },
            onToggleOnline = { isOnline -> viewModel.setOnlinePresence(isOnline) },
            onSignOut = {
              viewModel.setOnlinePresence(false)
              signOut(context, credentialManager, onSignOutComplete = onSignedOut, scope = scope)
            },
            onShowSnackbar = { msg -> viewModel.showMessage(msg) }
          )
        }
      }
    }
  }

  // New Chat Dialog
  if (showNewChatDialog) {
    NewChatDialog(
      friends = friendsList,
      onStartDirectChat = { friend ->
        viewModel.startDirectChatWithFriend(friend)
        showNewChatDialog = false
      },
      onCreateGroupChat = { title, selectedFriends ->
        viewModel.createGroupChat(title, selectedFriends)
        showNewChatDialog = false
      },
      onDismiss = { showNewChatDialog = false }
    )
  }
}
