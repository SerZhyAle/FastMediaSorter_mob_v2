# Спецификация (compact bugfix): S1349 - post-change.ps1 settings-doc-sync-gate collides with backgrounded detekt-gate on BUILD.LOCK

**Ticket:** S1349
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-01
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-01

**Текст:**

post-change.ps1 gate ordering bug: settings-doc-sync-gate collides with backgrounded detekt-gate on BUILD.LOCK

Discovered 2026-08-01 while closing S1313 (widen settings-search catalog to dialog-hosted settings) via post-change.ps1.

Symptom: post-change.ps1 -ChangeType Mixed with a changed-file set that includes both .kt files (triggers detekt-gate) and a settings doc (docs/settings/settings-annotations.json, triggers settings-doc-sync-gate) reliably fails at settings-doc-sync-gate with:

  BUILD.LOCK held - refusing to start a second gradle build.
    Holder PID: <pid>  age: <n>s  reason: 'assert-detekt.ps1 (app_v2 + wear)'  host: MARK

Reproduced twice in a row, different PIDs each time, lock-status.ps1 -Name Build reported the holder as STALE / processAlive:False by the time I checked (the post-change.ps1 process had already exited, orphaning/killing the still-running background job).

Root cause (read scripts/post-change.ps1): at line ~446 it launches `$detektJob = Start-ThreadJob -Name 'detekt-gate' -ScriptBlock {...}` which acquires BUILD.LOCK for a full `assert-detekt.ps1 (app_v2 + wear)` run, then continues the gate pipeline synchronously. `settings-doc-sync-gate` runs at line ~571 - only ~10s of lighter gates later (strings-audit, ticket-log-audit, doc-pins-sync, doc-pin-drift, detekt-preflight, flavor-flag-gate, neuroslop-gate, public-mutable-flow-gate) - well before the backgrounded detekt job can finish a real two-module detekt run. settings-doc-sync-gate internally needs gradle too (SettingsManifestExportTest freshness verification, non -SkipManifestTest path) when the touched-file set includes a settings surface, so it collides with the still-running detekt-gate job and fails immediately instead of waiting or queueing. The job is only drained later at line ~708-710 (`Receive-Job -Job $detektJob -Wait`), by which point settings-doc-sync-gate has already failed.

This is deterministic, not a flaky sibling-session race - confirmed by running `scripts/quality/assert-detekt.ps1 -Gate -ChangedFiles <same set>` standalone: it passed cleanly (169 files with new findings project-wide, none among changed files) and released BUILD.LOCK properly (lock-status.ps1 -Name Build reported "absent (free)" immediately after).

Impact: any ticket whose change set spans both Kotlin files and a registered settings-doc file (docs/settings/settings-manifest.json, settings-annotations.json, SETTINGS_REFERENCE*.md, howto-path-vocab.json) will hit this every time post-change.ps1 runs with -ChangeType Mixed/Kotlin, blocking the mandatory closure facade with a false FAIL (lock contention, not a real defect).

Suggested fix direction (not verified, just a starting hypothesis): serialize settings-doc-sync-gate's gradle-dependent step (the manifest-freshness check) against $detektJob - either drain/await the detekt job before settings-doc-sync-gate attempts its own gradle call, or have settings-doc-sync-gate reuse -SkipManifestTest when the caller can prove freshness was already established this run (e.g. -ScopeToFile plus a fresh reindex-settings.ps1 run), or simply move settings-doc-sync-gate's gradle-needing sub-step to run after the detekt job is drained.

Workaround used to close S1313: ran scripts/quality/assert-detekt.ps1 -Gate -ChangedFiles <full set> standalone (PASS) and scripts/quality/reindex-settings.ps1 standalone (PASS, chain green) to independently verify what settings-doc-sync-gate would have checked, since the facade itself could not complete due to this bug.

---

## 1. Проблема / симптом

`post-change.ps1` reliably fails its own `settings-doc-sync-gate` step with a `BUILD.LOCK held`
error whenever the changed-file set spans both `.kt` files (triggers `detekt-gate`, started as a
backgrounded `Start-ThreadJob`) and a registered settings doc (triggers `settings-doc-sync-gate`,
which needs gradle for the manifest-freshness check). The two gates run close together in the
pipeline and both want `BUILD.LOCK`; the backgrounded detekt job has not finished by the time
settings-doc-sync-gate starts, so the latter fails immediately with a lock-contention error instead
of a real content finding. First seen closing S1313.

---

## 2. Корневая причина

Confirmed by re-reading current line numbers (2026-08-02, both files unchanged since the raw capture):

`post-change.ps1:446` starts `$detektJob` as a background `Start-ThreadJob` that runs
`assert-detekt.ps1 -Gate` and, inside it, acquires `BUILD.LOCK`. The pipeline continues
synchronously. `post-change.ps1:571` invokes `settings-doc-sync-gate`
(`assert-settings-doc-sync.ps1 -Gate`), which - when the changed set touches a settings surface and
the manifest is affected - calls `Enter-BuildLockOrExit -Reason "assert-settings-doc-sync.ps1
(SettingsManifestExportTest)"` at its own line 92, **without `-Wait`**. `Enter-BuildLockOrExit`'s
default behavior (no `-Wait`) is a single-shot refusal: if `BUILD.LOCK` is held, it prints the
holder's PID/age/reason and exits 1 immediately - it does not queue or retry. Since the backgrounded
detekt job is very likely still running at this point in the pipeline (~10s of lighter gates after
it started, against a two-module detekt analysis that regularly takes longer), settings-doc-sync-gate
loses the race and reports a lock-contention failure that reads exactly like a real content finding.
`post-change.ps1` only drains `$detektJob` via `Receive-Job -Wait` at line ~708-710, well after
settings-doc-sync-gate has already failed.

This is not sibling-session contention (CLAUDE.md Rule 23's normal case) - it is this ONE
`post-change.ps1` invocation racing against its own backgrounded child job.

---

## 3. Исправление

`Enter-BuildLockOrExit` already supports a `-Wait` switch (added for exactly this class of problem,
per its own header comment) that blocks up to `-WaitTimeoutSeconds` (default 900s) for the current
holder to release the lock, instead of failing fast. Pass `-Wait` from
`assert-settings-doc-sync.ps1`'s own call site (line 92) - this fixes the race at its narrowest point,
requires no `post-change.ps1` pipeline reordering, and is a no-op when the gate runs standalone
(no `post-change.ps1`, no concurrent job, lock already free - `-Wait` returns immediately). The
default 900s timeout is generous enough that a cold two-module detekt run finishes well inside it;
no custom override needed.

Chosen over the two other directions in §0's raw capture: reordering `post-change.ps1` to drain
`$detektJob` before `settings-doc-sync-gate` starts would serialize two currently-parallel gradle
stages for EVERY run (not just the racing case), adding real wall-clock cost to the common path.
Reusing `-SkipManifestTest` would skip the actual freshness check rather than let it complete, which
is a weaker guarantee than waiting for the real answer.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1313 (discovered while closing it; workaround used there, not a bug fix)
- **Scope:** `scripts/quality/assert-settings-doc-sync.ps1` only. No `.kt`, no app-facing surface.
- **Flavors:** n/a - build tooling.

---

## 4. Проверка

Reproduced the exact race directly against `Enter-BuildLockOrExit`/`Enter-AgentLock` rather than
waiting on a lucky `post-change.ps1` timing window - a genuine, live-held `BUILD.LOCK` is a
deterministic precondition, unlike waiting for the backgrounded detekt job to still be mid-run.

- A real detached process acquired `BUILD.LOCK` (`Enter-AgentLock -Name Build -Reason 'S1349
  verification real holder'`), confirmed `HELD` + `processAlive: True` via `lock-status.ps1`.
- With that lock genuinely held, `assert-settings-doc-sync.ps1 -Gate` (no `-ChangedFiles`, so stage 2
  is forced) was launched. Before the fix this exits 1 immediately with `BUILD.LOCK held - refusing to
  start a second gradle build`. After the fix, its output showed `BUILD.LOCK held - waiting up to 900s
  for the holder to finish..` and it blocked instead of failing.
- The holder released after ~20s; `lock-status.ps1` then showed `BUILD.LOCK` re-acquired under reason
  `assert-settings-doc-sync.ps1 (SettingsManifestExportTest)` - proof the waiting call actually queued
  and took the lock the moment it freed, not a coincidental second attempt.
- The gate then ran the real `SettingsManifestExportTest` gradle stage and exited 0 (`elapsed=47s`
  total: ~20s wait + ~27s gradle) - a genuine PASS, never a lock-contention error.
- Standalone invocation with the lock already free (no concurrent holder) completed normally with no
  observable added delay - `-Wait` only engages `Enter-AgentLock`'s wait branch when the lock is
  actually held; a free lock is acquired on the first attempt exactly as before.

Confirms the fix per its design: the race is closed at its narrowest point, a genuine timeout still
reports the pre-existing exit 2 CANNOT-VERIFY (never exit 1), and the no-contention path is unchanged.

---

## Last Audit

**Date:** 2026-08-02
**Mode:** strategic (compact spec, Simple path)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

`Enter-BuildLockOrExit -Reason "assert-settings-doc-sync.ps1 (SettingsManifestExportTest)"` at
`scripts/quality/assert-settings-doc-sync.ps1:99` (line shifted from the raw capture's 92 by this
ticket's own comment block) now passes `-Wait`, with a comment naming the race and pointing back to
this ticket. Header exit-code doc updated to describe the new exit-2 wait-timeout path.
Reproduced the collision directly (real live-held `BUILD.LOCK`, not a lucky `post-change.ps1` timing
window): before the fix the gate would exit 1 immediately; with the fix applied it printed `BUILD.LOCK
held - waiting up to 900s for the holder to finish..`, blocked ~20s until the holder released, then
re-acquired the lock under its own reason and completed the real `SettingsManifestExportTest` gradle
stage with exit 0 (`elapsed=47s`). Standalone invocation with a free lock showed no added delay.
`post-change.ps1 -ScopeToFile` on the script (Script type): PASS. Regenerated
`docs/SCRIPT_CHEATSHEET.md` (the exit-code doc edit changed the script's `.DESCRIPTION`, which
feeds the cheatsheet render) - closed separately (Doc type): PASS. No `.kt` touched, so no build gate
applies; this ticket's whole surface is `scripts/quality/assert-settings-doc-sync.ps1`.

### Manual / on-device

- None. Build-tooling fix with no on-device or UI surface.
