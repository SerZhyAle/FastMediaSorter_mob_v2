# Spec Fix Run: vr-stereo-formats

**Source audit:** `spec_vr-stereo-formats__audit_2026-04-26.md`
**Fix date:** 2026-04-26
**Mode:** full
**Auto-applied:** 3
**Manual follow-ups:** 1

---

## 1. Auto-applied Fixes

| # | Origin | Category | Files | Outcome |
|---|--------|----------|-------|:-------:|
| 1 | [FAIL §2.4 — FEATURES.md] | FEATURES EN bullet | `docs/FEATURES.md` | ✅ |
| 2 | [FAIL §2.4 — FEATURES_RU.md] | FEATURES RU placeholder | `docs/FEATURES_RU.md` | ✅ |
| 3 | [FAIL §2.4 — FEATURES_UK.md] | FEATURES UK placeholder | `docs/FEATURES_UK.md` | ✅ |

EN bullet added after "VR stereoscopic playback" in §8 VR Edition in all three files.
RU and UK receive `<!-- TODO translate: <EN text> -->` placeholder per spec-fix category table.

---

## 2. Manual Follow-ups

### Follow-up 1 — GPU performance §6.3 still Open

- **What the audit said:** [WARN §2.3] §6.3 "GPU performance 7K @ 72fps" still `Status: Open`. Requires device measurement — deferred to manual acceptance.
- **Why not auto-fixed:** Requires device testing on Quest 3 — cannot be resolved statically.
- **Suggested next action:** Run GPU profiler (Meta Quest Developer Hub) with a 7K VR180 fisheye file while per-fragment shader is active. If FPS < 72 sustained, switch to LUT-based approach.

---

## 3. PRE-RESOLVED

- [WARN §2.3 — §6.1, §6.2, §6.4] Research items updated to `Resolved` inline during this fix run.

---

## 5. Next Steps

1. Translate `<!-- TODO translate -->` placeholders in `FEATURES_RU.md` and `FEATURES_UK.md` via `/doc-update`.
2. Address Follow-up 1 (GPU profiling on device).
3. Run `/spec-check vr-stereo-formats` to confirm Verified.
