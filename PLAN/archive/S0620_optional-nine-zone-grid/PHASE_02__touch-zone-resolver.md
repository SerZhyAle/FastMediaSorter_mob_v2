# Phase 02 - Touch-zone resolver honours the flag

**Strategic spec:** [`../S0620_optional-nine-zone-grid.md`](../S0620_optional-nine-zone-grid.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

When `nineZoneGridEnabled == false`, the fullscreen resolver returns the existing 3-zone map (REG_3100 for image/GIF, REG_375 for video/audio) instead of the 9-zone map (REG_9100 / REG_975). Default (`true`) is byte-for-byte the current behaviour. The first-run/overlay hint type follows the same flag.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`AppSettings.nineZoneGridEnabled` exists).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TouchZoneGestureManager.kt` | Modified | ≤ 750 |

---

## Steps

### Step 02.1 - Add the flag parameter to the resolver

**Files:** `ui/player/TouchZoneConfig.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend `getZoneMapForMediaType(mediaType, isFullscreen)` with a third parameter `nineZoneGridEnabled: Boolean = true`. When `isFullscreen && !nineZoneGridEnabled`, return the 3-zone map for the media type: `IMAGE`/`GIF` -> `REG_3100`, `VIDEO`/`AUDIO` -> `REG_375`, unknown -> `REG_3100`; `DOC` types stay `REG_DOC`. The `isFullscreen == false` and the default-`true` branches are unchanged. Keep the default value so non-fullscreen call sites need no edit.

**Verification:**

- `Grep` - `nineZoneGridEnabled: Boolean = true` matches once in `TouchZoneConfig.kt`.
- `Grep` - `!nineZoneGridEnabled` matches in the fullscreen branch.

**Status:** `[ ]` not done

---

### Step 02.2 - Pass the flag from the fullscreen call sites

**Files:** `ui/player/helpers/TouchZoneGestureManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> `TouchZoneGestureManager` calls `getZoneMapForMediaType` at five sites (~196, 249, 448, 566, 620). For the two fullscreen-capable sites - line ~448 (`isFullscreen` variable) and ~566 (`isFullscreen = true`) - pass the current `nineZoneGridEnabled` value read from settings (inject/read the same way the manager already reads `alwaysShowTouchZonesOverlay` / player settings). The three `isFullscreen = false` sites may pass the flag too (harmless - it only affects the fullscreen branch) or be left as-is. Do not change the 3-zone command-panel-mode behaviour here.

**Verification:**

- `Grep` - `nineZoneGridEnabled` matches at the ~448 and ~566 call sites in `TouchZoneGestureManager.kt`.

**Status:** `[ ]` not done

---

### Step 02.3 - Hint type follows the flag

**Files:** `ui/player/helpers/TouchZoneGestureManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Where the manager picks the touch-zones hint (`TouchZoneHintType` / the `tooltip_touch_zones_message` 9-zone hint vs the `hint_touch_zone_3zone` 3-zone hint), select the 3-zone hint string when `nineZoneGridEnabled == false` in fullscreen, so the overlay/first-run hint matches the active layout. Reuse the existing `hint_touch_zone_3zone` resource - do not author a new hint string here.

**Verification:**

- `Grep` - `hint_touch_zone_3zone` referenced in `TouchZoneGestureManager.kt` under a `nineZoneGridEnabled`-conditional path.

**Status:** `[ ]` not done

---

### Step 02.4 - Build gate

**Files:** (none - validation only)
**Depends on:** Steps 02.1-02.3

**Prompt for developer:**

> Run `/build` -> `standard debug`. With the flag defaulting to `true`, runtime behaviour is unchanged; the build proves the resolver signature change is consistent across all call sites.

**Verification:**

- `/build` standard debug PASS.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] If public API changed (`TouchZoneConfig` signature): `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Fullscreen now selects a 3-zone map when the grid is off. In that 3-zone fullscreen state there is currently no tap path to the command panel (the 9-zone bottom-left `COMMAND_PANEL` zone is gone) - Phase 03 adds it.

---

## Rollback Plan

Revert phase commit(s) - the resolver parameter is default-`true`; reverting restores the 9-zone-only behaviour with no data impact.
