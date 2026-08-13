# Спецификация (compact bugfix): S0917 - Клик по хлебной крошке произвольной глубины не работает для облачного ресурса

**Ticket:** S0917
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-03
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-11 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, при тактическом планировании S0906 (cloud-title-internal-id)

**Текст:**

При исследовании причины показа внутреннего идентификатора облака в заголовке/хлебных крошках браузера (S0906) обнаружена смежная, но отдельная проблема: `BrowseNavigationManager.navigateToDepth(depth: Int)` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt:232-275`) - обработчик клика по ПРОИЗВОЛЬНОМУ (не соседнему) уровню хлебных крошек - требует, чтобы `currentPath.startsWith(resourcePath)` (строка 239), чтобы вычислить целевой путь. Для облачных ресурсов путь плоский (`cloud://provider/<id>`), поэтому это условие всегда ложно - метод логирует предупреждение (строка 242: `Timber.w("BrowseNavigationManager.navigateToDepth: path mismatch")`) и молча ничего не делает вместо перехода.

Это отдельная, более глубокая проблема (сломанный переход при клике, а не только неверный отображаемый текст) - фикс S0906 её не затрагивает (S0906 чинит только отображаемые имена, не логику вычисления целевого пути для произвольной глубины). Требует отдельного решения: реконструкции целевого пути для плоской id-based иерархии облачного провайдера (вероятно, через стек путей `pathStack`/`pathNameStack`, а не через попытку вычислить путь из строки).

---

## 1. Проблема / симптом

- Пользователь в браузере облачного ресурса (Google Drive/Dropbox/OneDrive) заходит на 2+ уровня вглубь.
- Клик по промежуточной хлебной крошке (не «Назад», а конкретное имя папки в середине цепочки) не выполняет переход - экран остаётся на текущей папке.
- Обратной связи пользователю нет: `Timber.w(".. path mismatch")` пишется только в лог.
- Воспроизводится для всех облачных провайдеров одинаково: причина в плоском `cloud://provider/<id>` пути, общем для всех трёх.
- Локальные/сетевые (иерархические) ресурсы не затронуты - там строковый разбор пути совпадал по совпадению.

---

## 2. Корневая причина

- `navigateToDepth` вычисляла целевой путь строковым разбором: `currentPath.startsWith(resourcePath)` -> `removePrefix` -> `split("/")`.
- Для облака сегмент пути - непрозрачный id провайдера, а не имя папки; `currentPath` (`cloud://provider/<childId>`) не начинается с `resource.path` (`cloud://provider/<rootId>`), поэтому `startsWith` всегда ложно -> ранний `return` (no-op).
- Реальная история переходов уже хранится в `BrowseState.pathStack` (путь на каждом уровне) и `folderNameStack` (имя на каждом уровне), заполняемых единообразно для всех типов ресурсов (см. `navigateToFolder`/`navigateBack`/`navigateUp`).
- Строковый разбор был лишним: цель уровня `depth` доступна по индексу в этих стеках без парсинга.

---

## 3. Исправление

Реконструировать цель из отслеживаемых стеков по индексу глубины, без строкового разбора `currentPath` (`BrowseNavigationManager.navigateToDepth`).

- Собрать полную цепочку путей от корня до текущей папки: `fullPaths = pathStack + currentPath` (индекс == глубина; `fullPaths[0]` == корень ресурса).
- Собрать параллельную цепочку имён: `fullNames = folderNameStack + currentFolderName` (имя для глубины `d >= 1` лежит в `fullNames[d-1]`).
- Валидировать `depth` по границам `0 until fullPaths.size`; клик по текущему (последнему) уровню - ранний no-op.
- Целевое состояние: `currentPath = fullPaths[depth]`, `pathStack = fullPaths.take(depth)`, `folderNameStack = fullNames.take(depth-1)`, `currentFolderName = fullNames.getOrNull(depth-1)` (для `depth == 0` - корень: `null`/пустые стеки).
- `cancelLoad()` перед сменой состояния, затем `loadDirectoryContents(targetPath)` - как в остальных методах навигации.
- Подход path-agnostic: одинаково корректен для облачных плоских id-путей и локальных/сетевых иерархических.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0906 (cloud-title-internal-id) - источник обнаружения, смежная область (BrowseNavigationManager).

---

## 4. Проверка

- Unit-тест `BrowseNavigationManagerNavigateToDepthTest` (детерминированная проверка логики реконструкции):
  - Облачный ресурс (`cloud://provider/root`), 3 уровня вглубь с id-путями; клик на `depth = 1` -> `currentPath` == путь 1-го уровня, стеки срезаны корректно (регрессия - старый код был no-op).
  - Клик на `depth = 0` -> корень ресурса, пустые стеки, `currentFolderName == null`.
  - Локальный иерархический ресурс - переход на промежуточную глубину не сломан.
  - Некорректная `depth` (за границей) -> no-op, состояние не изменилось.
- Устройство (опционально, если доступен вошедший облачный аккаунт): навигация 3+ уровня вглубь в облачном ресурсе, клик на промежуточную крошку -> переход, не молчаливый no-op.

---

## Last Audit

**Date:** 2026-07-11 | **Status:** Verified | **Auditor:** /spec-all

- Fix implemented in `BrowseNavigationManager.navigateToDepth` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt`): target now reconstructed from `pathStack + currentPath` / `folderNameStack + currentFolderName` by breadcrumb index; removed the `currentPath.startsWith(resourcePath)` string-parse and the `path mismatch` silent-return no-op.
- Verified by unit test `BrowseNavigationManagerNavigateToDepthTest` (6 cases): cloud intermediate depth navigates (regression), cloud root reset, deeper cloud intermediate, local hierarchical still works, out-of-range no-op, current-level self-click no-op. `:app_v2:testStandardDebugUnitTest --tests "*BrowseNavigationManagerNavigateToDepthTest*"` -> BUILD SUCCESSFUL (exit 0).
- Path-agnostic reconstruction covers all three cloud providers (shared flat `cloud://provider/<id>` scheme) plus local/network - no per-provider branch.
- Layer discipline OK: logic stays in the navigation Manager; UI (`BreadcrumbView`/`BrowseManagerInitializer`) only forwards the clicked depth. No listener/lifecycle/Room/player surface touched.
- No device gate: deterministic logic fully covered by the unit test, so no `BlockNeedUserTest` / Timber probe required.
