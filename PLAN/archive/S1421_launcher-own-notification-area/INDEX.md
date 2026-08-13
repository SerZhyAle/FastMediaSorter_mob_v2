# Tactical Plan: S1421 - launcher-own-notification-area

**Strategic spec:** [`../S1421_launcher-own-notification-area.md`](../S1421_launcher-own-notification-area.md)
**Research inputs:** none (findings written straight into strategic §4.5 and §4.6)
**Feature:** Own-signal strip in the freed launcher status area
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | signal-contract | - | ✅ Done | 4/4 | [PHASE_01__signal-contract.md](PHASE_01__signal-contract.md) |
| 02 | strip-host | 01 | ✅ Done | 5/5 | [PHASE_02__strip-host.md](PHASE_02__strip-host.md) |
| 03 | signal-sources | 01 | ✅ Done | 4/4 | [PHASE_03__signal-sources.md](PHASE_03__signal-sources.md) |
| 04 | signal-row | 02, 03 | ✅ Done | 5/5 | [PHASE_04__signal-row.md](PHASE_04__signal-row.md) |
| 05 | overflow-list | 04 | ✅ Done | 4/4 | [PHASE_05__overflow-list.md](PHASE_05__overflow-list.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Phase order follows ADR-2

Strategic §6 names one ordering rule: the decisive node comes before content, or two tickets drop
independent views into one area. Phases 01 and 02 are that node - 01 is the contract it arbitrates over,
02 is the container that owns the area and its height. Content follows in 03-05.

---

## Source-set placement contract

- Every class lands under `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/`,
  which `app_v2/build.gradle.kts` mounts only for `standard` and `noLegal`. That placement, not a
  `BuildConfig.SUPPORT_LAUNCHER` guard, is what satisfies strategic §3.1 and CLAUDE.md Rule 14.
- Package precedent: `ui/launcher/gadget/nowplaying/` already holds a source contract plus two
  implementations in this source set. `ui/launcher/signal/` mirrors it.
- Layouts and dimens land under `app_v2/src/launcherEnabled/res/`, next to `launcher_taskbar.xml`.
- User-visible strings land in `app_v2/src/main/res/values*/strings.xml`, as every existing
  `launcher_*` key does - `set-android-string.ps1 -Action add` writes EN/RU/UK in one call.
- No file in `app_v2/src/main/java/**` gains a launcher branch.

---

## Pre-Implementation Blockers

None. Every phase below is implementable as written.

## Open owner decision - blocks the `Verified` flip, not a phase

Strategic §5.2 asks what occupies the strip when no signal is active. It is deliberately outside every
phase: phase 02 delivers the constant-height area and leaves the slot empty, and no step guesses what fills
it. The answer therefore closes one strategic §7 criterion without rewriting phases 01-06.

- [ ] **Owner decision (strategic §5.2):** content of the empty state.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` carries the capability record added via `scripts/all_features/add.ps1`.
      `docs/FEATURES*.md` is not touched here - it is `/skill-release`-owned (CLAUDE.md §11).
- [ ] `dev/CHANGELOG.md` has an entry for the ticket, written by `scripts/post-change.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this ticket adds public classes.
- [ ] Strategic §5.2 answered by the owner and its criterion implemented, or the ticket closes `Partial`
      with that criterion named.
- [ ] `/spec-check S1421` returns `Verified`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1421`.

---

## Blockers Log

- 2026-08-07 - Strategic §4.4 second answer rests on a premise the code falsifies (S1178 puts nothing in
  this strip; no strip host exists). Recorded as strategic §4.5, reopened as §5.2. Phases 01-06 are
  written so the answer does not rewrite them.

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
