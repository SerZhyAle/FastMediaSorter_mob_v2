# S1309 - RandomPhotoFrameRefreshWorker performs the whole Room+thumbnail refresh twice per 30-minute tick

**Ticket:** S1309
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): services-5.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: RandomPhotoFrameRefreshWorker performs the whole Room+thumbnail refresh twice per 30-minute tick

- Severity: P3, effort: trivial.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/worker/RandomPhotoFrameRefreshWorker.kt:49`
- Symptom: doWork() calls RandomPhotoFrameWidgetRefresher.refresh(...) and then RandomPhotoFrameWidgetProvider.updateAppWidget(...), but updateAppWidget itself calls refresh() again for any configured snapshot (RandomPhotoFrameWidgetProvider.kt:84-85). Every periodic tick therefore runs the runBlocking cached-list read, random pick, thumbnail resolution, file-exists checks and snapshot write twice per widget; the first pick is discarded.
- Failure scenario: User keeps a Random Photo Frame widget for weeks: every 30-minute WorkManager run does double the Room/gzip/Gson and disk IO per placed widget for the app's whole installed lifetime - wasted background CPU/IO on every tick, worse with multiple widget instances.
- Fix sketch: Drop the standalone refresh() call in doWork() and let updateAppWidget() do the single refresh, or add an updateAppWidget overload that accepts an already-computed snapshot.
- Verifier rationale: Confirmed. doWork() (lines 48-55) calls RandomPhotoFrameWidgetRefresher.refresh() and then RandomPhotoFrameWidgetProvider.updateAppWidget(); updateAppWidget (Provider lines 83-88) calls refresh() again whenever the stored snapshot isConfigured, so the worker's standalone refresh() produces a pick that is immediately discarded and all the work (described by the provider's own S0870 comment as a runBlocking Room+gzip+Gson round-trip plus thumbnail/file checks) runs twice per widget per 30-minute tick. Genuinely redundant, but bounded, battery-not-low-constrained background waste with no functional misbehavior beyond an extra random pick: P3, remove the redundant call.

Evidence excerpt:

```
appWidgetIds.forEach { appWidgetId ->
    RandomPhotoFrameWidgetRefresher.refresh(applicationContext, appWidgetId)   // pick #1 (discarded)
    RandomPhotoFrameWidgetProvider.updateAppWidget(...)                        // internally refresh() again -> pick #2
}
// Provider: val snapshot = if (storedSnapshot.isConfigured) RandomPhotoFrameWidgetRefresher.refresh(context, appWidgetId) ...
```

