# S1230 - The active-transfer flow reads SharedPreferences on the main thread on every emission

**Status:** Archived
**Priority:** 60

## 0. Raw capture

Found 2026-07-27 while verifying S1226 on the Quest 3, from `temp/scratch/vr_session_20260727-2224.log`. Not reported by the owner - the throttle fix simply made the pattern visible.

## 1. Symptom

Every progress emission of a running transfer triggers a StrictMode disk-read violation on the main thread:

```
23:33:11.324  2238 2238 D StrictMode: StrictMode policy violation; ~duration=2 ms:
                                      android.os.strictmode.DiskReadViolation
  .. at BrowseFileTransferCoordinator$activeTransferFlow$$inlined$map$1$2.emit(Emitters.kt:226)
```

Thread id equals the process id (2238), so this is the main thread. Several violations fire per emission, 1-2 ms each.

## 2. Mechanism

`ui/browse/transfer/BrowseFileTransferCoordinator.kt`, `activeTransferFlow()`:

```kotlin
return workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
    ..
    request = if (state == ENQUEUED || RUNNING || BLOCKED) {
        requestStore.readActiveRequest()      // SharedPreferences read
    } else null,
    ..
}
```

The `map` runs on the collector's dispatcher - the UI thread, since the consumer is `collectOnLifecycle` in `BrowseFileOperationsManager`. `requestStore.readActiveRequest()` goes to `SharedPreferences`, i.e. disk. The active request does not change between emissions of the same work, so the read is not only main-thread, it is also redundant.

Violates the "Room/IO main-safety" line of the audit protocol: I/O belongs at the repository/data-source boundary with `withContext(Dispatchers.IO)`, not inside a UI-collected `map`.

## 3. Why it surfaced now

Before **S1226** the emission rate was ~50/s, so this was ~50 main-thread disk reads per second during any transfer - the throttle cut it to ~1/s and made the remaining violations legible in the log rather than a wall of noise. The defect predates the throttle.

## 4. Fix direction

- Move the read off the collector: `.map { .. }.flowOn(Dispatchers.IO)`, or read the request once when the work id changes rather than per emission.
- Caching by work id is probably the better shape - the value is constant for the lifetime of one transfer, so re-reading it per progress tick is wasted work regardless of which thread it runs on.

## 5. Related

- **S1226** - the throttle that exposed this.
- **S1225** - the unification ticket; whatever owns transfer progress state afterwards should not re-read persisted request data per tick.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1226 (throttle that exposed it), S1225 (progress-state unification)

## 6. Fix (2026-07-28, spec-next loop)

One factual correction first: `readActiveRequest()` is not SharedPreferences - the store is a
JSON file (`files/browse_file_transfer/active_request.json`, `File.readText` + Gson parse under
a lock). Worse than a prefs hit, since prefs are memory-cached after first load while this
re-reads and re-parses the file on every emission. The mechanism and the violation are as
captured; only the §1/§2 storage naming was off.

Both §4 directions applied together in `activeTransferFlow()`:

- Cache per work id: the request is written once (`enqueueIfIdle` persists it before enqueue)
  and is immutable for the lifetime of one work id, so the map now re-reads the store only when
  the id changes. `var` captures in the returned flow keep the cache per-collection, and the
  sequential `map` contract makes them race-free.
- `.flowOn(Dispatchers.IO)` on the mapped flow: the one real read plus the progress decode run
  upstream on IO by Flow contract; the single collector (`BrowseFileOperationsManager` via
  `collectOnLifecycle`) still receives emissions on main.
- State branching now uses `WorkInfo.State.isFinished` - finished is exactly
  SUCCEEDED/FAILED/CANCELLED, so non-finished is exactly the old ENQUEUED/RUNNING/BLOCKED set;
  behaviour is unchanged, the long explicit comparisons are gone.
- Rule-7 cleanup while in the file: the line-shift resurfaced the coordinator's pre-existing
  detekt debt (over-long `isActive`/`isTerminal` getters, unwrapped `MutableSharedFlow` args,
  the `hasActiveTransfer` predicate) - all rewritten via the same `isFinished` equivalence /
  argument wrapping; the file is now detekt-clean (project-wide finding count dropped by one
  file).

## 7. Verification

- Compile: `.\a.ps1 fk` after the change - BUILD SUCCESSFUL, "Fast check passed" (39s).
  expected: clean compile | actual: PASS.
- Main-safety holds by construction: everything inside the `map` executes on `Dispatchers.IO`
  (`flowOn` moves the upstream context), so no code path in this flow can touch disk on the
  collector's thread any more.
- Cache correctness: a fresh collection starts with a null cache (first emission reads the
  store); a new work id invalidates it; process restart recreates the flow and the cache.
- Behavioural no-op elsewhere: the only consumer collects the same `TransferWorkState` values
  on the same thread as before.
- The next VR/device session's StrictMode log doubles as a free regression probe: any
  `DiskReadViolation` at `BrowseFileTransferCoordinator..emit` would mean this fix regressed.

## Last Audit

**Date:** 2026-07-28. **Verdict:** Verified.

- P1 (main-thread disk I/O per progress emission) removed by construction: per-work-id cache +
  `flowOn(Dispatchers.IO)` on the mapped WorkManager flow; branch sets preserved via
  `WorkInfo.State.isFinished` equivalence.
- Store naming in §1/§2 corrected: JSON file + Gson parse per read, not SharedPreferences -
  strictly worse than assumed, same fix.
- Evidence: clean `fk` compile; single collector unchanged (`collectOnLifecycle` on main);
  Flow context contract carries the main-safety proof.
