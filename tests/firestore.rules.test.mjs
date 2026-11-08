import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  after,
  before,
  beforeEach,
  describe,
  it,
} from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  deleteField,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
  query,
  where,
} from "firebase/firestore";

const projectId = "demo-marco-rx6";
const rules = readFileSync(
  fileURLToPath(new URL("../firestore.rules", import.meta.url)),
  "utf8",
);

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { rules },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv?.cleanup();
});

describe("Firestore user isolation rules", () => {
  it("denies unauthenticated reads and writes to private user profiles", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    const profile = doc(db, "users/user-a");

    await assertFails(getDoc(profile));
    await assertFails(setDoc(profile, { displayName: "Guest" }));
  });

  it("allows an authenticated user to manage only their own profile", async () => {
    const db = testEnv.authenticatedContext("user-a").firestore();
    const profile = doc(db, "users/user-a");

    await assertSucceeds(setDoc(profile, { displayName: "Marco" }));
    await assertSucceeds(getDoc(profile));
    await assertSucceeds(updateDoc(profile, { displayName: "Marco RX6" }));
    await assertSucceeds(deleteDoc(profile));
  });

  it("treats a Firebase anonymous provider as a guest and denies cloud preference writes", async () => {
    const db = testEnv.authenticatedContext("anonymous-guest-uid", {
      firebase: { sign_in_provider: "anonymous" },
    }).firestore();

    await assertSucceeds(
      setDoc(doc(db, "users/anonymous-guest-uid"), {
        uid: "anonymous-guest-uid",
        isAnonymous: true,
        updatedAt: new Date(),
      }),
    );
    await assertFails(
      setDoc(
        doc(db, "users/anonymous-guest-uid/settings/preferences"),
        { values: { theme: "dark" }, updatedAt: new Date() },
      ),
    );
  });

  it("allows a guest to delete its own preferences and profile during account deletion", async () => {
    const uid = "anonymous-delete-uid";
    const db = testEnv.authenticatedContext(uid, {
      firebase: { sign_in_provider: "anonymous" },
    }).firestore();

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const adminDb = context.firestore();
      await setDoc(doc(adminDb, "users/" + uid), {
        uid,
        isAnonymous: true,
        updatedAt: new Date(),
      });
      await setDoc(doc(adminDb, "users/" + uid + "/settings/preferences"), {
        values: { theme: "dark" },
        updatedAt: new Date(),
      });
    });

    const preferences = doc(db, "users/" + uid + "/settings/preferences");
    await assertSucceeds(deleteDoc(preferences));
    await assertSucceeds(deleteDoc(preferences));
    await assertSucceeds(deleteDoc(doc(db, "users/" + uid)));
  });

  it("allows a registered account to update bounded profile fields and photo data", async () => {
    const db = testEnv.authenticatedContext("profile-editor").firestore();
    const profile = doc(db, "users/profile-editor");

    await assertSucceeds(
      setDoc(profile, {
        displayName: "Marco RX6",
        bio: "Retro gaming fan",
        photoData: "a".repeat(420000),
        updatedAt: new Date(),
      }),
    );
    await assertFails(
      updateDoc(profile, { photoData: "a".repeat(600001) }),
    );
    await assertFails(
      updateDoc(profile, { bio: "x".repeat(161) }),
    );
    await assertFails(
      updateDoc(profile, { privateAdminFlag: true }),
    );
  });

  it("allows a registered account to create preferences only with the required fields", async () => {
    const db = testEnv.authenticatedContext("registered-user").firestore();
    await assertSucceeds(
      setDoc(doc(db, "users/registered-user/settings/preferences"), {
        values: { theme: "dark" },
        updatedAt: new Date(),
      }),
    );
  });

  it("publishes only explicitly shared profile fields and keeps private account data private", async () => {
    const ownerDb = testEnv.authenticatedContext("public-owner").firestore();
    const visitorDb = testEnv.authenticatedContext("profile-visitor").firestore();
    const publicProfile = doc(ownerDb, "publicProfiles/public-owner");
    const privateProfile = doc(ownerDb, "users/public-owner");

    await assertSucceeds(setDoc(privateProfile, {
      email: "private@example.com",
      displayName: "Public MARCO",
      updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(publicProfile, {
      uid: "public-owner",
      displayName: "Public MARCO",
      bio: "Retro games",
      showFavorites: true,
      favoriteGames: [{ name: "Game", platform: "PS2", coverUrl: "" }],
      photoUrl: "https://example.com/avatar.jpg",
      photoData: "a".repeat(120000),
      followerCount: 4,
      followingCount: 8,
      updatedAt: serverTimestamp(),
    }));
    const visibleProfile = await assertSucceeds(getDoc(doc(visitorDb, "publicProfiles/public-owner")));
    if (visibleProfile.data()?.email !== undefined) {
      throw new Error("Private email must never appear in a public profile");
    }
    if (visibleProfile.data()?.photoData !== "a".repeat(120000)) {
      throw new Error("An explicitly shared, size-bounded avatar should be visible in the public profile");
    }
    if (visibleProfile.data()?.followerCount !== 4 || visibleProfile.data()?.followingCount !== 8) {
      throw new Error("Public visitors should be able to read cached follow counts");
    }
    await assertFails(getDoc(doc(visitorDb, "users/public-owner")));
    await assertFails(getDocs(collection(visitorDb, "publicProfiles")));
    await assertFails(updateDoc(doc(visitorDb, "publicProfiles/public-owner"), {
      followerCount: 999,
      updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(publicProfile, {
      followerCount: 5,
      followingCount: 9,
      updatedAt: serverTimestamp(),
    }));

    await assertSucceeds(updateDoc(publicProfile, {
      showFavorites: false,
      favoriteGames: deleteField(),
      updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(publicProfile, {
      photoData: "updated-public-avatar",
      updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(publicProfile, {
      photoData: "x".repeat(180001),
      updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(deleteDoc(publicProfile));
  });

  it("bounds the public favorite-game preview stored in a profile", async () => {
    const db = testEnv.authenticatedContext("favorite-owner").firestore();
    const profile = doc(db, "publicProfiles/favorite-owner");
    const games = Array.from({ length: 31 }, (_, index) => ({
      name: "Game " + index,
      platform: "PS2",
      coverUrl: "",
    }));

    await assertFails(setDoc(profile, {
      uid: "favorite-owner",
      displayName: "Favorite owner",
      showFavorites: true,
      favoriteGames: games,
      updatedAt: serverTimestamp(),
    }));
  });

  it("denies access to another user's profile and nested data", async () => {
    const dbA = testEnv.authenticatedContext("user-a").firestore();
    const dbB = testEnv.authenticatedContext("user-b").firestore();

    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "users/user-b"), {
        displayName: "Private",
      });
      await setDoc(
        doc(context.firestore(), "users/user-b/settings/preferences"),
        { values: { private: true } },
      );
    });

    await assertFails(getDoc(doc(dbA, "users/user-b")));
    await assertFails(
      setDoc(doc(dbA, "users/user-b"), { displayName: "Intrusion" }),
    );
    await assertFails(
      getDoc(doc(dbA, "users/user-b/settings/preferences")),
    );
    await assertSucceeds(getDoc(doc(dbB, "users/user-b")));
  });

  it("allows users to create and remove only their own follow edges", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    const bob = testEnv.authenticatedContext("bob").firestore();
    const aliceFollowingBob = doc(alice, "userFollows/alice/following/bob");

    await assertSucceeds(setDoc(aliceFollowingBob, {
      uid: "alice",
      targetUid: "bob",
      createdAt: serverTimestamp(),
    }));
    await assertSucceeds(getDoc(aliceFollowingBob));
    // The followed account can see its incoming edge; unrelated users cannot.
    await assertSucceeds(getDoc(doc(bob, "userFollows/alice/following/bob")));
    const charlie = testEnv.authenticatedContext("charlie").firestore();
    await assertFails(getDoc(doc(charlie, "userFollows/alice/following/bob")));
    // Collection-group reads must be constrained to the signed-in user's incoming edges.
    await assertSucceeds(getDocs(
      query(collectionGroup(bob, "following"), where("targetUid", "==", "bob")),
    ));
    await assertFails(getDocs(collectionGroup(bob, "following")));
    await assertFails(setDoc(doc(bob, "userFollows/alice/following/charlie"), {
      uid: "bob",
      targetUid: "charlie",
      createdAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(alice, "userFollows/alice/following/alice"), {
      uid: "alice",
      targetUid: "alice",
      createdAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(aliceFollowingBob, { targetUid: "charlie" }));
    // The followed account may remove an incoming edge when deleting its profile.
    await assertSucceeds(deleteDoc(doc(bob, "userFollows/alice/following/bob")));
    await assertSucceeds(setDoc(aliceFollowingBob, {
      uid: "alice",
      targetUid: "bob",
      createdAt: serverTimestamp(),
    }));
    await assertSucceeds(deleteDoc(aliceFollowingBob));
  });


  it("reserves usernames uniquely and prevents case-insensitive duplicates", async () => {
    const alice = testEnv.authenticatedContext("username-alice").firestore();
    const bob = testEnv.authenticatedContext("username-bob").firestore();
    const claim = doc(alice, "usernames/marco_rx6");
    await assertSucceeds(setDoc(claim, {
      uid: "username-alice",
      username: "Marco_RX6",
      createdAt: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(doc(alice, "users/username-alice"), {
      username: "Marco_RX6",
      displayName: "Alice",
      updatedAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(bob, "usernames/marco_rx6"), {
      uid: "username-bob",
      username: "marco_rx6",
      createdAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(claim, { uid: "username-bob" }));
    await assertFails(deleteDoc(claim));
    await assertFails(setDoc(doc(bob, "users/username-bob"), {
      username: "marco_rx6",
      displayName: "Bob",
      updatedAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(bob, "usernames/invalid-name"), {
      uid: "username-bob",
      username: "invalid-name",
      createdAt: serverTimestamp(),
    }));
    await assertSucceeds(deleteDoc(doc(alice, "users/username-alice")));
    await assertSucceeds(deleteDoc(claim));
  });

  it("allows private messages only between mutually-following registered accounts", async () => {
    const alice = testEnv.authenticatedContext("message-alice").firestore();
    const bob = testEnv.authenticatedContext("message-bob").firestore();
    const outsider = testEnv.authenticatedContext("message-outsider").firestore();

    await assertSucceeds(setDoc(doc(alice, "userFollows/message-alice/following/message-bob"), {
      uid: "message-alice", targetUid: "message-bob", createdAt: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(doc(bob, "userFollows/message-bob/following/message-alice"), {
      uid: "message-bob", targetUid: "message-alice", createdAt: serverTimestamp(),
    }));

    await assertSucceeds(getDoc(doc(alice, "conversations/not-created-yet")));
    const conversationId = "mutual-follow-conversation";
    const conversation = doc(alice, "conversations/" + conversationId);
    await assertSucceeds(setDoc(conversation, {
      participants: ["message-alice", "message-bob"],
      participantA: "message-alice",
      participantB: "message-bob",
      lastMessage: "Hello",
      lastSenderUid: "message-alice",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));
    // Match the exact inbox query used by Android: participants array-contains
    // the signed-in UID, ordered by the latest conversation update.
    const aliceInbox = await assertSucceeds(getDocs(query(
      collection(alice, "conversations"),
      where("participantA", "==", "message-alice"),
    )));
    const bobInbox = await assertSucceeds(getDocs(query(
      collection(bob, "conversations"),
      where("participantB", "==", "message-bob"),
    )));
    if (aliceInbox.size + bobInbox.size !== 2) {
      throw new Error("Both participants should be able to query their private inbox");
    }
    await assertFails(getDocs(query(
      collection(outsider, "conversations"),
      where("participantA", "==", "message-alice"),
    )));
    const message = doc(alice, "conversations/" + conversationId + "/messages/first");
    await assertSucceeds(setDoc(message, {
      senderUid: "message-alice",
      text: "Hello",
      createdAt: serverTimestamp(),
      readBy: ["message-alice"],
    }));
    const bobMessage = doc(bob, "conversations/" + conversationId + "/messages/first");
    const aliceMessage = doc(alice, "conversations/" + conversationId + "/messages/first");
    await assertFails(updateDoc(aliceMessage, {
      readBy: ["message-alice", "message-bob"],
      readAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(bobMessage, {
      readBy: ["message-alice", "message-outsider"],
      readAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(bobMessage, {
      readBy: ["message-alice", "message-bob"],
      readAt: serverTimestamp(),
    }));
    await assertSucceeds(getDoc(bobMessage));
    await assertFails(getDoc(doc(outsider, "conversations/" + conversationId)));
    await assertFails(setDoc(doc(outsider, "conversations/" + conversationId + "/messages/intrusion"), {
      senderUid: "message-outsider", text: "Intrusion", createdAt: serverTimestamp(),
      readBy: ["message-outsider"],
    }));
  });

  it("denies starting a conversation when the follow relationship is one-way", async () => {
    const alice = testEnv.authenticatedContext("oneway-alice").firestore();
    const bob = testEnv.authenticatedContext("oneway-bob").firestore();
    await assertSucceeds(setDoc(doc(alice, "userFollows/oneway-alice/following/oneway-bob"), {
      uid: "oneway-alice", targetUid: "oneway-bob", createdAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(alice, "conversations/oneway-conversation"), {
      participants: ["oneway-alice", "oneway-bob"],
      lastMessage: "Hello",
      lastSenderUid: "oneway-alice",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));
  });

  it("allows one immutable game view per authenticated guest installation", async () => {
    const uid = "guest-viewer";
    const db = testEnv.authenticatedContext(uid, {
      firebase: { sign_in_provider: "anonymous" },
    }).firestore();
    const gameId = "a".repeat(64);
    const view = doc(db, "gameMetrics/" + gameId + "/views/" + uid);

    await assertSucceeds(setDoc(view, { uid, createdAt: serverTimestamp() }));
    // A second write targets the existing UID document and is denied as update.
    await assertFails(setDoc(view, { uid, createdAt: serverTimestamp() }));
    await assertFails(updateDoc(view, { createdAt: serverTimestamp() }));
    await assertFails(deleteDoc(view));
    await assertFails(
      setDoc(doc(db, "gameMetrics/" + gameId + "/views/another-user"), {
        uid,
        createdAt: serverTimestamp(),
      }),
    );
    await assertFails(
      setDoc(doc(db, "gameMetrics/not-a-sha256-id/views/" + uid), {
        uid,
        createdAt: serverTimestamp(),
      }),
    );
  });

  it("allows one immutable game download metric per authenticated user", async () => {
    const uid = "guest-downloader";
    const db = testEnv.authenticatedContext(uid, {
      firebase: { sign_in_provider: "anonymous" },
    }).firestore();
    const gameId = "e".repeat(64);
    const download = doc(db, "gameMetrics/" + gameId + "/downloads/" + uid);

    await assertSucceeds(setDoc(download, { uid, createdAt: serverTimestamp() }));
    // The UID document path prevents the same account from inflating the count.
    await assertFails(setDoc(download, { uid, createdAt: serverTimestamp() }));
    await assertFails(updateDoc(download, { createdAt: serverTimestamp() }));
    await assertFails(deleteDoc(download));
    await assertFails(
      setDoc(doc(db, "gameMetrics/" + gameId + "/downloads/another-user"), {
        uid,
        createdAt: serverTimestamp(),
      }),
    );
    await assertFails(
      setDoc(doc(db, "gameMetrics/not-a-sha256-id/downloads/" + uid), {
        uid,
        createdAt: serverTimestamp(),
      }),
    );
    await assertSucceeds(getDoc(download));
  });

  it("allows registered users to create and update only their own 1-to-5-star rating", async () => {
    const uid = "registered-rater";
    const db = testEnv.authenticatedContext(uid).firestore();
    const gameId = "b".repeat(64);
    const rating = doc(db, "gameMetrics/" + gameId + "/ratings/" + uid);

    await assertSucceeds(setDoc(rating, {
      uid,
      rating: 4,
      updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(
      updateDoc(rating, { rating: 5, updatedAt: serverTimestamp() }),
    );
    await assertFails(
      updateDoc(rating, { rating: 6, updatedAt: serverTimestamp() }),
    );
    await assertFails(
      updateDoc(rating, { rating: 4, updatedAt: new Date() }),
    );
    await assertFails(
      setDoc(doc(db, "gameMetrics/" + gameId + "/ratings/someone-else"), {
        uid: "somebody-else",
        rating: 5,
        updatedAt: serverTimestamp(),
      }),
    );
    await assertFails(
      setDoc(doc(db, "gameMetrics/not-a-sha256-id/ratings/" + uid), {
        uid,
        rating: 5,
        updatedAt: serverTimestamp(),
      }),
    );
    // The rating document remains publicly readable for the game summary.
    await assertSucceeds(getDoc(rating));
  });

  it("allows a user's collection-group query for public game ratings", async () => {
    const uid = "ratings-owner";
    const db = testEnv.authenticatedContext(uid).firestore();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const adminDb = context.firestore();
      await setDoc(doc(adminDb, "gameMetrics/" + "f".repeat(64) + "/ratings/" + uid), {
        uid, rating: 5, updatedAt: new Date(),
      });
    });
    const result = await assertSucceeds(getDocs(
      query(collectionGroup(db, "ratings"), where("uid", "==", uid)),
    ));
    if (result.size !== 1) throw new Error("Expected the user's rating to be returned");
  });

  it("denies ratings from anonymous visitors", async () => {
    const uid = "anonymous-rater";
    const db = testEnv.authenticatedContext(uid, {
      firebase: { sign_in_provider: "anonymous" },
    }).firestore();

    await assertFails(
      setDoc(doc(db, "gameMetrics/" + "c".repeat(64) + "/ratings/" + uid), {
        uid,
        rating: 5,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it("denies unauthenticated metric writes while allowing public summary reads", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    const gameId = "d".repeat(64);

    await assertSucceeds(getDoc(doc(db, "gameMetrics/" + gameId + "/ratings/any-user")));
    await assertFails(
      setDoc(doc(db, "gameMetrics/" + gameId + "/views/guest"), {
        uid: "guest",
        createdAt: serverTimestamp(),
      }),
    );
    await assertFails(
      setDoc(doc(db, "gameMetrics/" + gameId + "/ratings/guest"), {
        uid: "guest",
        rating: 5,
        updatedAt: serverTimestamp(),
      }),
    );
  });

  it("denies authenticated users access outside their /users/{uid} tree", async () => {
    const db = testEnv.authenticatedContext("user-a").firestore();

    await assertFails(getDoc(doc(db, "public/config")));
    await assertFails(setDoc(doc(db, "public/config"), { enabled: true }));
    await assertFails(getDocs(collection(db, "admin")));
  });
});

describe("Private notification inbox rules", () => {
  it("allows a real follow event to create a recipient notification", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    const bob = testEnv.authenticatedContext("bob").firestore();
    await assertSucceeds(setDoc(doc(alice, "userFollows/alice/following/bob"), {
      uid: "alice",
      targetUid: "bob",
      createdAt: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(doc(alice, "users/bob/notifications/follow-1"), {
      type: "follow",
      actorUid: "alice",
      title: "متابع جديد",
      body: "بدأ هذا المستخدم بمتابعتك",
      createdAt: serverTimestamp(),
      read: false,
      pushStatus: "pending",
    }));
    await assertSucceeds(updateDoc(doc(bob, "users/bob/notifications/follow-1"), {
      read: true,
      readAt: serverTimestamp(),
    }));
  });

  it("denies forged follow notifications and cross-account inbox reads", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    const bob = testEnv.authenticatedContext("bob").firestore();
    const mallory = testEnv.authenticatedContext("mallory").firestore();
    await assertFails(setDoc(doc(bob, "users/bob/notifications/forged"), {
      type: "follow",
      actorUid: "mallory",
      title: "متابع جديد",
      body: "مزيف",
      createdAt: serverTimestamp(),
      read: false,
      pushStatus: "pending",
    }));
    await assertFails(getDocs(collection(alice, "users/bob/notifications")));
    await assertFails(setDoc(doc(mallory, "users/bob/notifications/intrusion"), {
      type: "game",
      actorUid: "mallory",
      gameName: "Game",
      platform: "PS2",
      title: "لعبة جديدة",
      body: "Game • PS2",
      createdAt: serverTimestamp(),
      read: false,
      pushStatus: "pending",
    }));
  });

  it("allows a user to record and read a newly discovered local catalog game", async () => {
    const db = testEnv.authenticatedContext("game-user").firestore();
    const notice = doc(db, "users/game-user/notifications/game-1");
    await assertSucceeds(setDoc(notice, {
      type: "game",
      actorUid: "game-user",
      gameName: "Example Game",
      platform: "PS2",
      title: "لعبة جديدة في المكتبة",
      body: "Example Game • PS2",
      createdAt: serverTimestamp(),
      read: false,
      pushStatus: "pending",
    }));
    await assertSucceeds(getDoc(notice));
  });
});
