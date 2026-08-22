# Phase 01 - User Section Identity and Label

**Strategic spec:** [`../S1742_launcher-sections-manageable-entity.md`](../S1742_launcher-sections-manageable-entity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Make a section key that is not in the code catalogue a first-class citizen: give user sections their own key namespace, and make a header carrying such a key render its own name instead of "unavailable".

---

## Prerequisites

- [ ] Strategic spec is Approved or Tactical.
- [ ] research/01 read - in particular item 1, which is why this phase exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherSectionCatalog.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherDesktopUseCase.kt` | Modified | ≤ 40 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherDesktopUseCaseTest.kt` | Modified | ≤ 60 |

---

## Steps

### Step 01.1 - Give User Sections Their Own Key Namespace

**Files:** `LauncherSectionCatalog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add to `LauncherSectionCatalog` a constant prefix that marks a user-created section key, a function that mints a new key from a creation timestamp using that prefix, and a predicate that reports whether a given key is a user key. Do not change the six preset entries or `byKey`.

**Why:** Research 01 item 1 - a section's identity survives backup only as its `target` string, so the key must be self-sufficient, and it must be impossible for a minted key to collide with one of the six preset keys.

**Verification:**

- `Grep` - the prefix constant and the mint function are present in `LauncherSectionCatalog.kt`.
- `Grep` - no preset key literal in that file starts with the new prefix.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - USER_KEY_PREFIX/mintUserKey/isUserKey added; verified none of the six preset keys starts with the prefix.

---

### Step 01.2 - Render a Header Whose Key the Catalogue Does Not Know

**Files:** `ResolveLauncherDesktopUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the cell-to-UI mapping, when the command is a section and the resolver produced no visual, build the visual from the cell's own label override instead of leaving it null. A section with neither a resolvable key nor an override keeps today's behaviour.

**Why:** Research 01 item 1 measured that `labelOverride` is applied only inside the non-null branch, so a user section would draw "unavailable" no matter what the user named it. This is the single change that makes creating and renaming a section observable at all.

**Verification:**

- `Grep` - the section branch reads the label override when the resolved visual is absent.
- Unit test from step 01.3 passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - toUi falls back to the cell's own caption for a section command with no resolved visual; a.ps1 fk exit 0.

---

### Step 01.3 - Cover Both Section Label Paths With Tests

**Files:** `ResolveLauncherDesktopUseCaseTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add two cases: a section cell with a user key and a label override renders that override as its title; a section cell with a user key and no override still renders the unavailable placeholder. Keep the existing preset-section cases untouched.

**Why:** This phase's whole content is a rendering rule that no static gate can see - a wrong branch still compiles and still passes detekt, exactly as `LauncherDesktopRepositoryImplTest`'s own KDoc says of the placement rules.

**Verification:**

- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherDesktopUseCaseTest"` exits 0.
- The filtered result XML under `app_v2/build/test-results/testStandardDebugUnitTest-filtered/` lists both new case names and reports 0 failures.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - 4/4 tests PASS in the filtered run (fresh XML 12:44): a user section renders its caption, and one without a caption stays unresolved.
- 2026-08-18 - Phase closed: post-change PASS. Naming gate corrected so a test class is not counted as an architecture violation (baseline 377 -> 252).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles cleanly via `.\a.ps1 fk`.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

A minted user key now renders under its own name, so Phase 02 can create one and Phase 03 can rename one.

---

## Rollback Plan

Revert the phase commit(s) - no database migration changed.
