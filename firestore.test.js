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
const CHARLIE_UID = "charlie_789";

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("directory").where("discoverable", "==", true).get());
  await assertFails(unauth.collection("connectNumbers").doc("014829315").get());
  await assertFails(unauth.collection("friendRequests").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("friendships").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("conversations").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("calls").where("participantIds", "array-contains", ALICE_UID).get());
});

test("Private user & directory profiles + unique 01xxxxxxx connectNumbers lookup", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      photoUrl: "",
      connectNumber: "014829315",
      online: true,
      lastSeen: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("directory").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      displayNameLower: "alice",
      statusText: "Available",
      photoUrl: "",
      connectNumber: "014829315",
      discoverable: true,
      online: true,
      lastSeen: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("connectNumbers").doc("014829315").set({
      number: "014829315",
      uid: ALICE_UID,
      displayName: "Alice",
      photoUrl: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid format (not starting with 01 or wrong length) rejected
  await assertFails(
    bobDb.collection("connectNumbers").doc("991234567").set({
      number: "991234567",
      uid: BOB_UID,
      displayName: "Bob",
      photoUrl: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can look up Alice by exact 9-digit number, but cannot list all numbers
  await assertSucceeds(bobDb.collection("connectNumbers").doc("014829315").get());
  await assertFails(bobDb.collection("connectNumbers").get());

  // Bob cannot hijack Alice's connectNumber or private profile
  await assertFails(
    bobDb.collection("connectNumbers").doc("014829315").update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Friend requests and friendships: uncreated lookups, send, reject/resend, accept, and isolate", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const pairId = `${ALICE_UID}_${BOB_UID}`;
  const now = new Date(Date.now() - 1000);

  // Uncreated deterministic pair get() checks must succeed for participants, fail for outsiders
  await assertSucceeds(aliceDb.collection("friendships").doc(pairId).get());
  await assertSucceeds(aliceDb.collection("friendRequests").doc(pairId).get());
  await assertSucceeds(aliceDb.collection("conversations").doc(pairId).get());
  await assertFails(charlieDb.collection("friendships").doc(pairId).get());
  await assertFails(charlieDb.collection("friendRequests").doc(pairId).get());
  await assertFails(charlieDb.collection("conversations").doc(pairId).get());

  await assertSucceeds(
    aliceDb.collection("friendRequests").doc(pairId).set({
      fromUid: ALICE_UID,
      fromName: "Alice",
      fromPhoto: "",
      fromNumber: "014829315",
      toUid: BOB_UID,
      toName: "Bob",
      toPhoto: "",
      toNumber: "019876543",
      participantIds: [ALICE_UID, BOB_UID],
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Charlie cannot read or accept Alice->Bob request
  await assertFails(charlieDb.collection("friendRequests").doc(pairId).get());
  await assertFails(
    charlieDb.collection("friendRequests").doc(pairId).update({
      status: "ACCEPTED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob rejects, then Bob initiates a new PENDING request back to Alice preserving createdAt
  await assertSucceeds(
    bobDb.collection("friendRequests").doc(pairId).update({
      status: "REJECTED",
      updatedAt: new Date(Date.now() - 480),
    })
  );
  await assertSucceeds(
    bobDb.collection("friendRequests").doc(pairId).update({
      fromUid: BOB_UID,
      fromName: "Bob",
      fromPhoto: "",
      fromNumber: "019876543",
      toUid: ALICE_UID,
      toName: "Alice",
      toPhoto: "",
      toNumber: "014829315",
      status: "PENDING",
      updatedAt: new Date(Date.now() - 450),
    })
  );

  // Alice accepts Bob's request and writes mutual friendship
  await assertSucceeds(
    aliceDb.collection("friendRequests").doc(pairId).update({
      status: "ACCEPTED",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(
    aliceDb.collection("friendships").doc(pairId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPhotos: { [ALICE_UID]: "", [BOB_UID]: "" },
      participantNumbers: { [ALICE_UID]: "014829315", [BOB_UID]: "019876543" },
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("friendships").where("participantIds", "array-contains", ALICE_UID).get()
  );
  await assertFails(charlieDb.collection("friendships").doc(pairId).get());
});

test("Conversations & rich messages: ImgBB photos, replies, reactions, typing, wallpaper", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const convId = `${ALICE_UID}_${BOB_UID}`;
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("friendships").doc(convId).set({
      participantIds: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPhotos: { [ALICE_UID]: "", [BOB_UID]: "" },
      typingBy: {},
      wallpaperBy: {},
      lastMessageText: "Sent a photo",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice sends an IMAGE message with optional empty text caption & reply metadata
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_img").set({
      conversationId: convId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "",
      imageUrl: "https://i.ibb.co/example/photo.jpg",
      messageType: "IMAGE",
      replyToId: "msg_0",
      replyToText: "Earlier msg",
      replyToSenderName: "Bob",
      reactions: {},
      deleted: false,
      participantIds: [ALICE_UID, BOB_UID],
      createdAt: now,
    })
  );

  // Bob updates typing indicator & wallpaper without needing to overwrite lastMessageSenderId
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).update({
      typingBy: { [BOB_UID]: true },
      wallpaperBy: { [BOB_UID]: "https://i.ibb.co/example/wall.jpg" },
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Bob reacts with emoji to Alice's image message
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_img").update({
      reactions: { [BOB_UID]: "❤️" },
    })
  );

  // Bob cannot delete Alice's message; Charlie cannot read or inject
  await assertFails(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_img").update({
      deleted: true,
      text: "Message deleted",
      imageUrl: "",
    })
  );
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
});

test("WebRTC calls & ICE candidates: signaling lifecycle and participant isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const callId = "call_001";
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).set({
      callerId: ALICE_UID,
      callerName: "Alice",
      callerPhoto: "",
      calleeId: BOB_UID,
      calleeName: "Bob",
      calleePhoto: "",
      participantIds: [ALICE_UID, BOB_UID],
      callType: "VIDEO",
      status: "RINGING",
      offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      offerType: "offer",
      durationSeconds: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("calls").doc(callId).update({
      status: "CONNECTED",
      answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
      answerType: "answer",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).update({
      status: "ENDED",
      durationSeconds: 42,
      updatedAt: new Date(Date.now() - 400),
      endedAt: new Date(Date.now() - 400),
    })
  );

  await assertFails(charlieDb.collection("calls").doc(callId).get());
});

test("FeedPosts, Comments with replies & images, Follows, and Notifications + Recovery", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const pairIdAB = `${ALICE_UID}_${BOB_UID}`;

  // Seed directory docs & AB friendship
  await assertSucceeds(
    aliceDb.collection("directory").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      displayNameLower: "alice",
      photoUrl: "",
      connectNumber: "014829315",
      followersCount: 0,
      followingCount: 0,
      totalLikesCount: 0,
      postsCount: 0,
      discoverable: true,
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("friendships").doc(pairIdAB).set({
      participantIds: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates a PUBLIC post, a FRIENDS-only post, and an ONLY_ME post
  await assertSucceeds(
    aliceDb.collection("feedPosts").doc("post_public").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorPhoto: "",
      title: "Sunset Vibes",
      description: "Golden hour walk",
      mediaUrls: ["https://i.ibb.co/example/1.jpg", "https://i.ibb.co/example/2.jpg"],
      mediaType: "IMAGE",
      viewVisibility: "PUBLIC",
      likePermission: "FRIENDS",
      commentPermission: "PUBLIC",
      likeCount: 0,
      commentCount: 0,
      shareCount: 0,
      repostCount: 0,
      reactionsBy: {},
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("feedPosts").doc("post_friends").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      title: "Close Friends",
      description: "Only friends see this",
      mediaUrls: ["https://i.ibb.co/example/3.jpg"],
      mediaType: "IMAGE",
      viewVisibility: "FRIENDS",
      likePermission: "FRIENDS",
      commentPermission: "FRIENDS",
      likeCount: 0,
      commentCount: 0,
      shareCount: 0,
      repostCount: 0,
      reactionsBy: {},
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("feedPosts").doc("post_private").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      title: "Private Draft",
      description: "Only me",
      mediaUrls: [],
      mediaType: "IMAGE",
      viewVisibility: "ONLY_ME",
      likePermission: "ONLY_ME",
      commentPermission: "ONLY_ME",
      likeCount: 0,
      commentCount: 0,
      shareCount: 0,
      repostCount: 0,
      reactionsBy: {},
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob (friend) can view FRIENDS post; Charlie (not friend) cannot view FRIENDS or ONLY_ME
  await assertSucceeds(bobDb.collection("feedPosts").doc("post_friends").get());
  await assertFails(charlieDb.collection("feedPosts").doc("post_friends").get());
  await assertFails(bobDb.collection("feedPosts").doc("post_private").get());
  await assertSucceeds(aliceDb.collection("feedPosts").where("viewVisibility", "==", "PUBLIC").get());
  await assertSucceeds(bobDb.collection("feedPosts").where("viewVisibility", "==", "FRIENDS").where("authorUid", "in", [ALICE_UID]).get());

  // likePermission = FRIENDS: Bob succeeds, Charlie fails
  await assertSucceeds(
    bobDb.collection("feedPosts").doc("post_public").update({
      reactionsBy: { [BOB_UID]: "🔥" },
      likeCount: 1,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    charlieDb.collection("feedPosts").doc("post_public").update({
      reactionsBy: { [CHARLIE_UID]: "❤️" },
      likeCount: 2,
      updatedAt: new Date(Date.now() - 150),
    })
  );

  // commentPermission = PUBLIC: Charlie can add an image comment + reply
  await assertSucceeds(
    charlieDb.collection("feedPosts").doc("post_public").collection("comments").doc("c_1").set({
      postId: "post_public",
      authorUid: CHARLIE_UID,
      authorName: "Charlie",
      text: "",
      imageUrl: "https://i.ibb.co/example/sticker.jpg",
      replyToCommentId: "c_0",
      replyToAuthorName: "Bob",
      replyToText: "Awesome!",
      createdAt: now,
    })
  );

  // Follow & directory followersCount increment
  await assertSucceeds(
    charlieDb.collection("follows").doc(`${CHARLIE_UID}_${ALICE_UID}`).set({
      followerUid: CHARLIE_UID,
      targetUid: ALICE_UID,
      followerName: "Charlie",
      targetName: "Alice",
      createdAt: now,
    })
  );
  await assertSucceeds(
    charlieDb.collection("directory").doc(ALICE_UID).update({
      followersCount: 1,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Notification created by Charlie for Alice & recovered/marked read by Alice
  await assertSucceeds(
    charlieDb.collection("notifications").doc("notif_1").set({
      recipientUid: ALICE_UID,
      actorUid: CHARLIE_UID,
      actorName: "Charlie",
      type: "FOLLOW",
      title: "New Follower",
      body: "Charlie started following you",
      targetId: CHARLIE_UID,
      delivered: false,
      read: false,
      createdAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("notifications").where("recipientUid", "==", ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("notifications").doc("notif_1").update({
      delivered: true,
      read: true,
    })
  );
  await assertFails(charlieDb.collection("notifications").doc("notif_1").get());
});

test("Conversations enforce Friend-only or 3-message Feed DM limit until reply, plus Voice/Video & P2P transfer", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const convIdAC = `${ALICE_UID}_${CHARLIE_UID}`;

  // Without friendship, plain conversation without isFriendChat=false fails
  await assertFails(
    aliceDb.collection("conversations").doc(convIdAC).set({
      participantIds: [ALICE_UID, CHARLIE_UID],
      lastMessageText: "",
      lastMessageSenderId: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Feed DM with isFriendChat=false succeeds
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convIdAC).set({
      participantIds: [ALICE_UID, CHARLIE_UID],
      isFriendChat: false,
      dmInitiatorUid: ALICE_UID,
      dmSenderMessageCount: 0,
      dmRecipientReplied: false,
      lastMessageText: "",
      lastMessageSenderId: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice sends up to 3 intro messages, then 4th is blocked until Charlie replies
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convIdAC).collection("messages").doc("m1").set({
      conversationId: convIdAC,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Hi from your post!",
      messageType: "TEXT",
      participantIds: [ALICE_UID, CHARLIE_UID],
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convIdAC).update({
      dmSenderMessageCount: 3,
      lastMessageText: "Third intro",
      lastMessageSenderId: ALICE_UID,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    aliceDb.collection("conversations").doc(convIdAC).collection("messages").doc("m4").set({
      conversationId: convIdAC,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Fourth blocked msg",
      messageType: "TEXT",
      participantIds: [ALICE_UID, CHARLIE_UID],
      createdAt: now,
    })
  );

  // Charlie replies -> unlocks unlimited chat + supports VIDEO (Cloudinary) & VOICE (P2P)
  await assertSucceeds(
    charlieDb.collection("conversations").doc(convIdAC).collection("messages").doc("m_reply").set({
      conversationId: convIdAC,
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      text: "Hello Alice!",
      messageType: "TEXT",
      participantIds: [ALICE_UID, CHARLIE_UID],
      createdAt: now,
    })
  );
  await assertSucceeds(
    charlieDb.collection("conversations").doc(convIdAC).update({
      dmRecipientReplied: true,
      lastMessageText: "Hello Alice!",
      lastMessageSenderId: CHARLIE_UID,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convIdAC).collection("messages").doc("m_video").set({
      conversationId: convIdAC,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Watch this",
      videoUrl: "https://res.cloudinary.com/demo/video/upload/sample.mp4",
      videoThumbUrl: "https://res.cloudinary.com/demo/video/upload/so_0/sample.jpg",
      videoDurationMs: 12000,
      messageType: "VIDEO",
      deliveryStatus: "SENT",
      participantIds: [ALICE_UID, CHARLIE_UID],
      createdAt: now,
    })
  );

  // WebRTC P2P Voice Transfer session signaling
  await assertSucceeds(
    aliceDb.collection("voiceTransfers").doc("vt_1").set({
      senderUid: ALICE_UID,
      recipientUid: CHARLIE_UID,
      conversationId: convIdAC,
      participantIds: [ALICE_UID, CHARLIE_UID],
      durationMs: 4500,
      status: "OFFERED",
      offerSdp: "v=0...",
      offerType: "offer",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    charlieDb.collection("voiceTransfers").doc("vt_1").update({
      status: "TRANSFERRED",
      answerSdp: "v=0...",
      answerType: "answer",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

