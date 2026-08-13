# Phase 01 — Add Sub-section Header Strings (EN/RU/UK)

**Strategic spec:** [`../S0121_settings-general-tab-wave1-visual-grouping.md`](../S0121_settings-general-tab-wave1-visual-grouping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** —
**Depends on:** —
**Blocks:** Phase 02, Phase 03

---

## Objective

Add five new string keys used as sub-section header labels in the General tab. All three locale files must be updated atomically.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 5 lines added |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 5 lines added |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 5 lines added |

---

## Steps

### Step 1.1 — Add strings to EN

**File:** `app_v2/src/main/res/values/strings.xml`

**Depends on:** — (start of phase)

Add the following five keys adjacent to the existing `settings_category_*` block:

```xml
<string name="settings_section_network_actions">Network Actions</string>
<string name="settings_section_cache_management">Cache Management</string>
<string name="settings_section_settings_data">Settings Data</string>
<string name="settings_section_cloud_backup">Cloud Backup</string>
<string name="settings_category_about">About</string>
```

**Verification:**
- `Grep` — `settings_section_network_actions` found in `values/strings.xml`.
- `Grep` — `settings_category_about` found in `values/strings.xml`.

**Status:** —

---

### Step 1.2 — Add strings to RU

**File:** `app_v2/src/main/res/values-ru/strings.xml`

**Depends on:** Step 1.1

Add the same five keys with Russian translations:

```xml
<string name="settings_section_network_actions">Сетевые действия</string>
<string name="settings_section_cache_management">Управление кэшем</string>
<string name="settings_section_settings_data">Данные настроек</string>
<string name="settings_section_cloud_backup">Резервное копирование</string>
<string name="settings_category_about">О приложении</string>
```

**Verification:**
- `Grep` — `settings_section_network_actions` found in `values-ru/strings.xml`.
- `Grep` — `settings_category_about` found in `values-ru/strings.xml`.

**Status:** —

---

### Step 1.3 — Add strings to UK

**File:** `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 1.2

Add the same five keys with Ukrainian translations:

```xml
<string name="settings_section_network_actions">Мережеві дії</string>
<string name="settings_section_cache_management">Керування кешем</string>
<string name="settings_section_settings_data">Дані налаштувань</string>
<string name="settings_section_cloud_backup">Резервне копіювання</string>
<string name="settings_category_about">Про застосунок</string>
```

**Verification:**
- `Grep` — `settings_section_network_actions` found in `values-uk/strings.xml`.
- `Grep` — `settings_category_about` found in `values-uk/strings.xml`.

**Status:** —

---

## Phase Done Criteria

- [ ] All five keys present in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_section_"` exits 0.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_about"` exits 0.
- [ ] Dev log entry added.
