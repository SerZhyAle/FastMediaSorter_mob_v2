# S1362 research - Browse shutdown and worker cancellation

**Date:** 2026-08-03
**Question:** Does closing Browse cancel the WorkManager transfer, and which shared services are unsafe to reset while it runs?

## Evidence

- `BrowseFileTransferCoordinator` enqueues a unique `CoroutineWorker` and exposes `hasActiveTransfer()` by querying its WorkManager state.
- `BrowseFileTransferWorker.doWork()` delegates the batch to `FileOperationUseCase` and rethrows `CancellationException`; it is not owned by the Browse lifecycle.
- The only Browse transfer cancellation call is the progress dialog's explicit Cancel action. No Browse shutdown path calls `cancelActiveTransfer()` or cancels the transfer work name.
- `BrowseShutdownCoordinator.onShutdown()` unconditionally calls `ConnectionThrottleManager.cancelAllForResource()`. That reset removes the active resource state and semaphore.
- `BrowseShutdownCoordinator.launchPostShutdownCleanup()` unconditionally calls `UnifiedFileCache.clearAll()`, deleting cache files that a live network transfer may still use.
- `BrowseLifecycleSetupManager` also clears the unified cache during Browse initialization, so reopening Browse reproduces the same unsafe shared-cache cleanup.

## Decision

Treat the failure as lifecycle cleanup operating on process-shared transfer dependencies, not as a worker tied to the Activity. The shutdown and initialization paths must leave shared throttle/cache state intact while an interactive Browse transfer is active. Explicit user cancellation remains the only UI path that cancels the worker.

## Consequences

- The shutdown coordinator needs a transfer-activity predicate supplied by the Browse composition root.
- Cache cleanup must be guarded in both Browse shutdown and setup paths.
- The fix needs unit coverage for active and idle predicates; a real-device transfer remains the acceptance check because cloud I/O and Activity recreation are involved.
