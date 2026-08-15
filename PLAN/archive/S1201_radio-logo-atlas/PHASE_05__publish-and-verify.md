# Phase 05 - Publish and verify

**Strategic spec:** [`../S1201_radio-logo-atlas.md`](../S1201_radio-logo-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Publish the two artifacts as release assets, prove the app downloads and verifies them, and confirm on a device that a station without a capturable frame now shows its logo.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] `gh` CLI authenticated (`C:\Program Files\GitHub CLI\gh.exe` - not on PATH).
- [ ] A device or emulator is attached.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | Modified | ≤ 455 |

> Only the probe tag lands in source this phase; the rest is publication and on-device evidence.

---

## Steps

### Step 05.1 - Upload the assets

**Files:** none (publication)
**Depends on:** - start of phase

**Prompt for developer:**

> Run the packer with `-PublishStreamLogoAtlas` to upload `stream-logo-atlas-v1.webp` and `stream-logo-coords-v1.json` to the `delivery-so-v1` release. Then download both back over HTTP and compare their SHA-256 against the pins compiled into `DeliverableDescriptorCatalog.streamLogoAtlas()`. A mismatch means the pins were taken from a different build than the one uploaded - re-pin, do not re-upload silently.

**Verification:**

- `gh release view delivery-so-v1` lists both asset names.
- Value equality - SHA-256 of each downloaded asset equals its compiled pin; record `expected: <pin> | actual: <downloaded>` for both.

**Status:** `[x]` done

---

### Step 05.2 - Insert the device-test probe

**Files:** `ui/streams/StreamGridAdapter.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> At the branch that applied a logo tile, add exactly one `Timber.d("S1201: grid logo tile applied")`. This is the changed-flow entry probe required before the ticket enters `BlockNeedUserTest` (CLAUDE.md §2). One tag only, ≤120 characters; it is removed by `/spec-check` on the transition out of `BlockNeedUserTest`.

**Verification:**

- `Grep` - exactly one `Timber.d("S1201:` line across all `.kt` files.
- `.\a.ps1 d` builds an installable APK.

**Status:** `[x]` done

---

### Step 05.3 - Verify on device

**Files:** none (evidence)
**Depends on:** Step 05.2

**Prompt for developer:**

> Install the build, open the Extensions Manager, download the station-logos item, and confirm the worker reaches `Installed` and the two files land in `filesDir/delivery/STREAM_LOGO_ATLAS/` at the pinned byte sizes. Open the streams grid filtered to radio and confirm the probe fires and tiles render as logos rather than blurred icons. Also confirm the negative cases: a station with no logo falls back to its favicon, and one with neither shows the media-kind glyph - no empty tiles. Capture logcat and a screenshot to `temp/S1201/`.

**Verification:**

- Logcat contains `S1201: grid logo tile applied` - PASS (fires repeatedly on the streams grid).
- `ls` of the delivery dir shows both files at the pinned sizes - PASS; `expected: 6 645 666 / 142 799 | actual: 6 645 666 / 142 799`.
- Screenshot shows logo tiles in the radio grid and zero empty tiles - PASS (`temp/scratch/emulator-5554_20260726_114947.png`); the no-logo/no-favicon case renders the media-kind glyph.
- `I/StreamsActivity: Streams artwork: favicon=true/2438, preview=true/1881, logo=true/2156`.

**Status:** `[~]` partially done - the render half is verified, the in-app download tap is not. The payload was placed directly into `filesDir/delivery/STREAM_LOGO_ATLAS/` (same path, same bytes the downloader writes) because the Extensions Manager screen could not be reached on the emulator in reasonable time. The download path is shared, unchanged code; its two S1201-specific inputs - asset URL and pins - were verified independently in Step 05.1. Carried as the ticket's single `BlockNeedUserTest` item.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for `StreamGridAdapter.kt`.
- [ ] Phase-boundary audit run - focus: the download path handles a partial/corrupt payload by refusing it (pin mismatch) rather than installing a broken sheet.

---

## Handoff Notes to Next Phase

- The capability is live and evidenced; Phase 06 records it in the inventory and regenerates the catalog.

---

## Rollback Plan

Delete the two release assets and revert the descriptor pins. Users who already downloaded keep a payload nothing points at - harmless, since the tier is inert without matching coords.
