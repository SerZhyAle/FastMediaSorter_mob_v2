# Phase 01 — Refactor Shader Access

**Strategic spec:** [`../S0088_test-vr-video-layer-geometry-snapshot.md`](../S0088_test-vr-video-layer-geometry-snapshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Extract `fisheyeFragSrc` from `initGl()` to a public companion object val `FISHEYE_FRAG_SRC` in `VrStereoRenderer` so that the JVM unit test can assert on its content without a GL context.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt` | Modified | ≤ 510 |

> File is 508 LOC (>500) — `/spec-dev` will create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 1.1 — Extract fisheyeFragSrc to companion object FISHEYE_FRAG_SRC

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `VrStereoRenderer`, the multiline string `fisheyeFragSrc` is declared as a local `val` inside `initGl()` (lines 151–184). Move the string body into the existing `companion object` as `internal val FISHEYE_FRAG_SRC = """...""".trimIndent()` — paste the identical multiline string content. In `initGl()`, replace the original local `val fisheyeFragSrc = """...""".trimIndent()` with `val fisheyeFragSrc = FISHEYE_FRAG_SRC`. Do not change the string content or any other code.

**Verification:**

- `Grep` — `internal val FISHEYE_FRAG_SRC` present in `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt`.
- `Grep` — `val fisheyeFragSrc = FISHEYE_FRAG_SRC` present in `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt`.
- `Grep` — `0.5 - 0.5 \* r \* sin(az)` still present in `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt` (string content unchanged).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 3/3 PASS. Files: app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt (moved fisheyeFragSrc to companion object FISHEYE_FRAG_SRC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 1.*` above is `[x] done`.
- [x] Project compiles — run `.\build-debug.PS1`. BUILD SUCCESSFUL in 30s.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `VrStereoRenderer.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] Public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 01 establishes `VrStereoRenderer.FISHEYE_FRAG_SRC` as a stable `internal` accessor for the fisheye fragment shader source. Phase 02 reads it from the `testVr` source set.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
