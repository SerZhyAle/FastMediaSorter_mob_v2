# Phase 03 — docs-catalog-cleanup

**Strategic spec:** [`../S0109_bugfix-chromeos-textinput.md`](../S0109_bugfix-chromeos-textinput.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-07
**Completed:** 2026-05-07

---

## Objective

Update FEATURES trilingual docs to reflect Chrome OS text input fix, regenerate the class catalog, and ensure all dev log entries are present.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |

> Landscape variant: no layout files touched — no portrait/landscape parity check needed.

---

## Steps

### Step 03.1 — Update FEATURES trilingual docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In all three `FEATURES` files, locate the bullet about **"Full keyboard navigation on all screens"** (section 2 "Media Browsing"). Append to that bullet:
>
> - EN: "; text input fields on Chrome OS (ARC++) now accept hardware keyboard input correctly."
> - RU: "; текстовые поля на Chrome OS (ARC++) теперь принимают ввод с физической клавиатуры корректно."
> - UK: "; текстові поля в Chrome OS (ARC++) тепер приймають введення з фізичної клавіатури коректно."
>
> No new bullets needed — this is a fix to an existing capability statement.

**Verification:**

- `Grep` — `Chrome OS (ARC++)` with `accept` (or `принимают` / `приймають`) matches in `FEATURES.md`, `FEATURES_RU.md`, `FEATURES_UK.md` respectively.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 3/3 PASS. Files: FEATURES.md, FEATURES_RU.md, FEATURES_UK.md. Dev log recorded.

---

### Step 03.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has modification timestamp after Phase 01 commit.
- `Grep` — `AddResourceKeyboardDelegate` with `isTextEditorFocused` matches in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification PASS (JSONL). app_v2.jsonl exists; AddResourceKeyboardDelegate LOC=50 (updated), `isTextEditorFocused` present in functions array. Note: .md table does not render per-method detail — JSONL is authoritative.

---

### Step 03.3 — Dev log entries

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 03.2

**Prompt for developer:**

> Run for every file touched across all phases that does not yet have a dev log entry:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceKeyboardDelegate.kt" "S0109" "Add isTextEditorFocused guard for Chrome OS text input"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt" "S0109" "Implement isTextEditorFocused in keyboard delegate callback"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt" "S0109" "Guard Escape key when text editor is focused"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0109" "Chrome OS text input fix noted in keyboard navigation feature"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0109" "Chrome OS text input fix noted (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0109" "Chrome OS text input fix noted (UK)"
> ```

**Verification:**

- `Grep` — `S0109` matches at least 6 times in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification PASS. 12 × S0109 entries in dev/CHANGELOG.md (≥6 required).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Run `/spec-check S0109`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After `/spec-check S0109` returns Verified, remove all `Timber.d("S0109:` tags from `.kt` files and commit the removal together with the status change.

---

## Rollback Plan

Revert phase commit(s) — docs-only changes, no code or data affected.
