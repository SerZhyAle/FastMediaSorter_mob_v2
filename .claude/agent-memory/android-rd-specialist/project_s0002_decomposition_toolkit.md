---
name: s0002-decomposition-toolkit
description: Big-file decomposition know-how from S0002 (Archived) - mechanical-compression tactics + extraction patterns that worked; the temp/ helper scripts were wiped, re-author on demand
metadata:
  type: project
---

S0002 (Archived) Wave 53 (2026-05-27) drove 12/16 hard-limit-zone Kotlin files from 1000-1486 LOC to <=1000 LOC in a single session. (Wave 54 leftovers as of then: `TextViewerManager.kt` 1406, `PdfViewerManager.kt` 1274, `PlayerActivity.kt` 1229, `CommandPanelController.kt` 1155 - re-check live LOC via `dev/CATALOG` before acting.)

**Why:** the user's stated goal was "сжать все <1000, потом Verified". Sessions of this kind are time-bounded; mechanical compression wins fast, architectural extraction is the slow critical-path work.

**How to apply:**

Mechanical-compression tactics (the helper scripts lived in `temp/` and were since WIPED - re-author on demand, each is ~30 lines of PowerShell):
- Collapse multi-line KDoc (3+ lines without `@param`/`@return`/`@throws`/code fences/bullets) -> single-line `/** ... */`. Typical win 30-100 LOC per file.
- Collapse 2+ consecutive blank lines -> 1; strip trailing whitespace. Small win (~5 LOC).
- Collapse 3+ consecutive `// ...` lines (same indent, no TODO/FIXME/HACK/URL) -> single line. Modest win.
- Strip `Timber.d/v` by specific pattern (e.g. `Timber\.d\("EPUB:`) per file. Saves ~10-50 LOC. SAFE - keep Timber.e/w/i diagnostics; then remove `} else { }` husks left behind.
- Big-file inventory: read `dev/CATALOG/app_v2.jsonl`, filter LOC >= 700.

Extraction patterns that worked (one file each):
- `StandaloneLaunchDebugLogger` - debug-only `BuildConfig.DEBUG`-gated block (~100 LOC).
- `MainEventHandler` - sealed-class event dispatcher when each variant is a small Intent/Toast/Dialog action.
- `EpubWebViewLifecycle` - WebView config + asset interception + destroy/clean-up.
- `PlayerFileOpsInitializer` - a single `init*()` block from a big orchestrator class, passing lambdas back for state mutators.
- `GoogleDriveMultipartUploader` - single network operation with self-contained HTTP code.
- `ImageLoadingGlideListeners` - two RequestListener objects with shared state - take state via getter/setter lambdas.
- Extension functions on `View` for repeated click-listener boilerplate in adapters (MediaFileAdapter: bindFileClick, bindFileTypeClick).

Anti-pattern - DO NOT try in a single session:
- Architectural decomposition of UI-heavy classes (TextViewerManager, PlayerActivity) - 30-60 min each with high regression risk.
- Refactoring `withContext(IO) { try { ... } catch ... }` patterns into a generic helper - the catch branches differ enough that the helper bloats.

Rules of thumb:
- KDoc collapse + line-comment collapse + Timber.d strip + blank-line collapse together typically save 50-150 LOC per file.
- Files with delta-needed > 200 LOC need real extraction.
- Always run `a.ps1 dq` between extraction batches; never accumulate 3+ extractions without a build check.
- Per-method extraction (`fun X() {...}` -> helper class) costs 5-10 min; per-subsystem extraction costs 30+ min.
