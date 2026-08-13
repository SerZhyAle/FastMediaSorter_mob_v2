# S0077 — BDMV/DVD Network Thumbnail Routing Fix: Tactical Index

**Strategic spec:** `PLAN/S0077_bugfix-network-video-thumbnail-routing-bdmv-dvd.md`
**Status:** Verified
**Tier:** 2 — Easy
**Updated:** 2026-05-04

---

## Root Cause (confirmed via code)

`NetworkThumbnailExtractionPolicy.BLOCKED_EXTENSIONS` currently contains only `{"avi"}`.
For `.m2ts`, `.vob`, `.ts` etc. the policy check returns `false` (not blocked), so
`AdapterThumbnailLoader.loadVideo()` and `PagingMediaFileAdapter` issue a Glide request with
`NetworkFileData`. However:

- `NetworkFileModelLoader.handles()` **rejects** these extensions (they ARE in its `VIDEO_EXTENSIONS`).
- `NetworkVideoFrameDecoder.handles()` **also rejects** them (they are NOT in its `VIDEO_EXTENSIONS`).

Result: both registered loaders refuse the model → `NoModelLoaderAvailableException` every bind.

**Fix:** add the optical-disc extensions to `BLOCKED_EXTENSIONS`. The existing consumer code
(`AdapterThumbnailLoader`, `PagingMediaFileAdapter`) already has a correct fast-path for blocked
extensions — it shows a placeholder immediately without starting a Glide request. No new consumer
code needed.

**Secondary gap (from S0063):** `thumbnail_unavailable_network_format` string exists only in EN
(`values/strings.xml` line 6). RU/UK translations are missing.

---

## Phase Overview

| # | Phase | Status | Steps | Key files |
|---|-------|--------|-------|-----------|
| 01 | [Extend blocked extensions + i18n](PHASE_01__extend-blocked-extensions.md) | `[x]` done | 3 | `NetworkThumbnailExtractionPolicy.kt`, `values-ru/strings.xml`, `values-uk/strings.xml` |
| 02 | [Catalog + dev log](PHASE_02__catalog-devlog.md) | `[x]` done | 2 | `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md` |

---

## Affected Files

| File | Lines (approx) | Change | Phase |
|------|---------------|--------|-------|
| `data/network/glide/NetworkThumbnailExtractionPolicy.kt` | ~40 | Add optical-disc extensions to `BLOCKED_EXTENSIONS` | 01 |
| `app_v2/src/main/res/values-ru/strings.xml` | ~10 | Add RU translation for `thumbnail_unavailable_network_format` | 01 |
| `app_v2/src/main/res/values-uk/strings.xml` | ~10 | Add UK translation for `thumbnail_unavailable_network_format` | 01 |

**No new classes. No Room migrations. No Hilt changes. No Wear OS changes.**

---

## Completion Criteria (from strategic §11)

- [ ] Browse of BDMV/DVD network folder: no `NoModelLoaderAvailableException` for `.m2ts`/`.vob` in logcat.
- [ ] Same folder: placeholder shown immediately and stably for each optical-disc item.
- [ ] Repeat scroll / rebind of same items: no repeated failed Glide request (policy blocks before Glide).
- [ ] `.mp4`/`.mkv` thumbnails on network shares load normally (no regression).
- [ ] `.\gradlew.bat assembleStandardDebug` — build passes.
- [ ] `.\gradlew.bat lintStandardDebug` — lint clean.
- [ ] `values-ru/strings.xml` and `values-uk/strings.xml` both contain `thumbnail_unavailable_network_format`.

---

## Non-touched Areas

- `NetworkVideoFrameDecoder.VIDEO_EXTENSIONS` — **not changed**: optical-disc formats never reach
  the decoder after the policy gate; touching this would be unnecessary scope.
- `NetworkFileModelLoader.VIDEO_EXTENSIONS` — **not changed**: already correctly rejects these
  formats at the Glide layer; the fix operates one layer above.
- Playback routing for `.vob`/`.m2ts` — out of scope (S0076, S0054).
