# Tactical plan: S0743 - cloud auth self-heal when bound account removed from device

**Status:** Tactical
**Strategic spec:** ../S0743_cloud-auth-dead-end-stale-bound-account-removed-from-device.md

## Summary

Make `GoogleTokenIssuer` distinguish the GMS `ACCOUNT_NOT_PRESENT` failure from refreshable failures, and have the identity repository self-heal a stale binding to `Unbound` on that signal so the cloud picker stops dead-ending when the bound Google account was removed from the device. Scope is two `cloudEnabled` files plus their unit test. No UI change (the "Connected as" card already renders `Unbound` as "Not connected"). No new `NeedsResignInReason`. `requestAdditionalScopes` untouched (no production callers).

## Research inputs

- research/01__call-site-map.md - call sites, detection approach, state/store primitives.

## Phases

- [ ] Phase 01 - Token issuer: sealed `TokenIssueResult` + ACCOUNT_NOT_PRESENT detection (phase-01-token-issuer-result.md)
- [ ] Phase 02 - Repository self-heal to Unbound + unit tests (phase-02-repository-self-heal.md)

## Build gate

- `standard debug` compile (`a.ps1 dq`) after Phase 02.
- `testStandardDebugUnitTest --tests *PrimaryGoogleAccountStateTest` (constructor/return-type change - compile + run the identity test).

## Device verification (manual gate)

Owner device RFCR110NBQJ is already in the dead-end state (bound `serzhyale@gmail.com` absent; device has `serhii.zhyhunenko@gmail.com`). After build: Add -> Cloud -> Google Drive once (self-healing failure, card flips to "Not connected"), then tap again and sign in with the present account -> folders list. Confirms the dead-end is broken.
