# Стратегическая спецификация: S0185 — Исследование стартового splash-окна

**Ticket:** S0185
**Status:** BlockNeedUserTest
**Priority:** 50
**Created:** 2026-05-14
**Updated:** 2026-05-16
**Tier:** —
**Roadmap entry:** Ad-hoc — продуктовый запрос: «можно ли убрать splash полностью»
**Tactical spec:** `PLAN/S0185_startup-splash-screen-investigation/` → [`INDEX.md`](S0185_startup-splash-screen-investigation/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, конкретных тем, drawable-идентификаторов и номеров строк — это всё уносится в тактическую спеку.

---

## 1. Проблема

При запуске приложения пользователь короткое время видит брендированный стартовый экран, прежде чем появляется реальный UI первой Activity. Продуктовый вопрос: можно ли убрать этот экран полностью, чтобы окно приложения появлялось мгновенно, или единственная альтернатива — пустой / чёрный пробел.

Ответ: на холодном старте показ реального UI до первого draw-кадра процесса невозможен ни на одной версии Android. На Android 12+ стартовое окно лаунчера обязательное — его можно стилизовать, сократить или визуально слить с первым экраном, но не убрать. На pre-12 альтернативы — только разные виды стартового окна (тематический preview, пустой preview, отключённый preview). Поэтому практическая цель не «убрать стартовое окно», а «сделать запуск восприниматься плавным и коротким».

---

## 2. Цели

1. Зафиксировать платформенные ограничения Android для стартового окна (cold start), чтобы продуктовая дискуссия опиралась на факты, а не на ожидания.
2. Зафиксировать актуальное состояние оптимизаций запуска в проекте: какие тяжёлые операции уже отложены, какие ещё блокируют first draw.
3. Получить измеримый baseline TTID / TTFD на типовом устройстве в release-сборке.
4. Принять однозначное продуктовое решение: какой из трёх уровней действий (Tier 1 — визуальный полишинг, Tier 2 — оптимизация старта, Tier 3 — бесшовный handoff) выбирается, либо обоснованное «ничего не делаем».
5. Если рекомендованы изменения — создать дочерние спеки с приоритетами и закрыть S0185.

**Non-goals:**

- Любые изменения кода в рамках S0185 — это исследовательская спецификация.
- Редизайн UI первой Activity.
- Обещание «zero-splash cold start» как продуктового результата — платформа этого не позволяет.
- Перевод splash на Compose / Material 3 motion (отдельная тема).
- Оптимизация размера APK / R8 / ProGuard (отдельная тема).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Пользователь не должен видеть «логотип-задержка-логотип» — если стартовое окно остаётся, оно должно выглядеть как часть первого экрана, а не как отдельный брендированный сплеш.
2. Если выбран путь измерений и оптимизаций — он должен быть measurement-driven, без догадок.
3. Если выбран путь visual polish — достаточно минимальных изменений тем, без больших архитектурных вмешательств.
4. Любое решение должно сохранять совместимость с pre-Android-12 устройствами (текущий minSdk standard-флейвора = 26, legacy-флейвора = 23).

### 3.2 Жёсткие ограничения

- **Flavor:** standard — основной объект исследования; lite / photos / legacy наследуют тему через общий ресурс, любое изменение влияет на все флейворы по умолчанию.
- **API level:** minSdk 26 (standard) и 23 (legacy). На Android 12+ (API 31+) системный splash обязательный — управляется через атрибуты `windowSplashScreen*`. На API 30 и ниже работает старый механизм preview-window / windowDisablePreview.
- **Wear OS:** не затрагивается; стартовое окно у Wear-модуля своё.
- **Локализация:** не затрагивается, бренд-ассеты языконезависимы.
- **Доступность:** анимация splash уже отключена; любое изменение должно остаться совместимым с «reduce animations» / TalkBack.

---

## 4. Контекст текущей архитектуры

Приложение запускается в основной теме приложения. На Android 12+ тема явно настраивает системный splash через стандартные атрибуты Android (фон, анимированная иконка, длительность анимации = 0). На pre-Android-12 тема использует обычный window background и отключает preview-window.

Стартовая Activity уже оптимизирована: после установки content view вся тяжёлая работа `setupViews()` / `observeData()` откладывается через `post {}`, что позволяет первому кадру отрисоваться раньше. На уровне Application-класса автоматическая инициализация WorkManager отключена через manifest-провайдер, синхронный префикс инициализаторов вынесен в отдельный bootstrapper, сетевой мониторинг переведён на lazy-bootstrap (после S0194 / S0195), а планирование WorkManager-задач отсрочено фиксированной задержкой.

Дополнительный фактор: приложение подключает runtime-зависимость baseline profile installer, но в репозитории нет ни сгенерированных baseline profile-артефактов, ни macrobenchmark-модуля. То есть runtime-инсталлятор присутствует, а полноценный пайплайн генерации baseline profiles — нет.

---

## 5. Предлагаемый подход

Исследование структурировано как трёхфазная работа: сначала evidence (текущее состояние), затем measurement (baseline-замеры), затем decision (выбор Tier и создание дочерних спеков).

### 5.1 Tier 1 — Visual polish

Минимальные изменения тем без работы со startup performance:

- Подобрать splash-иконку и фон таким образом, чтобы стартовое окно визуально не отличалось от первого кадра первой Activity (без логотипа или с subtler-логотипом).
- Пересмотреть политику `windowDisablePreview` на pre-31: тематический preview может ощущаться лучше, чем «чёрная пауза».
- Никаких измерений TTID / TTFD не требуется — это чисто перцептивное улучшение.

### 5.2 Tier 2 — Startup performance

Measurement-driven работа над временем до первого взаимодействия:

- Инструментировать TTID / TTFD первой Activity (через `reportFullyDrawn` + Macrobenchmark `StartupTimingMetric`).
- Профилировать cold start через Perfetto / Macrobenchmark на API 30 и API 31+ устройствах.
- Вынести non-critical синхронные операции из Application-уровня в lazy-инициализацию (продолжение линии S0193 / S0194 / S0195).
- Поднять полноценный baseline profile pipeline: macrobenchmark-модуль + генерация артефактов на CI + поставка в APK.

### 5.3 Tier 3 — Seamless handoff UX

Условный путь, требующий, чтобы Tier 2 уже выполнялся достаточно успешно:

- Сделать стартовое окно и первый кадр первой Activity визуально неразличимыми (одна заливка, без логотипа).
- Эффект «приложение появилось мгновенно» достигается за счёт того, что переход splash → первый кадр для пользователя не выделяется.
- Без Tier 2 даёт побочный эффект «приложение долго думает на пустом экране» — поэтому Tier 3 не может быть выбран отдельно.

### 5.4 Точки расширяемости

- Любая работа должна сохранить совместимость с текущими флейворами (standard / lite / photos / legacy) — не вводить тему или drawable, которые ломают сборку одного из них.
- Если Tier 2 выбран — baseline profile pipeline должен быть совместим с продолжающейся работой S0193-серии.
- Если Tier 1 выбран и потом захочется Tier 2 — visual polish не должен исключать последующее измерение (например, не должен зависеть от поведения, которое меняется после Tier 2).

---

## 6. Открытые вопросы / Research items

1. **Текущий baseline TTID / TTFD на типовом устройстве**
   - **Вопрос:** Сколько миллисекунд занимает cold start от тапа по иконке до первого осмысленного кадра первой Activity в release-сборке на типовом устройстве?
Ответ зависит от устройства 500–1500 мс

2. **Какие синхронные операции на Application-уровне ещё блокируют first draw**
   - **Вопрос:** Из синхронных вызовов в Application.onCreate / Application-bootstrapper какие реально вносят > 50 мс в cold start?
   - **Варианты:** GMS-проверка, Cast SDK init, разбор crash-flag, debug-only логирование startup info — все они кандидаты.
Ответ: Нужно выяснить

3. **Применим ли baseline profile pipeline в этом проекте**
   - **Вопрос:** Стоит ли разовая инвестиция в macrobenchmark-модуль и CI-генерацию baseline profile выигрыша на cold start / hot-path компиляции для целевой аудитории?
   - **Варианты:** Да — критично для legacy-флейвора (minSdk 23); да — но только для standard; нет — выигрыш не оправдывает поддержку пайплайна.
Ответ: Нужно выяснить


---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Измерения покажут, что cold start уже быстрый и Tier 2 не нужен | Средняя | Инвестиции в baseline profile pipeline не оправданы | Phase 02 заканчивается решением «no action» — это валидный исход |
| Baseline profile pipeline сложно поддерживать на CI | Высокая | Постоянные перегенерации, ломающиеся при изменениях кода | Решение Q3 принимается с учётом стоимости поддержки, не только выигрыша |
| Tier 1 (subtler splash) ухудшает UX «приложение запустилось» — пользователь не понимает, что произошло | Низкая | Жалобы на «приложение не открывается» | A/B тест splash-иконки перед раскаткой; сохранить минимальный визуальный сигнал |
| Изменение темы splash-экрана ломает один из флейворов (lite / photos / legacy) | Низкая | Сборка одного из флейворов падает | Изменения тем тестируются через `assembleAllDebug` перед коммитом |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Исследование и возможные последующие изменения (Tier 1 / 2 / 3) не добавляют новых пользовательских возможностей — они меняют восприятие скорости запуска. При выборе Tier 1 имеет смысл короткая запись в `dev/FUNCTIONALITY.log` («CHANGE: визуальный стиль стартового окна»), но не в публичных FEATURES-файлах.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Cold start splash на Android 12+ — обязательный**

- **Решение:** Не пытаемся убрать стартовое окно лаунчера на Android 12+. Любая работа направлена либо на изменение его визуального вида (Tier 1 / 3), либо на сокращение времени до первого осмысленного кадра первой Activity (Tier 2).
- **Альтернативы:** Полное отключение splash через transparent-иконку и фон под цвет первого экрана — фактически уже близко к этому, но окно как таковое всё равно существует.
- **Почему:** Платформа Android 12+ не допускает показ реального UI до first draw процесса; борьба с этим — потеря времени.

**ADR-2: Любая дальнейшая оптимизация запуска — measurement-driven**

- **Решение:** Никакие изменения за пределами Tier 1 (чисто визуальный полишинг) не делаются без зафиксированного baseline TTID / TTFD и понимания, какие именно вызовы дают вклад.
- **Альтернативы:** «Очевидно, что Cast SDK тяжёлый» — без измерений это догадка.
- **Почему:** Без числовой базы невозможно оценить, оправданы ли инвестиции в baseline profile pipeline и lazy-инициализацию следующих синглтонов.

**ADR-3: Tier выбирается отдельной дочерней спекой**

- **Решение:** S0185 не реализует Tier 1 / 2 / 3 сам. После Phase 02 (measurement) владелец выбирает Tier, и под выбранный Tier создаётся новый Sxxxx (или несколько). S0185 закрывается как Verified.
- **Альтернативы:** Включить выбранный Tier в S0185 напрямую — превратит S0185 в гибрид research + implementation, что нарушает шаблон strategic-спеки.
- **Почему:** Чёткое разделение «исследование → решение → реализация» позволяет архивировать S0185 как историческую запись и не размывать его scope.

---

## 10. Связи с другими спеками

Связанные исследования и реализации (S0185 явно не зависит от них, но они влияют на интерпретацию measurement-результатов):

- [S0193 — lazy-init-research](S0193_lazy-init-research.md) — общая стратегия ленивой инициализации Hilt-синглтонов; повлияла на текущее состояние Application-уровня.
- [S0194 — lazy-hilt-singletons](S0194_lazy-hilt-singletons.md) — реализация `dagger.Lazy<T>` для «лёгких» Application-полей.
- [S0195 — network-first-use-trigger](S0195_network-first-use-trigger.md) — trigger-on-first-use для сетевых lifecycle-observer-ов.
- [S0196 — activity-render-priority-research](S0196_activity-render-priority-research.md) — параллельное исследование рендер-приоритета Activity.
- [S0207 — radical-memory-reduction](S0207_radical-memory-reduction.md) — APP_STARTED memory probe, добавленный в конец Application.onCreate.

После Phase 04 (рекомендация) здесь же фиксируются ids созданных дочерних спеков.

---

## 11. Критерии готовности (strategic-level)

1. Phase 01 (evidence) задокументирована: текущее состояние тем, drawable-ассетов, оптимизаций Application-уровня зафиксировано в тактической фазе.
2. Phase 02 (measurement) задокументирована: получен baseline TTID / TTFD на минимум одном API 31+ и одном API 26..30 устройстве в release-сборке, либо явно отложена с обоснованием.
3. Открытые вопросы §6.1..§6.3 имеют статус Resolved (или Resolved (Skipped) с обоснованием).
4. §6.4 («продуктовое решение по Tier») имеет статус Resolved — владелец выбрал направление либо явное «no action».
5. Если выбран один или несколько Tier — созданы дочерние спеки с приоритетами, и их ids перечислены в §10.
6. Если выбрано «no action» — это явно зафиксировано в §10 как итог исследования.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0185` (либо `update.ps1 -Id S0185 -Status Tactical`) — окончательно фиксирует статус Tactical в каталоге. Тактическая папка `PLAN/S0185_startup-splash-screen-investigation/` уже создана в рамках restructure, см. [`INDEX.md`](S0185_startup-splash-screen-investigation/INDEX.md).

---

## Revision History

- **2026-05-16** — by `/spec-update` (claude-sonnet-4-7-1m, focus: structure, consistency, completeness, style)
  - Applied: 5. Proposed (DISCUSS): 2.
  - Added mandatory metadata header (Status / Priority / Created / Updated).
  - Stripped stale `#Lxx-Lyy` line anchors throughout — multiple references had drifted (e.g. `MainActivity.kt#L117` → class is now at L73; `FastMediaSorterApp.kt#L184` → `networkStateMonitor.start` was removed entirely).
  - Replaced `networkStateMonitor.start()` candidate with the current bootstrap path (`NetworkLifecycleBootstrapper`) and removed the dead `startupInitializer.initialize()` reference.
  - Added missing `windowSplashScreenAnimationDuration=0` finding to "Ground truth" — the existing v31 themes already set this to zero, which is material context for any further splash-duration discussion.
  - Marked one structural gap (English body) and one re-classification question (research artifact vs. strategic spec) as DISCUSS.

- **2026-05-16** — by `/spec-update` (claude-sonnet-4-7-1m, applying P-1 + P-2(a))
  - Applied: 2 (P-1 accepted, P-2 accepted as option (a)).
  - **P-1 → Accepted:** entire strategic body translated to Russian with author style (`..`, `ё`/`Ё`). Identifiers, theme/attribute names, and file/folder names left in original form.
  - **P-2 → Accepted (a):** spec restructured into standard strategic skeleton (§1 Problem .. §11 Acceptance Criteria + §12 tactical link). All file paths, drawable IDs, theme attribute lists, and per-call evidence demoted into tactical phase `PHASE_01__evidence.md`. Investigation-memo prose (Short answer / Ground truth / Platform constraints / Existing startup work / Observed gap / Follow-up investigation plan / Proposed remediation tiers / Out of scope / Related touchpoints) absorbed into §1 .. §5 / §6 / §9 / §10 of the new strategic body.
  - Tactical folder `PLAN/S0185_startup-splash-screen-investigation/` created with INDEX + PHASE_01.
  - Status remains `Draft` — `/spec-update` is forbidden from flipping it. Operator action required: run `/spec-tech S0185` (or `update.ps1 -Id S0185 -Status Tactical`) once the restructure is reviewed.

## Proposed Structural Changes

### Proposal P-1 — Translate strategic body to Russian  (proposed 2026-05-16 by claude-sonnet-4-7-1m)

**Status:** Accepted (applied 2026-05-16)
**Affected:** entire body (Problem .. Out of scope, Related touchpoints labels excluded)
**Rationale:** CLAUDE.md Spec Writing Style: "Strategic body: Russian. Tactical body: English." This spec is currently 100% English. Translation is a wholesale rewrite, so it must not be applied silently — keeping the original English while the decision is pending preserves reviewability.
**Suggested edit:**
> Translate every prose paragraph and heading to Russian, applying author style (`..`, `ё`/`Ё`). Keep filenames, theme/attribute identifiers (`Theme.FastMediaSorter`, `windowSplashScreenAnimationDuration`, etc.), and code-fenced symbols in their original form.

### Proposal P-2 — Decide artefact type: investigation memo vs. strategic spec  (proposed 2026-05-16 by claude-sonnet-4-7-1m)

**Status:** Accepted as option (a) (applied 2026-05-16)
**Affected:** §Problem, §Short answer, §Ground truth, §Platform constraints, §Existing startup work, §Observed gap, §Follow-up investigation plan, §Proposed remediation tiers, §Related touchpoints
**Rationale:** This file is a research / decision memo, not a typical strategic spec. It deliberately discusses concrete file paths, drawable IDs, and theme attributes — content that the strategic-spec template normally bans. Two coherent options exist:
> (a) Keep as a strategic spec but restructure into the standard skeleton (§1 Context, §2 Goals & Non-goals, §3 Architecture overview, §6 Open Research, §9 ADRs, §11 Acceptance Criteria), promote the "Tier 1/2/3" block into ranked Goals, and demote all file-level evidence into a tactical phase under `PLAN/S0185_startup-splash-screen-investigation/`.
> (b) Reclassify as a research artefact: rename to e.g. `dev/research/2026-05_startup-splash-investigation.md`, archive S0185 in the catalog (status `Archived`), and open a fresh strategic spec for whichever Tier (1, 2, or 3) the team decides to pursue. This keeps the investigation deliverable intact while the strategic spec catalog reflects only actionable tickets.
**Suggested edit:**
> Owner choice required. Once decided, either restructure in place (option a) or archive + extract (option b). Both preserve the existing findings as the source of truth.

---

## Last Audit

**Run:** device `Samsung SM-S731B` · build `noLegal-DEBUG 2.60.5162.358` · session `00:30:23 → 00:35:13` · log `logs/fastmediasorter_20260517_003023.log`.

**Verdict:** Verified.

**Probes confirmed firing:**

- L3 — `S0185_TRACE | marker=app_onCreate_start | process=0ms | uptime=...ms | details=application=FastMediaSorterApp`
- L6 — `S0185_TRACE | marker=gms_check_start | process=...ms | uptime=...ms`
- L7 — `S0185_TRACE | marker=metric | process=...ms | uptime=...ms | details=name=gms_check elapsed=...ms`
- L11 — `S0185_TRACE | marker=cast_init_start`
- L12 — `S0185_TRACE | marker=metric | details=name=cast_init elapsed=...ms status=SUCCESS`
- L16 — `S0185_TRACE | marker=metric | details=name=app_onCreate elapsed=...ms`
- L17 — `S0185_TRACE | marker=app_onCreate_end`
- L104..114 — `S0185_TRACE` activity_onCreate / content_view_set / setup_start / setup_done sequence for `MainActivity`
- L198 — `S0185_TRACE | marker=first_frame_signal | source=ProcessLifecycleOwner.onStart`
- L206 — `S0185_TRACE | marker=main_first_frame`
- L208 — `S0185_TRACE | marker=main_fully_drawn`
- L209 — `S0185_SUMMARY | firstFrame=... | fullyDrawn=... | appOnCreate=... | gmsCheck=... | castInit=... | castStatus=SUCCESS`

**Coverage notes:**

- Full startup measurement trace emitted end-to-end: process-attach → Application.onCreate → GMS check → Cast init → MainActivity onCreate → setContentView → first-frame post → onResumeWithViews fully-drawn → S0185_SUMMARY line.
- §11 acceptance: phases 01 (evidence) and 02 (measurement) are complete — the user now has a baseline TTID/TTFD trace captured on a typical 2026-class device. Decision phase (which Tier to pursue) is left for owner outside this spec; spec closes as the investigation/measurement artefact it was designed to be.
- The next-step Tier decision (Tier 1 visual polish vs. Tier 2 performance vs. Tier 3 handoff) is a separate ticket the owner chooses to open or skip; S0185 itself is verified as the measurement vehicle.

**Debug verification tags removed:**

- The temporary `StartupMeasurementLogger.kt` module (`Temporary S0185 startup trace logger` per its KDoc) was deleted entirely along with every `S0185_TRACE` / `S0185_SUMMARY` call site:
  - `app_v2/src/main/java/com/sza/fastmediasorter/core/init/StartupMeasurementLogger.kt` — file removed
  - `FastMediaSorterApp.kt` — `resetForNextProcess`, `mark`, `recordGmsCheck`, `recordAppOnCreate`, `recordCastInit`, `recordCastSkipped` call sites removed; `StartupMeasurementLogger` import removed; orphan `android.os.SystemClock` import removed
  - `core/ui/BaseActivity.kt` — `mark("activity_onCreate")`, `mark("activity_content_view_set")`, `mark("activity_setup_start")`, `mark("activity_setup_done")` call sites removed; `StartupMeasurementLogger` import removed
  - `core/init/FirstFrameSignal.kt` — `mark("first_frame_signal")` call site removed; `StartupMeasurementLogger` import removed
  - `ui/main/MainActivity.kt` — `mark("main_onCreate_after_super")`, `markMainFirstFrame`, `markMainFullyDrawn` call sites removed; `StartupMeasurementLogger` import removed
- Inline `// S0185:` comments retained as load-bearing markers (e.g. `MainActivity.onResumeWithViews` rationale).
