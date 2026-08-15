# Tactical Plan: S0577 - background-audio-playback-group

**Strategic spec:** [`../S0577_background-audio-playback-group.md`](../S0577_background-audio-playback-group.md)
**Research inputs:** none (inline architecture research; findings folded into phase steps)
**Feature:** Background-audio playback group on Player tab + stream propagation
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (journal: BlockNeedUserTest)
**Phases:** 6 / 6 done
**Last updated:** 2026-06-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | audio-exit-reuse | - | ✅ Done | 3/3 | [PHASE_01__audio-exit-reuse.md](PHASE_01__audio-exit-reuse.md) |
| 02 | streams-settings-access | - | ✅ Done | 2/2 | [PHASE_02__streams-settings-access.md](PHASE_02__streams-settings-access.md) |
| 03 | streams-background-gate | 02 | ✅ Done | 3/3 | [PHASE_03__streams-background-gate.md](PHASE_03__streams-background-gate.md) |
| 04 | streams-exit-rule | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__streams-exit-rule.md](PHASE_04__streams-exit-rule.md) |
| 05 | settings-ui-move | - | ✅ Done | 6/6 | [PHASE_05__settings-ui-move.md](PHASE_05__settings-ui-move.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Key Decisions (locked before planning)

- **Stream behavior when background playback is OFF (default):** play inline via a local in-app `ExoPlayer` (no foreground service, no notification); stop on screen leave / app background - a faithful mirror of local audio. When ON: keep the `AudioServiceController` background-service path and apply the exit-behavior rule on leave. (Owner decision, 2026-06-21.)
- **Reuse, not duplication (strategic §5.3):** the exit-behavior decision and the exit dialog are extracted into reusable units in Phase 01 and consumed by both the player and the streams screen.
- **String rename target:** the existing key `background_audio_exit_behavior_title` value is changed in place (EN/RU/UK); the key is unchanged so settings backup/restore via `BackupMapper` is unaffected.
- **No new settings layout file:** the moved block is inlined into `fragment_settings_playback.xml`, so `SettingsSearchTabMapping` / `SettingsSearchLayoutCatalog` need no new entry; the moved rows inherit the PLAYBACK destination automatically.
- **Debug verification tags:** NOT inserted by phases. `/spec-dev` inserts one `Timber.d("S0577: ..")` per changed flow at the `BlockNeedUserTest` transition (settings group open on Player tab, stream play with bg OFF, stream play with bg ON, streams-exit decision).

---

## Pre-Implementation Blockers

- All strategic §6 research items are Resolved.
- The single open architecture fork (OFF-case stream mechanism) was resolved by owner on 2026-06-21 (see Key Decisions).

No open blockers - Phase 01 may start.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - NOT edited per-spec; the delivered capability is recorded in `docs/ALL_FEATURES.jsonl` (Phase 06). The public showcase is populated only by `/skill-release` from the ALL_FEATURES diff.
- [ ] `dev/CHANGELOG.md` has an entry for every logical change (batched per phase).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes added in Phases 01/03).
- [ ] `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` + `docs/settings/settings-annotations.json` regenerated; `scripts/quality/assert-settings-doc-sync.ps1` passes.
- [ ] `/spec-check S0577` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log; set journal status to the matching `Block*` with `-StatusNote`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0577`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-21 - Initial tactical plan authored by `/spec-tech`.
