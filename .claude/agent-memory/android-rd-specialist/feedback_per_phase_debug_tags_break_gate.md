---
name: per-phase-debug-tags-break-ticket-log-gate
description: Tactical phases must NOT insert Timber.d("Sxxxx:") per-phase; the ticket-log gate rejects it while spec is not BlockNeedUserTest
type: feedback
---

`/spec-tech` sometimes writes an "Insert `Timber.d(\"Sxxxx: …\")`" step (plus a `Grep Timber.d("Sxxxx:` verification predicate) into intermediate phases. That is a plan defect - strip it; tags belong only at the single final transition into `BlockNeedUserTest`.

**Why:** `assert-no-ticket-logs.ps1` (runs inside `post-change.ps1`, the mandatory per-step closure) hard-fails `expected: 0 | actual: N` for any `Timber.*("Sxxxx:` log line whose spec is not currently `BlockNeedUserTest`. During every intermediate phase the spec is `In Progress`, so the gate returns exit 1 and blocks the step. It only allows tags for specs the catalog lists as `BlockNeedUserTest` ("allowed BlockNeedUserTest probes: N"). This matches CLAUDE Rule 2: the tag exists IFF status is `BlockNeedUserTest`. Comments/KDoc containing `Sxxxx` are fine - the gate targets log statements only. (Hit on S0391 Phase 02, 2026-06-13.)

**How to apply:** When authoring or executing a multi-phase tactical plan with on-device acceptance, remove all per-phase tag-insertion steps and their `Timber.d("Sxxxx:` predicates; replace each with a `Grep -n "Sxxxx"` → zero-hits predicate. Insert every flow-entry `Timber.d("Sxxxx: …")` probe as the final code edits at the end, validated by one build, with the `BlockNeedUserTest` flip - spec-dev's "Final-phase debug-tag insertion" + `close-and-log.ps1` own this. See [[timber-tags-before-test]] and [[reference-ticket-log-gate]].

**Other-ticket stale probes can block your gate.** The gate is repo-wide: if it fails `actual: N` listing `[Syyyy]` ids that are NOT your ticket, another ticket left `BlockNeedUserTest` (often archived concurrently by the user mid-session - `allowed BlockNeedUserTest probes` count drops). Those `Timber.d("Syyyy:")` lines are now stale and must be removed on sight (CLAUDE Rule 2) - verify the foreign ticket is settled (`select.ps1 -Id Syyyy` → Archived/Verified/Implemented/etc., not an active device-test), then delete each probe line and any import it orphaned. This unblocks the gate for everyone; it is not optional cleanup. (Hit on S0417, 2026-06-14: S0054 archived at 13:38 left 4 stale probes that failed the gate during S0417 spec-dev.)
