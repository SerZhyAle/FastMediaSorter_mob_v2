# Phase 02 - Shortcut trampoline

**Strategic spec:** [`../S1471_stream-shortcut-starts-background-playback.md`](../S1471_stream-shortcut-starts-background-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Add the transparent `StreamPlayLaunchActivity` trampoline, register it in the manifest, and retarget newly created home-screen shortcuts at it, so a shortcut tap either plays through the service with no screen or forwards to the Streams screen unchanged.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/StreamPlayLaunchActivity.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamShortcutRouteManager.kt` | New | ≤ 70 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | +4 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamShortcutPinManager.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1340 |

> `StreamsActivity.kt` is over 500 LOC - Step 02.4 carries an explicit backup sub-step per CLAUDE.md Rule 5.

---

## Steps

### Step 02.1 - Add the trampoline activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/StreamPlayLaunchActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@AndroidEntryPoint class StreamPlayLaunchActivity : AppCompatActivity()` in `widget/`, modelled on the sibling trampolines in that package. Inject exactly one collaborator, `StreamShortcutRouteManager`. In `onCreate`, read the URL from the intent extra, and in a `lifecycleScope.launch` ask the manager for the channel that qualifies for screen-less play. When it answers null, start `StreamsActivity.createPlayShortcutIntent(this, url)` and `finish()`. Otherwise call `StreamHeadlessPlayManager(this).play(source) { finish() }`. Add a `companion object` with `const val EXTRA_STREAM_URL` and `fun createIntent(context: Context, url: String): Intent` targeting this class with `FLAG_ACTIVITY_NEW_TASK`. The activity must call `finish()` on every terminal branch and must never call `setContentView`.
>
> **Plan correction, 2026-08-08.** This step first told the developer to inject `GetStreamSourceByUrlUseCase`, `SettingsRepository`, `CapabilityAvailability` and `NetworkContextAnalyzer` straight into the Activity. That shape was implemented and then rejected by the `activity-logic` gate in `post-change.ps1`: "new domain-layer field injection in an Activity (+2)", which is CLAUDE.md Rule 3. The four collaborators now live in `StreamShortcutRouteManager` (Step 02.1a) and the Activity injects that manager alone. The decision logic did not change.

**Why:**

Strategic §3.2 requires an Activity rather than a receiver, because a pinned shortcut must target an Activity and because starting the media foreground service from a visible Activity start avoids the Android 12+ background-start restriction.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/widget/StreamPlayLaunchActivity.kt` exists.
- `Grep` - `class StreamPlayLaunchActivity` matches exactly once in that file.
- `Grep` - `@AndroidEntryPoint` present in that file.
- `Grep` - `setContentView` returns zero hits in that file.
- `Grep` - `routeManager.headlessSource` present in that file. (Was `qualifiesForHeadlessPlay`; the plan correction above moved that call into `StreamShortcutRouteManager`, so the predicate now names the collaborator the Activity actually asks.)
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

---

### Step 02.1a - Move the trampoline's collaborators into a manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamShortcutRouteManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class StreamShortcutRouteManager @Inject constructor(..)` in `ui/streams/helpers/` taking `GetStreamSourceByUrlUseCase`, `SettingsRepository`, `CapabilityAvailability` and `NetworkContextAnalyzer`. Give it one public method `suspend fun headlessSource(url: String): StreamSourceEntity?` returning the channel when it qualifies for screen-less play and null when the caller must open the Streams screen instead. Read the background-audio gate the way `StreamsActivity.isBackgroundAudioEnabled` does - the user's setting AND the capability - and delegate the final decision to `StreamHeadlessPlayManager.qualifiesForHeadlessPlay`. Keep it to two returns so detekt's ReturnCount holds.

**Why:**

Rule 3 forbids an Activity from holding domain-layer collaborators, and the mechanical `activity-logic` gate enforces it - the first shape of Step 02.1 failed that gate with two new domain injections, so the trampoline needs one UI-layer collaborator instead of four domain ones.

**Verification:**

- `Grep` - `class StreamShortcutRouteManager` matches exactly once in that file.
- `Grep` - `suspend fun headlessSource` present in that file.
- `Grep` - `GetStreamSourceByUrlUseCase` returns zero hits in `StreamPlayLaunchActivity.kt`.
- `post-change.ps1` `activity-logic` reports `new occurrences 0` for the changed set.

**Status:** `[x] done`

---

### Step 02.2 - Register the trampoline in the manifest

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add an `<activity>` entry for `.widget.StreamPlayLaunchActivity` next to the other trampolines with `android:exported="false"`, `android:theme="@style/Theme.FastMediaSorter.Transparent"`, `android:excludeFromRecents="true"`, `android:taskAffinity=""` and `android:noHistory="true"`. Add one comment line stating that `noHistory` is safe here because this trampoline awaits no activity result, unlike the S1174 capture trampolines above it.

**Why:**

Strategic §3.2 fixes this exact attribute set and states that `noHistory` is safe only because the trampoline never uses `startActivityForResult`, which is the combination that silently dropped every capture in S1174.

**Verification:**

- `Grep` - `.widget.StreamPlayLaunchActivity` present in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` - the same line contains `android:noHistory="true"` and `android:excludeFromRecents="true"`.
- `.\a.ps1 fr` exits 0.

**Status:** `[x] done`

---

### Step 02.3 - Point newly pinned shortcuts at the trampoline

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamShortcutPinManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Replace `setIntent(StreamsActivity.createPlayShortcutIntent(context, source.url))` with `setIntent(StreamPlayLaunchActivity.createIntent(context, source.url))` and drop the now-unused `StreamsActivity` import. Update the class KDoc: the shortcut's tap intent now starts background playback without opening the Streams screen, and falls back to that screen when background playback does not apply.

**Why:**

Strategic §1 identifies this intent as the reason every shortcut tap builds the whole Streams screen before any sound starts.

**Verification:**

- `Grep` - `StreamPlayLaunchActivity.createIntent` present in `StreamShortcutPinManager.kt`.
- `Grep` - `createPlayShortcutIntent` returns zero hits in `StreamShortcutPinManager.kt`.
- `Grep` - `import com.sza.fastmediasorter.ui.streams.StreamsActivity` returns zero hits in that file.

**Status:** `[x] done`

---

### Step 02.4 - Keep `createPlayShortcutIntent` as the fallback entry only

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Back up `StreamsActivity.kt` to `temp/S1471/` with a timestamped name before editing (Rule 5, the file is over 500 LOC). Then update the KDoc of `createPlayShortcutIntent` only: it is no longer what a pinned shortcut carries - it is the fallback the trampoline starts when background playback does not apply. Change no behaviour, no signature, and do not touch `createPlayIntent`, `handlePlayIntent` or `onPlay`.

**Why:**

Strategic §3.1 requires that in-app navigation keeps today's behaviour, because the main-window streams panel reaches this same screen through `createPlayIntent` and its purpose is to open the screen.

**Verification:**

- `Glob` - a timestamped `StreamsActivity` backup exists under `temp/S1471/`.
- `Grep` - `fun createPlayShortcutIntent` still present in `StreamsActivity.kt`.
- `Grep` - `fun createPlayIntent` still present in `StreamsActivity.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Phase-boundary audit - 2026-08-08

Protocol: `docs/CODE_AUDIT_PROTOCOL.md`, Layers 1-3 (no Room surface touched).

- **Cleared - the `noHistory` class of defect does not apply here.** S0790-S0794 lost every capture because a `noHistory` trampoline used `startActivityForResult`: launching the host backgrounded the trampoline, the system destroyed it, and the result had nowhere to return. This trampoline's fallback branch uses `startActivity` and awaits nothing, which is what the manifest comment states.
- **Cleared - the foreground-service start is not a background start.** In the play branch `finish()` runs inside the `onFinished` the manager passes to `playAudioWithMetadata`, so the Activity is still the top activity when the media service is promoted. Established by reading the ordering, not by running it; the on-device check belongs to the ticket's `BlockNeedUserTest` gate.
- **P3 - a cancelled lookup makes the shortcut look dead.** `route()` runs in `lifecycleScope`, so an Activity destroyed before `getStreamSourceByUrl` returns cancels the coroutine and no branch runs at all. Accepted: the lookup is a single indexed read and the trampoline is the top activity throughout. No fix.
- **P3 - `EXTRA_STREAM_URL` duplicates the value of `StreamsActivity.EXTRA_STREAM_URL`.** Accepted: these are two independent intent contracts, and collapsing them would make the trampoline's public surface depend on the screen it exists to avoid starting.
- Layer 1: placement in `widget/` matches the sibling trampolines, and injecting collaborators straight into a trampoline follows the precedent already set by `CameraQuickCaptureActivity`. 89 LOC against a 130 budget. Layer 2: the only coroutine is lifecycle-scoped; no listener or observer is registered, so none can leak. Layer 3: the service connection is owned by `StreamHeadlessPlayManager`, whose application-context binding was the Phase 01 P1 fix.

**Plan inaccuracy, not a code defect.** The phase file budgets `StreamsActivity.kt` at 1340 lines, but the file was already 1343 before this phase touched it and is 1348 after a KDoc-only edit. Rule 2's real ceiling is 1500, so nothing is breached; the budget in the plan was simply written low.

---

## Handoff Notes to Next Phase

`StreamPlayLaunchActivity.createIntent` is the single factory for the screen-less entry. Phase 03 routes the launcher tile through it and Phase 04 rewrites already-pinned shortcuts to it; neither re-implements the decision.

---

## Rollback Plan

Revert phase commit(s). Shortcuts pinned while the change was live keep an intent to a class that still exists after a revert only if the trampoline file survives - revert the `StreamShortcutPinManager` change first and re-pin, or keep the trampoline class in place.
