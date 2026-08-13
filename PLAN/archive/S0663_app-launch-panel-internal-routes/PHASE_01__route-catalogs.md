# Phase 01 - Route catalogs & availability

**Strategic spec:** [`../S0663_app-launch-panel-internal-routes.md`](../S0663_app-launch-panel-internal-routes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Introduce the internal-route registry (our features + resource), the OS-target catalog, the `targetId` namespace parser, and a single availability resolver. No launch wiring, no UI changes yet.

---

## Prerequisites

- [ ] Strategic §6.3 (OS-target list) resolved - it fixes the OS catalog content.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/panel/AppLaunchPanelRouteTarget.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/AppLaunchPanelRouteIntents.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/InternalRouteCatalog.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/OsShortcutCatalog.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 - Add the targetId namespace model

**Files:** `domain/model/panel/AppLaunchPanelRouteTarget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a sealed model `AppLaunchPanelRouteTarget` with variants `Feature(routeKey: String)`, `Resource(resourceId: Long)`, `OsShortcut(targetKey: String)`. Add `encode(): String` producing `fn:<key>` / `resource:<id>` / `os:<key>`, and `decode(targetId: String?): AppLaunchPanelRouteTarget?` tolerant of unknown/malformed input (returns null). This is the single parser/serializer for the `INTERNAL_ROUTE` tile `targetId`.

**Verification:**

- `Glob` - `domain/model/panel/AppLaunchPanelRouteTarget.kt` exists.
- `Grep` - `sealed (class|interface) AppLaunchPanelRouteTarget` matches once.
- `Grep` - `fun decode(` and `fun encode(` both present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: domain/model/panel/AppLaunchPanelRouteTarget.kt (+57 LOC, new sealed interface + encode/decode).

---

### Step 01.2 - Add per-feature launch-intent builders

**Files:** `core/panel/AppLaunchPanelRouteIntents.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `object AppLaunchPanelRouteIntents` with one builder per feature route returning an `Intent`: calculator (`CalculatorActivity`), game (reuse `GameLaunchIntents.game`), OCR (`CameraOcrTranslateActivity`), streams (`StreamsActivity`), favorites (Main with `open_favorites=true`), and resource (`BrowseActivity.createIntent(context, resourceId)`). Reuse existing activities/intents - do not duplicate navigation. Add `FLAG_ACTIVITY_NEW_TASK` consistently with the existing panel launch path.

**Verification:**

- `Glob` - `core/panel/AppLaunchPanelRouteIntents.kt` exists.
- `Grep` - `GameLaunchIntents` referenced (game reused, not re-created).
- `Grep` - `BrowseActivity` referenced (resource open reused).
- `Grep -n "Log\.d\("` on this file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 4/4 PASS. Files: core/panel/AppLaunchPanelRouteIntents.kt (+44 LOC, reuses widget entry points; GameLaunchIntents + BrowseActivity reused; no Log.d).

---

### Step 01.3 - Add the internal-route catalog

**Files:** `core/panel/InternalRouteCatalog.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> Create `InternalRouteCatalog` listing the feature routes, each with: route key, label string-res id, icon drawable-res id (reuse the resources the widgets already use), and a reference to its intent builder in `AppLaunchPanelRouteIntents`. Expose `all(): List<...>` and `byKey(key: String)`. No availability logic here (that is Step 01.5) - this is a static descriptor table only.

**Verification:**

- `Glob` - `core/panel/InternalRouteCatalog.kt` exists.
- `Grep` - `class InternalRouteCatalog` matches once.
- `Grep` - `"calculator"`, `"game"`, `"ocr"`, `"streams"`, `"favorites"` all present as route keys.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS (declared once as `object`, a static singleton table consistent with `AppLaunchPanelRouteIntents`; no DI). Files: core/panel/InternalRouteCatalog.kt (+74 LOC). Also added feature/OS label strings (EN/RU/UK) that the catalog references so Phase 01 compiles; chooser/category strings remain for Step 03.1.

---

### Step 01.4 - Add the OS-shortcut catalog with device-resolvability filter

**Files:** `core/panel/OsShortcutCatalog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `OsShortcutCatalog` listing the curated OS targets fixed by §6.3 (default: Settings, Wi-Fi, Bluetooth, Display, Sound, Battery, Storage, App info, Date/time), each with target key, label string-res id, icon drawable-res id and a system `Intent` factory. Add `available(context): List<...>` that returns only targets whose intent resolves on the device (use `resolveActivityCompat`/`queryIntentActivitiesCompat`). Run resolution off the main thread at the call site.

**Verification:**

- `Glob` - `core/panel/OsShortcutCatalog.kt` exists.
- `Grep` - `class OsShortcutCatalog` matches once.
- `Grep` - `resolveActivityCompat|queryIntentActivitiesCompat` present (resolvability filter).
- `Grep` - `Settings.ACTION_SETTINGS` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 4/4 PASS (declared once as `object`). Files: core/panel/OsShortcutCatalog.kt (+84 LOC, 9 curated targets, `available()` filters by `resolveActivityCompat`).

---

### Step 01.5 - Add the feature-route availability resolver

**Files:** `domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create `ResolvePanelRouteAvailabilityUseCase` answering, per feature route key, two booleans: compiled/available-in-build and enabled-at-runtime. Source compile/runtime availability from the existing single sources of truth only - `CapabilityAvailability` (streams via `isStreamsAvailable`, OCR via `isOcrAvailable`) and `SettingsRepository` (`embeddedGameEnabled` for the game). Calculator, resource and favorites are always available (favorites subject to its existing feature flag). Do NOT read `BuildConfig.*` here (CLAUDE.md Rule 15) - go through `CapabilityAvailability`.

**Verification:**

- `Glob` - `domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt` exists.
- `Grep` - `CapabilityAvailability` referenced.
- `Grep -n "BuildConfig\."` on this file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt (+63 LOC, sources CapabilityAvailability + SettingsRepository; no BuildConfig).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (compile-only, new classes).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [~] Dev log entry added for every file in "Files Touched" - batched at Phase 05 (one entry per logical change/ticket, CLAUDE.md §12).
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - batched at Phase 05 Step 05.3 (catalog_sync once per ticket).

---

## Handoff Notes to Next Phase

The namespace parser (`AppLaunchPanelRouteTarget`), both catalogs and the availability resolver exist and compile. Launch dispatch (Phase 02) and the editor (Phase 03) consume them. Storage type stays `INTERNAL_ROUTE`; nothing persists a new enum value.

---

## Rollback Plan

Revert the phase commit(s) - all files are new and unreferenced until Phase 02; no data migration or user-facing surface changed.
