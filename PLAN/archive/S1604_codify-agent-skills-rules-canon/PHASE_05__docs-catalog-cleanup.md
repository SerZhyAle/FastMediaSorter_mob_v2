# Phase 05 - Docs, registry and closure

**Strategic spec:** [`../S1604_codify-agent-skills-rules-canon.md`](../S1604_codify-agent-skills-rules-canon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-12

---

## Objective

Regenerate the registry's render targets, journal the change once, and close the ticket through the facade.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DOCS_MAP.md` | Regenerated | - |
| `sitemap.xml` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended by tooling | - |

---

## Steps

### Step 05.1 - Regenerate the registry render targets

**Files:** `docs/DOCS_MAP.md`, `sitemap.xml`

**Depends on:** - start of phase

**Prompt for developer:**

> Run `scripts/document_registry/validate.ps1`, then `generate.ps1`, then `generate.ps1 -Check`. Never hand-edit either render target.

**Why:**

The document-registry skill requires this closing sequence whenever a registered document or a registry record changes, and phases 01 and 03 each added a record.

**Verification:**

- `validate.ps1` exits 0.
- `generate.ps1 -Check` exits 0.
- `Grep` - `AGENT_HOOKS` matches in `docs/DOCS_MAP.md`.

**Status:** `[x]` done - Implemented and verified

**Step Log:**

- 2026-08-12 - Registry validated and generated views refreshed; capability decision recorded: no ALL_FEATURES entry because this ticket changes agent tooling only

---

### Step 05.2 - Record the capability decision

**Files:** none modified - decision recorded in the step log

**Depends on:** Step 05.1

**Prompt for developer:**

> Record that no `docs/ALL_FEATURES.jsonl` entry is added, because the ticket ships no user-visible capability - every artifact is agent-facing. State this explicitly rather than silently skipping the step.

**Why:**

Strategic §8 states there is no FEATURES change, and the capability inventory records shippable user capability only; a silent skip is indistinguishable from a forgotten step.

**Verification:**

- Step log carries the decision and its reason.

**Status:** `[x]` done - Implemented and verified

**Step Log:**

- 2026-08-12 - Registry validated and generated views refreshed; capability decision recorded: no ALL_FEATURES entry because this ticket changes agent tooling only

---

### Step 05.3 - Close through the facade

**Files:** `dev/CHANGELOG.md` via tooling

**Depends on:** Step 05.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` naming the whole changed set with `-Files` and `-ScopeToFile`, `-ChangeType Tooling`, and a description covering the three pillars. Read the verdict: only a bare `post-change: PASS` is clean, and `PASS WITH ADVISORIES` names each advisory to be read. Distinguish exit 1 from exit 2 - a defect found is not the same answer as could-not-verify.

**Why:**

CLAUDE.md section 12 makes the facade the mechanical closure and requires the whole changed set to be named, because the verdict covers exactly what was passed to it.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS`.
- `dev/CHANGELOG.md` gained exactly one entry for this ticket.

**Status:** `[x]` done - Implemented and verified

**Step Log:**

- 2026-08-12 - Closure facade completed; expected: post-change PASS and one S1604 dev-log entry | actual: post-change PASS, 12-file set logged at 2026-08-12 14:30:19
- 2026-08-12 - post-change: PASS (Doc) with registry ack accepted; a.ps1 fg PASS all gates; memory budget 16578 B under the 16595 B ceiling

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `/spec-check S1604` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. One open thread survives deliberately: the canon carries an unpublished delta from Phase 04, released only by an owner-invoked canon session.

---

## Rollback Plan

Revert the phase commit; the render targets regenerate from the registry, so no manual restoration is needed.
