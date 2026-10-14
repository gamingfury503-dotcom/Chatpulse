package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.OnlineGreen
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserAvatar(
  name: String,
  isOnline: Boolean? = null,
  size: Dp = 48.dp,
  backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
  contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
  modifier: Modifier = Modifier
) {
  val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

  Box(
    modifier = modifier.size(size)
  ) {
    Box(
      modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(backgroundColor),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = initial,
        fontWeight = FontWeight.Bold,
        fontSize = (size.value * 0.42f).sp,
        color = contentColor
      )
    }

    if (isOnline != null) {
      val badgeSize = (size.value * 0.28f).dp
      Box(
        modifier = Modifier
          .size(badgeSize)
          .align(Alignment.BottomEnd)
          .clip(CircleShape)
          .background(if (isOnline) OnlineGreen else OfflineGray)
          .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
      )
    }
  }
}

fun formatFriendlyTimestamp(timestamp: Timestamp?): String {
  if (timestamp == null) return ""
  val date = timestamp.toDate()
  val now = Date()

  val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date) ==
      SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now)

  return if (sameDay) {
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
  } else {
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
  }
}
