# Phase 06 - Platform residue triage

**Strategic spec:** [`../S1195_lint-strict-but-never-run-72-unbaselined-errors.md`](../S1195_lint-strict-but-never-run-72-unbaselined-errors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-07-31
**Depends on:** none - app-code only
**Blocks:** Phase 07
**Steps done:** 4 / 4

---

## Objective

The 8 errors that are not produced by the project's own detectors. Spec §1 lists only 2 of them (`MissingPermission`); `UseAppTint` (4) and `RepeatOnLifecycleWrongUsage` (2) appear nowhere in the spec. All 8 must be resolved, because §11.3 requires the whole task to go green and `abortOnError = true` does not care who wrote the rule.

- `MissingPermission` 2 - `screencapture/ScreenVideoRecordingService.kt:238` and `:251`. This was the *first failure* quoted in the spec's §0 captured output.
- `UseAppTint` 4 - `item_gesture_picker_entry.xml:70`, `item_launcher_taskbar_add.xml:24`, `item_launcher_taskbar_icon.xml:38`, `item_list_selection.xml:49`. Message: "Must use `app:tint` instead of `android:tint`".
- `RepeatOnLifecycleWrongUsage` 2 - `ui/cameracapture/CameraSettingsDialogFragment.kt:96` and `utils/LifecycleExtensions.kt:39`.

---

## Prerequisites

- [ ] `temp/CODE.LOCK` acquired.
- [ ] Two of the three files live outside `src/main` (`screenCapture`, `launcherEnabled` source sets) - check `dev/FLAVOR_DEVELOPMENT_RULES.md` for which variants compile them.

---

## Steps

### Step 06.1 - `MissingPermission` in the screen-recording service

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Read both call sites (lines 238 and 251) and decide between two outcomes. If the permission is genuinely guaranteed by the time the service runs - the recording flow goes through `ScreenVideoRecordingConsentActivity`, so the consent may already be a precondition - the correct fix is to make that guarantee visible to lint and to the next reader: an explicit `checkSelfPermission` guard, or a documented `@RequiresPermission` on the calling function. If it is not guaranteed, this is a real crash path and needs a handled `SecurityException` with a safe default, not a broad catch (Rule 19).
>
> Do not reach for `@Suppress` or a baseline entry as the first move. This is a P0-category finding by the §13 taxonomy - a `SecurityException` from a foreground service kills the recording - and the strategic spec's whole argument is that burying findings is what got the gate abandoned.

**Verification:**

- `.\a.ps1 fk` passes and the `screenCapture` variant compiles.
- Targeted lint shows `ScreenVideoRecordingService.kt` clean of `MissingPermission`, or the finding is classified with a written reason for Phase 07.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Both call sites are the same one: `NotificationManagerCompat.notify` repainting the
  ongoing-recording notification after pause and after resume. The permission is **not** guaranteed -
  `ScreenRecordingLaunchActivity` requests POST_NOTIFICATIONS before starting the flow, but the user can
  revoke it while a recording runs - so this is the handled-`SecurityException` branch, not the
  `@RequiresPermission` one.
- Fixed at source rather than baselined: the two calls now go through one private
  `refreshNotification(paused)` that catches `SecurityException` and logs a plain-English degradation at
  `Timber.i`. This is the identical shape `core/save/SaveFallbackNotifier.kt:67` already uses for the
  same permission, so the codebase keeps one answer to this problem. Recovery is real: the recording is
  unaffected by a missing repaint, so a stale pause badge is the correct degradation and the foreground
  service does not die - which is what the P0 concern in the prompt was about.
- Verification: `.\a.ps1 fk` -> `BUILD SUCCESSFUL`, exit 0, `temp/S1195/phase06-fk.log`; the
  `screenCapture` source set is mounted by the `standard` flavor (`app_v2/build.gradle.kts:586`), so that
  compile covers it. Lint: `expected: 0 live MissingPermission | actual: 0`, `temp/S1195/phase06-lint.log`.

---

### Step 06.2 - `UseAppTint` in four layouts

**Files:** `app_v2/src/main/res/layout/item_gesture_picker_entry.xml`, `app_v2/src/main/res/layout/item_list_selection.xml`, `app_v2/src/launcherEnabled/res/layout/item_launcher_taskbar_add.xml`, `app_v2/src/launcherEnabled/res/layout/item_launcher_taskbar_icon.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace `android:tint` with `app:tint` on the flagged `ImageView` / `ImageButton` elements, adding the `xmlns:app` declaration where the file lacks it. `app:tint` is the AppCompat-backed attribute and is what the rest of the codebase should be using; `android:tint` is only honoured from API 21 with different behaviour and is not backported by AppCompat's image views.
>
> Rule 11 applies: for each file, check `res/layout-land/` for a counterpart and apply the same edit there. Two of these are `launcherEnabled` layouts, so check that source set's `layout-land/` too, not only `main`'s.
>
> Rule 19 also applies while you are in these files: no hardcoded `="#hex"` colours - if the tint value is a literal, move it to `?attr/` or `@color/`.

**Verification:**

- `Grep` - `android:tint` absent from all four files and from any `-land` counterpart edited.
- `.\a.ps1 fr` passes (resources and manifest).
- `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` does not regress.

**Status:** `[x]` done

**Step Log:**

- 2026-07-30 - Replaced all four reported `android:tint` attributes with `app:tint`; no
  `layout-land` counterparts exist. `a.ps1 fr` passed: `BUILD SUCCESSFUL`, exit 0,
  `temp/S1195/phase06-fr.out.log`. Per-file XML post-change gates passed.

---

### Step 06.3 - `RepeatOnLifecycleWrongUsage`, and what it says about the neuroslop gate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt`, plus the caller traced below
**Depends on:** - start of phase

**Prompt for developer:**

> Both findings are `lifecycleScope.launch { repeatOnLifecycle(..) { .. } }` called from `onStart`. Lint's objection is the call site, not the guard: "Wrong usage of repeatOnLifecycle from `CameraSettingsDialogFragment.onStart`" and "from `PhotoVideoStandaloneActivity.onStart`". Calling `repeatOnLifecycle` inside `onStart` launches a fresh collector on every lifecycle restart, so the collectors accumulate.
>
> `CameraSettingsDialogFragment.kt:90-99` guards with `if (rotationJob != null) return` and cancels symmetrically in `onDestroyView` (an S0924 comment documents this). Decide whether that guard makes the accumulation impossible in practice; if it does, the honest outcome is a baseline entry naming the guard, and if it does not, move the subscription to `onViewCreated`.
>
> The second finding needs tracing before it can be fixed. Its reported location is `utils/LifecycleExtensions.kt:39` - the project's own sanctioned `collectOnLifecycle` helper, the one Rule 19 tells everyone to use - but the message names `PhotoVideoStandaloneActivity.onStart` as the offender, because lint attributes through the inlined helper. `PhotoVideoStandaloneActivity.onStart` (line 1157) does **not** call `collectOnLifecycle` directly; it calls `viewManager.show(..)` and `setupVideoControls(..)`. Follow that chain to whichever class actually collects, and fix it there. **Do not edit `LifecycleExtensions.kt` on the strength of the reported location** - the helper is correct and is used by the whole codebase.

**Verification:**

- Both findings resolved: fixed, or classified with a written reason for Phase 07.
- `Grep` - `LifecycleExtensions.kt` unchanged, unless a written justification says otherwise.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Both findings traced; both are guarded, so both are keeps with a reason rather than code
  changes. `LifecycleExtensions.kt` untouched, as the prompt requires.
- `CameraSettingsDialogFragment.onStart:96` - the subscription cannot move to `onViewCreated`: this is a
  `DialogFragment` that builds its content in `onCreateDialog` and never returns a view from
  `onCreateView`, so `viewLifecycleOwner` does not exist and `lifecycleScope` in `onStart` is the only
  available host. Accumulation is impossible anyway: `if (rotationJob != null) return` admits exactly one
  launch, and `repeatOnLifecycle` suspends until DESTROYED rather than returning on stop, so a
  stop/start cycle re-enters the existing block instead of adding a second collector. The S0924
  cancellation in `onDestroyView` closes it.
- `PhotoVideoStandaloneActivity.onStart` (reported at `LifecycleExtensions.kt:39` because lint attributes
  through the inlined helper) - the chain is `onStart:1157` -> `viewManager.show(.., onVideoReady)` ->
  `setupVideoControls:1044` -> `setupPictureInPicture:1007`, which is where the `collectOnLifecycle` sits.
  It opens with `if (pipManager != null) return`, so the collector is registered once per Activity even
  though the entry point is a repeatable lifecycle edge.
- Baseline reason for Phase 07, both entries: "called from onStart but registered once - an idempotency
  guard admits a single launch, and repeatOnLifecycle suspends until DESTROYED, so no collector
  accumulates across stop/start."

---

### Step 06.4 - Record the gate-coverage finding and park it

**Files:** this plan file; a new Draft spec via `/spec-draft`
**Depends on:** Step 06.3

**Prompt for developer:**

> CLAUDE.md Rule 19 bans lifecycle-unsafe Flow collection and `scripts/quality/assert-neuroslop.ps1` carries an `unsafe-collect` dimension for it, yet lint found two violations while that gate is green. This was already diagnosed and the answer is structural, not a ratchet threshold: `scripts/quality/assert-unsafe-collect.ps1` ends its per-launch predicate with
>
> ```
> if ($body -match '(repeatOnLifecycle|flowWithLifecycle)') { return $false }
> ```
>
> A launch body containing `repeatOnLifecycle` is **explicitly exempted**. The gate tests whether the guard is present and never where it is called from, so this shape is invisible to it by construction. Confirm that line still reads this way, then park the gap with `/spec-draft` - dedup first via `scripts/spec_catalog/search.ps1` by symptom ("repeatOnLifecycle in onStart", "unsafe-collect gate blind spot").
>
> This is a sibling of the pattern §10 already names (S1191, S1193, S1194): a gate configured strictly that does not actually cover what it claims. It is out of scope here under CLAUDE.md §3.1 - park it, report `parked: Sxxxx`, and resume. Do not extend the gate in this ticket.

**Verification:**

- The exemption line confirmed present in `scripts/quality/assert-unsafe-collect.ps1`, quoted in this file with its line number.
- `parked: Sxxxx <slug>` recorded here and reported in chat, or the id of the existing ticket that already covers it.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Exemption confirmed still present, `scripts/quality/assert-unsafe-collect.ps1:69`:
  `if ($body -match '(repeatOnLifecycle|flowWithLifecycle)') { return $false }`. The script's own header
  states the intent at lines 15-16, so this is by design, not an oversight - the gate asks whether the
  guard is present and never where the call is made from.
- No new draft: dedup via `search.ps1 -Query "collect"` found **S1283
  `unsafe-collect-gate-exempts-the-broken-case`** (Draft, priority 45) already covering exactly this gap.
  Referenced instead of duplicated, per CLAUDE.md §3.1.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] All 8 platform errors resolved: fixed, or classified with a written reason ready for Phase 07.
- [ ] `.\a.ps1 fc` green, plus a compile of the `screenCapture` and `launcherEnabled` variants, cited.
- [ ] Rule 11 satisfied - every edited portrait layout has its `-land` counterpart checked.
- [ ] The `unsafe-collect` coverage gap parked as its own ticket, not fixed here.
- [ ] `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Mixed -ScopeToFile ..` closure run.
- [ ] Dev log entry added.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Baseline entries this phase hands to Phase 07 - two, both `RepeatOnLifecycleWrongUsage`:

- `CameraSettingsDialogFragment.kt:96`
- `LifecycleExtensions.kt:39` (message names `PhotoVideoStandaloneActivity.onStart`)

Justification text, verbatim, for both: "called from onStart but registered once - an idempotency guard
admits a single launch, and repeatOnLifecycle suspends until DESTROYED, so no collector accumulates
across stop/start."

`MissingPermission` and `UseAppTint` were fixed at source and hand nothing forward.

Existing ticket referenced instead of a new draft: **S1283** `unsafe-collect-gate-exempts-the-broken-case`
(Draft) - the `assert-unsafe-collect.ps1` blind spot. Record it in the strategic spec's §10 at closure,
per Phase 08's handoff note.

---

## Rollback Plan

Three independent groups - service permission handling, four layouts, one lifecycle call site. Revert individually.
