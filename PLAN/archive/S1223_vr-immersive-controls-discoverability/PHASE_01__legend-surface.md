# Phase 01 - Legend surface

**Strategic spec:** [`../S1223_vr-immersive-controls-discoverability.md`](../S1223_vr-immersive-controls-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 3 / 3
**Started:** 2026-07-29
**Completed:** 2026-07-29

---

## Objective

Build the three self-contained pieces the legend needs - the "already shown" flag, the Canvas painter, and the controller that owns the HUD channel while the legend is up. No call site changes yet; nothing in this phase is reachable from the running app.

---

## Prerequisites

- [x] `scripts/utils/lock-status.ps1 -Name Build` shows no live build; acquire `CODE.LOCK` before edits.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/VrLegendPreferences.kt` | New | <= 60 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudLegendRenderer.kt` | New | <= 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudLegendController.kt` | New | <= 160 |

---

## Steps

### Step 01.1 - `VrLegendPreferences`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/VrLegendPreferences.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `VrLegendPreferences`, modelled on the neighbouring `MasterTogglePreferences`: `@Singleton class` with `@Inject constructor(@ApplicationContext private val context: Context)`, a file-private `preferencesDataStore(name = "vr_legend")` extension, and `booleanPreferencesKey("pref_vr_legend_shown")`. Expose `suspend fun isShown(): Boolean` reading the key with `false` as the default, and `suspend fun markShown()` writing `true`. KDoc must say the flag is per install, which is what makes "shown once" survive process death but not a reinstall - that is the acceptance criterion, not an accident of DataStore.

**Verification:**

- `Glob` - `VrLegendPreferences.kt` exists.
- `Grep` - `pref_vr_legend_shown` matches exactly once in the file.
- `Grep` - `preferencesDataStore(name = "vr_legend")` matches exactly once repo-wide (no store-name collision with `vr_master_toggle`).

**Status:** `[x]` done

---

### Step 01.2 - `HudLegendRenderer`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudLegendRenderer.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `HudLegendRenderer` - a Context-free Canvas painter for the legend page, the same shape as `HudCanvasRenderer` (companion `WIDTH`/`HEIGHT`, injected caption properties, one `render(canvas: Canvas)`).
>
> - `WIDTH = 1600`, `HEIGHT = 1120`. The 1.4286 aspect must match the quad Step 01.3 asserts, exactly as the strip's 2560x360 matches its 1.40x0.197 m quad.
> - Model: `var title: String`, `var footer: String`, and `var rows: List<LegendRow>` where `data class LegendRow(val input: String, val action: String)`. All text is injected by the host, so the renderer stays Context-free and localizable.
> - Paint: rounded translucent background using the same `BG_*` channel values and `BG_ALPHA` as `HudCanvasRenderer` so the two pages read as one surface; title in the header grey at 64 px; each row's `input` in the accent blue and `action` in white at 46 px; footer in the status green at 44 px.
> - Layout constants: `TITLE_BASELINE = 120f`, `ROWS_TOP = 250f`, `ROW_PITCH = 96f`, `INPUT_COLUMN_X = 96f`, `ACTION_COLUMN_X = 760f`, `FOOTER_BASELINE = 1044f`, `MARGIN = 32f`, `CORNER_RADIUS = 28f`. Named constants only - the detekt `MagicNumber` rule allows just -1/0/1/2 as bare literals.
> - Reuse the manual-trim ellipsize approach from `HudCanvasRenderer` for the action column so a long translation shortens instead of running off the page.
> - Keep every line at 120 characters or less (S0826 detekt-clean-first).

**Verification:**

- `Glob` - `HudLegendRenderer.kt` exists.
- `Grep` - `class HudLegendRenderer` matches exactly once.
- `Grep` - `data class LegendRow` matches exactly once.
- `Grep` in `HudLegendRenderer.kt` for `[^A-Za-z_"]\d{2,}f` returns only companion `const val` declarations (no bare numeric literal in a body).

**Status:** `[x]` done

---

### Step 01.3 - `HudLegendController`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudLegendController.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `HudLegendController(private val runtime: DiagnosticXrRuntime, private val renderer: HudLegendRenderer, private val onDismissed: () -> Unit)`.
>
> - `var isVisible: Boolean` with a private setter.
> - `fun show()` - no-op when already visible. Allocate the `ARGB_8888` bitmap, its `Canvas`, a direct `ByteBuffer` and the reusable `ByteArray` lazily here (not at construction - the legend is shown at most twice per session and the three buffers are about 6.4 MB each on a memory-tight headset). Paint via `renderer.render`, copy through `copyPixelsToBuffer` on the DIRECT buffer (a heap-backed `ByteBuffer.wrap` silently yields all zeros - S0290 round 3), `rewind`, `get(bytes)`, then `runtime.queueHud(bytes, HudLegendRenderer.WIDTH, HudLegendRenderer.HEIGHT)`, then `runtime.setHudQuadSize(QUAD_WIDTH_M, QUAD_HEIGHT_M, QUAD_OFFSET_Y_M)` and `runtime.setHudVisible(true)`.
> - `fun dismiss(): Boolean` - returns false when not visible so the caller can tell a consumed press from a pass-through one. When visible: clear `isVisible`, free the bitmap/buffers, then invoke `onDismissed()`. `onDismissed` is the single restore point for the strip's quad size and content, which is why the controller does not reassert strip geometry itself.
> - `fun release()` - free the buffers without touching native state; called from the host's `onDestroy`.
> - Companion: `QUAD_WIDTH_M = 1.00f`, `QUAD_HEIGHT_M = 0.70f`, `QUAD_OFFSET_Y_M = -0.05f`, `RGBA_BYTES_PER_PIXEL = 4`. KDoc the offset: the legend sits nearer the gaze ray than the strip's -0.30 m because it is read rather than glanced at, and the strip is not on screen at the same time.
> - Main-thread only, like every other HUD helper here. State it in the KDoc rather than adding synchronization.

**Verification:**

- `Glob` - `HudLegendController.kt` exists.
- `Grep` - `allocateDirect` matches in `HudLegendController.kt` and `ByteBuffer.wrap` does not.
- `Grep` - `setHudQuadSize` matches exactly once in `HudLegendController.kt`.
- `Grep` - `fun dismiss(): Boolean` matches exactly once.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fkn` (noLegal mounts `src/vr/`; the standard flavor does not compile this code).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Nothing in this phase is reachable yet: `HudLegendController` is constructed nowhere and `VrLegendPreferences` is injected nowhere. Phase 02 wires both into `DiagnosticXrActivity` and supplies `onDismissed`.

---

## Rollback Plan

Delete the three new files - no existing call site references them.
