# Phase 02 - Pin-shortcut creation helper

**Strategic spec:** [`../S0637_stream-channel-shortcut.md`](../S0637_stream-channel-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Introduce `StreamShortcutPinManager` that builds a pinned home-screen shortcut for one stream source (label + type icon + the Phase 01 play intent) and requests the launcher to pin it, degrading cleanly when pinning is unsupported. No UI trigger yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (intent contract `createPlayShortcutIntent` + constants exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamShortcutPinManager.kt` | New | ≤ 120 |

> `ShortcutManagerCompat` / `IconCompat` are AndroidX - they compile on legacy minSdk 23; `isRequestPinShortcutSupported` returns false below API 26, handled in Step 02.1.

---

## Steps

### Step 02.1 - Create the manager with a support guard

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamShortcutPinManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class StreamShortcutPinManager(private val context: Context)`. Add `fun requestPin(source: StreamSourceEntity): Boolean` that returns false immediately when `ShortcutManagerCompat.isRequestPinShortcutSupported(context)` is false (caller shows the unsupported toast), otherwise proceeds to Step 02.2 and returns true. No Hilt binding needed - instantiate it with the Activity context at the call site (Phase 03). Keep all logic here, none in the Activity (Rule 3).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamShortcutPinManager.kt` exists.
- `Grep` - `class StreamShortcutPinManager` matches once.
- `Grep` - `isRequestPinShortcutSupported` present.
- `Grep` - `fun requestPin` returns `Boolean`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Verification 4/4 PASS. Files: ui/streams/helpers/StreamShortcutPinManager.kt (New, guard-only). Dev log batched to finalization.

---

### Step 02.2 - Build and request the pinned shortcut

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamShortcutPinManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Build a `ShortcutInfoCompat.Builder(context, "stream_${source.id}")` with `setShortLabel(source.title)`, `setLongLabel(source.title)`, `setIcon(IconCompat.createWithResource(context, iconFor(source.mediaKind)))`, and `setIntent(StreamsActivity.createPlayShortcutIntent(context, source.url))`, then call `ShortcutManagerCompat.requestPinShortcut(context, info, null)`. Add a private `iconFor(mediaKind: String): Int` returning `R.drawable.ic_audio` for `"AUDIO"` else `R.drawable.ic_video` - mirroring `StreamSourceAdapter.kindIcon` so the shortcut icon matches the row. The stable per-channel id `stream_${source.id}` keeps multiple channel shortcuts independent (strategic §11.4).

**Verification:**

- `Grep` - `ShortcutInfoCompat.Builder` present.
- `Grep` - `requestPinShortcut` present.
- `Grep` - `createPlayShortcutIntent` referenced (reuses Phase 01 factory, no hand-built intent).
- `Grep` - `IconCompat.createWithResource` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Verification 4/4 PASS. Files: ui/streams/helpers/StreamShortcutPinManager.kt (+11 LOC: ShortcutInfoCompat build, requestPinShortcut, iconFor). Dev log batched to finalization.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` in the new file returns zero hits (Timber only).
- [ ] Dev log entry added for the new file.
- [ ] `dev/CATALOG/app_v2.jsonl` regeneration deferred to Phase 04 (single catalog regen per ticket).

---

## Handoff Notes to Next Phase

- `StreamShortcutPinManager(context).requestPin(source)` returns `Boolean`: `true` = pin dialog requested, `false` = launcher unsupported. Phase 03 maps the result to a "created" vs "unsupported" toast.
- The manager has no Hilt binding - Phase 03 constructs it directly with the Activity context.

---

## Rollback Plan

Revert phase commit - the new file has no references yet (Phase 03 introduces them), so removal is non-breaking.
