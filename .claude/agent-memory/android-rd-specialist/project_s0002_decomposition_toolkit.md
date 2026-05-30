---
name: s0002-decomposition-toolkit
description: Mechanical-compression and extraction toolkit accumulated during S0002 Wave 53 for driving Kotlin files under the 1000-LOC hard limit
metadata:
  type: project
---

S0002 Wave 53 (2026-05-27) drove 12/16 hard-limit-zone Kotlin files from 1000-1486 LOC to <=1000 LOC in a single session. Wave 54 still has 4 files: `TextViewerManager.kt` 1406, `PdfViewerManager.kt` 1274, `PlayerActivity.kt` 1229, `CommandPanelController.kt` 1155.

**Why:** the user's stated goal was "сжать все <1000, потом Verified". Sessions of this kind are time-bounded; mechanical compression wins fast, architectural extraction is the slow critical-path work.

**How to apply for Wave 54+:**

Reusable scripts in `temp/`:
- `collapse_simple_kdocs.ps1` - multi-line KDoc (3+ lines without `@param`/`@return`/`@throws`/```/bullets/`[](url)`) -> single-line `/** ... */`. Typical win 30-100 LOC per file.
- `collapse_blank_lines.ps1` - 2+ consecutive blank lines -> 1; removes trailing whitespace. Small win (~5 LOC).
- `collapse_line_comments.ps1` - 3+ consecutive `// ...` lines (same indent, no TODO/FIXME/HACK/URL/bullets) -> single line. Modest win.
- `strip_trace_logs.ps1` - parameterised `Timber.d/v` pattern strip. Use per-file with specific patterns (e.g. `Timber\.d\("EPUB:`). Saves ~10-50 LOC per file. SAFE - Timber.e/w/i for diagnostic info should stay.
- `fix_empty_else.ps1` - removes `} else { }` patterns left after Timber strip.
- `temp/get_big_files.ps1` - reads `dev/CATALOG/app_v2.jsonl` and lists files >= 700 LOC.

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
