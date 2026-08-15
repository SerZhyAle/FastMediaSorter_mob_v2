# Phase 01 — Rebuild auth record data model and encrypted storage

## Goal

Replace the deleted S0151/S0155 persistence layer with a host-scoped, multi-account, encrypted
authorization store that matches S0166 §1, §3, and the rejection-record behavior from §2 Step 1.

## Steps

- [x] Read the current auth-storage implementation surface and confirm which deleted contracts were already
  recreated in source (`AuthSessionRepository`, repository impl, encrypted cookie store).
  **Verification:** Actual source paths are listed in this phase file and no step still references a
  deleted-only class name.

- [x] Implement or finish the domain/data model for one auth record: host, account id/display name,
  encrypted session payload, updated timestamp, last-used timestamp, dismissed marker.
  **Verification:** The repository API can represent both a real session and a host-level rejection record
  without overloading nulls.

- [x] Ensure host matching keeps `threads.com` and `threads.net` separate and supports multiple accounts
  per host ordered by freshest update first.
  **Verification:** A repository read for one host can return more than one non-dismissed account plus the
  dismissal marker when present.

- [x] Ensure save/update/delete/mark-last-used flows refresh observable state for settings UI and share flow.
  **Verification:** One repository write updates both the visible account list and the all-records view
  without app restart.

## Verification predicate

The repository layer can answer all S0166 Step 1 decisions directly: no records, one session, many sessions,
dismissed host. No deleted S0151/S0155 storage class remains required by callers.

## Status: ✅ Complete