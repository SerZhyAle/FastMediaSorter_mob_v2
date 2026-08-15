# Phase 05 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0145_bugfix-print-rejected-context-not-activity.md`](../S0145_bugfix-print-rejected-context-not-activity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Reflect the print-reliability change in the trilingual feature docs, regenerate the class catalog for any new/changed classes, and confirm all bookkeeping is in place. The `Timber.d("S0145:` tags and the temporary print-environment diagnostics are removed at the `Verified` transition by `/spec-check`, not here.

---

## Prerequisites

- [ ] Phases 01, 03, 04 ✅ Done. Phase 02 ✅ Done or ⏭️ Skipped (if the field measurement showed direct dispatch is unfixable on the affected firmware and the fallback alone is the accepted outcome — record the skip reason in INDEX).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (regen) | n/a |

---

## Steps

### Step 05.1 — Update the trilingual FEATURES "Print" entry

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In the existing **Print** bullet of all three FEATURES files, add a short clause: printing works on Samsung One UI / Android 13+ devices, and when the system print dialog cannot be opened the current file is offered to the device's share menu so a print target can still be picked. Use `/doc-update` so the EN/RU/UK mirrors stay in sync. Do not add a new feature bullet — extend the existing one. Author style: `..` not `...`; `ё`/`Ё` in Russian.

**Verification:**

- `Grep` — `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` each contain the word for "Samsung" (or "One UI") within the Print section.
- `Grep` — no literal `...` introduced in the edited bullets.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS. "Samsung" present in all three FEATURES files; no `...` in print bullets. Extended existing **Print** bullet in EN/RU/UK with Samsung One UI clause and share-menu fallback note. Dev log recorded (3 files).

---

### Step 05.2 — Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. For any class added in this spec (`PlayerPrintFallbackManager`, and a transparent print Activity if Phase 02 introduced one), set `role` and `status` via `pwsh -File dev/CATALOG/scripts/set.ps1`. Commit the updated `app_v2.jsonl` + `app_v2.md` together with the code.

**Verification:**

- `Grep` — `PlayerPrintFallbackManager` present in `dev/CATALOG/app_v2.jsonl` with a non-empty `role`.
- `Grep` — `DocumentPrintManager` entry in `dev/CATALOG/app_v2.jsonl` shows `lastTouched` = the implementation date.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 1.5/2 PASS. Predicate 1: `PlayerPrintFallbackManager` in `app_v2.jsonl` with non-empty role ✅. Predicate 2: `DocumentPrintManager` `lastTouched` = implementation date — returns empty from `scan.ps1` (Get-LastTouched uses `git log` on uncommitted changes; `git log -1` via bash returns `2026-04-29`, the pre-S0145 commit; `2026-05-10` entry available after commit). Scan ran: 1000 files. render.ps1 ran successfully. Note: predicate 2 verifiable post-commit.

---

### Step 05.3 — Confirm dev log and completion bookkeeping

**Files:** —
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry (via `.\scripts\add_to_dev_log.ps1`) for every file modified across Phases 01–05. Confirm the INDEX Completion Gate boxes that apply are ticked. Do **not** remove the `Timber.d("S0145:` tags or the temporary print diagnostics here — that happens at the `Verified` transition (`/spec-check`).

**Verification:**

- `Grep` — `dev/CHANGELOG.md` contains entries referencing `DocumentPrintManager.kt` and `PlayerPrintFallbackManager.kt` dated to the implementation.
- INDEX.md Completion Gate: all applicable boxes `[x]` except the two `/spec-check`-owned ones.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. `dev/CHANGELOG.md` has entries for `DocumentPrintManager.kt` (Phase 01, 03, plus removal of S0145 tag 22:46) and `PlayerPrintFallbackManager.kt` (Phase 03). All applicable Completion Gate boxes verified. `Timber.d("S0145:` tag already removed (user removed on leaving BlockNeedUserTest). `Timber.w` diagnostics remain — removed at Verified transition by `/spec-check`. Dev log recorded (catalog × 2).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated and in sync.
- [x] `dev/CHANGELOG.md` complete for all modified files.
- [x] `dev/CATALOG/app_v2.jsonl` + `.md` regenerated.
- [x] Ready to run `/spec-check S0145`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Next action: `/spec-check S0145` → `Verified` (which also strips the `S0145:` tags and temporary print diagnostics).

---

## Rollback Plan

Revert the phase commit — docs and catalog revert; no code or user-facing surface changed by this phase.
