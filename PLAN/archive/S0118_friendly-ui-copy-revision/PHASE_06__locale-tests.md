# Phase 06 - Locale Tests

**Strategic spec:** [`../S0118_friendly-ui-copy-revision.md`](../S0118_friendly-ui-copy-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04, Phase 05
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Close locale parity gaps and add regression coverage so the friendly copy contract does not drift back to English fallbacks or hardcoded leaf strings.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Phase 05 is ✅ Done.
- [ ] All new S0118 string keys are present in EN.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | <= 160 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | <= 160 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | <= 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenterTest.kt` | Modified | <= 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/input/InputHelpRegistryTest.kt` | Modified | <= 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapperTest.kt` | New | <= 220 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/copy/FriendlyCopyParityTest.kt` | New | <= 220 |

---

## Steps

### Step 06.1 - Fix locale leakage and parity gaps

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Audit the touched user-facing keys for EN, RU, and UK parity and fix known leakage. Replace any English fallback text still living in RU or UK resources, including pre-existing cases discovered during S0118 research.

**Verification:**

- `Grep` - `An unexpected error occurred. Please try again.` returns zero hits in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `An unexpected error occurred. Please try again.` returns zero hits in `app_v2/src/main/res/values-uk/strings.xml`.
- `Grep` - every new S0118 key family hit exists in all three locale files.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Replaced English fallback in error_reason_unknown with localized RU/UK copy. friendly_copy_ key parity verified by check_strings_localized.ps1 (12/12 OK).

---

### Step 06.2 - Extend presenter and help tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenterTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/input/InputHelpRegistryTest.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Update the existing share and help tests to match the new copy wording and support-routing behavior. Keep the assertions behavioral: result branch, URI validity, and resource-backed message selection.

**Verification:**

- `Grep` - `class LinkAutoDownloadResultPresenterTest` still present.
- `Grep` - `class InputHelpRegistryTest` still present.
- `Grep` - at least one test method references the new support-routing or friendly-copy behavior in each file.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Added S0118 test methods referencing friendly-copy and support-routing in both presenter and help registry tests.

---

### Step 06.3 - Add mapper and parity regression tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapperTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/copy/FriendlyCopyParityTest.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Add targeted tests for the final user-facing message mapper and for locale parity on the shared S0118 key family. Keep the tests narrow: no UI rendering, only resource and mapping behavior.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapperTest.kt` exists.
- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/copy/FriendlyCopyParityTest.kt` exists.
- `Grep` - `class NetworkErrorMessageMapperTest` matches exactly once.
- `Grep` - `class FriendlyCopyParityTest` matches exactly once.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: NetworkErrorMessageMapperTest.kt (+34 LOC), FriendlyCopyParityTest.kt (+33 LOC).

---

### Step 06.4 - Run the residual copy drift checks

**Files:** touched test and resource files from this phase
**Depends on:** Step 06.3

**Prompt for developer:**

> Run the narrow grep checks used throughout S0118 one final time: English leakage in RU/UK, residual hardcoded production strings in target directories, and the presence of the shared copy key family in all locales. Fix any remaining drift before the cleanup phase.

**Verification:**

- `Grep` - residual English leakage in RU/UK touched keys returns zero hits.
- `Grep` - hardcoded production-visible strings in Phase 04 and 05 target directories remain at zero.
- `Grep` - shared S0118 key family present in EN, RU, and UK locale files.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. No "An unexpected error occurred" leakage in RU/UK; no Toast.makeText hardcoded literals in Phase 04/05 dirs; friendly_copy_ family verified by check_strings_localized.ps1.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` regenerated after any touched `.kt` files change.

---

## Handoff Notes to Next Phase

Locale parity is closed and regression coverage exists for the shared S0118 copy paths. The remaining work is documentation, catalog regeneration, and final audit closure.

---

## Rollback Plan

Revert the Phase 06 commit(s). Test and string parity work does not change runtime data or schema.