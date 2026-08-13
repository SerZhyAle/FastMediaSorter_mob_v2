# S0267 Child-Spec Matrix

Source of truth: strategic §11.3 of [`../S0267_cloud-auth-unified-storage-research.md`](../S0267_cloud-auth-unified-storage-research.md).

This document fixes the slug, scope, dependency order, validation class, and source anchors for every follow-on strategic ticket that decomposes the approved Hybrid Mirror (variant B) recommendation. Future `/spec ad-hoc <slug>` runs MUST consume this file as input.

---

## Required child specs

### 1. `cloud-auth-storage-foundation`

- **Goal:** Introduce the domain interface `AuthAccountSource` and the persistence backbone (`cloud_auth_accounts` Room table + `AppDatabase` v31 → v32 additive migration) that all provider mirrors and unified-source adapters will plug into.
- **In scope:** new entity `CloudAuthAccountEntity`, DAO, repository contract + impl, Hilt binding module, additive Room migration, Keystore-backed AES blob encryption for `token_payload` via existing `CryptoHelper`.
- **Out of scope:** any provider-specific adapter wiring, any UI work, any change to `network_credentials` schema, any change to `AuthSessionRepository`.
- **Depends on:** none - foundation.
- **Validation class:** Kotlin + Room migration + `standardDebug` build + DAO-level unit tests.
- **Primary source anchors:** `AppDatabase.kt`, `NetworkCredentialsEntity.kt`, `NetworkCredentialsDao.kt`, `NetworkCredentialsRepositoryImpl.kt`, `CryptoHelper`.

### 2. `google-drive-auth-mirror`

- **Goal:** Implement the `CloudAuthAccountSource` provider adapter for Google Drive that mirrors metadata (email, scopes, expires_at, last_used_at) into `cloud_auth_accounts` on every `getAccessToken` success without altering identity-domain ownership from S0200.
- **In scope:** Drive provider adapter, hook into `GoogleDriveAuthCoordinator`, reuse of existing `CredentialManagerGoogleIdentityRepository.requestSecondaryAccount` for multi-account, mirror writes per per-email credential row, mirror revoke on Drive sign-out.
- **Out of scope:** identity-domain refactor (single-primary stays per S0200 ADR-2), Dropbox/OneDrive adapters, UI work, eager backfill of historical accounts (lazy-fill per strategic §6.6).
- **Depends on:** `cloud-auth-storage-foundation`.
- **Validation class:** Kotlin + Room migration coexistence + `standardDebug` build + adapter-focused unit tests for write/revoke paths.
- **Primary source anchors:** `GoogleDriveAuthCoordinator.kt`, `GoogleDriveCredentialsManager.kt`, `CredentialManagerGoogleIdentityRepository.kt`, `GoogleTokenIssuer.kt`, `PrimaryGoogleAccountStore.kt`.

### 3. `dropbox-auth-mirror`

- **Goal:** Implement the `CloudAuthAccountSource` provider adapter for Dropbox that mirrors metadata into `cloud_auth_accounts` on `initializeWithCredential` and `refresh` paths, while migrating `DropboxClientUtils` from the hand-rolled JSON format to the official `DbxCredential.Reader`/`Writer`.
- **In scope:** Dropbox provider adapter, migration of `DropboxClientUtils.serializeCredential`/`deserializeCredential` to `DbxCredential.Reader`/`Writer`, hook into `DropboxClient.initializeWithCredential` and the refresh path, mirror revoke on Dropbox sign-out (SDK revoke + clear prefs + mirror delete).
- **Out of scope:** Google Drive/OneDrive adapters, UI work, server-side revoke detection beyond the reactive 401 path documented in strategic §6.4.
- **Depends on:** `cloud-auth-storage-foundation`.
- **Validation class:** Kotlin + Room migration coexistence + `standardDebug` build + adapter-focused unit tests for serialize/refresh/revoke.
- **Primary source anchors:** `DropboxClient.kt`, `DropboxClientUtils.kt`.

### 4. `onedrive-auth-mirror`

- **Goal:** Implement the `CloudAuthAccountSource` provider adapter for OneDrive that mirrors metadata into `cloud_auth_accounts` on every successful MSAL `acquireToken*` callback, while preserving MSAL ownership of the actual refresh-token cache.
- **In scope:** OneDrive provider adapter, hook into `OneDriveAuthCoordinator.handleAuthenticationResult`, lazy-fill removal of the legacy stub-row pattern in `network_credentials` (strategic §6.6), mirror revoke on MSAL `signOut`.
- **Out of scope:** any attempt to export refresh-tokens from MSAL (blocked per strategic §6.1 / ADR-1), Google Drive/Dropbox adapters, UI work.
- **Depends on:** `cloud-auth-storage-foundation`.
- **Validation class:** Kotlin + Room migration coexistence + `standardDebug` build + adapter-focused unit tests for mirror write / stub-row lazy-delete.
- **Primary source anchors:** `OneDriveAuthCoordinator.kt`, `NetworkCredentialsRepositoryImpl.kt`, `S0200AuthStateWipe.kt`.

### 5. `settings-authorizations-unified-sources`

- **Goal:** Implement the two non-cloud `AuthAccountSource` adapters - `SocialAuthAccountSource` over `AuthSessionRepository` and `NetworkAuthAccountSource` over `network_credentials` - so the unified Settings → Authorizations screen has a single domain entry point for all three source types (cloud, social, network).
- **In scope:** `SocialAuthAccountSource` and `NetworkAuthAccountSource` implementations, mapping of existing rename / delete / relogin operations onto the common `AuthAccountSource` interface, Hilt binding module that exposes all three sources to the registry.
- **Out of scope:** the UI screen itself (lives in `settings-authorizations-unified-ui`), schema changes to `network_credentials` or `AuthSessionRepository`, new provider adapters.
- **Depends on:** `cloud-auth-storage-foundation`, `google-drive-auth-mirror`, `dropbox-auth-mirror`, `onedrive-auth-mirror`.
- **Validation class:** Kotlin + compile + affected unit tests for each source adapter.
- **Primary source anchors:** `AuthSessionRepository.kt`, `AuthSessionRepositoryImpl.kt`, `NetworkCredentialsRepositoryImpl.kt`, `NetworkCredentialsDao.kt`.

### 6. `settings-authorizations-unified-ui`

- **Goal:** Ship the unified Settings → Authorizations screen that lists all three source types in one place with a type filter, per-type badges (`☁ Cloud`, `🍪 Social`, `🖥 Network`), and per-source-type confirmation dialogs for rename / delete-with-revoke / relogin (strategic §6.7).
- **In scope:** new screen replacing/extending `AuthSessionsListFragment`, ViewModel, RecyclerView adapter with filter chips, per-source-type dialog copy aligned with `docs/COMMUNICATION_POLICY.md`, EN/RU/UK trilingual strings, landscape parity, focus chain for keyboard / D-pad / mouse per CLAUDE.md Rule 17.
- **Out of scope:** any new source adapter (lives in `settings-authorizations-unified-sources`), any token-storage change, any change to `CredentialAuditor`.
- **Depends on:** `settings-authorizations-unified-sources`.
- **Validation class:** Xml + Kotlin + `standardDebug` build + `/ui-clarify` gate must pass before implementation.
- **Primary source anchors:** `AuthSessionsListFragment.kt`, `AuthSessionsListViewModel.kt`.

---

## Optional follow-up

### 7. `cloud-auth-auditor-extension` (post-release)

- **Goal:** Extend `CredentialAuditor` with a cloud-aware aggregation pass that surfaces stale / orphaned entries in `cloud_auth_accounts` without mixing into the existing SMB orphan policy.
- **In scope:** new dedicated auditor pass for `cloud_auth_accounts`, audit-report integration, no change to `UnusedCredentialPolicy` semantics for SMB rows.
- **Out of scope:** any change to provider adapters, any change to `cloud_auth_accounts` schema, any change to the unified UI scope.
- **Depends on:** `cloud-auth-storage-foundation`, `google-drive-auth-mirror`, `dropbox-auth-mirror`, `onedrive-auth-mirror`, `settings-authorizations-unified-sources`, `settings-authorizations-unified-ui` (must ship after the first unified-authorizations release).
- **Validation class:** Kotlin + compile + audit-focused unit tests.
- **Primary source anchors:** `CredentialAuditor.kt`, `UnusedCredentialPolicy.kt`.

This ticket is **post-release only** and MUST NOT block the first unified-authorizations release. It is a follow-up improvement that benefits from observed production data once the Hybrid Mirror is live.

---

## Delivery waves

Future strategic / tactical work MUST open child tickets in the wave order below. A wave starts only after the previous wave is fully Verified (see `ROLLOUT_ORDER.md` for stop-go checkpoints).

- Wave 1: `cloud-auth-storage-foundation` (foundation, sequential)
- Wave 2: `google-drive-auth-mirror`, `dropbox-auth-mirror`, `onedrive-auth-mirror` (provider mirrors, parallelizable after Wave 1)
- Wave 3: `settings-authorizations-unified-sources`, then `settings-authorizations-unified-ui` (unified surface, sequential within wave)
- Wave 4: `cloud-auth-auditor-extension` (post-release follow-up)

---

## Shared source anchors

Every future child spec MUST inspect the files below before drafting §4 (Current architecture). These are the concrete current-system anchors that determine the existing contracts the Hybrid Mirror integrates with:

- `AppDatabase.kt`
- `NetworkCredentialsEntity.kt`
- `NetworkCredentialsDao.kt`
- `NetworkCredentialsRepositoryImpl.kt`
- `AuthSessionRepository.kt`
- `AuthSessionRepositoryImpl.kt`
- `AuthSessionsListFragment.kt`
- `AuthSessionsListViewModel.kt`
- `GoogleDriveAuthCoordinator.kt`
- `GoogleDriveCredentialsManager.kt`
- `DropboxClient.kt`
- `DropboxClientUtils.kt`
- `OneDriveAuthCoordinator.kt`
- `CredentialAuditor.kt`
- `UnusedCredentialPolicy.kt`
