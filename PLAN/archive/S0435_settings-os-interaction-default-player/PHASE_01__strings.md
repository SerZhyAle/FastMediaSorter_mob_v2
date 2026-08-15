# Phase 01 - Strings

**Strategic spec:** [`../S0435_settings-os-interaction-default-player.md`](../S0435_settings-os-interaction-default-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Rename three group/subgroup titles in place and add the five new strings (default-player subgroup title + four button labels) across EN/RU/UK. No code or layout changes.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | n/a |

> The three renamed keys live across `strings.xml` (`setting_group_system_apps_title`, `setting_subgroup_screen_gestures_title`) and `strings_settings.xml` (`settings_subcategory_incoming_links`). `set-android-string.ps1` resolves the owning file automatically. Add the five new keys with `-Action add` (writes EN/RU/UK in lockstep).
>
> **Cyrillic boundary (agent-memory):** never pass RU/UK literals as `pwsh` CLI args through the Bash tool - mojibake. Run `set-android-string.ps1` via the PowerShell tool directly, or author a UTF-8 `.ps1` under `temp/` with `Write` and execute it. Verify written values with `Grep`/`Read`, not console echo.

---

## Steps

### Step 01.1 - Rename three titles in place (EN/RU/UK)

**Files:** `app_v2/src/main/res/values*/strings.xml`, `app_v2/src/main/res/values*/strings_settings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the VALUE (keep the key) of three existing keys across all three locales using `scripts/utils/set-android-string.ps1 -Action set` (one call per key per locale; the script finds the owning file). No layout edits - layouts already reference these keys.
> - `setting_group_system_apps_title`: EN `Operating system interaction` · RU `Взаимодействие с операционной системой` · UK `Взаємодія з операційною системою`
> - `setting_subgroup_screen_gestures_title`: EN `Left-edge screen gestures` · RU `Жесты с левого края экрана` · UK `Жести з лівого краю екрана`
> - `settings_subcategory_incoming_links`: EN `Incoming link handling` · RU `Реакция на входящие ссылки` · UK `Реакція на вхідні посилання`
>
> The screen-gestures and incoming-links headers render with `textAllCaps="true"`, so the visible text is uppercased automatically - author normal case. Use grammatically correct `по умолчанию` (no hyphen) anywhere it appears. Strings pass `docs/COMMUNICATION_POLICY.md` §2 (label formula) and §6 (tone checklist).

**Verification:**

- `Grep` - `setting_group_system_apps_title">Operating system interaction` in `values/strings.xml`.
- `Grep` - `Взаимодействие с операционной системой` in `values-ru/strings.xml`.
- `Grep` - `Реакция на входящие ссылки` in `values-ru/strings_settings.xml`.
- `Grep` - `Left-edge screen gestures` in `values/strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_group_system_apps_title"` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification 5/5 PASS. Renamed 3 titles x 3 locales (strings.xml + strings_settings.xml). Localization audit exit 0.

---

### Step 01.2 - Add default-player subgroup title + four button labels (EN/RU/UK)

**Files:** `app_v2/src/main/res/values*/strings_settings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add five new keys with `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>` (parity-enforced, one call per key). Button labels are sized to their own text in layout - keep them short.
> - `setting_subgroup_default_player_title`: EN `Default app` · RU `Программа по умолчанию` · UK `Програма за замовчуванням`
> - `settings_default_player_btn_images`: EN `Default image viewer` · RU `Просмотр изображений по умолчанию` · UK `Перегляд зображень за замовчуванням`
> - `settings_default_player_btn_audio`: EN `Default audio player` · RU `Аудиоплеер по умолчанию` · UK `Аудіоплеєр за замовчуванням`
> - `settings_default_player_btn_video`: EN `Default video player` · RU `Видеоплеер по умолчанию` · UK `Відеоплеєр за замовчуванням`
> - `settings_default_player_btn_docs`: EN `Default document viewer` · RU `Программа для просмотра по умолчанию` · UK `Програма для перегляду за замовчуванням`
>
> The on-page instruction text reuses the existing `welcome_default_player_hint` (no new key) so the settings copy stays in sync with the welcome page. Strings pass `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Grep` - `settings_default_player_btn_images` present in `values/strings_settings.xml`, `values-ru/strings_settings.xml`, `values-uk/strings_settings.xml` (three hits).
- `Grep` - `setting_subgroup_default_player_title">Программа по умолчанию` in `values-ru/strings_settings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_default_player_btn_"` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification 3/3 PASS. Added subgroup title + 4 button labels x 3 locales in strings_settings.xml. Parity exit 0.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (resource link).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every modified strings file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All keys consumed by Phase 02 layout now exist: three renamed titles (keys unchanged), `setting_subgroup_default_player_title`, four `settings_default_player_btn_*`, and the reused `welcome_default_player_hint`.

---

## Rollback Plan

Revert phase commit(s) - string-only change, no data migration or user-facing surface beyond text.
