# Спецификация (compact): S0973 - noLegal: «человеческая» пауза между элементами карусели при закачке

**Ticket:** S0973
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-10
**Tier:** 2 - Easy (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-10 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-07

**Текст (владелец):**

noLegal закачка контента из соцсетец. в случае если по ссылке нескольео объектов (например карусель) делать дополннительно между элементами паузу от половины секунд до двух . абсолютно рандомную в этих пределах. чтобы для соцсети это выглядело как живой трафик

---

## 1. Цель

При многообъектной закачке (карусель/альбом, >1 элемента) вставлять равномерно-случайную паузу 0.5..2.0 с **между** элементами, чтобы трафик выглядел «живым». Только noLegal; одиночная ссылка - без паузы; пауза прерываема.

---

## 2. Подход

Batch-цикл живёт в `LinkAutoDownloadCoordinator.runBatch` (`batch.items.forEachIndexed`, src/main). Пейсинг - noLegal-специфика, поэтому без `BuildConfig`-гварда в main (Rule 14): зеркалим существующий `@IntoSet`-паттерн link-download DI.

- Интерфейс `LinkDownloadPacer { suspend fun pauseBetweenItems() }` в `domain/usecase/link` (main).
- Main DI (`LinkDownloadStrategiesModule`): `@Multibinds` объявляет возможно-пустой `Set<LinkDownloadPacer>` -> в standard/lite/photos/legacy набор пуст (пауза не добавляется).
- noLegal: `HumanizedCarouselPacer` (`delay(Random.nextLong(500, 2001))`), `@Binds @IntoSet` в `NoLegalLinkDownloadModule`.
- Coordinator: инжектит `Set<@JvmSuppressWildcards LinkDownloadPacer>`; в `runBatch` при `index > 0` (после отправки прогресса следующего элемента, чтобы UI показывал «элемент N», а не зависание) вызывает `pacers.firstOrNull()?.pauseBetweenItems()`. `delay` отменяем - cancel загрузки прерывает паузу немедленно.

Без новых строк/ProgressState: прогресс уже переключается на следующий элемент до паузы (constraint «не как зависание»).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none (дедуп «carousel/delay/humanized» пуст).

---

## Фазы

### Фаза 01 - интерфейс + main multibinds

- Новый `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkDownloadPacer.kt`.
- `LinkDownloadModule.kt` -> в `LinkDownloadStrategiesModule` добавить `@Multibinds abstract fun linkDownloadPacers(): Set<LinkDownloadPacer>`.
- Verification: standard компилируется (`fk`).

### Фаза 02 - coordinator интеграция

- `LinkAutoDownloadCoordinator`: конструкторный параметр `pacers: Set<@JvmSuppressWildcards LinkDownloadPacer>`; в `runBatch` вставить `if (index > 0) pacers.firstOrNull()?.pauseBetweenItems()` после emit прогресса элемента.
- Обновить `LinkAutoDownloadCoordinatorTest` (прямая конструкция) - `pacers = emptySet()`.
- Verification: `fk` + `testStandardDebugUnitTest --tests *LinkAutoDownloadCoordinatorTest*` зелёные.

### Фаза 03 - noLegal impl + bind + unit-тест

- Новый `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/HumanizedCarouselPacer.kt` (диапазон в companion const, `kotlin.random.Random`).
- `NoLegalLinkDownloadModule.kt` -> `@Binds @IntoSet abstract fun bindHumanizedPacer(impl: HumanizedCarouselPacer): LinkDownloadPacer`.
- Новый `app_v2/src/testNoLegal/java/.../HumanizedCarouselPacerTest.kt`: `runTest` с виртуальным временем - elapsed в [500,2000] мс.
- Verification: `fkn` компилируется; noLegal unit-тест зелёный.

---

## 4. Проверка

Статическая + unit: standard (`fk`) и noLegal (`fkn`) компилируются; `HumanizedCarouselPacerTest` подтверждает диапазон паузы; координатор-тест зелёный после конструкторного апдейта. Placement паузы между элементами - код-ревью (guard `index > 0`, отменяемый `delay`). Реальный «живой» трафик соцсети (anti-detection) не верифицируется автоматически - внешняя среда.

---

## Last Audit

**Date:** 2026-07-10
**Outcome:** Verified
**Method:** static + unit (real social-carousel end-to-end is external: noLegal build + network + account/content - not emulator-reproducible; anti-detection efficacy inherently unverifiable).

- Flavor isolation (Rule 14): pause is noLegal-only via `@Multibinds` empty `Set<LinkDownloadPacer>` (main) + `@Binds @IntoSet HumanizedCarouselPacer` (noLegal). No `BuildConfig` guard in `src/main`. Both Dagger graphs validated by the unit-test app compiles (standard empty set; noLegal one pacer).
- `:app_v2:testStandardDebugUnitTest --tests *LinkAutoDownloadCoordinatorTest*` -> BUILD SUCCESSFUL (constructor `pacers = emptySet()`). expected PASS | actual PASS.
- `:app_v2:testNoLegalDebugUnitTest --tests *HumanizedCarouselPacerTest*` -> BUILD SUCCESSFUL; pause sampled 200x, always in [500,2000] ms. expected PASS | actual PASS.
- Placement (code review): `runBatch` pauses only when `index > 0` (single link = no pause, criterion 2); `delay` is cancellable so download-cancel interrupts the wait (criterion 3); next-item progress emitted before the pause (no visible hang).

No action items.
