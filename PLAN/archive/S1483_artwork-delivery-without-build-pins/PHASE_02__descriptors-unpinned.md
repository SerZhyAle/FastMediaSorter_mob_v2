# Phase 02 - Descriptors: blank pin, no revision in the name

**Strategic spec:** [`../S1483_artwork-delivery-without-build-pins.md`](../S1483_artwork-delivery-without-build-pins.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 2
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Point the two artwork descriptors at the stable asset names and stop pinning their SHA-256, leaving
the native-code descriptors untouched.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - the stable-named assets exist on the release.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalog.kt` | Modified | ≤ 210 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalogTest.kt` | New | ≤ 120 |

---

## Steps

### Step 02.1 - Unpin the artwork payloads and drop their revision suffix

**Files:** `DeliverableDescriptorCatalog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Give `channelPreviewAtlas()` and `streamLogoAtlas()` a blank `sha256` and a `minSize` floor that a
> truncated download cannot pass (1 MB for a pack, 32 KB for a sidecar), and resolve their file names
> with no revision suffix. Make `withRev` return the bare name when the revision is empty. Leave the
> native-library and audio-visualisation descriptors exactly as they are.

**Why:**

The pin is what makes an installed copy unable to accept a rebuilt payload, and the strategic spec
limits mandatory pinning to payloads that are loaded as executable code.

**Verification:**

- `Grep` - `ATLAS_PACK_SHA256` and `LOGO_PACK_SHA256` no longer exist.
- `Grep` - `TESSERACT`/`FFMPEG` sha constants still present.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

### Step 02.2 - Test that unpinned means unpinned only for artwork

**Files:** `DeliverableDescriptorCatalogTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add unit tests asserting that both artwork descriptors expose a blank `sha256` and a source URL
> ending in the stable file name, and that an OCR/ffmpeg descriptor still carries a 64-character
> `sha256`.

**Why:**

The split of policy by payload type is the whole ticket; a later edit that quietly unpins a native
library would otherwise pass every gate this repo has.

**Verification:**

- `.\a.ps1 fu --tests "*DeliverableDescriptorCatalogTest*"` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [ ] `.\a.ps1 fk` passes.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Descriptors no longer carry a freshness signal; Phase 03 supplies one from the manifest.
