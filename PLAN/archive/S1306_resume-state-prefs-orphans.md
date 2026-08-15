# S1306 - Per-window resume_state_prefs_<uuid>.xml SharedPreferences files are orphaned forever

**Ticket:** S1306
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): room-datastore-2.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: Per-window resume_state_prefs_<uuid>.xml SharedPreferences files are orphaned forever

- Severity: P2, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResumeStateRepositoryImpl.kt:38`
- Symptom: ResumeStateRepositoryImpl creates one SharedPreferences file per windowId ('resume_state_prefs_$windowId'). BrowseEventHandler mints a fresh java.util.UUID.randomUUID() windowId for every 'open in new window' / 'tear off' action (openBrowseInNewWindow line 275, openPlayerInNewWindow line 293, tearOffBrowse line 308). The player saves resume state into that file on every position save (PlayerViewerFactory: onPositionSaved = { saveResumeState() }). clearState(windowId) runs only on explicit back-exit; if the user swipes the window away from recents or the process dies, the UUID is lost and the XML file is never read again - startup resume (MainResumePlaybackHelper) only reads WINDOW_ID_MAIN. Grep confirms no sweep of resume_state_prefs_* files exists, and Settings Clear cache deletes only cacheDir, never shared_prefs. The 48h RESUME_TTL_MS is enforced only on read, and orphaned files are never read.
- Failure scenario: User routinely uses multi-window ('open in separate window') to play videos and dismisses those windows from recents. Each session leaves one orphan resume_state_prefs_<uuid>.xml in shared_prefs/; after months of daily use, hundreds to thousands of dead XML files accumulate in the app's data directory with no user-accessible way to remove them short of Clear data, and each file touched during a session also stays pinned in the framework's static SharedPreferences cache for the process lifetime.
- Fix sketch: In AppStartupInitializer add a deferred sweep: list files in context.dataDir/shared_prefs matching resume_state_prefs_* (excluding the 'main' slot), delete any whose lastModified is older than RESUME_TTL_MS (48h already defined in this class). Alternatively use context.deleteSharedPreferences(name) per stale name.
- Verifier rationale: Verified: BrowseEventHandler mints UUID.randomUUID() windowIds at lines 275/293/308; all clearState call sites for non-main ids live inside the window's own ViewModel/managers (explicit back-exit or resource change), so swipe-from-recents or process death orphans the file; MainResumePlaybackHelper reads only WINDOW_ID_MAIN; grep across src/main finds no shared_prefs enumeration and no deleteSharedPreferences call anywhere, and the 48h TTL is enforced only on read. Accumulation is genuinely unbounded with no user remedy short of Clear data. Impact per file is tiny (sub-KB XML), so this sits at the low end of P2 - unbounded persistent accumulation with missing lifecycle cleanup, not a heavy resource. Fix (startup sweep of resume_state_prefs_* older than TTL via deleteSharedPreferences) is small.

Evidence excerpt:

```
private fun prefs(windowId: String): SharedPreferences =
        context.getSharedPreferences("resume_state_prefs_$windowId", Context.MODE_PRIVATE)
```

