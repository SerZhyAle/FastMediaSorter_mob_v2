# Phase 03 - ViewModel

**Strategic spec:** [`../S1145_stream-edit-parameters-dialog.md`](../S1145_stream-edit-parameters-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-22
**Completed:** 2026-07-22

---

## Objective

Thread the media-kind override from the Activity through `StreamsViewModel.onEdit` into the use case, surface the new `Duplicate` result as a one-shot message, and expose a pure mapping the dialog uses to pre-select the type picker for an existing channel.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`UpdateStreamSourceUseCase.invoke` takes `mediaKindOverride`, returns `Duplicate`).
- [ ] Phase 01 is ✅ Done (`streams_error_duplicate_url` exists).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 540 |

---

## Steps

### Step 03.1 - onEdit forwards override and maps Duplicate to a message

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change `onEdit` to `fun onEdit(source: StreamSourceEntity, url: String, title: String?, mediaKindOverride: String?)` and pass `mediaKindOverride` into `updateStreamSource(source, url, title, mediaKindOverride)`. Replace the single `== InvalidUrl` check with a `when (val result = updateStreamSource(...))` that sends `StreamsEvent.Message(R.string.streams_error_invalid_url)` for `InvalidUrl`, `StreamsEvent.Message(R.string.streams_error_duplicate_url)` for `Duplicate`, and does nothing for `Success`/`NotEditable` (keep the existing silent-success behaviour). Keep the KDoc accurate (now: invalid-url and duplicate-url surface a message).

**Verification:**

- `Grep` - `mediaKindOverride: String?` in the `onEdit` signature.
- `Grep` - `updateStreamSource(source, url, title, mediaKindOverride)` present.
- `Grep` - `R.string.streams_error_duplicate_url` referenced in `onEdit`.
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-07-22 - onEdit now takes `mediaKindOverride` (defaulted null so the current 3-arg Activity caller still compiles until Phase 04) and maps InvalidUrl/Duplicate to messages via an exhaustive `when`. Verification 3/3 PASS.

---

### Step 03.2 - Expose the type-picker pre-selection mapping

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Inject `private val mediaKindClassifier: StreamMediaKindClassifier` into `StreamsViewModel`'s constructor (plain `@Inject constructor` wiring - no Hilt module). Add `fun resolveEditKindOption(source: StreamSourceEntity): String` (pure, non-suspend): return `"AUTO"` when `source.mediaKind == mediaKindClassifier.classify(source.url)` (the stored kind still matches what auto-derive would produce, so an untouched rtsp channel re-derives to RTSP on save); else `"AUDIO"` when `source.mediaKind == "AUDIO"`; else `"VIDEO"` (covers VIDEO and RTSP, which both route to the video player). WHY comment: this keeps the URL-classification decision in the domain-aware layer so the Activity stays presentation-only. This is the inverse of the override the dialog sends back: `"AUTO"` -> null override, `"AUDIO"`/`"VIDEO"` -> explicit.

**Verification:**

- `Grep` - `mediaKindClassifier: StreamMediaKindClassifier` in the constructor.
- `Grep` - `fun resolveEditKindOption(source: StreamSourceEntity): String` present.
- `Grep` - `mediaKindClassifier.classify(source.url)` present.

**Status:** `[x]` done

**Step Log:**

- 2026-07-22 - Injected `StreamMediaKindClassifier`; added pure `resolveEditKindOption` (AUTO when stored kind == classify(url), else AUDIO/VIDEO). Verification 3/3 PASS. VM at 538 LOC.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` (compileStandardDebugKotlin, includes the Activity caller) BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit - no P0/P1 (Layer 1 clean; Layer 2: `onEdit` stays a `viewModelScope.launch`, mapping exhaustive; `resolveEditKindOption` is pure/synchronous; no new listener/state).

---

## Handoff Notes to Next Phase

`onEdit` now requires a fourth `mediaKindOverride` argument. Phase 04 supplies it from the dialog's type picker and pre-selects the picker from the source's current kind.

---

## Rollback Plan

Revert to the 3-arg `onEdit` and the single-`InvalidUrl` check - no other caller depends on the new signature until Phase 04.
