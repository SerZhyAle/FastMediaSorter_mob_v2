# S1025 - Batch transfer should fail-fast when the destination server is unreachable

**Status:** Archived

## 0. Raw finding (auto-parked from S1021, 2026-07-13)

While confirming the S1021 StackOverflow fix on device
(`logs/fastmediasorter_20260713_001108.log`, 00:11), a 15-file Copy to
`smb://192.168.1.112/down/_iN` ran with the destination SMB host down. Observed behaviour:

- Every file re-runs the SMB TCP precheck and fails the same way:
  `Fast connectivity check failed to 192.168.1.112:445 after 3000ms` ->
  `SMB TCP precheck failed .. fast-fail without retry` ->
  `SMB connection failed: Server unreachable (192.168.1.112:445)`.
- The batch keeps advancing file-by-file (`FileOperationProgressDialog: Processing 1/15 .. 4/15`),
  each burning the full ~3s precheck timeout plus the SFTP download that precedes the doomed upload
  (~9s/file here), instead of aborting the whole operation after the first `Server unreachable`.
- The owner gave up after 4 files and pressed Back to cancel.

## 1. Symptom

When the destination server is entirely unreachable, a multi-file transfer wastes
`N x (download + precheck-timeout)` before the user manually cancels, and (for a Move) pointlessly
downloads each source into a temp file that can never be uploaded. There is no early "destination
unreachable, aborting" outcome.

## 2. Why its own ticket

- Needs a design decision on WHEN to abort: first `Server unreachable`? After K consecutive
  connect failures to the same host? Distinguish a truly-down host from a transient blip (the
  existing retry logic may be intentional for flaky Wi-Fi).
- Touches the transfer strategy / handler layer (`AtomicFileOperationStrategy`,
  `SmbOperationStrategy`, `SmbConnectionManager` precheck), not the S1021 use-case classification
  path - separate area, separate risk.
- Should surface a clear terminal result ("destination unreachable") + notification, rather than
  leaving the user to notice nothing is happening and cancel.

## 3. Candidate directions (decide at approval)

- Pre-flight the destination once before the per-file loop (single reachability probe); abort the
  whole batch with a clear error if it fails, instead of probing per file.
- Circuit-breaker: after K consecutive `Server unreachable` to the same destination host, stop the
  batch and report how many succeeded / how many were skipped.
- For Move specifically: do not download a source into temp until the destination is known
  reachable, so a dead destination never deletes/moves-half.

## 4. Evidence

- `logs/fastmediasorter_20260713_001108.log` lines ~8195-9033 (per-file `Server unreachable`
  retries, progress 1..4/15, then user Back-cancel).
- Related fixed ticket: [[S1021]] (the StackOverflow that previously masked this path entirely).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1021 (parent - the StackOverflow fix that unmasked this path).
- **Scope:** batch transfer to a network destination (SMB/SFTP/FTP/Cloud) runs one destination-reachability probe before the per-file loop; on failure the whole batch aborts with a clear "destination unreachable" terminal result + notification.
- **Data change:** none.
- **Flavor scope:** all flavors with network transfer.

## Owner decisions (2026-07-14)

- Abort trigger is a SINGLE pre-flight reachability probe run once before the per-file loop; on one failed check the whole batch aborts immediately. NOT a circuit-breaker after K failures, and NOT the extra Move-only guard. Implication: accept a small risk of a false abort on a transient blip - the existing per-file retry stays in place for in-loop transient errors.

## Device-test outcome (2026-07-31) - FAILED, two defects fixed under follow-up tickets

Field logs from 2026-07-30 and 2026-07-31 (`logs/fastmediasorter_20260730_175232.log`,
`logs/fastmediasorter_20260731_004038.log`) exercised this feature on the owner's device and it
failed the acceptance criteria on both halves.

**Defect A - false abort on a reachable destination. Fixed under [[S1320]].** Four Move batches
(203, 202, 4 and 2 files) to `sftp://192.168.1.112:11924/_iN` aborted with
`transfer_destination_unreachable`, while the same resource served browsing, playback and temp
cleanup throughout. The destination is a companion resource carrying a LAN address and a
port-forward address; the phone was on cellular, so the LAN address was correctly dead. The probe
added by this ticket parsed the host out of the destination path and connected to it directly,
bypassing the multipath endpoint resolution that every transfer handler already uses (see
[[S1006]]). The abort therefore fired on a destination the transfer itself would have reached.

**Defect B - the terminal notification dropped the reason. Fixed under [[S1321]].** This ticket's
own scope line promises a clear terminal result "+ notification". The in-app error surface did show
"Destination server is unreachable", but the notification posted a fixed "transfer failed" string
and ignored the reason carried on the event - so a user who had left the app, which is the case the
background worker exists for, learned only that something failed.

Re-test this ticket together with S1320 and S1321: the single-address dead-host case must still
abort immediately (the behaviour this ticket was built for), and the multipath case must transfer.

---

## Remote log pass 2026-08-01/02

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog` from `logs/fastmediasorter_20260729_162305.log` .. `logs/fastmediasorter_20260801_183450.log`.
This is a probe-firing record, not an acceptance verdict - a log proves the code path ran, not that
the screen looked right.

- Probe fired 7 times, always `preflight destination probe host=192.168.1.x reachable=false`, alongside `ScheduledOp[N] Target 'down_in' is unreachable`.
- Leg (a) - fail-fast on an unreachable single-address destination - is consistent with the log: no per-file loop followed.
- Not covered: leg (b) multipath alternate address and leg (c) the failure notification naming the reason.
