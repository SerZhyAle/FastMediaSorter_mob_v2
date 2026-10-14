---
layout: default
title: "Technical Requirements"
permalink: /docs/TECHNICAL_REQUIREMENTS.html
lang: en
---

<sub class="doc-stamp">26.10.06 14:18</sub>

# Technical Requirements

[Technical Specification](V2_Specification.html) | [Tech Stack, EN](TECH_STACK.html)

This page separates declared SDK requirements from feature-specific runtime conditions. It is a public reference, not an installation promise for every device or an announcement of a new release.

## SDK requirements

On narrow screens, scroll the SDK table horizontally to see every column.

| Module or edition | minSdk | targetSdk | compileSdk |
|---|---|---|---|
| `standard / noLegal / lite / photos` | 26 | 36 | 37 |
| `legacy / foss` | 23 | 36 | 37 |
| `vr` | 29 | 34 | 37 |
| `xr` | 26 | 36 | 37 |
| `wear: standard / noLegal` | 28 | 36 | 37 |
| `watchface` | 36 | 36 | 36 |

`minSdk` is the installation floor; `targetSdk` selects Android compatibility behavior; `compileSdk` is the API level used to compile. A high compileSdk does not mean every supported device needs that Android version.

## Device and service requirements

- Standard phone variants require Android 8.0 / API 26 or later. Legacy and FOSS have an API 23 floor; reduced features and device-specific compatibility still apply.
- VR has an API 29 floor and requires a compatible headset/runtime for immersive features; an Android API number alone does not establish headset support.
- The Wear application has an API 28 floor. The separate WFF v4 watch face requires Wear OS 6 / API 36 or later and is installed on the watch, not the phone.
- Phone/watch synchronization requires compatible application IDs, signing identities and connectivity. An application namespace is not a pairing identity.
- Local and document-tree storage need the corresponding Android permissions or user-granted access. Optional network/cloud features require connectivity and provider authorization.
- Google-dependent features need the corresponding Google services. This is not a claim that every cloud provider or every local feature requires Google Play Services.
- There is no universal RAM, heap or free-space threshold enforced by these Gradle definitions. Media resolution, codecs, caches and file operations determine practical resource needs; previously observed debug APK sizes are not minimum requirements.

## Edition availability

A flavor's presence in the build does not establish that its latest APK is published. Use the [product downloads](../index.html#download) for verified assets and the [generated capability matrix](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md) for build-time feature flags.
