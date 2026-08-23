# Стратегическая спецификация: S1928 - Устаревший JAVA_HOME валит каждую gradle-цель сессии

**Ticket:** S1928
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-21
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - найдено при работе над S1913
**Tactical spec:** `PLAN/S1928_stale-java-home-blocks-every-gradle-target/` - 2 фазы, обе закрыты

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-21
**Захвачено во время:** S1913

**Текст:**

A stale inherited JAVA_HOME silently costs every agent session a full gradle round trip. Observed 2026-08-21 22:15 in a /spec-do session: `scripts/builders/check-standard-fast.ps1 -Mode Unit` refused with "Launcher JVM unusable - refusing to start gradle. Nothing was built. JAVA_HOME: C:/Program Files/Java/jdk-21.0.10 / Missing: bin/java(.exe), lib/jvm.cfg". The directory jdk-21.0.10 no longer exists - the machine now has jdk-17, jdk-21.0.11 and a `latest\jdk-21` junction, and the persisted User environment variable JAVA_HOME is already correct at `C:\Program Files\Java\latest\jdk-21`. Only the *running* Claude Code process carried the stale value, inherited from before the JDK point-update, so every shell it spawns inherits it too and every gradle-backed target in that session fails identically until the operator restarts the process. Workaround used: `export JAVA_HOME="C:\Program Files\Java\latest\jdk-21"` in the same Bash call, after which the same command ran the test task and passed. The guard message itself is good - it names the variable, the missing files, and that org.gradle.java.home cannot rescue it. What needs deciding is whether a builder that finds JAVA_HOME unusable should fall back to a JDK it can verify (the persisted User JAVA_HOME, `C:\Program Files\Java\latest\jdk-21`, or the Android Studio jbr, all present here) and say so, or keep refusing on the grounds that silently switching JDK is worse than stopping. Both readings are defensible, which is why this is research and not a one-liner.

---

## 1. Проблема

Переменная окружения процесса - снимок, снятый при его запуске. Долгоживущая агентская сессия несёт этот снимок часами, поэтому точечное обновление JDK на машине делает её `JAVA_HOME` указателем на каталог, которого больше нет, - при том что настоящее, сохранённое значение переменной на машине уже исправлено.

Дальше отказ повторяется на каждой gradle-цели: сессия порождает оболочки, оболочки наследуют тот же устаревший снимок, и ни сборка, ни быстрая проверка, ни тесты не идут до перезапуска процесса. Стоимость - не одна упавшая команда, а весь остаток сессии.

Заметить это трудно ровно потому, что сообщение гейта верное: он честно называет переменную, отсутствующие файлы и то, что `org.gradle.java.home` тут не спасает. Ничто в нём не подсказывает, что машина уже настроена правильно, а несвежий только снимок в этом процессе.

---

## 2. Цели

1. Сессия с устаревшим снимком `JAVA_HOME` продолжает работать, если машина настроена верно, - без перезапуска процесса и без ручного `export`.
2. Отказ остаётся отказом там, где чинить нечего: сохранённое значение тоже непригодно или отсутствует.
3. Из вывода видно, что именно произошло: старое значение, новое, и откуда оно взято.

**Non-goals:**

- Поиск JDK по диску. Гейт не подбирает JVM и не угадывает версию.
- Использование Android Studio jbr как запасного варианта - это именно подбор чужой JVM, от которого предостерегает захват.
- Смена `org.gradle.java.home` и правка `gradle.properties`.
- Запись в переменные окружения машины или пользователя: чинится снимок текущего процесса, а не настройка машины.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- Не зафиксированы: тикет заведён как Draft из находки в S1913. Решение §6.1 принято автором спеки.

### 3.2 Жёсткие ограничения

- **Область починки:** только переменная текущего процесса. Ни `setx`, ни запись в реестр.
- **Место:** там же, где сейчас стоит отказ, - в общей библиотеке замков, а не в каждом билдере по копии.
- **Тишина запрещена:** починка обязана печатать старое и новое значение. Молчаливая подмена JVM - это ровно то, чего захват велит избегать.
- **Стоимость:** ноль дополнительных запусков процессов на здоровом пути. Проверка сохранённого значения делается только после того, как снимок уже признан непригодным.

### 3.3 Owner inputs (Approval gate)

Решение принято **автором спеки**; позднейшее решение владельца будет дополнением, а не переписыванием.

- **Validation level:** exit-код гейта на подставленном непригодном `JAVA_HOME` в обе стороны - когда сохранённое значение пригодно и когда нет.
- **Recovery model:** обновление снимка из сохранённого значения, без подбора JDK (см. §6.1).
- **Related tickets:** S1913 (где найдено), S1425 (ввёл проверку пригодности JVM), S1896 (добавил отдельную проверку launcher-JVM, куда это и встраивается).
- **Owner sign-off:** не получен; тикет проведён автономно, решение вынесено в отчёт.

---

## 4. Контекст текущей архитектуры

Отказ уже собран правильно и живёт в одном месте: общая библиотека замков проверяет пригодность JVM по двум файлам, отдельно для launcher-JVM и для toolchain-JVM, и отказывается запускать gradle, если чего-то нет. Проверка по файлам, а не запуском JVM, - сознательное решение: запуск стоил бы порождения процесса на каждой сборке.

Чего в этой картине нет - различия между **снимком** переменной в текущем процессе и **сохранённым** значением на машине. Гейт читает только первое. Между тем это разные вещи, и расходятся они ровно в описанном случае: значение пользователя обновили, а процесс, запущенный раньше, несёт прежнее. Прочитать сохранённое значение можно независимо от снимка и без запуска чего бы то ни было.

Отсюда и разрыв: гейт видит непригодный путь и делает единственный доступный ему вывод - «настройте JAVA_HOME», - тогда как настройка уже верна, а несвежий только снимок.

---

## 5. Предлагаемый подход

Перед отказом заглянуть в сохранённое значение переменной. Если оно есть, отличается от снимка и пригодно по тем же двум файлам - обновить снимок текущего процесса, громко сказать об этом и продолжить. Если его нет, оно совпадает со снимком или тоже непригодно - отказать ровно как сегодня, тем же сообщением и тем же кодом.

Это не запасной вариант и не подбор JVM: гейт не выбирает JDK, он перечитывает то значение, снимком которого процесс и является. Выбрать что-то, чего оператор не настраивал, эта ветка не может по построению.

### 5.1 Основные столпы / модули

**Чтение сохранённого значения.** Пользовательская область, затем машинная. Без запуска процессов.

**Условие починки.** Значение существует, отличается от снимка и проходит ту же проверку пригодности, что и снимок. Все три условия обязательны: совпадающее значение чинить нечем, а непригодное - незачем.

**Громкость.** Одна строка с прежним значением, новым и областью, из которой оно взято.

**Неизменность отказа.** Ветка отказа не трогается: те же строки, тот же код выхода.

### 5.2 Потоки данных и событий

Снимок `JAVA_HOME` → проверка пригодности → пригоден: дальше как сегодня | непригоден → сохранённое значение (пользователь, затем машина) → есть, отличается и пригодно → обновить снимок, напечатать, продолжить | иначе → прежний отказ, код 3.

### 5.3 Точки расширяемости

Порядок областей чтения - одна точка. Если когда-нибудь понадобится третий источник, он добавляется туда, а не заводит вторую ветку починки.

---

## 6. Открытые вопросы / Research items

1. **Запасной JDK или отказ**
   - **Вопрос:** должен ли билдер, нашедший `JAVA_HOME` непригодным, переходить на JDK, который он может проверить, или отказывать, потому что молчаливая смена JVM хуже остановки?
   - **Варианты:** запасной вариант из перечисленных в захвате (сохранённый пользовательский `JAVA_HOME`, `latest\jdk-21`, Android Studio jbr); либо прежний отказ.
   - **Статус:** Resolved. Ни то, ни другое в чистом виде - развилка ложная, потому что оба её конца принимают, что речь о выборе JVM. Речь не о нём: `latest\jdk-21` и jbr - действительно подбор чужой JVM и отвергнуты (§2 Non-goals), а вот сохранённый пользовательский `JAVA_HOME` - не запасной вариант вовсе, а то самое значение, устаревшим снимком которого процесс и является. Перечитать его - не «сменить JDK», а обновить снимок; выбрать что-то ненастроенное эта ветка не может. Проверено 2026-08-21: сохранённое значение читается независимо от снимка процесса, и в описанном инциденте оно уже было верным - починка сработала бы ровно тогда. Молчаливость, которой опасается захват, снимается отдельно: §3.2 требует печатать оба значения.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Сохранённое значение указывает на другую мажорную версию JDK, чем ожидала сборка | Низкая | Сборка идёт на неожиданном JDK | Приемлемо: это значение, настроенное оператором на этой машине, и оно печатается. Гейт по-прежнему не выбирает ничего сам |
| Починка скрывает настоящую поломку настройки | Низкая | Оператор не узнает, что снимок был устаревшим | §3.2 запрещает тишину: печатаются оба значения и область |
| Лишняя стоимость на здоровом пути | Низкая | Каждая сборка платит за чтение переменных | Чтение делается только после того, как снимок признан непригодным, то есть на пути, который сегодня и так заканчивается отказом |
| Расхождение с toolchain-проверкой ниже | Средняя | Launcher чинится, toolchain-JVM остаётся непригодной, отказ приходит строкой позже | Приемлемо и правильно: это разные настройки, и вторая не является снимком первой |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Обновление снимка, а не запасной JDK**

- **Решение:** при непригодном снимке перечитать сохранённое значение переменной и, если оно пригодно, обновить снимок процесса.
- **Альтернативы:** запасной вариант из проверяемых JDK (jbr, `latest\jdk-21`); прежний отказ без изменений.
- **Почему:** захват формулирует развилку как «подобрать JVM или отказать», и оба конца исходят из того, что гейт что-то выбирает. Сохранённое значение переменной - не выбор: процесс уже объявил его своим, просто снял снимок раньше, чем его исправили. Подбор же настоящих альтернатив (jbr) остаётся запрещённым, потому что там гейт действительно решал бы за оператора.

**ADR-2: Починка обязана быть громкой**

- **Решение:** печатать прежнее значение, новое и область, из которой оно взято.
- **Альтернативы:** чинить молча, раз результат всё равно верный.
- **Почему:** захват прямо называет молчаливую смену JVM худшим исходом, чем остановка. Громкость - это то, что отличает починку от подмены: оператор видит, что снимок был устаревшим, и может поправить среду по-настоящему.

---

## 10. Связи с другими спеками

- S1913 - там найдено; пересечения по коду нет.
- S1425 - ввёл проверку пригодности JVM по двум файлам; этот тикет добавляет ветку перед её отказом. Прецедент, не блокер.
- S1896 - выделил отдельную проверку launcher-JVM, в которую починка и встраивается. Прецедент, не блокер.

---

## 11. Критерии готовности (strategic-level)

1. Непригодный снимок `JAVA_HOME` при пригодном сохранённом значении не останавливает gradle: цель выполняется.
2. В выводе видно прежнее значение, новое и область, из которой взято новое.
3. Непригодный снимок при непригодном или отсутствующем сохранённом значении даёт прежний отказ с прежним кодом выхода.
4. Снимок, совпадающий с сохранённым значением, не объявляется починенным.
5. Здоровый путь не платит ничего: при пригодном снимке сохранённое значение не читается.

---

## Last Audit

**Date:** 2026-08-21
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 2

### Evidence for the load-bearing checks

- §2.1 / §11.1 / §11.2 - reproduced on the capture's own failure. A real gradle target was run with `JAVA_HOME` set to `C:\Program Files\Java\jdk-21.0.10`, the exact stale value from §0. The guard printed `JAVA_HOME snapshot was stale - refreshed from the persisted User value`, named both paths and the scope, and the target then reported `Fast check passed`, exit 0. Before this change that same command refused with exit 3 and built nothing for the rest of the session.
- §11.4 - a persisted value equal to the snapshot is not treated as a repair; asserted directly against the helper.
- §11.5 - the healthy path pays nothing: the helper is reached only from inside `if ($launcherMissing.Count -gt 0)`, asserted against the source rather than assumed.
- §3.2 - the repair touches this process only. `setx` and registry writes are absent from the file, verified by grep; the change sets `$env:JAVA_HOME` and nothing else.
- §6.1 - resolved by rejecting the question's framing rather than picking one of its two answers, and the reasoning is in ADR-1: the persisted variable is not a fallback JDK, it is the value the snapshot is a snapshot of. The genuine fallbacks the capture listed - `latest\jdk-21` as a literal path, the Android Studio `jbr` - stay refused in §2 Non-goals.

### §11.3 is proven compositionally, not observed live

Recorded plainly rather than folded into a pass. Producing "stale snapshot, and the persisted value is also unusable" would mean rewriting the machine's persisted `JAVA_HOME` to a broken path; a session dying between that write and its restore would leave every future session on this machine unable to build. The risk outweighs the evidence. What is verified instead, mechanically: the helper returns nothing when the persisted value is absent, equal to the snapshot, or unusable; the refusal block and its `exit 3` still match byte-for-byte the text that was already there; and the repair branch leaves `$launcherMissing` untouched when it does not fire, so the refusal remains reachable exactly as before.

### Manual / on-device

None. No device is involved, and nothing in this ticket ships inside an APK.

### Exempt

- §8 - "Без изменений в docs/FEATURES", so the trilingual showcase check does not apply.
- Catalog regeneration - no Kotlin source in the changed set.
