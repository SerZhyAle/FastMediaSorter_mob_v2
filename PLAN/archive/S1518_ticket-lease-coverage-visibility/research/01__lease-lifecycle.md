# S1518 Research - Lease Lifecycle

## Scope

Resolve lease ownership and queue visibility without changing the spec lifecycle or release-plan file.

## Evidence

- `ticket-lease.ps1` uses atomic file creation for Claim. Re-claim by the same session is idempotent and refreshes `lastSeenAt`.
- `agent-lock.ps1` judges lease liveness by `lastSeenAt`, then session transcript, then claim time. This explicitly covers a session waiting in a lock queue.
- `run-spec-all-queue.ps1` already claims before child execution, refreshes its own lease while polling, and releases in `finally`.
- `/spec-all` and `/spec-dev` currently document no equivalent lifecycle. The queue driver compensates with lock-reason inference, which cannot cover research or planning before a lock exists.
- `release-queue.ps1 -List` prints only canonical plan lines. `ticket-lease.ps1 -Verb Status -Json` already exposes ticket, session, host, reason, age, and liveness without mutating state.

## Decisions

1. The top-level entry point owns release. A nested executor receives an explicit parent-owned lease context, may re-claim only to refresh it, and must not release it.
2. Add an opt-in queue listing projection rather than writing owner data into `PLAN/RELEASE_QUEUE.md`.
3. Keep the current lease schema and liveness precedence. This ticket adds coverage and visibility, not another heartbeat mechanism.

## Rejected

- A persistent `InWork` status: it loses the resume stage, does not self-expire, and produces catalog/queue churn.
- Rendering leases into the release-plan file: ownership changes often and the file's ordering belongs to the owner.
