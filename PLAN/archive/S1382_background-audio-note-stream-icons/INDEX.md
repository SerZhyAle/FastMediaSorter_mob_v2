# Tactical Plan: S1382 - background-audio-note-stream-icons

**Strategic spec:** [`../S1382_background-audio-note-stream-icons.md`](../S1382_background-audio-note-stream-icons.md)
**Research inputs:** none - strategic §6 item 4 was resolved from the media3 1.2.1 sources during planning and its finding is recorded in §6 itself.
**Feature:** Rotating note and stream icon in the background-playback bar
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-05

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | uri-channel-and-animator | - | ✅ Done | 2/2 | [PHASE_01__uri-channel-and-animator.md](PHASE_01__uri-channel-and-animator.md) |
| 02 | rotating-note | 01 | ✅ Done | 4/4 | [PHASE_02__rotating-note.md](PHASE_02__rotating-note.md) |
| 03 | stream-icon | 01, 02 | ✅ Done | 3/3 | [PHASE_03__stream-icon.md](PHASE_03__stream-icon.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 carries four research items and all four are Resolved; item 4 was the last one open and closed on 2026-08-05.

---

## Decisions fixed by this plan

- The rotation animator is the existing `InlinePlaybackAnimator`, extended with a configurable turn duration and freeze/resume, not a second copy of the same `ObjectAnimator` + detach-guard pattern. Strategic §5 names S1302's animator as the technique to reuse; duplicating it would reproduce the leak class S1302 exists to prevent.
- The stream URL reaches the bar through `MediaItem.requestMetadata.mediaUri`, not through `localConfiguration` and not through a new metadata extra. Strategic §6 item 4 records why.
- No layout file changes. Strategic §2 non-goals forbid changing the panel layout; all three visual states reuse the existing `miniArtwork` view. `view_mini_now_playing.xml` has no `layout-land` twin, so CLAUDE.md Rule 11 does not apply.
- No flavor source set. `docs/FLAVOR_MATRIX.md` shows `ENABLE_PERSISTENT_AUDIO_PLAYBACK` and `SUPPORT_STREAMS` live in the same four flavors (standard, noLegal, legacy, vr), so the existing `persistentAudioCompiledIn` constructor gate already covers the stream branch.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here. Strategic §8 names a user-visible capability, and per CLAUDE.md §11 the showcase files are `/skill-release`-owned; the per-spec record goes to `docs/ALL_FEATURES.jsonl` in Phase 04.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1382` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1382`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-05 - Initial tactical plan authored by `/spec-tech`.
