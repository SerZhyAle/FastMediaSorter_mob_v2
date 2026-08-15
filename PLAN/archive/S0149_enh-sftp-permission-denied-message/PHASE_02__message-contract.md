# Phase 02 — Message Contract

**Strategic spec:** [../S0149_enh-sftp-permission-denied-message.md](../S0149_enh-sftp-permission-denied-message.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Define localized SFTP failure copy and a resolver that turns typed failures into resource-backed UI and log payloads.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] The classification scope for access denied is locked before new copy is written.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpOperationMessageResolver.kt` | New | ≤ 240 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 20 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 — Add a resolver for SFTP operation messages

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpOperationMessageResolver.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a resolver that accepts `SftpOperationFailure` plus operation context and returns a resource id, format args, and a short log label. Cover at least access denied, generic server rejection, and move-after-copy where the destination file exists but source deletion failed.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpOperationMessageResolver.kt` exists.
- `Grep` — `fun resolve(` present.
- `Grep` — `copyCompleted` present.

**Status:** `[ ]` not done

---

### Step 02.2 — Add EN/RU/UK strings for SFTP write failures

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add the string keys consumed by the resolver for access denied, generic server rejection, and move copied/source remains copy. Check `docs/COMMUNICATION_POLICY.md` §2 for the error-message formula and validate the final copy against the §6 tone checklist. Keep Russian `ё` correct and use `..` instead of `...`.

**Verification:**

- `Grep` — `error_sftp_(access_denied|server_rejected|move_copied_source_remains)` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `error_sftp_(access_denied|server_rejected|move_copied_source_remains)` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `error_sftp_(access_denied|server_rejected|move_copied_source_remains)` present in `app_v2/src/main/res/values-uk/strings.xml`.
- `Strings pass COMMUNICATION_POLICY §6 checklist.`

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Resolver-backed copy exists in EN/RU/UK and can now be wired into `FileOperationResult` without new wording decisions.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed at runtime before Phase 03 wiring.