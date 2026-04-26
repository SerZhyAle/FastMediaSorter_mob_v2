# PLAN Specifications Catalog

**Generated:** 2026-04-26  
**Scope:** current `PLAN` specification files and their latest visible states  
**Status source:** `**Status:**`, `**Outcome:**`, `**Loop status:**`, tactical `INDEX.md`, or latest stage log inside the corresponding artifact

---

## 1. Strategic specs

| Spec | Current state | Tactical plan | Tactical state | Notes |
| --- | --- | --- | --- | --- |
| [spec_browse-thumbnail-reliability.md](spec_browse-thumbnail-reliability.md) | Verified | [spec_browse-thumbnail-reliability/INDEX.md](spec_browse-thumbnail-reliability/INDEX.md) | Done | Completed via /spec-all 2026-04-26; 4/4 phases done |
| [spec_camera-capture-command.md](spec_camera-capture-command.md) | Verified | [spec_camera-capture-command/INDEX.md](spec_camera-capture-command/INDEX.md) | Done | Tactical plan exists and is fully done |
| [spec_network-smb-pooling.md](spec_network-smb-pooling.md) | Done | not created | — | New ad-hoc SMB playback/pooling spec |
| [spec_player-keybinding-remapping.md](spec_player-keybinding-remapping.md) | Verified | [spec_player-keybinding-remapping/INDEX.md](spec_player-keybinding-remapping/INDEX.md) | Done | Tactical plan exists and is fully done |

| [spec_player-lifecycle-cancellation.md](spec_player-lifecycle-cancellation.md) | Done | not created | — | New ad-hoc player lifecycle resilience spec |
| [spec_virtual-resource-lang-rename.md](spec_virtual-resource-lang-rename.md) | Verified | [spec_virtual-resource-lang-rename/INDEX.md](spec_virtual-resource-lang-rename/INDEX.md) | Done | Tactical plan exists and is fully done |

| [spec_vr-cast-availability-guard.md](spec_vr-cast-availability-guard.md) | Done | not created | — | New ad-hoc Quest/VR Cast capability spec |

| [spec_vr-hand-tracking-tech.md](spec_vr-hand-tracking-tech.md) | Done | not created | — | Technical/design-side VR hand tracking doc |
| [spec_vr-hand-tracking.md](spec_vr-hand-tracking.md) | Backlog (Blocked by `spec_vr-immersive-controls.md`) | not created | — | Waiting on upstream immersive-controls dependency |
| [spec_vr-immersive-controls-panel.md](spec_vr-immersive-controls-panel.md) | Tactical | [spec_vr-immersive-controls-panel/INDEX.md](spec_vr-immersive-controls-panel/INDEX.md) | Not started | Strategic doc already moved into tactical phase planning |
| [spec_vr-immersive-hud-gl.md](spec_vr-immersive-hud-gl.md) | Partial | [spec_vr-immersive-hud-gl/INDEX.md](spec_vr-immersive-hud-gl/INDEX.md) | Done | Tactical implementation complete, strategic remains Partial pending audit closure / manual verification |
| [spec_vr-immersive-toggle.md](spec_vr-immersive-toggle.md) | Verified | [spec_vr-immersive-toggle/INDEX.md](spec_vr-immersive-toggle/INDEX.md) | Done | Tactical plan exists and is fully done |
| [spec_vr-input-reliability.md](spec_vr-input-reliability.md) | Implemented | [spec_vr-input-reliability/INDEX.md](spec_vr-input-reliability/INDEX.md) | Implemented | Tactical plan exists and is implemented |
| [spec_vr-stereo-formats.md](spec_vr-stereo-formats.md) | Draft | not created | — | Strategic VR stereo format work |

| [spec_vr-stereo-state.md](spec_vr-stereo-state.md) | Draft | not created | — | Strategic VR stereo state work |

| [spec_vr-xr-cold-start.md](spec_vr-xr-cold-start.md) | Draft | not created | — | New ad-hoc XR cold-start latency spec |

### Strategic summary

| State | Count |
| --- | ---: |
| Draft | 7 |
| Verified | 5 |
| Implemented | 1 |
| Partial | 1 |
| Tactical | 1 |
| Backlog / Blocked | 1 |

---

## 2. Tactical plan folders

| Tactical plan | Current state | Phases summary |
| --- | --- | --- |
| [spec_browse-thumbnail-reliability/INDEX.md](spec_browse-thumbnail-reliability/INDEX.md) | Done | 4 / 4 done |
| [spec_camera-capture-command/INDEX.md](spec_camera-capture-command/INDEX.md) | Done | 6 / 6 done |
| [spec_player-keybinding-remapping/INDEX.md](spec_player-keybinding-remapping/INDEX.md) | Done | 8 / 8 done |
| [spec_virtual-resource-lang-rename/INDEX.md](spec_virtual-resource-lang-rename/INDEX.md) | Done | tactical plan present |
| [spec_vr-immersive-controls-panel/INDEX.md](spec_vr-immersive-controls-panel/INDEX.md) | Not started | 0 / 6 done |
| [spec_vr-immersive-hud-gl/INDEX.md](spec_vr-immersive-hud-gl/INDEX.md) | Done | 7 / 7 done |
| [spec_vr-immersive-toggle/INDEX.md](spec_vr-immersive-toggle/INDEX.md) | Done | tactical plan present |
| [spec_vr-input-reliability/INDEX.md](spec_vr-input-reliability/INDEX.md) | Implemented | 4 / 4 implemented |

---

## 3. Audit, fix, and pipeline artifacts

### 3.1 Audit reports

| Artifact | Current state |
| --- | --- |
| [spec_browse-thumbnail-reliability__audit_2026-04-26.md](spec_browse-thumbnail-reliability__audit_2026-04-26.md) | Outcome: Partial (iter 1; fixed by spec-fix) |
| [spec_browse-thumbnail-reliability__audit_2026-04-26_2.md](spec_browse-thumbnail-reliability__audit_2026-04-26_2.md) | Outcome: Verified (30P/0W/0F/2M/1E) |
| [spec_camera-capture-command__audit_2026-04-25.md](spec_camera-capture-command__audit_2026-04-25.md) | Outcome: Partial |
| [spec_player-keybinding-remapping__audit_2026-04-25.md](spec_player-keybinding-remapping__audit_2026-04-25.md) | Outcome: Verified |
| [spec_vr-immersive-hud-gl__audit_2026-04-25.md](spec_vr-immersive-hud-gl__audit_2026-04-25.md) | Outcome: Partial |
| [spec_vr-immersive-toggle__audit_2026-04-25.md](spec_vr-immersive-toggle__audit_2026-04-25.md) | Outcome: Verified |
| [spec_vr-input-reliability__audit_2026-04-26.md](spec_vr-input-reliability__audit_2026-04-26.md) | Outcome: Verified (after spec-fix iter 1) |

### 3.2 Fix runs

| Artifact | Current state |
| --- | --- |
| [spec_browse-thumbnail-reliability__fix_2026-04-26.md](spec_browse-thumbnail-reliability__fix_2026-04-26.md) | Auto-applied: 3 (INDEX drift, checkbox drift, line budget annotation) |
| [spec_vr-immersive-hud-gl__fix_2026-04-25.md](spec_vr-immersive-hud-gl__fix_2026-04-25.md) | Fix run generated; auto-applied fixes recorded |

### 3.3 spec-all pipeline logs

| Artifact | Current state |
| --- | --- |
| [spec-all_browse-thumbnail-reliability_2026-04-26.md](spec-all_browse-thumbnail-reliability_2026-04-26.md) | Stage 8 DONE — Verified |
| [spec-all_camera-capture-command_2026-04-25.md](spec-all_camera-capture-command_2026-04-25.md) | Pipeline log exists; latest recorded stage: Stage 4 DONE |
| [spec-all_player-keybinding-remapping_2026-04-25.md](spec-all_player-keybinding-remapping_2026-04-25.md) | Pipeline log exists; Stage 6 DONE |
| [spec-all_virtual-resource-lang-rename_2026-04-26.md](spec-all_virtual-resource-lang-rename_2026-04-26.md) | Pipeline log exists; Stage 6 DONE |
| [spec-all_vr-input-reliability_2026-04-26.md](spec-all_vr-input-reliability_2026-04-26.md) | Pipeline log exists; Stage 6 DONE |

---

## 4. Non-standard spec-prefixed tracking files

These files live in `PLAN`, but they are not standard strategic+tactical spec pairs.

| File | Current state | Notes |
| --- | --- | --- |
| [spec-list-vr-problems.md](spec-list-vr-problems.md) | Problem inventory / reference list | Session-specific VR problem ledger, not a strategic spec |
| [spec_decompose-giant-files.md](spec_decompose-giant-files.md) | Loop status: running automated decomposition loop | Ongoing engineering backlog / program tracker rather than a normal spec |

---

## 5. Quick interpretation

- The completed and closed spec lines are `browse-thumbnail-reliability`, `camera-capture-command`, `player-keybinding-remapping`, `virtual-resource-lang-rename`, and `vr-immersive-toggle`.
- `vr-input-reliability` has a Verified audit (2026-04-26) but its strategic header still reads `Implemented` — audit did not flip it.
- `vr-immersive-hud-gl` has a completed tactical plan, but the latest strategic/audit view is still `Partial`.
- Ad-hoc specs currently sitting in `Draft` without tactical plans: `network-smb-pooling`, `vr-xr-cold-start`, `vr-cast-availability-guard`, and `player-lifecycle-cancellation`.
