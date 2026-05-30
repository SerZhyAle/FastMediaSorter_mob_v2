# Постановка задачи: аудит и расширение тестового покрытия

Дата: 2026-05-27
Ветка: DEBUG-v008

## Цель

Провести аудит непокрытой функциональности по всему проекту FastMediaSorter v2 и добавить недостающие тесты по всем модулям и flavor, начиная с наиболее рискованных зон.

## Подтверждённый scope

- Источник непокрытой функциональности: whole-app audit.
- Модули: `app_v2`, `wear`.
- Flavor scope: all modules/flavors.
- Разрешение на реализацию: после аудита можно писать тесты.

## Ограничения

- Нельзя считать функциональность непокрытой без явного критерия: coverage report, сопоставление source/test, отсутствие contract/unit/instrumentation tests или uncovered changed production files.
- Если coverage tooling отсутствует, сначала фиксируется текущий test inventory и предлагается минимальный способ получения coverage baseline.
- Production-код не меняется без отдельного решения; при необходимости test seam выносится в отдельный risk item.
- Для `.kt` изменений обязательны catalog sync, dev log и релевантные Gradle test gates.

## Предварительная стратегия

- Сначала инвентаризация существующих тестов и source surface.
- Затем классификация gaps по слоям: domain, data, core, UI helpers/managers, instrumentation-only flows, flavor-specific code.
- Реализация идёт фазами, чтобы каждый набор тестов имел отдельную валидацию и не смешивал независимые риски.

## Progress Log

- 2026-05-27: Added and validated the first `app_v2` XR/noLegal slice:
	- `IntentSerializationCompatTest`: 2 tests, 0 failures, 0 errors, 0 skipped.
	- `VrLaunchContractTest`: 5 tests, 0 failures, 0 errors, 0 skipped.
	- `VrApkClassificationCacheTest`: 3 tests, 0 failures, 0 errors, 0 skipped.
- 2026-05-28: Generated inventory artifact: `temp/test_coverage_inventory_20260528_000730.md`.
- 2026-05-28: Added and validated the first `wear` JVM slice:
	- `ApplyWearSettingsUseCaseTest`: 1 test, 0 failures, 0 errors, 0 skipped.
	- `WearEventEnvelopeTest`: 2 tests, 0 failures, 0 errors, 0 skipped.
	- `ITunesTrackTest`: 2 tests, 0 failures, 0 errors, 0 skipped.
- 2026-05-28: Fixed existing Wear test fake contract drift in `ImportNetworkSourcesUseCaseTest` by implementing `observeSources()`.
- 2026-05-28: Added and validated the second `wear` JVM slice:
	- `FtpConnectionTestTest`: 1 test, 0 failures, 0 errors, 0 skipped.
	- `SftpConnectionTestTest`: 1 test, 0 failures, 0 errors, 0 skipped.

## Current Coverage Baseline Limitation

- No JaCoCo/Kover coverage baseline is configured yet.
- Current gap detection is risk-based plus source/test inventory, not line coverage.
- A reliable whole-app closure requires adding coverage tooling or importing an IDE coverage report.