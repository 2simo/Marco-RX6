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
      updatedAt: serverTimestamp(),
    }));
    const visibleProfile = await assertSucceeds(getDoc(doc(visitorDb, "publicProfiles/public-owner")));
    if (visibleProfile.data()?.email !== undefined || visibleProfile.data()?.photoData !== undefined) {
      throw new Error("Private email/photo data must never appear in a public profile");
    }
    await assertFails(getDoc(doc(visitorDb, "users/public-owner")));
    await assertFails(getDocs(collection(visitorDb, "publicProfiles")));

    await assertSucceeds(updateDoc(publicProfile, {
      showFavorites: false,
      favoriteGames: deleteField(),
      updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(publicProfile, {
      photoData: "private-photo-data",
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
