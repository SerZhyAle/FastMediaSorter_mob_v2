# Phase 03 — strings

**Strategic spec:** [`../S0183_nolegal-apk-install.md`](../S0183_nolegal-apk-install.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Add all `s0183_*` string keys in EN, RU, and UK `strings.xml` files. All keys must be present in all three locales before Phase 04 compiles.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +8 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +8 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +8 lines |

---

## Steps

### Step 3.1 — Add trilingual `s0183_*` string keys

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following 8 string keys to **all three** `strings.xml` files. Place them in a group after existing `s0177_*` keys (or at end of file before `</resources>`). Keys and English values:
>
> ```xml
> <!-- S0183: APK install from Browse (noLegal) -->
> <string name="s0183_apk_install_action">Install</string>
> <string name="s0183_apk_install_rationale_title">Install unknown apps</string>
> <string name="s0183_apk_install_rationale_message">To install APK files, allow this app to install apps from unknown sources in Settings.</string>
> <string name="s0183_apk_install_rationale_btn_settings">Open Settings</string>
> <string name="s0183_apk_install_rationale_btn_cancel">Cancel</string>
> <string name="s0183_apk_install_success">APK installed successfully</string>
> <string name="s0183_apk_install_cancelled">Installation cancelled</string>
> <string name="s0183_apk_install_failed">Installation failed</string>
> ```
>
> **RU translations:**
> ```xml
> <!-- S0183: APK install from Browse (noLegal) -->
> <string name="s0183_apk_install_action">Установить</string>
> <string name="s0183_apk_install_rationale_title">Установка из неизвестных источников</string>
> <string name="s0183_apk_install_rationale_message">Чтобы устанавливать APK-файлы, разрешите приложению устанавливать приложения из неизвестных источников в Настройках.</string>
> <string name="s0183_apk_install_rationale_btn_settings">Открыть настройки</string>
> <string name="s0183_apk_install_rationale_btn_cancel">Отмена</string>
> <string name="s0183_apk_install_success">APK установлен</string>
> <string name="s0183_apk_install_cancelled">Установка отменена</string>
> <string name="s0183_apk_install_failed">Ошибка установки</string>
> ```
>
> **UK translations:**
> ```xml
> <!-- S0183: APK install from Browse (noLegal) -->
> <string name="s0183_apk_install_action">Встановити</string>
> <string name="s0183_apk_install_rationale_title">Встановлення з невідомих джерел</string>
> <string name="s0183_apk_install_rationale_message">Щоб встановлювати APK-файли, дозвольте додатку встановлювати програми з невідомих джерел у Налаштуваннях.</string>
> <string name="s0183_apk_install_rationale_btn_settings">Відкрити налаштування</string>
> <string name="s0183_apk_install_rationale_btn_cancel">Скасувати</string>
> <string name="s0183_apk_install_success">APK встановлено</string>
> <string name="s0183_apk_install_cancelled">Встановлення скасовано</string>
> <string name="s0183_apk_install_failed">Помилка встановлення</string>
> ```
>
> **Communication Policy check (COMMUNICATION_POLICY.md §2 + §6):**
> - `s0183_apk_install_success` / `_cancelled` / `_failed` are result feedback messages — use past tense, no exclamation marks, no blame.
> - `s0183_apk_install_rationale_message` is a permission rationale — explain what the user gains, not what the app needs.
> - Tone checklist §6: no "please", no "sorry", no urgency markers, no tech jargon for user-facing text.

**Verification:**

- `Grep` in `values/strings.xml` — `s0183_apk_install_action` present.
- `Grep` in `values-ru/strings.xml` — `s0183_apk_install_action` present.
- `Grep` in `values-uk/strings.xml` — `s0183_apk_install_action` present.
- `Grep` in all three files — count of `s0183_` matches equals 8 in each.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0183_"` — exit code 0.
- Strings pass `COMMUNICATION_POLICY.md` §6 tone checklist.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 5/5 PASS. 8 keys × 3 locales. check_strings_localized exit 0. CommunicationPolicy §6: "APK installed successfully" → rewritten to "APK installed" (no "successfully"). Dev log recorded.

---

## Phase Done Criteria

- [ ] Step 3.1 is `[x] done`.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0183_"` exits 0.
- [ ] Project compiles after adding strings.
- [ ] Dev log entry added for each of the three strings.xml files.

---

## Handoff Notes to Next Phase

All `R.string.s0183_*` references in `BrowseApkInstallHandlerImpl` now resolve. Phase 04 (ui-integration) can compile without stubs.

---

## Rollback Plan

Remove the 8 `s0183_*` entries from each of the three files. No data migration.
