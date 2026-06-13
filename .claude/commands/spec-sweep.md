---
model: sonnet
---

# Spec Sweep - Batch Device-Test of BlockNeedUserTest Tickets

> **GLOBAL DIRECTIVES (anti-bureaucracy):**
> 1. Dry technical prose, no filler.
> 2. Autonomy over bureaucracy: don't block on minor issues; surface only critical findings.
> 3. Terse report: end with one batch verdict line + sweep report path.

On-demand batch device-test sweep over every active `BlockNeedUserTest` ticket honestly verifiable on a connected emulator / device. Operational execution of strategic spec **S0307** (`PLAN/S0307_emulator-user-test-sweep.md`) - run periodically to drain the manual-verification backlog.

Per eligible ticket it delegates to the single-ticket runners: `/spec-test-device <Sxxxx>` (full evidence) -> `/spec-check <Sxxxx>` (evidence -> final status). The sweep owns only discovery, exclusion filtering, classification, batch ordering, roll-up report.

**Context isolation (mandatory):** the per-ticket device run (step 5.1) is delegated to a **subagent**, not run inline. mobile-mcp screenshots, logcat dumps, and build output are large and stay in context for the rest of a session; across a multi-ticket sweep run inline they accumulate and bloat the parent context linearly. Isolating each device run in a throwaway subagent keeps that evidence in the subagent's own context - the parent receives only a compact text verdict. The status-flip / tag-removal / git work (step 5.2 `/spec-check`) stays in the parent so all git is centralized and sequential (parallel subagent git would clobber).

## Usage

```text
/spec-sweep                                  # all eligible non-VR BlockNeedUserTest tickets
/spec-sweep S0054 S0186 S0207                # restrict to explicit id subset
/spec-sweep --flavor <standard|lite|photos|legacy>   # default: per-ticket flavor, baseline standard
/spec-sweep --device <id>                    # pin adb id for whole sweep
/spec-sweep --limit <N>                      # process at most N eligible tickets
/spec-sweep --dry-run                        # discovery + classification + plan only, no build/install/UI
```

Hard requirement: **mobile-mcp** server reachable and a device online (same gate as `/spec-test-device`). If the device gate fails, the sweep aborts before touching any ticket.

## Scope (inherited from S0307)

- **In:** non-VR / non-3D tickets currently `BlockNeedUserTest` whose acceptance reproduces on an emulator with locally-copied or generated fixtures.
- **Out:** VR / 3D / OpenXR / Quest-only / headset-only / immersive tickets; defect fixes (verify, don't repair); release signing / publishing; real OAuth / cloud secrets.
- **Never:** request or record real tokens, passwords, account credentials, or private keys; never fake a successful auth flow without evidence.

---

## Process

### 1 - Discovery (always fresh)

Read the **current** catalog - the seed list in S0307 §4/§10 is a snapshot, never source of truth:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/search.ps1 -Status BlockNeedUserTest -Format json
```

If id arguments passed, intersect result with that subset (warn on any argument id not currently `BlockNeedUserTest` and drop it).

### 2 - Exclusion filter (VR / 3D)

Drop any ticket flagged VR/3D/immersive. Double filter:

- Known current exclusions from S0307 §10: `S0249`, `S0291` (re-confirm against catalog each run, don't hard-code beyond a fallback).
- Keyword scan over ticket name + strategic spec body + roadmap link: `VR`, `3D`, `OpenXR`, `Quest`, `headset`, `immersive`, `composition layer`, `SBS`, `OU`, `single eye`. A hit on a clearly stereoscopic/headset feature -> excluded.

Uncertain cases go to a **review bucket** (listed in report), never silently into pass/fail.

### 3 - Device pre-flight

```powershell
pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug [-DeviceId <id>] -CheckMcp -Json
```

Exit-code handling (see script header table):
- `0` -> record device identity (API level, ABI, model, screen, density) into report header; proceed.
- `2` (no online device) / `1` (adb missing) / `6` (mobile-mcp unresolvable) -> **abort whole sweep** with one line stating reason. No ticket touched.
- `3` (multiple devices, no `--device`) -> abort, ask user to pass `--device <id>`.

### 4 - Classify each eligible ticket

Read each ticket's current acceptance source (strategic §2/§11, `## Last Audit` Manual block, tactical phase manual steps). Assign exactly one class:

- **direct emulator** - verifiable with local files, system intents, app settings, UI actions. -> full run in step 5.
- **emulator with local service** - needs FTP/SFTP/SMB/HTTP/local webhook raisable safely on localhost. -> raise service if trivially possible, else record a blocker.
- **external dependency** - needs real OAuth / cloud provider / installed third-party app / account state / hardware. -> no run; set `BlockExternal` with reason; never inject secrets.
- **not testable by copied fixtures** - acceptance depends on conditions not honestly reproducible on emulator without expanding scope. -> leave in `BlockNeedUserTest` with justified blocker note; record in report.

If `--dry-run`: stop here. Write classification + route matrix to report and exit.

### 5 - Per-ticket evidence run (direct emulator + reproducible local-service)

For each ticket in priority order (highest catalog `priority` first), run the two-step pipeline. Process tickets **strictly sequentially** - never spawn the per-ticket subagents in parallel (step 5.2 commits to git; concurrent subagent git would clobber).

**5.1 - device evidence (subagent-isolated).** Delegate the device run to a subagent (Task tool, `subagent_type: android-rd-specialist` - it needs mobile-mcp + Skill access; never a read-only researcher). The subagent runs `/spec-test-device <Sxxxx>` (inheriting `--flavor` / `--device` from sweep args): builds, installs, drives the mobile-mcp scenario, harvests logcat (including the spec's own `Timber.d("Sxxxx:` probes - the spec is `BlockNeedUserTest`, so the tags exist), and patches the ticket's `## Last Audit` Manual block with PASS/FAIL / expected-actual. It does **not** flip status and does **not** touch git.

Subagent brief must require it to return **only** a compact text verdict - id, PASS/FAIL/INCONCLUSIVE, one-line expected-vs-actual, evidence path under `temp/`. Screenshots, logcat, and build output stay in the subagent context and must **not** be echoed back. The parent records the verdict line and moves on.

**5.2 - finalize (parent, no subagent).** In the parent context run `/spec-check <Sxxxx>` against the evidence the subagent wrote to `## Last Audit`: audit, flip status to `Verified` / `Partial` / `Broken`, and on the `Verified` transition remove the `Timber.d("Sxxxx:` tags (CLAUDE.md "Debug Verification Tags") and commit. Keeping this in the parent centralizes all git so the sequential sweep never races itself.

Idempotency: one ticket per pipeline; a failure (or a dead/aborted subagent) is recorded and the sweep continues with the next. Honor `--limit`.

For **external dependency** / **not testable** tickets, do not run the inner pipeline - set the verdict status directly:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status <BlockExternal|BlockQuestions>
```

A status leaving `BlockNeedUserTest` (including these block transitions) **must** be paired with grep-and-delete of that spec's `Timber.d("Sxxxx:` tags from `.kt`, committed together (CLAUDE.md "Debug Verification Tags"). Step 5.1 runs delegate this to `/spec-check`; for the direct `update.ps1` transitions here the sweep performs the grep-and-delete itself.

### 6 - Cleanup

Remove temporary fixtures and emulator state pushed during the sweep, unless a ticket explicitly needs preserved state for a follow-up round (record the exception in report). Leave no long-lived services or background jobs running.

### 7 - Sweep report

Write `temp/spec-sweep_<YYYYMMDD_HHmm>/report.md` (per-run timestamped dir; sub-artifacts from `/spec-test-device` stay under their own `temp/<Sxxxx>_*` paths, linked not duplicated). Group by verdict:

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

Functionality log and feature docs **not** touched - internal verification workflow (S0307 §8); any user-visible verdict belongs to the individual ticket's own `/spec-check` finalization.

### 9 - Final report line

```text
spec-sweep: device <id>, eligible E, closed N, reopened M, blocked K, excluded V. Report: temp/spec-sweep_<TS>/report.md
```

---

## Constraints

- Never write outside `temp/` except the targeted ticket spec MD files (delegated to `/spec-test-device` and `/spec-check`).
- Never flip a ticket status by name or assumption - only by evidence, only via catalog tooling, only after the inner pipeline ran (or a justified block reason for the no-run classes).
- Never edit `PLAN/spec-catalog.jsonl` directly.
- Never run `gradlew.bat` directly - the inner `/spec-test-device` goes through `scripts/builders/*-device.ps1`.
- Never collect secrets; never fake a successful OAuth/cloud flow.
- Never touch VR/3D/headset-only tickets.
- Never fix defects found during the sweep - reopen the ticket (`Broken`/`Partial`), leave the fix to a separate `/spec-fix` / `/spec`.
- Read-only zones - `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Tag lifecycle: a ticket leaving `BlockNeedUserTest` (to any status) must have its `Timber.d("Sxxxx:` tags grep-deleted in the same change. Never delete a tag for a ticket that stays in `BlockNeedUserTest`.

---

## Relationship to other skills

- **S0307** - the strategic spec this skill operationalizes. The skill is the repeatable execution; S0307 is the why/what/scope.
- **`/spec-test-device`** - the single-ticket evidence runner this skill loops over.
- **`/spec-check`** - converts on-device evidence into the final ticket status.
- **`/spec-all`, `/spec-dev`, `/spec-next`** - auto-invoke `/spec-test-device` + `/spec-check` for one ticket the moment they set `BlockNeedUserTest` (when a device is online). `/spec-sweep` is the after-the-fact batch drain for tickets parked while no device was attached.
