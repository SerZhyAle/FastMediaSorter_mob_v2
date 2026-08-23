# Стратегическая спецификация: S1816 - Wear debug build carries a frozen version stamp

**Ticket:** S1816
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-19
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - out-of-scope находка при подготовке pre-release прогона, запаркована 2026-08-19 (CLAUDE.md 3.1)
**Tactical spec:** inline (compact spec)

---

## 1. Goal

Сделать установленную на часах debug-сборку опознаваемой: сейчас любые две часовые debug-сборки, разделённые сколь угодно большим числом изменений кода, несут один и тот же `versionName`/`versionCode`, поэтому по устройству нельзя определить, какой код на нём стоит.

---

## 2. Symptom (измерено 2026-08-19)

- `scripts/builders/build-wear-debug.PS1` отработал в 17:54 и выдал `wear/build/outputs/apk/debug/wear-debug.apk` с `versionCode=26081516`, `versionName=2.60.8151.612-DEBUG` - штамп от 2026-08-15.
- Телефонная сборка того же сеанса получила `2.60.8191.752-NoLegal-DEBUG`, то есть живую метку времени.
- Источник: `wear/build.gradle.kts` держит литералы `defaultAppVersionCode = 26081516` и `defaultAppVersionName = "2.60.8151.612"`, а override приходит только через `-Pfms.versionCode`/`-Pfms.versionName`. `build-wear-debug.PS1` их не передаёт.

## 3. Evidence

- `aapt2 dump badging wear/build/outputs/apk/debug/wear-debug.apk` -> `versionCode='26081516' versionName='2.60.8151.612-DEBUG'`.
- Та же строка у копии `DOWNLOADS/FastMediaSorter_wear_debug.apk` (mtime 2026-08-19 17:54).
- Код в APK при этом свежий: распакованные `classes*.dex` содержат `ClassifyWearStreamMediaKindUseCase` (переименование того же дня) и не содержат прежнего `WearStreamMediaKindClassifier`. Врёт только метка, не содержимое.
- `adb -s emulator-5554 install -r` печатает `Success` и оставляет прежний `versionName`, потому что одинаковый `versionCode` переустановке не мешает.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1681

## 4. Why it matters

- Во время устройственного прогона ни владелец, ни агент не могут по экрану About или по `dumpsys package` сказать, какая сборка стоит на часах. Единственный признак - `lastUpdateTime`, который меняется и от переустановки того же APK.
- Это вторая грань известной ловушки часового модуля: `a.ps1 fw` компилирует, но не пакует APK, поэтому легко поставить старый артефакт и увидеть старый экран. Раньше расхождение читалось по версии; здесь не читается.
- Правило проекта уже сформулировано, просто не применено к часам. Шапка `scripts/builders/build-nolegal-debug.ps1` гласит: sideload-артефакт обязан нести реальную метку времени, а замороженные версии принадлежат компиляционным проверкам (`fk/fc/fr/fu`), которые APK не производят. Часовой APK копируется в `DOWNLOADS/` и на Google Drive и ставится на часы вручную, то есть он ровно такой же sideload-артефакт.

## 5. Constraints

- Релизный штамп трогать нельзя: `versionName` часов обязан быть побайтово равен телефонному, а `versionCode` обязан от него отличаться по правилу `wear = floor(app / 10)`.
- Правка касается только debug-пути и не должна менять поведение `scripts/release/build-release-spectrum.ps1`.
- Вшитые в оба `build.gradle.kts` литералы остаются нетронутыми.

## 6. Открытые вопросы / Research items

- **6.1 Форма опознавания.** Resolved 2026-08-19: не менять gradle и не добавлять `versionNameSuffix`, а передать `-Pfms.versionCode`/`-Pfms.versionName` из билдера - ровно так, как это делают пять телефонных билдеров (`build-debug.PS1`, `build-debug-clean.PS1`, `build-debug-device.ps1`, `build-nolegal-debug.ps1`, `build-standard-debug.ps1`). `build-wear-debug.PS1` - единственный билдер репозитория без такого штампа.
- **6.2 Цена конфигурационного кеша.** Resolved 2026-08-19: вопрос снят самой формой фикса. Вшитые литералы остаются на месте, поэтому та стабильность, ради которой они заведены, сохраняется целиком, а штамп приходит per-invocation свойством. Телефонный путь живёт с этим давно и предлагает явный отказ - `-AutoVersion:$false`; часовой получает такой же.
- **6.3 Область гейта.** Resolved 2026-08-19: `scripts/quality/assert-module-version-parity.ps1` читает **только** вшитые константы - он якорится на объявлениях `val defaultAppVersionCode` и `val defaultAppVersionName` - и в своих `.NOTES` прямо относит `-Pfms.*` к релизному скрипту. Значит штамп из билдера гейту не виден и сломать его не может.
- **6.4 Расхождение версий между модулями в debug.** Resolved 2026-08-19: после фикса часы и телефон, собранные в разное время, несут разные `versionName` (замерено: телефон `2.60.8191.752`, часы `2.60.8191.842`). Это осознанный размен, а не нарушение §5. Требование побайтового равенства принадлежит релизу, где `build-release-spectrum.ps1` штампует оба модуля из одной метки времени, и там оно продолжает выполняться. Для ad-hoc debug-сборок разные метки информативны: они называют момент сборки каждого артефакта - ровно то, чего не хватало. Замороженная версия читалась как несвежая установка гораздо чаще, потому что не менялась никогда.

## 7. Acceptance

- Две последовательные часовые debug-сборки, разделённые изменением кода, различимы по `versionName` на устройстве.
- `assert-module-version-parity.ps1` остаётся зелёным.
- Релизный путь `build-release-spectrum.ps1` выдаёт прежние значения для обоих модулей.
- Отказ от штампа доступен явным флагом, как на телефоне.

---

# Phase 01 - Stamp the wear debug build

**Status:** ✅ Done
**Depends on:** none - foundation phase
**Steps done:** 2 / 2

## Objective

`build-wear-debug.PS1` штампует в собираемый APK живую метку времени по тому же правилу, что и телефонные билдеры, с тем же явным отказом.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/builders/build-wear-debug.PS1` | Modified | ≤ 120 |

## Steps

### Step 01.1 - Add the auto-version stamp to the wear builder

**Files:** `scripts/builders/build-wear-debug.PS1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `[switch]$AutoVersion = $true` parameter and, when it is set, compute the stamp from the current time and append `-Pfms.versionCode=<code>` and `-Pfms.versionName=<name>` to the gradle argument list. `versionName` uses the shared `Y.YM.MDDH.Hmm` formula, byte-identical to the phone builders. `versionCode` is `yyMMddHH` as an 8-digit integer - the phone appends the first minute digit and gets 9, and the watch must stay one digit shorter so the two codes never collide under one applicationId. Replace the header comment that documents the frozen behaviour, and mirror the phone builders' parameter comment explaining that `-AutoVersion:$false` opts back into the frozen default for configuration-cache reuse.

**Why:** Without the stamp two watch debug builds separated by any amount of code change report one identical `versionName`, so neither the owner nor an agent can tell from the device which build is installed - the failure that opened this ticket during a pre-release run.

**Verification:** `pwsh -NoProfile -File scripts/builders/build-wear-debug.PS1` prints a `Version override:` line whose value differs from `2.60.8151.612`, and `aapt2 dump badging` on the produced APK reports that same value.

### Step 01.2 - Prove the release contract is untouched

**Files:** `scripts/builders/build-wear-debug.PS1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Run `scripts/quality/assert-module-version-parity.ps1 -Gate` and confirm exit 0, and confirm by reading that `wear/build.gradle.kts` and `app_v2/build.gradle.kts` still carry their original `defaultAppVersionCode`/`defaultAppVersionName` literals.

**Why:** The parity gate is the only mechanical guard that the two modules state one version under the documented derivation, and §5 forbids this ticket from disturbing the release stamping that `build-release-spectrum.ps1` owns.

**Verification:** `assert-module-version-parity.ps1 -Gate` exits 0, and both build files still state the literals recorded in §2.
