# Tactical Plan: S0443 - keep-send-option

**Strategic spec:** [`../S0443_keep-send-option.md`](../S0443_keep-send-option.md)
**Foundation:** [`../S0452_share-commands-infrastructure.md`](../S0452_share-commands-infrastructure.md) (Verified) + [`../S0452_share-commands-infrastructure/research/01__architecture.md`](../S0452_share-commands-infrastructure/research/01__architecture.md)
**Feature:** "Allow send to Google Keep" per-profile toggle + gating of the Keep command across its surfaces
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 4 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | register-keep-target | - | ⬜ Not started | 0/2 | [PHASE_01__register-keep-target.md](PHASE_01__register-keep-target.md) |
| 02 | gate-player-command-panel | 01 | ⬜ Not started | 0/2 | [PHASE_02__gate-player-command-panel.md](PHASE_02__gate-player-command-panel.md) |
| 03 | gate-editor-draw-standalone-surfaces | 01, 02 | ⬜ Not started | 0/3 | [PHASE_03__gate-editor-draw-standalone-surfaces.md](PHASE_03__gate-editor-draw-standalone-surfaces.md) |
| 04 | docs-catalog-cleanup | all | ⬜ Not started | 0/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Foundation reuse (what S0443 does NOT do)

The S0452 foundation already ships these, so this ticket adds none of them:

- Settings group "Команды отправить файл в.." and its per-target toggle rendering / persistence (`PlaybackSettingsFragment.setupSendCommandsGroup` renders one `SettingsToggleRow` per registered `ShareTarget`, disables + subtitles unavailable targets, and persists via `enabledShareTargets`/`disabledShareTargets`). No settings-UI or layout work.
- Per-profile flag storage + migration default (DataStore `AppSettings.enabledShareTargets`/`disabledShareTargets`; default resolved by `ShareTargetAvailabilityResolver.isDefaultEnabled`). No Room change, no `MIGRATION_NN`.
- `<queries>` for Keep packages (`com.google.android.keep`, `com.google.android.keep.notes`) are already declared in `app_v2/src/main/AndroidManifest.xml`. No manifest work.
- The effective-state seam `IsShareTargetEnabledUseCase(id, settings)` and the availability seam `ShareTargetAvailabilityResolver.isAvailable(target)`.

This ticket = register the `keep` `ShareTarget` (Phase 01) + thread the effective-state seam into the four existing Keep command surfaces (Phases 02-03) + docs/catalog (Phase 04).

---

## Keep command surfaces (gating targets)

The Keep command's visibility is currently gated only on `GoogleKeepAvailabilityChecker.isKeepAvailable()` in four places. Each must additionally honor the `keep` flag:

1. **Player command panel** - `ui/player/CommandPanelAvailabilityUpdater.isKeepInstalled()` feeds `keepInstalled` into `CommandPanelLayoutPlanner.buildActiveCommands(...)`, which adds `PlayerCommand.SEND_TEXT_TO_KEEP` for text files. (Phase 02)
2. **Text editor action panel** - `ui/player/helpers/TextViewerManager` passes `keepAvailable` to `ui/editor/actions/EditorActionPanelBinder`, which shows the "Send to Keep" overflow item; click routes through `ui/player/helpers/TextEditorActionPanelCallbacks.onSendToKeep`. (Phase 03)
3. **Draw overlay editor** - `ui/player/helpers/ImageDrawOverlayManager` (S0362) gates its "Send to Google Keep" overflow item on its own `keepChecker`. (Phase 03)
4. **Standalone text host overflow** - `res/menu/overflow_menu_standalone_player.xml` item `menu_send_to_keep` (hidden by default; the standalone text host toggles it visible when Keep is installed). (Phase 03)

---

## Gating-threading approach (decided)

`CommandPanelController`, `TextViewerManager`, `ImageDrawOverlayManager`, and the standalone hosts are manually-wired view managers, not `@AndroidEntryPoint`. They are constructed by factories (`PlayerManagerInitializer`, `PlayerViewerFactory`, `StandaloneViewManager`, `TextStandaloneActivity`, `StandaloneDrawSaveHelper`) that read injected fields off the `@AndroidEntryPoint` host activity (`activity.<dependency>`), and they already receive `SettingsRepository`.

Chosen seam (consistent across all surfaces, no new hot-path ctor param storms): inject `IsShareTargetEnabledUseCase` into each host activity, and at each Keep gate combine the existing availability check with the effective flag - `keepEnabled && keepChecker.isKeepAvailable()` - where `keepEnabled = isShareTargetEnabledUseCase("keep", currentSettings)`. The current `AppSettings` is already reachable at each gate (the command panel reads `settingsRepository.getSettings().first()`; the editor/draw managers receive `settingsRepository`). The installed-package check stays for actual launch; visibility now also honors the setting.

The shared id literal `"keep"` is declared once in Phase 01 (a `const` on the Keep registration) and reused by every gate - no scattered string literals.

---

## Pre-Implementation Blockers

None. All strategic §6 items are Resolved by the S0452 foundation (settings UI, flag storage, default rule, manifest `<queries>`). The only Open item (how to thread the gating predicate into manually-wired surfaces) is resolved above and is a tactical detail, not a blocker.

---

## Completion Gate

- [ ] All phases ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (new user-visible toggle - strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated for touched classes.
- [ ] `/spec-check S0443` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Implemented`, run `/spec-check S0443`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.
