# Phase 03 — Rewire the coordinator, share entry, and worker flow

## Goal

Make the share entry point and background worker follow the S0166 decision tree end to end:
host detection, stored-auth lookup, account selection, session-backed extraction, retry after login,
and queueing of real media downloads.

## Steps

- [x] Audit `ReceiveShareActivity`, the coordinator, and `LinkDownloadWorker` against S0166 §2.
  **Verification:** Each Step 0–6 branch is mapped to one concrete code path.

- [x] Remove or replace leftover S0144/S0155 decision logic that offers auth at the wrong time or ignores
  stored working cookies.
  **Verification:** The entry flow no longer imports deleted-only auth UI classes or legacy host heuristics.

- [x] Ensure the worker/result flow can request re-auth only for preview-only or login-wall results and
  re-run automatically after a successful saved login.
  **Verification:** One successful auth save can trigger exactly one retry without infinite loops.

- [x] Ensure real-media success returns focus to the source app quickly and queues the actual download in
  background where applicable.
  **Verification:** Successful share handling does not leave the share Activity hanging open unnecessarily.

## Verification predicate

Given one incoming social URL and one stored session, the code first tries the stored session, only escalates
to auth on preview/login-wall results, and queues real media when found.

## Status: ✅ Complete