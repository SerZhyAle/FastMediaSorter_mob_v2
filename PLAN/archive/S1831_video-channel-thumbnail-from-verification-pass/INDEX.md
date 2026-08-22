# Tactical Plan: S1831 - video-channel-thumbnail-from-verification-pass

**Strategic spec:** [`../S1831_video-channel-thumbnail-from-verification-pass.md`](../S1831_video-channel-thumbnail-from-verification-pass.md)
**Research inputs:** [`research/01__sheet-capacity-measured.md`](research/01__sheet-capacity-measured.md), [`research/02__one-pass-feasibility.md`](research/02__one-pass-feasibility.md), [`research/03__merged-pass-measured.md`](research/03__merged-pass-measured.md)
**Feature:** stream-catalog channel preview publishing
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 85
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-20

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | sheet-capacity-and-refusal | - | ✅ Done | 6/6 | [PHASE_01__sheet-capacity-and-refusal.md](PHASE_01__sheet-capacity-and-refusal.md) |
| 02 | capture-first-liveness | 01 | ✅ Done | 5/5 | [PHASE_02__capture-first-liveness.md](PHASE_02__capture-first-liveness.md) |
| 03 | docs-catalog-cleanup | 01 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Why the phases split where they do

The two pillars are independent and only one of them is gated.

Pillars 2 and 3 - sheet height derived from tile count, and a refusal that names the uncovered channels -
touch the packer alone, need no network at all, and close strategic goals 2 and 3 plus criterion 4's
mechanism on their own. They are Phase 01 and they can run unattended.

Pillar 1 - the frame coming from the run that proves liveness - is Phase 02, and it cannot be finished
without the capture run over live third-party streams that strategic §6.2 describes. That run is a real-world
side effect on other people's servers; the strategic spec's own header marks it as needing the owner's
go-ahead before an unattended session starts it. Putting both pillars in one phase would park the whole
ticket behind that gate for no reason.

Phase 03 depends on 01 rather than on 02, for the same reason: the documents Phase 01 falsifies must be
corrected whether or not Phase 02 ever runs.

---

## Pre-Implementation Blockers

- [x] **Research (strategic §6.2, cost and frame usability):** a capture run over live third-party video
      channels, measuring the merged pass against the two current passes for time and for whether the frame
      is recognisable rather than black. Required before **Phase 02** only. **Owner go-ahead required** - the
      run loads other people's servers for hours. Phase 01 and Phase 03 do not wait on it.

Strategic §6.1 is Resolved and needed nothing before Phase 01. Its remaining confirmatory question - whether
anything on StreamsPlayer's side pins the number 2 040 or 60 rows - is addressed to S1828's consumer registry
and is not a blocker: no boundary of that kind was ever declared, and the app's own slicer takes the atlas
dimensions as arguments rather than assuming a row count. It is genuinely still open, and Phase 03 recorded it
in `docs/STREAM_CATALOG_CONSUMERS.md` as a question to ask the consumer next time they are in contact. An
earlier draft of research 01 claimed the question was already answered by reproducing the consumer's 1 855
figure; that reproduction came from a transient state of `streams.csv` and is withdrawn.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/ALL_FEATURES.jsonl` carries the delivered capability - `streams.channel-preview-atlas-full-coverage`.
- [x] `dev/CHANGELOG.md` has an entry for every modified file, via `scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/<module>.jsonl` regeneration - **not applicable**, this ticket edits no Kotlin.
- [ ] `/spec-check S1831` returns `Verified` - the ticket is at `Implemented`; the audit is the next step.
- [x] Every strategic §6 research item is `Resolved` or carries a literal `Carrier: Sxxxx` token
      (CLAUDE.md section 4, gate `scripts/spec_catalog/check-open-items-carried.ps1`). Both are `Resolved`:
      §6.1 by research 01, §6.2 by research 03. No carrier is needed.

**What is deliberately not part of this ticket, so nobody looks for it here:** the payload is built but not
published. A publication changes an asset the app pins by SHA-256, so it needs a revision bump and a pin
update, and strategic §2 names that as S1828's scope. The full-catalog sweep on the new ladder has also not
been run - Phase 02 was verified on a 400-channel measurement and a 24-row A/B, not on all 2 763 rows. Both
belong to the next publishing run.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1831`.

---

## Blockers Log

- 2026-08-20 - Phase 02 was blocked before it started: strategic §6.2 needed a capture run over live
  third-party streams requiring the owner's go-ahead. **Resolved the same day.** The owner authorised it on
  the condition that it was not the run which had just finished in a neighbouring session; checked before
  starting - the frame cache had not been written since 2026-08-12 and no ffmpeg was alive, while what had
  finished next door at 11:11 was a liveness sweep and a catalog publication. Measurement ran, §6.2 is
  Resolved, phase 02 is done.

---

## Change Log

- 2026-08-20 - Initial tactical plan authored by `/spec-all` Stage F2, from research artifacts 01 and 02.
- 2026-08-20 - Phase 01 done: sheet height derived from tile count, both refusal paths exercised for real,
  48 MiB gate added where none existed, `-PreviewFromCacheOnly` added so the packer can be proven without
  spending requests on broadcasters. Coverage 2 040 -> 2 830 tiles, zero channels lost to capacity.
- 2026-08-20 - Phase 03 done: consumer registry carries the 48 MiB and derived-height invariants; capability
  recorded. Ticket was parked at `BlockQuestions` on the one owner decision phase 02 needed.
- 2026-08-20 - Phase 02 done after the owner authorised the run: capture-first liveness for VIDEO rows. The
  merged pass is 48% cheaper, confirms 360 channels where ffprobe confirms 340, and agreed with the old path
  on 24 of 24 verdicts. Two self-inflicted defects were caught by the phase's own verification - a
  cached-frame short-circuit that would have reported dead channels alive forever, and our own output codec
  leaking into the catalog's `media_codecs` column.
