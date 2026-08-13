# S0582 - streams-csv-parser-tests

**Status:** Archived
**Priority:** 35
**Date:** 2026-06-21
**Tier:** 2 - Easy

## 0. Raw capture

Parked during S0580 research (out-of-scope finding).

`StreamCatalogCsvParser` (`app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParser.kt`) hand-rolls an RFC-4180 tokenizer (~70 lines: quoted commas, escaped `""`, embedded newlines, CRLF, trailing newline) with zero unit tests. The live catalog has 384 rows and multi-value cells - regressions in the tokenizer would silently corrupt category/topic/language import.

Add JVM unit tests for the tokenizer + header-by-name mapping: quoted field with comma, escaped quote, embedded newline, CRLF vs LF, trailing newline, missing optional columns, reordered columns, blank url/name rows skipped.

Independent of S0580 (which only reads the already-parsed `language` field).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

## Last Audit

- **Date:** 2026-06-21
- **Verdict:** Verified
- **Method:** `testStandardDebugUnitTest --tests "*StreamCatalogCsvParserTest"` - 11 tests, 0 skipped, 0 failures, 0 errors.

Coverage of `StreamCatalogCsvParser` (`app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParser.kt`) by `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParserTest.kt`:

- Basic header-by-name parsing of multiple rows.
- Quoted field containing a comma.
- Escaped doubled quote `""` inside a quoted field.
- Embedded newline (LF and CRLF) inside a quoted field.
- LF vs CRLF record delimiters yield identical results.
- Trailing newline (LF and CRLF) does not emit a phantom row.
- Reordered header columns mapped correctly.
- Missing optional columns decode to empty string / `false` booleans.
- Blank `url` or `name` rows (including whitespace-only) skipped.
- `is_live` / `https` boolean flag parsing - only `true` (case/whitespace-insensitive) is `true`.
- Unknown extra columns tolerated.

Every assertion was cross-checked against parser behaviour, including `cell()` trim semantics and `toBooleanFlag()` (exact `true` match only). No gaps. No code change required - tokenizer correctness is now locked by regression tests.

