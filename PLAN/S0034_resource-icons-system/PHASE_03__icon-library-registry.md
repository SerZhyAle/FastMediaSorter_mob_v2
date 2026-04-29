# Phase 03 — Icon Library Registry

**Strategic spec:** [`../S0034_resource-icons-system.md`](../S0034_resource-icons-system.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02; external — designer asset delivery (see Phase 01)
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Embed the 50 vector drawables delivered by the designer, define a typed registry that maps `ico-XX-NNN` → `@DrawableRes`, expose set membership and lookups, and define the predefined-resource → fixed-icon mapping.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] All 50 SVG icons received and accepted in `ICON_INVENTORY.md` (every "received" + "accepted" checkbox ticked).
- [ ] SVGs converted to Android Vector Drawable XML via Android Studio Vector Asset Studio or `vd-tool`.
- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ico_NN_NNN.xml` × 50 | New | ≤ 50 each |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconSet.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconRegistry.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconDefaults.kt` | New | ≤ 150 |

---

## Steps

### Step 03.1 — Convert delivered SVGs to vector drawables

**Files:** `app_v2/src/main/res/drawable/ico_NN_NNN.xml` (50 files)
**Depends on:** designer delivery

**Prompt for developer:**

> For each of the 50 SVGs in the designer's archive, convert to Android Vector Drawable XML preserving the file name (`ico_01_001.xml` .. `ico_05_020.xml`). Place all 50 files under `app_v2/src/main/res/drawable/`. Each XML must declare `android:tint="?attr/colorOnSurface"` at the root so runtime tinting matches the current theme. No PNG rasters — vector only.

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable/ico_01_001.xml` exists.
- `Glob` — `app_v2/src/main/res/drawable/ico_05_020.xml` exists.
- Total of `app_v2/src/main/res/drawable/ico_*.xml` files = 50 (use `Glob` and count).

**Status:** `[ ]` not done

---

### Step 03.2 — Define `ResourceIconSet` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconSet.kt`
**Depends on:** — independent of asset delivery

**Prompt for developer:**

> Create the enum class:
>
> ```kotlin
> enum class ResourceIconSet(val setId: Int, val countInSet: Int) {
>     MUSIC(1, 10),
>     VIDEO(2, 10),
>     IMAGE(3, 10),
>     DOCS(4, 10),
>     OTHER(5, 20);
>
>     companion object {
>         fun fromSetId(setId: Int): ResourceIconSet? =
>             values().firstOrNull { it.setId == setId }
>     }
> }
> ```
>
> No imports needed beyond Kotlin stdlib.

**Verification:**

- `Grep` — `enum class ResourceIconSet` matches once.
- `Grep` — `MUSIC\(1, 10\)` matches once.
- `Grep` — `OTHER\(5, 20\)` matches once.

**Status:** `[ ]` not done

---

### Step 03.3 — Build `ResourceIconRegistry`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconRegistry.kt`
**Depends on:** Steps 03.1, 03.2

**Prompt for developer:**

> Create a singleton object holding a constant `Map<String, Int>` keyed by the `ico-XX-NNN` id and valued by `@DrawableRes Int`. Populate all 50 entries by hand (no reflection, no asset listing). Provide:
>
> ```kotlin
> object ResourceIconRegistry {
>     fun resolveDrawable(iconId: String?): Int?
>     fun idsFor(set: ResourceIconSet): List<String>
>     fun parseId(iconId: String): Pair<ResourceIconSet, Int>?
>     fun isValid(iconId: String): Boolean
>     fun randomIdFor(set: ResourceIconSet, random: kotlin.random.Random = kotlin.random.Random.Default): String
>     fun firstIdFor(set: ResourceIconSet): String
> }
> ```
>
> `parseId` validates the `ico-XX-NNN` regex and returns the set + ordinal; rejects malformed input with `null`. `firstIdFor` always returns `ico-0X-001` for that set — used by predefined resources.

**Verification:**

- `Grep` — `object ResourceIconRegistry` matches once.
- `Grep` — `fun resolveDrawable\(iconId: String\?\): Int\?` matches once.
- `Grep` — `R\.drawable\.ico_05_020` matches at least once (proves all 50 entries are referenced).
- `Grep` — `R\.drawable\.ico_01_001` matches at least once.
- `Grep` — `fun randomIdFor` matches once.
- `Grep` — `fun firstIdFor` matches once.

**Status:** `[ ]` not done

---

### Step 03.4 — Map `ResourceType` → `ResourceIconSet`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconDefaults.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create a stateless helper:
>
> ```kotlin
> object ResourceIconDefaults {
>     fun setForResource(profile: ResourceProfile, type: ResourceType): ResourceIconSet
>     fun fixedIconForVirtualPath(path: String): String?
> }
> ```
>
> Mapping rules for `setForResource`: `AUDIO_LIBRARY` → MUSIC, `VIDEO_LIBRARY` → VIDEO, `PHOTO_STORAGE` → IMAGE, `DOCUMENTS` → DOCS, anything else (`NONE`, `ALL_FILES`) → OTHER.
>
> Mapping rules for `fixedIconForVirtualPath` — predefined resources use the first id of their set so the icon is consistent across devices:
>
> - `LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO` → `ico-01-001`
> - `LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO` → `ico-02-001`
> - `LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES` → `ico-03-001`
> - `LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS` → `ico-04-001`
> - `LocalMediaScanner.VIRTUAL_PATH_RECENT` → `ico-05-001`
> - `LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS` → `ico-03-002`
> - any other path → null (caller falls back to random assignment).

**Verification:**

- `Grep` — `object ResourceIconDefaults` matches once.
- `Grep` — `fun setForResource\(profile: ResourceProfile, type: ResourceType\): ResourceIconSet` matches once.
- `Grep` — `fun fixedIconForVirtualPath\(path: String\): String\?` matches once.
- `Grep` — `ico-01-001` matches once (audio fixed).
- `Grep` — `ico-04-001` matches once (docs fixed).

**Status:** `[ ]` not done

---

### Step 03.5 — Compile gate

**Files:** —
**Depends on:** Steps 03.1..03.4

**Prompt for developer:**

> Trigger `/build` (standard debug). Verify all 50 vector drawables compile (lint may warn about path complexity — record but do not gate). Confirm registry resolves a sample id round-trip in a unit test or scratch debug log (delete the scratch line before commit).

**Verification:**

- `/build standard debug` exits with status PASS.
- `Grep -n "Log\.d\("` returns zero hits across files modified in this phase.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the new icon registry files via `.\scripts\add_to_dev_log.ps1`. (Vector drawables can be batched in a single log entry: target = `drawables`, description = "Add 50 resource icon vector drawables".)
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — three new public objects added.

---

## Handoff Notes to Next Phase

`ResourceIconRegistry.resolveDrawable(iconId)` is the single read path for any code that needs to display a themed icon. Phase 04 wraps it in composite rendering; Phase 05 uses `randomIdFor` + `fixedIconForVirtualPath`; Phase 06 uses `idsFor(set)` to populate the selector grid.

---

## Rollback Plan

Revert phase commit(s). Drawables and registry classes are additive — no consumer references them yet. Safe rollback at any time before Phase 07 lands.
