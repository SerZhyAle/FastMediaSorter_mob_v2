# Tactical Plan: S0019 — VR controls-panel flow restoration

**Strategic spec:** [`../S0019_vr-controls-panel-flow-restoration.md`](../S0019_vr-controls-panel-flow-restoration.md)
**Feature:** Restore the «settings → apply → 3D» scenario in VR. Replace MainActivity exit target with PlayerActivity. Add «apply and 3D» combo button. Add prev/next without exit from immersive. Extend S0009 HUD with passive playback indicators. Defer interactive HUD controls to S0024.
**Tier:** 4 — Strategic
**Status:** Broken — exit-to-panel cloning (S0038) and HUD-overlay flag (S0008) regressed §11.1/§11.3/§11.5
**Phases:** 5 / 6 done + 1 deferred (Phase 05 → S0024 — still blocked by S0033 In Progress)
**Last updated:** 2026-05-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | exit-target-redirect | — | ✅ Done | 3/3 | [PHASE_01__exit-target-redirect.md](PHASE_01__exit-target-redirect.md) |
| 02 | apply-and-3d-button | — | ✅ Done | 3/3 | [PHASE_02__apply-and-3d-button.md](PHASE_02__apply-and-3d-button.md) |
| 03 | immersive-prev-next | 01 | ✅ Done | 3/3 | [PHASE_03__immersive-prev-next.md](PHASE_03__immersive-prev-next.md) |
| 04 | hud-passive-content | — | ✅ Done | 3/3 | [PHASE_04__hud-passive-content.md](PHASE_04__hud-passive-content.md) |
| 05 | interactive-hud-controls | S0024 | ⏭️ Deferred | 0/0 | [PHASE_05__interactive-hud-controls.md](PHASE_05__interactive-hud-controls.md) |
| 06 | docs-catalog-cleanup | 01–04 | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Deferred`

---

## Pre-Implementation Blockers

Strategic §6 has 5 Closed decisions (all resolved by owner 2026-04-28). Strategic §«Proposed Structural Changes» has 3 proposals:

- [x] §6 #1 — exit target = existing PlayerActivity (Phase 01 implements).
- [x] §6 #2 — full immersive HUD scope: passive part landed via Phase 04, interactive part deferred to S0024 / Phase 05.
- [x] §6 #3 — context via intent-extras + in-memory; no DataStore (consumed in Phases 01+03).
- [x] §6 #4 — «apply and 3D» combo button (Phase 02).
- [x] §6 #5 — VR-photo symmetric; prev/next applies to both (Phase 03).
- [x] **P-1** — S0009 ADR-3 transitional guard removal — recorded as follow-up; requires `/spec-update S0009 --force-locked` AFTER S0019 lands.
- [x] **P-2** — interactive ray-input dependency split into S0024 (allocated, Status: BlockByOtherTask — blocked by S0033 In Progress 2026-05-02). Phase 05 of this spec is the placeholder waiting on S0024 implementation.
- [x] **P-3** — §6 heading kept as «Закрытые решения» for readability; auditor can rely on absence of `Status: Open` markers.

---

## Field-log — Quest 3 2026-05-02

`logs/fastmediasorter_20260502_035656.log` reveals two regressions hitting this spec's §11 criteria:

| § | Goal | Static code | On device 2026-05-02 | Verdict |
|---|------|:-----------:|----------------------|---------|
| §11.1 | Immersive HUD overlay with full playback UI on «show controls» | wired (Phase 04 + S0008 phases 03/04/05) | gated behind `VR_UI_COMPOSITION_LAYER_ENABLED=false` → banner instead | **FAIL** (cause: S0008) |
| §11.2 | Prev/next inside immersive without XR-session restart | Phase 03 implemented | not testable (HUD invisible) | MANUAL (blocked) |
| §11.3 | «Exit to panel» opens existing flat PlayerActivity at same file/position | Phase 01 wires `EXTRA_FORCE_PANEL` | `VrTaskTransition.exitImmersiveToFlatPlayer: routing via home-intent` creates **new VrPlayerActivity** instance, then launches PlayerActivity in a new task → user sees clone window in switcher | **FAIL** (cause: S0038 home-intent regression) |
| §11.4 | «Apply and 3D» button in flat dialog | `btnApplyAnd3D` in [`dialog_playback_control.xml:29`](../../app_v2/src/main/res/layout/dialog_playback_control.xml#L29), 3 locales `dialog_playback_apply_and_3d`, click-listener at [`PlaybackControlDialogFragment.kt:115-117`](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt#L115-L117), gated by `BuildConfig.SUPPORT_VR_PLAYER` | static present | **PASS** |
| §11.5 | End-to-end «browser → immersive → exit-to-panel → change format → return» without re-pick | depends on §11.3 | broken by §11.3 (clone) and §11.1 (no in-immersive change) | **FAIL** |
| §11.6 | Return to immersive with identical context — no XR-session restart | not implemented (each exit destroys VrPlayerActivity) | log shows full `onDestroy → onCreate` cycle every exit | **FAIL** |
| §11.7 | EN/RU/UK command texts match destination | partially verified | needs manual review | MANUAL |
| §11.8 | VR-photo symmetric to video except video-only HUD elements hidden | Phase 03 covers nav | not testable (HUD invisible) | MANUAL (blocked) |

**Action items:**

1. Wait on **S0008** fix (`VR_UI_COMPOSITION_LAYER_ENABLED=true` + cursor-dot rendering) — unlocks §11.1, §11.2, §11.5, §11.8.
2. Wait on **S0038 P-1** decision (move-to-front via cached taskId or `singleInstancePerTask`) — unlocks §11.3, §11.5, §11.6.
3. Wait on **S0033** decomposition completion → unblocks **S0024** Phase 02 → unblocks `Phase 05` of this spec (interactive controls).
4. After all three lands: re-run `/spec-check S0019`.

---

## Completion Gate

- [ ] Phases 01–04 + 06 show ✅ Done. Phase 05 stays ⏭️ Deferred until S0024 lands.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated for the user-visible surface (exit-to-player flow + «apply and 3D» button + immersive prev/next + passive HUD indicators).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0019` returns `Verified` (Phase 05 deferred = MANUAL but not FAIL).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress`, then `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.

---

## Blockers Log

- (none — Phase 05 is intentionally deferred, not blocked)

---

## Change Log

- 2026-04-28 — Initial tactical plan authored by `/spec-tech` (via `/spec-all`). Decomposed into 4 doable + 1 deferred (waits on S0024) + 1 cleanup phase.
