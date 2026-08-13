# S0524 - Dead BrowseSelectionManager stub in managers package

**Status:** Archived
**Priority:** 30
**Date:** 2026-06-19
**Tier:** 1 - Trivial (ad-hoc)
**Origin:** parked by /spec-all during S0512 research (2026-06-19)

<!-- auto-approved by /spec-all - 2026-06-19 -->

---

## Goal

Удалить мёртвый файл `ui/browse/managers/BrowseSelectionManager.kt` (37 LOC) - пустой shell с no-op `initialize()`, набором `selectedFiles`, который никогда не заполняется, и вложенным интерфейсом `SelectionCallbacks`, который нигде не используется. Боевая реализация живёт в `ui/browse/selection/BrowseSelectionManager.kt` и потребляется через `BrowseLifecycleSetupManager`. Stub не зарегистрирован в DI и недостижим; его удаление убирает dead weight и устраняет риск коллизии имён двух классов `BrowseSelectionManager`. CLAUDE.md Rule 20 (dead-weight hygiene).

---

## 0. Идея (исходная, raw)

Найдено при research S0512.

Symptom: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSelectionManager.kt` (37 LOC) - пустой shell с no-op `initialize()` / `cleanup()`, `mutableSetOf<String>()`, который никогда не заполняется, и полем `selectedFiles`, которое никогда не читается. Настоящая реализация живёт в `ui/browse/selection/BrowseSelectionManager.kt`.

Stub не зарегистрирован ни в одном DI-модуле и недостижим из `BrowseViewModel` (который напрямую использует класс из `selection/`). Dead weight + риск коллизии имён (два класса `BrowseSelectionManager` в разных пакетах).

Evidence:
- `ui/browse/managers/BrowseSelectionManager.kt:1-37`
- `BrowseViewModel.kt:120` использует `com.sza.fastmediasorter.ui.browse.selection.BrowseSelectionManager`

Действие: удалить stub, проверить отсутствие ссылок, собрать standard debug. CLAUDE.md Rule 20 (dead-weight hygiene).

---

## Phase 1 - Confirm dead and delete stub

1. Confirm no consumer imports `com.sza.fastmediasorter.ui.browse.managers.BrowseSelectionManager`.
   - Verification: Grep `browse\.managers\.BrowseSelectionManager` over `app_v2/src` returns zero matches.
2. Confirm the nested `SelectionCallbacks` interface from the stub is unused outside the stub file.
   - Verification: Grep `SelectionCallbacks` returns only matches inside the stub file itself.
3. Confirm the live implementation and its sole consumer reference the `selection/` package.
   - Verification: `BrowseLifecycleSetupManager.kt` imports `com.sza.fastmediasorter.ui.browse.selection.BrowseSelectionManager`.
4. Delete the file `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSelectionManager.kt`.
   - Verification: file no longer exists; Glob returns no path.
5. Re-run the catalog sync for `app_v2` so the deleted class drops from the local index.
   - Verification: `catalog_sync.ps1 -Module app_v2` exits 0.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0512 (parked during its research)

---

## Last Audit

**Date:** 2026-06-19 | **Verdict:** Verified | **Build:** skipped (NO BUILD directive; deletion compile-safe - zero referencing sites)

- Deleted `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSelectionManager.kt` (37 LOC dead stub).
- Grep `browse.managers.BrowseSelectionManager` across `app_v2/src` -> 0 matches (no consumer).
- Grep `SelectionCallbacks` -> 0 matches (interface was self-contained in the stub).
- Live implementation `ui/browse/selection/BrowseSelectionManager.kt` untouched; sole consumer `BrowseLifecycleSetupManager` still imports the `selection/` class.
- Catalog re-synced: only `selection/BrowseSelectionManager.kt` remains; the `managers/` record dropped.
- No user-visible capability delivered (internal dead-weight removal) - no `ALL_FEATURES.jsonl` record required.
