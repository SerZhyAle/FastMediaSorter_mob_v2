---
layout: default
title: "🏗️ Architecture Overview"
permalink: /docs/V2_architecture_overview.html
---

# 🏗️ Architecture Overview

FastMediaSorter v2 follows **Clean Architecture + MVVM + Hilt**.

## Layered flow

`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`

## Modules

- `app_v2/` - main Android application
- `wear/` - Wear OS companion application

## Key implementation notes

- UI layer contains presentation logic only
- Business rules are concentrated in domain use cases
- Data layer handles network/cloud/local providers
- Complex Activity logic is delegated to helper/manager classes

### Internet Streams subsystem

- Dedicated `StreamsActivity` / `StreamsViewModel` with a `StreamsRepository` and `StreamDataSource`.
- Inline audio playback via `StreamInlineAudioManager`; sticky mini-control surfaces ICY now-playing metadata without leaving the list.
- Video/RTSP opens the fullscreen player; back returns to list with scroll position preserved.
- Catalog import: `ImportStreamCatalogUseCase` fetches a remote curated catalog with fast-fail timeout; imported rows carry topic/language metadata and are filterable.
- Flavor scope: standard/legacy/noLegal/vr - HLS, DASH VOD, RTSP, progressive HTTP/ICY (`SUPPORT_STREAMS=true`); lite/photos - feature absent, no entry point (`SUPPORT_STREAMS=false`, lite hidden by S0575).

For full architecture details see [ARCHITECTURE.md](ARCHITECTURE.md).
