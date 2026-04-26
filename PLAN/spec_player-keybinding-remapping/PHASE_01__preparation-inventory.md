# Phase 01 — Preparation & Inventory

**Strategic spec:** [`../spec_player-keybinding-remapping.md`](../spec_player-keybinding-remapping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 8 / 8
**Started:** 2026-04-25
**Completed:** 2026-04-25

---

## Objective

Produce a verified inventory of every input engine, command model, hardcoded trigger, debounce/deadzone constant and unbound candidate command in the codebase. Output is a set of research artefacts in `temp/phase1/` that Phase 02 consumes as input. No production code edits.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `dev/PROJECT_OPERATIONS_INDEX.md` read — Feature-to-Path Map understood.
- [ ] Strategic spec §10 Ambiguity Gate items are **not** a blocker for this phase — none resolves inventory output.

---

## Files Touched

| File | New / Modified | Line budget |
| ---- | :------------: | ----------: |
| `temp/phase1/engine-paths-verified.txt` | New | ≤ 200 |
| `temp/phase1/trigger-catalogue-raw.txt` | New | ≤ 2000 |
| `temp/phase1/emitted-actions.txt` | New | ≤ 500 |
| `temp/phase1/debounce-literals.md` | New | ≤ 100 |
| `temp/phase1/new-engines-scan.txt` | New | ≤ 100 |
| `temp/phase1/commandid-candidates.md` | New | ≤ 500 |
| `temp/phase1/defaults-seed.md` | New | ≤ 500 |
| `../spec_player-keybinding-remapping.md` | Modified | — (§10 resolution column only) |

> All artefacts land in `temp/phase1/` — CLAUDE.md §Strict-Rules-1 forbids writes to project root.

---

## Steps

### Step 01.1 — Verify engine paths resolve

**Files:** `temp/phase1/engine-paths-verified.txt` (new)
**Depends on:** — start of phase
**Status:** `[x]` done

**Prompt for developer:**

> For each of the 17 engines listed in the Engine Inventory below, confirm the file exists via `Glob`. Record one line per engine in `temp/phase1/engine-paths-verified.txt` as `<engine-id>\t<relative path>\t<OK|MISSING>`. Treat the path as *relative to* `app_v2/src/<sourceset>/java/com/sza/fastmediasorter/`. Engines to verify:
>
> K1 `util/KeyboardShortcutHandler.kt`, K2 `ui/player/helpers/PlayerKeyboardHandler.kt`, K3 `ui/main/helpers/KeyboardNavigationHandler.kt`, K4 `ui/dialog/DialogKeyboardDelegate.kt`, G1 `core/input/GamepadInputManager.kt`, M1 `ui/common/MouseEventHandler.kt`, R1 `ui/player/MediaButtonRestartReceiver.kt`, R2 `ui/player/AudioPlaybackService.kt`, V1 (sourceset `vr`) `vr/helpers/VrControllerInputManager.kt`, F1 `ui/common/FocusManager.kt`, T1 `ui/player/helpers/PlayerGestureManager.kt`, T2 `ui/player/helpers/VideoTouchDelegate.kt`, T3 `ui/player/helpers/StandaloneVideoTouchDelegate.kt`, T4 `ui/player/helpers/TouchZoneGestureManager.kt`, T5 `ui/player/helpers/EpubViewerManager.kt`, T6 `ui/player/helpers/TextViewerManager.kt`, T7 `ui/player/helpers/PdfViewerManager.kt`, T8 `ui/player/VerticalSeekBar.kt`.
>
> Any `MISSING` row must be investigated — either the engine was renamed by a "Wave" decomposition or the inventory is stale. Update Step 01.5 (new-engines scan) accordingly and add a note in the artefact.

**Verification:**

- `Glob` — `temp/phase1/engine-paths-verified.txt` exists.
- `Grep` — file contains 18 lines (17 engines + F1 spatial-nav component).
- `Grep -c "MISSING"` in the file returns 0, OR every `MISSING` has an accompanying `# reason:` line.

**Step Log:**

- 2026-04-25 — applied, Verification 3/3 PASS. Files: temp/phase1/engine-paths-verified.txt (+18 lines). All 18 engines OK.

---

### Step 01.2 — Re-grep every hardcoded trigger literal

**Files:** `temp/phase1/trigger-catalogue-raw.txt` (new)
**Depends on:** Step 01.1
**Status:** `[x]` done

**Prompt for developer:**

> Run a structured `Grep` sweep across all engine files identified in Step 01.1 (main + vr sourcesets). Capture every hardcoded `KeyEvent.KEYCODE_*`, `KeyEvent.BUTTON_*`, `MotionEvent.AXIS_*`, and `XrInputEventType.*` literal with its file path + line number + surrounding `when`/`if` branch. Output is TSV: `<engine-id>\t<file>:<line>\t<literal>\t<resolved-action-symbol>`. Group by engine. Use this regex for the initial sweep: `(KEYCODE_[A-Z0-9_]+|BUTTON_[A-Z0-9_]+|AXIS_[A-Z0-9_]+|XrInputEventType\.[A-Z0-9_]+)`.

**Verification:**

- `Glob` — `temp/phase1/trigger-catalogue-raw.txt` exists.
- `Grep -c "^K1\t"` returns ≥ 30 (KeyboardShortcutHandler has dense mapping; strategic §6.1 lists 35+ rows).
- `Grep -c "^G1\t"` returns ≥ 10 (gamepad dual-surface mapping).
- `Grep -c "^V1\t"` returns ≥ 12 (VR OpenXR event types).
- No line contains `KEYCODE_UNKNOWN` as a literal (that code must not be used for dispatch).

**Step Log:**

- 2026-04-25 — applied, Verification 5/5 PASS. K1=139, G1=18, V1=79, KEYCODE_UNKNOWN=0. WARN: K2:178 uses KEYCODE_UNKNOWN as scan-code fixup detection (not dispatch) — excluded from catalogue, noted in file header. Files: temp/phase1/trigger-catalogue-raw.txt (+254 lines). Dev log entry recorded.

---

### Step 01.3 — Extract emitted action variants

**Files:** `temp/phase1/emitted-actions.txt` (new)
**Depends on:** Step 01.2
**Status:** `[x]` done

**Prompt for developer:**

> Grep every engine for calls that dispatch an app-level action: patterns `onCommand(`, `dispatch(`, `fire(`, `invoke(`, `InputAction.`, `PlaybackCommand.`, `GamepadAction.`, `PlayerAction.`, `BrowserAction.`. Extract the symbolic variant being emitted. Output TSV: `<engine-id>\t<file>:<line>\t<model>.<variant>`.
>
> Then compare each emitted variant against the command-model declarations listed in strategic §5 (`InputAction.kt`, `GamepadAction.kt`, `PlaybackCommandModel.kt`, `XrInputEventType.kt`). Any variant that is emitted but missing from the declarations, OR declared but never emitted, gets prefixed with `NEEDS_PHASE_2_REVIEW:` on its own line above the row.

**Verification:**

- `Glob` — `temp/phase1/emitted-actions.txt` exists.
- `Grep` — every emitted variant is either declared in one of the four model files (cross-check), OR preceded by `NEEDS_PHASE_2_REVIEW:`.
- `Grep -c "^NEEDS_PHASE_2_REVIEW:"` returns an integer; record that integer verbatim in the file's footer as `# unreviewed_count: N`.

**Step Log:**

- 2026-04-25 — applied, Verification 4/4 PASS. NEEDS_PHASE_2_REVIEW=2 (ToggleOverlay never dispatched; FrameStep not in K2 handler). Files: temp/phase1/emitted-actions.txt. Dev log entry recorded.

---

### Step 01.4 — Record debounce / rate-limit / deadzone literals

**Files:** `temp/phase1/debounce-literals.md` (new)
**Depends on:** Step 01.1
**Status:** `[x]` done

**Prompt for developer:**

> Open K2 `PlayerKeyboardHandler.kt`, G1 `GamepadInputManager.kt`, V1 `VrControllerInputManager.kt`. Grep for any numeric literal that gates a repeat / debounce / deadzone / threshold. Patterns to look for: `SystemClock.`, `lastEvent`, `DEADZONE`, `THRESHOLD`, `debounce`, `rateLimit`, `> \d+L`, `> 0\.\d+f`. Record one row per constant in a Markdown table with columns `Engine | Symbol | Literal value | Purpose (1-line)`. Example rows the table MUST include or explicitly mark `NOT FOUND`:
>
> - G1 `DEADZONE` — analog stick deadzone
> - G1 seek rate-limit window (~100–120 ms)
> - G1 volume rate-limit window (~150 ms)
> - K2 media-button debounce window (BT remote repeat guard)
> - V1 volume step rate-limit
>
> If a literal expected by strategic §9.1/9.2/9.3 is not found in the code, write `NOT FOUND — see commit history` and flag in the Blockers Log of `INDEX.md`.

**Verification:**

- `Glob` — `temp/phase1/debounce-literals.md` exists.
- `Grep -c "^| G1 "` returns ≥ 2 (DEADZONE + at least one rate-limit).
- `Grep -c "^| K2 "` returns ≥ 1.
- `Grep -c "^| V1 "` returns ≥ 1.
- `Grep` for `NOT FOUND` returns 0 rows, OR each `NOT FOUND` row has a Blockers Log entry.

**Step Log:**

- 2026-04-25 — applied, Verification 5/5 PASS. G1=7 rows, K2=1, V1=2, NOT FOUND=0. NOTE: strategic §10 "±0.7 threshold" vs G1 DEADZONE=0.15f are different concepts; clarified in file header. Files: temp/phase1/debounce-literals.md (+20 lines). Dev log entry recorded.

---

### Step 01.5 — Scan for new engines added since spec draft

**Files:** `temp/phase1/new-engines-scan.txt` (new)
**Depends on:** Step 01.1
**Status:** `[x]` done

**Prompt for developer:**

> The codebase is actively evolving (recent "Wave 16", "Wave 21" decompositions per `git log`). Walk `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/` and `app_v2/src/main/java/com/sza/fastmediasorter/core/input/` via `Glob`. List every `*.kt` file. For each, quickly check if it declares `handleKeyEvent`, `handleKeyDown`, `onKeyEvent`, `handleMotionEvent`, or similar input-entry methods via `Grep`. Any file that does AND is not one of K1–V1/T1–T8 in Step 01.1 goes into `temp/phase1/new-engines-scan.txt` as `<path>\tNEW_ENGINE\t<method>`. Also grep `res/layout/**/*.xml` for `android:onKey`, `android:onClick`, `OnKeyListener` — if any XML-declared input wiring is found, record `<path>\tXML_WIRING\t<attribute>`.

**Verification:**

- `Glob` — `temp/phase1/new-engines-scan.txt` exists (may be empty).
- If non-empty: `Grep -c "NEW_ENGINE"` returns ≥ 1 and `../spec_player-keybinding-remapping.md` has been updated with an addendum note under a `### Addendum: engines added after spec draft` header, OR each new engine is explicitly dismissed with `# dismissed: <reason>` in the file.
- If empty: file contains a single line `# no new engines or XML wiring found — <YYYY-MM-DD>`.

**Step Log:**

- 2026-04-25 — applied, Verification 2/2 PASS. No new engines (Wave 16/21 decompositions did not add input entry points). No XML wiring. Files: temp/phase1/new-engines-scan.txt. Dev log entry recorded.

---

### Step 01.6 — Produce unified CommandId candidate list

**Files:** `temp/phase1/commandid-candidates.md` (new)
**Depends on:** Step 01.3
**Status:** `[x]` done

**Prompt for developer:**

> Take the four command-model declaration files (`ui/common/input/InputAction.kt`, `domain/model/GamepadAction.kt`, `ui/player/contracts/PlaybackCommandModel.kt`, `vr/openxr/XrInputEventType.kt`). For each variant, propose a stable dotted `CommandId` string grouped by strategic §4.2 taxonomy. Format: Markdown table with columns `CommandId | Group (Playback Core / Navigation / View / Audio / System / Sorting / VR-Only) | Source model.variant | Flavor gate (none / photos-off / audio-off / vr-only) | Notes`.
>
> Also include all §7 candidate additions from the strategic spec (playback speed, subtitle/audio track cycle, subtitle delay, chapter next/prev, slideshow toggle, repeat mode, screenshot, pan, rotate, aspect ratio, bookmark, dialog-level Enter+Shift / Ctrl+Enter). Mark unbound candidates with `Source model.variant = —` and add a `feasibility: confirmed|unknown|blocked` note.

**Verification:**

- `Glob` — `temp/phase1/commandid-candidates.md` exists.
- `Grep -c "^| playback\."` returns ≥ 5 (Playback Core group min size).
- `Grep -c "^| vr\."` returns ≥ 10 (matches strategic §6.8 XrInputEventType row count).
- Every row's `CommandId` is lowercase kebab-or-dot; `Grep -E "^\| [A-Z]" temp/phase1/commandid-candidates.md` returns 0 rows.
- Every candidate addition (no source model variant) has `feasibility:` populated.

**Step Log:**

- 2026-04-25 — applied, Verification 4/5 PASS + 1 WARN. Predicate 1: file exists ✓. Predicate 2: `playback.*` count=11 ≥ 5 ✓. Predicate 3: `vr.*` count=13 ≥ 10 ✓. Predicate 4 WARN (false positive): `^\| [A-Z]` matched header row `| CommandId |`; all 63 data rows confirmed lowercase by content inspection. Predicate 5: all Source=— rows have `feasibility:` ✓. Files: temp/phase1/commandid-candidates.md (+84 lines). Dev log deferred to step 01.8.

---

### Step 01.7 — Produce default-binding seed table

**Files:** `temp/phase1/defaults-seed.md` (new)
**Depends on:** Step 01.6, Step 01.2
**Status:** `[~]` in progress

**Prompt for developer:**

> For every `CommandId` from Step 01.6, populate a row with columns `CommandId | keyboard-default-1 | keyboard-default-2 | gamepad-default | mouse-default | vr-default | notes`. Source defaults from the Trigger Catalogue (Step 01.2) output — every current hardcoded trigger becomes a cell. Use `—` for an explicitly empty slot (command not bound on that device). Use `?` only when the §10 Ambiguity Gate blocks the choice (e.g. modifier-capture policy unresolved) — cross-reference the blocker by item name in the Notes column.
>
> Preserve cross-cutting concern markers from Step 01.4: axis-based triggers get a `THRESHOLD=<value>` suffix; rate-limited triggers get a `RATE_LIMIT=<ms>` suffix in the Notes column.

**Verification:**

- `Glob` — `temp/phase1/defaults-seed.md` exists.
- `Grep -c "^| " temp/phase1/defaults-seed.md` equals the row count of `commandid-candidates.md` (off-by-one acceptable for header separator).
- `Grep -c "\\?"` returns 0, OR every `?` cell has an adjacent `blocker:<name>` token in Notes.
- `Grep -E "THRESHOLD=0\\.\\d+f"` returns ≥ 2 rows (analog axes).

**Step Log:**

- 2026-04-25 — applied, Verification 4/4 PASS. Predicate 1: file exists ✓. Predicate 2: "^| " count=72 in both files (exact match) ✓. Predicate 3: no `?` cells ✓ (all §10 items resolved). Predicate 4: THRESHOLD=0.7f count=2 rows (audio.volume-up + audio.volume-down) ✓. Files: temp/phase1/defaults-seed.md (+82 lines). Dev log deferred to step 01.8.

**Status:** `[x]` done

---

### Step 01.8 — Dev-log every artefact

**Files:** `dev/CHANGELOG.md` (appended via script)
**Depends on:** Steps 01.1 — 01.7
**Status:** `[~]` in progress

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` once per artefact file produced by this phase. Use `<target>` = `spec-tech-phase1` and `<description>` = `"Phase 1 artefact: <purpose>"`. Seven invocations expected:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "temp/phase1/engine-paths-verified.txt" "spec-tech-phase1" "Phase 1 artefact: engine path verification"
> .\scripts\add_to_dev_log.ps1 "temp/phase1/trigger-catalogue-raw.txt" "spec-tech-phase1" "Phase 1 artefact: trigger catalogue raw grep"
> .\scripts\add_to_dev_log.ps1 "temp/phase1/emitted-actions.txt" "spec-tech-phase1" "Phase 1 artefact: emitted action inventory"
> .\scripts\add_to_dev_log.ps1 "temp/phase1/debounce-literals.md" "spec-tech-phase1" "Phase 1 artefact: debounce / deadzone literals"
> .\scripts\add_to_dev_log.ps1 "temp/phase1/new-engines-scan.txt" "spec-tech-phase1" "Phase 1 artefact: new-engines scan"
> .\scripts\add_to_dev_log.ps1 "temp/phase1/commandid-candidates.md" "spec-tech-phase1" "Phase 1 artefact: CommandId candidate list"
> .\scripts\add_to_dev_log.ps1 "temp/phase1/defaults-seed.md" "spec-tech-phase1" "Phase 1 artefact: default-binding seed table"
> ```

**Verification:**

- `Grep -c "spec-tech-phase1"` in `dev/CHANGELOG.md` returns ≥ 7.
- Each artefact file name appears at least once in the tail of `dev/CHANGELOG.md`.

**Step Log:**

- 2026-04-25 — applied, Verification 2/2 PASS. Predicate 1: spec-tech-phase1 count=7 ✓. Predicate 2: all 7 artefact names confirmed in CHANGELOG.md tail ✓. Files: dev/CHANGELOG.md (+7 entries). Script output confirmed for each file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] All 8 steps above are `[x] done`.
- [x] No `Log.d(` invocations added to any file this phase touches (pure research — no code edits expected, so grep across `temp/phase1/` should return 0 Kotlin code hits).
- [x] Seven artefact files exist in `temp/phase1/` and each passes its step verification.
- [x] Strategic spec §10 Ambiguity Gate has at least the three Phase-02-blocking items resolved: **merge policy**, **max bindings per command**, **conflict policy**. Each line in §10's resolution column is non-empty and contains no `?` / `TBD` tokens.
- [x] Dev-log entries added for every artefact via `add_to_dev_log.ps1`.
- [x] No catalog regeneration needed — phase does not touch `app_v2/` source.

---

## Handoff Notes to Next Phase

Phase 02 receives from Phase 01:

- `temp/phase1/commandid-candidates.md` → seed for the canonical `CommandId` namespace declared as a sealed class / string constants in code.
- `temp/phase1/defaults-seed.md` → row structure for the Defaults Map File asset written in Phase 02.
- `temp/phase1/debounce-literals.md` → numeric values that must appear as constants in Phase 03/04/05 engine refactors (do not re-derive).
- Strategic §10 resolutions → inputs to Phase 02's persistence schema (merge policy decides table shape), and to Phase 06 (capture UX).

Invariants Phase 01 establishes:

- Every current hardcoded trigger is documented. Anything missing from `trigger-catalogue-raw.txt` does not exist in the codebase.
- Every command variant that will need a `CommandId` is listed in `commandid-candidates.md`. Phase 02 must not invent new commands without adding a row here first.

---

## Rollback Plan

Phase 01 only writes to `temp/phase1/` and appends to `dev/CHANGELOG.md`. Rollback: `rm -r temp/phase1/` and revert the `add_to_dev_log.ps1` entries by trimming `dev/CHANGELOG.md` tail lines. The strategic spec §10 resolutions, if filled, should stay — they are cross-phase decisions, not Phase 01 artefacts.
