# Tactical Plan: S1846 - wear-phone-browse-favourites-placeholders

**Strategic spec:** [`../S1846_wear-phone-browse-favourites-placeholders.md`](../S1846_wear-phone-browse-favourites-placeholders.md)
**Research inputs:** [`research/02__phone-browse-and-favourites-as-is.md`](research/02__phone-browse-and-favourites-as-is.md)
**Feature:** Watch home - the Favourites section and the five media-type chips of the Phone section
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done - blocked on S1860
**Phases:** 6 / 6 done
**Last updated:** 2026-08-20

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | transport-media-type-v3 | - | ✅ Done | 5/5 | [PHASE_01__transport-media-type-v3.md](PHASE_01__transport-media-type-v3.md) |
| 02 | phone-browse-by-media-type | 01 | ✅ Done | 3/3 | [PHASE_02__phone-browse-by-media-type.md](PHASE_02__phone-browse-by-media-type.md) |
| 03 | open-phone-file-from-watch | 01 | ✅ Done | 3/3 | [PHASE_03__open-phone-file-from-watch.md](PHASE_03__open-phone-file-from-watch.md) |
| 04 | favourites-record-store | - | ✅ Done | 4/4 | [PHASE_04__favourites-record-store.md](PHASE_04__favourites-record-store.md) |
| 05 | favourites-screen | 04 | ✅ Done | 4/4 | [PHASE_05__favourites-screen.md](PHASE_05__favourites-screen.md) |
| 06 | placeholders-docs-catalog | 02, 03, 05 | ✅ Done | 4/4 | [PHASE_06__placeholders-docs-catalog.md](PHASE_06__placeholders-docs-catalog.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All five strategic §6 research items are Resolved, and both owner quiz rounds (§12, §13) are recorded with their answers.

---

## Scope boundaries

- **S1781 is not reopened.** Strategic ADR-1 rules its `Verified` correct; this plan changes no status but its own.
- **The Apps section and the Streams section are untouched.** Their placeholders name S1710 and S1708, both open, so those references are already correct - only the two placeholders naming S1846 are replaced here.
- **Incoming favourites transfer from phone to watch is not built.** The watch store is filled by the watch's own players and returns a delta; strategic non-goals fix that direction.
- **No wire-version negotiation and no compatibility path.** The owner removed the requirement verbatim (strategic §13): there is no installed watch base, so both sides move to v3 at once. A step that adds a fallback branch for v2 is out of scope.

---

## Concurrency note - S1697 holds probes in four files this plan edits

S1697 sits in `BlockNeedUserTest` and its acceptance probes are live in
`PhoneResourceClient.kt`, `PhoneResourceViewModel.kt`, `ListPhoneResourcePageUseCase.kt` and
`PhoneWearListenerService.kt` - every one of them in this plan's Files Touched.

Strategic §7 offers two mitigations: run S1697's device test and close it first, or implement without
disturbing its probes. **The second is the one available here**, because this plan is executed device-free
and closing S1697 requires a paired phone and a watch. Therefore:

- Never delete or reword a `Timber.d("S1697: ..")` line. It is legitimate exactly while that ticket is in
  `BlockNeedUserTest`, and removing it would both break its pending device test and trip the ticket-log gate
  from the other side.
- Take `CODE.LOCK` per edit, not per phase.
- A step that moves code containing an S1697 probe carries the probe with it rather than dropping it.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 names user-visible change, so a sentence is owed; written by `/skill-release` from the `ALL_FEATURES` diff, never per-spec.
- [x] `dev/CHANGELOG.md` has one implementation entry naming the 23-file set.
- [x] Both catalogs regenerated; the three new `wear` classes carry a role and `status=new`.
- [ ] `/spec-check S1846` returns `Verified` - the device test of 2026-08-21 failed and all three of its
  failures belong to S1860 (`bugfix-wear-bridge-service-dies-mid-request`), not to this plan. The ticket is
  parked in `BlockByOtherTask` with `Blocker: S1860` and returns to `/spec-sweep` once S1860 closes.
- [ ] Strategic spec `Status:` advanced by the closing command.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1846`.

---

## Validation targets

The watch module has its own three targets and the phone ones do not cover it (CLAUDE.md section 9, S1807):

- `.\a.ps1 fw` - watch Kotlin compile. The only compile verdict that means anything for a `wear/` change.
- `.\a.ps1 fwu` - watch unit suite.
- `.\a.ps1 fk` - phone Kotlin compile, required because Phase 01 edits `ListPhoneResourcePageUseCase` and the phone's copy of the payload.

Quoting `fk` over a `wear/`-only change records a verdict about the other module. Read the banner line that names the module checked.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-08-20 - Initial tactical plan authored by `/spec-tech` inside `/spec-code`.
