# S1034 - Thread-safe date formatting in the logging path (shared SimpleDateFormat)

**Status:** Archived

## 0. Raw finding (auto-parked from S1030 audit, 2026-07-13)

`SimpleDateFormat` is not thread-safe; a shared/singleton instance formatted concurrently can corrupt
output or throw `NumberFormatException`/`ArrayIndexOutOfBoundsException`. The S1030 audit found 2 real
shared-field instances (most other sites are per-call locals = safe):

- `core/logging/LoggingHelper.kt:197-198` - `dateFormat`/`fileNameFormat` fields on
  `FileLoggingTree : Timber.Tree()`. Timber trees are invoked from `Timber.log()` on ANY thread (main,
  IO, worker, network callbacks all log concurrently in this app) -> concurrent `.format()` on one
  instance. Highest-risk site.
- `domain/usecase/ExecuteScheduledOperationUseCase.kt:48` - `logDateFormat` field on a `@Singleton`
  class; same risk if scheduled-op execution runs concurrently.

The project already knows the fix pattern: `ui/browse/AdapterFileInfoFormatter.kt:12-16` uses
`ThreadLocal<SimpleDateFormat>` with a comment explaining the exact hazard.

## 1. Why this needs its own ticket

- P1-ish correctness (crash/corruption risk under concurrency) touching the global logging path -
  deserves its own visible ticket + verification, not an inline edit buried in an audit umbrella.

## 2. Proposed direction (decide at approval)

- Wrap both shared fields in `ThreadLocal<SimpleDateFormat>` (mirror `AdapterFileInfoFormatter`), or
  migrate to `java.time.format.DateTimeFormatter` (immutable, thread-safe; API 26+ = standard minSdk,
  guard/desugar for the legacy flavor minSdk 23).
- Confirm no behavioural change to log timestamp/filename formats.

## 3. Resolution (2026-07-13) - Verified

Both shared fields wrapped in `ThreadLocal<SimpleDateFormat>` (API23-safe `object : ThreadLocal(){ initialValue() }`
form, mirroring `AdapterFileInfoFormatter`), patterns/locales unchanged (behaviour-preserving):
- `core/logging/LoggingHelper.kt` - `dateFormat`/`fileNameFormat` now ThreadLocal; all 7 call sites routed
  through new `formatTs(Date)`/`formatFileName(Date)` helpers; `!!` confined to the two helper bodies.
- `domain/usecase/ExecuteScheduledOperationUseCase.kt` - `logDateFormat` ThreadLocal; single call site via `.get()!!.format`.

Validation: `a.ps1 fk` BUILD SUCCESSFUL; detekt scoped PASS (no new findings in the two files); grep confirms
zero remaining bare `dateFormat.format(`/`fileNameFormat.format(`/`logDateFormat.format(` sites. No user-visible
change (identical timestamps/filenames). No device test needed.

## 4. Notes

- Parent audit: S1030 (archived umbrella).
