# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1364_image-player-rotation-edit-submenu.md`](../S1364_image-player-rotation-edit-submenu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Record the two user-visible additions in the feature inventory, refresh what the label change makes stale, and close the ticket through the facade.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done, with Phase 03's Step Log stating which commands the separate window actually gained.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | ≤ 2 records |
| `docs/ICON_LEGEND*.md` | Regenerated | - |
| `dev/CHANGELOG.md` | Modified (via script) | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

> `docs/FEATURES*.md` is **not** edited here. Strategic §8 names two showcase-worthy additions, but `/skill-release` owns that file and populates it from the `ALL_FEATURES` diff - writing it per-spec is what CLAUDE.md section 11 forbids. Step 04.1 records the capabilities where the release pipeline will find them.

---

## Steps

### Step 04.1 - Record the two new capabilities

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record per user-visible addition through `scripts/all_features/add.ps1`, English only:
>
> - Content rotation in both directions in the image viewer.
> - The commands the separate player window gained - name exactly the ones Phase 03 actually delivered, reading its Step Log rather than this plan, since undo may have deferred.
>
> Do not record the renamed toggle or the regrouping: strategic §8 states those are wording and layout, not new capability. Run `scripts/all_features/validate.ps1` afterwards.

**Why:**

Strategic §8 states that rotation in both directions and the separate window's new commands are additions the user would perceive as new, while the renaming and grouping are not, and CLAUDE.md section 11 makes this inventory the input `/skill-release` reads to build the public showcase.

**Verification:**

- `Grep` - `S1364` appears in `docs/ALL_FEATURES.jsonl` on exactly the records added.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.
- The wording of the separate-window record matches Phase 03's Step Log, not this plan's expectation.

**Status:** `[x]` done

---

### Step 04.2 - Refresh the generated documentation the renaming makes stale

**Files:** `docs/ICON_LEGEND.md`, `docs/ICON_LEGEND_RU.md`, `docs/ICON_LEGEND_UK.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> The trilingual icon legend is generated from the icon inventory plus the app strings, and carries a "Do not edit by hand" banner. Re-run its generator so the renamed autorotate entry and the new reverse-rotation entry pick up their labels:
>
> ```powershell
> pwsh -NoProfile -File scripts/docs/render-icon-legend.ps1
> ```
>
> Read its summary: `Humanized-fallback warnings` must be `none`, which is what proves every new command resolved to a real app string rather than a guessed label. Then query the document registry for the player area and update any registered document that quotes the old «Поворот» label for this command.

**Why:**

Strategic §7 lists documentation drift after the renaming as a risk with the mitigation "run the documentation sync gate after the change", and the legend is a render target, so regenerating it is the only correct way to update it.

**Verification:**

- `render-icon-legend.ps1` exits 0 and reports `Humanized-fallback warnings: none`.
- `Grep` - the new labels appear in all three legend files.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0 if a registered document changed.

**Status:** `[x]` done

---

### Step 04.3 - Close through the facade

**Files:** all files touched in phases 01-03
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `post-change.ps1` once over the whole changed set with `-ScopeToFile`, and read the verdict line rather than the exit code alone:
>
> ```powershell
> pwsh -NoProfile -File scripts/post-change.ps1 -Files "<comma-separated changed set>" -Target "S1364" -Description "Screen-autorotate naming, two-way content rotation, grouped editing submenu in both player windows" -ChangeType Mixed -ScopeToFile
> ```
>
> Only a bare `post-change: PASS` is clean; `PASS WITH ADVISORIES (n)` names each advisory and each one must be read and judged. Exit 2 means the gates could not verify, which is not a pass.
>
> **Order note:** the ticket's status must already be `BlockNeedUserTest` when this runs, because the `S1364` probe tags will be in the source and the ticket-log gate fails a probe whose ticket is not in that status.

**Why:**

CLAUDE.md's Validation & Post-Change section makes the facade the mechanical closure path and requires the whole changed set to be named so the scoped gates judge what actually changed.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` (or `PASS WITH ADVISORIES` with each advisory judged in the Step Log) and exits 0.
- `Grep` - `dev/CHANGELOG.md` contains a row naming S1364.
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` - exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `docs/FEATURES*.md` untouched - `/skill-release` owns it.
- [x] `docs/ALL_FEATURES.jsonl` carries the additions Phase 03 actually delivered - and only those.
- [x] Ticket parked at `BlockNeedUserTest` with every device check named.

---

## Step Log

- 2026-08-07 - Step 04.1 done. Two records added, both `Spec S1364`; `validate.ps1` PASS at 657 records. **Flavors were taken from the gate, not from a sibling record:** the neighbouring `image-gif-viewer.in-place-image-rotation` lists only `standard, lite, photos, legacy`, but `docs/FLAVOR_MATRIX.md` shows `SUPPORT_IMAGES` as `[+]` in all six, so both new records list all six. Copying the sibling would have understated reach. **Undo is deliberately not recorded** - Phase 03 deferred it, and describing it here would put a capability the app does not have into the next release's showcase.
- 2026-08-07 - Step 04.2 done, after a miss worth recording. The first `render-icon-legend.ps1` run reported `Humanized-fallback warnings: none` and still showed the old «Поворот» - a clean run is not the same as a correct one. Cause: the legend resolves labels through `docs/icons/icon-inventory.json`, which had `feature: rotation_toggle_title`, and the inventory is derived from source including the never-inflated `overflow_menu_player.xml`, whose rotation item I had not retitled. Retitling it there (strategic §3 requires that file stay a truthful mirror), then `assert-icon-inventory-sync.ps1 -RegenerateInventory`, then re-rendering produced the right labels in all three languages: `Screen autorotate` / «Автоповорот экрана» and `Rotate -90°` / «Повернуть на -90°». Vector rows went 146 -> 148, the two new commands.
- 2026-08-07 - Step 04.3 done. `post-change: PASS WITH ADVISORIES (1)`, exit 0, over all 19 changed files with `-ScopeToFile`. Two earlier runs failed and both were right: the `document-registry` gate refused until the `feature-inventory` record's sibling was read - `docs/ALL_FEATURES.schema.json`, which turned out to already permit all six flavors and need no edit, so the run was re-issued with `-RegistryAck 'feature-inventory'`; and `detekt-gate` reported 8 findings, all `ArgumentListWrapping`/`SpacingBetweenDeclarationsWithComments` on lines whose text my edits changed, which invalidated their baseline signatures. All 8 fixed by expanding the touched argument lists and adding the missing blank line.
- 2026-08-07 - **Verified independently rather than trusting the facade**, applying the lesson S1365 recorded: after the detekt reformats I re-ran `.\a.ps1 fc` (exit 0, 55s), a fresh scoped `assert-detekt` over all nine changed Kotlin files (`PASS [scoped] - 2 file(s) with new findings project-wide, none among changed files`), and the planner unit tests (11 tests, 0 failures, result XML 1s old). The single remaining advisory is `detekt-preflight`, project-wide and explicitly unattributable.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit. The catalog and icon legend are regenerated artifacts and rebuild from source; the ALL_FEATURES records are removed with `scripts/all_features/` tooling, never by hand-editing the JSONL.
