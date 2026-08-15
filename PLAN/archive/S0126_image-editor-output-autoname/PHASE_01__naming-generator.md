# Phase 01 — naming-generator

**Strategic spec:** [`../S0126_image-editor-output-autoname.md`](../S0126_image-editor-output-autoname.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Create `ImageEditorFileNamer` — a single-responsibility object that generates the standardised output filename for all image editor operations.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageEditorFileNamer.kt` | New | ≤ 40 |

---

## Steps

### Step 1.1 — Create ImageEditorFileNamer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageEditorFileNamer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file `ImageEditorFileNamer.kt` in `ui/player/helpers/`. Define a Kotlin `object ImageEditorFileNamer` with:
>
> - String constants `CROP = "crop"`, `COMPRESS = "compress"`, `DRAW = "draw"`.
> - A function `fun buildName(baseName: String, ext: String, operation: String): String` that returns
>   `"${baseName}_${operation}-${timestamp}.${ext}"` where `timestamp` is `LocalDateTime.now()` formatted
>   as `"yyMMdd-HHmm"` (hyphen between date and time parts). The `ext` parameter must not contain a
>   leading dot — the function appends one itself.
> - Add `Timber.d("S0126: buildName op=$operation base=$baseName")` inside `buildName`.
> - Import: `java.time.LocalDateTime`, `java.time.format.DateTimeFormatter`, `timber.log.Timber`.
> - No other logic.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageEditorFileNamer.kt` exists.
- `Grep` — `object ImageEditorFileNamer` matches exactly once in that file.
- `Grep` — `fun buildName` matches exactly once in that file.
- `Grep` — `"yyMMdd-HHmm"` (hyphen, not underscore) matches in that file.
- `Grep` — `Timber.d("S0126:` matches in that file.
- `Grep -n "Log\.d\("` — zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 6/6 PASS. Files: ImageEditorFileNamer.kt (new, 30 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Step 1.1 above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "app_v2/.../ImageEditorFileNamer.kt" "S0126" "Phase 01: add ImageEditorFileNamer utility"`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 02 may now import `ImageEditorFileNamer` from the same package. The constants `CROP`, `COMPRESS`, `DRAW` replace all inline string literals for operation names.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
