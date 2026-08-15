# Phase 04 — Docs Catalog Cleanup

**Strategic spec:** [../S0149_enh-sftp-permission-denied-message.md](../S0149_enh-sftp-permission-denied-message.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none — final phase
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Finish housekeeping, logging, catalog refresh, and verification handoff for S0149.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] All new SFTP error keys already exist in EN/RU/UK.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified | script-managed |
| `dev/CATALOG/app_v2.jsonl` | Modified | script-managed |
| `dev/CATALOG/app_v2.md` | Modified | script-managed |
| `PLAN/S0149_enh-sftp-permission-denied-message/INDEX.md` | Modified | ≤ 220 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 04.1 — Update progress tracking and dev log entries

**Files:** `PLAN/S0149_enh-sftp-permission-denied-message/INDEX.md`, `dev/CHANGELOG.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Mark the completed steps and phases in the tactical files, then run `./scripts/add_to_dev_log.ps1` for every modified file from Phases 01-03 plus the strategic spec status change. Do not edit `dev/CHANGELOG.md` manually.

**Verification:**

- `Grep` — `S0149` present in `dev/CHANGELOG.md`.
- `Grep` — `**Phases:** 4 / 4 done` present in `PLAN/S0149_enh-sftp-permission-denied-message/INDEX.md`.
- `Grep` — `**Status:** Done` present in `PLAN/S0149_enh-sftp-permission-denied-message/INDEX.md`.

**Status:** `[ ]` not done

---

### Step 04.2 — Refresh catalog and validate localization parity

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`, `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`, and `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_sftp_"`. Keep the generated catalog files in the same commit as the Kotlin changes. Run `/spec-check S0149` after the manual SFTP verification round moves the ticket out of `BlockNeedUserTest`.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists.
- `Grep` — `SftpOperationMessageResolver` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `error_sftp_(access_denied|server_rejected|move_copied_source_remains)` present in `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, and `app_v2/src/main/res/values-uk/strings.xml`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase — run `/spec-check S0149` after manual SFTP verification. While the ticket is `BlockNeedUserTest`, keep one `Timber.d("S0149: ..")` tag at each changed entry flow and remove those tags when the status leaves that block.

---

## Rollback Plan

Revert phase commit(s) — no schema, migration, or navigation state changed.