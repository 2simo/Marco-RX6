# MARCO RX6 — Architecture Guide

> Current implementation map for contributors. This document describes the existing code; it does not authorize removing compatibility code or changing runtime behavior.

## Project identity

- Android application ID / namespace: `com.marcorx6.library`
- Language: Kotlin
- Android SDK: min 24, target/compile 36
- Java / Kotlin bytecode target: 17
- Gradle wrapper/runtime used by CI: 9.8.2
- Current app version: 8.18 (versionCode 35)

## Source layout

All application sources live under `app/src/main/java/com/marcorx6/library`.

| Area | Responsibility | Change boundary |
|---|---|---|
| `data/` | Game, cover, profile, download persistence and repositories | Keep Android UI out of repositories; preserve database migrations |
| `data/auth/` | Firebase auth/session/user documents | Keep Firebase identity separate from Blogger/Drive OAuth |
| `network/` | Blogger feed/article/security clients and Drive integration | Validate remote URLs and preserve API behavior |
| `network/drive/` | Google Drive client | Keep upload/auth scopes compatible |
| `notification/` | Blog notification scheduling and worker | Preserve WorkManager unique work and worker compatibility |
| `ui/activity/` | Activity entry point, lifecycle, activity results and services | Keep MainActivity as composition/orchestration, not a feature implementation home |
| `ui/auth/` | Account UI, Google OAuth runtime and admin session state | Do not merge Firebase sign-in and Blogger/Drive OAuth sessions |
| `ui/blogger/`, `ui/blog/` | Blogger publishing, access and sync flows | Preserve owner/admin permissions and post lifecycle |
| `ui/covers/` | Cover selection, caching and upload flow | Preserve local fallback and Drive upload behavior |
| `ui/details/` | Game details, article WebView and download links | Keep URL handling and article rendering isolated |
| `ui/library/` | Library rendering, filtering, downloads and offline fallback | Avoid repeated full-list work during scrolling |
| `ui/settings/`, `ui/appearance/` | Preferences, appearance, typography and system bars | Keep preference keys backward-compatible |
| `ui/profile/`, `ui/notifications/`, `ui/experience/` | Profile, notifications and user experience | Keep user-facing state changes explicit |

## Important compatibility constraints

1. `BlogNotificationWorker.kt` in the root package is a compatibility entry point. Do not remove it based only on a lack of direct Kotlin references; WorkManager may have persisted work using the old class name.
2. Database schema changes must increment the database version and include a tested migration. Fresh-install table definitions must match the latest schema.
3. Firebase account state and the Google OAuth session used for Blogger/Drive are separate. Signing out of one must not silently break the other.
4. Keep the guest browsing path working when no registered Firebase account exists.
5. Preserve Blogger owner/admin authorization and Google Drive upload behavior.
6. Keep download records, WorkManager scheduling, notifications, pause/resume/cancel, and file storage behavior consistent.
7. Do not remove `OfflineCatalogLoader`; it is a fallback path when cached/catalog data is unavailable.
8. Avoid changing application ID, OAuth client configuration, signing certificate, Firebase project, or Firestore paths during code cleanup.

## Quality and refactoring rules

- Refactor one feature boundary at a time; do not combine package moves, behavior changes, and database migrations in one commit.
- Prefer small controllers and focused factories over growing Activity/controller classes.
- Keep network, storage, parsing, and UI responsibilities separate.
- Never delete code solely because it has no obvious direct call site. Check manifest entries, reflection, callbacks, XML resources, WorkManager persisted class names, and tests.
- Do not change WebView security settings to silence warnings without reviewing the trust boundary and content requirements.
- Preserve Arabic/RTL layouts, translations, accessibility descriptions, and edge-to-edge insets.
- Remove deprecated APIs only when the replacement preserves behavior on the full supported API range.

## Current verified baseline

The GitHub Actions run for commit `969a0e0e947ec27769714111658e7c3ac36dbdb2` completed successfully: unit tests, Android Lint, signed Release APK, Debug APK, signing-certificate comparison, and Release artifact upload all passed.

The successful build still reports deprecation warnings. These are technical-debt items, not build failures:
- Legacy Google Sign-In API usage.
- Legacy system-bar/window APIs in multiple screens.
- Deprecated WebView file-URL settings.
- Gradle deprecation notices that should be investigated with `--warning-mode all` before upgrading Gradle.

## Recommended refactor sequence

1. Establish tests around current download/database/auth/Blogger behavior.
2. Centralize window/inset handling and remove duplicate system-bar implementations.
3. Migrate Google sign-in deliberately, keeping Firebase and Blogger/Drive sessions separate.
4. Split the largest UI controllers by cohesive responsibility without changing public behavior.
5. Audit WebView HTML handling and URL policy; retain only the minimum JavaScript and permissions required.
6. Audit dead code and resources with static references plus manifest/XML/WorkManager checks.
7. Re-run the complete CI pipeline after each cohesive change and test the resulting APK on a physical phone.
