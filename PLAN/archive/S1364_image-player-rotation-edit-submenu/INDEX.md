# Tactical Plan: S1364 - image-player-rotation-edit-submenu

**Strategic spec:** [`../S1364_image-player-rotation-edit-submenu.md`](../S1364_image-player-rotation-edit-submenu.md)
**Research inputs:** none as files - the code-shape research was performed at planning time and its findings are recorded in the step bodies below
**Feature:** Image player rotation naming and edit submenu
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | rotation-commands | - | ✅ Done | 5/5 | [PHASE_01__rotation-commands.md](PHASE_01__rotation-commands.md) |
| 02 | edit-submenu-embedded | 01 | ✅ Done | 2/2 | [PHASE_02__edit-submenu-embedded.md](PHASE_02__edit-submenu-embedded.md) |
| 03 | standalone-parity | 01, 02 | ✅ Done | 3/4 + 1 deferred | [PHASE_03__standalone-parity.md](PHASE_03__standalone-parity.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All four strategic §6 research items are `Resolved`, each by an owner ruling recorded in "Quiz decisions (2026-08-05)".

---

## Planning-time research findings

These were established by reading the code before the phases were written. They are recorded here because several of them contradict the shape a reader would assume, and a step that assumed otherwise would be wrong.

- **Rotation is a fixed `+90` with no direction parameter.** `PlayerViewModel.rotateSession90()` adds `ROTATION_STEP_DEGREES` to an accumulating `sessionRotationAngle` and wraps with `%`. Kotlin's `%` keeps the dividend's sign, so a negative step stores a negative angle unless normalized - Phase 01 normalizes rather than relying on `%`.
- **The rotation state is duplicated.** `StandalonePlayerViewModel` carries a textually identical `rotateSession90()` and the same two constants. Both are changed together or the two windows diverge.
- **The autorotate on/off value at menu-build time is `state.playerRotationSensorEnabled`**, not `state.showRotationToggle` - the latter only decides whether the item exists at all. `showOverflowMenu()` already holds the state it needs.
- **Three of the four "parity" commands already have working handlers** on the image/video standalone host and only lack a menu entry: crop-in-place (`cropDelegate.enterCropMode`), rename (`showStandaloneRenameDialog`), autorotate (`viewModel.toggleRotationSensor`). **Undo is the exception** - it has zero wiring on every standalone host, which is why it gets its own step rather than sharing one.
- **The submenu is a rendering grouping, not a command-set change.** `EDIT` is bar-capable at priority 210 and legitimately appears on the command bar when there is room; only the overflow rendering is grouped. This matches strategic §5 ("наполняемая из отфильтрованного списка планировщика") and means the planner needs no change for the grouping itself.
- **Two unit tests use exact-match `assertEquals` on a command list**, both inside the `isLiveVideoStream` early-return branch, which never reaches the general-path rotation adds. Adding a general-path enum entry does not perturb them; adding one to the stream branch would.

---

## UI decision record (self-check 5.5)

Phases 01-03 touch `ui/**` and menu resources, so the placement decisions must be on record before they are written:

- **Submenu composition** - owner ruling, strategic §6 item 1 and "Quiz decisions": editor, crop, crop-to-file, drawing, rotate +90 and -90, compressed copy go in; rename and undo stay outside. Reason given by the owner for the compressed copy: it produces a changed image, exactly like crop-to-file.
- **Standalone scope** - owner ruling, §6 item 3: full parity, not merely regrouping what is already there.
- **Toggle name** - owner ruling, §6 item 4: «Автоповорот экрана», chosen after the code showed it controls screen rotation rather than the picture.
- **Empty section** - §6 item 2: Android does not hide an empty submenu, so it is hidden manually, following the `SendToMenuManager` precedent named in the spec.
- **Submenu position** - not ruled by the owner and not guessed: it is derived, taking the lowest priority number among the members actually present so the group sorts exactly where its first member would have. Recorded here as an agent derivation, open to owner override.

---

## Out of scope, recorded

- Undo semantics beyond restoring a deleted file - **S1326** owns what undo can do at all. This ticket wires the existing capability into the standalone hosts; it does not extend it.
- Dead `PlayerHostCapabilities.supportsDeleteUndo` flag - **S1452**, found during this ticket's research.
- Label wording for draw / correct / edit-text - **S1365** (already landed, `BlockNeedUserTest`).
- Edit-dialog layout - **S1366**.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **not written here.** The gate as first drafted said "required", which was wrong: CLAUDE.md section 11 makes that file `/skill-release`-owned and populated from the `ALL_FEATURES` diff, never per-spec. Strategic §8 is satisfied by recording the capabilities in the inventory, which is what the release pipeline reads.
- [x] `docs/ALL_FEATURES.jsonl` carries a record for each addition **actually delivered** - two. The third thing §8 anticipated, undo in the separate window, was deferred (Phase 03 step 03.3) and is deliberately absent.
- [x] `dev/CHANGELOG.md` has an entry for the change.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2529 records.
- [x] Ticket parked at `BlockNeedUserTest` with the device checks named in the status note.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1364`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
