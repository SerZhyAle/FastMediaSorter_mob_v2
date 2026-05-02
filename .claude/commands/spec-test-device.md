# Specification Device Test Run

> **GLOBAL EXECUTION DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **STRICTLY TECHNICAL LANGUAGE:** Dry technical prose, no filler.
> 2. **AUTONOMY OVER BUREAUCRACY:** Do not block on minor issues. Surface only critical findings.
> 3. **TERSE REPORTING:** End with one line — verdict + path to scenario file.

End-to-end on-device verification of a spec: build → install → run UI scenario via mobile-mcp → harvest logcat → synthesize follow-ups → patch strategic spec's `## Last Audit` Manual block. The skill writes a scenario file under `temp/`, screenshots, and a captured log; it does **not** edit the codebase and does **not** flip the strategic spec status (that is `/spec-check`'s job).

## Usage

```text
/spec-test-device <Sxxxx-or-slug>
/spec-test-device <Sxxxx-or-slug> --flavor <standard|lite|photos|legacy>   # default: standard
/spec-test-device <Sxxxx-or-slug> --no-build                                # use whatever APK is installed
/spec-test-device <Sxxxx-or-slug> --no-install                              # build but don't install
/spec-test-device <Sxxxx-or-slug> --device <id>                             # pick specific adb id
/spec-test-device <Sxxxx-or-slug> --release                                 # use release variant (avoid debug LeakCanary trap)
/spec-test-device <Sxxxx-or-slug> --dry-run                                 # generate scenario only, no execution
```

Hard requirement: the **mobile-mcp** server must be reachable. If `mcp__mobile-mcp__mobile_list_available_devices` is not available, abort with `mobile-mcp not configured — enable the MCP server first`.

## Status gate

| Strategic `Status:` | Allowed? | Why |
| --- | :---: | --- |
| `Draft` / `Approved` | ⛔ | No implementation to test |
| `Tactical` / `In Progress` | ✅ | Dev-time integration smoke |
| `Implemented` | ✅ | Pre-`/spec-check` validation |
| `Verified` | ✅ (regression) | Re-prove after later changes |
| `Partial` / `Broken` | ✅ | Confirm whether reported gaps reproduce |
| `BlockNeedUserTest` | ✅ (primary use case) | This is exactly what the block expects |
| `BlockByOtherTask` / `BlockQuestions` / `BlockExternal` | ⛔ | Resolve the block first |
| `Archived` | ⛔ | Historical |

If the gate refuses, abort with one line stating the current status and the next allowed action.

---

## Process

### 1 — Parse arguments, resolve spec

```powershell
pwsh -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
```

Locate strategic spec at `PLAN/Sxxxx_<slug>.md`. Record presence of `PLAN/Sxxxx_<slug>/INDEX.md`. Apply status gate.

### 2 — Build the scenario file

Read the strategic spec §2 (Goals), §11 (Criteria), §3.2 (Constraints). If a tactical INDEX exists, read every phase file's "Manual" steps (any step with `[manual` token, or any "smoke list" section). Distil into:

- **Coverage map** — table mapping every §11 criterion + every manual checklist item to one of: `automatable`, `partial`, `out-of-scope`. Mark anything requiring external fixtures (controlled HTTP server, custom redirect, contrived HTML) as `out-of-scope` with a one-line reason.
- **Scenario steps** — ordered list. Each step has: `goal`, `mobile-mcp action(s)`, `expected screen text or element id`, `expected log line(s)` (if any). Steps are atomic — one user-visible state change per step.
- **Pre-conditions** — required app state (e.g. specific resource registered, specific setting OFF). If pre-conditions cannot be met without manual setup, list them and stop before execution.

Write to `temp/<Sxxxx>_mobile_test_scenario_<YYYYMMDD_HHmm>.md`. Include a `## Run log` placeholder section.

If `--dry-run`, stop here and report the path.

### 3 — Device readiness

```text
mcp__mobile-mcp__mobile_list_available_devices
```

- Zero online devices → abort: `no online device — connect a device or start an emulator`.
- Multiple + no `--device` → list them, ask user to pick.
- One online → use it.

Sanity-check the chosen device:

```powershell
adb -s <id> shell getprop ro.build.version.release
adb -s <id> shell getprop ro.product.model
adb -s <id> shell wm size
adb -s <id> shell wm density
```

Record device profile in the scenario file's header.

### 4 — Build + install

Skip if `--no-build`.

Decide flavor:
- `--flavor` if given, else default `standard`.
- `--release` chooses release variant (avoids the debug-flavor LeakCanary launcher pre-empting `mobile_launch_app`); otherwise debug.

Run the appropriate builder script (do NOT invoke `gradlew.bat` directly):

| Combo | Script |
| --- | --- |
| `standard` + debug + install | `.\scripts\builders\build-standard-device.ps1` |
| Other flavor + debug + install | `.\scripts\builders\build-<flavor>-device.ps1` |
| `standard` + release | `.\scripts\builders\build-standard-release.ps1` then `adb install -r <apk>` |

On build failure: capture last 80 lines of the builder log into the scenario file's `## Run log` and abort. Do not proceed to UI execution.

After install (`--no-install` skips this), launch the app:
- For release builds: `mobile_launch_app packageName=com.sza.fastmediasorter`.
- For debug builds: `com.sza.fastmediasorter.debug` lands in **LeakCanary** because the `:leakcanary-android` artifact registers its own launcher activity. Two options:
  1. (Preferred) Use `--release` so launch is unambiguous.
  2. Launch the explicit activity: `adb -s <id> shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity` and skip `mobile_launch_app`.

Confirm runtime build identifier matches what was just built — read the Settings footer (`tvVersionInfo` element id) or query `adb shell dumpsys package <pkg> | grep versionName`. If mismatch → abort with `installed APK does not match build`.

### 5 — Start log capture

```powershell
adb -s <id> logcat -c
adb -s <id> logcat -v time *:V > temp/<Sxxxx>_run_<YYYYMMDD_HHmm>.log
```

Run the logcat command in the **background** (`run_in_background: true`). Record the start timestamp in the scenario.

### 6 — Execute scenario via mobile-mcp

Walk the scenario steps in order. For each step:

1. `mobile_take_screenshot` → save to `temp/<Sxxxx>_screens/step_<NN>_before.png`.
2. Read on-screen elements via `mobile_list_elements_on_screen` to resolve coordinates from element ids (NEVER hard-code coordinates from a previous run — densities and dynamic layouts shift them).
3. Perform the action (`click`, `type_keys`, `swipe`, `press_button`, `open_url`).
4. `mobile_take_screenshot` → `step_<NN>_after.png`.
5. Verify expected post-state:
   - Element id present / absent
   - Element text matches expected substring
   - Toast / Snackbar text appears (re-screenshot within 2 s)
6. Record one row in the scenario's `## Run log` table: `step | action | result (PASS/FAIL/INCONCLUSIVE) | evidence (screenshot path + log lines)`.

If a step needs an external Share-sheet roundtrip (e.g. share a URL from Chrome to FMS), drive it via:

```text
mobile_open_url url=<https-url>
# then via mobile-mcp: open Chrome overflow → Share → pick FMS
```

Avoid hardcoding share-sheet coordinates — always resolve via `mobile_list_elements_on_screen`.

If a step is `out-of-scope` per the coverage map, skip and record `SKIPPED (out-of-scope)`.

### 7 — Stop log capture

Kill the background `adb logcat` process. Record end timestamp.

### 8 — Research log

Run the project's log analyser against the captured file:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "temp/<Sxxxx>_run_<TS>.log" -Summary
.\scripts\utils\search-log.ps1 -LogFile "temp/<Sxxxx>_run_<TS>.log" -Errors -Unique -Stats -AppOnly
.\scripts\utils\search-log.ps1 -LogFile "temp/<Sxxxx>_run_<TS>.log" -Exceptions
```

Additionally grep for tags belonging to spec classes. Resolve class names from the catalog:

```powershell
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -PathMatches "*<spec slug fragment>*" -Format json
```

For each class returned, run:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "temp/<Sxxxx>_run_<TS>.log" -Tag "<ClassName>" -AppOnly
```

Append a `## Log findings` section to the scenario file: counts per level, top error messages with line refs, and any exception block found. Cross-reference each finding to the scenario step that was running at that timestamp (use the per-step start time recorded in step 6).

### 9 — Synthesize follow-ups

Based on FAILs and log errors, draft (do NOT auto-execute) a list of follow-up actions. Each item picks one of:

- **`/quick <one-line task>`** — single-file cosmetic fixes (typo, wrong dimen, missing string, wrong colour).
- **`/spec <Sxxxx> <name>-v2`** — feature-level gaps that need a fresh spec ticket.
- **`/spec-update <Sxxxx>`** — text-only spec corrections (e.g. wrong file pointer, missing constraint).
- **`/spec-fix <Sxxxx>`** — code change to close an open `## Last Audit` action item.

Append to the scenario file as `## Recommended follow-ups`. Do not run them — surface them as a punch list for the user to invoke.

### 10 — Update existing strategic spec

Open `PLAN/Sxxxx_<slug>.md`, find its `## Last Audit` block. Inside that block, locate the `### Manual / on-device` checklist (created by `/spec-check`).

For every checklist item that this run actually exercised:
- `PASS` → flip `[ ]` to `[x]` and append ` — verified on-device <YYYY-MM-DD>` to the line.
- `FAIL` → flip `[ ]` to `[!]` (custom marker the audit recognises) and append ` — failed on-device <YYYY-MM-DD>; see temp/<Sxxxx>_mobile_test_scenario_<TS>.md`.
- `SKIPPED` / `INCONCLUSIVE` → leave the line unchanged.

Append a one-line entry to the spec's `## Revision History` (create the section if missing):

```markdown
- **<YYYY-MM-DD>** — by `/spec-test-device` (`<model-id>`, device: <id> <Android version>)
  - Scenario: temp/<Sxxxx>_mobile_test_scenario_<TS>.md · PASS/FAIL/SKIPPED N/N/N · Errors in log: N
```

Touch the journal `updated` timestamp **without changing status**:

```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx>
```

### 11 — Dev log

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<slug>.md" "spec-test-device" "Device run on <device-id> -> PASS/FAIL/SKIPPED N/N/N"
```

If the strategic spec was untouched (zero checklist items recognised), still record the run with the scenario file path as the target.

### 12 — Final report

Single line:

```
<Sxxxx>: device <id>, PASS/FAIL/SKIPPED N/N/N, log errors N. Scenario: temp/<Sxxxx>_mobile_test_scenario_<TS>.md
```

Then a one-line follow-up offer if `/spec-fix` or a fresh `/spec` is the obvious next step.

---

## Constraints

- **Never write outside `temp/`** except for the targeted strategic spec MD file.
- **Never auto-invoke `/spec`, `/spec-fix`, `/quick`** — only surface them as recommendations. Skill chaining is the user's call.
- **Never flip `Status:`** of the strategic spec — `/spec-check` owns that transition. Only `updated` moves.
- **Never run `gradlew.bat` directly** — always go through `scripts/builders/*-device.ps1` (matches `/build` policy).
- **Never read full logcat into context** for runs > 2 MB — use `search-log.ps1` and quote line numbers only.
- **Never hard-code element coordinates** — always resolve via `mobile_list_elements_on_screen` immediately before each click. Densities, system bars, and dynamic content shift positions across runs.
- **Never click without a screenshot first** — silent clicks on unknown layouts produce false PASSes.
- **Never edit `PLAN/spec-catalog.jsonl`** directly — only via `update.ps1`.
- **Read-only zones** — `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/` — ignored.
- If the device runs `Android < minSdk` for the chosen flavor → abort with the version mismatch.
- If `mobile-mcp` returns an error mid-run, dump the partial run log to the scenario file before re-raising.

---

## Known device traps

| Symptom | Cause | Workaround |
| --- | --- | --- |
| `mobile_launch_app` opens **LeakCanary** instead of FMS | Debug build registers LeakCanary's `LeakActivity` as a launcher | Use `--release`, or `adb shell am start -n <pkg>/com.sza.fastmediasorter.ui.main.MainActivity` |
| Settings footer shows older `versionName` than just-built APK | `adb install` failed silently or wrong `-s <id>` | Re-install with `adb install -r -t -d <apk>`, re-check footer |
| `mobile_click_on_screen_at_coordinates` lands on adjacent button | Coordinates from a previous run reused; layout shifted | Always re-list elements before each click |
| Tab switch click does nothing | Tap landed below the tab strip but inside the inactive content area | Increase `y` to the centre of the tab `LinearLayout` (often y ≈ 460 on 1440×2880, varies per density) |
| Toast / Snackbar disappears before screenshot | 2-second auto-dismiss | Re-screenshot within 1 s of the action; if still missed, mark `INCONCLUSIVE` and rely on logcat trace |
| Share-sheet missing FMS entry | `acceptSharedFiles` setting OFF or `ReceiveShareActivity` component-disabled | Toggle `Settings → Playback → Default Media Player → Accept shared files` ON before the run |

---

## Spec Catalog hooks

- **Argument resolution.** First positional arg is `Sxxxx` (preferred) or a slug.
- **Status transition.** Never. Only `updated` is touched (`pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx>`).
- **Forbidden:** writing to `PLAN/spec-catalog.jsonl` directly; deleting screenshots from previous runs (they accumulate in `temp/<Sxxxx>_screens/` and may be useful for diff).

---

## Output artifacts

| Path | Purpose |
| --- | --- |
| `temp/<Sxxxx>_mobile_test_scenario_<TS>.md` | Scenario + run log + log findings + follow-ups |
| `temp/<Sxxxx>_screens/step_<NN>_{before,after}.png` | Per-step evidence |
| `temp/<Sxxxx>_run_<TS>.log` | Captured logcat for the run window |
| `PLAN/Sxxxx_<slug>.md` | Strategic spec — `## Last Audit` Manual block updated, `## Revision History` line appended |
| `dev/CHANGELOG.md` | One row via `add_to_dev_log.ps1` |
