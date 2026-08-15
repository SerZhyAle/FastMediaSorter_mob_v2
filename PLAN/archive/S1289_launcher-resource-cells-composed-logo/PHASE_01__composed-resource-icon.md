# Phase 01 - Composed resource icon behind a domain-owned seam

**Strategic spec:** [`../S1289_launcher-resource-cells-composed-logo.md`](../S1289_launcher-resource-cells-composed-logo.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 5 / 6 (01.5 deferred - see S1425)
**Started:** -
**Completed:** -

---

## Objective

Introduce a domain-owned resource-icon seam, implement it over the existing composer in the UI layer, bind it in Hilt, and make the launcher command resolver return the composed drawable with a stable identity key.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - all four are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/icon/ResourceIconProvider.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconProviderImpl.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/ResourceIconModule.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 250 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/icon/ResourceIconKeyTest.kt` | New | ≤ 120 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). Every file here is well under 500 LOC.
>
> **Flavor placement.** No flavor-only file in this phase. The resolver and the composer both live in `src/main` and compile for every flavor; the launcher surface that consumes them is already gated by the `launcherEnabled` / `launcherDisabled` source sets, so no `BuildConfig` guard and no flavor source set is introduced here.

---

## Steps

### Step 01.1 - Declare the resource-icon seam in the domain layer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/icon/ResourceIconProvider.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the domain-side contract. Declare `data class ResourceIcon(val drawable: Drawable, val key: String)` and `interface ResourceIconProvider { fun iconFor(resource: MediaResource): ResourceIcon }`. Keep the file free of any `com.sza.fastmediasorter.ui.*` import. KDoc on `key` states the contract the implementation must honour: the key changes if and only if the composed image changes, and it exists because `Drawable` compares by identity.

**Why:**

Strategic ADR-1 requires the domain resolver to reach the composed logo through a role it owns, because a direct import from `domain` to `ui` reverses the dependency direction the architecture fixes, and §2 goal 5 makes that boundary an explicit goal of this ticket.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/icon/ResourceIconProvider.kt` exists.
- `Grep` - `interface ResourceIconProvider` matches exactly once in that file.
- `Grep` - `fun iconFor(resource: MediaResource): ResourceIcon` present.
- `Grep` - `com.sza.fastmediasorter.ui.` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.2 - Implement the seam over the existing composer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconProviderImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Implement `ResourceIconProvider` as `@Singleton class ResourceIconProviderImpl @Inject constructor(@ApplicationContext private val context: Context)`. `iconFor` returns `ResourceIcon(ResourceIconComposer.compose(context, resource), resourceIconKey(resource))`. Add a `@VisibleForTesting` top-level or companion function `resourceIconKey(resource: MediaResource): String` in the same file that joins, with a separator that cannot occur in the values, exactly the fields `ResourceIconComposer` reads: `iconId`, `storageVolumeId`, `profile`, `type`, `cloudProvider?.name`, `path`, `id`. Do not reimplement any icon-selection branch - the composer stays the single source of that logic.

**Why:**

Strategic ADR-2 makes the key part of the seam's contract rather than an afterthought, and §7 records that populating the drawable without a matching key is what tears down the desktop on every emission; the key must therefore cover exactly the inputs the composer branches on.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconProviderImpl.kt` exists.
- `Grep` - `class ResourceIconProviderImpl` matches exactly once and the declaration carries `: ResourceIconProvider`.
- `Grep` - `ResourceIconComposer.compose(` present exactly once in that file.
- `Grep` - `fun resourceIconKey(` present.
- `Grep` - each of `iconId`, `storageVolumeId`, `profile`, `cloudProvider`, `storageVolumeId` appears inside the key function body.

**Status:** `[x]` done

---

### Step 01.3 - Bind the seam in Hilt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/ResourceIconModule.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create the Hilt module `ResourceIconModule`: `@Module @InstallIn(SingletonComponent::class) abstract class ResourceIconModule` with a single `@Binds @Singleton abstract fun bindResourceIconProvider(impl: ResourceIconProviderImpl): ResourceIconProvider`. Follow the shape of the sibling `LauncherDesktopModule` in the same package. Introduce no new scope and no new qualifier.

**Why:**

Strategic §5 puts the wiring of the role to its implementation in the dependency container, which is the only place that can satisfy the domain resolver without the domain naming the UI class.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/di/ResourceIconModule.kt` exists.
- `Grep` - `@InstallIn(SingletonComponent::class)` present in that file.
- `Grep` - `bindResourceIconProvider` present exactly once.
- `Grep` - `@Scope` and `@Qualifier` return zero hits in that file.

**Status:** `[x]` done

---

### Step 01.4 - Resolve resource tiles through the seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add `private val resourceIconProvider: ResourceIconProvider` to the constructor. Rewrite `resourceVisual` to build `LauncherCommandVisual(label = resource.name, iconRes = null, iconDrawable = icon.drawable, iconKey = icon.key)` from `resourceIconProvider.iconFor(resource)`. Remove the now-unused `ResourceTypeIconMap` import if no other branch in the file uses it. Update the class KDoc where it states that `iconDrawable` carries an installed app's own icon, since a resource tile now uses the same field.

**Why:**

This is the change the ticket exists for: strategic §1 records that the tile picks its glyph by resource type alone, and §2 goals 1 to 3 require the desktop and the taskbar to show the composed logo, which both reach through this one resolver.

**Verification:**

- `Grep` - `resourceIconProvider` present in the constructor parameter list.
- `Grep` - `ResourceTypeIconMap.iconFor(resource.type)` returns zero hits in that file.
- `Grep` - `iconKey = icon.key` present inside `resourceVisual`.
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 01.5 - Cover the key formula with a unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/icon/ResourceIconKeyTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Write a JUnit test over `resourceIconKey` with no Android context. Cover: two resources differing only in `iconId` produce different keys; two resources differing only in `storageVolumeId` produce different keys; two resources differing only in `cloudProvider` produce different keys; two identical resources produce equal keys; a resource differing only in a field the composer ignores, such as `lastAccessedDate` or `fileCount`, produces an equal key. Build each `MediaResource` with the three required arguments `name`, `path`, `type` and override only the field under test.

**Why:**

Strategic §7 records that the whole chain has no test coverage today and names the key formula the hurdle worth covering, because it is the one piece that is both purely computable and able to break desktop rendering silently when it stops tracking the composer's inputs.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/icon/ResourceIconKeyTest.kt` exists.
- `Grep` - at least five `@Test` annotations in that file.
- `.\a.ps1 fu` - `ResourceIconKeyTest` reports zero failures.

**Status:** `[DEFERRED]` - test written and compiling, but the unit-test tier cannot run on this machine: org.gradle.java.home points at a JBR whose lib/jvm.cfg is missing, so every forked test JVM dies before the suite starts. Parked as S1425; re-run this step's verification once that is fixed.

---

### Step 01.6 - Carry the icon identity into the taskbar strip

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTaskbarIconAdapter.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Open `LauncherCommandVisual.iconKey` for reading - it stays out of the constructor's public surface only by being `private`, and the taskbar now needs it. Add `val iconKey: String? = null` to `LauncherTaskbarIcon`, populate it from `visual.iconKey` at both build sites in `LauncherHomeViewModel` (`recentIcons` and `pinnedIcons`), and add `oldItem.icon.iconKey == newItem.icon.iconKey` to the adapter's `areContentsTheSame`. Keep the existing `iconRes` comparison - it still carries identity for the flat-drawable commands.

**Why:**

Strategic §2 goal 3 requires the taskbar to show the same composed logo as the desktop, and after step 01.4 a resource row's `iconRes` is always null, so the strip's `areContentsTheSame` would report two different logos as identical and never rebind - the user would reassign a resource icon and see the old one until something else rebuilt the strip.

**Verification:**

- `Grep` - `private val iconKey` returns zero hits in `ResolveLauncherCommandLabelUseCase.kt`.
- `Grep` - `val iconKey` present in the `LauncherTaskbarIcon` declaration.
- `Grep` - `iconKey = visual.iconKey` and `iconKey = entry.visual.iconKey` both present in `LauncherHomeViewModel.kt`.
- `Grep` - `oldItem.icon.iconKey == newItem.icon.iconKey` present in `LauncherTaskbarIconAdapter.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` - this phase adds public types.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The launcher command resolver no longer names any icon table. Any future surface that needs a resource logo injects `ResourceIconProvider` rather than reaching for `ResourceIconComposer` directly. `iconRes` stays the icon source for feature, OS-shortcut, stream and scheduled-operation tiles; only the resource branch moved to `iconDrawable` plus `iconKey`.

---

## Rollback Plan

Revert the phase commit - no data migration, no schema change, no user-facing string. The resolver's previous branch is a two-line restoration.
