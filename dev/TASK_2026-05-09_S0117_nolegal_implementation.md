# Задача 2026-05-09 — S0117: `noLegal` flavor и site-specific URL downloader

## Контекст

- Пользователь запросил implementation S0117.
- По spec catalog S0117 сейчас в статусе `Draft`; tactical-папка `PLAN/S0117_url-media-downloader-nolegal-flavor/` отсутствует.
- Блокер-предшественник снят: S0116 имеет статус `Verified`.
- Текущая кодовая база уже содержит основные точки расширения:
  - `app_v2/build.gradle.kts` — product flavors, sourceSets, flavor-specific dependencies.
  - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt` — текущий order стратегий `direct` → `html`; сюда должен встать site-resolver первым.
  - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` — сейчас покрывает только single-item pipeline; batch-координатор отсутствует.
  - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OpenSourceLicensesFragment.kt` и `app_v2/src/main/res/layout/fragment_open_source_licenses.xml` — существующий About/license surface для GPL notice.
- Репозиторий уже подключает JitPack в `settings.gradle.kts`; внешний артефакт NewPipe подтверждён как `com.github.TeamNewPipe:NewPipeExtractor`.

## Что уже подтверждено по фактам

1. S0117 должен идти через compile-time isolation: новый flavor + `src/noLegal/`, а не через runtime-only feature flag.
2. Market flavors не должны содержать site-specific классы и GPL dependency даже в неиспользуемом bytecode.
3. Существующий Open Source Licenses экран — естественная точка для `noLegal`-only GPL notice и source links.
4. Для album/multi-item сценария текущих `Result`/`ProgressState` недостаточно: нужен отдельный batch state/result слой поверх existing downloader pipeline.

## Решения, которые нужно зафиксировать перед кодом

1. **Статус-гейт:** трактуем текущий запрос пользователя как явное approval на перевод S0117 из `Draft` в execution path (`Approved` -> `Tactical` -> `In Progress`) без отдельного review-цикла.
2. **Dependency pin:** использовать upstream `com.github.TeamNewPipe:NewPipeExtractor` фиксированной версией. Базовое предложение: pinned stable tag `v0.24.0`, если пользователь не задаст другой tag/fork.
3. **GPL UI placement:** делать только existing About/Open Source Licenses entry в `noLegal`; без first-run диалога.
4. **MVP extractor scope:** делать internal allowlist-backed bridge на NewPipe в `src/noLegal/` без поимённых упоминаний платформ в публичных строках и docs.
5. **Album error policy:** при падении одного элемента продолжать остальные и в конце показывать итоговую сводку успехов/ошибок.
6. **Distribution channel work:** публикация sideload APK и релизные тексты не входят в этот implementation pass.

## Предлагаемый следующий ход после подтверждения

1. Перевести S0117 в implementation-ready состояние и создать tactical plan.
2. Добавить `noLegal` flavor, sourceSet и BuildConfig-изоляцию.
3. Ввести `noLegal`-only site-resolver/extractor contract и вставить его перед generic pipeline.
4. Расширить coordinator/result/progress для album batch поверх существующего writer/streaming pipeline.
5. Добавить GPL notice/source links в existing licenses screen только для `noLegal`.
6. Добить trilingual strings, dev log, catalog sync и узкую compile validation `noLegal` flavor.