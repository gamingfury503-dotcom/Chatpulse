package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.ChatRepository
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveUserProfile_authenticatedUser_createsAndReadsProfile() = runBlocking {
    val uniqueId = UUID.randomUUID().toString().take(6)
    val email = "alice_$uniqueId@test.com"
    val uid = signInTestUser(email)
    val repo = ChatRepository(firestore)

    val profile = UserProfile(
      userId = uid,
      displayName = "Alice $uniqueId",
      email = email,
      friendCode = "AL$uniqueId".uppercase(),
      isOnline = true
    )

    val saveResult = repo.saveUserProfile(profile)
    assertTrue("Save profile should succeed", saveResult.isSuccess)

    val fetched = repo.getUserProfile(uid)
    assertNotNull(fetched)
    assertEquals(uid, fetched?.userId)
    assertEquals(profile.displayName, fetched?.displayName)
  }

  @Test
  fun directChat_and_sendMessage_success() = runBlocking {
    val email1 = "alice_${UUID.randomUUID().toString().take(6)}@test.com"
    val uid1 = signInTestUser(email1)
    val user1 = UserProfile(userId = uid1, displayName = "Alice", email = email1, friendCode = "ALICE1")
    val bobUid = "bob_${UUID.randomUUID().toString().take(6)}"
    val user2 = UserProfile(userId = bobUid, displayName = "Bob", email = "bob@test.com", friendCode = "BOB123")
    val repo = ChatRepository(firestore)
    repo.saveUserProfile(user1)

    val convId = repo.getOrCreateDirectConversation(user1, user2)
    assertNotNull(convId)

    val sendResult = repo.sendMessage(convId, user1, "Hello Bob!", listOf(uid1, bobUid))
    assertTrue("Send message should succeed", sendResult.isSuccess)

    val messages = withTimeout(5000L) {
      repo.observeMessages(convId).first { it.isNotEmpty() }
    }
    assertEquals(1, messages.size)
    assertEquals("Hello Bob!", messages[0].text)
  }

  @Test
  fun conversationAccess_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    try {
      withTimeout(5000L) {
        firestore.collection("conversations").document("test_conv").get().await()
      }
      fail("Should have failed with PERMISSION_DENIED")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }
}
