# S0402 — Тактическая спецификация: страница разрешений в пейджере

**Status:** Tactical
**Strategic:** `PLAN/S0402_welcome-permissions-page.md`
**Research:** `research/05__permissions-ordering.md`

## Зафиксированные решения

- Позиция — индекс 4 (после functionality, перед default-player).
- Адаптивный набор (welcome-режим):
  - «Все файлы» (MANAGE_EXTERNAL_STORAGE, API30+) + MANAGE_MEDIA — только если `allFiles==true` (пресет профиля уже пишет `allFiles`, покрывает «подразумевающий профиль»).
  - RECORD_AUDIO — только если `supportAudio==true`.
  - POST_NOTIFICATIONS (33+) — общий батч в welcome-режиме (снятие flavor-гейта `ENABLE_PERSISTENT_AUDIO_PLAYBACK` только для welcome; настройки не трогаем).
  - Остальные READ_MEDIA_* / storage — по существующему реестру (SDK/flavor).
- Настройки — вход из настроек сохраняет ПОЛНЫЙ `getEntries()` (неадаптивный). Параметризация — новый `getWelcomeEntries(allFiles, audio)` в реестре; общий фрагмент получает adaptive-флаг.
- Чтение выборов функциональности — рантайм `settingsRepository.getSettings().first()` (`allFiles`, `supportAudio`); сборочной зависимости от S0400 нет.
- Хостинг (риск-ориентированно): permissions = страница пейджера; список рендерится в холдере (переиспользуем `PermissionRowAdapter`), grant-all делегируется `WelcomePermissionsManager`, владеющему ActivityResult-лаунчерами на `WelcomeActivity`. Сохранение состояния grant-all (спец-разрешения посреди прохода) — в менеджере (`onSaveInstanceState` Activity).
- Fallback при риске fragment-in-pager: оставить терминальный оверлей, но НЕ скрывать `layoutBottomNav`/индикатор (исправляет headline-баг criteria 1). Решение — на интеграции.
- НОВЫЙ landscape-layout (Rule 11 долг).

## Контракт файлов

NEW (disjoint, владелец — агент C):
- `res/layout/page_welcome_permissions.xml` + `layout-land/` → `PageWelcomePermissionsBinding`.
- `res/layout-land/fragment_permissions_management.xml` (Rule 11 долг — landscape к существующему portrait).
- `ui/welcome/holders/PermissionsPageViewHolder.kt`.
- `ui/welcome/helpers/WelcomePermissionsManager.kt` (адаптивный набор + grant-all flow).

SHARED-disjoint (агент C, не welcome-shared):
- `data/permissions/PermissionRegistryRepositoryImpl.kt` — `getWelcomeEntries(allFiles, audio)`.
- `ui/settings/fragments/PermissionsManagementFragment.kt` — adaptive-параметр (welcome-режим), settings-путь без изменений.

SHARED (центрально):
- `WelcomePagerAdapter.kt` — VIEW_TYPE_PERMISSIONS + dispatch + поля.
- `WelcomeActivity.kt` — `pagesList.add(4)`; интеграция менеджера; ретайр/репурпоз `finishWelcome` оверлея.
- strings ~2 (`welcome_permissions_title/description`).

## Фазы
1. Skeleton: VIEW_TYPE + stub холдер/layout + поле + pagesList(4). Build green.
2. Адаптивный фильтр реестра + параметризация фрагмента (settings не меняем).
3. PermissionsManager: лаунчеры на Activity, grant-all, state-preservation.
4. NEW landscape layouts (страница + fragment_permissions_management).
5. Интеграция хостинга (страница ИЛИ улучшенный оверлей с видимыми nav/индикатором), cleanup, build.

## Валидация
- assemble Standard/Lite/Legacy debug green.
- Юнит: adaptive-фильтр (pure-функция settings+SDK+flavor).
- Ручной прогон по API-уровням (BlockNeedUserTest).
