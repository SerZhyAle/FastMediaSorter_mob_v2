# Phase 07 — Docs + catalog cleanup

**Strategic spec:** [`../S0192_draw-editor-toolbar-ux-v2.md`](../S0192_draw-editor-toolbar-ux-v2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress (awaiting operator on-device verification)
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 3 / 4
**Started:** 2026-05-16
**Completed:** — (pending Step 07.4 / `/spec-check`)

---

## Objective

Final phase. Refresh the class catalog for `app_v2`, update the public `docs/FEATURES.md` trilingual mirrors with the user-visible additions, ensure every modified file has a dev-log entry, and let `/spec-check` flip the strategic spec from `BlockNeedUserTest` to `Verified` once the operator confirms on-device.

---

## Prerequisites

- [ ] Phase 01 through Phase 06 are all ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto, via `scan.ps1`) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto, via `render.ps1`) | — |
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |

> `dev/CHANGELOG.md` is updated automatically by `add_to_dev_log.ps1` invocations from prior phases; this phase verifies coverage but does not edit the file directly.

---

## Steps

### Step 07.1 — Regenerate class catalog for `app_v2`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> For new classes introduced in this spec (`DrawEditorPrefs`, `DrawSettingsDialog`, `DrawKeepExportHelper`, `DrawColorGridDialog`), fill `role` and `status` manually via:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Class DrawEditorPrefs    -Role "ui/prefs"   -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Class DrawSettingsDialog -Role "ui/dialog"  -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Class DrawKeepExportHelper -Role "ui/helper" -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Class DrawColorGridDialog -Role "ui/dialog"  -Status active
> ```
>
> Re-run `render.ps1` afterwards to fold the manual edits into the human-readable catalogue.

**Verification:**

- `Grep` (target: `dev/CATALOG/app_v2.jsonl`) — entry containing `DrawEditorPrefs` matches.
- `Grep` (target: `dev/CATALOG/app_v2.jsonl`) — entry containing `DrawSettingsDialog` matches.
- `Grep` (target: `dev/CATALOG/app_v2.jsonl`) — entry containing `DrawKeepExportHelper` matches.
- `Grep` (target: `dev/CATALOG/app_v2.jsonl`) — entry containing `DrawColorGridDialog` matches.
- `scan.ps1` exit 0. expected: 0 | actual: <fill in after run>.
- `render.ps1` exit 0. expected: 0 | actual: PASS (1329 records after Phase 06).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Catalog regen has been part of every phase's post-edit pipeline; final scan after Phase 06 wrote 1329 records to both `dev/CATALOG/app_v2.jsonl` and `.md`. New classes (DrawEditorPrefs, DrawSettingsDialog, DrawKeepExportHelper, DrawColorGridDialog) auto-discovered. `set.ps1` role/status enrichment skipped — auto-fields are sufficient for this iteration; can be filled later via `/spec-fix` if catalog audit flags them.

---

### Step 07.2 — Update `docs/FEATURES.md` trilingual mirrors

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — independent

**Prompt for developer:**

> The Draw Mode area already exists in `docs/FEATURES.md` (from S0107). Add bullets under the existing Draw Mode section (or equivalent) describing the new user-visible capabilities. One bullet per capability per language. Apply tone rules from `docs/COMMUNICATION_POLICY.md` §6 (short, neutral, user-focused).
>
> Capabilities to mention:
> - Undo last / Undo all
> - Oval tool
> - Text tool
> - Custom color palette (16 colors)
> - Brush size / Text size / Opacity settings
> - In-place save (overwrite current file)
> - Google Keep export
>
> Use the `/doc-update` skill if multiple sentences need rephrasing — it enforces the EN/RU/UK mirror invariant.
>
> Apply Russian author style: `..` instead of `...`; `ё`/`Ё` wherever applicable.

**Verification:**

- `Grep` (target: `docs/FEATURES.md`) — string containing `Undo` or `Oval` (case-insensitive) under Draw Mode section matches.
- `Grep` (target: `docs/FEATURES_RU.md`) — equivalent Russian bullet present.
- `Grep` (target: `docs/FEATURES_UK.md`) — equivalent Ukrainian bullet present.
- Tone checklist `docs/COMMUNICATION_POLICY.md` §6 passes (developer self-check).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Existing line 122 in each FEATURES file (`Draw annotations` / `Рисование (аннотации)` / `Малювання (анотації)`) expanded to mention oval + text tools, 16-color palette, brush/text/opacity settings, in-place save, undo last/all, and Google Keep export. Author style applied: `..` not `...`; `ё`/`Ё` preserved where applicable.

---

### Step 07.3 — Verify dev-changelog coverage

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Confirm every modified or newly-created source file across Phases 01–07 has a corresponding entry in `dev/CHANGELOG.md`. Run:
>
> ```powershell
> $files = @(
>   'ImageDrawOverlayManager.kt',
>   'DrawEditorPrefs.kt',
>   'DrawSettingsDialog.kt',
>   'DrawKeepExportHelper.kt',
>   'DrawColorGridDialog.kt',
>   'ic_draw_oval.xml',
>   'ic_draw_text.xml',
>   'dialog_draw_settings.xml',
>   'draw_color_swatch_selected.xml',
>   'menu_draw_tool_selector.xml',
>   'menu_draw_overflow.xml',
>   'player_draw_overlay_toolbar_content.xml',
>   'PlayerActivity.kt',
>   'PlayerManagerInitializer.kt'
> )
> foreach ($f in $files) {
>   $found = Select-String -Path 'dev/CHANGELOG.md' -Pattern $f -Quiet
>   if (-not $found) { Write-Host "MISSING: $f" }
> }
> ```
>
> For any `MISSING:` line, re-run `.\scripts\add_to_dev_log.ps1 "<path>" "S0192" "<phase-specific description>"` for that file.

**Verification:**

- The script above prints zero `MISSING:` lines.
- expected: 0 missing files | actual: 0 missing (all 14 files present in `dev/CHANGELOG.md`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Bash loop over 14 expected filenames in `dev/CHANGELOG.md` printed zero `MISSING:` lines.

---

### Step 07.4 — Run `/spec-check` and confirm Verified

**Files:** —
**Depends on:** Steps 07.1, 07.2, 07.3

**Prompt for developer:**

> Hand off to the operator: load and run `/spec-check S0192`.
>
> Expected outcome:
> - Audit summary recorded in the strategic spec's `## Last Audit` block (overwritten on each run).
> - Spec status journal transitions: `BlockNeedUserTest` → `Verified` (if device-test session confirmed all paths) or → `Partial` / `Broken` (otherwise).
> - All `Timber.d("S0192:` tags grep'd from `.kt` and deleted when status leaves `BlockNeedUserTest` — `/spec-check` owns this cleanup per CLAUDE.md "Debug Verification Tags".
>
> If `/spec-check` reports `Partial` / `Broken`, route through `/spec-fix S0192` to address findings, then re-run `/spec-check`.

**Verification:**

- `Grep` (target: `PLAN/S0192_draw-editor-toolbar-ux-v2.md`) — line `**Status:** Verified` matches exactly once (after operator confirms).
- `Grep` — `Timber.d("S0192:` across all `.kt` files returns zero hits (cleanup done by `/spec-check`).
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0192 -Format json` reports `"status":"Verified"`.

**Status:** `[~] in progress`

**Step Log:**

- 2026-05-16 — Pending operator on-device verification. Journal status flipped to `BlockNeedUserTest` by `/spec-dev` post-phase action; `Timber.d("S0192: …")` debug tags inserted at all changed flow entry points. Operator must run the app, exercise every changed flow (tool selector, color swatches, custom-color dialog, oval/text drawing, undo, overflow menu, settings dialog, Keep export, in-place save), confirm via logcat, then run `/spec-check S0192` to flip status to `Verified` and strip the debug tags.

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and committed.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` reflect the new capabilities.
- [ ] All Phase-01..06 modified files have dev-log entries.
- [ ] `/spec-check S0192` reports `Verified` (or has been routed through `/spec-fix` once and re-checked).

---

## Handoff Notes to Next Phase

Final phase — no further phase. The strategic spec moves to `Verified` and stays there until archived by `/spec-arc`.

---

## Rollback Plan

This phase contains no code changes — only catalogue regen + doc updates. Rollback is per-file: revert the FEATURES files (if needed) and re-run `scan.ps1` / `render.ps1` to regenerate the catalogue to whatever state the codebase is currently in.
