# Phase 03 - Dispatcher trampoline activity

**Strategic spec:** [`../S0380_split-standalone-player.md`](../S0380_split-standalone-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (Phase 01 skipped - foundation already in code)
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-07
**Completed:** 2026-06-08

> **Ordering correction (2026-06-07):** Step 03.2 (dispatcher activity) forwards to the specialized activities created in Phase 04, so it must run **after** Phase 04. Step 03.1 (`MediaFamilyResolver`) is independent and is done. Sequence on resume: Phase 02 (gap-check) → Phase 04 (specialized activities) → Step 03.2 (dispatcher) → Phase 05.
>
> **Build-validation paused:** the branch currently does not compile due to unrelated uncommitted WIP (`ui/player/FileOperationsHandler.kt:81`, missing `import android.net.Uri`). Owner will finish that WIP; re-validate Phase 03 once the branch compiles.

---

## Objective

Add a no-UI dispatcher activity that accepts intents with no MIME type or a generic `*/*`, resolves the real media family, and forwards to the matching specialized activity - without loading any heavy media SDK.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/dispatch/StandalonePlayerDispatcherActivity.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/dispatch/MediaFamilyResolver.kt` | New | ≤ 160 |

> No layout file - the dispatcher uses a `NoDisplay`/transparent theme and never calls `setContentView`.

---

## Steps

### Step 03.1 - Media family resolver

**Files:** `ui/player/dispatch/MediaFamilyResolver.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MediaFamilyResolver` that maps an intent (URI + optional MIME) to one of: `PHOTO_VIDEO`, `AUDIO`, `DOCUMENT`, `TEXT`. Resolution order: explicit MIME → `ContentResolver.getType` → file signature/extension. Pure logic, unit-testable, no Android UI and no heavy SDK imports (no ExoPlayer/Glide/PDF/WebView).

**Verification:**

- `Glob` - `MediaFamilyResolver.kt` exists.
- `Grep` - `fun resolve` present; enum/sealed result covering `PHOTO_VIDEO`, `AUDIO`, `DOCUMENT`, `TEXT`.
- `Grep` - no `import` of `exoplayer`, `glide`, `pdf`, or `webkit` in this file (`expected: 0 | actual: 0`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS (Glob: file exists; Grep: `fun resolve` + enum PHOTO_VIDEO/AUDIO/DOCUMENT/TEXT; `^import` heavy-SDK count expected 0 / actual 0). Reuses `MediaTypeUtils.getMediaTypeFromMimeOrExtension` - no duplicated detection. Files: ui/player/dispatch/MediaFamilyResolver.kt (+50 LOC, New).
- 2026-06-07 - Build note: first build caught a real KDoc bug (`*/` glob inside the block comment closed it early) - fixed. Second build: `MediaFamilyResolver` passed `kaptGenerateStubs` + `kaptStandardDebugKotlin` with zero errors against it; `compileStandardDebugKotlin` then failed in an UNRELATED pre-existing WIP file `ui/player/FileOperationsHandler.kt:81` (`Unresolved reference 'Uri'` - missing `import android.net.Uri`, file is uncommitted WIP per git status). Full-build validation of Phase 03 is BLOCKED by that pre-existing branch breakage, not by this step's code. Resolver itself is correct.
- 2026-06-07 - Build-validated: owner fixed the WIP, `assembleStandardDebug` BUILD SUCCESSFUL in 59s with `MediaFamilyResolver` compiled in `compileStandardDebugKotlin` (`expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL`, v2.60.6071.405). Step 03.1 fully validated.

---

### Step 03.2 - Dispatcher activity

**Files:** `ui/player/dispatch/StandalonePlayerDispatcherActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `StandalonePlayerDispatcherActivity` (no UI). In `onCreate`, resolve the family via `MediaFamilyResolver`, build an explicit intent for the matching specialized activity (carry over data, flags, and grant permissions), `startActivity`, then `finish()`. If resolution fails, fall back to the document/text activity and log at `Timber.w` in plain English (no ticket id in permanent logs).

**Verification:**

- `Glob` - `StandalonePlayerDispatcherActivity.kt` exists.
- `Grep` - `class StandalonePlayerDispatcherActivity` once; `setContentView` absent; `finish()` present.
- `Grep` - `MediaFamilyResolver` referenced.
- Build: `/build` standardDebug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 4/4 PASS. Glob: `StandalonePlayerDispatcherActivity.kt` exists. Grep: `class StandalonePlayerDispatcherActivity` (`expected: 1 | actual: 1`); `setContentView` call (`expected: 0 | actual: 0`); `finish()` (`expected: >=1 | actual: 2`); `MediaFamilyResolver` referenced (`expected: >=1 | actual: 1`). Build: standardDebug `BUILD SUCCESSFUL in 54s`. Implementation: no-UI `Activity` (no Hilt, no layout); extracts URI (VIEW data / SEND EXTRA_STREAM), resolves MIME via intent.type/ContentResolver + display-name, `MediaFamilyResolver.resolve` → forwards a copied intent (action/data/extras/flags preserved) with FLAG_GRANT_READ_URI_PERMISSION to the matching specialized activity, then finish(). Unresolved/binary types route to the lightest text surface (shows the explicit unsupported-format message) and log at Timber.w in plain English (no ticket id). No heavy-SDK imports (expected 0 | actual 0). Run after Phase 04 per the ordering correction (forwards to the now-existing specialized activities). Files: ui/player/dispatch/StandalonePlayerDispatcherActivity.kt. Manifest registration is Phase 05.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - run `/build` (standardDebug).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Dispatcher + resolver exist but are not yet wired into the manifest. Phase 05 registers the dispatcher for `*/*` / typeless intents and the specialized activities it forwards to.

---

## Rollback Plan

Revert phase commit(s). Both files are additive and unreferenced by the manifest until Phase 05 - reverting has zero runtime effect.
