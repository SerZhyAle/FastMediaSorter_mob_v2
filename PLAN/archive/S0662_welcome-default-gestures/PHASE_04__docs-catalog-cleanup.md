# Phase 04 - docs-catalog-cleanup

**Strategic spec:** [`../S0662_welcome-default-gestures.md`](../S0662_welcome-default-gestures.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Record the shipped capability, regenerate the class catalog, batch the dev log, and prove the standard debug build before device verification.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via `add.ps1`) | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | n/a |
| `dev/CHANGELOG.md` | Appended (via `add_to_dev_log.ps1`) | n/a |

> `docs/FEATURES*.md` is intentionally NOT edited here - it is `/skill-release`-owned and populated from the `ALL_FEATURES` diff at release (CLAUDE.md Rule 11). The capability is standard + noLegal, so it goes to `docs/ALL_FEATURES.jsonl` (not `_noLegal`). No data-class default changed (runtime seeding), so the settings manifest is unchanged; the `post-change.ps1` settings-doc-sync gate confirms this - only regenerate the manifest/reference/annotations if it flags.

---

## Steps

### Step 04.1 - Record the capability in ALL_FEATURES.jsonl

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing: an onboarding toggle to enable left-edge gestures, plus default install bindings (swipe up = open the launch panel, swipe right = screenshot then open the editor, swipe down = silent screenshot with a toast); available on standard (when screen capture is enabled) and noLegal. Then validate with `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - the new gesture-onboarding record is present in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Recorded capability in docs/ALL_FEATURES.jsonl as `onboarding.welcome-gesture-enablement` (standard+noLegal). close-and-log FuncOp ADD emitted a spec-id-prefixed id (`s0662.*`) rejected by validate.ps1; fixed this record to the area slug. validate.ps1 still reports 4 pre-existing same-shape failures from prior tickets (S0646/S0656/S0644/S0648) - out of scope, parked as S0665 (close-and-log id-derivation bug).

---

### Step 04.2 - Regenerate catalog and set roles for new classes

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Set `role` + `status` for the new classes via `set.ps1`: `SeedDefaultGestureBindingsUseCase` and `WelcomeGesturesManager`. Both compile in `src/main` (shared) - no `-NoFlavors` needed; the capability gate is the runtime controller-set presence.

**Verification:**

- `Grep` (Grep tool, catalog is gitignored) - `SeedDefaultGestureBindingsUseCase` and `WelcomeGesturesManager` present in `dev/CATALOG/app_v2.jsonl` with a non-empty `role`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - catalog_sync via close-and-log (2006 records); set.ps1 role+status=new for SeedDefaultGestureBindingsUseCase and WelcomeGesturesManager.

---

### Step 04.3 - Batch dev log and prove the build

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add one dev-log entry for the S0662 change set via `.\scripts\add_to_dev_log.ps1` (or `close-and-log.ps1 -DevLogs` for the batch). Run `pwsh -NoProfile -File scripts/post-change.ps1` gates over the touched files (neuroslop, ticket-log, strings, settings-doc-sync) and fix any failure. Build the standard debug APK with `.\a.ps1 d` to prove packaging before device verification.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` has an S0662 entry.
- `.\a.ps1 d` completes successfully (read the build log for the real verdict).

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - 8 dev-log entries via close-and-log; `.\a.ps1 d` BUILD SUCCESSFUL in 38s (APK produced). S0662 debug tags inserted (2 flow entries) before this final build.

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] `docs/ALL_FEATURES.jsonl` validates.
- [x] Standard debug build passes (`.\a.ps1 d` - BUILD SUCCESSFUL).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, `/spec-dev` inserts the S0662 debug tags and moves the ticket to `BlockNeedUserTest`. Device verification: fresh install on standard (screen capture on) and noLegal shows the toggle; lite/photos/legacy show it absent; enabling requests the permission and activates up = launch panel, right = screenshot + editor, down = silent screenshot + toast; an upgrade install keeps prior gesture configuration.

---

## Rollback Plan

Revert the phase commit(s). The catalog is regenerated (not committed); `ALL_FEATURES.jsonl` and the dev log are append-only documentation - remove the added lines to revert.
