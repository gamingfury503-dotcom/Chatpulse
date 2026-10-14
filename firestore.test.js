const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read conversations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("conversations").get());
});

test("Unauthenticated user: cannot create user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@test.com",
      friendCode: "ALICE1",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: Alice can create and read her own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@test.com",
      friendCode: "ALICE1",
      isOnline: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: Alice cannot spoof Bob profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@test.com",
      friendCode: "BOB123",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Conversations: Alice and Bob can read conversation they belong to", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc("chat_1").set({
      memberIds: [ALICE_UID, BOB_UID],
      lastMessage: "Hello Bob",
      lastMessageSenderId: ALICE_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("conversations").doc("chat_1").get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("conversations").doc("chat_1").get());
});

test("Conversations: Charlie cannot read Alice and Bob conversation", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc("chat_1").set({
      memberIds: [ALICE_UID, BOB_UID],
      lastMessage: "Hello Bob",
      lastMessageSenderId: ALICE_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();
  await assertFails(charlieDb.collection("conversations").doc("chat_1").get());
});

test("Messages: Alice can send message in shared conversation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("conversations").doc("chat_1").collection("messages").doc("msg_1").set({
      conversationId: "chat_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Hey Bob! How are you?",
      memberIds: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
    })
  );
});

test("Messages: Charlie cannot send message or read messages in Alice and Bob chat", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc("chat_1").collection("messages").doc("msg_1").set({
      conversationId: "chat_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Secret message",
      memberIds: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
    });
  });

  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();
  await assertFails(
    charlieDb.collection("conversations").doc("chat_1").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc("chat_1").collection("messages").doc("msg_2").set({
      conversationId: "chat_1",
      senderId: "charlie_789",
      senderName: "Charlie",
      text: "Intruder!",
      memberIds: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
    })
  );
});

test("Friend Requests: Alice can send friend request to Bob", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("friend_requests").doc("req_1").set({
      senderId: ALICE_UID,
      senderName: "Alice",
      senderEmail: "alice@test.com",
      receiverId: BOB_UID,
      receiverEmail: "bob@test.com",
      status: "pending",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Friend Requests: Charlie cannot view or accept Alice-Bob friend request", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("friend_requests").doc("req_1").set({
      senderId: ALICE_UID,
      senderName: "Alice",
      senderEmail: "alice@test.com",
      receiverId: BOB_UID,
      receiverEmail: "bob@test.com",
      status: "pending",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();
  await assertFails(charlieDb.collection("friend_requests").doc("req_1").get());
  await assertFails(
    charlieDb.collection("friend_requests").doc("req_1").update({
      status: "accepted",
      updatedAt: new Date(),
    })
  );
});
