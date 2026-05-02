# Phase 01 — Add Button Affordance to HUD pause/play icon

**Strategic spec:** [../S0040_bugfix-vr-hud-button-affordance.md](../S0040_bugfix-vr-hud-button-affordance.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-04-30
**Completed:** 2026-04-30 (build gate pending Stage 6)

---

## Objective

Add a rounded-rect background fill + border stroke to the `drawPauseIcon` function in `VrHudSceneComposer.kt`. The icon currently renders as a bare text glyph with no visual container, making it indistinguishable from informational labels. After this phase the pause/play element reads as a tappable button with state-differentiated shading (active vs. inactive), and its bounding rect is registered in `VrHudElementRegistry` for future ray-input use (S0024).

## Files Touched

| File | Action | Note |
|------|--------|------|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` | Modified | Add button Paint fields, modify `drawPauseIcon`, add `HUD_ELEMENT_PLAY_PAUSE` constant |

---

## Steps

### Step 1.1 — Add button Paint fields to VrHudSceneComposer

**Status:** `[x] done`
**Depends on:** none
**Blocks:** Step 1.2

**Prompt for developer:**

In `VrHudSceneComposer.kt`, after the `flashPaint` field declaration block and before the `private val tmpRect = RectF()` line, insert three new Paint field declarations:

```kotlin
// WHY: button affordance paints — S0040. Two bg variants encode active/inactive
// state without per-frame Paint mutation. Border is a thin STROKE on top.
private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(100, 255, 255, 255) // inactive / paused state
}
private val btnBgActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(170, 255, 255, 255) // active / playing state — more opaque
}
private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(128, 255, 255, 255) // #80FFFFFF border
    style = Paint.Style.STROKE
    strokeWidth = 2f
}
```

**Files Touched:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`

**Verification:**

```
# All three fields declared
Grep: "btnBgPaint" in VrHudSceneComposer.kt → ≥ 1 hit on a declaration line
Grep: "btnBgActivePaint" in VrHudSceneComposer.kt → ≥ 1 hit on a declaration line
Grep: "btnBorderPaint" in VrHudSceneComposer.kt → ≥ 1 hit on a declaration line

# STROKE style set
Grep: "Paint.Style.STROKE" in VrHudSceneComposer.kt → 1 hit
```

---

### Step 1.2 — Add HUD_ELEMENT_PLAY_PAUSE constant

**Status:** `[x] done`
**Depends on:** Step 1.1
**Blocks:** Step 1.3

**Prompt for developer:**

In `VrHudSceneComposer.kt`, inside the `companion object` block, add the `HUD_ELEMENT_PLAY_PAUSE` constant next to the existing `HUD_ELEMENT_SEEK_BAR`:

```kotlin
const val HUD_ELEMENT_PLAY_PAUSE: Int = 2
```

The companion object currently contains only `HUD_ELEMENT_SEEK_BAR = 1`.

**Files Touched:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`

**Verification:**

```
# Constant declared with value 2
Grep: "HUD_ELEMENT_PLAY_PAUSE" in VrHudSceneComposer.kt → ≥ 1 hit

# Both constants present in companion
Grep: "HUD_ELEMENT_SEEK_BAR" in VrHudSceneComposer.kt → ≥ 1 hit
```

---

### Step 1.3 — Modify drawPauseIcon to draw affordance

**Status:** `[x] done`
**Depends on:** Step 1.2
**Blocks:** Step 1.4

**Prompt for developer:**

Replace the entire `drawPauseIcon` function in `VrHudSceneComposer.kt` with the following:

```kotlin
private fun drawPauseIcon(state: VrHudState, canvas: Canvas) {
    val paused = state.isPaused ?: return
    val glyph = if (paused) "❚❚" else "▶"
    val cx = width / 2f
    val cy = height / 2f - 20f
    // WHY: glyph width varies between pause and play glyphs; measure each frame
    // to avoid a fixed rect that clips "❚❚" or leaves too much space for "▶".
    // Paint.measureText does not allocate — safe inside draw (see KDoc contract).
    val hw = iconPaint.measureText(glyph) / 2f + 20f
    tmpRect.set(cx - hw, cy - 70f, cx + hw, cy + 20f)
    // Active (playing) state → stronger bg opacity; inactive (paused) → dimmer.
    // Two separate Paint instances avoid mutating color on the hot path.
    val bg = if (paused) btnBgPaint else btnBgActivePaint
    canvas.drawRoundRect(tmpRect, 6f, 6f, bg)
    canvas.drawRoundRect(tmpRect, 6f, 6f, btnBorderPaint)
    // Register bounds for S0024 ray-input hit-testing.
    registry.register(HUD_ELEMENT_PLAY_PAUSE, tmpRect, "play_pause") {}
    canvas.drawText(glyph, cx, cy, iconPaint)
}
```

**Files Touched:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`

**Verification:**

```
# drawRoundRect calls now ≥ 3 (progress bar bg + play/pause bg + play/pause border)
Grep: "drawRoundRect" in VrHudSceneComposer.kt → ≥ 3 hits

# registry.register called with HUD_ELEMENT_PLAY_PAUSE
Grep: "HUD_ELEMENT_PLAY_PAUSE" in VrHudSceneComposer.kt → ≥ 2 hits (declaration + usage)

# measureText used in drawPauseIcon context
Grep: "measureText" in VrHudSceneComposer.kt → ≥ 2 hits
```

---

### Step 1.4 — Verify no Log.d usage introduced, run dev log

**Status:** `[x] done`
**Depends on:** Step 1.3
**Blocks:** Phase 02

**Prompt for developer:**

1. Verify no `Log.d(` or `Log.e(` was introduced in `VrHudSceneComposer.kt`.
2. Run the dev log command:
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt" "VrHudSceneComposer" "S0040: add rounded-rect affordance to pause/play button; register HUD_ELEMENT_PLAY_PAUSE"
   ```

**Files Touched:** `dev/CHANGELOG.md`

**Verification:**

```
# No Log.d() in file
Grep: "Log\.d\(" in VrHudSceneComposer.kt → 0 hits

# No Log.e() in file
Grep: "Log\.e\(" in VrHudSceneComposer.kt → 0 hits

# dev/CHANGELOG.md updated
Grep: "VrHudSceneComposer" in dev/CHANGELOG.md → ≥ 1 hit
```

---

## Phase Done Criteria

- [x] Project compiles (BUILD-REQUIRED — auto-build by /spec-all Stage 6 — PASS. APK v2.60.4301.230, 33s).
- [ ] `Grep: "drawRoundRect" in VrHudSceneComposer.kt` → ≥ 3 hits.
- [ ] `Grep: "HUD_ELEMENT_PLAY_PAUSE" in VrHudSceneComposer.kt` → ≥ 2 hits.
- [ ] `Grep: "btnBgPaint|btnBgActivePaint|btnBorderPaint" in VrHudSceneComposer.kt` → 3 distinct declarations.
- [ ] `Grep: "Log\.d\(" in VrHudSceneComposer.kt` → 0 hits.

---

## Step Log

- 2026-04-30 12:28 — Step 1.1 done: btnBgPaint, btnBgActivePaint, btnBorderPaint declared.
- 2026-04-30 12:28 — Step 1.2 done: HUD_ELEMENT_PLAY_PAUSE = 2 added to companion.
- 2026-04-30 12:28 — Step 1.3 done: drawPauseIcon rewritten with roundRect bg + border + registry.register.
- 2026-04-30 12:28 — Step 1.4 done: Log.d = 0, CHANGELOG updated.
