# Phase 04 — Composite Rendering

**Strategic spec:** [`../S0034_resource-icons-system.md`](../S0034_resource-icons-system.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 06, Phase 07
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Build a single rendering helper that composes a `LayerDrawable` from (a) a transparent background carrying a small connection-type indicator in the top-left corner and (b) a centred themed icon scaled smaller than the background. Returns a drawable any `ImageView` can consume.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.
- [ ] Existing connection-type icons (`ic_resource_local`, `ic_resource_smb`, `ic_resource_sftp`, `ic_resource_ftp`, `ic_resource_cloud`, dropbox / drive / onedrive variants) confirmed present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconComposer.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ConnectionBadgeMapper.kt` | New | ≤ 120 |
| `app_v2/src/main/res/values/dimens.xml` | Modified | — |

---

## Steps

### Step 04.1 — Define composite dimensions

**Files:** `app_v2/src/main/res/values/dimens.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Append three dimension resources:
>
> ```xml
> <dimen name="resource_icon_composite_size">48dp</dimen>
> <dimen name="resource_icon_badge_size">18dp</dimen>
> <dimen name="resource_icon_theme_inset">8dp</dimen>
> ```
>
> `composite_size` is the canvas; `badge_size` is the corner indicator; `theme_inset` is the inset applied to the themed icon so it sits centred and smaller than the canvas.

**Verification:**

- `Grep` — `resource_icon_composite_size` matches once.
- `Grep` — `resource_icon_badge_size` matches once.
- `Grep` — `resource_icon_theme_inset` matches once.

**Status:** `[ ]` not done

---

### Step 04.2 — Create `ConnectionBadgeMapper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ConnectionBadgeMapper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Stateless object exposing:
>
> ```kotlin
> object ConnectionBadgeMapper {
>     @DrawableRes
>     fun badgeFor(resource: MediaResource): Int?
> }
> ```
>
> Replicate the existing connection-type → drawable mapping from `ResourceAdapter.bind` (LOCAL, SMB, SFTP, FTP, CLOUD with provider variants, plus virtual-path overrides for ALL_AUDIO/ALL_VIDEO/ALL_DOCS/RECENT/favorites). For purely local non-virtual resources, return `null` so the composer renders no badge (strategic spec: "подложка пустая для локального"). Do not introduce new drawables — reuse what `ResourceAdapter.kt` already references.

**Verification:**

- `Grep` — `object ConnectionBadgeMapper` matches once.
- `Grep` — `fun badgeFor\(resource: MediaResource\): Int\?` matches once.
- `Grep` — `R\.drawable\.ic_resource_smb` matches in `ConnectionBadgeMapper.kt`.

**Status:** `[ ]` not done

---

### Step 04.3 — Create `ResourceIconComposer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconComposer.kt`
**Depends on:** Steps 04.1, 04.2, Phase 03

**Prompt for developer:**

> Provide a single entry point:
>
> ```kotlin
> object ResourceIconComposer {
>     fun compose(context: Context, resource: MediaResource): Drawable
> }
> ```
>
> Resolution order inside `compose`:
>
> 1. `themeDrawable` = `ResourceIconRegistry.resolveDrawable(resource.iconId)` → if null, fall back to `ResourceIconRegistry.resolveDrawable(ResourceIconDefaults.firstIdFor(ResourceIconDefaults.setForResource(resource.profile, resource.type)))`. This guarantees a non-null icon at all times.
> 2. `badgeDrawable` = `ConnectionBadgeMapper.badgeFor(resource)` → may be null.
> 3. Build a `LayerDrawable` of size `R.dimen.resource_icon_composite_size`. Bottom layer: themed icon, inset on all sides by `R.dimen.resource_icon_theme_inset`, centred. Top layer (only if badge exists): badge of size `R.dimen.resource_icon_badge_size` aligned to top-left corner via `setLayerInsetTop(0, 0)` + `setLayerInsetLeft(1, 0)` (use index-based `setLayerInset*` APIs).
> 4. Apply theme tint via `DrawableCompat.setTint` only to the themed icon — badge keeps its own colour.
>
> No caching layer in this phase. Caching is a Phase 07 concern if profiling shows recycler thrash.

**Verification:**

- `Grep` — `object ResourceIconComposer` matches once.
- `Grep` — `fun compose\(context: Context, resource: MediaResource\): Drawable` matches once.
- `Grep` — `LayerDrawable` matches at least once in `ResourceIconComposer.kt`.
- `Grep` — `ResourceIconRegistry\.resolveDrawable` matches at least twice (primary + fallback).

**Status:** `[ ]` not done

---

### Step 04.4 — Compile gate

**Files:** —
**Depends on:** Steps 04.1..04.3

**Prompt for developer:**

> Trigger `/build` (standard debug). Verify the new package compiles. No call sites yet — Phase 07 wires it into the adapter.

**Verification:**

- `/build standard debug` exits with status PASS.
- `Grep -n "Log\.d\("` returns zero hits across files modified in this phase.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — two new public objects added.

---

## Handoff Notes to Next Phase

`ResourceIconComposer.compose(context, resource)` is the only render entry point downstream code should call. Phase 06 (selector) uses `ResourceIconRegistry.resolveDrawable` directly to render solo previews — composite (with badge) is reserved for the main resource list.

---

## Rollback Plan

Revert phase commit(s). No runtime consumers yet; rollback is risk-free.
