# Log Reader — Android Logcat Analyst

Analyse FastMediaSorter Android logcat files for patterns, errors, warnings, and behaviour flows.

## Usage

```
/log-reader [optional: what to look for / file path]
```

Examples:
- `/log-reader` — analyse `logs/current.log` (auto-summary)
- `/log-reader errors` — show all E-level lines in current log
- `/log-reader PlayerActivity crash` — trace a specific area
- `/log-reader temp/fastmediasorter_20260326.log warnings` — named file + filter
- `/log-reader flow BrowseViewModel,MediaFileAdapter` — cross-tag flow trace

---

## Log File Resolution

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Resolve the target log file.**

Parse `$ARGUMENTS` for a file path token (ends with `.log` or is an existing path):
- If a `.log` path is found → use it as-is (relative to project root)
- If `$ARGUMENTS` contains `temp/` or `logs/` prefix → use that path directly
- Default: `logs/current.log`

If the resolved file does not exist, check `temp/current.log` as fallback. If neither exists, list available `.log` files:
```powershell
Get-ChildItem -Path "logs","temp" -Filter "*.log" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 10 | Format-Table Name, LastWriteTime, @{N="KB";E={[int]($_.Length/1KB)}}
```
Show the list and ask the user which file to use.

**Step 2 — Check file size.**

```powershell
$size = (Get-Item "<log_file>").Length / 1MB
```
- **< 2 MB** → read directly with the Read tool (offset/limit as needed)
- **2–20 MB** → use `search-log.ps1` exclusively; do NOT load the full file into context
- **> 20 MB** → use `search-log.ps1` with targeted queries only; warn the user about size

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

Report structure:
1. **File info** — path, size, time range, total lines
2. **Level distribution** — counts for E/W/I/D/V
3. **Top errors** — first 30, grouped by tag if > 5 unique tags
4. **Top warnings** — first 20
5. **Spam tags** — tags with > 100 occurrences
6. **Verdict** — one-paragraph health assessment: any crashes? repeated errors? suspicious patterns?

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

Each block is numbered (`══ BLOCK #N (line NNN) ══`) with line numbers on every line — copy the line number to jump directly in the log file.

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
...
```

Annotate state transitions and flag any gaps > 2 seconds between consecutive flow events.

---

### Tag filter mode

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -Tag "<name>" -AppOnly
```

Show all lines matching the tag, then print level distribution for that tag specifically.

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

---

### Time-range slice mode

Parse `HH:MM:SS` or `HH:MM` tokens from `$ARGUMENTS`.

```powershell
.\scripts\utils\search-log.ps1 -LogFile "<file>" -From "<start>" -To "<end>" -AppOnly
```

Then run auto-summary on the slice.

---

## Common Android Log Patterns to Recognise

When reading log content, proactively flag these patterns:

| Pattern | Significance |
|---------|-------------|
| `FATAL EXCEPTION` / `Process: ... PID:` | App crash — show full stack trace |
| `ANR in` | App Not Responding — show what was running |
| `OutOfMemoryError` | Memory pressure — check heap stats from startup banner |
| `FileNotFoundException` / `IOException` | File access failure — note path if visible |
| `SecurityException` | Permission denied — note API level and permission |
| `NetworkOnMainThreadException` | Threading violation |
| `IllegalStateException` | Often lifecycle issue in Fragment/Activity |
| `NullPointerException` | Show surrounding 10 lines for context |
| `W  Slow operation` / `I/Choreographer.*skipped` | Performance issue |
| `E  SQLite` | Database error |
| `W  ExoPlayer` / `E  ExoPlayer` | Media playback failure |
| `W  Glide` / `E  Glide` | Image loading failure |
| `E  SMB` / `E  SFTP` / `E  FTP` | Network protocol failure |

For FastMediaSorter-specific tags, look for:
- `FastMediaSorter` — app-level events (startup banner, key lifecycle)
- `BrowseViewModel`, `PlayerViewModel`, `MainViewModel` — ViewModel state
- `ImageLoading`, `ImageLoadingManager` — Glide/image pipeline
- `NetworkSync`, `NetworkFilesSyncWorker` — background sync
- `SmbOps`, `SftpOps`, `FtpOps` — protocol-level network ops
- `CastManager`, `ChromecastSession` — Cast feature
- `ThumbnailWorker`, `ThumbnailPreload` — thumbnail pipeline

---

## Output Format Rules

- Always start with **file metadata**: path, size, time range, total/app-only line count.
- Use a **findings table** for grouped results (tag | count | level | sample message).
- Every output line includes a **line number** `[  NNN]` — reference it when quoting log lines (e.g. "line 344").
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
- `logs/current.log` — most recent session (primary)
- `temp/current.log` — fallback copy
- `temp/fastmediasorter_YYYYMMDD_HHmmss.log` — timestamped archives
