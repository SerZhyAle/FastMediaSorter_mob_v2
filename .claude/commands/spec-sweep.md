# Spec Sweep - Batch Device-Test of BlockNeedUserTest Tickets

> **GLOBAL EXECUTION DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **STRICTLY TECHNICAL LANGUAGE:** Dry technical prose, no filler.
> 2. **AUTONOMY OVER BUREAUCRACY:** Do not block on minor issues. Surface only critical findings.
> 3. **TERSE REPORTING:** End with one batch verdict line + path to the sweep report.

On-demand batch device-test sweep over every active `BlockNeedUserTest` ticket that can be honestly verified on a connected emulator / device. This skill is the operational execution of strategic spec **S0307** (`PLAN/S0307_emulator-user-test-sweep.md`) - run it from time to time to drain the manual-verification backlog.

Per eligible ticket it delegates the heavy lifting to the single-ticket runners: `/spec-test-device <Sxxxx>` (full evidence) → `/spec-check <Sxxxx>` (evidence → final status). The sweep itself owns only discovery, exclusion filtering, classification, batch ordering, and the roll-up report.

## Usage

```text
/spec-sweep                                  # all eligible non-VR BlockNeedUserTest tickets
/spec-sweep S0054 S0186 S0207                # restrict to an explicit id subset
/spec-sweep --flavor <standard|lite|photos|legacy>   # default: per-ticket flavor, baseline standard
/spec-sweep --device <id>                    # pin a specific adb id for the whole sweep
/spec-sweep --limit <N>                      # process at most N eligible tickets this run
/spec-sweep --dry-run                        # discovery + classification + plan only, no build/install/UI
```

Hard requirement: the **mobile-mcp** server must be reachable and a device online (same gate as `/spec-test-device`). If the device gate fails, the sweep aborts before touching any ticket.

## Scope (inherited from S0307)

- **In:** non-VR / non-3D tickets currently in `BlockNeedUserTest` whose acceptance is reproducible on an emulator with locally-copied or generated fixtures.
- **Out:** VR / 3D / OpenXR / Quest-only / headset-only / immersive tickets; defect fixes (this sweep verifies, it does not repair); release signing / publishing; real OAuth / cloud secrets.
- **Never:** request or record real tokens, passwords, account credentials, or private keys; never fake a successful auth flow without evidence.

---

## Process

### 1 - Discovery (always fresh)

Read the **current** catalog - the seed list in S0307 §4/§10 is a snapshot, never a source of truth:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/search.ps1 -Status BlockNeedUserTest -Format json
```

If id arguments were passed, intersect the result with that subset (warn on any argument id that is not currently `BlockNeedUserTest` and drop it).

### 2 - Exclusion filter (VR / 3D)

Drop any ticket flagged VR/3D/immersive. Apply a double filter:

- Known current exclusions from S0307 §10: `S0249`, `S0291` (re-confirm against catalog each run, do not hard-code beyond a fallback).
- Keyword scan over the ticket name + strategic spec body + roadmap link: `VR`, `3D`, `OpenXR`, `Quest`, `headset`, `immersive`, `composition layer`, `SBS`, `OU`, `single eye`. A hit on a clearly stereoscopic/headset feature → excluded.

Uncertain cases go to a **review bucket** (listed in the report), never silently into pass/fail.

### 3 - Device pre-flight

```powershell
pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug [-DeviceId <id>] -CheckMcp -Json
```

Exit-code handling (see script header table):
- `0` → record device identity (API level, ABI, model, screen, density) into the report header; proceed.
- `2` (no online device) / `1` (adb missing) / `6` (mobile-mcp unresolvable) → **abort the whole sweep** with one line stating the reason. No ticket is touched.
- `3` (multiple devices, no `--device`) → abort and ask the user to pass `--device <id>`.

### 4 - Classify each eligible ticket

Read each ticket's current acceptance source (strategic §2/§11, `## Last Audit` Manual block, tactical phase manual steps). Assign exactly one class:

- **direct emulator** - verifiable with local files, system intents, app settings, UI actions. → full run in step 5.
- **emulator with local service** - needs FTP/SFTP/SMB/HTTP/local webhook that can be raised safely on localhost. → raise the service if trivially possible, else record a blocker.
- **external dependency** - needs real OAuth / cloud provider / installed third-party app / account state / hardware. → no run; set `BlockExternal` with reason; never inject secrets.
- **not testable by copied fixtures** - acceptance depends on conditions that cannot be honestly reproduced on emulator without expanding scope. → leave in `BlockNeedUserTest` with a justified blocker note; record in report.

If `--dry-run`: stop here. Write the classification + route matrix to the report and exit.

### 5 - Per-ticket evidence run (direct emulator + reproducible local-service)

For each ticket in priority order (highest catalog `priority` first), run the full single-ticket pipeline:

1. `/spec-test-device <Sxxxx>` - inherit `--flavor` / `--device` from sweep args. This builds, installs, drives the mobile-mcp scenario, harvests logcat (including the spec's own `Timber.d("Sxxxx:` probes - the spec is `BlockNeedUserTest`, so the tags exist), and patches the ticket's `## Last Audit` Manual block with PASS/FAIL/expected-actual. It does **not** flip status.
2. `/spec-check <Sxxxx>` - audit the now-evidenced ticket and flip status to `Verified` / `Partial` / `Broken`. On the `Verified` transition `/spec-check` removes the `Timber.d("Sxxxx:` tags (CLAUDE.md "Debug Verification Tags"); the sweep never touches those tags itself.

Idempotency: one ticket per inner pipeline; a failure in one ticket's run is recorded and the sweep continues with the next. Honor `--limit`.

For **external dependency** / **not testable** tickets, do not run the inner pipeline - set the verdict status directly via catalog tooling:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status <BlockExternal|BlockQuestions>
```

A status leaving `BlockNeedUserTest` (including these block transitions) **must** be paired with grep-and-delete of that spec's `Timber.d("Sxxxx:` tags from `.kt`, committed together (CLAUDE.md "Debug Verification Tags"). `/spec-check` handles this for the runs in step 5.1; for the direct `update.ps1` transitions here the sweep performs the grep-and-delete itself.

### 6 - Cleanup

Remove temporary fixtures and emulator state pushed during the sweep, unless a ticket explicitly needs preserved state for a follow-up round (record the exception in the report). Leave no long-lived services or background jobs running.

### 7 - Sweep report

Write `temp/spec-sweep_<YYYYMMDD_HHmm>/report.md` (per-run timestamped dir; sub-artifacts from `/spec-test-device` stay under their own `temp/<Sxxxx>_*` paths and are linked, not duplicated). Group by verdict:

- **Closed** (`Verified`): id, slug, tested flavor, evidence path.
- **Reopened** (`Broken` / `Partial`): id, slug, one-line defect summary, evidence path.
- **External / blocked** (`BlockExternal` / `BlockQuestions`): id, slug, blocker reason.
- **Still `BlockNeedUserTest`** (not testable): id, slug, why.
- **Excluded VR/3D:** id list.
- **Review bucket:** uncertain-classification ids needing a human call.

Header records: sweep timestamp, device identity, total `BlockNeedUserTest` at start, eligible count, processed count.

### 8 - Dev log (once per sweep)

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/S0307_emulator-user-test-sweep.md" "spec-sweep" "Batch device sweep on <device-id>: closed N, reopened M, blocked K, excluded V"
```

Functionality log and feature docs are **not** touched - this is an internal verification workflow (S0307 §8), and any user-visible verdict belongs to the individual ticket's own `/spec-check` finalization.

### 9 - Final report line

```text
spec-sweep: device <id>, eligible E, closed N, reopened M, blocked K, excluded V. Report: temp/spec-sweep_<TS>/report.md
```

---

## Constraints

- **Never write outside `temp/`** except the targeted ticket spec MD files (delegated to `/spec-test-device` and `/spec-check`).
- **Never flip a ticket status by name or assumption** - only by evidence, only via catalog tooling, only after the inner pipeline ran (or a justified block reason for the no-run classes).
- **Never edit `PLAN/spec-catalog.jsonl`** directly.
- **Never run `gradlew.bat` directly** - the inner `/spec-test-device` goes through `scripts/builders/*-device.ps1`.
- **Never collect secrets** and never fake a successful OAuth/cloud flow.
- **Never touch VR/3D/headset-only tickets.**
- **Never fix defects** found during the sweep - reopen the ticket (`Broken`/`Partial`) and leave the fix to a separate `/spec-fix` / `/spec` invocation.
- **Read-only zones** - `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Tag lifecycle: a ticket leaving `BlockNeedUserTest` (to any status) must have its `Timber.d("Sxxxx:` tags grep-deleted in the same change. Never delete a tag for a ticket that stays in `BlockNeedUserTest`.

---

## Relationship to other skills

- **S0307** - the strategic spec this skill operationalizes. The skill is the repeatable execution; S0307 is the why/what/scope.
- **`/spec-test-device`** - the single-ticket evidence runner this skill loops over.
- **`/spec-check`** - converts the on-device evidence into the final ticket status.
- **`/spec-all`, `/spec-dev`, `/spec-next`** - these auto-invoke `/spec-test-device` + `/spec-check` for one ticket the moment they set `BlockNeedUserTest` (when a device is online). `/spec-sweep` is the after-the-fact batch drain for tickets that were parked while no device was attached.
