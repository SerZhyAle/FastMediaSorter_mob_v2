# S0400 — Тактическая спецификация: страница функциональности с загрузками

**Status:** Tactical
**Strategic:** `PLAN/S0400_welcome-functionality-page.md`
**Research:** `research/06__page4-functionality-toggles.md`, `07__onboarding-downloads.md`, `09__flavor-matrix.md`

## Зафиксированные решения

- Позиция страницы — индекс 3 (после networks, перед permissions).
- Тумблеры — file-manager(allFiles), audio, video, documents(мастер/4), OCR, translation, VR. Видимость:
  - audio/video/documents → `MediaCapabilities.supports*`.
  - OCR → `CapabilityAvailability.isOcrAvailable(context)`.
  - translation → `isTranslationAvailable()` И (`isTranslationBundled()` ИЛИ `InstallSourceProvider.isPlayInstall()`) — скрыт на DFM-флейворах вне Play.
  - VR → `CapabilityAvailability.isVrAvailable()`.
- Documents master — ON → `supportText/Pdf/Epub/OfficeDocuments=true`; OFF → все false + `exitAllFilesForManualSupportToggle`; checked = any-true. EPUB исключается где `!MediaCapabilities.supportsEpub`.
- Тип-тумблеры — пишут `support*` через `exitAllFilesForManualSupportToggle(isChecked).copy(...)`; `updateSettings`.
- OCR/translation — на флипе `DeliverableDownloadRunner.enqueue(set)` + inline-прогресс из `progressOf(set)`; навигация не блокируется; `enableOcr/enableTranslation=true` ТОЛЬКО на `DownloadProgress.Installed` (инвариант S0386). Fail/offline → остаётся OFF.
- Дефолты — пост-пресетные (читаем текущий `AppSettings.first()`), OCR/translation = OFF по умолчанию.
- Кнопка «Загружаемые элементы» — видима по `isExtensionsScreenAvailable()`; запуск `ExtensionsManagerFragment` через `supportFragmentManager.add(android.R.id.content,...)`.
- VR-тумблер — пишет через инъектируемый `WelcomeVrToggle` (no-op в main, реальный в src/vr).
- «Отклонено в онбординге» — простой `enable*=false` + повторный перехват при первом использовании; `DISABLED_BY_USER` не эмитим.

## Новая общая инфраструктура (центрально, до параллели)

- `InstallSourceProvider` (core/capability) — `isPlayInstall(): Boolean` через `getInstallSourceInfo` (API30+) / fallback. Общий с S0401.
- `CapabilityAvailability.isTranslationBundled()` — новая capability `CAP_TRANSLATION_BUNDLED`, контрибьютится из source set `translationMlKit` (bundled-флейворы noLegal/legacy).
- `WelcomeVrToggle` интерфейс + no-op main binding + vr binding.

## Контракт файлов

NEW (disjoint, владелец — агент B):
- `res/layout/page_welcome_functionality.xml` + `layout-land/` → `PageWelcomeFunctionalityBinding`.
- `ui/welcome/holders/FunctionalityPageViewHolder.kt` (тумблеры + inline-прогресс + live-region).
- `ui/welcome/helpers/WelcomeFunctionalityBinder.kt` (логика тумблер→настройка + enqueue/observe), если холдер пухнет.

SHARED (центрально):
- `WelcomePagerAdapter.kt` — VIEW_TYPE_FUNCTIONALITY + dispatch + поля `WelcomePage`.
- `WelcomeActivity.kt` — `pagesList.add(3)` + inject `CapabilityAvailability`, `InstallSourceProvider`; колбэки.
- `WelcomeViewModel.kt` — методы persist каждого тумблера + `enqueueDeliverable`/`deliverableProgress`; inject `DeliverableDownloadRunner`, `CapabilityAvailability`, `WelcomeVrToggle`.
- strings ~12 (`welcome_func_*`).

## Фазы
1. Общая инфра: InstallSourceProvider, CAP_TRANSLATION_BUNDLED, WelcomeVrToggle. Build green.
2. Skeleton: VIEW_TYPE + stub холдер/layout + поля + pagesList(3) + ViewModel-методы (stub). Build green.
3. Тумблеры → настройки (file-manager/audio/video/documents-master) + видимость по capability.
4. OCR/translation: enqueue + inline-прогресс + post-install-enable.
5. VR-тумблер + кнопка элементов.
6. Cleanup + build все флейворы.

## Валидация
- assemble Standard/Lite/Photos/Legacy debug green (составы тумблеров отличаются по флейвору).
- Юнит: documents-master агрегация + allFiles-exit; post-install-only flip.
