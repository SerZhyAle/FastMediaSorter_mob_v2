# Phase 04 — HUD Passive Content Extension

**Strategic spec:** [`../S0019_vr-controls-panel-flow-restoration.md`](../S0019_vr-controls-panel-flow-restoration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent of Phases 01–03
**Blocks:** —
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Extend the existing S0009 HUD canvas with **passive** indicators for the new strategic §2 goals: a textual hint «← prev / next →» visible briefly when the user pauses or seeks (helps the user discover the prev/next bindings), and a brief «applied: <stereo-mode>» badge when the route decision settles on a different stereo-mode than was previously playing. No interactivity in this phase — that is Phase 05 / S0024.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/hud/VrHudManager.kt` (or whichever class composites HUD content per S0009) | Modified | ≤ 600 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1700 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

---

## Steps

### Step 04.1 — Locate the S0009 HUD composer entry point

**Files:** read-only inspection
**Depends on:** — start of phase

**Prompt for developer:**

> S0009 §5.1.3 introduced the HUD content composer. Find the actual class name in `app_v2/src/vr/.../hud/` (likely `VrHudManager` / `VrHudCanvasComposer` / similar — see `dev/CATALOG/app_v2.md`). Note its public API for «show transient badge» (S0009 already added pause/seek/volume badges via this API). If the API exists, Step 04.2 reuses it. If not, abort: this phase blocks on completing S0009.

**Verification:**

- `Grep` — find a function whose signature accepts a transient-badge identifier + duration.
- Document the chosen entry-point class name in this step's commit message.

**Status:** `[x]` done

---

### Step 04.2 — Add «prev/next hint» trilingual strings + badge call

**Files:** three `values*/strings.xml` + `VrPlayerActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add strings:
>
> - `vr_hud_prev_next_hint` — EN: `← prev / next →`, RU: `← пред. / след. →`, UK: `← попер. / наст. →`
> - `vr_hud_applied_format` — EN: `Applied: %1$s`, RU: `Применено: %1$s`, UK: `Застосовано: %1$s`
>
> In `VrPlayerActivity` add a one-shot Timber.i + HUD-badge call when `isPaused` flips to true (call the S0009 transient-badge API with `vr_hud_prev_next_hint` for ~3s). Add another HUD-badge call when `route decision` produces a stereo-mode different from the previously applied mode (`vr_hud_applied_format` filled with the new mode, ~2s).

**Verification:**

- `Grep` — `name="vr_hud_prev_next_hint"` matches exactly 3 times across `values*/strings.xml`.
- `Grep` — `name="vr_hud_applied_format"` matches exactly 3 times.
- `Grep` — `vr_hud_prev_next_hint` referenced at least 1 time in `VrPlayerActivity.kt`.
- `Grep` — `vr_hud_applied_format` referenced at least 1 time in `VrPlayerActivity.kt`.

**Status:** `[x]` done

---

### Step 04.3 — Confirm no interactive callbacks added

**Files:** `VrHudManager.kt` (or its real name from Step 04.1) + `VrPlayerActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Audit the changes from Step 04.2: nothing should register a click-callback or hover-handler on HUD elements. The S0009 non-goal «interactive HUD» is preserved — interactivity comes only with S0024. Add a `// S0019: passive only — interactivity in S0024` comment near each new HUD call-site.

**Verification:**

- `Grep` — `S0019: passive only — interactivity in S0024` matches at least 2 times in the relevant files.
- `Grep` — `setOnClickListener`, `OnHoverListener`, `pickElement` — zero hits in the new HUD code.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — `/build` for `vr debug`.
- [ ] Dev log entries for the modified files.

---

## Handoff Notes to Next Phase

Phase 05 (interactive controls) is deferred until S0024 lands. Phase 06 (cleanup) consumes the work of 01–04.

---

## Rollback Plan

Revert phase commit.
