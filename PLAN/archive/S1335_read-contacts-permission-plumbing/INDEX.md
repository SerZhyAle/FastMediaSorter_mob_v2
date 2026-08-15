# Tactical Plan: S1335 - read-contacts-permission-plumbing

**Strategic spec:** [`../S1335_read-contacts-permission-plumbing.md`](../S1335_read-contacts-permission-plumbing.md)
**Research inputs:** none (research performed inline during `/spec-tech`; findings folded directly into the strategic spec's corrections, 2026-08-01)
**Feature:** READ_CONTACTS as a first-class registry permission (Contacts group)
**Tier:** ad-hoc
**Priority:** 55
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-01

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | buildconfig-launcher-gate | - | ✅ Done | 1/1 | [PHASE_01__buildconfig-launcher-gate.md](PHASE_01__buildconfig-launcher-gate.md) |
| 02 | trilingual-strings | - | ✅ Done | 1/1 | [PHASE_02__trilingual-strings.md](PHASE_02__trilingual-strings.md) |
| 03 | permission-registry-entry | 01, 02 | ✅ Done | 4/4 | [PHASE_03__permission-registry-entry.md](PHASE_03__permission-registry-entry.md) |
| 04 | privacy-policy-paragraph | - | ✅ Done | 1/1 | [PHASE_04__privacy-policy-paragraph.md](PHASE_04__privacy-policy-paragraph.md) |
| 05 | docs-catalog-cleanup | 01, 02, 03, 04 | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01, 02 and 04 are independent of each other and of `BuildConfig`/registry work. Phase 03
consumes both Phase 01's `BuildConfig.SUPPORT_LAUNCHER` field and Phase 02's three string resources
(its `PermissionEntry`/`getGroups()` reference `R.string.perm_*` at compile time) - it must run after
both. Phase 05 is final.

---

## Pre-Implementation Blockers

No blockers - strategic spec is `Approved`, owner decisions (2026-07-31) are final, and the two
tactical-planning corrections (contextual-ask landing point; `BuildConfig` naming/declaration
mechanism) were resolved via codebase research and patched into the strategic spec directly
(2026-08-01) rather than left open.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/ALL_FEATURES.jsonl` gains an `ADD` record for the Contacts permission (developer
      inventory) - user-visible capability, per CLAUDE.md §11. **`docs/FEATURES.md` + `_RU.md` +
      `_UK.md` are NOT touched here** - those are `/skill-release`-owned, populated only from the
      `ALL_FEATURES` diff at release time.
- [x] `dev/CHANGELOG.md` has entry for every modified file (via `add_to_dev_log.ps1`).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (no new class - `PermissionGroup.CONTACTS` is an enum
      constant, not a class - but keeps the index current for the touched files).
- [ ] `/spec-check S1335` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

**Manual / release-time items this tactical plan does NOT cover (per strategic spec):**
- Play Console restricted-permission declaration form - a release-console action, not a repo change;
  gates the next release that ships this, not this ticket's phases.
- Device measurement of the S1176 MESSAGE-channel prediction (grant `READ_CONTACTS` via Settings,
  re-check whether `LauncherContactPickManager`'s MESSAGE channel starts resolving) - on-device,
  tracked as a manual verification item, not a phase.
- Cross-flavor absence of the Contacts entry on `lite`/`photos`/`legacy`/`vr` is implied by
  `BuildConfig.SUPPORT_LAUNCHER = false` on those flavors plus the existing `resolveFlavorGate`
  mechanism (same mechanism already proven correct for `SUPPORT_AUDIO`/`SUPPORT_LOCAL_NETWORK`) - not
  independently re-tested per flavor by a new phase step, since Phase 03's unit test only runs against
  the `standardDebug` test variant.
- R8/release-build correctness (the entry is not silently disabled on a minified build) is inherited
  from using the same compile-time `resolveFlavorGate` map S0970 already established for this exact
  failure mode - no new code path is introduced that could reintroduce the reflection bug S0970 fixed.

No `BlockNeedUserTest` transition is planned from the phases themselves (everything here is
mechanically verifiable - compile, unit test, string/doc greps); the two manual items above are
recorded for the final report / `/spec-check`, not gated by a probe tag in this ticket. If the owner
wants device confirmation before shipping, that is `/spec-check`'s "Manual / on-device" list, not a
`Timber.d("S1335: ...")` tag - nothing in this ticket's own code path needs one to prove it compiled
and is wired correctly.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <Sxxxx>`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-08-01 - Initial tactical plan authored by `/spec-tech`, after patching two strategic
  corrections found during planning research (contextual-ask landing point; BuildConfig
  naming/declaration mechanism) directly into the strategic spec.
- 2026-08-01 - Phase 03 closure found the `flavor-flags` mechanical gate had no exclusion for the
  S0970 whitelist function it was about to flag as new growth; fixed the gate script itself
  (`scripts/quality/lib/source-matchers.ps1`), matching the existing `PackageManagerCompat.kt`
  precedent on the neighboring `deprecated-pm-flags` rule. Did not block Phase 03.
