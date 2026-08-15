# S1002 - Тактический план: контракт компаньона v2 (параметры ресурса)

**Ticket:** S1002
**Status:** Tactical
**Strategic:** `PLAN/S1002_companion-config-v2-resource-params.md`
**Date:** 2026-07-11

Расширить обменный контракт компаньона до schemaVersion 2: каждый корень несёт опциональные параметры ресурса (профиль/тип, медиатипы, условия сканирования, назначение, комментарий, пин, интервал слайд-шоу). Импорт применяет их, экспорт эмитит. Полная обратная совместимость с v1. Deliverable для стороны компаньона.

## Инварианты

- Все новые поля DTO - nullable с дефолтом `null`, добавляются ПОСЛЕ существующих (позиционные вызовы `CompanionRootDto(virtualPath, label)` в тестах не ломаются).
- Отсутствие поля v2 = поведение v1 (набор медиатипов ALL, `scanSubdirectories=true`, `isReadOnly=true`, `comment="Companion: .."`).
- Единый источник пресета профиля: `ResourceProfile.mediaPreset()` (извлечь из `ResourceFormData.applyProfile`, не дублировать).
- Транспортный конверт `FMSCFG1:` не трогаем (это версия конверта, не payload).
- Кросс-репный контракт: изменения фиксируются в deliverable-спеке (Phase 05) для синхронного bump companion-стороны.

## Фазы

- Phase 01 - `phase-01-schema-v2-dto-parser.md`: расширить `CompanionConfigDto`/`CompanionRootDto`, поднять `SUPPORTED_SCHEMA_VERSION=2`, мягкая валидация новых полей, токен-мапперы профиля/медиатипов, извлечь `ResourceProfile.mediaPreset()`.
- Phase 02 - `phase-02-import-mapping.md`: `ImportCompanionConfigUseCase` применяет per-root параметры; профиль -> медиатипы/флаги; назначение снимает read-only.
- Phase 03 - `phase-03-export-v2.md`: `ExportCompanionConfigUseCase` эмитит v2 с реальными параметрами ресурса; предупреждение про пин.
- Phase 04 - `phase-04-vectors-tests.md`: канонический вектор v2 + сохранить v1; тесты парсера (v1 принимается, v2 round-trip) и сериализатора.
- Phase 05 - `phase-05-companion-deliverable.md`: `COMPANION_EXPORT_SPEC.md` - контракт v2 + требования к UI/экспорту компаньона (файл и QR).

## Валидация

- Компиляция: `.\a.ps1 fk` после Kotlin-правок; `.\a.ps1 fc` при затрагивании ресурсов/строк.
- Юнит: `.\gradlew.bat testStandardDebugUnitTest --tests "*CompanionConfig*"`.
- Пост-изменение: `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile`.
