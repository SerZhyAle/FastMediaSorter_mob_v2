---
layout: default
title: "Architecture Overview"
permalink: /docs/V2_architecture_overview.html
lang: en
---

# Architecture Overview

[Technical specification](V2_Specification.html) | [Technology stack](TECH_STACK.html) | [Requirements](TECHNICAL_REQUIREMENTS.html)

The application uses MVVM, Hilt dependency injection, repositories and use cases. Its runtime flow resembles Clean Architecture, but compile-time dependencies are intentionally pragmatic.

## Layered flow

`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`

ViewModels expose observable state and events. Use cases coordinate operations. Repository interfaces provide a testable seam; implementations manage persistence and local, network or cloud access. Domain code also reuses selected data-layer types, so this is not a claim of strict dependency inversion throughout the project.

## Modules

- `app_v2`: the main application and its eight declared phone/headset flavors; XML layouts and ViewBinding with existing Compose surfaces.
- `wear`: the Wear OS application, implemented in Compose, with `standard` and `noLegal` flavors.
- `watchface`: a separate resource-only Watch Face Format v4 application for Wear OS 6 / API 36 and later.
- `lint-rules`: custom build-time lint checks.
- `benchmark`: benchmark and baseline-profile tooling.

## Key implementation notes

- Presentation state belongs in ViewModels; complex screen behavior is delegated to managers/helpers rather than added to Activities.
- Room stores application state; migrations and recovery behavior are module-specific. A recovery path is not a substitute for verified migrations.
- Flavor-specific source sets and injected contracts select optional integrations; disabled functionality must not be inferred from common source code alone.
- The phone and Wear applications share `com.sza.fastmediasorter` as their application ID, while Wear retains a separate namespace. Compatible package/signing identities matter for the Data Layer.
- The watch face is not compiled into the Wear application: its XML is rendered by the operating system.

### Internet Streams subsystem

`StreamsActivity` and `StreamsViewModel` present the list. Catalog import uses `ImportStreamCatalogUseCase` and `StreamSourceRepository`, backed by Room entities and DAOs. `StreamInlineAudioManager` owns list-side audio playback and ICY metadata; fullscreen video uses the existing player path.

Protocol support and screen availability depend on the edition. Consult the [generated capability matrix](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md), rather than treating every declared flavor as equivalent. Remote endpoint availability and codec compatibility are not guaranteed by the catalog.

### File operations and launcher

File operations dispatch through protocol-specific strategies. Cross-protocol folder transfers process entries incrementally; completed destination entries can remain after cancellation, and a move removes an entry only after its copy is confirmed. This does not provide whole-folder transactional undo.

Launcher-enabled builds offer a desktop, taskbar and gadgets, but Android Home-role activation is chosen by the user. Portrait and landscape desktop layouts are independent application state.

## Authoritative implementation references

- [Gradle module list](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/settings.gradle.kts)
- [Phone flavors and source sets](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/app_v2/build.gradle.kts)
- [Wear identity and packaging](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/wear/build.gradle.kts)
- [Watch Face Format package](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/watchface/build.gradle.kts)

For practical setup instructions, use the [user documentation portal](../documentation/).
