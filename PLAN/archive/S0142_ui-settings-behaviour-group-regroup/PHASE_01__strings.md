# Phase 01 — Strings

**Strategic spec:** [`../S0142_ui-settings-behaviour-group-regroup.md`](../S0142_ui-settings-behaviour-group-regroup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Add four new string resources (two sub-section headers, one tooltip title, one tooltip message) to all three locale files. No layout or code changes yet.

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

---

## Steps

### Step 01.1 — Add four string keys to all three locale files

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add these four string keys with the same name in all three `strings.xml` files; place them near the existing `settings_category_behaviour` / `tooltip_*` keys:
> - `settings_subcategory_incoming_links` — short sub-section header for the link auto-download block. EN: `Incoming links`. RU: `Входящие ссылки`. UK: `Вхідні посилання`.
> - `settings_subcategory_camera_capture` — short sub-section header for the camera capture block. EN: `Camera capture`. RU: `Захват с камеры`. UK: `Захоплення з камери`.
> - `tooltip_saved_authorizations_title` — title of the help tooltip for the saved-authorizations row. EN: `Saved authorizations`. RU: `Сохранённые авторизации`. UK: `Збережені авторизації`.
> - `tooltip_saved_authorizations_message` — body of that tooltip. EN: explain that the in-app web sign-in screen captures cookies/logins used when auto-downloading files from sites that require login, and that this screen lets you review and delete them. RU/UK: equivalent. Two short sentences max.
> Apply `docs/COMMUNICATION_POLICY.md` §2 (informational/help message formula) and §6 tone checklist to all user-visible text. Keep `..` (not `...`) and `ё`/`Ё` in Russian.

**Verification:**

- `Grep -n "settings_subcategory_incoming_links"` — matches in all three `strings.xml` files (3 hits).
- `Grep -n "settings_subcategory_camera_capture"` — matches in all three `strings.xml` files (3 hits).
- `Grep -n "tooltip_saved_authorizations_title"` — matches in all three `strings.xml` files (3 hits).
- `Grep -n "tooltip_saved_authorizations_message"` — matches in all three `strings.xml` files (3 hits).
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_subcategory_"` — exit code 0.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "tooltip_saved_authorizations"` — exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 6/6 PASS. Added `settings_subcategory_incoming_links`, `settings_subcategory_camera_capture`, `tooltip_saved_authorizations_title`, `tooltip_saved_authorizations_message` to values/, values-ru/, values-uk/ strings.xml. `check_strings_localized.ps1` OK for both prefixes. Dev log recorded.

---

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] `scripts/check_strings_localized.ps1` passes for both prefixes (exit code 0).
- [x] Dev log entry added for each modified `strings.xml` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 02 references `@string/settings_subcategory_incoming_links`, `@string/settings_subcategory_camera_capture`, `@string/tooltip_saved_authorizations_title`, `@string/tooltip_saved_authorizations_message`. They must resolve before Phase 02 builds.

---

## Rollback Plan

Revert phase commit — string additions only, no user-facing surface yet.
