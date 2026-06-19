---
name: search-duplicates-by-symptom
description: Before creating a bugfix spec from logs, search existing tickets by the symptom (errorCode, crashing class, DataSource name) across recent dates - not by one keyword
metadata:
  type: feedback
---

Before allocating a new `bugfix-*` spec from a log analysis, search the spec catalog broadly **by the symptom**, not by a single keyword. A crash/error usually has several searchable handles: the `errorCode`, the crashing class name, the failing API (`getUri`, `copyTo`), the protocol/subsystem (`datasource`, `streaming`, `buffering`). Search several of them, and also scan tickets created on the same date(s) as the log.

**Why:** In the 2026-06-04 log-analysis session I created S0357 (SMB video robustness) covering errorCode 2000 (getUri null) and 1004 (stuck buffering). Both were already covered by **S0343** (`bugfix-smb-datasource-uri-null-race`) and **S0344** (`smb-streaming-playback-robustness`) - created the same morning from the same night session and already Implemented (BlockNeedUserTest). My catalog search used `search.ps1 -Query "seek" / "decoder" / "cloud"` and missed both. A subagent doing `/spec-tech S0357` caught the overlap; S0357 was then archived as a pure duplicate. The 8K-decoder line I also attributed to S0357 was actually a VR-diagnostic run (S0322, Verified), not a player bug.

**How to apply:** When triaging logs into tickets, for each distinct error run `search.ps1` on 2-3 orthogonal handles (errorCode, class, subsystem verb) and check `a.ps1 ss` / recent `created` dates before `insert.ps1`. Treat an active `BlockNeedUserTest` ticket from the same day as a strong duplicate signal - the owner likely already filed the fix. Spawning a `/spec-tech` subagent per ticket is a useful late safety net: brief it to look for overlapping tickets and report rather than blindly draft. See [[feedback_verify_subagent_build_failures]] for the general "trust but verify subagent findings" stance.
