# S1243 - The VR docs never mention the in-headset immersive browser

**Status:** Archived
**Priority:** 40

## 0. Raw capture

Found 2026-07-28 while checking the document registry for S1222 (immersive browse playback ignored the stereo config). Not related to that fix - parked per CLAUDE.md 3.1.

## 1. Symptom

The immersive in-headset browse grid shipped in **S0963** and has had four tickets filed against it since (S1116 readability, S1132 ray inversion, S1133 grid navigation, S1222 stereo playback). It is absent from every published VR document.

Evidence - both registry-tracked pages list exactly three immersive entry points and stop there:

- `docs/VR_EDITION.md:35` - "opening 3D content through the player's VR badge, Browse's 'Open in VR Cinema' menu item, or the 'Test Immersive' button in Settings".
- `docs/VR_CONTROLS.md:13` - the same three, verbatim.

Neither describes browsing a resource from inside the headset, paging through a tile grid, or selecting a file there. A reader of the docs would conclude the immersive session can only ever play the one file that launched it.

## 2. Why it matters

`docs/VR_EDITION.md:35` also says movement between files inside the session "is next/previous only for now". With the in-headset browser that statement is incomplete rather than wrong, and it is the one line a user would read to decide whether the VR build can browse at all.

## 3. Scope

- Registry record `vr-docs` (`docs/VR_*.md`), languages `en`, `ru`, `uk` - so six files, not two.
- Whether the browser also belongs in `docs/VR_CONTROLS.md`'s input tables depends on **S1240** (controller mapping) landing first; document what exists, not what is planned.

## 4. Open

- Should this wait for **S1116**/**S1132** to reach `Verified`? Documenting a grid whose ray aim is still under device test risks describing behaviour that changes.
  - **Status: Resolved by fact, 2026-08-14.** Both are past `Verified` and already `Archived`, as are S1222 and S1240. Nothing to wait for. The one pillar ticket still open is **S1133** (thumbstick grid navigation, `BlockQuestions`), so the docs describe ray-and-trigger only and say outright that thumbstick navigation of the grid is not implemented - which is §3's "document what exists, not what is planned" applied literally.

## 5. What was written (2026-08-14)

Six files, `en`/`ru`/`uk` in parity:

- `VR_EDITION*`: the "next/previous only" sentence corrected to name the browser as the other way to move between files, followed by a paragraph describing the in-headset browse window - "Open in VR Cinema" on a *resource* opens it as a tile grid, ray plus trigger selects, folders open in place, a file starts in the immersive player without returning to the flat screen, tiles carry previews, 3D images use the same SBS/OU/equirectangular detection. Two limits stated: local resources only, and ray rather than thumbstick.
- `VR_CONTROLS*`: a bullet placed last in the live control list, before the "still missing" bullet, saying the same ray drives the browse grid and that there is no separate grid control scheme.

Sourced from the archived S0963 (goals 1-5, and its owner device audit on Quest 3 of 2026-07-19 confirming grid, ray navigation, cell selection, folder entry and immersive launch), plus the current statuses of S1116/S1132/S1133/S1222/S1240. No headset was available in this session, so nothing was claimed beyond what those records establish.

## 6. Parked while doing this

- **S1654** - the whole immersive-browser pillar (S0963, S1116, S1132, S1222, S0962, S0964) has **zero** records in `docs/ALL_FEATURES.jsonl`. The capability shipped and was verified on device, but the inventory never learned about it. That is why S1395's guide-versus-inventory reconciliation could not have found this gap, and why the gate S1653 proposes would inherit the same blind spot.

## Last Audit

**Date:** 2026-08-14
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

Проверено: обе страницы реестра `vr-docs` описывают внутришлемный браузер на трёх языках;
утверждение «next/previous only» исправлено во всех трёх `VR_EDITION`; §4 снят фактом (блокеры
`Archived`); §3 соблюдён - `S1133` открыт, поэтому навигация стиком явно названа нереализованной;
сетевые и облачные источники названы неподдерживаемыми, как в non-goals S0963.
`post-change.ps1` PASS, реестр подтверждён (`vr-docs`).

### Manual / on-device

- [ ] Текст написан по архивным записям, а не по наблюдению: гарнитуры в этой сессии не было. Стоит сверить при следующем прогоне на Quest.
