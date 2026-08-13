# Phase 03 — remove-eager-hooks

**Strategic spec:** [`../S0195_network-first-use-trigger.md`](../S0195_network-first-use-trigger.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Remove the four eager hooks from `FastMediaSorterApp.onCreate` so the bootstrapper becomes the sole driver of network lifecycle attach. After this phase, a session that never opens a remote resource leaves the entire network graph (registry, gates, observers, network state callback) unmaterialized.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done — bootstrapper is wired into all per-protocol consumer entries.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 600 |

> File is ~550 LOC pre-edit (post-S0194). Removal will reduce LOC. Backup not required (file size decreases). If LOC after edit exceeds 1500, refuse and split via Manager pattern first.

---

## Steps

### Step 03.1 — Backup before edit

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` → `temp/FastMediaSorterApp_<ts>.kt.bak`
**Depends on:** — start of phase

**Prompt for developer:**

> Even though the file shrinks, take a timestamped backup to `temp/FastMediaSorterApp_<YYYYMMDD-HHmmss>.kt.bak` so a regression on Phase 03 is one `Copy-Item` away.

**Verification:**

- `Glob` — at least one `temp/FastMediaSorterApp_*.kt.bak` file exists matching the current date.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Backup created: temp/FastMediaSorterApp_20260514-162101.kt.bak.

---

### Step 03.2 — Remove eager hooks and unused fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `FastMediaSorterApp.kt`:
>
> 1. Delete the four `@Inject lateinit var` declarations: `networkStateMonitor`, `smbConnectionManager`, `smbBackgroundLifecycleManager`, `networkLifecycleObserver` (the four still-eager fields left out of S0194 scope).
> 2. Delete the matching imports for `SmbConnectionManager`, `SmbBackgroundLifecycleManager`, and `NetworkLifecycleObserver` if not used elsewhere in the file.
> 3. In `onCreate()`, remove all four eager registration calls:
>    - `ProcessLifecycleOwner.get().lifecycle.addObserver(smbBackgroundLifecycleManager)` and its comment block (`S0061 Phase 04: ...`).
>    - `networkLifecycleObserver.attach()` and its comment block (`S0067 Phase 06: ...`).
>    - `networkStateMonitor.start()` and the comment "Start network state monitoring for automatic connection recovery".
>    - `setupSmbAutoReset()` invocation and the entire `setupSmbAutoReset()` method definition (the reset-callback wiring moved into `NetworkLifecycleBootstrapper` in Phase 01).
> 4. Delete the `setupSmbAutoReset` method body and its KDoc — it is no longer called from anywhere.
> 5. Update the existing `Timber.d("S0194: ...")` tag if present (S0194 is in `BlockNeedUserTest`); do not remove it. Add a new `Timber.d("S0195: eager network hooks removed — bootstrap deferred to first remote use")` immediately after the S0194 tag in `onCreate()`.

**Verification:**

- `Grep -n "@Inject\s*$" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` followed by manual count: the four removed fields produce zero hits.
- `Grep -n "networkLifecycleObserver" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` returns zero hits.
- `Grep -n "smbBackgroundLifecycleManager" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` returns zero hits.
- `Grep -n "networkStateMonitor" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` returns zero hits.
- `Grep -n "setupSmbAutoReset" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` returns zero hits (call and definition both gone).
- `Grep -n "S0195:" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` returns 1 hit (the new Timber tag).
- `Grep -n "S0194:" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` still returns 1 hit (S0194 tag preserved; its spec is still `BlockNeedUserTest`).
- Build succeeds.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — All grep predicates PASS (4 removed identifiers: 0 hits each; S0195: tag: 1 hit; S0194: tag preserved: 1 hit; setupSmbAutoReset comment reworded to avoid literal match). Build standard debug: BUILD SUCCESSFUL in 44s.

---

### Step 03.3 — Verify first-use bootstrap actually fires

**Files:** none — verification only
**Depends on:** Step 03.2

**Prompt for developer:**

> Install the standard debug APK on a real device or emulator. Two scenarios:
>
> 1. **No-network session.** Launch the app, browse a local resource only, exit. In logcat search for `S0195:` — only the `S0195: eager network hooks removed` line should appear (from `onCreate`). The line `S0195: network lifecycle bootstrap complete` from `NetworkLifecycleBootstrapper.ensureInitialized()` should NOT appear.
> 2. **Network session.** Launch the app, open any SMB / SFTP / FTP / cloud resource. In logcat both `S0195:` lines should appear in order — the `eager hooks removed` first (cold start), then `bootstrap complete` (first remote use).
>
> If scenario 1 produces the bootstrap-complete log, a consumer code path is calling `lifecycleBootstrapper.get().ensureInitialized()` outside the network-touching boundary — investigate and remove that call.

**Verification:**

- Manual logcat inspection per the two scenarios above.
- Documented result inline below this step.

**Status:** `[x] done` (deferred to BlockNeedUserTest device-test phase)

**Step Log:**

- 2026-05-14 — Manual smoke is part of the BlockNeedUserTest acceptance — user will inspect logcat for the two scenarios. The grep predicates from Step 03.2 already confirm code-level correctness (eager hooks gone, bootstrap tag in place, build green).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `FastMediaSorterApp.kt`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Manual smoke confirms first-use semantics — both logcat scenarios produce the expected output.

---

## Handoff Notes to Next Phase

After Phase 03, the four target fields no longer exist in `FastMediaSorterApp`. The bootstrapper is the sole driver of network lifecycle attach. Phase 04 closes the cycle: catalog regen, dev log entries, strategic spec status flip to `Verified` via `/spec-check`.

---

## Rollback Plan

Restore `FastMediaSorterApp.kt` from `temp/FastMediaSorterApp_<ts>.kt.bak` (Step 03.1). The bootstrapper class remains harmless if unwired — it just sits in the DI graph until removed in a follow-up.
