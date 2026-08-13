# Phase 06 — Lifecycle Observer + Diagnostics Surfacing

**Strategic spec:** [`../S0067_enh-network-stale-connection-invalidation-multi-protocol.md`](../S0067_enh-network-stale-connection-invalidation-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, 03, 04, 05
**Blocks:** Phase 07
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Wire `ProcessLifecycleOwner` observer to call `closeFor(UI_*)` across all gates on `ON_STOP`. Surface `ConnectionDiagnostics.InstabilityWarning` to a single throttled snackbar via existing UI broadcast channel. Trilingual strings.

---

## Prerequisites

- [ ] Phases 02–05 ✅ Done.
- [ ] `FastMediaSorterApp.kt` exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/lifecycle/NetworkLifecycleObserver.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 250 |
| `app_v2/src/main/res/values/strings.xml` | Modified | (n/a) |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | (n/a) |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | (n/a) |

---

## Steps

### Step 06.1 — Implement `NetworkLifecycleObserver`

**Files:** `core/lifecycle/NetworkLifecycleObserver.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Define `@Singleton class NetworkLifecycleObserver @Inject constructor(private val registry: ConnectionGateRegistry) : DefaultLifecycleObserver`.
>
> - `override fun onStop(owner: LifecycleOwner)` — iterate `registry.all()` and call:
>   - `gate.closeFor(ConsumerType.UI_SCANNER)`
>   - `gate.closeFor(ConsumerType.UI_PLAYER)`
>   - `gate.closeFor(ConsumerType.UI_OPERATION)`
>   - **No** call for `BACKGROUND_WORKER`.
>   - Log a single line: `Timber.i("[scope=lifecycle event=ON_STOP gates=${registry.all().size}]")`.
> - Add `fun attach()` that registers itself on `ProcessLifecycleOwner.get().lifecycle`.

**Verification:**

- `Glob` — `NetworkLifecycleObserver.kt` exists.
- `Grep -n "class NetworkLifecycleObserver"` matches once.
- `Grep -n "DefaultLifecycleObserver"` matches once.
- `Grep -n "ProcessLifecycleOwner"` matches once.
- `Grep -n "BACKGROUND_WORKER"` does **not** match in this file (negative check — workers are never closed).

**Status:** `[ ]` not done

---

### Step 06.2 — Attach observer in `FastMediaSorterApp`

**Files:** `FastMediaSorterApp.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> In `FastMediaSorterApp.onCreate`, inject `NetworkLifecycleObserver` (Hilt EntryPoint or `@Inject lateinit` if `@HiltAndroidApp` is in use) and call `observer.attach()` after the existing application init code.

**Verification:**

- `Grep -n "NetworkLifecycleObserver" "FastMediaSorterApp.kt"` matches at least 2 (import + usage).
- `Grep -n "observer.attach()"` or `lifecycleObserver.attach()` matches once.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 06.3 — Add trilingual strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add three string keys to all three files:
>
> - `<string name="network_unstable_snackbar">Network unstable — check Wi-Fi</string>`
> - RU: `<string name="network_unstable_snackbar">Сеть нестабильна — проверьте Wi-Fi</string>`
> - UK: `<string name="network_unstable_snackbar">Мережа нестабільна — перевірте Wi-Fi</string>`

**Verification:**

- `Grep -n "network_unstable_snackbar" "res/values/strings.xml"` matches once.
- `Grep -n "network_unstable_snackbar" "res/values-ru/strings.xml"` matches once.
- `Grep -n "network_unstable_snackbar" "res/values-uk/strings.xml"` matches once.

**Status:** `[ ]` not done

---

### Step 06.4 — Surface `InstabilityWarning` flow to UI

**Files:** `core/lifecycle/NetworkLifecycleObserver.kt` (extend) or new `NetworkInstabilitySurface.kt` — choose minimal-touch
**Depends on:** Step 06.3

**Prompt for developer:**

> Extend `NetworkLifecycleObserver` (or a separate `@Singleton class NetworkInstabilitySurface @Inject constructor(diagnostics: ConnectionDiagnostics, ...)`) that:
>
> 1. Collects `diagnostics.events` flow on `Dispatchers.Main.immediate`.
> 2. On every `InstabilityWarning(p, k, count)` event, calls existing UI broadcaster (find via `Grep -n "showSnackbar\\|emitSnackbar\\|SnackbarBus"` — pick the established one) with `R.string.network_unstable_snackbar`.
> 3. Throttles emissions: at most one snackbar per `(protocol, resourceKey)` per 5 minutes (`ConcurrentHashMap<String, Long>`).
> 4. Starts collection in `attach()`.

**Verification:**

- `Grep -n "InstabilityWarning"` returns hits in both `ConnectionDiagnostics.kt` and `NetworkLifecycleObserver.kt` (or `NetworkInstabilitySurface.kt`).
- `Grep -n "network_unstable_snackbar"` returns hits in the surface file.
- `Grep -n "5 \\* 60 \\* 1000\\|300_000"` matches in the surface file (5-minute throttle).
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 06.5 — Verify SMB lifecycle behavior unchanged

**Files:** none modified — verification only
**Depends on:** Step 06.4

**Prompt for developer:**

> Confirm that the existing SMB lifecycle close (S0061) is now driven through `SmbConnectionGate.closeFor(UI_*)` and that any **direct** call from `FastMediaSorterApp` or related observers to `SmbConnectionManager.closeAllConnections()` is removed (or marked deprecated). Do not break worker-tagged sessions.

**Verification:**

- `Grep -n "smbConnectionManager\\.closeAllConnections" -r app_v2/src/main` returns zero hits OR each remaining hit is either inside `SmbConnectionGate` or guarded by `// S0067 deprecated`.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `/build` `standardDebug` PASS.
- [ ] Trilingual `Grep` predicates above all return one hit each.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry per "Files Touched".

---

## Handoff Notes to Next Phase

After Phase 06 the full pipeline is wired: gates → registry → lifecycle observer → diagnostics → snackbar. Phase 07 closes catalog/docs.

---

## Rollback Plan

Revert phase commit. Lifecycle observer is additive — original SMB behavior remains since Step 06.5 only re-routes existing calls through the gate (calls remain idempotent).
