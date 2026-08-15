# Tactical Plan: S0303 - telegram-integration

**Strategic spec:** [`../S0303_telegram-integration.md`](../S0303_telegram-integration.md)
**Feature:** Telegram integration (t.me download + send/share)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented (awaiting on-device test)
**Phases:** 3 / 4 done (Phase 03 ⏭️ Skipped)
**Last updated:** 2026-05-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | share-intent | - | ✅ Done | 4/4 | [PHASE_01__share-intent.md](PHASE_01__share-intent.md) |
| 02 | link-extraction | - | ✅ Done | 4/4 | [PHASE_02__link-extraction.md](PHASE_02__link-extraction.md) |
| 03 | bot-upload | 01 | ⏭️ Skipped | 0/4 | [PHASE_03__bot-upload.md](PHASE_03__bot-upload.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 (share-intent) has no blocking research and may proceed independently. Phases 02 and 03 are gated below.

- [x] **Research:** lightweight public t.me post extraction path (§6.1). **Resolved 2026-05-30:** fetch `https://t.me/<channel>/<id>?embed=1` and Jsoup-parse `tgme_widget_message_photo_wrap` (background-image URL) + `tgme_widget_message_video` (src); single media → `direct.open(cdnUrl, onProgress, Referer=https://t.me/)`, album (`tgme_widget_message_grouped`) → `OpenResult.Batch`. **Caveat:** exact `tgme_widget_message_*` selectors / CDN hosts were NOT live-verified (WebFetch unavailable in research env) - High-risk per research; pin via canned fixture + verify against a real `curl …?embed=1` before relying in production. **Correction:** `"ytdlp"` is NOT a working fallback for `t.me` (its probe returns NotApplicable for generic-only matches); real fallbacks are `"html"`/`"dynamic"`. The lightweight native strategy is effectively the sole Telegram-aware path.
- [x] **Research:** supported t.me link forms and canonicalization (§6.2). **Resolved 2026-05-30:** support `t.me/<channel>/<id>`; canonicalize `t.me/s/<channel>/<id>` and `telegram.me/...` into it (in `LinkUrlCanonicalizer`); reject private `t.me/c/...` (`telegram_private_channel`), invites `t.me/+…`/`joinchat` (`telegram_invite_link`), bare profiles (`telegram_no_post_id`), bot deep links (`telegram_bot_deeplink`). Accept regex: `^/(?!s/|c/|joinchat/|\+)([A-Za-z0-9_]{5,32})/(\d+)$` on the canonical `t.me` path.
- [x] **Decision:** send-path scope for first iteration - share-intent only, or share-intent + Bot API (§6.3). **Resolved 2026-05-30: share-intent only. Phase 03 ⏭️ Skipped.**
- [x] **Decision:** Bot API configuration UX - token entry, recipient target, error/limit handling (§6.4). **Resolved 2026-05-30: N/A this iteration (Bot API deferred with Phase 03).**

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated for the send-to-Telegram capability (strategic §8 mandates it).
- [ ] `docs/FEATURES_noLegal.md` + `_RU` + `_UK` updated for the t.me download capability (gitignored).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0303` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0303`.

---

## Blockers Log

- 2026-05-30 - Phases 02 and 03 blocked at authoring: §6 research/decision items Open. Phase 02 needs §6.1 + §6.2 resolved; Phase 03 needs §6.3 + §6.4. Next: resolve via research + owner decision, then `/spec-update S0303` to mark items Resolved and uncheck blockers.
- 2026-05-30 - Owner decisions taken: §6.3 share-intent only → Phase 03 ⏭️ Skipped; §6.4 N/A. §6.1/§6.2 research resolved. Steps 01.1, 01.2, 02.1 done (backend + ordering token). **Two open items surfaced for owner before continuing:**
  - **Step 01.3 under-scoped + needs /ui-clarify finalization.** Tactical Files Touched lists only `BrowseShareOperationsHelper.kt` + `PlayerShareManager.kt`, but a dedicated "Send to Telegram" action requires: player overflow command infra (`overflow_menu_player.xml` item + `CommandPanelLayoutPlanner.PlayerCommand` entry with a `telegramInstalled` visibility flag + `CommandPanelAvailabilityUpdater` flag plumbing + `CommandPanelController` dispatch & `Callback` method + `PlayerCommandPanelCallbackImpl` impl), and a Browse surface. Browse multi-select uses a fixed toolbar/button surface (not a PopupMenu) → a dedicated button there pulls in layout + `res/layout-land` parity (Rule 12) the plan never modeled. No Telegram icon asset exists (would reuse `ic_share`). Needs `/spec-update --tactical --phase 01` to expand Files Touched + `/ui-clarify` to pin: player overflow-only vs bar button; Browse per-file overflow menu vs multi-select toolbar button; icon.
  - **Step 02.2 markup verification gap.** `tgme_widget_message_*` selectors / CDN hosts were not live-verified (WebFetch unavailable in research env). Implementable against documented stable selectors with a canned-fixture unit test + graceful `NotFound` degradation, but should be confirmed against a real `t.me/<channel>/<id>?embed=1` response before production reliance.

---

## Change Log

- 2026-05-30 - Initial tactical plan authored by `/spec-tech`.
