# Phase 04 — Docs, Catalog, Cleanup

**Strategic spec:** [../S0176_nolegal-session-context-etld-fix.md](../S0176_nolegal-session-context-etld-fix.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none — final phase
**Steps done:** 3 / 3
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Close the bookkeeping, refresh the catalog, and leave S0176 ready for user verification.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] The implementation is ready to enter `BlockNeedUserTest`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0176_nolegal-session-context-etld-fix.md` | Modified | ≤ 260 |
| `PLAN/S0176_nolegal-session-context-etld-fix/INDEX.md` | Modified | ≤ 220 |
| `dev/CHANGELOG.md` | Modified | script-managed |
| `dev/CATALOG/app_v2.jsonl` | Modified | script-generated |
| `dev/CATALOG/app_v2.md` | Modified | script-generated |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split before continuing.

---

## Steps

### Step 04.1 — Record dev log entries for every touched file

**Files:** `dev/CHANGELOG.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `./scripts/add_to_dev_log.ps1` for every Kotlin, test, and spec file touched by S0176. Keep the descriptions short and specific to the changed behavior.

**Verification:**

- `Grep` — `S0176` is present in `dev/CHANGELOG.md`.
- `Grep` — `LinkAutoDownloadCoordinator.kt` is present in `dev/CHANGELOG.md`.
- `Grep` — `LinkCookieDomainResolver.kt` is present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Dev log: 6 entries added for all S0176 files.

---

### Step 04.2 — Regenerate the app_v2 catalog outputs

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` and `render.ps1` after the Kotlin changes land. Commit the regenerated catalog outputs together with the source changes.

**Verification:**

- `Grep` — `LinkCookieDomainResolver` is present in `dev/CATALOG/app_v2.md`.
- `Grep` — `LinkCookieDomainResolver` is present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `LinkAutoDownloadCoordinator` is present in `dev/CATALOG/app_v2.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. scan.ps1 (1016 files), render.ps1 — both complete. LinkCookieDomainResolver present in catalog.

---

### Step 04.3 — Hand the ticket off for user verification

**Files:** `PLAN/S0176_nolegal-session-context-etld-fix.md`, `PLAN/S0176_nolegal-session-context-etld-fix/INDEX.md`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Move the ticket into `BlockNeedUserTest`, update the tactical files to reflect completed phases, and leave exactly one `Timber.d("S0176: ...")` entry-point tag in the changed coordinator flow. Do not remove the tag until `/spec-check` advances the ticket out of `BlockNeedUserTest`.

**Verification:**

- `Grep` — `**Status:** BlockNeedUserTest` is present in `PLAN/S0176_nolegal-session-context-etld-fix.md`.
- `Grep` — `✅ Done` is present in the completed phase rows inside `PLAN/S0176_nolegal-session-context-etld-fix/INDEX.md`.
- `Grep` — `Timber.d("S0176:` is present in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Timber.d("S0176: applySessionContext entry") inserted in coordinator. Strategic spec flipped to BlockNeedUserTest. Journal updated via update.ps1.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Dev log entry added for every file touched during S0176.
- [x] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated and committed.
- [x] The ticket is parked in `BlockNeedUserTest` with its debug tag intact.
- [x] Final phase — see INDEX.md Completion Gate.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the final bookkeeping commit(s) and restore the prior tactical/spec status if user verification must be postponed.