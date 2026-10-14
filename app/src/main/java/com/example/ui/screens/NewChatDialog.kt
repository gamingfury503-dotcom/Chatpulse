package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.UserProfile
import com.example.ui.components.UserAvatar

@Composable
fun NewChatDialog(
  friends: List<UserProfile>,
  onStartDirectChat: (UserProfile) -> Unit,
  onCreateGroupChat: (title: String, selectedFriends: List<UserProfile>) -> Unit,
  onDismiss: () -> Unit
) {
  var selectedTab by remember { mutableStateOf(0) }
  var groupTitle by remember { mutableStateOf("") }
  val selectedFriendsForGroup = remember { mutableStateListOf<UserProfile>() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (selectedTab == 0) "New Direct Chat" else "Create Group Chat",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = selectedTab) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("1-on-1") },
            icon = { androidx.compose.material3.Icon(Icons.Default.Person, contentDescription = null) }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("Group") },
            icon = { androidx.compose.material3.Icon(Icons.Default.Group, contentDescription = null) }
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (friends.isEmpty()) {
          Text(
            text = "You don't have any friends added yet. Add friends via the Friends tab using their Friend Code or email first!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
          )
        } else {
          if (selectedTab == 0) {
            Text(
              text = "Select a friend to message:",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              items(friends, key = { it.userId }) { friend ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      onStartDirectChat(friend)
                      onDismiss()
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  UserAvatar(name = friend.displayName, isOnline = friend.isOnline, size = 40.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column {
                    Text(
                      text = friend.displayName,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold
                    )
                    Text(
                      text = if (friend.isOnline) "Online" else "Offline",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          } else {
            // Group Chat Tab
            OutlinedTextField(
              value = groupTitle,
              onValueChange = { groupTitle = it },
              label = { Text("Group Title") },
              placeholder = { Text("e.g. Weekend Squad") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("group_title_input")
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Select friends to include (${selectedFriendsForGroup.size} selected):",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              items(friends, key = { it.userId }) { friend ->
                val isSelected = selectedFriendsForGroup.contains(friend)
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      if (isSelected) selectedFriendsForGroup.remove(friend)
                      else selectedFriendsForGroup.add(friend)
                    }
                    .padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Checkbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                      if (checked) selectedFriendsForGroup.add(friend)
                      else selectedFriendsForGroup.remove(friend)
                    }
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  UserAvatar(name = friend.displayName, isOnline = friend.isOnline, size = 32.dp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleSmall
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (selectedTab == 1 && friends.isNotEmpty()) {
        Button(
          onClick = {
            if (groupTitle.isNotBlank() && selectedFriendsForGroup.isNotEmpty()) {
              onCreateGroupChat(groupTitle, selectedFriendsForGroup.toList())
              onDismiss()
            }
          },
          enabled = groupTitle.isNotBlank() && selectedFriendsForGroup.isNotEmpty(),
          modifier = Modifier.testTag("create_group_button")
        ) {
          Text("Create Group")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
