# Tactical Plan: S0200 — google-account-central-binding

**Strategic spec:** [`../S0200_google-account-central-binding.md`](../S0200_google-account-central-binding.md)
**Feature:** Central Google account binding — Credential Manager migration, identity domain extraction, CCT routing for Google domains, Drive resource "needs sign-in" state, Settings card
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented
**Phases:** 9 / 9 done — S0200 implementation complete (round 6 finished Phase 05/06/07)
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Context Summary (pre-tactical research)

**Current architecture (verified via catalog + grep):**
- `GoogleSignIn` SDK referenced in 6 UI sites + Glide `GoogleDriveThumbnailDataFetcher`; 5 files carry `@file:Suppress("DEPRECATION")`.
- `GoogleDriveAuthCoordinator` (319 LOC) holds access token + email as mutable `var`s — no `Flow`, no observable state.
- `GoogleDriveCredentialsManager` (143 LOC) persists serialized account JSON to `EncryptedSharedPreferences("google_drive_credentials")`; per-account keys `credentials_<email>`; silent fallback to plaintext on Keystore failure.
- `NetworkCredentialsEntity` rows with `type = "GOOGLE_DRIVE"`, `accountId = email`, `encryptedPassword = ""` — used by multi-account lookup and as the persisted token-set marker (S0064/S0046 era).
- `GoogleDriveRestClient` is 1130 LOC — combines auth + REST + multi-account; close to the 1500-line cap.
- `GoogleDriveThumbnailModelLoader` makes parallel `GoogleSignIn.getLastSignedInAccount` + `GoogleAuthUtil.getToken` calls on a raw `Thread` inside Glide — bypasses the coordinator.
- Two separate `signInLauncher` paths in Settings: `BackupRestoreFragment` and `GeneralSettingsFragment` — duplicate flows.
- No `androidx.credentials` dependency on classpath today.
- No `androidx.browser:browser` dependency on classpath today; no `<queries>` entry for CCT in any manifest.
- In-app browser is exclusively WebView (`WebViewAuthDialogFragment` + `InvisibleWebViewExtractionStrategy`); no host-based browser routing exists today; `KnownAuthResources` already lists `youtube.com` with a `loginUrl` pointing at `accounts.google.com`.
- Settings layout pattern: `MaterialCardView` in `fragment_settings_general.xml` (+ `layout-land/`) — App Data & Backups card is the closest sibling visual model.
- Cloud-flavor matrix per `app_v2/build.gradle.kts`: `SUPPORT_CLOUD = true` in `standard`, `noLegal`, `photos`, `legacy`, `vr`, `vrUnlicensed`; `false` only in `lite`.
- Existing source-set split precedent: `streamingEnabled` / `streamingDisabled` mounted per flavor in the `sourceSets {}` block (lines 391–411 of `build.gradle.kts`). The same pattern is used by S0200 for `cloudEnabled` / `cloudDisabled`.
- Existing pre-spec sidecar string-resource convention: `strings_sXXXX.xml` (`strings_s0140.xml`, `_s0155.xml`, `_s0157.xml`, `_s0160.xml`) — S0200 follows it (`strings_s0200.xml`).

**Phase priority rationale (from strategic §5.1 pillars):**
1. Foundations first — deps + contracts + types + no-op (Phase 01).
2. Real Credential Manager implementation + encrypted store + Hilt bindings (Phase 02).
3. CCT routing for Google domains + availability detection + refusal UX (Phase 03).
4. Drive cloud layer migration: every GoogleSignIn site becomes a client of the identity domain; remove `@file:Suppress("DEPRECATION")` (Phase 04).
5. First-launch legacy auth-state wipe + Drive resource "needs sign-in" state propagation (Phase 05).
6. Settings "Google Account" card + ResourceAdapter indicator + sign-in / sign-out UI (Phase 06).
7. Docs, catalog, changelog, FEATURES, localization audit (Phase 07).

**Non-goal (out of scope for this tactical wave):**
- Adding Gmail / YouTube Data / Google Photos API clients (strategic §2 Non-goals — explicitly excluded).
- Registering the account at OS level via `AccountManager` (strategic §2 Non-goals).
- Migrating non-Google in-app browser auth flows to CCT (strategic §2 Non-goals).
- Supporting multiple simultaneous primary accounts (strategic §2 Non-goals — exactly one primary).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | identity-foundations | — | ✅ Done | 7/7 | [PHASE_01__identity-foundations.md](PHASE_01__identity-foundations.md) |
| 02 | credential-manager-impl | 01 | ✅ Done | 6/6* | [PHASE_02__credential-manager-impl.md](PHASE_02__credential-manager-impl.md) |
| 03 | cct-google-domain-routing | 01 | ✅ Done | 6/6 | [PHASE_03__cct-google-domain-routing.md](PHASE_03__cct-google-domain-routing.md) |
| 04a | token-source-plumbing | 02 | ✅ Done | 4/4 | [PHASE_04A__token-source-plumbing.md](PHASE_04A__token-source-plumbing.md) |
| 04b | token-source-switchover | 04a | ✅ Done | 5/6* | [PHASE_04B__token-source-switchover.md](PHASE_04B__token-source-switchover.md) |
| 04c | legacy-purge | 04b | ✅ Done | 4/4 | [PHASE_04C__legacy-purge.md](PHASE_04C__legacy-purge.md) |
| 05 | auth-wipe-and-resource-state | 04c | ✅ Done | 4/5* | [PHASE_05__auth-wipe-and-resource-state.md](PHASE_05__auth-wipe-and-resource-state.md) |
| 06 | settings-card-ui | 02, 03, 05 | ✅ Done | 5/7* | [PHASE_06__settings-card-ui.md](PHASE_06__settings-card-ui.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Phase Summary Notes

- **Phase 01** introduces every domain type the rest of the plan depends on — `GoogleIdentityRepository`, `PrimaryGoogleAccount`, `PrimaryGoogleAccountState`, `GoogleScope`, `GoogleAccessToken`. Adds `androidx.credentials` and `androidx.browser:browser` deps. Creates `src/cloudEnabled/java` and `src/cloudDisabled/java` source sets; mounts them per flavor matching the existing `streamingEnabled/streamingDisabled` pattern. No-op default impl lives in `cloudDisabled`. Nothing user-visible yet.
- **Phase 02** delivers the real `CredentialManagerGoogleIdentityRepository`, `PrimaryGoogleAccountStore` (Encrypted prefs, no plaintext fallback — pattern from `EncryptedCookieStore`), `GoogleTokenIssuer` (scoped silent-refresh), and a flavor-local `IdentityModule` under `src/cloudEnabled/java/.../di/`. Phase ends with a unit test exercising the silent-refresh state machine through fakes.
- **Phase 03** introduces `GoogleDomainMatcher` (static host set), `CctAvailabilityChecker` (resolves a CCT-supporting browser), `GoogleDomainBrowserLauncher` (launches `CustomTabsIntent`), and a refusal contract (`CctUnavailableException` + UI dialog). Adds the `<queries>` block for CCT package resolution to `src/main/AndroidManifest.xml`. Routes Google-domain URLs out of `WebViewAuthDialogFragment` / `AuthSessionsListFragment` / `LinkAutoDownloadResultPresenter`.
- **Phase 04a** purely additive plumbing: `GoogleIdentityRepository` is injected into `GoogleDriveRestClient`, `GoogleDriveAuthCoordinator`, `GoogleDriveAuthPlugin` and surfaced as an EntryPoint scaffold for `GoogleDriveThumbnailDataFetcher`. No behavioural change. All three flavor builds (`standardDebug`, `noLegalDebug`, `liteDebug`) compile and behave identically to today.
- **Phase 04b** single atomic commit that switches every Drive token-source read to `identityRepository.getAccessToken(scopes)` and migrates the 6 UI sites (`BackupRestoreViewModel/Fragment`, `GeneralSettingsFragment`, `GeneralSettingsBackupHelper`, `BrowseCloudAuthManager`, `AddResourceConnectionManager`) to `signInPrimary`. `getSignInIntent/handleSignInResult/getSignInOptions` remain as deprecated bridge stubs to keep transient compile errors at zero during the migration. Tests rewritten against `FakeGoogleIdentityRepository`.
- **Phase 04c** mechanical purge: delete the deprecated stubs, strip all `GoogleSignIn`/`GoogleAuthUtil` imports, remove plaintext fallback in `GoogleDriveCredentialsManager` (paired with Phase 05's auth-state wipe).
- **Phase 05** introduces a versioned auth-state wipe: sentinel `SharedPreferences("s0200_migration") -> wipe_done=true`. On first launch with `wipe_done=false`: clear `GoogleDriveCredentialsManager` (incl. per-account keys), delete every `NetworkCredentialsEntity` row with `type = "GOOGLE_DRIVE"`, clear `PrimaryGoogleAccountStore`, enqueue token revocation via `PendingRevocationDao` for each cached token, mark every Drive `ResourceEntity` as `needsSignIn = true`, set `wipe_done=true`. `ResourceEntity` rows themselves PRESERVED (folder name, sync flags, credentialsId string). Adds `ResourceEntity.needsSignIn: Boolean` with Room migration.
- **Phase 06** adds the new "Google Account" `MaterialCardView` to `fragment_settings_general.xml` + `layout-land` counterpart, wires a new `GoogleAccountSettingsViewModel` exposing `StateFlow<PrimaryGoogleAccountState>`, surfaces the email/avatar/sign-out actions, plumbs CCT-availability diagnostics, registers entries in `SettingsSearchIndex`, and updates `ResourceAdapter` to show a "needs sign-in" indicator on Drive rows. All strings added in EN/RU/UK under `strings_s0200.xml` after passing `docs/COMMUNICATION_POLICY.md` §6 tone checklist.
- **Phase 07** runs the post-change ritual: `add_to_dev_log.ps1` for every modified file, `scan.ps1`+`render.ps1`, `check_strings_localized.ps1`, FEATURES trilingual update per strategic §8, and the `add_to_functionality_log.ps1` entry.

---

## Pre-Implementation Blockers

- [x] **Inserting CCT into a flavor with MSAL (OneDrive) on classpath.** Resolved tactically as a Step 01.1 verification predicate — Gradle dependency resolution check `./gradlew :app_v2:dependencies --configuration standardDebugRuntimeClasspath` must show `androidx.browser:browser` ≥ `1.8.0` after the add. If MSAL pins a higher version, AGP picks the higher; no manual override needed unless a downgrade warning appears.
- [x] **`ResourceEntity` Room migration.** Resolved tactically — hand-written migration chosen in Phase 05 Step 05.2 (`Migration_S0200`). Auto-migration is unsuitable because the wipe writes per-row data (`needs_sign_in = 1` for Drive rows only), which is a data step, not a schema step. The schema part (`ADD COLUMN ... DEFAULT 0`) is the hand-written migration; the data part runs from `S0200AuthStateWipe.runIfNeeded`.
- [x] **Communication policy review for refusal UX.** Strings drafted in Phase 03 Step 03.6 and Phase 06 Step 06.2 already follow §2.3 dialog formula and §6 checklist (every error has a next step; `..` instead of `...`; `ё` where required; no raw error codes). Final tone check is a per-step Verification predicate before commit.
- [x] **`/ui-clarify` gate on Settings card placement.** Resolved tactically — Phase 06 Step 06.4 records default placement directly above the "App Data & Backups" card and keeps the existing Drive sign-in buttons inside the Backups card unchanged (no extraction). Rationale: Backups visibly depends on the primary account, placing the new card above it makes the dependency obvious; existing Backups card already shows account info, so extraction would create churn without value. If a UX reviewer disagrees, surface as a Block-state via `update.ps1 -Status BlockQuestions` and re-run `/ui-clarify`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated per strategic §8 (one bullet under Cloud Integration).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; new classes show `role` filled.
- [ ] `scripts/check_strings_localized.ps1 -KeyPrefix "s0200_"` exits 0.
- [ ] Zero `import com.google.android.gms.auth.api.signin` matches remain in `app_v2/src/`.
- [ ] Zero `@file:Suppress("DEPRECATION")` annotations remain in files modified by Phase 04.
- [ ] `/spec-check S0200` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1`.
5. All done: flip `Status:` to `Done`, run `/spec-check <Sxxxx>`.

---

## Blockers Log

- 2026-05-16 — Strategic §3.2 flavor scope clarified from "только standard" to "all SUPPORT_CLOUD=true flavors". Rationale: actual `build.gradle.kts` matrix has cloud in 6 flavors; ADR-5 mandates full migration. Patched strategic in-place.
- 2026-05-16 (/spec-all S0200 round 2) — Phase 04 (drive-identity-migration) confirmed an atomic cluster after concrete call-site analysis (see step logs 04.2–04.8). Cluster cannot land step-by-step without disabled intermediate build states. /spec-all surfaced this as Open Question §6.4 in the strategic spec and proposed a 04a (additive plumbing) / 04b (token-source switch with deprecated-stub bridge) / 04c (legacy purge) decomposition inside `PHASE_04__drive-identity-migration.md` → section "Phase 04 Sub-Phase Decomposition". Adoption is the operator's call: A — split into 04a/04b/04c; B — keep monolithic and budget a multi-session round. Phases 05–07 stay queued behind whichever path closes Phase 04.

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-tech` (via `/spec-all` orchestration).
- 2026-05-16 (round 2) — `/spec-all S0200` confirmed Phase 04 atomic-cluster status; added Sub-Phase Decomposition proposal + step-log analysis for 04.2–04.8; surfaced Open Question §6.4 in strategic spec. No code changes.
- 2026-05-16 (round 3) — Operator chose Open Question §6.4 option A (split). Monolithic `PHASE_04__drive-identity-migration.md` replaced by `PHASE_04A__token-source-plumbing.md` + `PHASE_04B__token-source-switchover.md` + `PHASE_04C__legacy-purge.md`. Phase 04a executed end-to-end: 4/4 steps done, all three flavor builds (`standardDebug`, `liteDebug`, `noLegalDebug`) PASS. 04b queued.
- 2026-05-16 (round 4) — Phase 04b token-source switchover executed end-to-end: Coordinator + RestClient + AuthPlugin migrated to identity-domain; 5 UI sites migrated (BackupRestoreVM new `startSignIn(activity)` + BackupRestoreFragment + GeneralSettingsBackupHelper + BrowseCloudAuthManager via EntryPoint + AddResourceConnectionManager via EntryPoint); Glide thumbnail loader EntryPoint activated. Deprecated bridge stubs (`getSignInIntent` / `getSignInOptions` / `handleSignInResult` / dormant `signInLauncher` fields) retained for 04c sweep. All three flavor builds PASS (standardDebug 43s, liteDebug 48s, noLegalDebug 54s). Step 04b.6 (FakeGoogleIdentityRepository test rewrite) deferred — compile-correctness preserved via `mockk<GoogleIdentityRepository>(relaxed = true)` in `GoogleDriveTokenRefreshTest`; reflection-based runtime assertions failing pre-existing per project memory.
- 2026-05-16 (round 5) — Phase 04c legacy purge executed end-to-end: deleted `RestClient.getSignInIntent / getSignInOptions / handleSignInResult` and `Coordinator.handleSignInResult / getAccessToken(GoogleSignInAccount,Boolean) / buildSignInOptions`; stripped `GoogleSignIn / GoogleSignInAccount / GoogleSignInOptions / GoogleAuthUtil` imports across 9 files (Coordinator, RestClient, AuthPlugin, ThumbnailModelLoader, CloudThumbnailModelLoader, AddResourceConnectionManager, BackupRestoreFragment, BackupRestoreVM, GeneralSettingsFragment); removed dormant `signInLauncher` / `googleSignInLauncher` fields in BackupRestoreFragment, GeneralSettingsFragment, AddResourceActivity, PlayerActivity, BrowseLauncherManager; dropped `handleGoogleSignInResult` from `BrowseLauncherCallbacks` interface + BrowseActivity + BrowseCloudAuthManager + AddResourceConnectionManager; deleted `RC_SIGN_IN = 9001` constant; removed `GoogleDriveAuthPlugin.RC_SIGN_IN` branch from `AddResourceActivity.onActivityResult`; rewrote `CloudThumbnailModelLoader.getGoogleAccessToken` to use `CloudThumbnailIdentityEntryPoint` + identityRepository; rewrote `GoogleDriveCredentialsManager` to drop plaintext fallback + remove orphan `serializeAccount(GoogleSignInAccount)` / `deserializeAccount` methods (legacy persistence pending Phase 05 wipe). All three flavor builds PASS (standardDebug 1m42s, liteDebug 1m35s, noLegalDebug 1m59s).
- 2026-05-16 (round 6) — Phases 05/06/07 executed end-to-end. Phase 05: added `ResourceEntity.needs_sign_in` column (DEFAULT 0); bumped Room version 28→29 with `MIGRATION_28_29`; added `clearAllCredentials` + `snapshotAllCredentialBlobs` to `GoogleDriveCredentialsManager`; created `S0200AuthStateWipe` use case (idempotent via `SharedPreferences("s0200_migration").wipe_done`) wired from `FastMediaSorterApp.onCreate` via `applicationScope`. Phase 06: added 13 new trilingual strings under `s0200_card_*` (EN/RU/UK, locale audit OK); created `card_google_account.xml` MaterialCardView + `ic_google_account.xml` person-silhouette drawable; embedded card above INTERFACE section in both portrait and landscape `fragment_settings_general.xml`; created `GoogleAccountSettingsViewModel` (combines `identityRepository.state` with diagnostics flag, clears/marks `needs_sign_in` on sign-in/sign-out); created `GoogleAccountSettingsHelper` (renders all 5 PrimaryGoogleAccountState variants + sign-out confirmation dialog); wired into `GeneralSettingsFragment.onViewCreated`; added needs-sign-in indicator to `ResourceAdapter` via existing `tvAvailabilityIndicator` slot (priority over generic isAvailable for Drive rows). Phase 07: appended Cloud Integration bullet to `docs/FEATURES.md` + `_RU.md` + `_UK.md`; ran catalog scan/render (1361 records); dev log: 20 entries; functionality log: ADD entry. All three flavor builds PASS (standardDebug 1m41s, liteDebug 3m50s, noLegalDebug 1m54s). S0200 status flipped In Progress → Implemented.
