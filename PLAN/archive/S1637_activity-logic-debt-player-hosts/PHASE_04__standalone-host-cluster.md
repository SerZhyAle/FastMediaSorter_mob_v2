# Phase 04 - Standalone host cluster

**Strategic spec:** [`../S1637_activity-logic-debt-player-hosts.md`](../S1637_activity-logic-debt-player-hosts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Move the six edit-cluster dependencies of `PhotoVideoStandaloneActivity` behind `ImageEditFactory`, and take its two self-built managers from the existing `StandaloneHostFactory`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [x] Backup taken before editing the host file (CLAUDE.md Rule 5) - a working-tree safety chore, not audit evidence: the copy is disposable by design and is deliberately not cited as a closing artifact.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1322 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandaloneHostFactory.kt` | Modified | ≤ 300 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 04.1 - Inject the factory and rewire the two dialog constructions

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `@Inject lateinit var imageEditFactory: ImageEditFactory`. In `openImageEditDialog()` (lines 1023-1032 at plan time) replace the five use case arguments of `ImageEditDialog(..)` with the matching factory properties, and in `ensureDrawHelper()` (line 193 at plan time) do the same for the `mergeDrawOverlayUseCase` argument of `StandaloneDrawSaveHelper(..)`. Leave both constructor signatures unchanged.

**Why:**

Strategic §6.1 measured all six of this host's cluster sites as constructor hand-offs, and §0 forbids changing manager constructor signatures.

**Verification:**

- `Grep` - `imageEditFactory` matches at least seven times in `PhotoVideoStandaloneActivity.kt`.
- `Grep` - `ImageEditDialog(` argument count is unchanged from the pre-phase file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Host injects ImageEditFactory by FQN (it lives in ui.player, this host in ui.player.standalone); the five ImageEditDialog use case arguments and the StandaloneDrawSaveHelper mergeDrawOverlayUseCase argument now read factory properties. Verified: seven imageEditFactory hits (one declaration, five dialog arguments, one draw helper), and the ImageEditDialog argument name list is byte-identical to the pre-phase backup - context, imagePath, five use cases, onEditComplete. Neither constructor signature touched.

---

### Step 04.2 - Delete the six cluster fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Delete the `@Inject` declarations for `rotateImageUseCase`, `flipImageUseCase`, `networkImageEditUseCase`, `applyImageFilterUseCase`, `adjustImageUseCase` and `mergeDrawOverlayUseCase`, together with any import left unused.

**Why:**

Strategic §2 goal 1 requires this host to declare no `@Inject` field of a domain type.

**Verification:**

- `Grep` - each of the six field names returns zero hits in `PhotoVideoStandaloneActivity.kt`.
- `pwsh -NoProfile -File scripts/quality/assert-activity-logic-not-growing.ps1` reports `actual 17` when Phase 03 is already done.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Six @Inject declarations deleted, plus the now-stale 'S0393 wave-C: image edit dialog use-cases' comment that described them. Verified: all six names return zero hits in the file, gate reads actual 17 and ratcheted its baseline 23 -> 17 - exactly the INDEX field budget for phases 01-04. File 1322 -> 1315 lines. No import cleanup needed; every deleted field named its type by FQN.

---

### Step 04.3 - Take `TranslationManager` and `DestinationButtonsManager` from `StandaloneHostFactory`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandaloneHostFactory.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Replace the two `by lazy` manager constructions in `PhotoVideoStandaloneActivity` (`TranslationManager` at line 296, `DestinationButtonsManager` at line 538 at plan time) with calls to the existing `StandaloneHostFactory`, which already builds both for four other hosts. Extend the factory only if its current entry point cannot serve this host as written; do not create a second factory. Only these two managers are in scope - `NetworkFileManager`, `PdfViewerManager` and `EpubViewerManager` do not occur in this file.

**Why:**

Strategic §4 records that this host self-builds exactly two of the managers the factory already owns, and §0 forbids re-adding those dependencies anywhere, including to a new parallel factory.

**Verification:**

- `Grep` - `TranslationManager(` and `DestinationButtonsManager(` return zero direct-construction hits in `PhotoVideoStandaloneActivity.kt`.
- `Grep` - `StandaloneHostFactory` matches at least once in `PhotoVideoStandaloneActivity.kt`.
- `Grep` - no new class whose name ends in `Factory` was added under `ui/player/standalone/`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Both by-lazy manager constructions now call the existing StandaloneHostFactory - createTranslationManager for the OCR facade and createDestinationButtons for the Copy/Move panels; the host injects the factory as its fifth standalone consumer. The factory needed no extension: both entry points already build exactly what this host built by hand. Verified: zero direct helpers.TranslationManager( and player.DestinationButtonsManager( constructions, four StandaloneHostFactory references, and StandaloneHostFactory.kt is still the only *Factory class under ui/player/standalone. File 1315 -> 1313 lines.

---

### Step 04.4 - Drop the fields the two managers needed

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Remove any `@Inject` field that existed only to feed the two managers rewired in step 04.3 and now has no remaining reader. Leave fields that still have other readers to Phase 05; do not force them out here.

**Why:**

Strategic §2 goal 1 counts fields, not managers, and a field left behind after its only consumer moved is dead weight under CLAUDE.md Rule 20.

**Verification:**

- `Grep` - every field name removed in this step returns zero hits in `PhotoVideoStandaloneActivity.kt`.
- `pwsh -NoProfile -File scripts/quality/assert-activity-logic-not-growing.ps1` count matches the INDEX field budget for phases 01-04, or the INDEX budget is corrected in the same change.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - No field qualified: the step removes only fields whose last reader left with the two managers, and neither candidate is one. settingsRepository still has eleven references (OCR settings read, the file-operations handler's getCurrentSettings, and more) and getDestinationsUseCase still feeds the StandaloneFileOperationsHandler construction at line 522. Both belong to phase 05 by this step's own instruction. Gate reads actual 17, which is the INDEX budget for phases 01-04 (32 - 1 - 8 - 6), so no budget correction is due.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Both hosts now reach the shared cluster through one supplier, and the standalone host uses the shared manager factory. What remains is the non-cluster tail.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
