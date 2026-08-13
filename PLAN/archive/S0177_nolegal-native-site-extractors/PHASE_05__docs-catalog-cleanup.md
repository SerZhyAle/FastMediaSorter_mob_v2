# Phase 05 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0177_nolegal-native-site-extractors.md`](../S0177_nolegal-native-site-extractors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04 (all extractors implemented)
**Blocks:** nothing — final phase
**Steps done:** 3 / 3
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Update noLegal feature docs (EN/RU/UK), regenerate the class catalog, and add remaining dev log entries.

---

## Prerequisites

- [ ] Phases 01–04 are all ✅ Done.
- [ ] noLegal build passes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified | +3 lines |
| `docs/FEATURES_noLegal_RU.md` | Modified | +3 lines |
| `docs/FEATURES_noLegal_UK.md` | Modified | +3 lines |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |

---

## Steps

### Step 05.1 — Update noLegal feature docs (EN / RU / UK)

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a bullet to the link-download or extraction section in each file (see strategic §8 for the exact text):
>
> **EN** (`docs/FEATURES_noLegal.md`):
> `- Native Kotlin extractors for ArtStation, DeviantArt, Vimeo, and Dailymotion — no browser rendering, results in under 1 second.`
>
> **RU** (`docs/FEATURES_noLegal_RU.md`):
> `- Нативные Kotlin-extractors для ArtStation, DeviantArt, Vimeo и Dailymotion — без рендеринга браузера, результат менее чем за 1 секунду.`
>
> **UK** (`docs/FEATURES_noLegal_UK.md`):
> `- Нативні Kotlin-extractors для ArtStation, DeviantArt, Vimeo та Dailymotion — без рендерингу браузера, результат менш ніж за 1 секунду.`
>
> These files are gitignored (noLegal-only, local). Do not edit `docs/FEATURES.md`, `docs/FEATURES_RU.md`, or `docs/FEATURES_UK.md`.

**Verification:**

- `Grep` — `ArtStation` present in `docs/FEATURES_noLegal.md`.
- `Grep` — `ArtStation` present in `docs/FEATURES_noLegal_RU.md`.
- `Grep` — `ArtStation` present in `docs/FEATURES_noLegal_UK.md`.
- `Grep` — `ArtStation` absent from `docs/FEATURES.md` (public file must not contain noLegal entries).

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 4/4 PASS. Files: docs/FEATURES_noLegal.md, docs/FEATURES_noLegal_RU.md, docs/FEATURES_noLegal_UK.md. ArtStation present in all 3, absent from public FEATURES.md. Dev log recorded.

---

### Step 05.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Then set role and status for each new class via `set.ps1`:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ArtStationExtractionStrategy -Role "noLegal URL extraction strategy for artstation.com" -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class DeviantArtExtractionStrategy -Role "noLegal URL extraction strategy for deviantart.com" -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class VimeoExtractionStrategy -Role "noLegal URL extraction strategy for vimeo.com" -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class DailymotionExtractionStrategy -Role "noLegal URL extraction strategy for dailymotion.com" -Status active
> ```
> Re-run `render.ps1` after `set.ps1` calls.

**Verification:**

- `Grep` — `ArtStationExtractionStrategy` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `DeviantArtExtractionStrategy` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `VimeoExtractionStrategy` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `DailymotionExtractionStrategy` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 4/4 PASS. scan.ps1 fixed to include noLegal\java; 1032 records. All 4 extractor classes in app_v2.md with role+status=new. Dev log recorded.

---

### Step 05.3 — Dev log for docs and catalog

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_noLegal.md" "S0177" "Document native extractors: ArtStation, DeviantArt, Vimeo, Dailymotion"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.md" "S0177" "Catalog regen: 4 new noLegal extractor classes"
> ```

**Verification:**

- `Grep` — `FEATURES_noLegal` appears in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 1/1 PASS. `FEATURES_noLegal` present in dev/CHANGELOG.md. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] All three noLegal feature doc files have the new bullet.
- [ ] `dev/CATALOG/app_v2.md` contains all four new class entries.
- [ ] INDEX.md `Phases: 5/5 done` and `Status: Done`.
- [ ] Run `/spec-check S0177` to advance strategic spec to `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Low risk — only docs and generated catalog files changed. Revert the phase commit; code changes from Phases 01–04 are unaffected.
