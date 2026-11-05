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
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
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
  it("denies unauthenticated reads and writes", async () => {
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
      await setDoc(doc(adminDb, `users/${uid}`), {
        uid,
        isAnonymous: true,
        updatedAt: new Date(),
      });
      await setDoc(doc(adminDb, `users/${uid}/settings/preferences`), {
        values: { theme: "dark" },
        updatedAt: new Date(),
      });
    });

    await assertSucceeds(deleteDoc(doc(db, `users/${uid}/settings/preferences`)));
    await assertSucceeds(deleteDoc(doc(db, `users/${uid}`)));
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

  it("denies authenticated users access outside their /users/{uid} tree", async () => {
    const db = testEnv.authenticatedContext("user-a").firestore();

    await assertFails(getDoc(doc(db, "public/config")));
    await assertFails(setDoc(doc(db, "public/config"), { enabled: true }));
    await assertFails(getDocs(collection(db, "admin")));
  });
});
