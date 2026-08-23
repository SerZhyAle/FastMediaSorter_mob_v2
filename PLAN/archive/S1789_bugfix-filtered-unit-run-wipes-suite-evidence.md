# Спецификация (compact bugfix): S1789 - Отфильтрованный прогон юнит-тестов уничтожает отчёты полного прогона

**Ticket:** S1789
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-17
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-17

**Текст:**

Найдено при ревью S1786, когда полный прогон `.\a.ps1 fu` запускался ради замера реального числа падающих тестов.

```text
Полный прогон закончился так:
  3795 tests completed, 6 failed, 17 skipped
  assert-test-suite-complete: 521 report(s) for 521 *Test.kt file(s) (ratio 1)
  assert-test-suite-complete: PASS - every package with test sources produced reports.

Через несколько минут в app_v2/build/test-results/testStandardDebugUnitTest остался
ОДИН файл TEST-*.xml:
  TEST-com.sza.fastmediasorter.domain.usecase.BackupMapperTest.xml
  tests=20 failures=0 errors=0 skipped=0   (mtime 2026-08-17 23:02:42)
HTML-отчёт app_v2/build/reports/tests/testStandardDebugUnitTest/index.html показывает
те же 20 тестов, 0 сбоев, 1.080s - то есть тоже перезаписан.
```

Гипотеза (не подтверждена): Gradle чистит каталог результатов на старте задачи, поэтому любой последующий отфильтрованный прогон (`-Tests "*BackupMapperTest"`) стирает XML полного прогона. mtime уцелевшего отчёта раньше конца полного прогона, что с этой гипотезой согласуется.

Почему это дорого. Документированный путь доказательства в проекте - читать `app_v2/build.gradle.kts` (`testOptions.unitTests.all`). После чужого отфильтрованного прогона этот путь пуст, а `assert-test-suite-complete` на тех же данных посчитает ratio 1/521 и назовёт прогон TRUNCATED - то есть агент увидит не «отчётов нет», а ложный вердикт о неполном прогоне. Восстановление стоит полного прогона: 7 м 49 с в этом замере.

---

## 1. Проблема / симптом

Запуск единичного юнит-теста по фильтру (например `--tests *SettingsManifestExportTest` в `check-standard-fast.ps1`, `assert-settings-doc-sync.ps1`, `assert-icon-inventory-sync.ps1`) перезаписывает `app_v2/build/test-results/testStandardDebugUnitTest/`, очищая отчёты полных прогонов юнит-тестов и сбивая статическую проверку полноты сьюта `assert-test-suite-complete.ps1`.

---

## 2. Корневая причина

AGP/Gradle `Test` таски по умолчанию очищают целевую директорию `reports.junitXml.outputLocation` и `reports.html.outputLocation` при начале выполнения таски. Когда таска вызывается с `--tests`, результаты пишутся в ту же стандартную директорию (`build/test-results/test<Variant>UnitTest/`), что стирает отчёты предыдущего полного прогона.

---

## 3. Исправление

1. В [`app_v2/build.gradle.kts`](file:///P:/ANDROID/FastMediaSorter_mob_v2/app_v2/build.gradle.kts) в блоке `unitTests.all` добавлены dynamic Provider-блоки для `outputLocation` JUnit XML и HTML отчётов.
2. При наличии активных паттернов фильтрации (проверено через `filter.includePatterns` и reflection-вызов `getCommandLineIncludePatterns`) выводимые директории перенаправляются в `test-results/${it.name}-filtered` и `reports/tests/${it.name}-filtered`.
3. В [`scripts/quality/assert-settings-doc-sync.ps1`](file:///P:/ANDROID/FastMediaSorter_mob_v2/scripts/quality/assert-settings-doc-sync.ps1) добавлена поддержка чтения результатов `SettingsManifestExportTest` как из `-filtered` директории, так и из стандартной.
4. В [`app_v2/src/test/java/com/sza/fastmediasorter/testing/fakes/FakeSettingsRepository.kt`](file:///P:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/test/java/com/sza/fastmediasorter/testing/fakes/FakeSettingsRepository.kt) реализован отсутствующий метод `isConsolidatedStorageActive()`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1786 (ревью, в ходе которого найдено); S1463, S1244 (история вердиктов полноты прогона)

---

## 4. Проверка

1. Полный прогон `check-standard-fast.ps1 -Mode Unit` -> 525 отчётов сгенерировано в `build/test-results/testStandardDebugUnitTest/`.
2. `assert-test-suite-complete.ps1` -> PASS (525/525).
3. Запуск отфильтрованного теста `check-standard-fast.ps1 -Mode Unit -Tests "*SettingsManifestExportTest"` -> отчёт помещён в `testStandardDebugUnitTest-filtered/`.
4. Повторный запуск `assert-test-suite-complete.ps1` -> PASS (525/525 отчётов полного прогона сохранены в целости).

---

## Last Audit
- **Outcome:** Verified
- **Date:** 2026-08-18
- **Evidence:** `check-standard-fast.ps1 -Mode Unit -Tests "*SettingsManifestExportTest"` writes XML report to `testStandardDebugUnitTest-filtered/`, keeping `testStandardDebugUnitTest/` intact. `assert-test-suite-complete.ps1` passes with 525/525 report ratio.

