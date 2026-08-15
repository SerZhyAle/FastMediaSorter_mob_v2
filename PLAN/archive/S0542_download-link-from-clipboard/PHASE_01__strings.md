# Phase 01 - Strings

**Strategic spec:** [`../S0542_download-link-from-clipboard.md`](../S0542_download-link-from-clipboard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** -
**Completed:** -

---

## Objective

Add trilingual (EN/RU/UK) string resources for the new menu entry and the link-input dialog. No code consumes them yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +3 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +3 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +3 |

---

## Steps

### Step 01.1 - Add menu label and dialog strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three string keys in lockstep across EN/RU/UK using one call per key of `scripts/utils/set-android-string.ps1 -Action add` (parity-enforced). Keys and values:
> - `download_by_link_menu_label` - EN `Download by link`, RU `Загрузить по ссылке`, UK `Завантажити за посиланням`.
> - `download_by_link_dialog_title` - EN `Link to download`, RU `Ссылка для загрузки`, UK `Посилання для завантаження`.
> - `download_by_link_dialog_hint` - EN `Paste or type a link`, RU `Вставьте или введите ссылку`, UK `Вставте або введіть посилання`.
> Use Russian Ё/ё where grammatically correct. The OK / Cancel buttons reuse existing platform strings (`android.R.string.ok` / `android.R.string.cancel`) - do not add new ones. Strings pass `docs/COMMUNICATION_POLICY.md` §2 (label/title formula) and §6 (tone checklist).

**Verification:**

- `Grep` - `download_by_link_menu_label` matches in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (3 hits).
- `Grep` - `download_by_link_dialog_title` matches in all three locale files (3 hits).
- `Grep` - `download_by_link_dialog_hint` matches in all three locale files (3 hits).
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "download_by_link"` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "download_by_link"` exits 0.
- [ ] Dev log entry added for the string change via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Three keys (`download_by_link_menu_label`, `download_by_link_dialog_title`, `download_by_link_dialog_hint`) exist on all locales and may be referenced by Phase 02 code.

---

## Rollback Plan

Remove the three keys from all three `strings.xml` files - no data migration or runtime surface changed.
