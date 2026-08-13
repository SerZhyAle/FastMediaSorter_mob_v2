# Phase 06 - Strings, feature inventory, docs + catalog cleanup

**Strategic spec:** [`../S0668_streams-favicon-sprite-map.md`](../S0668_streams-favicon-sprite-map.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01-05
**Blocks:** none - final phase before device test
**Steps done:** 0 / 4

---

## Objective

Add the localized contentDescription string (EN/RU/UK), record the shipped capability in the feature inventory, regenerate the class catalog, and log the change. The public showcase (`docs/FEATURES*.md`) is NOT edited here - that is `/skill-release`-owned.

---

## Prerequisites

- [ ] Phases 01-05 complete (the `streams_favicon` string is referenced by the layout in Phase 05).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` + `values-ru/` + `values-uk/` | Modified | ≤ 5 each |
| `docs/ALL_FEATURES.jsonl` | Modified (append via tool) | n/a |
| `dev/CATALOG/app_v2.*` | Regenerated (gitignored) | n/a |
| `dev/CHANGELOG.md` | Modified (via dev-log script) | n/a |

---

## Steps

### Step 06.1 - Add the `streams_favicon` contentDescription string (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one key `streams_favicon` across EN/RU/UK with `scripts/utils/set-android-string.ps1 -Action add -Key streams_favicon -En "Channel icon" -Ru "Значок канала" -Uk "Значок каналу"` (byte-preserving, parity-enforced). RU must use ё where grammatical (none needed here). This is the favicon ImageView's contentDescription for TalkBack.

**Verification:**

- `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key streams_favicon` lists the key present in EN/RU/UK.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_favicon"` exits 0 (parity OK).

**Status:** `[ ]`

---

### Step 06.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Append one EN-only record via `scripts/all_features/add.ps1` describing the shipped capability: the stream catalog shows a recognizable per-channel favicon thumbnail (sliced from a sprite atlas bundled in the catalog archive) where available; channels without one show an empty slot. Flavor reach standard/legacy/noLegal/vr. This is a standard (not noLegal) capability, so it goes to `docs/ALL_FEATURES.jsonl` (NOT the gitignored noLegal inventory). Do NOT edit `docs/FEATURES*.md`.

**Verification:**

- `Grep` - a new line mentioning `favicon` (and stream catalog) exists in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[ ]`

---

### Step 06.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (gitignored, regenerated)
**Depends on:** - new classes added in Phases 03/04

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once so the new `FaviconAtlasStore` and `FaviconAtlasSlicer` are indexed. Fill their `role` + `status` via `dev/CATALOG/scripts/set.ps1` if the sync flags them as unclassified. These indexes are gitignored - regenerate, do not commit.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*FaviconAtlas*"` returns both `FaviconAtlasStore` and `FaviconAtlasSlicer`.

**Status:** `[ ]`

---

### Step 06.4 - Dev log + flag the external closure items

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Steps 06.1-06.3

**Prompt for developer:**

> Add ONE consolidated dev-log entry for S0668 (strings + feature inventory + the whole favicon feature) via `scripts/add_to_dev_log.ps1` (one entry per logical change, not per file). In the entry, note that three closure items are EXTERNAL and owner-run: (1) run the collector with `-WithFavicons` to build a real atlas; (2) `Invoke-PublishCatalog` to publish the new `stream-catalog.zip`; (3) on-device verification of rendered thumbnails. Then set the journal status: code is complete but the headline behavior can only be proven once the real atlas is published + viewed on device, so transition to `BlockNeedUserTest` with `-StatusNote` describing the device check (favicon thumbnails render for channels with a favicon; favicon-less rows show an empty slot). Per CLAUDE.md §2, insert exactly ONE `Timber.d("S0668: ..")` probe at the import->render entry point (e.g. when the adapter binds the first favicon tile) since the spec is now `BlockNeedUserTest`.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an S0668 entry referencing the favicon feature.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0668 -Format json` shows `BlockNeedUserTest` (after the transition).
- `Grep` - exactly one `Timber.d("S0668:` line exists in `app_v2/src` (the BlockNeedUserTest probe).

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `streams_favicon` exists in EN/RU/UK with parity (audit exit 0).
- [ ] `docs/ALL_FEATURES.jsonl` records the capability; `docs/FEATURES*.md` NOT touched.
- [ ] The class catalog indexes the two new classes.
- [ ] One consolidated dev-log entry exists; status moved to `BlockNeedUserTest` with a note; exactly one `Timber.d("S0668: ")` probe present.

---

## Handoff Notes (closure)

After this phase the code is complete. Remaining work is EXTERNAL/owner-run (build real atlas, publish zip, device-verify). Once device verification passes, remove the `Timber.d("S0668: ")` probe, move status to Verified via `/spec-check S0668`, and let `/skill-release` emit the showcase sentence from the `ALL_FEATURES` diff.

---

## Rollback Plan

Revert the phase commit: remove the string (all three locales), remove the `ALL_FEATURES` line, regenerate the catalog. Code from Phases 01-05 stays; the feature is functional but undocumented/unstringed (would fail the string audit, so roll back the layout reference too if reverting this far).
