# Спецификация (compact bugfix): S1375 - KSP падает на кросс-дисковом пути, сборка app_v2 невозможна

**Ticket:** S1375
**Status:** Archived
**Priority:** 95
**Date:** 2026-08-03
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-03

**Захвачено во время:** S1360 (`/spec-next`, попытка скомпилировать правку `HeadlessPhotoCapturer.kt`)

**Текст:**

KSP падает на кросс-дисковом пути: сборка app_v2 невозможна.

`.\a.ps1 fk` (и любая компиляция Kotlin в app_v2) падает в задаче `:app_v2:kspStandardDebugKotlin`, до `compileStandardDebugKotlin` дело не доходит:

```
e: [ksp] java.lang.IllegalArgumentException: this and base files have different roots: C:\Users\serzh\.gradle\caches\9.4.1\transforms\5fc6888f589349a983738ae4e97fabe6\transformed\okhttp3-integration-4.16.0-api.jar!\com\bumptech\glide\annotation\compiler\GlideIndexer_GlideModule_com_bumptech_glide_integration_okhttp3_OkHttpLibraryGlideModule.class and P:\ANDROID\FastMediaSorter_mob_v2\app_v2

FAILURE: Execution failed for task ':app_v2:kspStandardDebugKotlin'.
> A failure occurred while executing com.google.devtools.ksp.gradle.KspAAWorkerAction
```

Проект лежит на диске `P:`, кеш Gradle - на `C:`. KSP пытается построить относительный путь от класса внутри трансформированного JAR-а Glide к корню модуля, и `Path.relativize` бросает исключение, потому что корни разные.

Эвиденс, снятый 2026-08-03, не выведенный:

- 12:36 и 12:41 тот же `.\a.ps1 fk` проходил успешно (`BUILD SUCCESSFUL`, задача `kspStandardDebugKotlin` отрабатывала). Около 13:0x начал падать стабильно.
- Четыре прогона подряд - одна и та же ошибка, побайтово. Не транзиент.
- Очистка `app_v2/build/kspCaches` и `app_v2/build/generated/ksp` не помогла.
- Очистка `.gradle/configuration-cache` не помогла (`Calculating task graph as no cached configuration is available` в выводе - кеш действительно был пересобран).
- Правка, на которой это вскрылось (S1360, `HeadlessPhotoCapturer.kt`), к Glide и KSP отношения не имеет; `compileStandardDebugKotlin` вообще не запускается.

Почему это блокер, а не неудобство: пока не решено, ни один тикет с кодом Kotlin в `app_v2` нельзя закрыть - валидационная лестница CLAUDE.md требует прохода компиляции, а её нет.

Направления для расследования (не решение):
- Не пересоздался ли трансформ `5fc6888f...` сторонней сессией; сравнить с тем, что было до 12:41.
- Помогает ли перенос кеша Gradle на `P:` через `GRADLE_USER_HOME` - тогда корень один и `relativize` не бросает.
- Известный ли это баг KSP/Glide-процессора на Windows при кросс-дисковой раскладке; проверить версии KSP и `com.github.bumptech.glide:ksp`.
- Помогает ли `--rerun-tasks` или полная очистка `~/.gradle/caches/9.4.1/transforms`.

Блокирует: S1360 (правка написана, но не верифицирована).

---

## 1. Проблема / симптом

Задача `:app_v2:kspStandardDebugKotlin` падает, `compileStandardDebugKotlin` не запускается вовсе.
Компиляция Kotlin в модуле `app_v2` невозможна ни для одного тикета - валидационная лестница
CLAUDE.md требует прохода компиляции, а её нет.

Флейворы: не при чём, отказ на этапе обработки аннотаций, до вариантности. Воспроизводится на
`standard debug`; маршрут общий для всех.

---

## 2. Корневая причина

Расследовано 2026-08-03, доказательства сняты с рабочего дерева, не выведены.

**Механика.** KSP2 в инкрементальном учёте строит путь каждой записи classpath **относительно
каталога модуля**. Проект лежит на `P:`, кеш Gradle - на `C:` (`GRADLE_USER_HOME` не задан, то есть
дефолтный `C:\Users\serzh\.gradle`). `Path.relativize` на паре путей с разными корнями бросает
`IllegalArgumentException`, что и видно в сообщении: база - `P:\..\app_v2`, вторая сторона - класс
Glide внутри трансформированного JAR-а под `C:\Users\serzh\.gradle\caches\9.4.1\transforms\..`.

**Что отвергнуто по фактам, а не по рассуждению:**

- «Трансформ пересоздала соседняя сессия» - **неверно**. Каталог
  `transforms/5fc6888f589349a983738ae4e97fabe6` создан 2026-07-02 20:26:23 и с тех пор не менялся.
  Кросс-дисковая раскладка была такой всё время.
- «Транзиент» - **неверно**. Пять прогонов подряд, ошибка побайтово одна.
- «Виновата правка S1360 в `HeadlessPhotoCapturer.kt`» - **неверно**. Отказ в обработке JAR-а Glide,
  до компиляции исходников дело не доходит.
- Очистка `app_v2/build/kspCaches`, `app_v2/build/generated/ksp` и `.gradle/configuration-cache` -
  не помогает ни по отдельности, ни вместе.

**Почему сработало в 12:36 и 12:41, а потом перестало:** при валидном инкрементальном состоянии KSP
не пересчитывает эти пути. Как только состояние потребовало полного прохода, скрытый дефект вышел
наружу. То есть отказ был отложенным, а не новым.

**KSP1 как путь отступления закрыт.** Проверено прогоном: `ksp.useKSP2=false` даёт
`RuntimeException: KSP1 is no longer available. Please use KSP2 instead`. Плагин
`com.google.devtools.ksp` версии 2.3.8 несёт только KSP2.

---

## 3. Исправление

`ksp.incremental=false` в `gradle.properties` с записанной причиной. Инкрементальный учёт - это и
есть код, который считает кросс-корневой относительный путь; без него считать нечего.

Почему не иначе:

- **Не `GRADLE_USER_HOME` на `P:`.** Корни бы совпали, но это машинно-специфичный абсолютный путь.
  `gradle.properties` уже несёт прецедент и объяснение, почему такие пути сюда не коммитятся
  (строки 1-5, про `org.gradle.java.home`): они роняют Linux-раннер CI. Осталось бы решением на
  одной машине, а тикет заведён на репозиторий.
- **Не откат на KSP1** - его нет, см. §2.
- **Не смена версии KSP** - пин тулчейна меняется отдельным решением, а не попутно в багфиксе.

Цена измерена, а не оценена:

- Прогон без изменений - 2 с, задачи `UP-TO-DATE`.
- Правка одного файла - **24 с** (`BUILD SUCCESSFUL`). Быстрый путь `docs/BUILD_TEST_FAST_PATH.md`
  описывает полосу 14-21 с, порог фон/передний план из CLAUDE.md §6 - 120 с. Укладывается.
- Разовая полная пересборка сразу после смены свойства - 1 мин 55 с. Это цена инвалидации, а не
  постоянная: следующий прогон уже 2 с.

На однокорневой раскладке (Linux CI, либо кеш на том же диске) строка инертна - relativize там не
пересекает корни.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1360 (`BlockNeedUserTest`) - был заблокирован этим дефектом; после правки его
  изменение компилируется (`.\a.ps1 fk` -> exit 0), блокировка снята, тикет ушёл на проверку
  устройством.

---

## 4. Проверка

Кода приложения тикет не трогает - только `gradle.properties`. Доказательства - прогоны 2026-08-03.

- `.\a.ps1 fk` до правки -> `exit 1`, `kspStandardDebugKotlin FAILED`, `different roots`.
- `.\a.ps1 fk` после правки -> `exit 0`, `BUILD SUCCESSFUL`, `compileStandardDebugKotlin` отработал.
- `ksp.useKSP2=false` -> `exit 1`, `KSP1 is no longer available`. Путь отступления закрыт явно.
- Прогон без изменений -> `exit 0`, 2 с, всё `UP-TO-DATE`.
- Правка одного файла -> `exit 0`, 24 с.
- Регрессия по существу: S1360 (`HeadlessPhotoCapturer.kt`), ради которого дефект и вскрылся,
  компилируется после правки - это и есть доказательство, что блокировка снята.
