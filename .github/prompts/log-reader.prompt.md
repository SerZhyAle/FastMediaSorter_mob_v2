---
agent: "agent"
description: "Use when: analyzing Android logcat files, diagnosing runtime errors or crashes, searching logs for patterns, or asked to run /log-reader command. Triggers on: logs, logcat, current.log, errors in log, crash analysis, ANR."
---

# Log Reader - Android Logcat Analyst

> **GLOBAL EXECUTION DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **STRICTLY TECHNICAL LANGUAGE:** No fluff, no conversational filler, dry technical prose only.
> 2. **AUTONOMY OVER BUREAUCRACY:** Resolve minor path or specification gaps without asking. Ask the user only when log file resolution still does not produce one existing target after the built-in fallback order is exhausted.
> 3. **TERSE REPORTING:** NO verbose summaries or time tracking. After executing this skill, output ONLY a single dry, concise statement of what was done and why.

Analyse FastMediaSorter Android logcat files for patterns, errors, warnings, and behaviour flows.

## Usage

```
/log-reader [optional: what to look for / file path]
```

Examples:

- `/log-reader` - analyse `logs/current.log` (auto-summary)
- `/log-reader errors` - show all E-level lines in current log
- `/log-reader PlayerActivity crash` - trace a specific area
- `/log-reader temp/fastmediasorter_20260326.log warnings` - named file + filter
- `/log-reader flow BrowseViewModel,MediaFileAdapter` - cross-tag flow trace

---

## Log File Resolution

When this command is invoked with `$ARGUMENTS`:

**Step 1 - Resolve the target log file.**

Use this fallback order and stop at the first existing file:

1. A `.log` path explicitly present in `$ARGUMENTS`
2. An existing path in `$ARGUMENTS` under `logs/` or `temp/`
3. `logs/current.log`
4. `temp/current.log`

If none of the candidates exists, list available `.log` files:

```powershell
Get-ChildItem -Path "logs","temp" -Filter "*.log" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 10 | Format-Table Name, LastWriteTime, @{N="KB";E={[int]($_.Length/1KB)}}
```

Show the list and ask the user which file to use.

**Step 2 - Get line count and file size. MANDATORY - do this before reading any content.**

```powershell
$f = Get-Item "<log_file>"
$lines = (Get-Content "<log_file>").Count
Write-Host "Size: $([int]($f.Length/1KB)) KB | Lines: $lines | Modified: $($f.LastWriteTime)"
```

If the resolved file exists, report its line count to the user before further analysis. If the count is `0`, report that the file is empty and stop. This prevents the critical mistake of reading only the first N lines and missing errors that appear at the end.

**Precedence rules:**

1. Resolve the file first.
2. Collect file size and line count.
3. Use file size to choose the tool family.
4. Use line count to choose how much content to read.
5. For diagnosis, prefer the tail before the head.

**Reading strategy based on line count:**

- **< 500 lines** → read the whole file
- **500–5000 lines** → read last 300 lines first (errors are at the tail), then head 50 lines for settings context
- **> 5000 lines** → use `search-log.ps1` with `-Errors`/`-Last`/`-Pattern`; never read linearly from the top

**File size for tool selection:**

- **< 2 MB** → `search-log.ps1` or Read tool with explicit tail offset
- **2–20 MB** → use `search-log.ps1` exclusively; do NOT load the full file into context
- **> 20 MB** → use `search-log.ps1` with targeted queries only; warn the user about size

> **RULE**: The beginning of the log contains app startup config and settings - useful for context. Errors and crashes are almost always at the END. Always read the tail before the head when diagnosing a problem.

---

## Analysis Mode Selection

Determine the analysis mode from `$ARGUMENTS` (remaining tokens after removing the file path):

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

If `$ARGUMENTS` contains none of these keywords but has free text, treat it as a **Pattern search** against the message and tag fields.

---

## Mode Procedures

### Auto-summary (default)

Run the summary script and report findings:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Summary
```

Then run errors check:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Errors -Unique -Stats -Top 30 -AppOnly
```

Then run exceptions check:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Exceptions
```

Then run warnings check:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Warnings -Top 20 -AppOnly
```

Then scan for spec verification tags:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "S\d{4}:" -AppOnly
```

Report structure:

1. **File info** - path, size, time range, total lines
2. **Level distribution** - counts for E/W/I/D/V
3. **Top errors** - first 30, grouped by tag if > 5 unique tags
4. **Top warnings** - first 20
5. **Spam tags** - tags with > 100 occurrences
6. **Spec verification tags** - see the dedicated subsection below; list which `Sxxxx` probes fired (count + first time). Omit this section only if none were found.
7. **Verdict** - one-paragraph health assessment: any crashes? repeated errors? suspicious patterns?

---

### Errors mode

```powershell
.\.scripts\utils\search-log.ps1 -LogFile "<file>" -Errors -AppOnly
```

For each error group (same tag):

- Show count, tag name, first occurrence time
- Show up to 5 representative messages
- If message contains `Exception` or `FATAL` → flag as **crash candidate** and switch to Exceptions mode
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

Use when crash, FATAL, or ANR keywords appear, or when `-Errors` output contains stack traces.

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Exceptions
```

The script auto-detects `FATAL EXCEPTION`, `AndroidRuntime`, `Exception:`, `Caused by:`, `ANR in`, and `begin of crash dump` blocks, then prints the full surrounding stack trace (up to 80 lines forward).

Each block is numbered (`══ BLOCK #N (line NNN) ══`) with line numbers on every line - copy the line number to jump directly in the log file.

For crashes matching a known exception type, also run:

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

Extract tag list from `$ARGUMENTS` (comma or space separated after `flow` keyword).

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

Annotate state transitions and flag any gaps > 2 seconds between consecutive flow events.

---

### Tag filter mode

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Tag "<name>" -AppOnly
```

Show all lines matching the tag, then print level distribution for that tag specifically.

> **TIMBER note**: In Timber format, the tag is the Timber tree tag (e.g. `App`, `BrowseViewModel`).
> **JSON note**: In JSON format, the tag is from `header.tag` which may include prefixes like `[CT]`.

---

### Pattern search mode

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "<regex>" -Context 2 -AppOnly
```

If > 100 matches: show first 30, last 10, and a count. Offer to narrow with `-From`/`-To` time range.

For case-sensitive searches (e.g. exact class names, constants):

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "<regex>" -CaseSensitive -Context 2
```

To deduplicate noisy repeated messages in results:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "<regex>" -Unique -Stats
```

To filter by a specific thread (useful when tracing a coroutine or worker):

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Thread "<tid>" -Pattern "<regex>"
.\scripts\utils\search-log.ps1 -LogFile "<file>" -ProcessFilter "<pid-tid>"
```

---

### Spam/noise detect mode

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Spam -Top 25
```

Report the top 25 noisy tags. For tags with > 500 occurrences, suggest adding them to `-Exclude` in future queries.

> **JSON note**: JSON exports from Android Studio may contain heavy system noise (oculus services, anchor queries etc.) - use `-AppOnly` to focus on the app.

---

### Time-range slice mode

Parse `HH:MM:SS` or `HH:MM` tokens from `$ARGUMENTS`.

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -From "<start>" -To "<end>" -AppOnly
```

Then run auto-summary on the slice.

---

### Spec verification tags (`Sxxxx:` debug probes)

App log messages whose text begins with a ticket id and a colon - `S0043: …`, `S0127: …` - are **debug verification tags** placed by the spec pipeline (see CLAUDE.md "Debug Verification Tags"). A tag exists in code only while its spec is in status `BlockNeedUserTest`; its presence in the log proves that the spec's changed code path was exercised in this session.

Find them:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Pattern "S\d{4}:" -AppOnly
```

What to do with them:

- Group by id. For each `Sxxxx` report: hit count, first/last occurrence time, and the message text (it usually names the exercised flow, e.g. `S0054: TsPacketFormatDetector.detect probeSize=576 -> BD_192`).
- Resolve each id to confirm it really is awaiting on-device test: `pwsh -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`. Expected status `BlockNeedUserTest`. If the journal says anything else, flag the tag as **stale** (it should have been removed when the spec left `BlockNeedUserTest`) - note it so the next `/spec-check` / `/spec-fix` strips it.
- In the verdict line, state which `Sxxxx` probes fired this session - that is the signal the user needs before running `/spec-check Sxxxx` (which, on `Verified`, also deletes the tags).
- If the user is testing a specific spec and its `Sxxxx:` probe is **absent** from the log, say so: the scenario did not reach that code path → on-device verification is incomplete, not failed.
- Never treat a `Sxxxx:` line as an error or warning regardless of its level - it is an instrumentation probe.

---

## Format Conversion (Optional)

To convert any format to standard logcat text for archival or manual inspection:
```powershell
.\scripts\utils\convert-log.ps1 -InputFile "<file>"
# Writes to: temp/<name>.normalized.log
# Then analyse normally:
.\scripts\utils\search-log.ps1 -LogFile "temp/<name>.normalized.log" -Summary
```

Use `convert-log.ps1` when:
- You need to grep the raw text of a JSON `.logcat` file
- You want to combine multiple Timber session exports into one file
- You want to archive a session in a universally readable format

---

## Common Android Log Patterns to Recognise

When reading log content, proactively flag these patterns:

| Pattern | Significance |
|---------|-------------|
| `FATAL EXCEPTION` / `Process: ... PID:` | App crash - show full stack trace |
| `ANR in` | App Not Responding - show what was running |
| `OutOfMemoryError` | Memory pressure - check heap stats from startup banner |
| `FileNotFoundException` / `IOException` | File access failure - note path if visible |
| `SecurityException` | Permission denied - note API level and permission |
| `NetworkOnMainThreadException` | Threading violation |
| `IllegalStateException` | Often lifecycle issue in Fragment/Activity |
| `NullPointerException` | Show surrounding 10 lines for context |
| `W  Slow operation` / `I/Choreographer.*skipped` | Performance issue |
| `E  SQLite` | Database error |
| `W  ExoPlayer` / `E  ExoPlayer` | Media playback failure |
| `W  Glide` / `E  Glide` | Image loading failure |
| `E  SMB` / `E  SFTP` / `E  FTP` | Network protocol failure |
| message text matching `^S\d{4}: ` (e.g. `S0043: …`) | Spec verification probe - the spec is in `BlockNeedUserTest`; this line proves its code path ran. Not an error. Report the id; cross-ref `select.ps1 -Id Sxxxx`. See "Spec verification tags" mode. |

For FastMediaSorter-specific tags, look for:

- `FastMediaSorter` - app-level events (startup banner, key lifecycle)
- `BrowseViewModel`, `PlayerViewModel`, `MainViewModel` - ViewModel state
- `ImageLoading`, `ImageLoadingManager` - Glide/image pipeline
- `NetworkSync`, `NetworkFilesSyncWorker` - background sync
- `SmbOps`, `SftpOps`, `FtpOps` - protocol-level network ops
- `CastManager`, `ChromecastSession` - Cast feature
- `ThumbnailWorker`, `ThumbnailPreload` - thumbnail pipeline

---

## FastMediaSorter-Specific Log Tags

Key tags to watch for in this project:

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
- **LOGCAT/JSON**: tags are full class-level or Android framework tags
- **TIMBER**: most app logs use the single tag `App` (Timber default tree) - filter by pattern in message, not tag:
  ```powershell
  .\scripts\utils\search-log.ps1 -LogFile "<file>" -Tag "App" -Pattern "BrowseViewModel|SMB|Player"
  ```

---

## Output Format Rules

- Always start with **file metadata**: path, size, time range, total/app-only line count.
- Use a **findings table** for grouped results (tag | count | level | sample message).
- Every output line includes a **line number** `[  NNN]` - reference it when quoting log lines (e.g. "line 344").
- For crashes: reproduce the **full stack trace** inline (not truncated).
- For large results: show first N, summarise the rest ("… and X more similar lines").
- End each analysis with a **1–3 line verdict**: what is healthy, what needs attention.
- If the log shows a startup banner (FastMediaSorter V2 - STARTUP INFO), extract and display: version, flavor, API level, device model, RAM, build type.

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

# Pull fresh logs from device
.\scripts\utils\extract-device-logs.ps1
```

Available log locations:

- `logs/current.log` - most recent session (primary)
- `temp/current.log` - fallback copy
- `temp/fastmediasorter_YYYYMMDD_HHmmss.log` - timestamped archives
