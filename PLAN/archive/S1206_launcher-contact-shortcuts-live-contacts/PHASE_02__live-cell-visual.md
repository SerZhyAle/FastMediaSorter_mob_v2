# Phase 02 - Live cell visual

**Strategic spec:** [`../S1206_launcher-contact-shortcuts-live-contacts.md`](../S1206_launcher-contact-shortcuts-live-contacts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Make a contact cell resolve its caption and picture from the address book on every display, falling back to
the stored snapshot whenever the live read cannot answer.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherDesktopUseCase.kt` | Modified | ≤ 90 |

> Neither file exceeds 500 LOC today (327 and 62), so no backup step is required. Both stay well under the
> 1500 LOC limit.
>
> **No UI or layout file is touched.** The photo occupies the `cellIcon` slot that
> `LauncherCellViewBinder.bindMonogram` already shows whenever `monogramSeed` is null, so the rendering
> change is expressed entirely as a different `LauncherCommandVisual`. Consequently there is no
> `res/layout-land` counterpart question in this phase.

---

## Steps

### Step 02.1 - Resolve the live name and photo in `contactVisual`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `LiveContactDataSource` into `ResolveLauncherCommandLabelUseCase` and make `contactVisual` a
> `suspend` function that first calls `read(target.lookupKey)`.
>
> With a live answer, build the label from the live `displayName`, keeping the existing
> `displayName -> phoneNumber -> launcher_contact_cell_unnamed` fallback chain for a name-less record. When
> the live answer also carries a `photoUri`, decode it to a `Drawable` through
> `androidx.core.graphics.drawable.RoundedBitmapDrawableFactory` with `isCircular = true` - it ships in
> `androidx.core:core-ktx`, already on the classpath, and is not used anywhere in the project yet - set it
> as `iconDrawable`, set `iconKey` to a
> string combining the lookup key and the photo URI, and set `monogramSeed` to null.
>
> With no live answer, or with a live answer carrying no photo, return exactly what the function returns
> today - the snapshot label and `monogramSeed`, no `iconDrawable`.
>
> Decode failures degrade to the monogram rather than propagating. Update the function's KDoc: the current
> text states the snapshot IS the answer and cites S1176 ADR-1, which this ticket supersedes.

**Why:**

Strategic §1 lists a stale caption after a rename and a missing photograph as the two limitations the
permission removes, and §3.3 scopes the first iteration to exactly "живые имя и фотография контакта на
каждый показ ячейки". The circular shape is not a new decision: the monogram it replaces is already a disc
(S1176), and the two share the same 44dp box, so a square photo would make a contact cell change shape
depending on whether the person has a picture. Setting `monogramSeed` to null is what actually reveals the
photo - `LauncherCellViewBinder.bindMonogram` hides `cellIcon` whenever a seed is present.

**Verification:**

- `Grep` - `private suspend fun contactVisual` matches exactly once.
- `Grep` - `liveContactDataSource` present in the constructor parameter list.
- `Grep` - `RoundedBitmapDrawableFactory` present.
- `Grep` - `isCircular` present.
- `Grep` - `iconKey =` present inside the contact branch.
- `Grep` - `ADR-1` returns zero hits in this file.
- `Grep` - `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

---

### Step 02.2 - Re-resolve the desktop when the address book changes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherDesktopUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `LiveContactDataSource` and add its `changes()` flow as a fourth input to the existing `combine`
> in `invoke`, discarding the emitted value and keeping the existing `RadioStates` mapping unchanged. Add a
> KDoc sentence naming this ticket and the reason, in the same shape as the existing S1441 sentence above it.

**Why:**

Strategic §3.4 records that re-rendering has to be driven through this `combine` "тем же приёмом, каким
S1441 завёл туда состояние радио", because the stored cell list re-emits only on a database write and a
contact renamed in the system produces no such write.

**Verification:**

- `Grep` - `changes()` present in this file.
- `Grep` - `combine(` still matches exactly once.
- Read the file - the `combine` has four source flows and the lambda takes four parameters.

**Status:** `[x]` done

---

### Step 02.3 - Cover the fallback chain with unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCaseContactTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a unit test class for the contact branch with a faked `LiveContactDataSource`, asserting four cases:
> a live name overrides a stale snapshot name; a null live answer keeps the snapshot label and
> `monogramSeed`; a live answer with a photo sets `iconDrawable` with a non-null `iconKey` and a null
> `monogramSeed`; a blank lookup key never reaches the data source. Stub the remaining constructor
> dependencies - no test for this class exists yet, so there is no fixture to reuse.

**Why:**

Strategic §3.3 makes the stored snapshot the fallback whenever the contact is deleted or the read fails,
and that branch is invisible on a device with a healthy address book, so only a test distinguishes "the
fallback works" from "the fallback was never exercised".

**Verification:**

- `Glob` - the test file exists.
- `.\a.ps1 fu` - the four new tests pass.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, `Fast check passed` (2026-08-08).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added - batched for the whole ticket at Phase 05 closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The photo decode runs inside the resolver's existing `Dispatchers.IO` block; `iconKey` is derived from the lookup key and photo URI, so the binder's identity guard sees a stable value; a burst of address-book events collapses on the combine's slower downstream rather than queueing re-resolutions.

**Step Log**

- Step 02.1 added a call-site guard skipping the read entirely for a blank lookup key. The data source already refuses one, but a DIAL or SMS cell carries no lookup key at all, so the guard is what makes "never reaches the address book" true rather than merely harmless - and it is what Step 02.3's fourth test asserts.

---

## Handoff Notes to Next Phase

A contact cell now shows live data whenever `READ_CONTACTS` happens to be granted - through Settings >
Permissions, which S1335 already ships. Phase 04 adds the request at pin time; nothing in Phase 04 is
required for this phase to be correct.

---

## Rollback Plan

Revert phase commit(s) - the contact branch returns to the snapshot-only visual. No data migration and no
stored state changed.
