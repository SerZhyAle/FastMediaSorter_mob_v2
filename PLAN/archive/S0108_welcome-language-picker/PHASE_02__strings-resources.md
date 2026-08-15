# Phase 02 — Strings: Accessibility Label

**Strategic spec:** [`../S0108_welcome-language-picker.md`](../S0108_welcome-language-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Add string key `welcome_language_picker_hint` in all three locale files (EN / RU / UK). This key is referenced as `contentDescription` on the language picker buttons in `page_welcome_enhanced.xml`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 1500 |

---

## Steps

### Step 02.1 — Add string key to EN locale

**Files:** `app_v2/src/main/res/values/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `values/strings.xml`, locate the block of `welcome_*` strings (around line 829, near `welcome_title_1`). Append the following entry after the last `welcome_*` string in that cluster:
>
> ```xml
> <string name="welcome_language_picker_hint">Select app language</string>
> ```

**Verification:**

- `Grep` — `welcome_language_picker_hint` appears exactly once in `values/strings.xml`.
- `Grep` — value is `Select app language` in that file.

**Status:** `[ ]` not done

---

### Step 02.2 — Add string key to RU locale

**Files:** `app_v2/src/main/res/values-ru/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `values-ru/strings.xml`, locate the corresponding `welcome_*` cluster. Append:
>
> ```xml
> <string name="welcome_language_picker_hint">Выберите язык приложения</string>
> ```

**Verification:**

- `Grep` — `welcome_language_picker_hint` appears exactly once in `values-ru/strings.xml`.
- `Grep` — value contains `Выберите язык` in that file.

**Status:** `[ ]` not done

---

### Step 02.3 — Add string key to UK locale

**Files:** `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `values-uk/strings.xml`, locate the corresponding `welcome_*` cluster. Append:
>
> ```xml
> <string name="welcome_language_picker_hint">Оберіть мову застосунку</string>
> ```

**Verification:**

- `Grep` — `welcome_language_picker_hint` appears exactly once in `values-uk/strings.xml`.
- `Grep` — value contains `Оберіть мову` in that file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every Step above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] String locale audit passes: run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_language_picker"` — exit code must be 0.
- [ ] Dev log entry added for each file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `R.string.welcome_language_picker_hint` is available for use in `EnhancedViewHolder` (Phase 03) if needed in code, and is already referenced in the layout XML.

---

## Rollback Plan

Remove the three appended string entries. No Kotlin or layout changes. Zero risk.
