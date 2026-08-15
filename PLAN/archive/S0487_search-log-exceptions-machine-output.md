# S0487 - search-log.ps1 -Exceptions machine-readable output

**Status:** Archived
**Priority:** 40
**Date:** 2026-06-17

## 1. Problem

- `scripts/utils/search-log.ps1 -Exceptions` emitted only a human-readable, colored block dump ending in `"N exception block(s) found."` / `"No exception/crash blocks found."`.
- No `-Count` / `-Json` support and no distinct stdout contract: a caller could not tell "0 blocks" from "N blocks" without scraping the trailing message string.
- Discovered during S0484 (pre-release verdict aggregator). The aggregator could not consume `-Exceptions`, so `scripts/devtest/prerelease-verdict.ps1` fell back to a direct `Select-String` over the raw file.
- CLAUDE.md Rule 13 favours fixing the insufficient script over working around it.

## 2. Solution

- Added a `-Json` switch. `-Exceptions -Json` prints one compact JSON object on stdout: `{"count":N,"blocks":[{block,line,trigger,lines,text}]}`.
- Wired `-Exceptions -Count` to print `Match count: N` - the same contract the filter pipeline already uses, so the existing `Get-Count` helper in `prerelease-verdict.ps1` consumes it with no change.
- Per-block fields:
  - `block` - 1-based ordinal.
  - `line` - 1-based line number of the triggering line in the source log.
  - `trigger` - the crash-pattern substring that opened the block (leftmost match).
  - `lines` - number of displayed lines in the expanded block.
  - `text` - the trimmed triggering line (evidence).
- The `-Exceptions` scan now collects every block once into a structured list, then renders Json / Count / human from that single list, so all three modes report the identical block set and count.
- Diagnostic loader banners (`Loading ..`, `Loaded N raw lines ..`) are suppressed under `-Json` so stdout is pure JSON. Other modes are unchanged.
- Human-readable output is byte-for-byte the prior behaviour (header, per-block dump, trailing count).

## 3. Design decisions

- `-Count` prints `Match count: N`, not a bare integer, to stay consistent with the script's existing `-Count` mode and to be a drop-in for the aggregator's `Match count:\s*(\d+)` parser.
- `-Json` precedence: when both `-Json` and `-Count` are passed, `-Json` wins.
- The outer JSON object is hand-assembled around a `ConvertTo-Json -AsArray` block array so a single block still serializes as a list, not an object. Consumers read the authoritative top-level `count`.
- Exit code is intentionally left unchanged (0 on success, 1 reserved for "log file not found"). Overloading the exit code to signal "blocks found" would collide with the existing error convention and with caller exit-code schemes such as `prerelease-verdict.ps1` (0/1/2). Machine callers use `-Count` / `-Json` instead.

## 4. Collateral fix

- The Timber and LOGCAT loaders read `Get-Content` directly into `$rawLines`. For a single-line or empty log `Get-Content` returns a scalar, and under `Set-StrictMode -Version Latest` the later `$rawLines.Count` threw `property 'Count' cannot be found`. Pre-existing, but it crashes the new machine modes on a short or empty capture window.
- Wrapped both loaders in `@(Get-Content ..)` so `$rawLines` is always an array. An empty log now yields `{"count":0,"blocks":[]}` instead of a crash.

## 5. Validation

- `-Exceptions -Json` on a 3-block synthetic log -> `{"count":3,"blocks":[..]}`, valid JSON, no banner pollution, exit 0.
- Single block -> `blocks` is still a JSON array (`-AsArray` holds).
- No crashes -> `{"count":0,"blocks":[]}`; empty file -> same, exit 0 (no scalar crash).
- `-Exceptions -Count` -> `Match count: N`; the aggregator's `Match count:\s*(\d+)` regex extracts it.
- JSON round-trips through `ConvertFrom-Json`; `blocks` is an array of length `count`.
- Regression smoke on `-Errors -Count`, `-Summary`, `-Pattern -Context`: unchanged.

## 6. Known limitations

- `-Exceptions` still scans all raw lines and ignores `-From` / `-To` time scoping (pre-existing; `-Exceptions` operates on `$rawLines`, not the time-filtered set). Out of scope here.
- `trigger` reports the leftmost crash-pattern substring on the line, which can be the tag (e.g. `AndroidRuntime`) rather than the keyword (e.g. `FATAL EXCEPTION`). It documents what opened the block, not a classification.
- `-Exceptions` deliberately keeps its broad pattern set; benign system `Exception:` / `Caused by:` lines are still counted as blocks. Callers needing strict fatal-only counts filter on `trigger` or match raw markers themselves (as `prerelease-verdict.ps1` does).

## 7. Touched files

- `scripts/utils/search-log.ps1` - `-Json` param, banner gating, `-Exceptions` mode rewrite, loader `@()` guard, synopsis examples.

## Origin

- Parked via `/spec-draft` during S0484 section 6.5 research (log verdict markers). Evidence at the time: `scripts/utils/search-log.ps1:444-484` (`-Exceptions` returned early, stdout-only).

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (compact)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [ ] None. Functionally verified this audit: `-Exceptions -Json` -> valid `{"count":N,"blocks":[..]}` (parses, `blocks` is an array, no banner pollution); `-Exceptions -Count` -> `Match count: N`; empty log -> `{"count":0,"blocks":[]}` (no scalar crash). `-Json` param + `@(Get-Content)` guards present; dev log records the single touched file; 0 debug tags. (§8 FEATURES EXEMPT - internal tooling.)
