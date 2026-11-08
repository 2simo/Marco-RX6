# MARCO RX6 — Release Quality Checklist

Use this checklist for every device-test build. A green build is necessary, but it does not replace physical-device testing.

## Source and repository
- [ ] Changes are on `feature/next-development`; do not merge to `main` without explicit approval.
- [ ] No service-account JSON, keystore, passwords, tokens, or private credentials are committed.
- [ ] Package name, Firebase project, OAuth clients, signing identity, and Firestore paths are unchanged unless intentionally migrated.
- [ ] No compatibility worker, manifest component, resource, or callback was removed without a reference audit.

## Automated verification
- [ ] Kotlin unit tests pass.
- [ ] Android Lint passes.
- [ ] Release APK builds and is signed.
- [ ] Debug APK builds with the expected OAuth certificate for account-flow testing.
- [ ] Debug and Release signing certificates match when the CI workflow requires it.
- [ ] Release APK artifact exists and can be downloaded.
- [ ] Firestore rules tests pass when rules or engagement/auth behavior changes.

## Core device regression
- [ ] App opens and completes first-run setup.
- [ ] Visitor mode works without sign-in.
- [ ] Google and email/password sign-in, sign-out, and account switching work.
- [ ] Blogger owner/admin can create, edit, publish, and delete a post.
- [ ] Google Drive cover upload and display work.
- [ ] Library feed, search, platform filters, favorites, details, and similar games work.
- [ ] Download links open in the in-app browser and direct-file confirmation is correct.
- [ ] Download queue, progress, pause/resume, cancel, retry, and notifications work.
- [ ] Downloads survive app restart and network interruption where supported.
- [ ] Arabic/RTL, language switching, appearance, and system bars remain correct.
- [ ] No noticeable lag, repeated flicker, clipped content, or overlapping controls.

## Security and privacy
- [ ] WebView only enables capabilities required by the article experience.
- [ ] External URLs are restricted to intended schemes and validated before opening/downloading.
- [ ] Firestore rules reject unauthorized writes and anonymous ratings.
- [ ] Account deletion and privacy-policy links remain reachable.
- [ ] No private user data is written to logs.

## Release decision
- [ ] Automated checks are green.
- [ ] Physical-device regression is complete.
- [ ] Known limitations are documented.
- [ ] Only then mark the build ready for wider distribution.
