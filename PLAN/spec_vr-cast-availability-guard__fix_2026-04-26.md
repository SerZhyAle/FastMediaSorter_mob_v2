# Spec Fix Run: vr-cast-availability-guard

**Source audit:** `spec_vr-cast-availability-guard__audit_2026-04-26.md`
**Fix date:** 2026-04-26
**Mode:** full
**Auto-applied:** 0
**Manual follow-ups:** 3

---

## 1. Auto-applied Fixes

None. All action items are spec documentation updates — out of scope for `spec-fix` (codebase only).

---

## 2. Manual Follow-ups

### Follow-up 1 — [WARN §2.3 — §6.1]

- **What the audit said:** `Status: Open` in strategic spec for "Где хранить capability verdict?"
- **Why not auto-fixed:** spec content change (not codebase) — belongs to `/spec-update`.
- **Suggested next action:** Update §6.1 in `PLAN/spec_vr-cast-availability-guard.md`: `Status: Open` → `Status: Resolved — BuildConfig.SUPPORT_CAST compile-time flag; no runtime singleton needed`.

### Follow-up 2 — [WARN §2.3 — §6.2]

- **What the audit said:** `Status: Open` in strategic spec for "Нужно ли скрывать UI-команду полностью?"
- **Why not auto-fixed:** spec content change (not codebase) — belongs to `/spec-update`.
- **Suggested next action:** Update §6.2 in `PLAN/spec_vr-cast-availability-guard.md`: `Status: Open` → `Status: Resolved — hidden entirely via BuildConfig.SUPPORT_CAST gate, same pattern as SUPPORT_VR_PLAYER`.

### Follow-up 3 — [WARN §2.3 — §6.3]

- **What the audit said:** `Status: Open` in strategic spec for "Жёсткий flavor guard vs runtime guard"
- **Why not auto-fixed:** spec content change (not codebase) — belongs to `/spec-update`.
- **Suggested next action:** Update §6.3 in `PLAN/spec_vr-cast-availability-guard.md`: `Status: Open` → `Status: Resolved — strict flavor gate chosen; vr/vrUnlicensed always lack Google Play Services, runtime probe adds no value`.

---

## 3. Skipped (filter flags)

None.

---

## 4. PRE-RESOLVED

- `**Audit:**` pointer in strategic spec — already inserted by `/spec-check`. PRE-RESOLVED.

---

## 5. Next Steps

1. Implement follow-ups 1–3 (§6 Status updates in strategic spec) — derivable from INDEX.md, no design decision required.
2. Run `/spec-check vr-cast-availability-guard` to confirm Verified.
