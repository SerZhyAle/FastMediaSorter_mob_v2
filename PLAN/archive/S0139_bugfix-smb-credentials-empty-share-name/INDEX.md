# Tactical Index: S0139 — SMB credentials with empty `shareName`

**Ticket:** S0139
**Status:** Tactical
**Strategic:** [PLAN/S0139_bugfix-smb-credentials-empty-share-name.md](../S0139_bugfix-smb-credentials-empty-share-name.md)
**Roadmap entry:** Ad-hoc — field session 2026-05-10

## Phases

- [F1-backfill.md](F1-backfill.md) — One-shot startup backfill of empty `shareName` from `MediaResource.path`.
- [F2-guard.md](F2-guard.md) — Write-side warning when SMB credential is persisted with empty `shareName`.

## Phase R — resolved inline (no separate phase file)

Findings are baked into strategic spec §6:

- Empty value can be either `NULL` or `""`. SQL filter must cover both.
- Multiple write paths can leave empty value; the most plausible historical source is `SettingsViewModel.importSzaResources` (factory called without `shareName` argument).
- Backfill on startup is safe — Hilt is up by `Application.onCreate`, repositories are accessible.

## Constraints

- No Room schema change. No DB migration.
- Backfill is idempotent and one-shot — guarded by SharedPreferences flag `smb_share_name_backfill_v1_done`.
- Self-heal in `ResourceRepositoryImpl.testSmbConnection` stays as defense-in-depth.
- Trilingual mirrors not affected (no user-visible strings).

## Implementation order

1. F1 — backfill use-case + wiring in `FastMediaSorterApp`.
2. F2 — write-side guard logging in `NetworkCredentialsRepositoryImpl`.
3. Build gate `standard debug`.
4. Audit + Verified.

## Debug verification tag

`/spec-dev` inserts at backfill entry point:

```kotlin
Timber.d("S0139: backfill scanning SMB credentials for empty shareName")
```

Removed on transition to `Verified`.
