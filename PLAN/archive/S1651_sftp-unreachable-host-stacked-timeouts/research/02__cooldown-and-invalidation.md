# S1651 Research 02 - Cooldown and Invalidation

**Question:** What cooldown prevents stacked timeouts while allowing a recovered SFTP endpoint to reconnect promptly?

## Evidence

- A new SSH connection has a 10-second connection timeout.
- The reported resource open performs cleanup, normal listing, paged listing, and counting in close succession. The first timeout therefore completes before later calls begin, but no successful pooled session exists for them to reuse.
- Network handover and explicit invalidation already reset the SFTP connection pool, which are natural recovery boundaries.
- The scanner watchdog is 60 seconds and is a final safeguard for an already-started blocking listing, not a suitable retry-delay policy.

## Decision

Use a fixed 15-second in-memory cooldown from the failed connection attempt. It is long enough to cover the immediate follow-up calls after a 10-second failure and short enough that a recovered endpoint receives a new handshake without app restart. Expired entries are removed when checked; the cache has a bounded maximum size.

Clear entries for an endpoint on successful connection and explicit invalidation. Clear all entries with the existing all-session reset used on network handover/loss, since the new transport may reach a host that the previous network could not.

## Planning Impact

The integration step must place checks and writes under the existing per-endpoint creation lock so concurrent callers cannot stampede into separate handshakes. Tests need a controllable clock for expiry, endpoint-scoped clearing, global clearing, and recovery after success.
