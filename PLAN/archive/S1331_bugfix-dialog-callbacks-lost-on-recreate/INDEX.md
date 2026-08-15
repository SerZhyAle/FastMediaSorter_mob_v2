# Tactical Plan: S1331 - bugfix-dialog-callbacks-lost-on-recreate

**Strategic spec:** [`../S1331_bugfix-dialog-callbacks-lost-on-recreate.md`](../S1331_bugfix-dialog-callbacks-lost-on-recreate.md)
**Research inputs:** none
**Feature:** Dialog result delivery survives host recreation
**Tier:** 2 - Small (ad-hoc)
**Priority:** 40
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | network-discovery-result | - | ✅ Done | 3/3 | [PHASE_01__network-discovery-result.md](PHASE_01__network-discovery-result.md) |
| 02 | filter-resource-result | - | ✅ Done | 3/3 | [PHASE_02__filter-resource-result.md](PHASE_02__filter-resource-result.md) |
| 03 | option-picker-result | - | ✅ Done | 4/4 | [PHASE_03__option-picker-result.md](PHASE_03__option-picker-result.md) |
| 04 | color-picker-result | - | ✅ Done | 2/2 | [PHASE_04__color-picker-result.md](PHASE_04__color-picker-result.md) |
| 05 | permission-rationale-result | - | ✅ Done | 4/4 | [PHASE_05__permission-rationale-result.md](PHASE_05__permission-rationale-result.md) |
| 06 | docs-catalog-cleanup | 01-05 | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `✅ Done` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01-05 are independent: each converts one dialog and its own call sites, and none consumes an artifact
another produces. They are ordered by how long a user can leave the dialog on screen, so the highest-exposure
conversion lands first if the ticket is cut short.

---

## Conversion Template (applies to every phase)

The shipped reference is `SearchableLanguagePickerDialog` (S1214). Each conversion repeats these five moves:

- Declare `RESULT_KEY`, one `RESULT_*` payload key per returned value, and `ARG_REQUEST_KEY` in the companion.
- Take `requestKey: String = RESULT_KEY` as a `newInstance` parameter and store it in `arguments`.
- Read `requestKey` from `requireArguments()` in `onCreate`, never in `onCreateDialog` and never from a field
  the caller assigned.
- Replace the callback invocation with `setFragmentResult(requestKey, bundleOf(..))` followed by `dismiss()`.
- Register `setFragmentResultListener(RESULT_KEY, lifecycleOwner) { _, bundle -> .. }` in the host's own
  `onCreate` / `onViewCreated`, so the listener is re-registered by the recreated host before the restored
  dialog is resumed.

Payloads must be Bundle primitives. Where a value is a domain object, put its primitive fields in the bundle
and rebuild the object in the host rather than making a domain model `Parcelable`.

---

## Out of Scope

The survey behind this plan found seven dialogs holding host-supplied callbacks, not the four listed in
strategic §0. Two are excluded here because they are a different transformation, not a wider instance of this
one. Both need their own ticket.

- `ui/cameracapture/CameraSettingsDialogFragment.kt` - holds `capabilities`, `initialSettings` and `callbacks`
  as `lateinit var`, so a restored instance throws `UninitializedPropertyAccessException` in `onCreateDialog`
  instead of silently doing nothing. Higher severity than every dialog in this plan, and a different fix: its
  `Callbacks` interface carries a live preview stream that fires on every slider move, which FragmentResult is
  a poor carrier for.
- `ui/share/SendToBottomSheet.kt` - holds `content`, `settings` and `onPickResource`, but already detects the
  loss and dismisses itself with a log line, so the failure is visible rather than silent. Lowest exposure of
  the seven.

---

## Known Risks and Accepted Limitations

- The reference pattern's own ticket S1214 is still `BlockNeedUserTest`, so the pattern these five conversions
  mirror has not yet passed its device test. The API is stock AndroidX FragmentResult, so the risk is that
  S1214's device test finds a wiring mistake worth copying back here, not that the approach is wrong.
- Phase 03 delivers a partial cure for the streams filter, and this is deliberate. S1214 accepted the same
  limitation for two of its own hosts: when the parent is a plain `AlertDialog` rather than a `DialogFragment`,
  the parent does not survive recreation, so a pick made after recreation arrives the next time that picker is
  opened rather than immediately. Making the streams filter parent a `DialogFragment` is a separate change.
- `FilterResourceDialog` shares the main screen with S1272. Phase 02 touches the dialog and one click handler
  in `MainActivity`, not the filter warning strip, so the two should not collide - confirm S1272's working
  state before starting Phase 02.

---

## Pre-Implementation Blockers

None. The strategic spec has no open research items.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic §8 carries no FEATURES sentence, and this is a
      defect fix with no new user-facing capability.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - every converted dialog's `newInstance` signature changed.
- [x] `Grep` - zero remaining `((.*) -> Unit)? = null` result-callback fields across the five converted dialogs.
- [ ] `/spec-check S1331` returns `Verified`. Pending the device test - the ticket is `BlockNeedUserTest`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`. Pending the device test.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1331`.

Debug probes: do not insert `Timber.d("S1331: ..")` in any phase below. The ticket-log gate rejects a probe
whose spec is not currently `BlockNeedUserTest`, and the spec is `In Progress` throughout. `/spec-dev` inserts
every probe as the final edit, together with the `BlockNeedUserTest` flip.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
