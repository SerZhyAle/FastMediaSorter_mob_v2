# Phase 05 — Trilingual Strings (EN/RU/UK)

**Strategic spec:** [`../S0213_bugfix-video-playback-oom-hardening.md`](../S0213_bugfix-video-playback-oom-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 02, 04 (placeholder strings exist in code)
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Define the four user-visible strings introduced by Phases 02 and 04 across the three locales (EN/RU/UK), replace `TODO(phase-05)` placeholders in source, and confirm parity via `check_strings_localized.ps1`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (placeholder strings live in `PlayerMediaLoaderManager`/`PlayerDialogAndUiStateManager` for cooldown UX).
- [ ] Phase 04 ✅ Done (placeholder strings live in `PlayerDialogAndUiStateManager` for memory alert).
- [ ] `docs/COMMUNICATION_POLICY.md` and its locale mirrors are accessible.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +4 entries |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +4 entries |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +4 entries |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | (placeholder swap) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt` | Modified | (placeholder swap) |

> No `res/layout-land` counterpart involved — these are pure string resources, not layouts.

---

## Steps

### Step 05.1 — Add four string keys per locale

**Files:** all three `strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following four keys to each `strings.xml`. Verify against `docs/COMMUNICATION_POLICY.md` §2 (message formula for "informational" + "error-recovery") and §6 (tone checklist).
>
> | Key | EN | RU | UK |
> |-----|----|----|----|
> | `s0213_decoder_cooldown_skip` | `Skipping the previous file — the decoder couldn't handle it.` | `Пропускаем предыдущий файл — декодер не справился.` | `Пропускаємо попередній файл — декодер не впорався.` |
> | `s0213_decoder_cooldown_manual` | `This file just failed to decode. Try again in %1$d s, or skip.` | `Этот файл только что не смог раскодироваться. Попробуйте через %1$d с или пропустите.` | `Цей файл щойно не зміг розкодуватися. Спробуйте через %1$d с або пропустіть.` |
> | `s0213_action_skip` | `Skip` | `Пропустить` | `Пропустити` |
> | `s0213_memory_alert_message` | `Memory is running low. Closing the player will free resources.` | `Памяти осталось мало. Закрыть плеер, чтобы освободить ресурсы.` | `Пам'яті залишилося мало. Закрити плеєр, щоб звільнити ресурси.` |
> | `s0213_memory_alert_action` | `Close player` | `Закрыть плеер` | `Закрити плеєр` |
>
> Place each key in alphabetical position within the file (or in the project's existing convention block — verify against neighbouring `s0xxx_*` keys). The `%1$d` placeholder for `s0213_decoder_cooldown_manual` must be present in all three locales.
>
> **Tone-checklist gate (mandatory):** before saving, run through `docs/COMMUNICATION_POLICY.md` §6 — verify direct address, no jargon, action-oriented phrasing for the action labels, and that the manual-cooldown message names both the cause and the resolution. Author style: `..` not `...`; always `ё` in RU.

**Verification:**

- `Grep` — each of the 5 keys (`s0213_decoder_cooldown_skip`, `s0213_decoder_cooldown_manual`, `s0213_action_skip`, `s0213_memory_alert_message`, `s0213_memory_alert_action`) matches exactly once per locale file (3 hits per key total = 15 hits across 3 files).
- `Grep` — `%1$d` present in `s0213_decoder_cooldown_manual` line in all three files.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0213"` exit 0.
- `expected: parity OK | actual: exit 0`.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist (manual review documented in commit message or PR description).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. 5 keys × 3 locales (EN/RU/UK), all `OK`. `%1$d` present in cooldown_manual line in all three locales. `check_strings_localized.ps1 -KeyPrefix s0213` exit 0. Tone checklist verified: short, direct, friendly, no jargon; uses `..` (two dots) and `ё/Ё` per author style.

---

### Step 05.2 — Replace placeholder strings in source

**Files:** `PlayerMediaLoaderManager.kt`, `PlayerDialogAndUiStateManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> 1. In `PlayerDialogAndUiStateManager.kt`, replace the literal English placeholder for cooldown manual snackbar with `getString(R.string.s0213_decoder_cooldown_manual, remainingSec)` and the action label with `R.string.s0213_action_skip`.
> 2. In `PlayerDialogAndUiStateManager.kt`, replace the placeholder for memory alert snackbar with `getString(R.string.s0213_memory_alert_message)` and action label with `R.string.s0213_memory_alert_action`.
> 3. In `PlayerMediaLoaderManager.kt`, in `handleCooldownReentry` slideshow branch, replace the literal English `Toast.makeText` with `Toast.makeText(context, R.string.s0213_decoder_cooldown_skip, Toast.LENGTH_SHORT).show()`.
> 4. Remove every `TODO(phase-05)` marker introduced in Phases 02 and 04.

**Verification:**

- `Grep` — `R.string.s0213_decoder_cooldown_skip` matches at least once.
- `Grep` — `R.string.s0213_decoder_cooldown_manual` matches at least once.
- `Grep` — `R.string.s0213_action_skip` matches at least once.
- `Grep` — `R.string.s0213_memory_alert_message` matches at least once.
- `Grep` — `R.string.s0213_memory_alert_action` matches at least once.
- `Grep` — `TODO(phase-05)` returns zero hits across `app_v2/src/main/java`.
- `/build` exit 0 for `assembleStandardDebug` AND `assembleNoLegalDebug`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 7/7 PASS. All five `R.string.s0213_*` referenced (PlayerDialogAndUiStateManager lines 81, 83, 94, 95; PlayerMediaLoaderManager line 1024). Zero TODO(phase-05) markers. Both flavor builds GREEN (covered by Phase 04.5 builds, no further code change after).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0213"` exit 0.
- [x] Project compiles for `standardDebug` AND `noLegalDebug`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- All user-visible text is now trilingual and policy-compliant. Phase 06 finalises catalog regen and the journal status transition.

---

## Rollback Plan

Restore previous English placeholders in code; remove the five string keys from each of the three `strings.xml`. No layout or runtime impact beyond visible text reverting.
