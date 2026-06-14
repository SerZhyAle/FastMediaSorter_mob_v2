# Specification Device Test Run

> **GLOBAL DIRECTIVES (anti-bureaucracy):**
> 1. Dry technical prose, no filler.
> 2. Autonomy over bureaucracy: don't block on minor issues; surface only critical findings.
> 3. Terse report: end with one line - verdict + scenario path.

End-to-end on-device verification: build -> install -> run UI scenario via mobile-mcp -> harvest logcat -> synthesize follow-ups -> patch strategic spec's `## Last Audit` Manual block. Writes a scenario file, screenshots, captured log under `temp/`. Does NOT edit codebase, does NOT flip strategic spec status (that is `/spec-check`).

## Usage

```text
/spec-test-device <Sxxxx-or-slug>
/spec-test-device <Sxxxx-or-slug> --flavor <standard|lite|photos|legacy>   # default: standard
/spec-test-device <Sxxxx-or-slug> --no-build                                # use installed APK
/spec-test-device <Sxxxx-or-slug> --no-install                              # build but don't install
/spec-test-device <Sxxxx-or-slug> --device <id>                             # specific adb id
/spec-test-device <Sxxxx-or-slug> --release                                 # release variant (avoid debug LeakCanary trap)
/spec-test-device <Sxxxx-or-slug> --dry-run                                 # scenario only, no execution
```

Hard requirement: **mobile-mcp** server reachable. Project ships `.mcp.json` registering `@mobilenext/mobile-mcp@latest` via npx; if Node.js / npx missing, abort with `mobile-mcp not configured - ensure .mcp.json is present and Node.js / npx are installed`.

Before any device interaction, run pre-flight:

```powershell
pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package <pkg> [-DeviceId <id>] [-CheckMcp] -Json
```

`<pkg>`: `com.sza.fastmediasorter.debug` (debug) / `com.sza.fastmediasorter` (release). Exit codes 1..6 are abort signals - see `scripts/devtest/device-ready.ps1` header table.

## Status gate

| Strategic `Status:` | Allowed? | Why |
| --- | :---: | --- |
| `Draft` / `Approved` | ⛔ | No implementation to test |
| `Tactical` / `In Progress` | ✅ | Dev-time integration smoke |
| `Implemented` | ✅ | Pre-`/spec-check` validation |
| `Verified` | ✅ (regression) | Re-prove after later changes |
| `Partial` / `Broken` | ✅ | Confirm whether reported gaps reproduce |
| `BlockNeedUserTest` | ✅ (primary use case) | Exactly what the block expects |
| `BlockByOtherTask` / `BlockQuestions` / `BlockExternal` | ⛔ | Resolve the block first |
| `Archived` | ⛔ | Historical |

If gate refuses, abort with one line: current status + next allowed action.

---

## Process

### 1 - Parse arguments, resolve spec

```powershell
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
```

Locate strategic spec at `PLAN/Sxxxx_<slug>.md`. Record presence of `PLAN/Sxxxx_<slug>/INDEX.md`. Apply status gate.

### 2 - Build the scenario file

Read strategic spec §2 (Goals), §11 (Criteria), §3.2 (Constraints). If tactical INDEX exists, read every phase file's "Manual" steps (any step with `[manual` token, or any "smoke list" section). Distil into:

- **Coverage map** - table mapping every §11 criterion + every manual checklist item to: `automatable`, `partial`, or `out-of-scope`. Mark anything needing external fixtures (controlled HTTP server, custom redirect, contrived HTML) `out-of-scope` with one-line reason.
- **Scenario steps** - ordered list. Each step: `goal`, `mobile-mcp action(s)`, `expected screen text or element id`, `expected log line(s)` (if any). Atomic - one user-visible state change per step.
- **Pre-conditions** - required app state (e.g. specific resource registered, specific setting OFF). If unmeetable without manual setup, list them and stop before execution.

Write to `temp/<Sxxxx>_mobile_test_scenario_<YYYYMMDD_HHmm>.md`. Include a `## Run log` placeholder section.

If `--dry-run`, stop here and report the path.

### 3 - Device readiness

```text
mcp__mobile-mcp__mobile_list_available_devices
```

- Zero online devices -> abort: `no online device - connect a device or start an emulator`.
- Multiple + no `--device` -> list them, ask user to pick.
- One online -> use it.

Sanity-check chosen device:

```powershell
adb -s <id> shell getprop ro.build.version.release
adb -s <id> shell getprop ro.product.model
adb -s <id> shell wm size
adb -s <id> shell wm density
```

Record device profile in scenario file header.

### 4 - Build + install

Skip if `--no-build`.

Decide flavor:
- `--flavor` if given, else default `standard`.
- `--release` -> release variant (avoids debug-flavor LeakCanary launcher pre-empting `mobile_launch_app`); otherwise debug.

Run the builder script (never invoke `gradlew.bat` directly):

| Combo | Script |
| --- | --- |
| `standard` + debug + install | `.\scripts\builders\build-standard-device.ps1` |
| Other flavor + debug + install | `.\scripts\builders\build-<flavor>-device.ps1` |
| `standard` + release | `.\scripts\builders\build-standard-release.ps1` then `adb install -r <apk>` |

On build failure: capture last 80 lines of builder log into scenario `## Run log` and abort. Do not proceed to UI execution.

After install (`--no-install` skips this), launch the app:
- Release builds: `mobile_launch_app packageName=com.sza.fastmediasorter`.
- Debug builds: `com.sza.fastmediasorter.debug` lands in **LeakCanary** because `:leakcanary-android` registers its own launcher activity. Two options:
  1. (Preferred) Use `--release` so launch is unambiguous.
  2. Launch explicit activity: `adb -s <id> shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity` and skip `mobile_launch_app`.

Confirm runtime build identifier matches just-built - read Settings footer (`tvVersionInfo` element id) or `adb shell dumpsys package <pkg> | grep versionName`. Mismatch -> abort with `installed APK does not match build`.

### 5 - Start log capture

```powershell
adb -s <id> logcat -c
adb -s <id> logcat -v time *:V > temp/<Sxxxx>_run_<YYYYMMDD_HHmm>.log
```

Run logcat in **background** (`run_in_background: true`). Record start timestamp in scenario.

### 6 - Execute scenario via mobile-mcp

Token discipline: `mobile_take_screenshot` returns the image **into context**, and every inline image is re-sent on every later turn - across a multi-step scenario this is the dominant token cost. Drive targeting and verification from the text accessibility tree; never screenshot just to find or check an element (the tool itself instructs: for anything in the view hierarchy use `mobile_list_elements_on_screen` instead).

Walk scenario steps in order. For each step:

1. `mobile_list_elements_on_screen` -> single source of truth for this step: resolve target coordinates from element id/label AND read current state. NEVER hard-code coordinates from a previous run - densities and dynamic layouts shift them.
2. Perform action (`click`, `type_keys`, `swipe`, `press_button`, `open_url`) using coordinates from step 1.
3. `mobile_list_elements_on_screen` again -> verify expected post-state from the text tree:
   - target element present / absent
   - element text matches expected substring
   - Toast / Snackbar text appears (re-list within 2 s - toasts auto-dismiss)
4. Evidence: `mobile_save_screenshot saveTo=temp/<Sxxxx>_screens/step_<NN>.png` - writes a PNG to disk, does NOT load the image into context. One per step at zero token cost.
5. Record one row in scenario `## Run log` table: `step | action | result (PASS/FAIL/INCONCLUSIVE) | evidence (screenshot path + log lines)`.

Use `mobile_take_screenshot` (inline image, costs context) ONLY when the text tree cannot answer:
- a11y tree empty / non-semantic (custom-rendered surfaces, games, `SurfaceView`/`GLSurfaceView`, WebView internals, Compose without `Modifier.semantics`);
- a purely visual assertion (colour, layout, rendered image/thumbnail content) the tree cannot express;
- diagnosing a FAIL whose cause is not visible in the tree.
One inline screenshot, then return to the text tree. For flows needing many visual states, prefer `mobile_start_screen_recording` / `mobile_stop_screen_recording` (video saved to file, zero context) over a burst of `take_screenshot`.

External Share-sheet roundtrip (e.g. share a URL from Chrome to FMS):

```text
mobile_open_url url=<https-url>
# then via mobile-mcp: open Chrome overflow → Share → pick FMS
```

Never hardcode share-sheet coordinates - resolve via `mobile_list_elements_on_screen`.

If a step is `out-of-scope` per coverage map, skip and record `SKIPPED (out-of-scope)`.

### 7 - Stop log capture

Kill background `adb logcat` process. Record end timestamp.

### 8 - Research log

Run log analyser against captured file:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "temp/<Sxxxx>_run_<TS>.log" -Summary
.\scripts\utils\search-log.ps1 -LogFile "temp/<Sxxxx>_run_<TS>.log" -Errors -Unique -Stats -AppOnly
.\scripts\utils\search-log.ps1 -LogFile "temp/<Sxxxx>_run_<TS>.log" -Exceptions
```

First grep for the spec's own debug verification tags - the primary "code path exercised" signal (spec is in `BlockNeedUserTest`, so per CLAUDE.md "Debug Verification Tags" it carries `Timber.d("<Sxxxx>: …")` lines). Only debug-level hits count as valid probes; `I/W/E` lines with `<Sxxxx>:` are invalid instrumentation - report separately, do not count as exercised:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "temp/<Sxxxx>_run_<TS>.log" -Pattern "<Sxxxx>:" -AppOnly
```

For every `<Sxxxx>:` line found, mark the corresponding criterion / changed flow **exercised on-device** in `## Log findings`. A flow whose tag never appeared = not exercised by the scenario (note as coverage gap, not necessarily FAIL).

Additionally grep for tags of spec classes. Resolve class names from catalog:

```powershell
pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -PathMatches "*<spec slug fragment>*" -Format json
```

For each class returned:

```powershell
.\scripts\utils\search-log.ps1 -LogFile "temp/<Sxxxx>_run_<TS>.log" -Tag "<ClassName>" -AppOnly
```

Append `## Log findings`: counts per level, top error messages with line refs, any exception block. Cross-reference each finding to the scenario step running at that timestamp (use per-step start time from step 6).

### 9 - Synthesize follow-ups

Based on FAILs and log errors, draft (do NOT auto-execute) a list of follow-up actions. Each item picks one:

- **`/quick <one-line task>`** - single-file cosmetic fixes (typo, wrong dimen, missing string, wrong colour).
- **`/spec <Sxxxx> <name>-v2`** - feature-level gaps needing a fresh spec ticket.
- **`/spec-update <Sxxxx>`** - text-only spec corrections (wrong file pointer, missing constraint).
- **`/spec-fix <Sxxxx>`** - code change to close an open `## Last Audit` action item.

Append to scenario as `## Recommended follow-ups`. Do not run them - surface as a punch list for the user.

### 10 - Update existing strategic spec

Open `PLAN/Sxxxx_<slug>.md`, find `## Last Audit` block. Inside it, locate the `### Manual / on-device` checklist (created by `/spec-check`).

For every checklist item this run actually exercised:
- `PASS` -> flip `[ ]` to `[x]`, append ` - verified on-device <YYYY-MM-DD>`.
- `FAIL` -> flip `[ ]` to `[!]` (custom marker the audit recognises), append ` - failed on-device <YYYY-MM-DD>; see temp/<Sxxxx>_mobile_test_scenario_<TS>.md`.
- `SKIPPED` / `INCONCLUSIVE` -> leave line unchanged.

Append one line to spec's `## Revision History` (create section if missing):

```markdown
- **<YYYY-MM-DD>** - by `/spec-test-device` (`<model-id>`, device: <id> <Android version>)
  - Scenario: temp/<Sxxxx>_mobile_test_scenario_<TS>.md · PASS/FAIL/SKIPPED N/N/N · Errors in log: N
```

Touch journal `updated` **without changing status**:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx>
```

### 11 - Dev log

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<slug>.md" "spec-test-device" "Device run on <device-id> -> PASS/FAIL/SKIPPED N/N/N"
```

If strategic spec was untouched (zero checklist items recognised), still record the run with scenario file path as target.

### 12 - Final report

Single line:

```
<Sxxxx>: device <id>, PASS/FAIL/SKIPPED N/N/N, log errors N. Scenario: temp/<Sxxxx>_mobile_test_scenario_<TS>.md
```

Then a one-line follow-up offer if `/spec-fix` or a fresh `/spec` is the obvious next step.

---

## Constraints

- Never write outside `temp/` except the targeted strategic spec MD file.
- Never auto-invoke `/spec`, `/spec-fix`, `/quick` - only surface as recommendations. Skill chaining is the user's call.
- Never flip `Status:` of strategic spec - `/spec-check` owns that. Only `updated` moves.
- Never run `gradlew.bat` directly - go through `scripts/builders/*-device.ps1` (matches `/build`).
- Never read full logcat into context for runs > 2 MB - use `search-log.ps1`, quote line numbers only.
- Never hard-code element coordinates - resolve via `mobile_list_elements_on_screen` immediately before each click. Densities, system bars, dynamic content shift positions across runs.
- Never click without listing elements first - resolve every target from `mobile_list_elements_on_screen` immediately before the click; silent clicks on stale coordinates produce false PASSes.
- Prefer `mobile_save_screenshot` (writes a file) over `mobile_take_screenshot` (loads the image into context) for evidence. Reserve inline `take_screenshot` for the step-6 fallback cases - inline images accumulate and re-send every turn, dominating token cost.
- Never edit `PLAN/spec-catalog.jsonl` directly - only via `update.ps1`.
- Read-only zones - `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/` - ignored.
- Device runs `Android < minSdk` for chosen flavor -> abort with version mismatch.
- `mobile-mcp` errors mid-run -> dump partial run log to scenario file before re-raising.

---

## Known device traps

| Symptom | Cause | Workaround |
| --- | --- | --- |
| `mobile_launch_app` opens **LeakCanary** instead of FMS | Debug build registers LeakCanary's `LeakActivity` as launcher | Use `--release`, or `adb shell am start -n <pkg>/com.sza.fastmediasorter.ui.main.MainActivity` |
| Settings footer shows older `versionName` than just-built APK | `adb install` failed silently or wrong `-s <id>` | Re-install with `adb install -r -t -d <apk>`, re-check footer |
| `mobile_click_on_screen_at_coordinates` lands on adjacent button | Coordinates from previous run reused; layout shifted | Always re-list elements before each click |
| Tab switch click does nothing | Tap landed below tab strip but inside inactive content area | Increase `y` to centre of tab `LinearLayout` (often y ≈ 460 on 1440×2880, varies per density) |
| Toast / Snackbar gone before it can be read | 2-second auto-dismiss | Re-list elements within 1 s of the action; if still missed, mark `INCONCLUSIVE`, rely on logcat trace |
| Share-sheet missing FMS entry | `acceptSharedFiles` setting OFF or `ReceiveShareActivity` component-disabled | Toggle `Settings → Playback → Default Media Player → Accept shared files` ON before run |

---

## Spec Catalog hooks

- **Argument resolution.** First positional arg is `Sxxxx` (preferred) or a slug.
- **Status transition.** Never. Only `updated` touched (`pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx>`).
- **Forbidden:** writing to `PLAN/spec-catalog.jsonl` directly; deleting screenshots from previous runs (they accumulate in `temp/<Sxxxx>_screens/`, may be useful for diff).

---

## Output artifacts

| Path | Purpose |
| --- | --- |
| `temp/<Sxxxx>_mobile_test_scenario_<TS>.md` | Scenario + run log + log findings + follow-ups |
| `temp/<Sxxxx>_screens/step_<NN>.png` | Per-step evidence (via `mobile_save_screenshot`, off-context) |
| `temp/<Sxxxx>_run_<TS>.log` | Captured logcat for the run window |
| `PLAN/Sxxxx_<slug>.md` | Strategic spec - `## Last Audit` Manual block updated, `## Revision History` line appended |
| `dev/CHANGELOG.md` | One row via `add_to_dev_log.ps1` |
