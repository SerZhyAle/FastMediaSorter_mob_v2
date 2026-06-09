---
name: s0386-delivery-pause
description: S0386 on-demand delivery - additive parts (channel/UX/contract/extensions screen) done & inert; debundle core still BlockExternal pending multi-ABI payload hosting + device validation
type: project
---

S0386 (on-demand OCR/translation/heavy-asset delivery): phases 01-04, 06, 08 and steps 05.1-05.2 done and `standardDebug`-green as of 2026-06-09 (commit `9a097b7e` on `DEBUG-v013`). Delivered and **inert** (nothing stripped from the base yet, so no behavior change): delivery channel (descriptor/manifest/integrity-verifier/downloader), capability contract, default-OFF migration, enable-intercept UX, set-contributor extension point, the store translation Play dynamic-feature path (`:translate_feature` module + shared `translationMlKit` source set), and the "Downloadable Extensions" manager screen (Pillar G, Phase 08 - built ahead of Phase 07).

Debundle core (phases 05.3-05.7, 07) remains at **BlockExternal**.

**Why:** Removing the native libs/assets from the base breaks live OCR/translation/DTS/audio-viz until Phase 07 attaches the delivered payloads, and that needs (a) OSS payloads hosted for ALL store ABIs - store flavors ship `arm64-v8a/armeabi-v7a/x86/x86_64`, but only `arm64-v8a` is hosted in GitHub release `delivery-so-v1`; (b) Set C audio-viz hosted; (c) a release build + on-device validation pass. Hosting is owner-only (`gh` is not available to the agent), and store-flavor device tests are not automatable here. Set A translation is split by flavor: store = Play dynamic-feature, sideload/VR (`noLegal`/`vr`) = bundled (Google `.so` are not re-hosted).

**How to apply:** The additive work is safe to extend without the block. To resume the debundle: host the remaining OSS `.so` (armeabi-v7a/x86/x86_64) + Set C, then execute 05.3-05.6 + Phase 07 together and validate on a release build + device. Verify current state against the spec header / INDEX before acting - this snapshot is point-in-time.
