---
model: sonnet
---

# New Log - Remote Diagnostics Intake

> **GLOBAL DIRECTIVES (anti-bureaucracy):**
> 1. Dry technical prose, no filler.
> 2. Autonomy: run the import + analysis chain without asking. Ask only when the import script
>    fails to find any candidate archive (exit code 1/2) - then surface the reason and wait.
> 3. Terse report: end with one line per analysed file (verdict) plus the aggregate parked-ticket list.

Pulls the newest diagnostic-log archive dropped by an externally-tested device into `logs/`,
then runs `/log-reader` on exactly the sessions that are new since the last import - to surface
issues and create/update `Sxxxx` tickets from a remote test pass.

## Why this exists

The app is tested on external/remote devices this machine cannot `adb` into directly. On error,
the tester uses the app's built-in "export logs" action (`LogExportHelper.kt`), which zips every
on-device `fastmediasorter_*.log` file and shares it out; that share is routed through Google
Drive, landing the archive in the locally synced drop folder `C:\GD\temp`.

The archive's filename is **not** a reliable signal:
- It comes from the share intent's subject text (`export_logs_subject`), which is
  locale-dependent - the device may be running EN/RU/UK, so the name text differs per drop.
- Google Drive appends `" (1)"`, `" (2)"`, .. on repeat drops with the same subject.
- It can arrive with no file extension at all.

So selection is by **recency + zip validity + content** only, never by name - see
`scripts/utils/import-remote-logs.ps1` header for the exact algorithm.

## Usage

```text
/newlog                              # auto-detect newest archive in C:\GD\temp, import + analyse
/newlog --source "<dir>"             # override the drop folder (default C:\GD\temp)
/newlog --archive "<path>"           # import one specific archive, skip auto-detection
```

---

## Process

### 1 - Import

```powershell
pwsh -NoProfile -File scripts/utils/import-remote-logs.ps1 -Json [-SourceDir "<dir>"] [-ArchivePath "<path>"]
```

Parse the JSON result: `archive`, `archiveModified`, `newFiles`, `updatedFiles`, `unchangedFiles`.

Exit codes:

| Code | Meaning | Action |
|------|---------|--------|
| 0 | archive found and imported | Continue to step 2 |
| 1 | `SourceDir` does not exist | Report the path, ask user to confirm the drop location (or pass `--source`) |
| 2 | no zip-with-`.log`-entries found among the newest files (or `--archive` was not one) | Report `reason` verbatim, ask user whether the export already synced to the drop folder |
| 3 | extraction failed (I/O error writing to `logs/`) | Report `reason` verbatim, check `logs/` is writable |

### 2 - Determine files to analyse

`toAnalyze = newFiles + updatedFiles`, ordered chronologically by the timestamp embedded in the
filename (`fastmediasorter_YYYYMMDD_HHmmss.log`), oldest first.

`unchangedFiles` were already extracted (and presumably analysed) by a prior `/newlog` run or a
manual import - skip them, they are not re-read.

`toAnalyze` empty -> report: archive `<path>` (modified `<time>`) imported, `<N>` sessions already
known, nothing new to analyse. **Stop - do not invoke `/log-reader`.**

### 3 - Analyse each new/updated session

For every file in `toAnalyze`, in chronological order:

```
/log-reader logs/<file>
```

No focus keyword - a remote diagnostic drop is triaged blind, so `/log-reader`'s default
auto-summary mode is the correct one. Let `/log-reader` run its own out-of-scope `/spec-draft`
auto-park (CLAUDE.md §3.1, dedup'd via `search.ps1`) and its `Sxxxx:` probe-verification report -
`/newlog` does not draft or mutate specs itself, that logic already lives in `/log-reader`.

Record, per file: the one-line verdict, any parked `Sxxxx` ids, any `Sxxxx:` probes reported as
fired or stale.

### 4 - Aggregate report

One consolidated summary, most-recent session last:

```
newlog: archive <name> (<modified>), N new / M updated / K unchanged sessions
  <file1>: <verdict> | parked: <Sxxxx,..|none>
  <file2>: <verdict> | parked: <Sxxxx,..|none>
```

If any session surfaced a crash/exception, lead with that file instead of chronological order.

---

## Constraints

- **Source folder is read-only.** `C:\GD\temp` holds unrelated personal files (documents, photos,
  other archives) - `/newlog` never deletes or moves anything there, it only reads.
- **Extraction target is exactly `logs/`.** Never write elsewhere.
- **Filename is never an identity signal for the archive itself** - selection is recency + zip
  content only, per the "Why this exists" section above. Do not special-case a language string.
- **Dedup is by (name, size)**, not hash - filenames are session-start timestamps generated
  on-device, so a same-name/same-size collision with different content is not realistic here.
- **Ticket creation/update is fully delegated to `/log-reader`.** `/newlog` is the intake +
  fan-out glue, not a spec-mutating skill in its own right.

---

## Output artifacts

| Path | Purpose |
|------|---------|
| `logs/fastmediasorter_*.log` | Extracted session log files (additive, dedup-aware) |
| Whatever `/log-reader` and its `/spec-draft` auto-park produce per analysed session | Ticket creation/update |

No other paths are written.
