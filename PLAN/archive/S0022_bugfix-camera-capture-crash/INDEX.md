# S0022 — Тактическая спецификация: Захват фото из Browse не должен крашить приложение

**Ticket:** S0022
**Tier:** 2 — Easy
**Priority:** 90
**Status:** In Progress
**Strategic spec:** `PLAN/S0022_bugfix-camera-capture-crash.md`
**Date:** 2026-04-29

---

## Контекст

Команда «Сделать фото» из popup-меню ресурса в Browse:

- На Quest 3 (VR, HorizonOS, API 34): тихий abort — `handlers=0` для `VIDEO_CAPTURE` intent, нулевая пользовательская обратная связь.
- На телефоне: потенциальный краш при отсутствии обработчика или после process death.

Полный field evidence: `PLAN/S0022_bugfix-camera-capture-crash.md §13`.

---

## Оси реализации

| Фаза | Файл | Описание |
|------|------|----------|
| Phase 01 | [phase-01-strings.md](phase-01-strings.md) | Новые локализованные строки ошибок (EN/RU/UK) |
| Phase 02 | [phase-02-visibility.md](phase-02-visibility.md) | Предохранитель видимости команды (QueryIntentActivities на открытии меню) |
| Phase 03 | [phase-03-exceptions.md](phase-03-exceptions.md) | Обвязка исключений + Snackbar вместо Toast |
| Phase 04 | [phase-04-process-death.md](phase-04-process-death.md) | Выживание после process death (savedInstanceState) |

---

## Затрагиваемые файлы

| Файл | Тип изменения |
|------|---------------|
| `app_v2/src/main/res/values/strings.xml` | Новые строки |
| `app_v2/src/main/res/values-ru/strings.xml` | Новые строки (RU) |
| `app_v2/src/main/res/values-uk/strings.xml` | Новые строки (UK) |
| `ui/browse/managers/BrowseCameraCaptureManager.kt` | Рефакторинг launch(), handleResult(), новые методы saveState/restoreState |
| `ui/browse/managers/BrowseManagerInitializer.kt` | Добавление handler-check в onResourceOpsClicked |
| `ui/browse/BrowseActivity.kt` | onSaveInstanceState / onCreate для process death |

---

## Жёсткие ограничения

- Все флейворы (standard, lite, photos, legacy) — команда видна во всех.
- Без новых API dependencies.
- Все новые строки UI обязательны EN/RU/UK.
- `Timber` вместо `Log.d`. Строки диагностики с маркером `S0022-CAM` сохраняются.

---

## Критерии завершения фазы

- [ ] Phase 01: новые строки присутствуют в трёх locale, компилируются без ошибок
- [ ] Phase 02: на устройстве без камеры (или при эмуляции) команда отсутствует в popup-меню; лог содержит `CameraCapture: no handlers, command hidden`
- [ ] Phase 03: каждый класс исключения выдаёт отдельный Snackbar с локализованным текстом; ни один путь не использует хардкоженные английские строки
- [ ] Phase 04: после process death + возврат → либо файл сохранён, либо Snackbar "попытка не завершена"
- [ ] Build: `assembleStandardDebug` без ошибок
- [ ] Lint: `lintStandardDebug` без новых предупреждений в затронутых файлах
