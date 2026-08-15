# Phase 01 - Geographic Command

**Strategic spec:** [`../S1175_launcher-google-maps-integration.md`](../S1175_launcher-google-maps-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phases 02, 03, and 04
**Steps done:** 2 / 2

## Objective

Persist and execute a geographic launcher command for route, immediate navigation, and place display.

## Steps

### Step 01.1 - Add geographic command codec

**Files:** `domain/model/launcher/LauncherCellCommand.kt`, `src/test/.../LauncherCellCommandTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a percent-encoded multi-field geographic command containing a target and action enum. Decode malformed payloads to null and cover encoding, decoding, and invalid records.

**Why:** Strategic ADR-4 requires a command kind that carries geographic data without a Room migration.

**Verification:**

- Geographic command codec tests pass.

**Status:** `[x]` done

### Step 01.2 - Execute and render geographic commands

**Files:** `domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt`, `domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`, launcher drawable and strings resources
**Depends on:** Step 01.1

**Prompt for developer:**

> Build documented Maps direction and navigation intents, use a generic geo intent for place display, and give each action a distinct accessible visual.

**Why:** Strategic criteria 1 and 2 require a route action, immediate navigation action, and a visually distinct place fallback.

**Verification:**

- `LauncherCellCommand.Geographic` is handled exhaustively by both launcher command use cases.

**Status:** `[x]` done

## Phase Done Criteria

- [x] All steps are `[x] done`.
- [x] Targeted codec tests pass.
- [x] `a.ps1 fk` passes.
