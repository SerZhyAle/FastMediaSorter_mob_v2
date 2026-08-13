# Phase 01 - Voluntary restart branch in the rules engine

**Strategic spec:** [`../S1359_minigame-restart-level-command.md`](../S1359_minigame-restart-level-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Give the engine a public way to restart a level that is still being played, at exactly the price of dying, and prove the price with a test.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/game/GameRulesEngine.kt` | Modified | ≤ 340 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/game/GameRulesEngineTest.kt` | Modified | ≤ 400 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). Both files are under 500 LOC, so no backup sub-step is required.

---

## Steps

### Step 01.1 - Add `restartLevelVoluntarily` to the engine

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/game/GameRulesEngine.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a public `fun restartLevelVoluntarily(playingState: GameLevelState, restartedLevel: GameLevelState): GameLevelState` next to `restartLevel`. It opens with `require(playingState.status == GameStatus.PLAYING) { "current level is not being played" }` and returns `restartLevel(playingState.asGameOver(), restartedLevel)`. Leave `restartLevel` and its own `require` untouched. Add a comment naming S1359 and stating that routing through `asGameOver()` is what makes the voluntary price identical to dying.

**Why:**

Strategic §6 item 1 records that both the engine `require` and the ViewModel guard reject a live state, and strategic §5 forbids the UI faking `GAME_OVER` to get past them - the engine has to own the normalisation or its invariant becomes decorative.

**Verification:**

- `Grep` - `fun restartLevelVoluntarily` matches exactly once.
- `Grep` - `require(playingState.status == GameStatus.PLAYING)` present.
- `Grep` - `restartLevel(playingState.asGameOver(), restartedLevel)` present.
- `Grep` - `require(lostState.status == GameStatus.GAME_OVER)` still present exactly once - the original invariant was not weakened.
- `Grep` - `Log\.d\(` returns zero hits in `GameRulesEngine.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 5\5 PASS. Files: domain/game/GameRulesEngine.kt (+16 LOC). The original `require(lostState.status == GameStatus.GAME_OVER)` is still present exactly once - the invariant was not weakened to make room for the new branch.

---

### Step 01.2 - Prove the price equality with a test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/game/GameRulesEngineTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `@Test fun voluntaryRestartCostsTheSameAsDying()` modelled on the existing `restartLevelKeepsCurrentProgressWithPenalty`. Build one live state with `status = GameStatus.PLAYING` and `GameStats(score = 900, highScore = 900)`, and one lost state identical except `status = GameStatus.GAME_OVER`. Call `engine.restartLevelVoluntarily(live, restarted)` and `engine.restartLevel(lost, restarted)` and assert the two results carry the same `stats.score`, the same `stats.highScore`, the same `stats.survivalStreak`, and `GameStatus.PLAYING`. Add a second test asserting `restartLevelVoluntarily` throws `IllegalArgumentException` when handed a `GAME_OVER` state.

**Why:**

Strategic §2 goal 2 requires the voluntary restart to cost exactly what dying costs and §11 criterion 2 states it as an acceptance criterion; a unit test settles it without a device, leaving only the UI criteria to on-device verification.

**Verification:**

- `Grep` - `fun voluntaryRestartCostsTheSameAsDying` matches exactly once.
- `Grep` - `restartLevelVoluntarily` matches at least twice in the test file.
- `.\a.ps1 fu` - the two new tests pass (report the `GameRulesEngineTest` result line, not the whole suite).

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS. Ran the class only: `scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests com.sza.fastmediasorter.domain.game.GameRulesEngineTest`, exit 0. Result XML written 12:46:56 (this run, not a stale artifact): `tests="16" skipped="0" failures="0" errors="0"`, carrying `voluntaryRestartCostsTheSameAsDying` and `voluntaryRestartRejectsALostLevel`. §11 criterion 2 is now settled without a device.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - the targeted unit run compiled main and test source sets, exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1` - verdict `post-change: PASS`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same facade run.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Phase notes:**

- Audit: pure domain change. The new branch delegates to `restartLevel` instead of copying its body, so scoring stays single-sourced; no coroutine, listener or lifecycle surface is touched. `restartLevelVoluntarily` throws on a `GAME_OVER` state by design - the ViewModel guards on `PLAYING`, so no UI path can reach the throw, and a test pins that contract.
- No UI-phase screenshot gate: `Files Touched` names no layout, Activity, Fragment, `*View` or `ui/**` class.

---

## Handoff Notes to Next Phase

`GameRulesEngine.restartLevelVoluntarily(playingState, restartedLevel)` is the only supported way to restart a live level, and its price is test-pinned to the death path.

---

## Rollback Plan

Revert phase commit(s) - the new function is additive and nothing calls it yet.
