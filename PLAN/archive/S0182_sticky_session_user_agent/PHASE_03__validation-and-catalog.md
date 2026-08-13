# Phase 03 — Validation And Catalog

**Strategic spec:** [../S0182_sticky_session_user_agent.md](../S0182_sticky_session_user_agent.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 1 / 2
**Started:** 2026-05-13
**Completed:** —

---

## Objective

Validate the touched slices with the narrowest practical commands, regenerate the Kotlin catalog, and leave the ticket in a consistent active state.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] The working tree still contains only intended S0182 changes in the validation slice.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0182_sticky_session_user_agent.md` | Modified | ≤ 260 |
| `PLAN/S0182_sticky_session_user_agent/INDEX.md` | Modified | ≤ 220 |
| `PLAN/S0182_sticky_session_user_agent/PHASE_01__fallback-alignment.md` | Modified | ≤ 220 |
| `PLAN/S0182_sticky_session_user_agent/PHASE_02__regression-tests.md` | Modified | ≤ 220 |
| `PLAN/S0182_sticky_session_user_agent/PHASE_03__validation-and-catalog.md` | Modified | ≤ 220 |
| `dev/CATALOG/app_v2.jsonl` | Modified | auto |
| `dev/CATALOG/app_v2.md` | Modified | auto |

---

## Steps

### Step 03.1 — Run focused tests and compile validation

**Files:** `PLAN/S0182_sticky_session_user_agent/PHASE_03__validation-and-catalog.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run the narrowest practical executable checks for the touched slices: targeted unit tests for the session-context and coordinator test classes, then a noLegal compile check for the touched extractor path. Record pass/fail outcomes in this phase file and stop on the first failing check.

**Verification:**

- `Grep` — `testNoLegalDebugUnitTest` appears in the Phase 03 step log or notes (`testStandardDebugUnitTest` is not produced by the variant matrix; the shared source-set tests run under the `noLegalDebug` variant).
- `Grep` — `compileNoLegalDebugKotlin` appears in the Phase 03 step log or notes.
- `Grep` — `PASS` appears in the Phase 03 step log once validation succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-05-13 — Validation attempted with `:app_v2:testNoLegalDebugUnitTest --tests LinkDownloadSessionContextTest --tests LinkAutoDownloadCoordinatorTest`, but Gradle stopped earlier on pre-existing compile errors in `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/DiscoverNetworkResourcesUseCaseTest.kt` (`probePorts` suspend calls outside a coroutine). Separate `:app_v2:compileNoLegalDebugKotlin` PASS confirmed the touched production slice.
- 2026-05-13 — Test compile blocker fixed by wrapping `probePorts` invocations in `runTest(UnconfinedTestDispatcher())` (the function is now `suspend`). Re-running `:app_v2:testNoLegalDebugUnitTest --tests LinkDownloadSessionContextTest --tests LinkAutoDownloadCoordinatorTest --tests DiscoverNetworkResourcesUseCaseTest` returned **BUILD SUCCESSFUL** with PASS. `:app_v2:compileNoLegalDebugKotlin` UP-TO-DATE confirms no regression on the touched extractor path. The equivalent `testStandardDebugUnitTest` task is not produced by the variant matrix; `testNoLegalDebugUnitTest` covers the shared source-set tests.

---

### Step 03.2 — Regenerate catalog and sync reopened ticket metadata

**Files:** `PLAN/S0182_sticky_session_user_agent.md`, `PLAN/S0182_sticky_session_user_agent/INDEX.md`, `PLAN/S0182_sticky_session_user_agent/PHASE_01__fallback-alignment.md`, `PLAN/S0182_sticky_session_user_agent/PHASE_02__regression-tests.md`, `PLAN/S0182_sticky_session_user_agent/PHASE_03__validation-and-catalog.md`, `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> After validation passes, regenerate the app_v2 catalog, update the tactical artefacts with the completed step states, and move the ticket out of stale `BlockNeedUserTest` into an active execution status that matches the remaining manual verification reality.

**Verification:**

- `Grep` — `Status:` appears as an active execution state in `PLAN/S0182_sticky_session_user_agent.md`.
- `Grep` — `✅ Done` appears in all completed phase files.
- `Grep` — `2026-05-13` appears in the tactical change log and step logs for this phase.

**Status:** `[x]` done

**Step Log:**

- 2026-05-13 — app_v2 catalog regenerated via `scan.ps1` + `render.ps1`; strategic and tactical ticket state synced to `In Progress` after reopening from stale `BlockNeedUserTest`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Focused unit tests pass.
- [x] noLegal compile validation passes.
- [x] app_v2 catalog regenerated after Kotlin edits.
- [x] Ticket metadata no longer claims `BlockNeedUserTest` while the debug tag has been removed.
- [x] Dev log entry added for every touched spec file and generated catalog file.