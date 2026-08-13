# S1301 - Cancelled browse scan orphans an infinite 2s-tick progress coroutine on viewModelScope (loop body is dead code)

**Ticket:** S1301
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): coroutines-flows-1, observers-receivers-1.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Findings coroutines-flows-1/observers-receivers-1 describe the same defect from two dimensions.

## Finding 1: Cancelled browse scan leaks an infinite 2s-tick progress coroutine on viewModelScope

- Severity: P2, effort: trivial.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt:301`
- Symptom: Every media-file scan launches `progressJob` with `while (true) { delay(2000); ... }` directly on BrowseViewModel.viewModelScope as a sibling of the scan job. Its only cancellation sites live inside the scan's own success/error paths (BrowseLoadingManager.loadFilesStandard lines 144/167/209/217 and the non-cancellation catch at line 363). When the scan job itself is cancelled - STOP button on a network scan (BrowseViewModel.cancelScan line 764), forced cancel on Activity onStop, or a re-entrant loadMediaFiles()/subfolder navigation superseding an in-flight scan (currentScanJob.cancel at lines 294-296, loadFilesJob.cancel at BrowseViewModel line 785) - the CancellationException is rethrown at line 361, the flow's .catch never runs for collector cancellation, and the finally at lines 365-367 cancels only stopTimerJob. The orphaned loop then ticks every 2 seconds until onCleared(). The loop body is also dead code: it only writes a local var (`lastProgressUpdate`), so it never terminates on its own.
- Failure scenario: User keeps one Browse screen on a large SMB/SFTP share open for hours, navigating subfolders and occasionally pressing STOP or backgrounding the app mid-scan (onStop force-cancels the scan). Each such cancellation orphans one while(true) coroutine; after a long session dozens of coroutines accumulate on the IO dispatcher, each holding the manager/state references and waking a timer every 2 seconds - steady background CPU/battery churn and unbounded Job growth for the lifetime of that Browse ViewModel (which survives in the back stack while the app idles in background).
- Fix sketch: Make the loop's lifetime equal to the scan's: either launch progressJob inside filesJob (child job dies with the scan) or add progressJob.cancel() to the finally block at lines 365-367 and to cancelLoad(). Since the loop body no longer does any observable work (leftover from removed progress logging), the cleanest fix is deleting progressJob and its parameter threading through BrowseLoadingManager entirely.
- Verifier rationale: Confirmed by reading both files. progressJob (BrowseResourceLoadManager.kt:301) is launched directly on viewModelScope (BrowseViewModel.kt:445) as a sibling of filesJob, with an infinite while(true){delay(2000)} body whose only effect is writing a captured local var - it never exits on its own. Its only cancel sites are BrowseLoadingManager.kt:144/167/209/217 (flow error/empty/success completions) and BrowseResourceLoadManager.kt:363 (non-cancellation retry catch). When the scan job itself is cancelled - STOP via BrowseViewModel.cancelScan (line 764), loadFilesJob?.cancel() (lines 679/785), re-entrant loadMediaFiles (lines 294-296), or cancelLoad (374-379, which also skips progressJob) - the CancellationException is rethrown at line 361, the flow's .catch at BrowseLoadingManager.kt:142 does not run for collector cancellation (kotlinx catch only intercepts upstream exceptions), and the finally at 365-367 cancels only stopTimerJob. No reference to progressJob is stored anywhere, so nothing can cancel it afterwards; each cancelled scan orphans one 2s-tick coroutine until onCleared(). Not P1: each orphan is a cheap timer with no heavy resource, so this is leaked-coroutine churn / missing lifecycle-awareness rather than unreleased heavy resource. Fix is trivial: cancel progressJob in the finally, or delete the dead progressJob entirely along with its parameter threading.

Evidence excerpt:

```
val progressJob = scope.launch(ioDispatcher) {
    while (true) {
        delay(2000)
        val p = stateFlow.value.loadingProgress
        if (p > 0 && p != lastProgressUpdate) lastProgressUpdate = p
    }
}
...
try {
    loadMediaFilesStandard(resourceForScan, sizeFilter, progressJob)
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    ...
} finally {
    stopTimerJob?.cancel()   // progressJob NOT cancelled on the cancellation path
}
```

## Finding 2: Scan progress ticker coroutine orphaned on every cancelled or superseded browse scan

- Severity: P3, effort: trivial.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt:301`
- Symptom: Each browse rescan that interrupts an active scan, and each user STOP of a scan, leaves behind an infinite `while (true) { delay(2000) }` coroutine on viewModelScope that ticks every 2 seconds until the Browse window's ViewModel is cleared; the tickers accumulate one per interrupted scan and keep waking the IO dispatcher even while the app sits in the background.
- Failure scenario: User opens a large SMB/SFTP share whose scan runs for minutes, and over an hours-long session repeatedly presses STOP or triggers rescans (external file changes via FileObserver debounced reload, pull-to-refresh, sort/filter changes that restart loadMediaFiles). Every scan interrupted before BrowseLoadingManager.loadFilesStandard reaches one of its progressJob?.cancel() completion paths (BrowseLoadingManager.kt:144/167/209/217) orphans one infinite 2-second ticker. After N interruptions, N coroutines wake every 2s for the remaining life of the Browse ViewModel - including while the activity is backgrounded but not destroyed - producing steady needless CPU wakeups (battery drain) and unbounded Job accumulation in viewModelScope within the session. The loop body only writes a local variable, so all of this work is pure waste.
- Fix sketch: The loop body is vestigial (lastProgressUpdate is a local never read elsewhere), so simplest is to delete progressJob and its parameter threading entirely. If a progress watchdog is ever needed again, launch it inside filesJob so structured concurrency cancels it with the scan, or cancel it explicitly in cancelLoad() and at the top of loadMediaFiles() next to currentScanJob?.cancel().
- Verifier rationale: Confirmed. progressJob (BrowseResourceLoadManager.kt:301) launches directly on viewModelScope (BrowseViewModel.kt:445) as a sibling of filesJob, so cancelling the scan does not cancel it. All interrupt paths - rescan restart (l.294-296 cancels only currentScanJob), cancelLoad (l.374-379), cancelScan force/network (BrowseViewModel.kt:764) - skip progressJob; the only cancel() calls are on normal-completion/error paths (BrowseLoadingManager.kt:144/167/209/217), and the CancellationException rethrow at BrowseResourceLoadManager.kt:361 bypasses the cancel at l.363 while the finally (l.365-367) cancels only stopTimerJob. Each interrupted scan therefore orphans one infinite 2s-delay ticker whose body only writes a local (lastProgressUpdate, never read elsewhere - grep confirms). Severity capped at P3: leak is bounded by the Browse ViewModel's lifetime (onCleared cancels viewModelScope), the graceful local-scan STOP path does reach a cancel, each ticker holds no resource beyond a timed wakeup, and realistic accumulation is tens of trivial coroutines - wasted work and missing structured-concurrency hygiene, not OOM/ANR/heavy-resource risk. Fix is deleting the vestigial ticker and its parameter threading.

Evidence excerpt:

```
val progressJob = scope.launch(ioDispatcher) {
    while (true) {
        delay(2000)
        val p = stateFlow.value.loadingProgress
        if (p > 0 && p != lastProgressUpdate) lastProgressUpdate = p
    }
}
// scope = viewModelScope (BrowseViewModel.kt:445). progressJob is NOT a child of filesJob.
// loadMediaFiles() restart path cancels only the scan job: `if (currentScanJob?.isActive == true) { currentScanJob?.cancel() }` (l.294-296)
// cancelLoad() cancels only loadFilesJob/currentScanJob (l.374-379); CancellationException rethrow at l.361 skips progressJob.cancel()
```

