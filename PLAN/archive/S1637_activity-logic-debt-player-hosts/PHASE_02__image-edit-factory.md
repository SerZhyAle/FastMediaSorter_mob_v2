# Phase 02 - Image edit factory

**Strategic spec:** [`../S1637_activity-logic-debt-player-hosts.md`](../S1637_activity-logic-debt-player-hosts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 01, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Introduce one injectable supplier of the nine image and GIF edit use cases, modelled on the existing `StandaloneHostFactory`; no host wiring changes yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageEditFactory.kt` | New | ≤ 120 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Flavor placement.** Not applicable - strategic §3.2 names all flavors and the class carries no flavor-specific behaviour, so it belongs in `src/main/java/`.

---

## Steps

### Step 02.1 - Add `ImageEditFactory`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageEditFactory.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ImageEditFactory` as an `@Inject constructor` class holding the nine edit use cases: `RotateImageUseCase`, `FlipImageUseCase`, `NetworkImageEditUseCase`, `ApplyImageFilterUseCase`, `AdjustImageUseCase`, `MergeDrawOverlayUseCase`, `ExtractGifFramesUseCase`, `SaveGifFirstFrameUseCase`, `ChangeGifSpeedUseCase`. Expose each as a read-only property. Where the host declared a dependency as `dagger.Lazy<T>`, keep it `Lazy<T>` here and expose it as such. Do not add a Hilt `@Module`: constructor injection needs none, matching how `StandaloneHostFactory` is provided.

**Why:**

Strategic §6.1 measured all fifteen cluster use sites as object hand-offs with zero behaviour calls, so the form must be a supplier of objects; a behavioural facade would carry a surface nothing calls.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageEditFactory.kt` exists.
- `Grep` - `class ImageEditFactory` matches exactly once in that file.
- `Grep` - each of the nine use case type names appears at least once in that file.
- `Grep` - `@Module` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - ImageEditFactory added at ui/player/ImageEditFactory.kt: @Inject constructor, nine edit use cases as public read-only properties, no @Module. Verified: file exists, 'class ImageEditFactory' matches once, all nine type names present, zero @Module hits, 34 LOC against a 120 budget. No host declared any of the nine as dagger.Lazy, so none is wrapped.

---

### Step 02.2 - Prove the factory resolves in the Hilt graph

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageEditFactory.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `ImageEditFactory` at one existing consumption point that already has a Hilt entry point, and build. A compile-only check does not exercise the graph, so use a build that runs the Hilt processor.

**Why:**

Strategic §2 goal 4 requires behaviour to stay unchanged, and a missing binding surfaces only when the graph is generated - not from a plain Kotlin compile.

**Verification:**

- `/build` → `standard debug` completes without a `MissingBinding` error naming `ImageEditFactory`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Consumption point is PlayerActivity (step 01.3). a.ps1 dq exit 0, BUILD SUCCESSFUL in 1m25s: kspStandardDebugKotlin, hiltSyncStandardDebug and hiltJavaCompileStandardDebug all ran, so the graph was generated - no MissingBinding naming ImageEditFactory.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - a public class was added.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

One supplier now exists for both hosts. Phases 03 and 04 consume it independently and may be done in either order.

---

## Rollback Plan

Revert phase commit(s) - the class is new and nothing consumes it yet.
