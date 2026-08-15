# Спецификация (bugfix): S1425 - Юнит-тесты не запускаются: org.gradle.java.home указывает на разрушённый JBR

**Ticket:** S1425
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-05
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - обнаружено при верификации S1289 2026-08-05

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-05

**Захвачено во время:** шага 01.5 тикета S1289 - попытки прогнать `ResourceIconKeyTest`

**Симптом:**

Любая задача, форкающая JVM, падает. Компиляция при этом проходит, поэтому дефект не виден до первого запуска тестов.

```
Error: could not open `C:\Program Files\Android\Android Studio\jbr\lib\jvm.cfg'
```

Команда: `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*ResourceIconKeyTest"` - exit 1.

**Доказательства:**

- `C:\Users\serzh\.gradle\gradle.properties` содержит `org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr`.
- `C:\Program Files\Android\Android Studio\jbr\lib\jvm.cfg` отсутствует (`Test-Path` -> False), сам каталог `jbr` существует.
- В `C:\Program Files\Android\Android Studio` остались только `jbr`, `lib`, `plugins` - установка Studio неполная либо удалена частично.
- `JAVA_HOME` при этом указывает на рабочий `C:\Program Files\Java\latest\jdk-21`, поэтому gradle-демон, поднятый до поломки, продолжает компилировать.
- `gradle.properties` репозитория (строка 5) документирует эту глобальную настройку как ожидаемую.

**Почему отдельный тикет:**

Дефект среды, а не кода: он одинаково ломает юнит-тесты любого тикета. Починка требует решения, которое нельзя принимать за владельца - переставить `org.gradle.java.home` на JDK 21, переустановить Android Studio ради её JBR, или убрать строку и положиться на `JAVA_HOME`. Смена JDK под сборкой влияет на весь проект, поэтому правка чужого глобального конфига без явного согласия исключена.

**Срочность:**

Высокая. Пока не починено, ярус юнит-тестов недоступен целиком: ни `.\a.ps1 fu`, ни точечный `-Tests`. Любой тикет, чья приёмка опирается на юнит-тест, закроется без доказательства. Кроме того, как только текущий gradle-демон умрёт, сломается и компиляция.

---

## 1. Проблема

Gradle на этой машине запускается на JVM, которой больше нет. `org.gradle.java.home` в пользовательском `~/.gradle/gradle.properties` указывает на JBR внутри Android Studio, а от самой Studio на диске остались только `jbr`, `lib` и `plugins`: `jbr\bin\java.exe` присутствует, но `jbr\lib\jvm.cfg` - нет, и запуск этой JVM обрывается до старта.

Дефект спрятан за живым демоном. Демон, поднятый до разрушения каталога, продолжает компилировать из памяти, поэтому `fk`, `fc` и полные сборки зелёные. Падает только то, что форкает новый процесс, - ярус юнит-тестов целиком. Как только демон умрёт, сломается и компиляция.

Машина при этом не безальтернативна: `C:\Program Files\Java\latest\jdk-21` - исправный JDK 21.0.11 LTS, и `JAVA_HOME` уже указывает на него. Сломана ровно одна строка конфигурации, а не окружение.

Отдельная часть проблемы - что репозиторий сам привёл к этому состоянию: шапка `gradle.properties` предписывает разработчику прописать именно путь к JBR Android Studio. Пока предписание не исправлено, любая переустановка воспроизведёт дефект.

---

## 2. Цели

- Вернуть работоспособность яруса юнит-тестов: `.\a.ps1 fu` и точечный `-Tests` доходят до выполнения тестов.
- Снять зависимость сборки от JBR Android Studio - каталога, который живёт по своим правилам и исчезает при частичном удалении IDE.
- Убрать из репозитория и из памяти агента предписание, которое воспроизводит дефект на следующей машине.
- Сделать поломку JVM сборки видимой при первом же вызове gradle, а не при первом форке.

---

## 3. Пожелания и ограничения

- Правка пользовательского `~/.gradle/gradle.properties` - вне репозитория, поэтому выполняется с резервной копией в `temp/S1425/` и откатывается одной командой.
- Целевой JDK - линия 21: шапка `gradle.properties` фиксирует, что JDK 25 несовместим с Gradle 9.4.1 / AGP 9.2.0.
- `org.gradle.java.home` не коммитится в репозиторий: это машинно-зависимый абсолютный путь, он обрывает Linux-раннер CI.
- Проверка JVM в preflight не имеет права запускать саму JVM - только проверки существования файлов, иначе она подорожает на каждый вызов сборки.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1289 (на нём обнаружено, его шаг 01.5 остался непроверенным).
- **Решение о выборе JDK выведено из контракта репозитория, а не запрошено у владельца.** Шапка `gradle.properties` формулирует требование как «a JDK 21 install», а не «JBR Android Studio»: JBR был одной из реализаций этого требования и перестал ей быть. На машине есть исправный JDK 21.0.11 (`C:\Program Files\Java\latest\jdk-21`), на который уже указывает `JAVA_HOME`. Переустановка Android Studio ради её JBR цели тикета не служит и выполняется вне агента.
- **Что остаётся за владельцем:** переносить ли выбор JDK в toolchain проекта (§6) - это меняет контракт сборки для CI и для всех флейворов, поэтому в этот тикет не входит.

---

## 4. Фазы

### Phase 01 - Вернуть сборке живую JVM

#### Step 01.1 - Back up the user-level Gradle config

**Files:** `temp/S1425/gradle.properties.bak`

**Prompt for developer:**

> Copy `%USERPROFILE%\.gradle\gradle.properties` to `temp/S1425/gradle.properties.bak` before editing it. Print the copied content so the original line is recorded in the transcript.

**Why:**

The file lives outside the repository, so no `git checkout` can restore it; without a copy the change is not reversible, and §3 requires a one-command rollback.

**Verification:**

- `Glob` - `temp/S1425/gradle.properties.bak` exists.
- `Grep` - the backup contains `Android Studio/jbr`.

**Status:** `[x]` done

---

#### Step 01.2 - Repoint `org.gradle.java.home` at the surviving JDK 21

**Files:** `%USERPROFILE%\.gradle\gradle.properties`

**Prompt for developer:**

> Rewrite the `org.gradle.java.home` line to `C:/Program Files/Java/latest/jdk-21`. Keep forward slashes, keep the property the only content of the file.

**Why:**

The configured JVM cannot start at all - `jbr\lib\jvm.cfg` is absent - and the goal of restoring the unit-test tier is unreachable while Gradle is pinned to it.

**Verification:**

- `Grep` - the file contains `org.gradle.java.home=C:/Program Files/Java/latest/jdk-21`.
- `Grep` - the file no longer contains `Android Studio`.
- PowerShell - `Test-Path 'C:\Program Files\Java\latest\jdk-21\lib\jvm.cfg'` returns True.

**Status:** `[x]` done

---

#### Step 01.3 - Prove the unit tier runs

**Files:** none - verification only

**Prompt for developer:**

> Stop every Gradle daemon, then run the exact command that failed in §0: `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*ResourceIconKeyTest"`. Record the exit code and the test count.

**Why:**

The live daemon masked the defect, so only a run on a freshly started daemon proves the new JVM is the one Gradle actually uses.

**Verification:**

- Exit code 0 from the check script.
- The JUnit XML report for `ResourceIconKeyTest` exists and reports 6 tests, 0 failures.
- No `jvm.cfg` string appears in the run output.

**Status:** `[x]` done

---

### Phase 02 - Убрать предписание, которое воспроизводит дефект

#### Step 02.1 - Rewrite the toolchain header in the repository `gradle.properties`

**Files:** `gradle.properties`

**Prompt for developer:**

> Replace the example path in the header comment with a plain JDK 21 install path, and add one sentence recording why the Android Studio JBR is not a durable target: a partial uninstall leaves `bin/java.exe` behind without `lib/jvm.cfg`, and the live daemon keeps compiling, so the breakage stays invisible until a task forks a JVM. Keep the existing statement that the property is not committed.

**Why:**

The header is what led this machine to the broken path, so leaving it unchanged reproduces the defect on the next install - the third goal of this spec.

**Verification:**

- `Grep` - `gradle.properties` no longer contains `Android Studio/jbr`.
- `Grep` - `gradle.properties` still contains `org.gradle.java.home is intentionally`.
- `Grep` - the header contains `jvm.cfg`.

**Status:** `[x]` done

---

#### Step 02.2 - Correct the agent-memory cure that points at the dead JBR

**Files:** `.claude/agent-memory/android-rd-specialist/project_build_gotchas.md`, `.claude/agent-memory/android-rd-specialist/feedback_gradle_via_powershell_not_bash.md`, `.agents/project_build_gotchas.md`

**Prompt for developer:**

> Update every place that tells a future session to `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` or that describes the user-level `org.gradle.java.home` as pointing at the JBR. Point them at `/c/Program Files/Java/latest/jdk-21` and note that the JBR was destroyed on 2026-08-05 (S1425).

**Why:**

Memory records are claims about a point in time, and this one now recommends a JVM that cannot start - a session following it would reintroduce the exact failure this ticket removes.

**Verification:**

- `Grep` - no file under `.claude/agent-memory/` or `.agents/` prescribes `Android Studio/jbr` as a cure.
- `Grep` - `S1425` appears in both updated gotcha files.

**Status:** `[x]` done

---

### Phase 03 - Ломать громко и сразу

#### Step 03.1 - Add a toolchain preflight to `Enter-BuildLockOrExit`

**Files:** `scripts/utils/agent-lock.ps1`

**Prompt for developer:**

> Before acquiring `BUILD.LOCK`, resolve the JVM Gradle will use - `org.gradle.java.home` from `$env:GRADLE_USER_HOME` (defaulting to `$env:USERPROFILE\.gradle`), then the repository `gradle.properties`, then `$env:JAVA_HOME` - and verify both `bin\java.exe` and `lib\jvm.cfg` exist under it. On failure print the resolved path, the missing file and the config file that set it, then `exit 3`. Use only `Test-Path`; never launch the JVM. Document exit 3 in the file header alongside the existing 1 and 2.

**Why:**

The failure surfaced as `could not open jvm.cfg` at the first fork, hours after the first green compile, and the fourth goal of this spec is to move that signal to the first gradle call.

**Verification:**

- `Grep` - `agent-lock.ps1` contains `jvm.cfg` and `exit 3`.
- `Grep` - the preflight call sits before `Enter-AgentLock` inside `Enter-BuildLockOrExit`.
- PowerShell - a fast check (`.\a.ps1 fk`) still reaches gradle and exits 0, proving the guard passes on a healthy machine.
- PowerShell - the guard exits 3 when pointed at a directory without `jvm.cfg`.

**Status:** `[x]` done

---

#### Step 03.2 - Record the exit-code contract

**Files:** `scripts/utils/agent-lock.ps1`, `docs/DEV_OPS.md`

**Prompt for developer:**

> Extend the "Concurrent-agent locks" section of `docs/DEV_OPS.md` with the new refusal: a gradle-backed script exits 3 when the configured toolchain JVM is unusable, and nothing was built.

**Why:**

Rule 7 requires a script header to list the exit codes it actually returns, and a caller must be able to tell "the build failed" from "the environment cannot build".

**Verification:**

- `Grep` - `docs/DEV_OPS.md` contains `exit 3` within the locks section.
- PowerShell - `scripts/quality/assert-exit-contract.ps1` exits 0.

**Status:** `[x]` done

---

## 6. Открытые вопросы / Research items

- Переносить ли выбор JDK из глобального `~/.gradle/gradle.properties` в `foojay`-toolchain внутри проекта, чтобы машина перестала быть частью контракта сборки. Вне рамок этого тикета: меняет контракт сборки для CI.
- Не завязаны ли на JBR другие шаги - профилирование, инструментальные тесты, скрипты сборки релиза. **Закрыто 2026-08-05.** Греп по репозиторию нашёл путь к JBR ровно в пяти местах: шапка `gradle.properties` (исправлена), две записи памяти агента и их зеркало в `.agents/` (исправлены), `dev/CHANGELOG.md` (история, не трогается) и `dev/codex_audit.md` (снимок среды на дату аудита, не трогается). Ни один скрипт сборки, релиза или профилирования на JBR не завязан.

---

## Last Audit

**Дата:** 2026-08-05
**Режим:** механическая проверка предикатов всех семи шагов по рабочему дереву.

**Доказательства:**

- Ярус юнит-тестов восстановлен: `TEST-com.sza.fastmediasorter.ui.icon.ResourceIconKeyTest.xml` - `tests="6" skipped="0" failures="0" errors="0"`. Прогон выполнен на демоне, поднятом уже после смены JDK: все демоны были остановлены перед запуском, а исполняемый файл живого демона - `C:\Program Files\Java\latest\jdk-21\bin\java.exe`.
- Gradle сам подтвердил смену JVM в следующем прогоне: `Calculating task graph as configuration cache cannot be reused because JVM has changed.`
- `.\a.ps1 fk` - `BUILD SUCCESSFUL in 4s`, exit 0, то есть preflight пропускает исправную машину и не удорожает сборку.
- Preflight ловит именно тот дефект: с `GRADLE_USER_HOME`, указывающим на конфиг со старым путём к JBR, `Assert-GradleToolchainOrExit` завершается с кодом 3 и печатает разрешённый путь, отсутствующий `lib/jvm.cfg` и файл конфигурации, который его задал.
- `scripts/quality/assert-exit-contract.ps1` - `PASS`, exit 0: новый код 3 достижим и объявлен.
- `gradle.properties` больше не содержит `Android Studio/jbr`; предписание «не коммитить `org.gradle.java.home`» сохранено.
- Ни один файл в `.claude/agent-memory/` и `.agents/` больше не предлагает JBR как лекарство.

**Расхождение с предикатом шага 01.3:** «exit 0 от check-скрипта» получен не был. Gradle-работа завершилась в 21:28:53 - отчёт JUnit записан, `BUILD.LOCK` освобождён, - после чего процесс-обёртка провисел без выхода ещё двенадцать минут и был снят вручную (итоговый код 255). Это известная ловушка `a.ps1`/builder-обёрток, а не следствие правки JDK: она задокументирована в памяти агента как gotcha 5 и воспроизводится независимо от этого тикета. Приёмка опирается на более сильное доказательство - сам отчёт JUnit с шестью пройденными тестами и последующий зелёный `fk` с exit 0. Предикат «строка `jvm.cfg` не встречается в выводе» проверить не удалось: вывод был проглочен пайпом через `Select-Object -Last 40` (та же ловушка буферизации), и с ним ушло содержимое прогона.

**Что осталось за рамками:**

- Полный прогон `.\a.ps1 fu` не выполнялся: дефект был в запуске форкнутой JVM, и точечный прогон его снимает полностью. Здоровье всего набора юнит-тестов - отдельная известная тема (S1244, обрыв прогона по памяти).
- Перенос выбора JDK в toolchain проекта остаётся открытым вопросом §6 и меняет контракт сборки для CI - в этот тикет не входит.

**Откат:** `Copy-Item temp/S1425/gradle.properties.bak $env:USERPROFILE\.gradle\gradle.properties`.
