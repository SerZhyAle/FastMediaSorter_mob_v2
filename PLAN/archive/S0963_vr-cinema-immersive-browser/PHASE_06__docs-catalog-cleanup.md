# Phase 06 - Docs, catalog, finalization

**Strategic spec:** [`../S0963_vr-cinema-immersive-browser.md`](../S0963_vr-cinema-immersive-browser.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog for the new `src/vr` classes with correct flavor hints, record the delivered capability, insert/confirm the single `S0963:` device probe, and hand the spec to `BlockNeedUserTest` for on-device Quest 3 verification.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done and compiling (`.\a.ps1 fkn` + `.\a.ps1 fc`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (regen, gitignored) | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Appended (via `add.ps1`) | n/a |

> No source edits in this phase except confirming the probe. `docs/FEATURES*.md` is NOT touched (owned by `/skill-release`).

---

## Steps

### Step 06.1 - Catalog regen + flavor hints

**Files:** `dev/CATALOG/app_v2.jsonl` (regen)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. For each new `src/vr`-only class (`ImmersiveBrowseActivity`, `ImmersiveBrowseGridRenderer`, `ImmersiveBrowseInteractionDispatcher`, `ImmersiveThumbnailDecoder`, `ImmersiveBrowseCell`, `ImmersiveBrowseContentLoader`, `ImmersiveBrowsePlaybackController`) set `role` + `status` and declare flavor isolation via `set.ps1 -NoFlavors "standard,lite,photos,legacy"` (VR classes absent from non-VR flavors).

**Verification:**

- `Grep` - `ImmersiveBrowseActivity` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

### Step 06.2 - Record delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - independent

**Prompt for developer:**

> Append one capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only) describing the in-headset VR Cinema file browser: navigate a resource in immersive, see video + 3D images with thumbnails, pick an item to play in-headset, plus the resource ⋮ "Open in VR Cinema" entry. `spec` field = `S0963`. Note VR-capable builds only.

**Verification:**

- `Grep` - `S0963` present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done

---

### Step 06.3 - Confirm single device probe

**Files:** `ui/xr/ImmersiveBrowseActivity.kt` (confirm only)
**Depends on:** Phase 03

**Prompt for developer:**

> Confirm exactly one `Timber.d("S0963: ..")` probe exists at the immersive-browse entry (Phase 03 step 03.3) and no other `S0963:` probe leaked into any other file. This is the sole BlockNeedUserTest tag; it is removed by `/spec-check` when the ticket leaves `BlockNeedUserTest`.

**Verification:**

- `Grep -rn "S0963:" app_v2/src` returns exactly one hit (in `ImmersiveBrowseActivity.kt`).

**Status:** `[x]` done

---

### Step 06.4 - Dev log + status to BlockNeedUserTest

**Files:** `dev/CHANGELOG.md` (via script), journal
**Depends on:** Step 06.1, Step 06.2, Step 06.3

**Prompt for developer:**

> Add dev-log entries for all touched source files (batch via `close-and-log.ps1 -DevLogs`). Set the journal status: `update.ps1 -Id S0963 -Status BlockNeedUserTest -StatusNote '<on-device Quest 3 (noLegal): resource ⋮ -> Open in VR Cinema -> immersive BROWSE grid shows the resource files/3D-images with thumbnails; hover+click selects; media plays in-headset; folder cells drill down; back returns to grid then exits. Toggle VR-3D off -> item hidden. Probe: adb logcat S0963:>'`.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0963 -Format json` shows `"status":"BlockNeedUserTest"`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; new VR classes present with flavor hints.
- [ ] `docs/ALL_FEATURES.jsonl` has an S0963 record.
- [ ] Exactly one `S0963:` probe in the codebase.
- [ ] Journal status = `BlockNeedUserTest` with a device-test note.

---

## Handoff Notes to Next Phase

Final phase. On-device verification via `/spec-test-device S0963` on a Quest 3, then `/spec-check S0963` converts evidence to `Verified` / `Partial` / `Broken` and removes the probe. See INDEX.md Completion Gate.

---

## Rollback Plan

No source rollback specific to this phase - catalog/features/dev-log are additive metadata.
