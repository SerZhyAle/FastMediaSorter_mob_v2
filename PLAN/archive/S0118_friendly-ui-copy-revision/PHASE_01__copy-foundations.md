# Phase 01 - Copy Foundations

**Strategic spec:** [`../S0118_friendly-ui-copy-revision.md`](../S0118_friendly-ui-copy-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Introduce shared copy primitives and resource scaffolding so later phases can rewrite screens without inventing new message rules per surface.

---

## Prerequisites

- [ ] Strategic §6 has no open blockers.
- [ ] Working tree is clean or on a feature branch.
- [ ] No parallel feature branch is editing the same string keys.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiMessageFamily.kt` | New | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiNextStep.kt` | New | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiMessageSpec.kt` | New | <= 220 |
| `app_v2/src/main/res/values/strings.xml` | Modified | <= 180 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | <= 180 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | <= 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/copy/UiMessageSpecTest.kt` | New | <= 220 |

> String catalogs already exceed 500 lines. Create timestamped backups in `temp/` before editing them.

---

## Steps

### Step 01.1 - Add the message family model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiMessageFamily.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiNextStep.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a small shared model describing the allowed user-facing message families and the single contextual next-step types supported by S0118. Keep the model UI-only and dependency-free so formatters, presenters, and settings helpers can reuse it.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiMessageFamily.kt` exists.
- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiNextStep.kt` exists.
- `Grep` - `enum class UiMessageFamily` matches exactly once.
- `Grep` - `sealed interface UiNextStep` or `sealed class UiNextStep` matches exactly once.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: UiMessageFamily.kt (+18 LOC), UiNextStep.kt (+39 LOC). Dev log recorded.

---

### Step 01.2 - Add the shared message spec contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiMessageSpec.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a shared `UiMessageSpec` contract that carries one short user-facing message, optional detailed text, and at most one contextual next step. Do not perform rendering here; this phase only defines the payload shape and invariants used by later phases.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiMessageSpec.kt` exists.
- `Grep` - `data class UiMessageSpec` matches exactly once.
- `Grep` - `val nextStep:` present.
- `Grep` - `val detailedMessage:` or `val details:` present.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: UiMessageSpec.kt (+76 LOC). Dev log recorded.

---

### Step 01.3 - Seed localized baseline keys

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add the shared S0118 baseline keys used by later phases for short errors, detailed follow-up labels, empty-state prompts, and the three contextual next steps. Use one stable prefix for the new key family and add EN, RU, and UK values in the same change.

**Verification:**

- `Grep` - `friendly_copy_` returns at least one hit in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `friendly_copy_` returns at least one hit in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `friendly_copy_` returns at least one hit in `app_v2/src/main/res/values-uk/strings.xml`.
- `Grep` - `friendly_copy_next_step_help` present in all three locale files.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: strings.xml EN/RU/UK (+13 keys each). Dev log recorded.

---

### Step 01.4 - Lock the invariants with unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/copy/UiMessageSpecTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add unit tests for the copy contract. Cover the required short-message presence, the single-next-step rule, and at least one success and one error payload example so later formatter refactors have a stable target.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/copy/UiMessageSpecTest.kt` exists.
- `Grep` - `class UiMessageSpecTest` matches exactly once.
- `Grep` - `short message` or `next step` appears in a test method name.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: UiMessageSpecTest.kt (+105 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - BUILD SUCCESSFUL in 48s.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` regenerated after the new `.kt` files are added.

---

## Handoff Notes to Next Phase

Shared copy payloads exist, baseline keys are present in all three locales, and later phases can project messages without inventing new family types.

---

## Rollback Plan

Revert the Phase 01 commit(s) and remove the new `ui/common/copy` files. No data migration or persisted state change is introduced here.