---
model: sonnet
---

# Verify - On-Device Sanity Check

> **GLOBAL DIRECTIVES (anti-bureaucracy):**
> 1. Dry technical prose, no filler.
> 2. Autonomy over bureaucracy: surface only critical findings. Do not edit specs or the journal.
> 3. Terse report: end with one line - verdict + scenario path.

Lightweight on-device smoke verification of a code change or behaviour claim. Builds (optional), installs (optional), launches the app, walks a minimal UI scenario via mobile-mcp, captures logcat, reports PASS/FAIL with evidence. Does NOT touch `PLAN/`, `dev/CHANGELOG.md`, or `PLAN/spec-catalog.jsonl`. Use for quick "does it actually work on a device" without the bureaucracy of `/spec-test-device`.

## When to use vs `/spec-test-device`

| Situation | Skill |
|-----------|-------|
| Spec-driven verification, update `## Last Audit`, append `## Revision History` | `/spec-test-device` |
| Quick "does this still launch?", "does the new dialog appear?", "any crash on this screen?" | `/verify` (this skill) |
| Lightweight smoke after `/quick` or `/skill-fix` | `/verify` |
| Pre-merge plateau check across multiple specs | `/spec-test-device` per spec, not `/verify` |

If a `Sxxxx` is the primary subject, prefer `/spec-test-device`. `/verify` is for the in-between cases.

## Usage

```text
/verify                                           # smoke: launch standard-debug, screenshot home, scan logcat for E-level
/verify <free-text describing what to check>      # author a scenario from the description
/verify <Sxxxx>                                   # read spec §11 criteria as scenario hints; do NOT patch the spec
/verify --build                                   # rebuild + reinstall before running (default: skip build)
/verify --flavor <standard|lite|photos|legacy|noLegal>   # default: standard
/verify --device <id>                             # pick adb id when multiple online
/verify --release                                 # release variant (avoid LeakCanary launcher on debug)
/verify --dry-run                                 # author scenario only, no device execution
```

Hard requirement: **mobile-mcp** server reachable. If `mcp__mobile-mcp__mobile_list_available_devices` unavailable, abort with `mobile-mcp not configured - ensure .mcp.json is present and Node.js / npx are installed`.

---

## Process

### 1 - Parse arguments

- First positional arg matching `^S\d{4}$` -> spec id; resolve via `scripts/spec_catalog/select.ps1` (read-only, never write).
- Otherwise treat all positional args as free-text scenario description.
- Empty args -> default smoke (launch, screenshot, crash scan).

### 2 - Pre-flight

One call:

```powershell
pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package <pkg> [-DeviceId <id>] -Json
```

`<pkg>`: debug variants `com.sza.fastmediasorter.debug`; release variants `com.sza.fastmediasorter`.

Exit codes from `device-ready.ps1`:

| Code | Meaning | Action |
|------|---------|--------|
| 0 | Ready | Continue |
| 1 | adb missing | Abort, ask user to install platform-tools |
| 2 | no online device | Abort, ask user to connect device / start AVD |
| 3 | multiple devices, no `-DeviceId` | Re-run with `--device <id>` |
| 4 | package not installed | If `--build` not given, suggest `/verify --build`; otherwise proceed (build will install) |
| 5 | version mismatch | Informational only - continue |
| 6 | mobile-mcp launcher not resolvable | Abort, point at `.mcp.json` + Node.js |

### 3 - Build + install (only when `--build`)

Builder matching the flavor:

| Flavor + variant | Script |
|---|---|
| `standard` debug device | `.\scripts\builders\build-standard-device.ps1` |
| Other flavor debug device | `.\scripts\builders\build-<flavor>-device.ps1` |
| `standard` release | `.\scripts\builders\build-standard-release.ps1` then `adb install -r <apk>` |

On build failure: capture last 80 lines of builder output into `temp/verify_<TS>.md` `## Build log` and abort. Do not proceed.

Skip this step when `--build` not set. Pre-flight will have validated the package is installed (or not, in which case ask user whether to rebuild).

### 4 - Author the scenario

Write `temp/verify_<TS>.md` with header (device id, model, density, flavor, package, versionName) and an ordered scenario.

Scenario sources, in priority order:

1. Free-text from user -> distil into 1..5 steps.
2. `Sxxxx §11 Criteria` -> map each criterion to one minimal interactive step (no `## Last Audit` editing). Skip criteria infeasible without external fixtures (mark `out-of-scope`).
3. No input -> default smoke:
   - Step 1: launch app, screenshot home.
   - Step 2: assert no crash dialog visible.
   - Step 3: scan logcat for `E/` lines from app package.

Each step has: `goal`, `mobile-mcp action(s)`, `expected screen text or element id`, optional `expected log line(s)`.

If `--dry-run`, stop here and report the path.

### 5 - Start log capture

```powershell
adb -s <id> logcat -c
adb -s <id> logcat -v time *:V > temp/verify_run_<TS>.log
```

Background process (streaming capture - keep raw `adb logcat` here; `adb.ps1 log` is a `-d`
one-shot dump, not a stream). Record start timestamp.

For out-of-loop chores prefer the swiss-army over raw `adb`: `scripts/devtest/adb.ps1 launch`
(debug: explicit MainActivity, dodges LeakCanary), `stop`, `clear`, `shot`, `props`
(`.\a.ps1 adb <verb>`).

### 6 - Execute the scenario via mobile-mcp

Walk each step:

1. `mobile_take_screenshot` -> `temp/verify_screens/step_NN_before.png`
2. `mobile_list_elements_on_screen` -> resolve element coordinates fresh (never reuse coords across runs).
3. Perform action (click / type / swipe / press button / open_url).
4. `mobile_take_screenshot` -> `step_NN_after.png`
5. Verify expected post-state.
6. Append one row to scenario `## Run log` table: `step | action | result | evidence`.

Crash detection:

- After every step run `mcp__mobile-mcp__mobile_get_crash` -> if non-empty, mark FAIL with the stack, screenshot, capture remaining logcat, stop the run.

### 7 - Stop log capture

Kill background `adb logcat` process. Record end timestamp.

### 8 - Log analysis

```powershell
.\scripts\utils\search-log.ps1 -LogFile "temp/verify_run_<TS>.log" -Errors -Unique -AppOnly
.\scripts\utils\search-log.ps1 -LogFile "temp/verify_run_<TS>.log" -Exceptions
```

If the scenario was built from an `Sxxxx` currently `BlockNeedUserTest`, additionally grep for the spec's debug verification tags:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "temp/verify_run_<TS>.log" -Pattern "<Sxxxx>:" -AppOnly
```

Each tag hit at `D/` level = "code path exercised". `I`/`W`/`E` lines containing the ticket id are instrumentation bugs (CLAUDE.md "Persistent log lines must not contain Sxxxx") - report them, but do not count as PASS evidence.

Append `## Log findings` to scenario file: counts per level, top 3 errors with line refs, any exception block.

### 9 - Final report

Single line:

```
verify: device <id>, PASS/FAIL/SKIPPED N/N/N, log errors N, crashes K. Scenario: temp/verify_<TS>.md
```

Recommended next-step (one line, optional):

- All PASS, no errors -> "OK to commit."
- Any FAIL -> "Suggest `/quick <one-line>` or `/spec-fix <Sxxxx>` to address: <one-sentence root cause>."
- Crash -> "Stack captured in scenario `## Crash`."

---

## Constraints

- **Read-only on journal.** Never call `update.ps1`, `complete.ps1`, `archive.ps1`. The journal `updated` timestamp does **not** move for `/verify`.
- **Read-only on specs.** Never touch `PLAN/Sxxxx_*.md`. `## Last Audit`, `## Revision History`, `[manual]` boxes owned by `/spec-test-device` / `/spec-check`.
- **No dev log.** `/verify` is a diagnostic, not a change. `dev/CHANGELOG.md` stays untouched. Skill-internal exception: if `/verify` is invoked as the final step of `/skill-fix` or `/quick`, the parent skill writes its own dev log entry; `/verify` itself still does not.
- **No commits.** `git status` / `git diff` may be inspected, but `git commit` is the user's call.
- **Outputs land in `temp/` only.** Screenshots, scenario, captured log - all under `temp/verify_*` and `temp/verify_screens/`. Never write to project root.
- **Never run `gradlew.bat` directly.** Always go through `scripts/builders/build-*-device.ps1`.
- **Never read full logcat into context** when > 2 MB - use `search-log.ps1`, quote line numbers.
- **Never hardcode element coordinates** - re-list elements before each click.
- **Read-only zones** `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/` ignored entirely.

---

## Known device traps

(Inherits from `/spec-test-device` - same LeakCanary launcher trap, density-shifting coordinates, share-sheet missing-FMS-entry, Snackbar disappearance. See `/spec-test-device` §"Known device traps".)

---

## Output artifacts

| Path | Purpose |
| --- | --- |
| `temp/verify_<TS>.md` | Scenario header + run log table + log findings + final verdict |
| `temp/verify_screens/step_NN_{before,after}.png` | Per-step evidence |
| `temp/verify_run_<TS>.log` | Captured logcat for the run window |

No other paths are written. No project files are modified.
