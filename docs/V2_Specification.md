---
layout: default
title: "Technical Specification"
permalink: /docs/V2_Specification.html
lang: en
---

# Technical Specification

[Product](../index.html) | [User documentation](../documentation/) | [Terminology](V2_TERMS.html)

Fast Media Sorter combines an Android device shell, media playback, file organization, network/cloud access, Wear OS integration and a separate watch-face package. This specification describes the current source configuration; published releases may contain an earlier or narrower subset.

## Primary technical references

- [Architecture overview](V2_architecture_overview.html): modules, runtime flow and subsystem boundaries.
- [Technology stack](TECH_STACK.html): build tools, dependency sources and package identities.
- [Technical requirements](TECHNICAL_REQUIREMENTS.html): SDK floors, device capabilities and edition constraints.
- [Build and verification](BUILD_AND_VERIFICATION.html): public CI, SBOMs and build-versus-release distinctions.
- [Generated edition capability matrix](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md): the build-derived source of truth, viewed on GitHub.

## Scope

- Phone, tablet, TV, automotive and headset variants live in `app_v2`; the Wear application and resource-only watch face are separate modules.
- The main runtime flow is UI → ViewModel → UseCase → Repository → DataSource. Compile-time layer dependencies are pragmatic, not strict textbook Clean Architecture.
- Phone UI is primarily XML/ViewBinding; Wear UI is Compose. These are not interchangeable UI architectures.
- SDK and package identity are edition-specific. In particular, VR has its own SDK overrides, and the Wear application ID is not its namespace.
- Feature availability is determined by flavor flags and source sets. A compiled variant or a capability flag is not proof that an APK is publicly released.
- Launcher activation needs the user's Android Home-role selection. Storage access, cloud authorization and device capabilities can impose additional runtime requirements.

## Source and release boundaries

Use [source code](https://github.com/SerZhyAle/FastMediaSorter_mob_v2), [public releases](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases) and [public issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) for their respective purposes. Internal engineering plans, local credentials and signing material are not part of this website. The [public development pointer](TODO_V2.html) explains how to follow published changes without relying on inaccessible internal paths.
