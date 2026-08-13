# Phase 01 - parser-tests

**Strategic spec:** [`../S0582_streams-csv-parser-tests.md`](../S0582_streams-csv-parser-tests.md)  
**Tactical index:** [`INDEX.md`](INDEX.md)  
**Status:** ✅ Done  
**Depends on:** none - foundation phase  
**Blocks:** Phase 02  
**Steps done:** 1 / 1  
**Started:** 2026-06-21  
**Completed:** 2026-06-21  

---

## Objective

Create the `StreamCatalogCsvParserTest` class to verify RFC-4180 CSV parser logic, including quoted commas, escaped quotes, embedded newlines, line ending variations (CRLF/LF), missing or reordered fields, blank URL/name row skipping, and boolean flag parsing.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParserTest.kt` | New | ≤ 300 |

---

## Steps

### Step 01.1 - Create StreamCatalogCsvParserTest and implement unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParserTest.kt`  
**Depends on:** - start of phase  

**Prompt for developer:**

> Create the unit test class `StreamCatalogCsvParserTest` under `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/`.  
> Implement unit tests verifying:  
> 1. Basic CSV parsing with default headers and data.  
> 2. Quoted fields containing commas.  
> 3. Escaped quotes inside quoted fields.  
> 4. Embedded newlines inside quoted fields.  
> 5. LF vs CRLF record delimiters.  
> 6. Trailing newline behavior.  
> 7. Reordered headers mapping.  
> 8. Missing optional columns mapping to empty strings or default boolean flags.  
> 9. Skipping rows where `url` or `name` is blank.  
> 10. Ignored unknown extra columns.  
> 11. Trimming and case-insensitivity of header names and boolean values.  

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParserTest.kt` exists.  
- `Grep` - `class StreamCatalogCsvParserTest` matches exactly once in that file.  

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 01.1` above is `[x] done`.
- [x] Project compiles and the new unit tests pass: `testStandardDebugUnitTest --tests "*StreamCatalogCsvParserTest"` - 11 tests, 0 failures.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the new file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All CSV parser tests pass, demonstrating that the hand-rolled tokenizer and field mappings are correct and robust against changes.

---

## Rollback Plan

Revert the newly created test file.
