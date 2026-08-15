# Спецификация (compact bugfix): S1109 - Устаревшая секция «Internet Streams Subsystem» в ARCHITECTURE.md

**Ticket:** S1109
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-19
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-19

**Захвачено во время:** ad-hoc задача документирования подсистемы Streams (source-spec для FastMediaSorter for Windows). Находка §3.1, запаркована без переключения активной задачи.

**Текст:**

Doc-drift finding surfaced independently by two research subagents (entry-points agent E and player-routing agent D) while extracting the current Streams implementation.

`docs/ARCHITECTURE.md`, section "Internet Streams Subsystem" (~lines 203-213), is stale versus the working tree:

- (E) It names 5 classes that no longer exist in the codebase: `GetStreamsUseCase`, `AddStreamUseCase`, `StreamsRepository`, `StreamsDataSource`, `StreamCatalogRemoteDataSource`. The real current classes are `StreamSourceRepository`, `StreamSourceDao`, `StreamSourceEntity`, `ImportStreamCatalogUseCase`, `StreamCatalogCsvParser`, `StreamMediaKindClassifier`, `FaviconAtlasStore`, `StreamsViewModel`/`StreamsActivity`.
- (D) Line ~209 misattributes fullscreen-stream protocol selection to `PlayerMediaLoaderManager` / `NetworkAwareMediaSourceFactory`. The actual path is `StreamPlaybackHelper.playStreamVideo` + `StreamDataSourceFactoryProvider`; `NetworkAwareMediaSourceFactory` is used only by `AudioPlaybackService`.

Effect: the architecture doc actively misleads anyone navigating the Streams feature (dead class names + wrong protocol-selection owner).

Proposed scope (for later, not now): rewrite the "Internet Streams Subsystem" section of `docs/ARCHITECTURE.md` to match the current class graph and the real player-side protocol path; close via the document-registry loop (`validate.ps1` + `generate.ps1 -Check`).

**Эвиденс (в репозитории, по путям):**
- `docs/ARCHITECTURE.md:203-213` - устаревшая секция.
- `temp/scratch/streams-src-doc/E_entrypoints_gating.md` - §12 (registry drift, 5 dead classes).
- `temp/scratch/streams-src-doc/D_player_routing.md` - §15 (misattributed protocol path).

**Дедуп:** S1086 (Approved) - отдельная проблема ARCHITECTURE.md (dependency rule: domain imports data directly), не про устаревшие имена streams-классов. Не дубликат.

---

## 1. Проблема / симптом

Секция `docs/ARCHITECTURE.md` «Internet Streams Subsystem» описывает архитектуру Streams именами классов, которых давно нет (`GetStreamsUseCase`, `AddStreamUseCase`, `StreamsRepository`, `StreamsDataSource`, `StreamCatalogRemoteDataSource`), и неверно указывает владельца выбора протокола для полноэкранного потока. Реальная реализация - `StreamSourceRepository`/`StreamSourceDao`/`ImportStreamCatalogUseCase`/`StreamMediaKindClassifier` (данные) и `StreamPlaybackHelper.playStreamVideo` + `StreamDataSourceFactoryProvider` (плеер).

---

## 2. Корневая причина

Doc-drift: секция писалась под старую архитектуру Streams (до рефактора на `StreamSource*`-стек и `StreamPlaybackHelper`), доку не синхронизировали. Подтверждено грепом по `app_v2/src`:

- 5 имён из доки = 0 вхождений: `GetStreamsUseCase`, `AddStreamUseCase`, `StreamsRepository`, `StreamsDataSource`, `StreamCatalogRemoteDataSource`.
- Реальные присутствуют: `StreamSourceRepository` (19), `ImportStreamCatalogUseCase` (8), `StreamPlaybackHelper` (2), `StreamDataSourceFactoryProvider` (6), `StreamMediaKindClassifier` (8), `StreamCatalogCsvParser` (5), `FaviconAtlasStore` (9).
- Протокол полноэкранного потока: `VideoPlayerManager` (стр. 718) роутит `HTTP_STREAM`/`RTSP_STREAM` в `playStreamVideo` (`StreamPlaybackHelper`) -> `StreamDataSourceFactoryProvider` (+ per-session `BandwidthAdaptiveLoadControl`). `NetworkAwareMediaSourceFactory` - фабрика аудио-сервиса (`AudioPlaybackService`/`AudioServiceController`), не видео-путь.

---

## 3. Исправление

Переписать в `docs/ARCHITECTURE.md` секцию «Internet Streams Subsystem»:

1. Bullet Video/RTSP: заменить неверного владельца (`PlayerMediaLoaderManager` / `NetworkAwareMediaSourceFactory`) на реальный путь `VideoPlayerManager.playStreamVideo` (`StreamPlaybackHelper`) -> `StreamDataSourceFactoryProvider` + `BandwidthAdaptiveLoadControl`; отметить, что `NetworkAwareMediaSourceFactory` - фабрика аудио-сервиса.
2. Bullet Data flow: заменить 5 мёртвых имён на реальный граф (`StreamsViewModel` -> `ImportStreamCatalogUseCase` (+ `StreamCatalogCsvParser` / `StreamMediaKindClassifier` / `FaviconAtlasStore`) -> `StreamSourceRepository` -> `StreamSourceDao` / `StreamSourceEntity` (Room); каталог - mutable GitHub Release asset).

Закрыть через document-registry loop (`validate.ps1` + `generate.ps1 -Check`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1086 (другой дрейф ARCHITECTURE.md), S0565/S0570/S0668 (streams-архитектура).

---

## 4. Проверка

- Grep: dead-имена (`GetStreamsUseCase` / `AddStreamUseCase` / `StreamsRepository` / `StreamsDataSource` / `StreamCatalogRemoteDataSource`) в `docs/ARCHITECTURE.md` = 0.
- Grep: секция содержит `StreamPlaybackHelper`, `StreamDataSourceFactoryProvider`, `StreamSourceRepository`, `ImportStreamCatalogUseCase`.
- Document-registry: `validate.ps1` exit 0; `generate.ps1 -Check` exit 0.
- Doc-only: сборка не требуется.

---

## Last Audit

**2026-07-19 (static, doc-only):** Verified.

- `docs/ARCHITECTURE.md` "Internet Streams Subsystem": Video/RTSP bullet rewritten to the real path (`VideoPlayerManager.playStreamVideo` -> `StreamPlaybackHelper` -> `StreamDataSourceFactoryProvider` + `BandwidthAdaptiveLoadControl`; `NetworkAwareMediaSourceFactory` relabelled the audio-service factory). Data-flow bullet's 5 dead class names replaced with the live graph (`StreamsViewModel` -> `ImportStreamCatalogUseCase` + parser/classifier/atlas -> `StreamSourceRepository` -> Room DAO/entity).
- Verification: dead names = 0 in the doc; grep-confirmed the 5 dead classes absent from `app_v2/src` and the real classes present.
- Document-registry loop: `validate.ps1` PASS (23 records); `generate.ps1 -Check` current.
- No user-visible capability (developer architecture doc) - no `ALL_FEATURES` record.
