# Phase 01 - What is already stored on the device

**Strategic spec:** [`../S1703_bugfix-paddleocr-postprocess-returns-empty.md`](../S1703_bugfix-paddleocr-postprocess-returns-empty.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-17
**Completed:** 2026-08-17

---

## Objective

A device or a backup that says `PADDLE` keeps working after the word means nothing, and says so in the log
rather than silently.

---

## Prerequisites

- [ ] Understood: `ocrEngineType` is a String, so this is a normalisation of a value, not a schema migration.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/TextRecognitionSettingsStore.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt` | Modified | ≤ 40 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/settings/RetiredOcrEngineValueTest.kt` | New | ≤ 140 |

---

## Steps

### Step 01.1 - Normalise the retired value on the way in

**Files:** `.../settings/TextRecognitionSettingsStore.kt`, `.../usecase/ImportSettingsUseCase.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> Both the settings store's read path and the settings import path map a stored `PADDLE` to the default
> engine before it reaches the rest of the app, and write the normalised value back so the next read finds
> nothing to fix. Log the substitution once per occurrence at info level, naming the retired value and what
> replaced it - a user asking "why did my engine setting change" deserves an answer in the log. Do not put
> the retired name anywhere else: this is the one place that still knows the word.

**Why:**

Strategic §3 withdraws the engine, and a stored setting outlives the code that understood it; without this,
a device that had chosen PaddleOCR selects an engine that no longer exists, which is the same silent
fallback the ticket exists to end.

**Verification:**

- `Grep` - both files map the retired value to the default.
- `Grep` - the substitution is logged at info level, without a ticket id in the message.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - TextRecognitionSettingsStore.normaliseEngine maps any non-default stored engine to TESSERACT and logs the substitution at info level without a ticket id; the settings read path and ImportSettingsUseCase both go through it, so a device and an old backup carrying the withdrawn engine keep working. RetiredOcrEngineValueTest 5 tests 0 failures; a.ps1 fk and a.ps1 fu exit 0.
- 2026-08-17 - S1723 verification: re-ticking an already-done step must leave the file consistent.

---

### Step 01.2 - Cover the compatibility

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/settings/RetiredOcrEngineValueTest.kt`

**Depends on:** Step 01.1

**Prompt for developer:**

> Cover: a stored `PADDLE` reads back as the default; a stored `TESSERACT` is untouched; an unknown value
> reads back as the default; an imported backup carrying `PADDLE` lands as the default; and a second read
> finds the value already normalised.

**Why:**

Strategic §4 asks for verification, and this is the half of the withdrawal that touches data a user already
has - the only part where a mistake reaches somebody who never chose the engine.

**Verification:**

- `Grep` - all five cases present.
- `.\a.ps1 fu` - passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - TextRecognitionSettingsStore.normaliseEngine maps any non-default stored engine to TESSERACT and logs the substitution at info level without a ticket id; the settings read path and ImportSettingsUseCase both go through it, so a device and an old backup carrying the withdrawn engine keep working. RetiredOcrEngineValueTest 5 tests 0 failures; a.ps1 fk and a.ps1 fu exit 0.
- 2026-08-17 - Restored after the S1723 verification round-trip: this step was and is done - RetiredOcrEngineValueTest 5 tests 0 failures, a.ps1 fu exit 0.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `.\a.ps1 fu` passes.
- [ ] Dev log entry added for every file in Files Touched.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Nothing is deleted yet, but a stored `PADDLE` no longer selects anything. The removal can now proceed
without breaking a device that had it.

---

## Rollback Plan

Revert both files and delete the test; the value simply stops being normalised.
