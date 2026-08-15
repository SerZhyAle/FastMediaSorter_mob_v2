# Phase 04 — Progress feedback during cast preparation

**Strategic spec:** [`../S0137_feature-cast-network-cloud-streaming.md`](../S0137_feature-cast-network-cloud-streaming.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked — `/ui-clarify` must resolve the progress-indicator format
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Replace the one-shot `cast_preparing` toast with a continuous progress indicator that conveys download advancement so the user knows whether the cast is progressing or stuck. The exact UI format (updating toast / foreground notification / dialog with cancel) is decided by `/ui-clarify`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] `/ui-clarify` produced a written decision on:
    1. Indicator surface — toast updates / persistent notification / modal dialog.
    2. Cancel affordance — yes / no, and where.
    3. Behaviour for sub-second downloads (cached LAN file) — show / suppress.
- [ ] If "notification" — `cast_preparing_progress` channel allocated and registered in the same step (specify channel id in step body once decided).

---

## Files Touched

The exact list depends on the `/ui-clarify` outcome. Conservative bound:

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt` | Modified | ≤ 380 |
| `app_v2/src/main/res/values/strings.xml` | Modified | append |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | append |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | append |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastProgressNotifier.kt` *(only if notification chosen)* | New | ≤ 200 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). Currently `CastMediaManager.kt` is ~377 lines — within budget.

---

## Steps

### Step 04.1 — Implement the progress indicator chosen by `/ui-clarify`

**Files:** `CastMediaManager.kt` (+ optional new helper, depending on the decision)
**Depends on:** — start of phase

**Prompt for developer:**

> Reread the `/ui-clarify` outcome before writing code. Implement the chosen surface:
>
> - **Updating toast:** schedule periodic re-toasts (`cast_preparing_progress` with `%1$d` percent) at 1.0 s cadence while the suspend in `downloadToTemp` is in flight; cancel on completion / failure.
> - **Foreground notification:** create a small notifier helper (e.g. `CastProgressNotifier.kt`) that owns a single ongoing notification with an indeterminate or determinate progress bar; tie its lifecycle to `downloadJob`.
> - **Modal dialog:** present a non-cancellable `AlertDialog` from `PlayerActivity` via the `onCastStateChanged` callback; dismiss when the cast loads on the receiver.
>
> Insert `Timber.d("S0137: CastMediaManager — progress feedback start")` and `Timber.d("S0137: CastMediaManager — progress feedback end")` at the start and end of the indicator's lifetime. If `NetworkFileManager.prepareFileForRead` does not currently expose progress, decide whether to extend it (small change) or leave the indicator as indeterminate. Document the choice inline.

**Verification:**

- `Grep` — `S0137: CastMediaManager — progress feedback start` matches exactly once.
- `Grep` — `S0137: CastMediaManager — progress feedback end` matches exactly once.
- The chosen UI surface is reachable from `CastMediaManager` (verify by static reference: notification helper / dialog / toast call).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

### Step 04.2 — Add the progress strings in EN/RU/UK

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `cast_preparing_progress` (parametrised with `%1$d` percent or with a generic "Downloading.." label, depending on whether progress is determinate). Mirror the entry across all three locales. Reuse the existing `cast_preparing` string only if it survived `/ui-clarify` unchanged.

**Verification:**

- `Grep` — `name="cast_preparing_progress"` appears exactly once in each of the three `strings.xml` files.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "cast_"` — exit code 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [ ] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "cast_"` returns exit 0.
- [ ] Manual smoke test note in handoff: `BlockNeedUserTest` on a slow SMB file — indicator visible for the duration of download.

---

## Handoff Notes to Next Phase

Phase 05 picks up the docs / catalog / dev-log housekeeping for the entire S0137 ticket.

---

## Rollback Plan

Revert the phase commit. The pre-phase one-shot `cast_preparing` toast is restored.
