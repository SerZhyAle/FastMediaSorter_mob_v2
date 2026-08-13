# Phase 01 - Foundations

**Strategic spec:** [`../S0268_agent_continuity_layer.md`](../S0268_agent_continuity_layer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06, 07
**Steps done:** 1 / 1
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Establish the `scripts/agent_continuity/` directory and write a single foundational README that documents the contract of all five utilities and records the tactical resolutions for every §6 research item.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done. (foundation phase)
- [x] Strategic §6 research items blocking this phase are Resolved. (resolved inline in INDEX Pre-Implementation Blockers and persisted by this phase's README)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/agent_continuity/README.md` | New | ≤ 250 |

---

## Steps

### Step 01.1 - Write foundational README

**Files:** `scripts/agent_continuity/README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the directory `scripts/agent_continuity/` and write `scripts/agent_continuity/README.md`. The README has three top-level sections in this exact order:
>
> 1. **Pillars** - one paragraph per pillar (Bootstrap packet, Resume layer, Request logger, Request digest, Dirty-tree guard) with its strategic-spec section reference (`§5.1` .. `§5.5`) and the exact relative path of its utility file (`scripts/agent_continuity/start-packet.ps1`, `scripts/agent_continuity/session-snapshot.ps1`, `scripts/agent_continuity/session-resume.ps1`, `scripts/agent_continuity/request-log.ps1`, `scripts/agent_continuity/request-digest.ps1`, `scripts/agent_continuity/dirty-tree-guard.ps1`).
> 2. **Tactical decisions** - six numbered sub-sections, one per §6 research item, with the exact resolutions copied from `INDEX.md` Pre-Implementation Blockers (replace vs reanimate, agent-id source, snapshot trigger, high-risk overlap list, Sxxxx candidate source, request log format).
> 3. **Deprecation notice** - one short paragraph: the legacy `scripts/log-ai-request.ps1` is superseded by `scripts/agent_continuity/request-log.ps1`; legacy script remains on disk but must not be invoked by new code or skills; physical removal deferred to a follow-up cleanup spec.
>
> Use markdown headings (`##` for the three top-level sections, `###` for the six tactical decisions). English text throughout. No emoji, no pseudographics. Bullet lists allowed; tables only if needed for the pillar / utility mapping. Keep the file under 250 lines.

**Verification:**

- `Glob` - `scripts/agent_continuity/README.md` exists.
- `Grep` - exactly one occurrence of `## Pillars` in that file.
- `Grep` - exactly one occurrence of `## Tactical decisions` in that file.
- `Grep` - exactly one occurrence of `## Deprecation notice` in that file.
- `Grep` - all six utility paths appear at least once: `start-packet.ps1`, `session-snapshot.ps1`, `session-resume.ps1`, `request-log.ps1`, `request-digest.ps1`, `dirty-tree-guard.ps1`.
- `Grep` - all six research-item resolutions referenced by their identifier substrings: `§6.1`, `§6.2`, `§6.3`, `§6.4`, `§6.5`, `§6.6`.
- File size < 250 lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 7/7 PASS. Files: scripts/agent_continuity/README.md (+61 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `scripts/agent_continuity/README.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The README is the canonical reference for every later phase. Each pillar's phase reads the exact utility path and contract decisions from here. Do not duplicate the tactical decisions in pillar phases; reference the README section instead.

---

## Rollback Plan

Revert the phase commit. No data migration, no user-facing surface, no Android build impact - the file is documentation only.
