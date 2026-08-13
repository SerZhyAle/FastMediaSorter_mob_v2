# Phase 05 - docs-catalog-cleanup

**Strategic spec:** [`../S0984_share-sftp-resource-config.md`](../S0984_share-sftp-resource-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-11
**Completed:** 2026-07-11

---

## Objective

Regenerate the class catalog, record the delivered capabilities in the feature inventory, and confirm the docs surface is consistent. No behavior change.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+`.md`) | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CHANGELOG.md` | Appended (via script) | - |

---

## Steps

### Step 05.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set `role`+`status` for the new classes via `dev/CATALOG/scripts/set.ps1`: `CompanionConfigSerializer`, `ExportCompanionConfigUseCase`, `SftpHostReachabilityClassifier`, `MainSftpShareManager`, `CompanionConfigImportActivity`.

**Verification:**

- `Grep` - `CompanionConfigImportActivity` and `ExportCompanionConfigUseCase` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

---

### Step 05.2 - Record capabilities in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add two records via `scripts/all_features/add.ps1` (EN-only), `spec = S0984`, `area = "Network & Cloud"`, flavors `standard,photos,legacy,vr,noLegal` (not `lite` - no SFTP there): (1) "Share SFTP resource access as a file" - exports a tapped SFTP resource to a `.fmscfg` config (optional password omission, private-network warning) and hands it to the system share sheet for Telegram/email. (2) "One-tap import of a shared SFTP access file" - receiving a `.fmscfg` attachment opens a confirm dialog (with a password field when the file carries none) and creates the ready-to-use SFTP resource. Run `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - `S0984` appears in `docs/ALL_FEATURES.jsonl` (2 records).
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

---

### Step 05.3 - Dev log + docs consistency check

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> Ensure a `dev/CHANGELOG.md` entry exists (via `add_to_dev_log.ps1`) for the S0984 change set (batch the code + doc files in one logical entry). Confirm no settings changed (no `docs/settings/*` regen needed - this feature adds no setting). Confirm `docs/FEATURES*.md` is NOT edited here (owned by `/skill-release`).

**Verification:**

- `Grep` - an S0984 / share-sftp entry present in `dev/CHANGELOG.md`.
- `Grep` - no new key added under `docs/settings/settings-manifest.json` for this ticket.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` validates.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Dev log entry present.

---

## Handoff Notes to Next Phase

Final phase - see [INDEX.md](INDEX.md) Completion Gate. Next: `/spec-check S0984` (or the device round-trip test if the ticket entered `BlockNeedUserTest`).

---

## Rollback Plan

Documentation/catalog only - regenerate from source; no code to revert.
