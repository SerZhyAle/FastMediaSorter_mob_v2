# S0267 Rollout Order

Source of truth for slugs and scope: [`CHILD_SPECS.md`](CHILD_SPECS.md). Source of truth for `/spec` commands and owner-input packets: [`PROMPTS.md`](PROMPTS.md).

This document fixes wave-by-wave execution, parallelism, and stop-go checkpoints for the seven follow-on tickets. Operators MUST follow these waves verbatim - skipping or reordering waves voids the dependency assumptions baked into the strategic decision.

---

## Execution waves

### Wave 1 - Foundation

- **Entry criteria:** S0267 strategic status is `Verified`; `cloud-auth-storage-foundation` ticket is open.
- **Outputs:** new Room table `cloud_auth_accounts`, additive migration `AppDatabase` v31 → v32, `AuthAccountSource` domain interface, Keystore-backed AES blob encryption for `token_payload`, DAO + repository + Hilt binding.
- **Exit gate:** `standardDebug` builds; v32 migration applies cleanly on a pre-existing v31 database; DAO unit tests pass; child ticket flips to `Verified` via `/spec-check`.

### Wave 2 - Provider mirrors

- **Entry criteria:** Wave 1 exit gate satisfied; `cloud-auth-storage-foundation` is `Verified`.
- **Outputs:** three provider adapter implementations writing into `cloud_auth_accounts` on every successful auth/refresh callback - `google-drive-auth-mirror`, `dropbox-auth-mirror`, `onedrive-auth-mirror`. `DropboxClientUtils` migrated from hand-rolled JSON to `DbxCredential.Reader`/`Writer`. `OneDriveAuthCoordinator` stub-row pattern in `network_credentials` replaced by lazy-fill.
- **Exit gate:** all three provider tickets at `Verified`; existing user sessions for all three providers survive without forced re-login; adapter-focused unit tests pass; mirror row written and removed on the expected hooks for each provider.

### Wave 3 - Unified surface

- **Entry criteria:** Wave 2 exit gate satisfied for all three provider tickets.
- **Outputs:** `SocialAuthAccountSource` over `AuthSessionRepository`, `NetworkAuthAccountSource` over `network_credentials`, and unified Settings → Authorizations screen with type filter + per-type badges + per-source-type confirmation dialogs.
- **Exit gate:** `settings-authorizations-unified-sources` at `Verified` before any UI work starts. `settings-authorizations-unified-ui` passes `/ui-clarify`, ships landscape parity, EN/RU/UK strings, communication-policy compliant copy, focus traversal for keyboard / D-pad / mouse, and `standardDebug` build; child ticket flips to `Verified` via `/spec-check`.

### Wave 4 - Post-release auditor

- **Entry criteria:** Wave 3 unified-authorizations release has shipped to users and production telemetry shows the mirror keeps in sync with provider state on the first 401-based reconcile path.
- **Outputs:** `cloud-auth-auditor-extension` - dedicated auditor pass for `cloud_auth_accounts`, surfacing stale or orphaned entries without contaminating SMB-only `UnusedCredentialPolicy` semantics.
- **Exit gate:** new auditor pass at `Verified`; `CredentialAuditor` regression suite still green for SMB.

---

## Parallelism rules

- `cloud-auth-storage-foundation` must complete first. Nothing in Wave 2 may start until this ticket is `Verified`.
- `google-drive-auth-mirror`, `dropbox-auth-mirror`, and `onedrive-auth-mirror` may run in parallel after Wave 1 is `Verified`. The three provider tickets are independent: they share only the `cloud_auth_accounts` table contract from Wave 1 and the `AuthAccountSource` interface boundary.
- `settings-authorizations-unified-sources` starts only after all three provider tickets are at least `Implemented`. It needs the cloud-side adapters to exist (even if not yet `Verified`) so the unified source interface can be wired without dangling stubs.
- `settings-authorizations-unified-ui` starts only after the sources ticket is `Verified` and `/ui-clarify` is complete. The clarify session is a hard gate, not an opportunistic check.
- `cloud-auth-auditor-extension` is deferred until after the first unified-authorizations release. It MUST NOT block Wave 3 and MUST NOT ship in the same release as the unified UI; it is a post-release follow-up that benefits from observed production behaviour.

---

## Stop-go checkpoints

Each checkpoint MUST pass before the next wave may open. A FAIL leaves the wave at `BlockExternal` or `BlockQuestions` until resolved.

- **Checkpoint after Wave 1:** Room migration builds on `standardDebug` and applies cleanly on a pre-existing v31 database; `cloud_auth_accounts` DAO unit tests pass.
- **Checkpoint after Wave 2:** all three provider tickets pass compile and token-path validation; mirror row written on each provider's auth/refresh hook and removed on revoke; existing user sessions for all three providers survive without forced re-login.
- **Checkpoint after Wave 3:** unified UI builds on `standardDebug` and passes orientation parity review (`res/layout-land/*.xml` mirror present and behaviourally equivalent); focus traversal works under keyboard / D-pad / mouse; communication-policy §6 tone checklist passes for all new strings.
- **Checkpoint after Wave 4:** optional post-release only - new auditor pass does not regress SMB `UnusedCredentialPolicy` behaviour and surfaces stale `cloud_auth_accounts` rows where expected.

---

## Suggested operator sequence

For each child ticket, run the commands in this order (per CHILD_SPECS.md / PROMPTS.md):

1. child `/spec` - draft and approve the strategic specification using the matching owner-input packet from PROMPTS.md.
2. child `/spec-tech` - decompose the approved strategic spec into phase files.
3. child `/spec-dev` - execute the tactical phases.
4. child `/spec-check` - audit the implementation and flip status to `Verified` (or trigger `/spec-fix` then re-audit).

Move to the next ticket only after `/spec-check` returns `Verified` and the relevant stop-go checkpoint above has passed.
