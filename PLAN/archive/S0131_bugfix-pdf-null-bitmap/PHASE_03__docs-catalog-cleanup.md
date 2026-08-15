# Phase 03 — docs-catalog-cleanup

**Strategic spec:** [`../S0131_bugfix-pdf-null-bitmap.md`](../S0131_bugfix-pdf-null-bitmap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 1 / 2
**Started:** 2026-05-09
**Completed:** —

---

## Objective

Regenerate the class catalog after `.kt` changes, record dev log entries for all touched files, and confirm no Timber debug tags leak past this phase.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | — |
| `dev/CATALOG/app_v2.md` | Modified | — |
| `dev/CHANGELOG.md` | Modified (via script) | — |

---

## Steps

### Step 3.1 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run catalog scan and render for the `app_v2` module to pick up changes to `PlayerLifecycleManager.kt` and `ImagePreloadHelper.kt`:
>
> ```powershell
> & "C:/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` modified timestamp ≥ today.
- `Glob` — `dev/CATALOG/app_v2.md` modified timestamp ≥ today.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. `app_v2.jsonl` and `app_v2.md` updated (993 records). Dev log recorded.

---

### Step 3.2 — Record dev log entries and remove S0131 debug tags

**Files:** `dev/CHANGELOG.md` (via script), `PlayerLifecycleManager.kt`, `ImagePreloadHelper.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> Record dev log entries for all files touched across Phases 01 and 02:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt" "bugfix" "S0131: guard lazy viewer-manager accesses in teardown with backing-field null checks"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt" "bugfix" "S0131: replace fixed 20MB native heap threshold with adaptive relative+absolute check"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "catalog" "S0131: regenerate catalog after PlayerLifecycleManager + ImagePreloadHelper edits"
> ```
>
> This phase is also when S0131 transitions to `Implemented`. After dev log entries are recorded, remove all `Timber.d("S0131:` debug tags from both modified files:
>
> ```powershell
> # Verify tags are present before removal
> Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt","app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt" -Pattern 'Timber\.d\("S0131:'
> ```
>
> Remove each matching line, then advance spec status:
>
> ```powershell
> & "C:/Program Files/PowerShell/7/pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0131 -Status Implemented
> ```
>
> Note: tags are removed at `Implemented` stage only after on-device confirmation that the fixed paths are exercised (logcat shows the tag at least once for each flow). If on-device test has not been performed yet, keep tags and set status to `BlockNeedUserTest` instead.

**Verification:**

- `Grep -rn "Timber.d(\"S0131:" app_v2/src/` — zero hits (tags removed after device test).
- `Grep -n "Log\.d(" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` — zero hits.
- `Grep -n "Log\.d(" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt` — zero hits.
- `dev/CHANGELOG.md` contains at least two entries referencing `S0131`.

**Status:** `[~] in progress`

**Step Log:**

- 2026-05-09 — Dev log recorded for 3 files. `S0131` Timber tags retained — awaiting on-device confirmation. Status → `BlockNeedUserTest`. Tags + final status flip to `Implemented` after user confirms logcat.

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] INDEX.md `Phases` counter updated to `3 / 3 done`, `Status` flipped to `Done`.
- [ ] Run `/spec-check S0131`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

No code changes in this phase — revert is not applicable. If catalog is corrupted: re-run `scan.ps1 + render.ps1`.
