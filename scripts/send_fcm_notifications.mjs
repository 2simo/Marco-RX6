import { JWT } from "google-auth-library";
import { readFile } from "node:fs/promises";

const credentialsPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
if (!credentialsPath) throw new Error("GOOGLE_APPLICATION_CREDENTIALS is not set");

const serviceAccount = JSON.parse(await readFile(credentialsPath, "utf8"));
const projectId = serviceAccount.project_id;
if (projectId !== "marco-rx6-v2") {
  throw new Error(`Unexpected Firebase project: ${projectId || "missing"}`);
}

const auth = new JWT({
  email: serviceAccount.client_email,
  key: serviceAccount.private_key,
  scopes: [
    "https://www.googleapis.com/auth/firebase.messaging",
    "https://www.googleapis.com/auth/datastore"
  ]
});

async function accessToken() {
  const result = await auth.getAccessToken();
  if (!result.token) throw new Error("Google did not return an access token");
  return result.token;
}

async function googleFetch(url, token, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      authorization: `Bearer ${token}`,
      ...(options.headers || {})
    }
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Google API ${response.status}: ${detail.slice(0, 700)}`);
  }
  if (response.status === 204) return null;
  return response.json();
}

function stringField(fields, key, fallback = "") {
  return fields?.[key]?.stringValue ?? fallback;
}

function notificationData(document, recipientUid) {
  const fields = document.fields || {};
  const data = {
    title: stringField(fields, "title", "MARCO"),
    body: stringField(fields, "body", "لديك إشعار جديد"),
    type: stringField(fields, "type", "general"),
    recipientUid,
    notificationId: document.name.split("/").pop()
  };
  for (const key of ["actorUid", "conversationId", "messageId", "gameName", "platform"]) {
    const value = stringField(fields, key);
    if (value) data[key] = value;
  }
  return data;
}

async function updatePushStatus(documentName, token, status) {
  const mask = [
    "updateMask.fieldPaths=pushStatus",
    "updateMask.fieldPaths=pushAttemptAt"
  ];
  if (status === "sent" || status === "skipped_no_devices") {
    mask.push("updateMask.fieldPaths=pushSentAt");
  }
  const url = `https://firestore.googleapis.com/v1/${documentName}?${mask.join("&")}`;
  const fields = {
    pushStatus: { stringValue: status },
    pushAttemptAt: { timestampValue: new Date().toISOString() }
  };
  if (status === "sent") fields.pushSentAt = { timestampValue: new Date().toISOString() };
  await googleFetch(url, token, {
    method: "PATCH",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ fields })
  });
}

async function removeDevice(documentName, token) {
  const response = await fetch(`https://firestore.googleapis.com/v1/${documentName}`, {
    method: "DELETE",
    headers: { authorization: `Bearer ${token}` }
  });
  if (!response.ok && response.status !== 404) {
    throw new Error(`Unable to remove invalid device token: ${response.status}`);
  }
}

async function sendToDevice(token, fcmToken, data) {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          data,
          android: { priority: "HIGH" }
        }
      })
    }
  );
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const serialized = JSON.stringify(payload);
    if (serialized.includes("UNREGISTERED") ||
        serialized.includes("registration-token-not-registered")) {
      return { invalid: true };
    }
    throw new Error(`FCM send failed: ${response.status} ${serialized.slice(0, 500)}`);
  }
  return { sent: true };
}

async function processNotification(document, token) {
  const parts = document.name.split("/");
  const usersIndex = parts.indexOf("users");
  if (usersIndex < 0 || !parts[usersIndex + 1] ||
      parts[usersIndex + 2] !== "notifications") {
    console.log("Skipping unsupported notification path:", document.name);
    return;
  }

  const recipientUid = parts[usersIndex + 1];
  const devicesUrl =
    `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/users/${encodeURIComponent(recipientUid)}/devices?pageSize=100`;
  const devicesResult = await googleFetch(devicesUrl, token);
  const devices = devicesResult.documents || [];

  if (devices.length === 0) {
    await updatePushStatus(document.name, token, "skipped_no_devices");
    console.log("No registered devices for recipient:", recipientUid);
    return;
  }

  const data = notificationData(document, recipientUid);
  let delivered = 0;
  for (const device of devices) {
    const fcmToken = stringField(device.fields, "token");
    if (!fcmToken) continue;
    try {
      const result = await sendToDevice(token, fcmToken, data);
      if (result.invalid) await removeDevice(device.name, token);
      else delivered++;
    } catch (error) {
      console.error("Device delivery failed:", recipientUid, error.message);
    }
  }

  await updatePushStatus(document.name, token, delivered > 0 ? "sent" : "pending");
  console.log(`Notification ${document.name}: delivered to ${delivered} device(s)`);
}

async function sendTargetedTestNotification(token, recipientUid) {
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(recipientUid)) {
    throw new Error("Invalid Firebase recipient UID");
  }

  const devicesUrl =
    `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/users/${encodeURIComponent(recipientUid)}/devices?pageSize=100`;
  const devicesResult = await googleFetch(devicesUrl, token);
  const devices = devicesResult.documents || [];
  if (devices.length === 0) {
    throw new Error("No registered devices found for the selected UID");
  }

  const data = {
    title: "MARCO RX6 – TEST",
    body: "This is a test notification.",
    type: "test",
    recipientUid
  };

  let delivered = 0;
  let invalid = 0;
  for (const device of devices) {
    const fcmToken = stringField(device.fields, "token");
    if (!fcmToken) continue;
    try {
      const result = await sendToDevice(token, fcmToken, data);
      if (result.invalid) invalid++;
      else delivered++;
    } catch (error) {
      console.error("Test notification delivery failed:", error.message);
    }
  }

  if (delivered === 0) {
    throw new Error(`Test notification was not accepted by FCM. Invalid tokens: ${invalid}`);
  }
  console.log(`Targeted test accepted by FCM for ${delivered} device(s). No notification queue was read or modified.`);
}

const token = await accessToken();
const testRecipientUid = process.env.FCM_TEST_RECIPIENT_UID?.trim();

if (testRecipientUid) {
  await sendTargetedTestNotification(token, testRecipientUid);
} else {
  const queryUrl =
    `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:runQuery`;
  const query = {
    structuredQuery: {
      from: [{ collectionId: "notifications", allDescendants: true }],
      where: {
        fieldFilter: {
          field: { fieldPath: "pushStatus" },
          op: "EQUAL",
          value: { stringValue: "pending" }
        }
      },
      limit: 100
    }
  };

  const rows = await googleFetch(queryUrl, token, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(query)
  });
  const documents = (rows || []).map(row => row.document).filter(Boolean);
  let processed = 0;
  for (const document of documents) {
    try {
      await processNotification(document, token);
      processed++;
    } catch (error) {
      console.error("Notification processing failed:", document.name, error.message);
    }
  }
  console.log(`MARCO RX6 FCM sweep complete. Found: ${documents.length}; processed: ${processed}`);
}
