# Tactical Plan: S1026 - animated-webp-support

**Strategic spec:** [`../S1026_animated-webp-support.md`](../S1026_animated-webp-support.md)
**Research inputs:** [`research/01__animated-image-pipeline.md`](research/01__animated-image-pipeline.md)
**Feature:** Animated WebP (and APNG) animate in the image viewer like GIF, via platform `ImageDecoder` (API 28+); static fallback below.
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (awaiting device verification - BlockNeedUserTest)
**Phases:** 3 / 3 done
**Last updated:** 2026-07-14

> Resolved (research/01, no owner input): platform `ImageDecoder`->`AnimatedImageDrawable` API 28+ (no new dependency); gate animated UI on decoded `resource is Animatable` (not extension); webp stays `MediaType.IMAGE`; APNG included.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | glide-animated-decoder | - | ⬜ Not started | 3 | [PHASE_01__glide-animated-decoder.md](PHASE_01__glide-animated-decoder.md) |
| 02 | viewer-animatable-gating | 01 | ⬜ Not started | 3 | [PHASE_02__viewer-animatable-gating.md](PHASE_02__viewer-animatable-gating.md) |
| 03 | build-and-device-probe | 02 | ⬜ Not started | 2 | [PHASE_03__build-and-device-probe.md](PHASE_03__build-and-device-probe.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Completion Gate

- [ ] Animated webp/apng decode to an `AnimatedImageDrawable` (API 28+) through Glide; below API 28 static (unchanged).
- [ ] Animated badge + play/pause key on decoded `resource is Animatable`; static webp shows no badge/no-op toggle.
- [ ] `MediaType` unchanged; no bitmask/preset churn.
- [ ] Leftover webp diagnostic logging in `ImageLoadingGlideListeners` cleaned up.
- [ ] standard debug build PASS.
- [ ] Device verification (animated webp/apng animates + play/pause; static webp no badge) - deferred to `BlockNeedUserTest`.

---

## Change Log

- 2026-07-14 - Tactical plan authored by `/spec-tech` (F2). Forks resolved from codebase precedent.
