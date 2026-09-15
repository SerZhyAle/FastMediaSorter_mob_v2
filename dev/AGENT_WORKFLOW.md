# Agent workflow

A routing page. Each line points at the one place that owns the contract; nothing here restates it.

## Pick the tier

- Ticket work runs through the size tiers of `CLAUDE.md` section 3, smallest first: `/quick`, `/skill-fix`, then `/spec-all`.
- `/spec-all` chooses its own path: Trivial (one page, one closure, one build), Simple (compact spec with inline phases) or Full (strategic spec, tactical folder, `/spec-dev`).
- `/spec-code` is the same pipeline without a device; the build is the only test.
- An out-of-scope finding is parked with `/spec-draft` (`CLAUDE.md` section 3.1) and the current task resumes.

## Before editing

- Research order: `CLAUDE.md` section 5 - operations index, spec catalog, class catalogue, domain docs.
- Any unresolved UI placement, visibility or fallback decision: `/ui-clarify` before implementation (`CLAUDE.md` Rule 10).
- Flavor-specific work follows `dev/FLAVOR_DEVELOPMENT_RULES.md`: interface in `src/main`, implementation in the flavor source set, no `BuildConfig` checks in `src/main`.

## While implementing

- Steps are ticked only through `scripts/spec_catalog/plan-tick.ps1`, consecutive passing steps in one call; never hand-edit a `[x]`.
- Each phase ends with the phase-boundary audit of `CLAUDE.md` section 13.
- Each change closes through `scripts/post-change.ps1` with the evidence of `CLAUDE.md` section 12; the closure writes the one changelog row.
- Capabilities go to `docs/ALL_FEATURES.jsonl` through the closure; `docs/FEATURES*.md` belongs to `/skill-release`.

---

## Session start and continuity

Session start is `scripts/spec_catalog/session-bootstrap.ps1` - one call composing round state, device readiness, selection preflight and the ticket lease.

There is no separate continuity layer any more. S1596 deleted its bootstrap packet, request logger, request digest and dirty-tree guard after measuring zero invocations of each; S1603 deleted the remaining snapshot writer and reader after measuring 222 writes and zero reads across 2026-07-17..2026-08-12. What a resuming session needs to know already lives in two places that cannot go stale: the tactical plan's step markers and the ticket's status plus status note. A hand-written summary of those was never read, because it was a lossy copy of both.
