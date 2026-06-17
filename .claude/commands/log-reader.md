---
model: sonnet
---

# Log Reader - Android Logcat Analyst

> **GLOBAL DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **Dry technical prose only** - no filler.
> 2. **Autonomy:** resolve minor path/spec gaps without asking. Ask only when log file resolution still yields no existing target after the fallback order is exhausted.
> 3. **Terse report:** one dry statement of what was done and why.
> 4. **Park out-of-scope findings (CLAUDE.md §3.1):** a log analysis that surfaces problems unrelated to the requested focus and non-trivial (own research + fix) → auto `/spec-draft`, one per distinct problem, after dedup via `scripts/spec_catalog/search.ps1`. Capture the symptom + offending log lines into the skeleton's §0. List the parked `Sxxxx` ids in the verdict. Already-ticketed or trivial findings are not parked.

Analyse FastMediaSorter Android logcat files for patterns, errors, warnings, behaviour flows.

## Usage

```
/log-reader [optional: what to look for / file path]
```

Examples:
- `/log-reader` - analyse `logs/current.log` (auto-summary)
- `/log-reader errors` - all E-level lines in current log
- `/log-reader PlayerActivity crash` - trace a specific area
- `/log-reader temp/fastmediasorter_20260326.log warnings` - named file + filter
- `/log-reader flow BrowseViewModel,MediaFileAdapter` - cross-tag flow trace

---

## Log File Resolution

On `$ARGUMENTS`:

**Step 1 - Resolve target log file.** Fallback order, stop at first existing:
1. A `.log` path explicitly in `$ARGUMENTS`
2. An existing path in `$ARGUMENTS` under `logs/` or `temp/`
3. `logs/current.log`
4. `temp/current.log`

None exist → list available `.log` files, then ask which to use:

```powershell
Get-ChildItem -Path "logs","temp" -Filter "*.log" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 10 | Format-Table Name, LastWriteTime, @{N="KB";E={[int]($_.Length/1KB)}}
```

**Step 2 - Get line count and file size. MANDATORY before reading any content.**

```powershell
$f = Get-Item "<log_file>"
$lines = (Get-Content "<log_file>").Count
Write-Host "Size: $([int]($f.Length/1KB)) KB | Lines: $lines | Modified: $($f.LastWriteTime)"
```

Report line count before further analysis. Count `0` → report empty and stop. This prevents reading only the first N lines and missing errors at the end.

**Precedence:** resolve file → collect size + line count → file size picks tool family → line count picks how much to read → prefer tail before head for diagnosis.

**Reading strategy by line count:**
- **< 500 lines** → read whole file
- **500–5000 lines** → read last 300 lines first (errors at tail), then head 50 for settings context
- **> 5000 lines** → `search-log.ps1` with `-Errors`/`-Last`/`-Pattern`; never read linearly from top

**File size → tool:**
- **< 2 MB** → `search-log.ps1` or Read with explicit tail offset
- **2–20 MB** → `search-log.ps1` exclusively; do NOT load full file into context
- **> 20 MB** → `search-log.ps1` targeted queries only; warn user about size

> **RULE:** log head = app startup config + settings (context). Errors/crashes are almost always at the END. Always read tail before head when diagnosing.

---

## Analysis Mode Selection

Mode from `$ARGUMENTS` (tokens remaining after file path):

| Keyword(s) in $ARGUMENTS | Mode |
|--------------------------|------|
| *(empty)* | **Auto-summary** |
| `error`, `errors`, `crash`, `exception` | **Errors** |
| `exceptions`, `fatal`, `anr` | **Exceptions** |
| `warn`, `warning`, `warnings` | **Warnings** |
| `flow <tags>` | **Flow trace** |
| `tag <name>` | **Tag filter** |
| `pattern <regex>` | **Pattern search** |
| `spam`, `noise` | **Spam/noise detect** |
| `time <HH:MM> <HH:MM>` | **Time-range slice** |
| `summary` | **Full summary** |

No keyword but free text present → treat as **Pattern search** against message + tag fields.

---

## Mode Procedures

### Auto-summary (default)

Run in sequence:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Summary
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Errors -Unique -Stats -Top 30 -AppOnly
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Exceptions
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Warnings -Top 20 -AppOnly
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "S\d{4}:" -AppOnly
```

Report structure:
1. **File info** - path, size, time range, total lines
2. **Level distribution** - counts for E/W/I/D/V
3. **Top errors** - first 30, grouped by tag if > 5 unique tags
4. **Top warnings** - first 20
5. **Spam tags** - tags with > 100 occurrences
6. **Spec verification tags** - see dedicated subsection; list which `Sxxxx` probes fired (count + first time). Omit only if none found.
7. **Verdict** - one paragraph: crashes? repeated errors? suspicious patterns?
8. **Parked findings** - out-of-scope, non-trivial problems captured as `/spec-draft` skeletons per directive 4 (id + slug + one-line symptom). Omit if none parked.

---

### Errors mode

```powershell
.\.scripts\utils\search-log.ps1 -LogFile "<file>" -Errors -AppOnly
```

Per error group (same tag):
- Show count, tag name, first occurrence time
- Up to 5 representative messages
- Message contains `Exception` or `FATAL` → flag as **crash candidate**, switch to Exceptions mode
- Show context around first crash: `-Context 5`

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "<ExceptionClass>" -Context 5 -AppOnly
```

Use `-Unique` to collapse repeated identical errors before grouping:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Errors -Unique -Stats -AppOnly
```

---

### Exceptions mode

Use when crash, FATAL, or ANR keywords appear, or `-Errors` output has stack traces.

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Exceptions
```

Auto-detects `FATAL EXCEPTION`, `AndroidRuntime`, `Exception:`, `Caused by:`, `ANR in`, `begin of crash dump` blocks; prints full surrounding stack trace (up to 80 lines forward). Each block numbered (`══ BLOCK #N (line NNN) ══`) with line numbers on every line - copy the number to jump in the file.

For known exception types also run:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "IllegalStateException|NullPointerException" -Context 10 -AppOnly
```

Save full crash dump:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Exceptions -OutFile "temp/crashes.txt"
```

---

### Warnings mode

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Warnings -AppOnly
```

Group by tag, show counts, highlight any that also appear in error context.

---

### Flow trace mode

Extract tag list from `$ARGUMENTS` (comma/space separated after `flow`).

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Flow "<tag1>","<tag2>",... -AppOnly
```

Present as a chronological event table:

```
TIME        TAG                     LVL  MESSAGE
14:16:07    BrowseViewModel         I    loadDirectory called: /sdcard/DCIM
14:16:07    MediaFileAdapter        D    onFilesChanged: 42 items
..
```

Annotate state transitions; flag gaps > 2 seconds between consecutive flow events.

---

### Tag filter mode

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Tag "<name>" -AppOnly
```

Show all lines matching the tag, then level distribution for that tag.

> **TIMBER note:** tag is the Timber tree tag (e.g. `App`, `BrowseViewModel`).
> **JSON note:** tag is from `header.tag`, may include prefixes like `[CT]`.

---

### Pattern search mode

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "<regex>" -Context 2 -AppOnly
```

> 100 matches: show first 30, last 10, count. Offer `-From`/`-To` to narrow.

Case-sensitive (exact class names, constants):

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "<regex>" -CaseSensitive -Context 2
```

Deduplicate noisy repeats:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "<regex>" -Unique -Stats
```

Filter by thread (tracing a coroutine/worker):

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Thread "<tid>" -Pattern "<regex>"
.\scripts\utils\search-log.ps1 -LogFile "<file>" -ProcessFilter "<pid-tid>"
```

---

### Spam/noise detect mode

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Spam -Top 25
```

Report top 25 noisy tags. Tags with > 500 occurrences → suggest adding to `-Exclude` in future queries.

> **JSON note:** JSON exports from Android Studio may carry heavy system noise (oculus services, anchor queries) - use `-AppOnly` to focus on the app.

---

### Time-range slice mode

Parse `HH:MM:SS` or `HH:MM` tokens from `$ARGUMENTS`.

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -From "<start>" -To "<end>" -AppOnly
```

Then run auto-summary on the slice.

---

### Spec verification tags (`Sxxxx:` debug probes)

App log messages beginning with a ticket id + colon - `S0043: …`, `S0127: …` - are **debug verification tags** placed by the spec pipeline (CLAUDE.md "Debug Verification Tags"). Valid form = a temporary debug-level probe via `Timber.d`; a tag exists in code only while its spec is `BlockNeedUserTest`. Its presence proves the spec's changed code path was exercised this session.

Find them:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "S\d{4}:" -AppOnly
```

Handling:
- Group by id. Per `Sxxxx` report: hit count, first/last time, message text (usually names the flow, e.g. `S0054: TsPacketFormatDetector.detect probeSize=576 -> BD_192`).
- Resolve each id - expected status `BlockNeedUserTest`: `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`. Any other status → flag tag as **stale** (should have been removed when spec left `BlockNeedUserTest`); note it for next `/spec-check` / `/spec-fix`.
- `Sxxxx:` at `I`/`W`/`E` level, or any form not the temporary `Timber.d("Sxxxx: …")` probe → flag as **invalid instrumentation**. Ticket ids are reserved for temporary verification probes only; must not remain in persistent info/warn/error text.
- In the verdict, state which `Sxxxx` probes fired this session - the signal the user needs before `/spec-check Sxxxx` (which, on `Verified`, deletes the tags).
- User testing a specific spec but its `Sxxxx:` probe is **absent** → say so: scenario did not reach that code path → verification incomplete, not failed.
- Treat `Sxxxx:` as valid only when it matches the temporary debug-level pattern AND spec is currently `BlockNeedUserTest`. Otherwise call out as stale/invalid.

---

## Format Conversion (Optional)

Convert any format to standard logcat text for archival/inspection:

```powershell
.\scripts\utils\convert-log.ps1 -InputFile "<file>"
# Writes to: temp/<name>.normalized.log
# Then analyse normally:
.\scripts\utils\search-log.ps1 -LogFile "temp/<name>.normalized.log" -Summary
```

Use `convert-log.ps1` when:
- Grepping the raw text of a JSON `.logcat` file
- Combining multiple Timber session exports into one file
- Archiving a session in a universally readable format

---

## Common Android Log Patterns to Recognise

Proactively flag:

| Pattern | Significance |
|---------|-------------|
| `FATAL EXCEPTION` / `Process: ... PID:` | App crash - show full stack trace |
| `ANR in` | App Not Responding - show what was running |
| `OutOfMemoryError` | Memory pressure - check heap stats from startup banner |
| `FileNotFoundException` / `IOException` | File access failure - note path if visible |
| `SecurityException` | Permission denied - note API level + permission |
| `NetworkOnMainThreadException` | Threading violation |
| `IllegalStateException` | Often Fragment/Activity lifecycle issue |
| `NullPointerException` | Show surrounding 10 lines for context |
| `W  Slow operation` / `I/Choreographer.*skipped` | Performance issue |
| `E  SQLite` | Database error |
| `W  ExoPlayer` / `E  ExoPlayer` | Media playback failure |
| `W  Glide` / `E  Glide` | Image loading failure |
| `E  SMB` / `E  SFTP` / `E  FTP` | Network protocol failure |
| message text matching `^S\d{4}: ` (e.g. `S0043: …`) | Valid only as a temporary debug-level spec verification probe while spec is `BlockNeedUserTest`. If `I/W/E` or any other status, report as stale/invalid instrumentation to remove or rewrite. See "Spec verification tags" mode. |

FastMediaSorter-specific tags to look for:
- `FastMediaSorter` - app-level events (startup banner, key lifecycle)
- `BrowseViewModel`, `PlayerViewModel`, `MainViewModel` - ViewModel state
- `ImageLoading`, `ImageLoadingManager` - Glide/image pipeline
- `NetworkSync`, `NetworkFilesSyncWorker` - background sync
- `SmbOps`, `SftpOps`, `FtpOps` - protocol-level network ops
- `CastManager`, `ChromecastSession` - Cast feature
- `ThumbnailWorker`, `ThumbnailPreload` - thumbnail pipeline

---

## FastMediaSorter-Specific Log Tags

| Tag | Component | Notes |
|-----|-----------|-------|
| `BrowseViewModel` | File browser screen | |
| `MediaFileAdapter` | RecyclerView adapter for media files | |
| `PlayerActivity` | Video/audio player | |
| `SmbManager` / `SmbBrowseManager` | SMB/network file access | |
| `FtpManager` / `SftpManager` | FTP/SFTP file access | |
| `GlideAppModule` / `ImageLoad` | Image loading & thumbnail cache | |
| `SortUseCase` | Sorting logic | |
| `TransferManager` | File copy/move operations | |
| `WearDataSync` | Wear OS data layer | |
| `WorkManager` | Background workers | |
| `App` | Timber tree tag (TIMBER format) | All app logs in Timber export |

### Format-specific tag notes
- **LOGCAT/JSON:** tags are full class-level or Android framework tags
- **TIMBER:** most app logs use the single tag `App` (Timber default tree) - filter by pattern in message, not tag:
  ```powershell
  .\scripts\utils\search-log.ps1 -LogFile "<file>" -Tag "App" -Pattern "BrowseViewModel|SMB|Player"
  ```

---

## Output Format Rules

- Start with **file metadata**: path, size, time range, total/app-only line count.
- **Findings table** for grouped results (tag | count | level | sample message).
- Every output line includes a **line number** `[  NNN]` - reference it when quoting (e.g. "line 344").
- Crashes: reproduce the **full stack trace** inline (not truncated).
- Large results: show first N, summarise the rest ("… and X more similar lines").
- End each analysis with a **1–3 line verdict**: what is healthy, what needs attention.
- Startup banner present (FastMediaSorter V2 - STARTUP INFO) → extract + display: version, flavor, API level, device model, RAM, build type.

---

## Script Quick Reference

```powershell
# Summary overview
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Summary

# All errors (app only)
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Errors -AppOnly

# Unique errors with tag/level breakdown
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Errors -Unique -Stats -AppOnly

# Crash / exception blocks with full stack traces
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Exceptions
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Exceptions -OutFile "temp/crashes.txt"

# Warnings + errors
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Warnings -AppOnly

# Pattern with context (line numbers shown on every match)
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Pattern "regex" -Context 3 -AppOnly

# Case-sensitive pattern (class names, constants)
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Pattern "ExactClass" -CaseSensitive -Context 3

# Deduplicate repeated messages
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Pattern "failed" -Unique

# Stats breakdown of any result set
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Errors -Stats

# Tag drill-down
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Tag "TagName" -AppOnly

# Filter by thread ID (TID) or process (PID-TID)
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Thread "4823" -Errors
.\scripts\utils\search-log.ps1 -LogFile "<f>" -ProcessFilter "1234-5678"

# Flow across multiple tags
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Flow "VM1","VM2","Adapter" -AppOnly

# Time slice
.\scripts\utils\search-log.ps1 -LogFile "<f>" -From "14:16:00" -To "14:20:00" -AppOnly

# Spam/noise detection
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Spam -Top 25

# Save results (works in ALL modes: Summary, Errors, Exceptions, Flow, Context, etc.)
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Errors -OutFile "temp/errors_analysis.txt"
.\scripts\utils\search-log.ps1 -LogFile "<f>" -Summary -OutFile "temp/summary.txt"

# Pull fresh logs from device (full harvest: logcat + prefs + device info)
.\scripts\utils\extract-device-logs.ps1

# Quick filtered live tail of the app's recent logcat (writes full dump to temp/)
.\a.ps1 adb log -Tail 500 -Grep "Exception|S\d{4}:"
```

Available log locations:
- `logs/current.log` - most recent session (primary)
- `temp/current.log` - fallback copy
- `temp/fastmediasorter_YYYYMMDD_HHmmss.log` - timestamped archives
