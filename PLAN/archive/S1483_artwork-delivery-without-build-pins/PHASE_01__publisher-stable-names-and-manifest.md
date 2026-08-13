# Phase 01 - Publisher: stable asset names + artwork manifest

**Strategic spec:** [`../S1483_artwork-delivery-without-build-pins.md`](../S1483_artwork-delivery-without-build-pins.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 0 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Publish the two artwork tile packs and their sidecars under stable names, and publish an
`artwork-manifest.json` beside them that states what is currently live.

---

## Prerequisites

- [ ] `gh` CLI authenticated (`gh auth status`).
- [ ] Tile packs already cut (`-WithTilePacks`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | ≤ 120 added |
| `delivery/stream-catalog/README.md` | Modified | ≤ 60 added |

---

## Steps

### Step 01.1 - Publish the app-fetched payloads under stable names

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> In `Invoke-PublishTilePacks`, upload the packs and their coords sidecars under names with no
> revision suffix: `channel-preview-tiles.zip`, `channel-preview-coords.json`,
> `stream-logo-tiles.zip`, `stream-logo-coords.json`. Keep uploading the revisioned sprite sheets as
> they are today - those serve third-party consumers, not the app.

**Why:**

A revision in the asset name is what forces a rebuilt payload to land at a URL no installed app knows
about, so the rebuild reaches nobody until a new app version ships - the failure the owner hit on
2026-08-07.

**Verification:**

- `gh release view delivery-so-v1 --json assets` lists `stream-logo-tiles.zip` with no `-vN` suffix.
- Previous `-vN` assets still present (never deleted - older builds still pin them).

**Status:** `[x]` done

---

### Step 01.2 - Write and publish `artwork-manifest.json`

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `Write-ArtworkManifest`, producing `artwork-manifest.json` with a `schemaVersion` of 1, a
> `generatedAt` ISO-8601 stamp taken from the publish run, and one entry per set (`channelPreview`,
> `streamLogo`) carrying each published file's name, byte size and SHA-256. Upload it with the packs.

**Why:**

The app needs a freshness signal it does not compile into itself; a manifest published by the same
run that uploads the payload is a contract this repo controls, unlike the object-store `ETag` a
GitHub asset redirect returns.

**Verification:**

- `Invoke-WebRequest` on the published manifest URL returns HTTP 200 and parses as JSON.
- The `size` values in the manifest equal the byte sizes of the uploaded assets.

**Status:** `[x]` done

---

### Step 01.3 - Document the manifest contract

**Files:** `delivery/stream-catalog/README.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Document the manifest in the tile-pack section: its URL, its schema, that consumers treat an absent
> or unparseable manifest as "nothing new", and that the stable-named packs are what the app fetches
> while the `-vN` sheets remain for third parties.

**Why:**

The manifest becomes a published artifact other consumers may read, and an undocumented delivery
contract is how the atlas shipped broken twice before (S0925, S1200).

**Verification:**

- `Grep` - `artwork-manifest.json` present in `delivery/stream-catalog/README.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [ ] Manifest and stable-named payloads are live on the release.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

The stable names and the manifest URL are the contract Phase 02 and Phase 03 code against.
