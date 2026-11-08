# MARCO RX6 Cloudflare push sender

This Worker polls Firestore notification documents every minute and sends Firebase Cloud Messaging (FCM HTTP v1) data messages to the recipient's registered Android devices.

## Required configuration

1. Create/keep the Worker named `marco-rx6-notifications`.
2. Set the encrypted Worker secret `FCM_SERVICE_ACCOUNT_JSON` to the service-account JSON already created for Firebase project `marco-rx6-v2`. Never commit this JSON or paste it into chat.
3. In Google Cloud IAM, grant that service account:
   - Firebase Cloud Messaging API Admin
   - Cloud Datastore User (`roles/datastore.user`) so it can query notifications, read device tokens, and update delivery status.
4. Add the Cron Trigger `*/1 * * * *` (once per minute).
5. Deploy the contents of `worker.js`. The `/health` route only confirms that the Worker is running; it does not send a test push.

## Firestore queue contract

The app writes each new notification with `pushStatus: "pending"`. The Worker queries the `notifications` collection group, reads `users/{uid}/devices`, sends a data-only FCM message, then changes `pushStatus` to `sent`. If there are no registered devices it records `skipped_no_devices`. Temporary delivery failures are returned to `pending` for retry. Invalid FCM device tokens are removed.

## Important

- Deploy the updated Firestore rules from the repository after the matching app build is available.
- Push delivery is at-least-once: if a Worker stops after FCM accepts a message but before Firestore records `sent`, a duplicate may be delivered on a later run.
- Android 13+ users must grant notification permission. The app already checks that permission before displaying notifications.
- Cloudflare Cron Triggers and Worker usage are subject to the current Free plan limits.
