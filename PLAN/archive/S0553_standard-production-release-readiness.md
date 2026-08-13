# Стратегическая спецификация: S0553 - Standard production release readiness

**Ticket:** S0553
**Status:** Archived
**Priority:** 80
**Date:** 2026-06-20
**Tier:** 4 - Large
**Roadmap entry:** Release engineering - standard Google Play production gate
**Tactical spec:** `PLAN/S0553_standard-production-release-readiness/`
**Tactical plan:** `PLAN/S0553_standard-production-release-readiness/INDEX.md`
**Implemented date:** 2026-06-20

> **Scope:** STRATEGIC. Release target, intentional exclusions, regression surface, evidence model, coverage gaps, owner decisions. Без имён классов, путей реализации, лимитов строк, Room-миграций, Hilt-модулей.

<!-- auto-approved by /spec-all - 2026-06-20 -->

---

## 1. Проблема

В репозитории уже есть несколько полезных, но разрозненных артефактов:

- `dev/PRE_RELEASE_MANUAL_TESTS.md` описывает полный ручной сценарный прогон.
- Архивный `/spec-prerelease` (`S0484`) покрывал только эмуляторно-скриптуемый spine.
- `store_assets/PLAY_CONSOLE_CHECKLIST.md` покрывает только операторскую часть Play Console.
- build- и release-скрипты умеют собирать и публиковать артефакты, но не являются продуктовой спецификацией релизной готовности.

Сейчас нет единой стратегической спецификации, которая отвечает сразу на четыре вопроса для **standard production** сборки:

1. Что именно считается целевой функциональной поверхностью standard market build.
2. Какие потери функциональности относительно `debug`, `noLegal`, `vr`, `wear`, эмуляторного smoke и локальных owner-tooling путей являются **ожидаемыми**, а какие являются **регрессией**.
3. Каким набором доказательств подтверждается готовность release AAB к Google Play Production.
4. Какие зоны покрытия остаются непроверенными, частично проверенными или принципиально непокрываемыми автоматикой.

Без такого документа релиз standard рискует выйти в одном из двух плохих состояний:

- **Ложный PASS:** собран `standardRelease.aab`, но фактическая функциональная поверхность уже уже, чем ожидалось, из-за flavor isolation, release-only ключей, R8/resource shrink, Play policy или ручных операторских пропусков.
- **Ложный FAIL:** команда тратит время на расследование ожидаемых различий между `standard` и `noLegal` / `debug`, принимая продуктовые ограничения market build за баги.

---

## 2. Цели

1. Зафиксировать **единую release contract surface** для `standard` production build.
2. Разделить все различия на три класса: **intentional exclusion**, **release-only variance**, **true regression**.
3. Описать полный набор возможных потерь функциональности, охвата и операционной готовности, которые нужно проверять перед выкладкой в Production.
4. Определить **evidence ladder** для standard release: что доказывается статически, что сборкой, что internal track, что Play Console, что ручным устройством.
5. Задать release gate, который блокирует выкладку при отсутствии обязательных доказательств или при наличии неразрешённых waivers.

**Non-goals:**

- Эта спека не внедряет изменения в код или скрипты сама по себе.
- Эта спека не описывает VR production, noLegal sideload, lite/photos/legacy релизные процессы и Wear-only публикацию как primary target.
- Эта спека не заменяет тактические планы `/spec-tech` и `/spec-dev`; она определяет критерии и область работы.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Релизный gate должен быть практическим: не идеальная бесконечная матрица, а минимальный набор доказательств, который реально выполняется перед каждым production release.
2. Документ должен явно отличать **потерю market-incompatible функций** от **неожиданной потери standard surface**.
3. Спека должна быть пригодна как вход для будущего `/spec-tech` и автоматизации release bundle proof.

### 3.2 Жёсткие ограничения

- **Flavor scope:** только `standard`.
- **Distribution target:** Google Play production.
- **Artifact target:** signed `standardRelease` AAB.
- **Product baseline:** standard feature surface из `docs/FEATURES.md` плюс фактическая BuildConfig matrix для `standard`.
- **Platform baseline:** minSdk 26, targetSdk 35 (с обязательной валидацией ограничений Android 15 на фоновые службы и оптимизацию батареи).
- **Policy baseline:** Google Play policy, Data Safety, production signing, production OAuth registrations, store listing and pre-launch signals.
- **Localization baseline:** EN/RU/UK для user-visible release surface и store metadata.
- **Evidence rule:** "build succeeded" недостаточно; нужен набор продуктовых доказательств по зонам риска.

### 3.3 Owner inputs (Approval gate)

Решения владельца зафиксированы 2026-06-20 (gate Draft -> Approved):

- **Release blocker policy:** tiered. Жёсткий стоп - flavor-surface regressions (§5.2), release-only technical regressions (§5.3), operational/policy failures (§5.5, §6.3). Waiver-eligible - coverage losses (§5.4) и временные ограничения подсистем, но только с записанным waiver-note. Intentional exclusions (§5.1) не являются потерями.
- **Minimum device matrix:** один современный телефон (recent API). Owner осознанно принимает слабый proof по API/OEM-разнообразию; этот зазор фиксируется как явный coverage gap в матрице §8.3, а не выдаётся за полное покрытие.
- **Cast/Wear/cloud policy:** best-effort waiver. Cast и Wear (standard contract: `SUPPORT_CAST=true`, `SUPPORT_WEAR_COMPANION=true`) проверяются физически только когда изменение затрагивает соответствующую подсистему; иначе покрываются записанным waiver и не блокируют каждый phone release. Cloud при правках auth/callback подпадает под release-only auth proof (§5.3, §6.2).
- **Play Console gate strictness:** clean Pre-launch report обязателен; известные non-user-facing warnings (например emulator-only crashes, ad-SDK шум) допускаются только с записанным waiver-note.
- **Waiver storage:** per-release файл `store_assets/release_waivers/<versionName>.md` (versioned, рядом с `store_assets/PLAY_CONSOLE_CHECKLIST.md`). Один файл на релиз - список разрешённых отклонений с автором, датой, ссылкой на loss class и сроком пересмотра.
- **Related tickets:** S0484 (archived `/spec-prerelease` emulator spine), S0135 (Play Console operator checklist).

---

## 4. Целевая поверхность standard release

`standard` production build должен сохранять весь market-совместимый пользовательский surface, который заявлен для Standard в `docs/FEATURES.md` и BuildConfig matrix.

В этот baseline входят:

- Local + SMB + FTP + SFTP + cloud resources.
- Browse / filter / sort / copy / move / rename / delete / undo flows.
- Video / audio / image / PDF / EPUB / text playback/editing.
- OCR / translation / downloadable extensions, если они входят в standard contract и разрешены market-distribution path.
- Chromecast support.
- Persistent audio playback and notification flows.
- Quick widgets and default-player integration.
- Usage statistics, settings search, backup/restore, cloud auth, send-to surface.
- Wear companion support как часть standard contract.

В `standard` **не входят** следующие surface-ы, и их отсутствие в standard не считается регрессией само по себе:

- `noLegal`-only capabilities, исключённые из market builds по policy / licensing / heavy-runtime причинам.
- VR immersive player surface.
- noLegal screen-gesture screenshot overlay and adjacent sideload-only workflows.
- APK install from browse, heavy diagnostics/fingerprinting surface и другие store-incompatible seams.
- owner-only debug tooling, test import helpers, integration-test UI, debug package identity и debug-only credentials.

---

## 5. Таксономия потерь, которую обязан покрыть релизный gate

### 5.1 Intentional exclusions

Это различия, которые standard production **обязан** иметь по дизайну. Они документируются и не считаются багами:

- Отсутствие `noLegal`-only feature delta.
- Отсутствие VR-only immersive surface.
- Отсутствие debug-only controls, dialogs, test harnesses, owner-trigger shortcuts и debug package identity.
- Отсутствие sideload-only permissions / flows, которые Google Play не допускает.

### 5.2 Flavor-surface regressions

Это потери внутри самого standard contract. Любая такая потеря - release blocker до явного waiver:

- Feature flag отключился в `standard`, хотя документирован как standard-supported.
- UI entrypoint остался в `docs/FEATURES.md`, но в standard скрыт или неработоспособен.
- Flavor isolation случайно унесла shared implementation из standard.
- Market-safe capability переехала в `noLegal` или другой source set без продуктового решения.

### 5.3 Release-only technical regressions

Это потери, возникающие только на `release` по сравнению со `standardDebug`:

- R8 / resource shrink ломает reflection, serialization, OAuth callback, JS bridge, optional modules, menu/icon/string reachability (требуется автоматическая проверка запуска обфусцированной release-сборки).
- Специфичные для targetSdk 35 изменения поведения (ограничения фоновой работы, энергопотребление, новые типы foreground-сервисов и фото-разрешения).
- Release signing и production OAuth registrations не совпадают с debug registrations.
- Release-only manifest / package identity / redirect URI / keystore fingerprint ломают sign-in, deep links, AppAuth/MSAL/Dropbox callbacks.
- Production network security / cleartext / trust anchor policy меняет поведение runtime.
- Release logging / diagnostics path становится либо слишком немым для расследования, либо слишком шумным/опасным для privacy (требуется GDPR-compliant диагностика с opt-in).

### 5.4 Coverage losses

Это не обязательно баги продукта, но это зоны, где proof of readiness ослабляется:

- Эмуляторный spine не покрывает значительную часть реального user surface.
- Single-device pass не доказывает API diversity, OEM behavior, D-pad/TV, notification policy, PiP, Cast, network heterogeneity.
- Debug-only smoke не доказывает release artifact.
- Manual pass без operator evidence не доказывает Play-side readiness.
- Play Console pass без real-device product pass не доказывает actual UX.

### 5.5 Operational losses

Это не потери пользовательской функции, а потери готовности релиза как поставляемого продукта:

- Неверный signing key / upload key / pinned fingerprint.
- Незаполненный Data Safety / Privacy / store listing drift.
- Internal Testing не пройден.
- Pre-launch report не просмотрен.
- Mapping / symbols / crash-reporting path не подготовлены.
- Production release process зависит от локального ad-hoc знания и не оставляет evidence trail.

---

## 6. Основные risk buckets для standard production

### 6.1 Flavor boundary drift

Риск: standard surface незаметно сужается из-за жесткой изоляции source sets, когда market-safe код случайно уходит из `src/main` или `src/standard`, а проверка ведётся на другом флейворе.

Нужно доказательство:

- Явная сверка standard feature baseline против `docs/FEATURES.md` и BuildConfig matrix.
- Явная матрица "supported / intentionally absent / blocked by waiver".

### 6.2 Release-vs-debug drift

Риск: `standardDebug` зелёный, а `standardRelease` теряет часть surface из-за shrink/signing/manifest differences.

Нужно доказательство:

- Release-variant build proof.
- Release-variant spot checks на критических seams.
- OAuth and deep-link proofs на release identity.

### 6.3 Play-policy drift

Риск: стандартная market build технически работает, но фактически непроходима через Production из-за policy/Data Safety/permissions/listing mismatch.

Нужно доказательство:

- Store operator checklist complete.
- Privacy Policy link live.
- Data Safety reviewed.
- Pre-launch report reviewed.
- Permission surface audited against market claim.

### 6.4 Coverage illusion

Риск: `/spec-prerelease` и fast checks дают чувство готовности, хотя они не покрывают большую часть реальной release surface.

Нужно доказательство:

- Отдельная coverage matrix.
- Явное разделение `scripted`, `manual`, `release-only`, `Play-only`, `untested`.

### 6.5 Production diagnostics gap

Риск: после релиза crash/incident нельзя быстро деобфусцировать и triage'ить.

Нужно доказательство:

- Mapping/symbol policy закреплена.
- Crash-reporting path или заменяющий diagnostic path зафиксирован.
- Known privacy-sensitive logs audited for standard production.

---

## 7. Что именно считается "учтёнными потерями"

Для этой спеки "учесть все возможные потери" означает не бесконечно перечислить каждый экран, а гарантировать, что **каждый класс потери имеет owner, доказательство и статус**.

Обязательные loss classes:

- Потеря feature surface относительно `standard` baseline.
- Потеря доступности feature entrypoint при сохранённом backend capability.
- Потеря release-only auth / callback / signing functionality.
- Потеря locale/store metadata parity.
- Потеря device/form-factor coverage.
- Потеря Play-operator readiness.
- Потеря observability / deobfuscation / post-release triage capability.
- Потеря expected intentional exclusions inventory, из-за чего команда начинает чинить не-баги.

---

## 8. Предлагаемый подход

Следующий тактический план должен разложить работу на пять потоков.

### 8.1 Surface inventory

- Зафиксировать canonical standard surface.
- Зафиксировать canonical intentional exclusions relative to `noLegal`, `vr`, `debug`.
- Зафиксировать ambiguous areas, где surface в коде и docs расходятся.

### 8.2 Release-risk audit

- Сверить `standardDebug` vs `standardRelease`.
- Интегрировать автопроверку запуска release-сборки на базовых сценариях (smoke-тест для детекции R8/shrink поломок).
- Проверить signing / OAuth / manifest / network-security / shrink-sensitive seams.
- Проверить ограничения targetSdk 35 (фоновые службы, разрешения, лимиты Android 15).
- Проверить policy-sensitive permission and Data Safety surface.

### 8.3 Coverage matrix

- Разнести проверки по уровням: static, fast build, release build, emulator spine, manual real-device, Play Console.
- Для каждой крупной capability-группы указать `covered`, `partially covered`, `not covered`, `intentionally excluded`.

### 8.4 Operator evidence pack

- Описать, какие артефакты должны остаться после подготовки релиза.
- Определить места хранения screenshots, report links, verdict files, waiver notes.

### 8.5 Release verdict contract

- Определить единый PASS/FAIL/WAIVED verdict для standard production release.
- Зафиксировать, кто имеет право открыть waiver и что является достаточной записью waiver.

---

## 9. Открытые вопросы / Research items

1. **Standard baseline source of truth**
   - Решение: канонический baseline = срез `docs/ALL_FEATURES.jsonl` по `flavors ∋ standard` + `status=active`, сверенный с BuildConfig standard matrix. `docs/FEATURES.md` - витрина, не источник истины для gate.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0553_standard-production-release-readiness/research/01__standard-baseline-source-of-truth.md`

2. **Minimum production device matrix**
   - Решение: один современный телефон (recent API). Зазор по API/OEM-разнообразию фиксируется как явный coverage gap §8.3. См. §3.3.
   - **Статус:** Resolved (owner decision §3.3)

3. **Wear companion blocking policy**
   - Решение: best-effort waiver. Wear проверяется только при изменениях в подсистеме; иначе записанный waiver, не per-release блокер. См. §3.3.
   - **Статус:** Resolved (owner decision §3.3)

4. **Cast blocking policy**
   - Решение: best-effort waiver. Физический Cast proof только при изменениях в подсистеме Cast; иначе записанный waiver. См. §3.3.
   - **Статус:** Resolved (owner decision §3.3)

5. **Diagnostics policy**
   - Решение: baseline = существующий in-app crash/log export path; внешний crash sink вне объёма gate. Gate требует retention `mapping.txt` + native symbols по `versionCode`.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0553_standard-production-release-readiness/research/02__diagnostics-and-mapping-policy.md`

6. **Release logging privacy line**
   - Решение: зафиксирован запрещённый/допустимый набор категорий release-логов; нарушение = operational loss до waiver.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0553_standard-production-release-readiness/research/03__release-logging-privacy-line.md`

---

## 10. Риски

- Команда будет продолжать смешивать `standard release readiness` с `noLegal capability surface`, если intentional exclusions не выделены отдельным блоком.
- Тактическая автоматизация может снова переоценить эмуляторный smoke как полноценный release proof.
- Без release-vs-debug drift-аудита можно получить Production-only OAuth/callback failures уже после выкладки.
- Без явного waiver contract любой спорный пункт будет закрываться устным решением и терять трассируемость.

---

## 11. Связи с существующими артефактами

- `S0484` archived `/spec-prerelease` - источник автоматизированного emulator spine, но не заменяет эту спеку.
- `dev/PRE_RELEASE_MANUAL_TESTS.md` - источник intent для ручного продукта.
- `store_assets/PLAY_CONSOLE_CHECKLIST.md` - источник Play operator checklist.
- `docs/FEATURES.md` - curated standard surface baseline.
- `docs/FEATURES_noLegal*.md` - источник intentional exclusions relative to market builds.

---

## 12. Критерии готовности strategic-level

1. Для standard production release определён один canonical baseline surface.
2. Все различия standard vs noLegal/debug/vr/wear классифицированы как `intentional exclusion`, `release variance` или `regression`.
3. Для каждой крупной capability-группы есть coverage status и required evidence level.
4. Для каждого release-risk bucket определён blocking verdict.
5. Internal Testing, Pre-launch, Data Safety, Privacy, signing, release artifact и diagnostics включены в единый gate, а не живут отдельными несвязанными заметками.
6. Следующий шаг `/spec-tech S0553` можно выполнить без повторного переосмысления области работы.

---

## 13. Архитектурные решения (ADR)

**ADR-1: Standard release gate строится от standard baseline, а не от noLegal superset**

- **Решение:** canonical target - это `standard` contract, а не "всё, что умеет repo".
- **Почему:** иначе каждая market-несовместимая потеря будет выглядеть как ложная регрессия.

**ADR-2: Coverage gaps документируются как first-class verdict, а не скрываются за PASS smoke**

- **Решение:** `untested` и `partially covered` - допустимые, но явные состояния до owner waiver.
- **Почему:** молчаливое преобразование непокрытых зон в PASS создаёт ложную уверенность.

**ADR-3: Release readiness включает operator and policy readiness наравне с runtime functionality**

- **Решение:** Play Console / Data Safety / signing / mapping считаются частью release contract.
- **Почему:** production release проваливается не только кодом, но и операционной неподготовленностью.

---

## 14. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0553` - создать tactical plan по surface inventory, release-risk audit, coverage matrix, operator evidence pack и verdict contract.

---

## Last Audit

**Date:** 2026-06-20
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 26 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 1

All five phases Done and verified against the repo. Deliverables exist and run: `standard-surface-snapshot.ps1` (`-Json` exit 0 / `-CheckRegressions` exit 0, 329 standard capabilities, no flag regressions), `standard-release-smoke.ps1` (`-CheckSeams` exit 0 / no-device `-Json` exit 2 graceful infra abort), `standard-release-gate.ps1` (FAIL exit 1 without waiver, WAIVED exit 3 with waiver). Gate doc `docs/RELEASE_READINESS_STANDARD.md` complete (all six sections, zero placeholders); coverage manifest parses; waiver pack present; cross-linked from the release how-to, ops index, and Play checklist. Debug-tag invariant clean (0 `Timber.d("S0553:` tags; status Implemented). No ticket id in persistent logs. Dev log entries present for every deliverable. FEATURES EXEMPT (internal release tooling; strategic §2 non-goal: no code/FEATURES change by the gate itself). The single-device matrix is a recorded coverage gap (owner §3.3), not a FAIL.

### Manual / on-device

- [ ] Run `scripts/release/standard-release-gate.ps1 -VersionName <v>` on an actual release cycle (release keystore + device) and confirm PASS or a recorded WAIVED.
- [ ] Run `scripts/release/standard-release-smoke.ps1 -Build` once on a device to exercise the on-device R8/shrink launch path on the minified artifact.
