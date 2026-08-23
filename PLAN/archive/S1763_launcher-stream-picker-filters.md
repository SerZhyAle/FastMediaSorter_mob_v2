# Стратегическая спецификация: S1763 - Фильтры в диалоге выбора трансляции

**Ticket:** S1763
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-16
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - эпик S1615 (кластер C-27)

---

## Goal

1. В диалоге выбора трансляции есть фильтр по типу (Все / Видео / Аудио).
2. Есть фильтр по рубрике / теме (Topic / Category).
3. Есть фильтр по языку трансляции (Language).
4. Фильтры комбинируются с текстовым поиском.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1615 (родительский эпик, запись L-038), S1762.
- **UI placement contract:** фильтры интегрированы в верхнюю панель `LauncherStreamPickerDialogFragment`.
- **Validation level:** фильтры по типу, теме и языку сужают список в сочетании с поисковой строкой.
- **Owner sign-off:** делегировано конвейеру /spec-all эпика S1615 - 2026-08-16.

<!-- auto-approved by /spec-all - 2026-08-18 -->

---

# Phase 01 - Stream Selection Dialog Filtering

**Strategic spec:** `PLAN/S1763_launcher-stream-picker-filters.md`
**Status:** ✅ Done

## Objective

Add media kind toggle group (All / Audio / Video), topic dropdown, and language dropdown to `LauncherStreamPickerDialogFragment`, combining all active filter facets with the search field.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/dialog_launcher_stream_picker.xml` | New | ≤ 100 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherStreamPickerDialogFragment.kt` | Modified | ≤ 250 |

## Steps

### Step 01.1 - Create dialog_launcher_stream_picker layout with filter controls

**Files:** `app_v2/src/launcherEnabled/res/layout/dialog_launcher_stream_picker.xml`

**Prompt for developer:**

> Create `dialog_launcher_stream_picker.xml` containing `tvOptionPickerTitle`, media kind toggle group (`toggleMediaKind`), topic spinner (`spinnerTopic`), language spinner (`spinnerLanguage`), search layout (`layoutOptionSearch`, `editOptionSearch`), RecyclerView (`recyclerOptions`), and empty label (`tvOptionsEmpty`).

**Why:**

Provides dedicated UI for stream filtering without affecting other option pickers using `dialog_searchable_option_picker.xml`.

**Verification:**

- File exists and compiles cleanly.

**Status:** `[x]` done

---

### Step 01.2 - Implement facet filtering and multi-criterion search in LauncherStreamPickerDialogFragment

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherStreamPickerDialogFragment.kt`

**Prompt for developer:**

> Update `LauncherStreamPickerDialogFragment` to inflate `dialog_launcher_stream_picker.xml`, extract available topics and languages from stream sources, set up listeners on toggleMediaKind, spinnerTopic, spinnerLanguage, and editOptionSearch, and dynamically filter stream options.

**Why:**

Allows users to narrow down large stream catalogs by media type, topic/category, language, and search query.

**Verification:**

- `.\a.ps1 fk` compiles cleanly.
- Stream options update dynamically when filter buttons or dropdowns are changed.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every Step 01.* above is `[x]` done.
- [x] Project compiles cleanly.
