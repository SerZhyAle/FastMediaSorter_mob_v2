# Phase 06 - Device-test tags, dev-log, capability inventory

**Goal:** Final build with device-test probes, journaling, and capability record.

These features are interaction-only and need on-device verification (mouse + touch gestures) - the pipeline sets `BlockNeedUserTest`.

## Steps

- [ ] Insert `Timber.d("S0512: <entry-point>")` probes at the changed-flow entry points (one per flow), as the final code edits before the last build:
  - Browse drag-select start (`BrowseDragSelectManager` selection-start callback).
  - Duplicates within-group drag-select start.
  - Hover overlay is passive (no code path) - no probe needed.
  - Verification: exactly the changed flows carry one `Timber.d("S0512:` each; no probe on unchanged code.
- [ ] Build: `.\a.ps1 dq` (standard debug, validates code + tags in one pass). Expect PASS.
  - Verification: exit 0.
- [ ] Capability inventory: `scripts/all_features/add.ps1` - record the user-visible capability (mouse band-select + touch drag-select + hover-highlight on Browse/Duplicates lists). EN-only.
  - Verification: `docs/ALL_FEATURES.jsonl` has an S0512 record.
- [ ] Dev log + catalog sync via `close-and-log.ps1` on the `BlockNeedUserTest` transition (`-StatusNote` describing what to test on device: mouse band-select rectangle in Browse-grid, touch drag in multi-select mode, within-group drag in Duplicates, hover highlight under mouse).
  - Verification: `dev/CHANGELOG.md` updated; status header shows `BlockNeedUserTest` + note.

## Device-test checklist (for user)

- Browse-grid: mouse drag over empty area draws a band and selects swept items.
- Browse-grid: with items already selected, finger drag extends selection.
- Browse list + grid: item under mouse shows hovered tint, no focus-ring stolen.
- Duplicates: mouse/touch drag within an expanded group selects file rows.
- Duplicates: hovered file row shows tint.
