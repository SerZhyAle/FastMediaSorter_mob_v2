# Phase 03 - Bound spawns and device safety

**Strategic spec:** [`../S1341_agent-model-routing-tiers.md`](../S1341_agent-model-routing-tiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Cap the two new Sonnet-tier agents' spawn cost (turn budget, structured report, no-gradle) per strategic §4.3, and make device work "run a known procedure" rather than "sometimes needs a smart recovery" per §4.4 by removing the last hardcoded adb path and routing through the existing auto-discovery.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - `android-device-operator.md` and `repo-mechanic.md` must exist (Phase 02).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/agents/android-device-operator.md` | Modified | +5-8 lines |
| `.claude/agents/repo-mechanic.md` | Modified | +5-8 lines |
| `scripts/devtest/lib/find-adb.ps1` | New | ≤ 20 |
| `scripts/devtest/adb.ps1` | Modified | dot-source instead of inline function |
| `.claude/commands/spec-prerelease.md` | Modified | dot-source + call instead of hardcoded path |

---

## Steps

### Step 03.1 - Add turn budget and structured report contract to `android-device-operator`

**Files:** `.claude/agents/android-device-operator.md`
**Depends on:** - start of phase (after Phase 02 lands)

**Prompt for developer:**

> Add to the body: an explicit turn/step budget for a single spawn (state a concrete small number, e.g. "stop and report after 10 tool calls if the task is not done - hand back to the caller rather than looping"), a structured report contract (fixed fields: what was done, what was observed, screenshot/log paths if any - not free prose), and an explicit "never run gradle, `.\a.ps1`, or any build command - hand build/verification needs back to the caller" line (per strategic §4.3, gradle-waiting spawns are pure loss).

**Verification:**

- `Grep "turn budget\|step budget\|stop and report after" .claude/agents/android-device-operator.md -i` matches.
- `Grep "never run gradle\|no gradle\|forbid.*gradle" .claude/agents/android-device-operator.md -i` matches.
- `Grep "structured report\|fixed fields" .claude/agents/android-device-operator.md -i` matches.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. Content already written during Phase 02 step 02.3 (authored with this requirement anticipated) - confirmed present: turn-budget line 15, no-gradle line 14, "Structured report contract" heading line 19. No additional edit needed this step; predicates re-verified live. Dev log: none new (no file changed in this step).

### Step 03.2 - Add turn budget and structured report contract to `repo-mechanic`

**Files:** `.claude/agents/repo-mechanic.md`
**Depends on:** - start of phase (after Phase 02 lands)

**Prompt for developer:**

> Same three additions as 03.1, adapted to this agent's scope: a turn/step budget, a structured report contract (which script ran, its verdict/exit code, verbatim key output lines - not an interpretation), and the explicit "never run gradle directly - only through the closure facade / gate scripts that already acquire BUILD.LOCK" line (this agent runs `post-change.ps1` and `assert-*` gates, which already handle locking - it must not additionally invoke raw `gradlew`).

**Verification:**

- `Grep "turn budget\|step budget\|stop and report after" .claude/agents/repo-mechanic.md -i` matches.
- `Grep "never run gradle\|no gradle\|forbid.*gradle\|BUILD.LOCK" .claude/agents/repo-mechanic.md -i` matches.
- `Grep "structured report\|verbatim" .claude/agents/repo-mechanic.md -i` matches.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. Content already written during Phase 02 step 02.4 (authored with this requirement anticipated) - confirmed present: turn-budget line 14, BUILD.LOCK/no-raw-gradle line 13, "verbatim" in report contract line 25. No additional edit needed this step; predicates re-verified live. Dev log: none new (no file changed in this step).

### Step 03.3 - Extract `Find-Adb` into a shared lib and remove the hardcoded path

**Files:** `scripts/devtest/lib/find-adb.ps1`, `scripts/devtest/adb.ps1`, `.claude/commands/spec-prerelease.md`
**Depends on:** Step 03.1, Step 03.2 (sequential within phase - same review pass, no functional dependency)

**Prompt for developer:**

> `scripts/devtest/adb.ps1` defines `function Find-Adb { .. }` inline (currently lines 147-161: checks `ANDROID_HOME`/`ANDROID_SDK_ROOT`, then `PATH`, then falls back to the hardcoded `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`). `.claude/commands/spec-prerelease.md:109` hardcodes that same fallback path directly, bypassing all the smarter discovery tiers. Extract `Find-Adb` verbatim into a new file `scripts/devtest/lib/find-adb.ps1` (a dot-sourceable script exposing the `Find-Adb` function, no other content). Update `scripts/devtest/adb.ps1` to dot-source it (`. "$PSScriptRoot/lib/find-adb.ps1"`) instead of defining the function inline, then call `Find-Adb` as before - behavior must be identical. Update `spec-prerelease.md`'s step 1.0 PowerShell block to dot-source the same lib and call `Find-Adb` to populate `$adb`, instead of the hardcoded literal - keep the rest of that step's phantom-device-cleanup sequence (`emu kill`, `disconnect`) unchanged, since `adb.ps1`'s own verbs do not cover that specific multi-emulator-cleanup case.

**Verification:**

- `Glob scripts/devtest/lib/find-adb.ps1` matches (file exists).
- `Grep "function Find-Adb" scripts/devtest/lib/find-adb.ps1` matches.
- `Grep "function Find-Adb" scripts/devtest/adb.ps1` returns zero matches (inline definition removed).
- `Grep "lib/find-adb" scripts/devtest/adb.ps1` matches (dot-source added).
- `Grep "LOCALAPPDATA\\\\Android\\\\Sdk\\\\platform-tools\\\\adb.exe" .claude/commands/spec-prerelease.md` returns zero matches (hardcoded literal removed).
- `Grep "find-adb" .claude/commands/spec-prerelease.md -i` matches (dot-source added).
- `pwsh -NoProfile -File scripts/devtest/adb.ps1 -Verb devices` still exits 0 or a documented non-zero (1/2/3) - not a script-load error - confirming the refactor didn't break the script (run once, read exit code and first output line).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 7/7 PASS. `find-adb.ps1` created (29 lines) with `Find-Adb` function verbatim. `adb.ps1` inline function removed, dot-source added (line 149). `spec-prerelease.md:109` hardcoded literal removed, dot-source + `Find-Adb` call added. Smoke-run: `adb.ps1 -Verb devices` -> exit 0, "OK 1 device(s) online" (emulator-5554, Android 15) - refactor confirmed behavior-preserving on a live device. Files: `scripts/devtest/lib/find-adb.ps1` (new), `scripts/devtest/adb.ps1`, `.claude/commands/spec-prerelease.md`. Dev log recorded via post-change.ps1.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - skip. No Kotlin/build-graph file touched.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (4 entries: 03.3 extraction, cheatsheet regen, spec-prerelease.md).
- [x] If public API changed: skip - no app source touched; `scripts/devtest/adb.ps1`'s CLI surface (verbs/params) is unchanged, only its internal implementation moved.
- [x] Phase-boundary audit run - not applicable in the Kotlin-audit sense; confirmed `scripts/devtest/adb.ps1 -Verb devices` smoke-run passed live (exit 0, device listed) - not just the text-only greps.

---

## Handoff Notes to Next Phase

Phase 04 (final) verifies the full strategic §7 acceptance list and runs the document-registry closing calls.

---

## Rollback Plan

Revert phase commit(s). The `find-adb.ps1` extraction is behavior-preserving (same logic, new location) - low risk. If `adb.ps1`'s smoke-run predicate fails, revert just step 03.3's three files rather than the whole phase.
