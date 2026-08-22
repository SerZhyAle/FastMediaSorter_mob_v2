# Спецификация (compact bugfix): S1636 - lint не запускается из-за гварда версии BouncyCastle

**Ticket:** S1636
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-14
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14

**Захвачено во время:** S1329

**Текст:**

Android lint cannot run at all: the S1496 BouncyCastle drift guard fails the `androidLintTool` configuration, so every lint task dies during dependency resolution.

Evidence, from `scripts/utils/check-typo-lint.ps1 -SkipTypo -SkipActivityChecks -LintTask updateLintBaselineStandardDebug` on 2026-08-14 13:16 (exit 1):

Execution failed for task ':app_v2:lintAnalyzeStandardDebug'.
> Error while evaluating property 'lintTool.versionKey' of task ':app_v2:lintAnalyzeStandardDebug'.
   > Could not resolve all files for configuration ':app_v2:androidLintTool'.
      > Could not resolve org.bouncycastle:bcpkix-jdk18on:1.79.
        Required by: project ':app_v2' > com.android.tools.lint:lint-gradle:32.2.1 > com.android.tools.lint:lint:32.2.1 > com.android.tools:sdk-common:32.2.1
         > BouncyCastle version drift: org.bouncycastle:bcpkix-jdk18on resolved to 1.79, expected 1.75.
      > Could not resolve org.bouncycastle:bcprov-jdk18on:1.79. (same chain)

Root-cause candidate, not verified: `app_v2/build.gradle.kts` around line 1670 declares `expectedBouncyCastleVersion = "1.75"` and applies the guard to `configurations.matching { !it.name.contains("test", ignoreCase = true) }`. The exclusion covers test classpaths with the stated reason that nothing on a test classpath reaches the APK. `androidLintTool` is the same kind of classpath - it is the lint tool's own runtime, not app code - but it is not excluded, so lint's transitive BouncyCastle 1.79 trips a guard that exists to watch what ships in the APK.

Consequence: `.\a.ps1 ch` and every lint task fail, and `app_v2/lint-baseline.xml` cannot be regenerated - which is what blocked step 06.2 of S1329. The APK build itself is unaffected (`assembleStandardDebug` succeeds).

Care needed when fixing: do not simply raise `expectedBouncyCastleVersion`, which would weaken the S1496 assertion over what actually ships. Excluding the lint tool configuration keeps the APK assertion intact. Whatever the fix, prove both halves: lint runs, and the version asserted for the packaged app is still the one that ships.

---

## 1. Проблема / симптом

Любая lint-задача падает на разрешении конфигурации `androidLintTool`: гвард дрейфа BouncyCastle (S1496) бросает исключение на транзитивной зависимости самого lint 1.79 при ожидаемой 1.75. Сборка APK при этом зелёная - страдает только lint, то есть `.\a.ps1 ch`, регенерация `app_v2/lint-baseline.xml` и любой гейт, опирающийся на lint.

---

## 2. Корневая причина

Кандидат из §0 подтверждён чтением `app_v2/build.gradle.kts`. Гвард стоял на выборке
`configurations.matching { !it.name.contains("test", ignoreCase = true) }` - то есть на «всём,
кроме тестового». Под это описание попадают не только классpath'ы приложения, но и классpath'ы
инструментов, а `androidLintTool` - это собственный runtime линта, который тянет
`bcpkix/bcprov 1.79` через `com.android.tools.lint:lint-gradle:32.2.1`. Гвард, написанный про
то, что уезжает в APK, срабатывал на зависимости инструмента, который в APK не попадает никогда.

Обоснование самого гварда при этом верное: BouncyCastle приходит транзитивно через SMBJ, и его
версию надо утверждать, а не форсировать. Ошибочна была не идея, а область действия.

---

## 3. Исправление

Область действия переписана с чёрного списка на белый: гвард применяется к
`*RuntimeClasspath` вариантов, за вычетом тестовых. Это ровно то множество, которое
формулирует его собственное обоснование - «что уезжает в APK», - и его нельзя расширить
случайно.

```kotlin
configurations.matching {
    it.name.endsWith("RuntimeClasspath") && !it.name.contains("test", ignoreCase = true)
}.configureEach {
```

Почему не «добавить `androidLintTool` в исключения»: следующая конфигурация инструмента
(ksp, kapt, detekt, lintChecks) наступила бы на тот же провод, и тикет вернулся бы под другим
именем. Версия `expectedBouncyCastleVersion` не тронута - поднимать её значило бы ослабить
утверждение о том, что реально упаковано, ровно как предупреждает §0.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1496 (ввёл гвард), S1329 (на нём заблокирован шаг 06.2)

---

## 4. Проверка

Обе половины, как требует §0.

**Половина первая - lint работает.** Прогнана дословно та команда, которой дефект зафиксирован:
`scripts/utils/check-typo-lint.ps1 -SkipTypo -SkipActivityChecks -LintTask updateLintBaselineStandardDebug`.
Было exit 1 с падением на разрешении `androidLintTool`; стало `BUILD SUCCESSFUL`, `Lint exit code: 0`,
`Checks completed successfully`. В журнале прогона на 900 КБ нет ни одного упоминания дрейфа
BouncyCastle. `app_v2/lint-baseline.xml` перезаписан - его временная метка 2026-08-14 18:46:17, то
есть артефакт, который по §0 «не может быть перегенерирован», перегенерирован.

Оговорка, которую честнее назвать: в этом прогоне `lintAnalyzeStandardDebug` был UP-TO-DATE, а
выполнился `updateLintBaselineStandardDebug`. Раньше падала вся инвокация целиком, на разрешении
конфигурации, до выполнения задач - поэтому зелёный прогон и свежий baseline вместе означают, что
разрешение прошло. Отдельного прогона с принудительным пересчётом задач не делалось: он стоит
полного цикла lint ради подтверждения того, что уже подтверждено вторым экспериментом ниже.

**Половина вторая - гвард всё ещё сторожит APK.** Проверено экспериментом, а не рассуждением:
`expectedBouncyCastleVersion` временно заменён на `1.75-S1636-PROBE`, прогон `.\a.ps1 fk` упал
ровно так, как должен:

```
> Could not resolve all files for configuration ':app_v2:standardDebugRuntimeClasspath'.
   > BouncyCastle version drift: org.bouncycastle:bcprov-jdk18on resolved to 1.75, expected 1.75-S1636-PROBE.
BUILD FAILED in 5s
```

Значение имеет имя конфигурации: `standardDebugRuntimeClasspath` - это классpath, который
упаковывается, и гвард на нём по-прежнему стоит и по-прежнему видит реальную версию 1.75. После
возврата значения `.\a.ps1 fk` - `BUILD SUCCESSFUL`.

**Что осталось красным и почему это не этот тикет.** `.\a.ps1 ch` целиком по-прежнему падает, но
раньше lint: на typo-проверке, которая ругается на итальянские и немецкие строки (`foto`, `Titel`,
`Ressource`) и на тестовый ресурсный файл. Это чужой преждевременный шум, к гварду отношения не
имеющий; после `-SkipTypo` lint зелёный.
