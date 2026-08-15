# S0267 Prompt Pack

Source of truth for child-spec slugs and scope: [`CHILD_SPECS.md`](CHILD_SPECS.md).

This document fixes the exact `/spec` command line for each follow-on strategic ticket and stages the owner-input bundle that future drafting must copy into strategic §0. Operators MUST use these commands verbatim - do not invent variants, do not re-discover scope.

---

## Commands

Run these commands sequentially in the rollout order from [`ROLLOUT_ORDER.md`](ROLLOUT_ORDER.md). Each command opens a single strategic spec.

```text
/spec ad-hoc cloud-auth-storage-foundation
```

```text
/spec ad-hoc google-drive-auth-mirror
```

```text
/spec ad-hoc dropbox-auth-mirror
```

```text
/spec ad-hoc onedrive-auth-mirror
```

```text
/spec ad-hoc settings-authorizations-unified-sources
```

```text
/spec ad-hoc settings-authorizations-unified-ui
```

```text
/spec ad-hoc cloud-auth-auditor-extension
```

---

## Owner-input packets

Each packet below is a copy-paste ready bundle for strategic §0 (Owner inputs) when running the corresponding `/spec` command. Bullets follow the §3.3 "Owner inputs" structure from `dev/SPEC_AUTHORING.md`. The optional `cloud-auth-auditor-extension` ticket is post-release and its packet is omitted here intentionally; it will be drafted once the unified-authorizations release is live.

### Packet: `cloud-auth-storage-foundation`

- **Requested mode:** ad-hoc.
- **Goal / expected outcome:** ship the `AuthAccountSource` domain interface and the `cloud_auth_accounts` Room table (additive migration `AppDatabase` v31 → v32) so provider mirrors and the unified UI have a single typed entry point.
- **Local anchor:** strategic S0267 §11.3 child #1; CHILD_SPECS.md §1; source anchors `AppDatabase.kt`, `NetworkCredentialsEntity.kt`, `CryptoHelper`.
- **Scope boundaries / forbidden areas:** no provider adapter code, no UI, no change to `network_credentials` columns, no change to `AuthSessionRepository`.
- **Done / success signal:** Room v32 migration ships, `cloud_auth_accounts` DAO + repository compile and pass DAO-level unit tests on `standardDebug`.
- **Autonomy rule:** proceed without owner input for module structure, Hilt binding placement, and entity column naming; stop only if Room migration requires destructive schema change.
- **UI decisions / delegation:** none - this ticket is data-layer only.

### Packet: `google-drive-auth-mirror`

- **Requested mode:** ad-hoc.
- **Goal / expected outcome:** mirror Drive account metadata (email, scopes, expires_at, last_used_at) into `cloud_auth_accounts` on every `getAccessToken` success, preserving S0200 identity-domain ownership.
- **Local anchor:** strategic S0267 §11.3 child #2 + §6.3; CHILD_SPECS.md §2; source anchors `GoogleDriveAuthCoordinator.kt`, `CredentialManagerGoogleIdentityRepository.kt`.
- **Scope boundaries / forbidden areas:** no identity-domain refactor (S0200 ADR-2 stays), no Dropbox/OneDrive code, no UI, no eager backfill of historical Drive accounts (lazy-fill per §6.6).
- **Done / success signal:** mirror write executes on every `getAccessToken` success; revoke removes mirror row; multi-account Drive picker keeps working unchanged.
- **Autonomy rule:** proceed without owner input for adapter naming and Hilt binding; stop only if hook placement requires changes to `PrimaryGoogleAccountStore` contract.
- **UI decisions / delegation:** none - no UI surface in this ticket.

### Packet: `dropbox-auth-mirror`

- **Requested mode:** ad-hoc.
- **Goal / expected outcome:** mirror Dropbox account metadata into `cloud_auth_accounts` on `initializeWithCredential` and refresh paths, and migrate `DropboxClientUtils` from the hand-rolled JSON format to the official `DbxCredential.Reader`/`Writer`.
- **Local anchor:** strategic S0267 §11.3 child #2 + §6.2; CHILD_SPECS.md §3; source anchors `DropboxClient.kt`, `DropboxClientUtils.kt`.
- **Scope boundaries / forbidden areas:** no Drive/OneDrive code, no UI, no proactive server-side revoke listener (per §6.4 such listener does not exist).
- **Done / success signal:** existing Dropbox sign-in still works end-to-end, mirror row written on each successful auth/refresh, mirror row removed on revoke, old hand-rolled JSON format dropped without breaking existing user sessions.
- **Autonomy rule:** proceed without owner input for migration of the prefs format; stop only if migration cannot preserve existing user credentials without re-login.
- **UI decisions / delegation:** none - no UI surface in this ticket.

### Packet: `onedrive-auth-mirror`

- **Requested mode:** ad-hoc.
- **Goal / expected outcome:** mirror OneDrive account metadata into `cloud_auth_accounts` on every successful MSAL `acquireToken*` callback, and replace the legacy stub-row pattern in `network_credentials` via lazy-fill.
- **Local anchor:** strategic S0267 §11.3 child #2 + §6.1 + §6.6; CHILD_SPECS.md §4; source anchors `OneDriveAuthCoordinator.kt`, `NetworkCredentialsRepositoryImpl.kt`, `S0200AuthStateWipe.kt`.
- **Scope boundaries / forbidden areas:** no attempt to export refresh-tokens from MSAL (blocked per §6.1), no Drive/Dropbox code, no UI.
- **Done / success signal:** mirror row written on each `acquireToken*` success, stub-row in `network_credentials` removed on first OneDrive auth post-migration, MSAL silent refresh keeps working.
- **Autonomy rule:** proceed without owner input for lazy-fill removal of stub-rows; stop only if removal would break the existing OneDrive multi-account picker.
- **UI decisions / delegation:** none - no UI surface in this ticket.

### Packet: `settings-authorizations-unified-sources`

- **Requested mode:** ad-hoc.
- **Goal / expected outcome:** ship `SocialAuthAccountSource` over `AuthSessionRepository` and `NetworkAuthAccountSource` over `network_credentials`, both adapted to the common `AuthAccountSource` interface so the unified UI has one entry point for all three source types.
- **Local anchor:** strategic S0267 §6.7; CHILD_SPECS.md §5; source anchors `AuthSessionRepository.kt`, `NetworkCredentialsRepositoryImpl.kt`.
- **Scope boundaries / forbidden areas:** no UI screen (lives in the next ticket), no schema changes to `network_credentials` or `AuthSessionRepository`, no new provider adapters.
- **Done / success signal:** all three sources expose `observe()`, `rename()`, `delete()`, `relogin(activity)` under the unified interface; existing social / network flows keep working.
- **Autonomy rule:** proceed without owner input for adapter naming and Hilt binding shape; stop only if mapping existing rename/delete semantics onto the unified interface requires a user-visible behaviour change.
- **UI decisions / delegation:** `/ui-clarify` is mandatory before the unified UI ticket starts. This ticket itself is non-UI but its outputs feed the UI ticket's `/ui-clarify` session - record any source-specific UX hint (e.g. "delete = revoke + clear" vs "delete = remove row") inline so the UI ticket can pick it up.

### Packet: `settings-authorizations-unified-ui`

- **Requested mode:** ad-hoc.
- **Goal / expected outcome:** ship the unified Settings → Authorizations screen with a type filter, per-type badges (`☁ Cloud`, `🍪 Social`, `🖥 Network`), and per-source-type confirmation dialogs for rename / delete-with-revoke / relogin.
- **Local anchor:** strategic S0267 §6.7; CHILD_SPECS.md §6; source anchors `AuthSessionsListFragment.kt`, `AuthSessionsListViewModel.kt`.
- **Scope boundaries / forbidden areas:** no new source adapter (lives in `settings-authorizations-unified-sources`), no token-storage change, no change to `CredentialAuditor`.
- **Done / success signal:** unified screen renders all three source types with correct filter/badge/dialog behaviour, landscape parity holds, focus chain works under keyboard / D-pad / mouse, EN/RU/UK strings present, communication policy passes §6 tone checklist.
- **Autonomy rule:** proceed without owner input for layout grid, badge iconography, and filter chip ordering; stop for any decision that changes per-source-type confirmation copy or destructive-action consent flow.
- **UI decisions / delegation:** `/ui-clarify` is MANDATORY before any layout XML or Kotlin edit. The clarify session must cover orientation parity (`res/layout-land/*.xml`), focus traversal across all three source groups, empty-state copy per source type, and the difference between "delete with revoke" vs plain "delete" for cloud / social / network entries.

---

## Validation expectations

Each child ticket's `/spec-dev` execution closes against the minimum class below. Target variant is `standardDebug` for all cloud-capable children because `CLOUD` BuildConfig is gated on the `standard` flavor only (strategic §3.2).

- **`cloud-auth-storage-foundation`** - Kotlin + Room migration (`AppDatabase` v31 → v32 must apply cleanly on a pre-existing database) + `standardDebug` build + DAO-level unit tests for the new `cloud_auth_accounts` table.
- **`google-drive-auth-mirror`** - Kotlin + Room migration coexistence (mirror writes do not corrupt the new table) + `standardDebug` build + adapter-focused unit tests for the write/revoke paths.
- **`dropbox-auth-mirror`** - Kotlin + Room migration coexistence + `standardDebug` build + adapter-focused unit tests for the serialize / refresh / revoke paths; smoke test that existing Dropbox sessions survive the `DropboxClientUtils` format migration.
- **`onedrive-auth-mirror`** - Kotlin + Room migration coexistence + `standardDebug` build + adapter-focused unit tests for mirror write and stub-row lazy-delete.
- **`settings-authorizations-unified-sources`** - Kotlin + compile + affected unit tests covering each source adapter's `observe()`, `rename()`, `delete()`, `relogin()` behaviour.
- **`settings-authorizations-unified-ui`** - Xml + Kotlin + `standardDebug` build + `/ui-clarify` gate passed before any layout edit. Landscape parity check is mandatory because the screen replaces/extends an existing rotation-aware fragment.
- **`cloud-auth-auditor-extension`** (post-release) - Kotlin + compile + audit-focused unit tests for the new auditor pass; verify no regression in the SMB-only `UnusedCredentialPolicy` semantics.
