# Phase 05 - Skill Orchestrator

**Strategic spec:** [`../S0484_prerelease-emulator-sweep.md`](../S0484_prerelease-emulator-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-06-17
**Completed:** 2026-06-17

> **Blocked:** requires research §6.4 (standalone-player intent + return path) and §6.6 (spec-draft dedup + ticket-update rules) Resolved before start.

---

## Objective

Author the `/spec-prerelease` command skill that orchestrates prepare → configure → scenario (mobile-mcp) → measure → verdict, then branches: PASS prints a report and proposes `/skill-release` (no auto-run); FAIL auto-creates deduped `/spec-draft` tickets and updates pending-test tickets.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.
- [ ] Research §6.4 Resolved - `research/04__standalone-player-intent.md` exists.
- [ ] Research §6.6 Resolved - `research/06__catalog-mutation-rules.md` exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/spec-prerelease.md` | New | ≤ 320 |

---

## Steps

### Step 05.1 - Skill header, usage, device gate

**Files:** `.claude/commands/spec-prerelease.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the command skill with the project's global anti-bureaucracy directives, a usage block (`/spec-prerelease [--device <id>] [--dry-run]`), and a pre-flight section that runs `scripts/devtest/prerelease-prepare.ps1` and aborts on its non-zero exit codes. State the mobile-mcp hard requirement as in `/spec-test-device`.

**Verification:**

- `Glob` - `.claude/commands/spec-prerelease.md` exists.
- `Grep` - `prerelease-prepare.ps1` referenced.
- `Grep` - `--dry-run` documented.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (file exists, prerelease-prepare.ps1 referenced, --dry-run + mobile-mcp documented). Header, global directives, usage, pre-flight section. Files: .claude/commands/spec-prerelease.md (New). Dev log recorded.

---

### Step 05.2 - Configure stage section

**Files:** `.claude/commands/spec-prerelease.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add the configure section in two parts. First run `scripts/devtest/prerelease-configure.ps1` for the adb-scriptable work (reachability pre-check honouring per-resource SKIP, intent-push import trigger via `ResourceImportActivity`, theme/language settings). Then drive the UI via mobile-mcp for the parts adb cannot do: tap the import confirm dialog, set the DataStore-backed setting toggles (sort/grid/trash/accept-shared), and verify each resource listing via the `BrowseLoadingManager: COMPLETE` log marker. Reference the run config `scripts/devtest/prerelease.config.psd1`.

**Verification:**

- `Grep` - `prerelease-configure.ps1` referenced.
- `Grep` - `prerelease.config.psd1` referenced.
- `Grep` - import confirm-dialog tap + DataStore toggle UI steps present (mobile-mcp).
- `Grep` - `BrowseLoadingManager` listing marker referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS (configure.ps1 + config.psd1 referenced; confirm/DataStore mobile-mcp steps; BrowseLoadingManager marker). Two-part configure: adb script + UI confirm/DataStore/listing with OWNER_TRIGGER fallback. Files: .claude/commands/spec-prerelease.md (+section). Dev log recorded.

---

### Step 05.3 - Scenario section (playback, standalone-player roundtrip, re-entry, network scroll)

**Files:** `.claude/commands/spec-prerelease.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add the mobile-mcp scenario section: play several file types in-app, close, launch the standalone player for a file and return into the app (using the intent from research §6.4), re-enter the app without reinstall, scroll lists and play on the network resource. Drive targeting from `mobile_list_elements_on_screen`; capture per-checkpoint timings via `scripts/devtest/prerelease-measure.ps1`; save evidence with `mobile_save_screenshot` (off-context). Mirror the token-discipline rules from `/spec-test-device`.

**Verification:**

- `Grep` - `prerelease-measure.ps1` referenced.
- `Grep` - `mobile_list_elements_on_screen` referenced.
- `Grep` - standalone-player roundtrip step present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (prerelease-measure.ps1 referenced, mobile_list_elements_on_screen, standalone roundtrip + menu_open_in_fms). Six-step scenario with per-checkpoint measure wiring + background logcat + metrics JSON. Files: .claude/commands/spec-prerelease.md (+section). Dev log recorded.

---

### Step 05.4 - Verdict + PASS branch (report, propose release)

**Files:** `.claude/commands/spec-prerelease.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add the section that runs `scripts/devtest/prerelease-verdict.ps1` and, on PASS, writes a timestamped report under `temp/`, then proposes `/skill-release` as the next step without auto-invoking it (ADR-1, stop on go/no-go). State explicitly that release starts only on owner confirmation.

**Verification:**

- `Grep` - `prerelease-verdict.ps1` referenced.
- `Grep` - `/skill-release` referenced as a proposal.
- `Grep` - explicit "no auto-run" / owner-confirmation wording present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (prerelease-verdict.ps1 referenced, /skill-release proposed, explicit no-auto-run + owner-confirmation wording). PASS branch writes report + proposes release without running it (ADR-1). Files: .claude/commands/spec-prerelease.md (+section). Dev log recorded.

---

### Step 05.5 - FAIL branch (spec-draft dedup + pending-test updates)

**Files:** `.claude/commands/spec-prerelease.md`
**Depends on:** Step 05.4

**Prompt for developer:**

> Add the FAIL branch: per the rules from research §6.6, dedup each new defect by symptom via `scripts/spec_catalog/search.ps1` before creating a `/spec-draft`, and update pending-test (`BlockNeedUserTest`) tickets according to the run outcome. Reference the tag-lifecycle rule when a ticket leaves `BlockNeedUserTest`.

**Verification:**

- `Grep` - `search.ps1` referenced for dedup.
- `Grep` - `/spec-draft` referenced.
- `Grep` - `BlockNeedUserTest` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (search.ps1 dedup, /spec-draft, BlockNeedUserTest). FAIL branch: deduped /spec-draft per defect + pending-test via /spec-check (no guess flips, tag lifecycle respected). All four helper scripts referenced; final report line. Files: .claude/commands/spec-prerelease.md (+section). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `Grep` - `.claude/commands/spec-prerelease.md` references all four helper scripts (prepare, configure, measure, verdict).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for the skill file.

---

## Handoff Notes to Next Phase

The skill is complete and self-contained. Phase 06 registers it (prompt mirror, CLAUDE.md routing) and finalizes docs.

---

## Rollback Plan

Delete `.claude/commands/spec-prerelease.md` - no data migration or user-facing surface changed.
