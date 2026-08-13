# Стратегическая спецификация: S0286 - Compliance lint-gate и санитизация FEATURES от поимённых платформ

**Ticket:** S0286
**Status:** Verified
**Priority:** 65
**Date:** 2026-05-21
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - закрытие двух follow-up гэпов из аудита S0140 (2026-05-21): §11.9 (compliance lint-gate не реализован) и §11.11 (`docs/FEATURES*.md` всё ещё содержит поимённые платформы).
**Tactical spec:** `PLAN/S0286_compliance-lint-gate-and-features-sanitization/`
**Tactical plan:** `PLAN/S0286_compliance-lint-gate-and-features-sanitization/INDEX.md`
**Implemented date:** 2026-05-21

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

S0140 ADR-6 зафиксировал требование «CI lint-gate, ломающий сборку при появлении поимённой платформы в литералах `src/main/`», и §11.9 / §11.11 закрепили его как критерий готовности. Аудит 2026-05-21 показал, что инвариант не реализован: `app_v2/build.gradle.kts` имеет `abortOnError = true`, но не имеет ни custom Lint-правила, ни Gradle-таски, ни любого другого механизма, который запрещал бы появление литералов `"Instagram"`, `"TikTok"`, `"Threads"`, и других имён в market-flavor сорсах. Параллельно `docs/FEATURES.md:280-283` (плюс `_RU` / `_UK` зеркала) до сих пор поимённо называют `Instagram`, `TikTok`, `Threads` - это формулировки эпохи S0116/S0117, переехавшие в S0140 как pre-existing tech debt и не убранные.

Эффект: любой коммит может молча вернуть поимённое упоминание платформы в market-сборку и пройти CI. Это нарушает §3.2 hard constraint S0140 «Compliance - поимённые платформы» и создаёт реальный риск отклонения публикации в Google Play (Play Store IP policy targets *inducing/encouraging copyright infringement*, и хотя обобщённая capability допустима, поимённое нацеливание на платформу - нет). Аудит S0140 уже стоит в `Partial` именно из-за этих двух открытых WARN; без закрытия их S0140 не может перейти в `Verified`.

---

## 2. Цели

1. Любой коммит, добавляющий поимённое упоминание платформы из deny-list в market-flavor сорсы (`src/main/`, либо `src/<market-flavor>/`), приводит к красному CI - сборка `assembleStandardDebug` и далее `lint` падают с понятным сообщением и указанием на конкретный файл/строку.
2. `docs/FEATURES.md` и оба зеркала (`_RU`, `_UK`) больше не содержат поимённых упоминаний платформ; затронутые буллеты (auto-download / background queue / Instagram-Threads / multi-account) переписаны в нейтральных формулировках, описывающих capability, а не конкретный сайт.
3. Deny-list - не магическая константа: она задокументирована (где живёт, как добавить новое слово, как легитимно разрешить упоминание в `noLegal`-сорсах, что считается false-positive).
4. После того как этот тикет уходит в `Implemented`, повторный аудит S0140 убирает `WARN` по §11.9 и §11.11. Финальный статус S0140 после этого зависит только от его оставшихся manual/on-device пунктов.

**Non-goals:**

- Не трогать runtime-пилары S0140 (P/Q/R+S/T/U/V) - они уже SHIPPED и exercised, эта спека сугубо про compliance-инструментарий.
- Не вводить deny-list в `noLegal` flavor - sideload-канал является легитимным site-specific extraction surface, и поимённые упоминания платформ там разрешены и нужны.
- Не трогать `docs/FEATURES_noLegal*.md` (gitignored sideload-документация) - по дизайну там платформы названы.
- Не трогать `dev/CHANGELOG.md`, commit-сообщения, `PLAN/*.md`, `dev/CATALOG/*.md` и любые внутренние engineering-документы - они вне deny-list, потому что не уходят в публикацию.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Скорость доставки важнее архитектурного блеска: если выбор стоит между быстрым Gradle-таском и полноценным custom Lint-правилом - сначала ship Gradle-таск, custom Lint можно докрутить отдельной фазой позднее.
2. Ошибка должна быть actionable: разработчик, не знакомый с историей S0140, по сообщению CI должен сразу понять, что делать (какое слово запрещено, в каком файле, как его правильно убрать, куда обратиться за исключением).
3. Deny-list должна быть легко расширяема - добавить новое имя платформы при появлении новой угрозы должно занимать одну строку правки одного файла, без повторного гонения custom Lint-модуля через kapt.
4. Исключения для `noLegal` обязаны быть явными и тривиально проверяемыми (один path-фильтр, не whitelist по hash или md5).

### 3.2 Жёсткие ограничения

- **Flavor:** deny-list применяется только к market-flavor сорсам (`standard`, `legacy`, `lite`, `photos`, `vr`). Поведение строго соответствует `dev/FLAVOR_DEVELOPMENT_RULES.md` - `noLegal` source-set (`app_v2/src/noLegal/`) полностью исключён из проверки, потому что sideload-канал по дизайну site-specific.
- **API level:** без зависимостей от уровня Android - проверка статическая, на этапе сборки.
- **Wear OS:** не затрагивается (`wear/` модуль не входит в scope; market-публикация watch-сборки уже соответствует политике).
- **Производительность:** проверка должна добавлять ≤ 2 секунды к холодному `assembleStandardDebug` на текущей дев-машине; на инкрементальной сборке - ≤ 200 мс (cache hit или skip when nothing changed).
- **Совместимость данных:** изменений пользовательских хранилищ нет; deny-list - чисто build-time артефакт.
- **Локализация:** EN/RU/UK - обязательно для всех правок `docs/FEATURES*.md`. Все три зеркала переписываются в одной фазе, в одном коммите, синхронно. Любой деривация (например, появление новой capability-строки в EN без RU/UK) ломает аудит S0140 §11.12 и трактуется как регрессия.
- **Доступность:** не применимо (build-time gate без UI surface).
- **Compliance - поимённые платформы (наследуется из S0140 §3.2):** seed deny-list - `Instagram`, `TikTok`, `Threads`, `threads.com`, `threads.net`, `Facebook`, `Snapchat`, `Twitter`, `X.com`, `Reddit`, `Pinterest`, `LinkedIn`, `Tumblr`, `Vimeo`, `Dailymotion`. `YouTube` / `youtube.com` / `youtu.be` - tricky case: для S0140 / generic-чейна они запрещены в market, но для core media intent routing встроены в Android (`android.intent.action.VIEW` на YouTube intent-filter в `noLegal`/`vr`) - решение по §6.1.
- **Communication policy:** переписывание `docs/FEATURES*.md` проходит чек-лист `docs/COMMUNICATION_POLICY.md` §6 (tone gate) и его трилингвальные зеркала. Сохраняем capability-описание, теряем поимённые ссылки; tone остаётся ровным, без bullet-summary типа «that's why we built this».

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** market-flavors (`standard`, `legacy`, `lite`, `photos`, `vr`) попадают под проверку; `noLegal` исключён через path-фильтр на уровне Gradle-таски и через `src/noLegal` source-set в Lint-правиле (если оно будет добавлено в фазе 2). Никакого `BuildConfig`-флага в `src/main` для deny-list - это нарушило бы CLAUDE.md Rule 15.
- **Localization:** EN / RU / UK переписаны синхронно, валидация - mental equivalent `check_strings_localized.ps1` для `docs/FEATURES*.md` (поскольку формальный скрипт работает только со `strings.xml`, тактическая фаза предложит lightweight grep-сверку «один capability-буллет в каждом локалe-зеркале»).
- **Communication policy:** новые буллеты `docs/FEATURES*.md` проходят §6 tone-gate `docs/COMMUNICATION_POLICY.md`; deviation допускается только под legal-обязательством (не применимо в этой спеке).
- **Validation level:** Config (`build.gradle.kts` + новая Gradle-таска / Lint-модуль) + Doc (`docs/FEATURES*.md`) → Mixed change по Validation Requirements; closure - `assembleStandardDebug` PASS + `assembleStandardDebug` FAIL на negative-test (внедрение запрещённого литерала вручную, восстановление, повторный PASS).
- **Owner sign-off:** 2026-05-21 (commit point in chat: автор S0140 inputs).
- **Related tickets:** parent audit S0140 (Partial → flipping to Verified depends on this), originator S0116 (deny-list spirit), exemption rationale S0117 (sideload site-specific), tone canon S0118 (COMMUNICATION_POLICY).

---

## 4. Контекст текущей архитектуры

Lint-инфраструктура проекта - стандартная AGP `android { lint { ... } }` блок в `app_v2/build.gradle.kts` (строки 592-609 на момент аудита). `abortOnError = true` гарантирует, что любая Lint-ошибка валит `assembleStandardDebug` / `assembleStandardRelease`. Что отсутствует - так это custom Issue / Detector, который бы знал про deny-list платформ. Сегодняшняя цепочка `./gradlew lint` отлавливает только встроенные правила (`InvalidPackage`, `MissingTranslation`, `NewApi` и т.д.), часть из которых даже отключена через `disable +=`.

Документация `docs/FEATURES*.md` - публичный engineering-каталог фич, читается человеком; трилингвальные зеркала `_RU` / `_UK` синхронизируются вручную или через `/doc-update`-skill. Никакого автоматизированного gate, проверяющего «список запрещённых литералов» здесь сегодня нет - предыдущий аудит S0140 поймал расхождение глазами через `grep`.

`noLegal` source-set имеет легитимные site-specific литералы (имена сервисов, host'ы YouTube/SoundCloud в `ALLOWED_SERVICE_IDS` NewPipe-стратегии); эти упоминания - часть архитектурного контракта sideload-канала и не должны быть тронуты deny-list'ом. Любая проверка обязана уметь различать `app_v2/src/main/` (под gate'ом) и `app_v2/src/noLegal/` (вне gate'а) по пути исходника.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Столп A - Build-time deny-list registry.**

- Один источник истины - текстовый файл (или однострочный список в `build.gradle.kts`), содержащий запрещённые литералы и метаданные о каждом (одна строка - одно слово / host).
- Источник доступен и Gradle-таске (Столп B), и future custom Lint-правилу (Столп C).
- Расширяемость: добавление нового запрещённого слова - правка одного файла, без kapt / без regenerate / без сборки нового AAR.

**Столп B - Gradle verify-task (первая итерация, ship-first).**

- Standalone Gradle task `verifyNoPlatformNames` (или эквивалентное имя, окончательное - в `/spec-tech`).
- Сканирует `app_v2/src/main/`, `app_v2/src/standard/`, `app_v2/src/legacy/`, `app_v2/src/lite/`, `app_v2/src/photos/`, `app_v2/src/vr/`. **Не сканирует** `app_v2/src/noLegal/`, `dev/`, `docs/FEATURES_noLegal*.md`, `PLAN/`, `temp/`, `V1/`, `v2_6/`.
- Фильтр расширений: `.kt`, `.java`, `.xml` (strings, layouts, manifests), `.kts` (если в market-источниках). HTML / Markdown в market-флейворах нет, поэтому не нужно расширять.
- Сообщение об ошибке: `Forbidden platform name "<word>" found in <path>:<line>. See docs/<governance-doc>.md for the deny-list policy.`
- Plug into task-graph: `tasks.named("preBuild").dependsOn(verifyNoPlatformNames)` или эквивалент, который ловит сборку **до** долгого Kotlin-compile.
- Производительность: parallel walk по filtered tree, кэш через Gradle inputs/outputs annotations, чтобы не ререранить на инкрементальной сборке.

**Столп C - Custom Android Lint rule (опциональная фаза 2).**

- Реализуется только если Gradle-таски (Столп B) окажется недостаточно (например, нужны PSI-проверки для escape-cases типа `"Insta" + "gram"`, что Gradle-таск не отлавливает grep'ом).
- Architecture: отдельный `lint-checks/` модуль с `Detector` + `Issue` + `IssueRegistry`, подключается через `lintChecks` configuration.
- Не блокирует первую итерацию - решение «нужно ли строить» зависит от §6.2.

**Столп D - FEATURES доку-санитизация.**

- Перепись затронутых буллетов `docs/FEATURES.md:280-283` + EN / RU / UK зеркала.
- Capability-описание сохраняется (что приложение умеет принимать share-URL'ы и качать по ним media), поимённые упоминания платформ убираются.
- Tone проходит `docs/COMMUNICATION_POLICY.md` §6 чек-лист.
- Параллельно - однократная проверка остальных мест в `docs/FEATURES*.md` на тот же deny-list (в текущем аудите выявлены только эти 4 буллета, но full-pass safer).

**Столп E - Governance документация.**

- Однострочный README или раздел в `docs/COMMUNICATION_POLICY.md` (либо отдельный `docs/COMPLIANCE_DENYLIST.md`) описывающий: где живёт deny-list, как добавить слово, как исключить путь, что делать если CI упал.
- Цель - чтобы новый разработчик, столкнувшийся с CI-failure, нашёл ответ за 30 секунд.

### 5.2 Потоки данных и событий

Сборка `assembleStandardDebug`:

- AGP запускает task-graph → `preBuild` → triggered → `verifyNoPlatformNames` запускается → читает deny-list source → читает фильтрованный source tree → если нашёл совпадение - emit failure с file:line + word → exit non-zero → Gradle обрывает task-graph → CI красный.
- Если совпадений нет - task завершается за < 200 мс на инкрементальном входе - и task-graph продолжается на `compileKotlin`.

Build-time проверка не имеет runtime канала - никаких уведомлений или Timber-логов в приложении. Это полностью статический gate.

### 5.3 Точки расширяемости

- Deny-list source - один текстовый источник, расширение - одна строка.
- Path-фильтры - выражены declaratively (set of relative source-roots), новый flavor добавляется одной правкой `build.gradle.kts` либо deny-list-конфига.
- Future custom Lint module (Столп C) - подключаемый отдельным Gradle-зависимостью; если Столп B окажется достаточным навсегда, Столп C просто никогда не добавляется без поломки контракта.
- Future regex-rules (host-fragment matching типа `*.instagram.*`) - возможно добавить как отдельный список правил в том же deny-list source, со строгим switch literal-vs-regex.

---

## 6. Открытые вопросы / Research items

1. **YouTube как edge-case в deny-list**
   - **Вопрос:** `YouTube` / `youtube.com` / `youtu.be` появляются в market-flavor (`standard`, `vr`) контексте по двум легитимным причинам: (а) intent-filter в манифесте маршрутизирует share-intent на YouTube URL'ы в noLegal extractor, ВНУТРИ `src/noLegal/AndroidManifest.xml` (вне gate'а); (б) UrlCanonicalizer в `src/main/` нормализует `music.youtube.com → www.youtube.com` (S0260 §1 - часть YTMusic recovery). Сценарий (б) технически попадёт под deny-list, если включить `youtube.com` без оговорок.
   - **Варианты:** (i) не включать YouTube в deny-list совсем (риск: маркетинговый материал случайно протащит «watch YouTube videos in FMS»); (ii) включить YouTube, но добавить path-исключение для `UrlCanonicalizer.kt` и любых других файлов, где упоминание неизбежно (хрупко - требует обновлять path-list при рефакторах); (iii) включить YouTube как literal, но позволить comment-exemption `// allow-platform-literal: <причина>` рядом со строкой (читаемо для review, robust к рефакторам).
   - **Нужно выяснить:** провести `grep -rn "youtube\|YouTube\|youtu\.be" app_v2/src/main/ app_v2/src/standard/ app_v2/src/vr/` и определить, сколько легитимных вхождений есть; от этого числа зависит, какой из вариантов проще.
   - **Статус:** Resolved - первая итерация исключает `YouTube` / `youtube.com` / `youtu.be` из seed deny-list, так как в market-source остаются легитимные Google-auth и media-routing ссылки. Повторный визит - отдельный follow-up после burn-down'а legacy YT-поверхности.

2. **Достаточно ли Gradle-таски, или custom Lint-правило обязательно**
   - **Вопрос:** Gradle-таска grep'ает по тексту - escape-cases типа `"Insta" + "gram"`, или конкатенация через интерполяцию, или Base64-обфускация - могут пройти. Custom Lint-правило, работая на PSI, может ловить такие случаи (constant-folding analysis). Стоит ли проектировать инфраструктуру так, чтобы Столп C обязательно был добавлен в фазу 2, или Столп B остаётся единственным gate'ом «навсегда»?
   - **Варианты:** (i) Столп B - единственный gate (риск: escape-cases пройдут, но реальная вероятность что кто-то будет специально обфусцировать имя платформы - крайне низкая в этой команде); (ii) Столп B как фаза 1, Столп C как обязательная фаза 2 (риск: scope creep, но более надёжно).
   - **Нужно выяснить:** owner decision - готов ли проект жить с риском escape-case'ов в обмен на простоту и скорость доставки.
   - **Статус:** Resolved - выбран Столп B (Gradle verify-task) как единственный gate. Custom Android Lint module explicitly out of scope для S0286; вернуться - только при появлении задокументированного escape-case в реальной кодовой базе.

3. **Где живёт deny-list source**
   - **Вопрос:** Текстовый файл в `app_v2/` (например `compliance-denylist.txt`), или отдельный конфиг в `dev/` (например `dev/compliance/platform-deny-list.txt`), или прямо в `build.gradle.kts` как Kotlin `setOf("Instagram", "TikTok", ...)`?
   - **Варианты:** (i) `build.gradle.kts` inline (плюс: один источник, минус: каждая правка - изменение build script, что инвалидирует Gradle config-cache); (ii) `app_v2/compliance-denylist.txt` (плюс: cache-friendly, минус: создаёт новый артефакт в module root); (iii) `dev/compliance/...` (плюс: семантически правильное место для dev-only артефакта, минус: нужно проверить что `dev/` не game-ignored from build).
   - **Нужно выяснить:** проверка, влияет ли изменение build.gradle.kts на Gradle config-cache invalidation (на современных AGP - да).
   - **Статус:** Resolved - deny-list source и legacy baseline живут в `app_v2/compliance/platform-name-denylist.txt` и `app_v2/compliance/platform-name-baseline.txt`; module-local, cache-friendly, вне `src/main/assets/` (не уходит в APK).

4. **Как выглядит exemption для `noLegal` после возможного будущего merge market+noLegal архитектуры**
   - **Вопрос:** Если в будущем (после S0290+ гипотетического) проект перейдёт на single-source architecture с runtime gate вместо source-set isolation - что произойдёт с deny-list?
   - **Варианты:** (i) deny-list следует за source-set-границей и при merge переедет внутрь runtime-конфига; (ii) deny-list перестанет иметь смысл (вся compliance переедет в Google Play submission flow).
   - **Нужно выяснить:** ничего на сегодня - вопрос parked до того момента, когда такая миграция станет реальной задачей.
   - **Статус:** Resolved (parked - не влияет на S0286 первую итерацию).

5. **False-positive сценарии в текстах документации**
   - **Вопрос:** `docs/FEATURES*.md` после санитизации - под gate'ом или нет? Что если новый редактор случайно добавит «Instagram» в новый буллет?
   - **Варианты:** (i) `docs/FEATURES*.md` входит в scope deny-list (плюс: симметрия; минус: документация в принципе не уходит в APK, риск ложный); (ii) `docs/FEATURES*.md` под отдельным lightweight grep-check'ом, не блокирующим сборку.
   - **Нужно выяснить:** простая команда `grep -rn "<deny-word>" docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md` уже достаточна как pre-commit check; решение - в `/spec-tech`.
   - **Статус:** Resolved - публичные `docs/FEATURES.md` + `_RU` + `_UK` сканируются той же `verifyNoPlatformNames`-таской через explicit file inputs; `docs/FEATURES_noLegal*.md` остаются вне scope.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Gradle-таска ловит false-positive в комментариях / KDoc, ломая релевантные дискуссии | Средняя | Разработчик не может закоммитить осмысленный комментарий о компатибилити с платформой | Comment-exemption pattern `// allow-platform-literal: <причина>` (см. §6.1 вариант iii) либо path-фильтр на `.md` файлы вне `docs/FEATURES*.md` |
| Custom Lint-модуль (Столп C) добавляет heavy kapt-cost к каждой сборке | Средняя | Cold build растягивается на десятки секунд | Не строить Столп C пока не докажем что Столп B недостаточен (§6.2); если строить - изолировать в отдельный `lint-checks/` модуль чтобы kapt был targeted |
| Deny-list расходится с актуальным compliance-периметром (новые имена платформ появляются, но не попадают в список) | Высокая | Compliance-gate перестаёт быть полезным | Раздел в governance-документации `docs/<...>.md` (Столп E) описывает процесс обновления; владелец репо проводит ревью раз в квартал (или при появлении новой capability в S-spec) |
| FEATURES.md переписывается слишком абстрактно, теряя пользовательскую ценность | Низкая | Документация становится бесполезной для маркетинга | Communication policy tone-gate §6 проверяет читаемость; перепись пробегает через `/doc-update`-skill |
| `youtube.com` остаётся в `src/main/UrlCanonicalizer.kt` после включения в deny-list без exemption | Высокая | Сборка падает после landing'a первой фазы | §6.1 должна быть resolved до /spec-tech; если §6.1 idle - phase 02 (lint-rule) откладывается до её закрытия |
| Сборка инкрементальная не использует Gradle inputs/outputs кэш и пересканирует source tree каждый раз | Низкая | Падает скорость разработки | Аннотации `@InputFiles` / `@OutputFile` на таске; интеграционный тест-кейс «no changes → task SKIPPED» в `/spec-check` |
| Deny-list source случайно попадает в APK как ассет | Низкая | Список запрещённых слов уезжает в опубликованный артефакт | Source хранится вне `src/main/assets/` и не привязан к `android.assets.srcDirs`; явное исключение в `packagingOptions` если нужно |
| Path-фильтр `src/noLegal/` exemption обходится через симлинк | Низкая | Литералы из `noLegal` контекста утекают в market-сборку | Проверять реальный path после Path.toRealPath() в Gradle-таске |

---

## 8. Влияние на пользователя (docs/FEATURES)

Конкретное изменение в публичной документации:

- `docs/FEATURES.md:280-283` (и оба зеркала `_RU` / `_UK`) переписывается на платформ-агностический язык. Capability-описание сохраняется («приложение принимает share-URL'ы и качает media через WebView-авторизацию для любого хоста»), поимённые упоминания удаляются.
- Multi-account буллет (`docs/FEATURES.md:283`) - сегодняшняя формулировка содержит «personal and work Instagram account» как пример; переформулируем на «personal and work account on the same host».
- `docs/FEATURES_noLegal*.md` - **не трогаем**, по дизайну там платформы названы (gitignored sideload-документация).

Tone проходит `docs/COMMUNICATION_POLICY.md` §6 чек-лист. Никаких новых capability-буллетов - только rewording существующих.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Gradle verify-task как первая итерация, custom Lint-правило - опционально**

- **Решение:** Столп B (Gradle-таск) shipping первым. Столп C (custom Lint module) - только если §6.2 покажет, что escape-cases - реальная угроза.
- **Альтернативы:** (i) сразу строить custom Lint module как «правильно архитектурно»; (ii) держать оба механизма параллельно с самого начала.
- **Почему:** Gradle-таск - ~50 строк build script, шиппится за день; custom Lint module - отдельный gradle-модуль с kapt, IssueRegistry, Detector, тестами через `lint:lint-tests`. Cost / benefit для первого этапа явно в пользу Gradle-таски. Owner-пожелание §3.1.1 эту приоритизацию подтверждает.

**ADR-2: `src/noLegal/` исключается через path-фильтр, не через literal whitelist**

- **Решение:** Gradle-таск принципиально не заходит в директории `app_v2/src/noLegal/`, `dev/`, `docs/FEATURES_noLegal*.md`, `PLAN/`, `temp/`, `V1/`, `v2_6/`.
- **Альтернативы:** (i) per-literal whitelist (например `YouTube` разрешён, `Instagram` - нет); (ii) глобальный path-deny-list.
- **Почему:** path-фильтр - один-файл-одна-проверка, понятный любому разработчику; per-literal whitelist быстро разрастается и становится непредсказуемым. Path-isolation совпадает с уже принятой архитектурной границей market vs sideload (S0156 / S0250 / `dev/FLAVOR_DEVELOPMENT_RULES.md`).

**ADR-3: Comment-exemption pattern для редких легитимных вхождений**

- **Решение:** Если строка в market-source легитимно содержит имя платформы (например, URL host'а, который не может быть выражен иначе) - добавляется inline-комментарий `// allow-platform-literal: <одно-предложение причины>` непосредственно над строкой, и Gradle-таск пропускает её. Comment должен быть актуальным (проверка с tooling-pass в будущем - вне scope).
- **Альтернативы:** (i) глобальный whitelist с списком файлов; (ii) `@Suppress("PlatformNameLiteral")` аннотация (требует Столп C).
- **Почему:** Локальное exemption - один комментарий рядом со строкой - делает решение проверяемым во время code review, не требует поддерживать отдельный whitelist-файл и не зависит от наличия custom Lint module.

**ADR-4: Deny-list source - текстовый файл вне `src/main/assets/`**

- **Решение:** Один plain-text файл (один literal на строку, `#`-комментарии разрешены). Точное место - в `/spec-tech` (см. §6.3), но **не** внутри `src/main/assets/` (чтобы не попасть в APK).
- **Альтернативы:** (i) inline `setOf(...)` в `build.gradle.kts`; (ii) properties-файл; (iii) YAML / JSON.
- **Почему:** Text file - максимально simple, нет parser-зависимости, изменения видны в diff линейно. JSON / YAML потребуют либо bundled parser, либо AGP'шный workaround. `build.gradle.kts` inline инвалидирует config-cache на каждой правке.

---

## 10. Связи с другими спеками

- **S0140** (`extend-market-url-coverage`, Partial) - родительский аудит. После landing'a S0286 WARN §11.9 + §11.11 в S0140 уже закрыты (Phase 03 / Step 03.2 синхронизировал audit-блок S0140). Финальный verdict S0140 (Partial → Verified) зависит только от его оставшихся manual / on-device пунктов и не флипается автоматически этим тикетом.
- **S0116** (`url-media-downloader`, Verified) - originator deny-list-духа, §3.2 compliance constraint наследуется.
- **S0117** (`url-media-downloader-nolegal-flavor`, Archived) - rationale для `noLegal` exemption (sideload site-specific extraction surface).
- **S0118** (`friendly-ui-copy-revision`, In Progress) - communication policy canon; tone-gate переписи FEATURES'а строится на нём.
- **S0156** (`nolegal-capability-surface-audit`) - архитектурная граница market vs sideload, под которую ложится path-фильтр §3.2 / ADR-2.
- **S0260** (`nolegal-ytmusic-audio-share-recovery`, BlockNeedUserTest) - содержит `UrlCanonicalizer` с легитимной обработкой `music.youtube.com` URL'ов, что делает §6.1 (YouTube edge-case) актуальным.

---

## 11. Критерии готовности (strategic-level)

1. `./gradlew :app_v2:assembleStandardDebug` падает с понятным error-сообщением (включая file:line + запрещённое слово), если вручную внедрить любую строку из seed deny-list в любой файл `app_v2/src/main/`, `app_v2/src/standard/`, `app_v2/src/legacy/`, `app_v2/src/lite/`, `app_v2/src/photos/`, `app_v2/src/vr/`.
2. То же `assembleStandardDebug` после отката внедрённой строки PASS'ит без изменений конфигурации.
3. `app_v2/src/noLegal/` остаётся не затронутым - вручную внедрённая строка с поимённой платформой в noLegal-файл НЕ роняет сборку (path-exemption работает).
4. На холодном `assembleStandardDebug` deny-list-task добавляет ≤ 2 секунды wall-time; на инкрементальной (no changes) - task SKIPPED через UP-TO-DATE check.
5. `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` после санитизации содержат zero matches на `grep -rn "Instagram\|TikTok\|Threads\|threads\.com\|threads\.net" docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md`.
6. Три буллета `docs/FEATURES*.md` (auto-download incoming links / Instagram-Threads / multi-account) переписаны в нейтральных формулировках, сохраняющих capability-описание и проходящих `docs/COMMUNICATION_POLICY.md` §6 tone-gate.
7. Governance-документ (`docs/COMPLIANCE_DENYLIST.md` или раздел в `docs/COMMUNICATION_POLICY.md` - точное место в `/spec-tech`) описывает: где живёт deny-list source, как добавить слово, как добавить path exemption, как использовать comment-exemption.
8. После landing'a S0286 последний аудит S0140 показывает 0 WARN, 0 FAIL по §11.9 и §11.11; дальнейший verdict S0140 зависит только от §11.1 / §11.4 / §11.8 manual/on-device пунктов.
9. Negative-test случай: `grep` по всем `app_v2/src/main/**/*.kt`, `**/*.xml`, `**/*.java` на seed deny-list возвращает 0 **новых** строк после landing'a первой фазы. Существующие легаси-вхождения (включая YouTube exemption из §6.1 и ранее закреплённые имена платформ в market-source) зарегистрированы построчно в `app_v2/compliance/platform-name-baseline.txt` (reviewed baseline) и допускаются только через эту запись; добавление новой строки в baseline требует ревью и сопровождается follow-up'ом на её ликвидацию.

---

## 12. Ссылка на тактическую спецификацию

Текущий tactical plan: `PLAN/S0286_compliance-lint-gate-and-features-sanitization/INDEX.md`.

Активные фазы:

- Phase 01: gate-foundation - deny-list source, baseline legacy suppressions, governance doc, cacheable Gradle verify-task wired into `preBuild`.
- Phase 02: features-sanitization - neutral rewrite of `docs/FEATURES.md` + `_RU` + `_UK` and baseline cleanup for the public feature inventory.
- Phase 03: docs-catalog-cleanup - final validation, tactical/strategic metadata closure, and parent-audit follow-up for S0140.

---

## Last Audit

**Date:** 2026-05-22
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 16 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

### Manual / on-device

- [ ] Confirm the performance budget from §3.2 / §11.4 on a clean machine: cold build delta `≤ 2 s`, no-change incremental run `≤ 200 ms` or `UP-TO-DATE`.
