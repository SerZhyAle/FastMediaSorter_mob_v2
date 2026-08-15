# Спецификация (compact bugfix): S1584 - карточка ресурса обещает 45 файлов, browse показывает пустую папку

**Ticket:** S1584
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-11

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-11

**Захвачено во время:** device-дренаж тикетов S1579 / S1569 / S1581 на реальном устройстве RFCR110NBQJ
(Samsung Galaxy S21+, SM-G996U1, Android 15 / SDK 35), standard debug `2.60.8111.809-DEBUG`. К проверявшимся
тикетам отношения не имеет - запарковано без переключения активной задачи.

**Текст:**

Карточка ресурса «Camera Photos» на главном экране показывает `45 files`. Открытие той же карточки приводит
в `BrowseActivity`, где список пуст и написано «Nothing here. We checked twice.». При следующем открытии того
же ресурса приложение уходит сразу в `PlayerActivity`, минуя список.

То есть три обращения к одному виртуальному ресурсу подряд дали три разных ответа: счётчик на карточке,
пустой список и переход в плеер.

**Что известно без расследования.** Ресурс виртуальный (`virtual://`, MediaStore-подложка), а не папка на
диске. Счётчик на карточке и содержимое списка берутся из разных мест - иначе они не могли бы разойтись, -
но какие именно это места и какое из двух значений верно, из наблюдения не следует.

**Почему это не мелочь.** Формулировка «We checked twice» - это обещание, что пустота проверена, поэтому
пользователь ей верит и не открывает папку второй раз. Если верен счётчик, то приложение утверждает, что
проверило дважды, ровно там, где не нашло 45 файлов.

**Дедуп.** По симптому «счётчик расходится со списком» отдельного тикета в каталоге не нашлось.

**Захвачено во время:** device-дренаж `/spec-do` (раунд 6)

---

## 1. Проблема / симптом

Счётчик на карточке ресурса и список файлов в `BrowseActivity` считают один и тот же ресурс двумя
независимыми наборами фильтров. Наборы никогда не сверяются между собой, и фильтр счётчика по построению
слабее фильтра списка - поэтому счётчик систематически завышен, а расхождение ничем не сигнализируется.

Наблюдаемое следствие: карточка обещает 45 файлов, список показывает ноль и утверждает, что проверил дважды.

Третий симптом из §0 - «следующее открытие уходит сразу в `PlayerActivity`» - к этому расхождению отношения
не имеет. Это штатный resume-on-launch: `MainResumePlaybackHelper.attemptResumePlayback()` срабатывает на
холодном старте по `Intent.ACTION_MAIN` из одного глобального слота `ResumeState`
(`ResumeStateRepository.WINDOW_ID_MAIN`), а не из состояния конкретной карточки. Он открывает плеер тем же
`GetMediaFilesUseCase`, то есть попадает в тот же пустой список, просто другим маршрутом. Отдельного дефекта
здесь нет, и в объём тикета этот пункт не входит.

---

## 2. Корневая причина

Одна величина - «какие файлы этого ресурса видит приложение» - выводится в двух местах, независимо.

**Счётчик карточки.** `ResourceScanCoordinator.getFileCount()`
(`app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt:280-309`):

- типы: `currentSettings.getGloballyEnabledMediaTypes()` - только глобальные настройки, без пересечения с
  `resource.supportedMediaTypes` и без flavor-гейта;
- размер: `imageSizeMax` / `videoSizeMax` / `audioSizeMax` жёстко выставлены в `Long.MAX_VALUE`
  (строки 286, 288, 290), то есть пользовательские потолки размера игнорируются.

**Список browse.** `BrowseResourceLoadManager` (`ui/browse/managers/BrowseResourceLoadManager.kt:333-347`)
плюс `GetMediaFilesUseCase` (`domain/usecase/GetMediaFilesUseCase.kt:235`):

- типы: `resource.supportedMediaTypes.intersect(settings.getGloballyEnabledMediaTypes())` (или все типы при
  `resource.allFiles`), затем ещё `applyFlavorMediaTypeRestrictions()`;
- размер: реальные `settings.imageSizeMax` / `videoSizeMax` / `audioSizeMax`.

Набор счётчика - надмножество набора списка по обеим осям сразу. Значит, счётчик никогда не может оказаться
меньше списка, а любой файл, отсечённый в browse, остаётся посчитанным на карточке. Расхождение не
исключение, а инвариант кода.

Два конкретных механизма, каждый из которых в одиночку даёт наблюдённые «45 против 0»:

1. **Потолок размера.** `imageSizeMax` по умолчанию 10 МБ (`domain/model/AppSettings.kt:71`). Оригиналы с
   камеры Galaxy S21+ этот потолок превышают. Счётчик их считает (`Long.MAX_VALUE`), browse их отсекает.
2. **Просроченный снимок типов.** `resource.supportedMediaTypes` у виртуального ресурса заморожен в момент
   провижининга (`domain/usecase/ProvisionDefaultResourcesUseCase.kt:99-110`) из тогдашних настроек. Если
   пользователь позже изменил набор поддерживаемых типов, пересечение в browse может опустеть, тогда как
   счётчик продолжает считать по живым настройкам.

Какой из двух механизмов сработал на устройстве, из наблюдения §0 не следует, и для исправления это не важно:
оба - следствия одной причины (две независимые деривации), и оба закрываются одним общим выводом фильтра.

**Что здесь верно, а что нет.** Авторитетен список: пользователь получает именно его, и потолки размера -
осознанная пользовательская настройка. Значит, счётчик обязан принять фильтр списка, а не наоборот.

Но одного выравнивания мало. После него карточка честно покажет `0 files`, а 45 реальных снимков останутся
невидимыми, и приложение по-прежнему не скажет почему. Поэтому исправление обязано ещё и объяснять пустоту,
когда она вызвана фильтром, - иначе тикет закроется «согласованной неправдой» вместо правды.

---

## 3. Исправление

Один вывод фильтра на оба места вызова плюс честное пустое состояние.

- **Единственный источник.** Новый `ResolveScanFilterUseCase` возвращает пару «эффективные типы + `SizeFilter`»
  по `MediaResource` и `AppSettings`. Логика берётся из browse как из авторитетной ветки: `allFiles` -> все
  типы, иначе пересечение `resource.supportedMediaTypes` с глобально включёнными, затем flavor-гейт; размеры -
  реальные min/max из настроек.
- **Оба вызова переводятся на него.** `ResourceScanCoordinator.getFileCount()` перестаёт собирать свой набор и
  перестаёт подставлять `Long.MAX_VALUE`. `BrowseResourceLoadManager` теряет свою inline-копию. После этого
  разойтись физически нечему.
- **Пустое состояние перестаёт врать.** Когда список пуст, browse доводит тот же скан с снятым потолком
  размера; если непустой результат существует, вместо «Nothing here. We checked twice.» показывается
  сообщение о том, что файлы скрыты текущими фильтрами, с их количеством. Второй скан выполняется только на
  ветке пустого списка, поэтому штатный путь не дорожает.

Дефолт `imageSizeMax = 10 МБ` тикет не меняет - это отдельное решение владельца, вынесено в §3.3.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none - дедуп по симптому ничего не дал.
- **UI placement:** сообщение о скрытых фильтром файлах занимает уже существующий `tvEmptyStateMessage` в
  `BrowseActivity` - новых элементов и новых экранов не добавляется, лендскейп-вариант не затрагивается.
- **UI fallback:** если добор без потолка размера вернул ноль или упал, показывается прежняя строка
  `no_files_found` - поведение не хуже текущего.
- **Deferred to owner:** поднимать ли дефолт `imageSizeMax` с 10 МБ (сейчас он молча прячет оригиналы с
  камеры) и вести ли из пустого состояния прямой переход в настройки размера. Тикет работает при любом ответе.

---

## 4. Проверка

- `.\a.ps1 fk` - standard-компиляция проходит.
- Юнит-тест на `ResolveScanFilterUseCase`: для одного `MediaResource` и одних `AppSettings` типы и `SizeFilter`
  совпадают с тем, что раньше строил `BrowseResourceLoadManager`; при `imageSizeMax = 10 МБ` возвращается
  именно он, а не `Long.MAX_VALUE`.
- Юнит-тест на инвариант: счётчик и список, получив один ресурс и одни настройки, запрашивают один набор типов
  и один `SizeFilter`.
- Устройство: ресурс с файлами крупнее `imageSizeMax` - число на карточке равно длине списка в browse; при
  пустом списке показывается сообщение о скрытых файлах, а не «We checked twice».

---

## 5. Фазы

### Phase 1 - Single scan-filter derivation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveScanFilterUseCase.kt` (new),
`app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolveScanFilterUseCaseTest.kt` (new)

#### Step 1.1 - Add `ResolveScanFilterUseCase`

**Files:** `domain/usecase/ResolveScanFilterUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ResolveScanFilterUseCase` with an `operator fun invoke(resource: MediaResource, settings: AppSettings): ScanFilter`, where `ScanFilter` is a data class holding `mediaTypes: Set<MediaType>` and `sizeFilter: SizeFilter`. Derive `mediaTypes` exactly as `BrowseResourceLoadManager` does today: all `MediaType.entries` when `resource.allFiles`, otherwise `resource.supportedMediaTypes.intersect(settings.getGloballyEnabledMediaTypes())`; then apply the flavor gate currently implemented as `GetMediaFilesUseCase.applyFlavorMediaTypeRestrictions()`. Build `sizeFilter` from the real `imageSizeMin`/`imageSizeMax`/`videoSizeMin`/`videoSizeMax`/`audioSizeMin`/`audioSizeMax` settings. Inject `MediaCapabilities` for the flavor gate. Declare `ScanFilter` in the same file.

**Why:**

The counter and the browse list each derive this filter independently today, and the counter's derivation is a superset on both the type axis and the size axis, so the card can never report fewer files than the list shows - the divergence described in §2 is structural, not accidental. A single derivation is what removes it.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveScanFilterUseCase.kt` exists.
- `Grep` - `class ResolveScanFilterUseCase` matches exactly once in that file.
- `Grep` - `data class ScanFilter` present in that file.
- `Grep` - `imageSizeMax = settings.imageSizeMax` present in that file, proving the user's ceiling is
  carried rather than replaced by `Long.MAX_VALUE`. (`Long.MAX_VALUE` does occur in the file, in
  `withoutSizeCeiling`, which Phase 3 needs - the original "zero hits" predicate was wrong.)

**Status:** `[x]` done

---

#### Step 1.2 - Unit-test the derivation

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolveScanFilterUseCaseTest.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Add unit tests covering: `allFiles = true` yields every `MediaType`; `allFiles = false` yields the intersection of `resource.supportedMediaTypes` with the globally enabled types; a resource whose snapshot is disjoint from the global settings yields an empty type set; and `sizeFilter.imageSizeMax` equals `settings.imageSizeMax` rather than `Long.MAX_VALUE`.

**Why:**

§2 names two independent mechanisms that each reproduce the reported "45 versus 0" - the size ceiling and the frozen type snapshot - and a test that pins both is what stops either from silently returning after the fix.

**Verification:**

- `Glob` - the test file exists.
- `Grep` - `imageSizeMax` present in the test file.
- `.\a.ps1 fu` - `ResolveScanFilterUseCaseTest` passes.

**Status:** `[x]` done - 6 tests, 0 failures (`TEST-..ResolveScanFilterUseCaseTest.xml`, 2026-08-12 01:35)

---

### Phase 2 - Route both call sites through it

**Files:** `ui/main/helpers/ResourceScanCoordinator.kt`, `ui/browse/managers/BrowseResourceLoadManager.kt`

#### Step 2.1 - Counter adopts the shared filter

**Files:** `ui/main/helpers/ResourceScanCoordinator.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `ResourceScanCoordinator.getFileCount()` replace the locally built `supportedTypes` and `sizeFilter` with a single `ResolveScanFilterUseCase` call, and pass its `mediaTypes` and `sizeFilter` to `scanner.getFileCount()`. Inject the use case through the constructor. Remove the three `Long.MAX_VALUE` assignments.

**Why:**

The card counter is the surface that made the false promise of 45 files, and it stays wrong for as long as it counts with a weaker filter than the list the user is sent to.

**Verification:**

- `Grep` - `Long.MAX_VALUE` returns zero hits in `ResourceScanCoordinator.kt`.
- `Grep` - `resolveScanFilter` present in `ResourceScanCoordinator.kt`.
- `Grep` - `getGloballyEnabledMediaTypes` returns zero hits in `ResourceScanCoordinator.kt`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done - `MainViewModel` also builds this coordinator by hand, so its construction site
and the `ResolveScanFilterUseCase` injection were updated together.

---

#### Step 2.2 - Browse drops its inline copy

**Files:** `ui/browse/managers/BrowseResourceLoadManager.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Replace the inline `sizeFilter` construction and the `effectiveMediaTypes` block in `BrowseResourceLoadManager` with a `ResolveScanFilterUseCase` call, keeping the existing `resource.copy(supportedMediaTypes = ..)` hand-off to `loadMediaFilesStandard` so the downstream scan contract is unchanged.

**Why:**

Leaving browse on its own copy of the derivation would let the two sides drift apart again on the next edit, which is exactly the failure mode §2 identifies as structural.

**Verification:**

- `Grep` - `resolveScanFilter` present in `BrowseResourceLoadManager.kt`.
- `Grep` - `getGloballyEnabledMediaTypes` returns zero hits in `BrowseResourceLoadManager.kt`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done - the manager is hand-constructed, so `BrowseContentDiscoveryDependencies` gained
the use case and `BrowseViewModel` passes it through.

---

### Phase 3 - Honest empty state

**Files:** `ui/browse/managers/BrowseResourceLoadManager.kt`, `ui/browse/managers/BrowseObserverManager.kt`,
`app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`)

#### Step 3.1 - Detect filter-suppressed emptiness

**Files:** `ui/browse/managers/BrowseResourceLoadManager.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> When a completed scan yields an empty list, re-run the same scan once with the size ceilings raised to `Long.MAX_VALUE` and the same type set. Publish the resulting count into the browse UI state as a `filteredOutCount`, defaulting to zero. Run this probe only on the empty-list branch, and fall back to zero on any failure.

**Why:**

After Phase 2 the card and the list agree at zero, which is consistent but still hides the 45 real files that exist - §2 records that shipping the alignment without an explanation would replace a visible contradiction with a silent one.

**Verification:**

- `Grep` - `filteredOutCount` present in `BrowseResourceLoadManager.kt`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done - the probe uses `scanner.getFileCount` rather than a second full scan, so the
empty-list branch costs a count and the populated branch costs nothing.

---

#### Step 3.2 - Say why the folder looks empty

**Files:** `ui/browse/managers/BrowseObserverManager.kt`, `app_v2/src/main/res/values/strings.xml`
**Depends on:** Step 3.1

**Prompt for developer:**

> Add string key `browse_empty_filtered_out` via `scripts/utils/set-android-string.ps1 -Action add` with EN/RU/UK values telling the user that N files are hidden by the current size and type filters. In `BrowseObserverManager.renderEmptyState()`, show that string when `filteredOutCount > 0` and keep `no_files_found` otherwise.

**Why:**

§0 records that "We checked twice" is read as a promise the emptiness was verified, so the user does not look again - which makes the wording actively harmful precisely where a filter, not an empty folder, produced the result.

**Verification:**

- `Grep` - `browse_empty_filtered_out` present in `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- `Grep` - `browse_empty_filtered_out` present in `BrowseObserverManager.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "browse_empty"` - exit 0.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done - two keys added (`browse_empty_filtered_out` + `_hint`). The message names the
size ceiling specifically, because the probe lifts only that - claiming "type filter" would be a guess.

---

## 6. Last Audit

**Date:** 2026-08-12 - phase-boundary audit of the implemented change (CLAUDE.md §13).

**Findings fixed in this pass:**

- **P1 - the probe would blame the size filter for a scan the user cancelled.** A scan stopped with the
  STOP button also ends with an empty list and no exception, so `reportFilterSuppressedFiles` would run
  and report hidden files that were simply never enumerated. That is a second false explanation on top
  of the one this ticket removes. Guarded on `shouldStopScanRef`.
- **P2 - redundant state emission.** The probe wrote `filteredOutCount = 0` on every populated load;
  now it writes only when the value actually changes.

**Checked, no defect found:**

- A scan that throws twice propagates out of the `try`, so the probe never runs on a failed scan.
- `CancellationException` is rethrown rather than swallowed, in both the load path and the probe.
- The probe uses `getFileCount`, not a second `scanFolder`, and only on the empty-list branch, so a
  populated folder costs nothing extra.
- Hilt graph validated by `hiltJavaCompileStandardDebug` during `.\a.ps1 fu` - `fk` alone would not have
  caught a missing binding for the new use case.

**Device evidence (RFCR110NBQJ, Galaxy S21+, Android 15, standard debug `2.60.8112.319-DEBUG`):**

- **Исходный симптом не воспроизводится.** Открытие карточки «Camera Photos» приводит в browse с 52
  файлами и сеткой миниатюр. Пустого списка и строки «Nothing here. We checked twice.» больше нет -
  именно то сочетание, ради которого заведён тикет.
- Тег подтверждает разрешённый фильтр: `S1584: 'Camera Photos' types=[IMAGE, VIDEO, GIF] max=10485760`.
- **Механизм 2 из §2 (просроченный снимок типов) на этом устройстве не срабатывал** - снимок содержит
  IMAGE/VIDEO/GIF, то есть пересечение непустое. Значит, наблюдённые «45 против 0» давал механизм 1,
  потолок размера в 10 МБ. Это подтверждает выбор ремонта, но не отменяет второго механизма как причины.
- **Инвариант тикета подтверждён.** Карточка «Camera Photos» показывает 52, browse показывает 52, и это
  значение не меняется после полного явного рескана всех ресурсов. Промежуточные «46 на карточке против
  52 в списке» были до-фиксовым кэшем: `fileCount` - значение в Room, переписываемое при открытии
  ресурса в browse и при явном refresh. То же поведение независимо наблюдалось на «All Music» (0 -> 7
  после открытия) и на рескане («All Videos» 0 -> 9, «All Files» 179 -> 181).
- Все девять ресурсов после рескана разрешают фильтр через один use case - по одной строке лога на
  ресурс, с осмысленным набором типов у каждого (`All Music` -> `[AUDIO]`, `All Images` ->
  `[IMAGE, GIF]`, `All Documents` -> `[TEXT, PDF, EPUB, OFFICE_DOCUMENT]`).
- FATAL / крэшей нет ни в одном из окон захвата.
- **Честное пустое состояние проверено принудительно.** Потолок размера изображений временно снижен через
  настройки с 10240 КБ до 1 КБ. Browse «Camera Photos» опустел и показал ровно: «52 files are hidden by
  the maximum file size setting.» с подсказкой «Raise the maximum file size in Settings to see them.» -
  вместо «Nothing here. We checked twice.». Лог: `S1584: browse list empty for 'Camera Photos', size
  filter hid 52 file(s)`. Настройка возвращена в 10240 КБ и восстановление подтверждено дампом UI.

**Чего доказательство не покрывает (сказано прямо):** исходное сочетание «45 на карточке против пустого
списка» до фикса на этом устройстве в этой сессии не воспроизводилось - при потолке 10 МБ все 52 снимка
проходят фильтр. Значит, ремонт подтверждён инвариантом (счётчик и список сошлись, пустота объясняет себя),
а не сравнением «до и после» на самом отказе. Какой из двух механизмов §2 дал наблюдённые «45 против 0» в
августе, доказательно не установлено; оба закрыты одним ремонтом.

**Open (deliberately not addressed here):**

- `PlayerMediaFilesLoader`'s early "resource unavailable" guard still reads the cached `resource.fileCount`
  and is bypassed whenever `skipAvailabilityCheck = true`. Now consistent rather than inflated, so it is
  no longer a source of this symptom, but it remains a stale-cache read. Not this ticket's scope.
- The `resource.supportedMediaTypes` snapshot is still frozen at provisioning time. This ticket makes the
  two consumers agree about the snapshot; it does not re-sync the snapshot when settings change.

---
