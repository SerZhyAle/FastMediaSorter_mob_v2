# Research 02 - Diagnostics and mapping policy (S0553 §9.5)

**Вопрос:** достаточно ли текущего in-app crash/log export path, либо standard production обязан иметь внешний crash reporting sink?

## Наблюдения

- In-app диагностика уже существует: `core/logging/LogExportHelper.kt` (экспорт логов), `core/logging/LoggingHelper.kt` (инициализация Timber), `ui/main/helpers/CrashReportPromptManager.kt` (prompt после краша), `ui/common/support/SupportIntentFactory.kt` (отправка логов в support).
- `standardRelease` (`build.gradle.kts` buildType `release`): `isMinifyEnabled=true` -> R8 автоматически эмитит `mapping.txt`; `ndk { debugSymbolLevel = "FULL" }` -> нативные debug-символы кладутся в AAB. То есть артефакты деобфускации ГЕНЕРИРУЮТСЯ сборкой; пробел - в политике их хранения, а не генерации.
- Внешний sink (Crashlytics/Sentry) = новая зависимость + Data Safety раскрытие + отдельный privacy-контур. Это значимый scope, не часть данного gate.

## Решение

- Для данного gate diagnostics baseline = существующий in-app crash/log export path. Внешний crash sink - OUT of scope (при необходимости отдельный тикет через `/spec-draft`).
- Gate ОБЯЗАН требовать retention артефактов деобфускации на каждый production release:
  - `mapping.txt` (R8) сохранён и привязан к `versionCode`.
  - Нативные debug-символы (`debugSymbolLevel=FULL`) сохранены/загружены в Play Console.
- Артефакты деобфускации - часть operator evidence pack (§8.4), извлекаемы по `versionCode` после выкладки. Их отсутствие = §5.5 operational loss (deobfuscation/triage capability).

**Статус:** Resolved
