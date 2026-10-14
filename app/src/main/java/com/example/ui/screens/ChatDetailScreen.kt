package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatFriendlyTimestamp
import com.example.ui.theme.MyBubbleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
  conversation: Conversation,
  currentUserId: String,
  messages: List<ChatMessage>,
  onSendMessage: (String) -> Unit,
  onAddReaction: (messageId: String, emoji: String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)

  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  var selectedMessageForReaction by remember { mutableStateOf<ChatMessage?>(null) }

  val chatTitle = if (conversation.isGroup) {
    conversation.groupTitle ?: "Group Chat"
  } else {
    conversation.memberNames.filterKeys { it != currentUserId }.values.firstOrNull() ?: "Friend"
  }

  // Auto-scroll to bottom on new messages
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (conversation.isGroup) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.Default.Group,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(22.dp)
                )
              }
            } else {
              UserAvatar(name = chatTitle, size = 38.dp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = chatTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
              )
              Text(
                text = if (conversation.isGroup) "${conversation.memberIds.size} members" else "Active chat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        // Quick reaction bar if selected
        if (selectedMessageForReaction != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 8.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            val emojis = listOf("❤️", "👍", "😂", "🔥", "🎉", "😮")
            emojis.forEach { emoji ->
              Text(
                text = emoji,
                fontSize = 24.sp,
                modifier = Modifier
                  .clip(CircleShape)
                  .clickable {
                    val msgId = selectedMessageForReaction?.id
                    if (msgId != null) {
                      onAddReaction(msgId, emoji)
                    }
                    selectedMessageForReaction = null
                  }
                  .padding(4.dp)
              )
            }
            IconButton(
              onClick = { selectedMessageForReaction = null },
              modifier = Modifier.size(24.dp)
            ) {
              Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }

        // Message Input Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("Type a message...") },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
              onSend = {
                if (inputText.isNotBlank()) {
                  onSendMessage(inputText)
                  inputText = ""
                }
              }
            ),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("message_input_field")
          )

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = {
              if (inputText.isNotBlank()) {
                onSendMessage(inputText)
                inputText = ""
              }
            },
            enabled = inputText.isNotBlank(),
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(
                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
              )
              .testTag("send_message_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Send",
              tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (messages.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "Say hello! 👋",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Send your first message to start the conversation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(messages, key = { it.id }) { msg ->
          MessageBubble(
            message = msg,
            isMine = msg.senderId == currentUserId,
            showSenderName = conversation.isGroup && msg.senderId != currentUserId,
            onLongPress = {
              selectedMessageForReaction = msg
            }
          )
        }
      }
    }
  }
}

@Composable
fun MessageBubble(
  message: ChatMessage,
  isMine: Boolean,
  showSenderName: Boolean,
  onLongPress: () -> Unit
) {
  val alignment = if (isMine) Alignment.End else Alignment.Start
  val bubbleColor = if (isMine) MyBubbleColor else MaterialTheme.colorScheme.surfaceVariant
  val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

  val bubbleShape = if (isMine) {
    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
  } else {
    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalAlignment = alignment
  ) {
    if (showSenderName) {
      Text(
        text = message.senderName,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
      )
    }

    Box(
      modifier = Modifier
        .widthIn(max = 280.dp)
        .clip(bubbleShape)
        .background(bubbleColor)
        .clickable { onLongPress() }
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .testTag("chat_message_bubble_${message.id}")
    ) {
      Column {
        Text(
          text = message.text,
          color = textColor,
          style = MaterialTheme.typography.bodyLarge,
          fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier.align(Alignment.End),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (!message.reactions.isNullOrBlank()) {
            Box(
              modifier = Modifier
                .padding(end = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
              Text(
                text = message.reactions ?: "",
                fontSize = 12.sp
              )
            }
          }

          Text(
            text = formatFriendlyTimestamp(message.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = if (isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 10.sp
          )
        }
      }
    }
  }
}
