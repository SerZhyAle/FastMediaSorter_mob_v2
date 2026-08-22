# S1651 Research 01 - Connection Failure Eligibility

**Question:** Which SFTP failures may be reused briefly without masking authentication or host-key errors?

## Evidence

- The connection-pool creation path calls the SSH handshake before a session enters the shared pool.
- The pool already catches the failed operation once and returns the original exception as a failure result to its callers.
- SFTP protocol failures have a dedicated typed classifier. Its transient category intentionally groups all non-protocol throwables, so it is too broad for a negative connection cache.
- A rejected host key or authentication exchange can be represented as an SSH-library exception without proving that the endpoint is unreachable.

## Decision

Reuse only an exception whose cause chain proves a socket-establishment failure: `SocketTimeoutException`, `ConnectException`, `NoRouteToHostException`, or `UnknownHostException`. Preserve the original throwable as the cached failure so callers retain the existing error path and message.

Do not cache a bare SSH-library exception, an SFTP protocol exception, or an authentication/host-key failure. These outcomes may change immediately when credentials or trust configuration changes and must trigger a normal new attempt.

## Planning Impact

The failure cache needs an explicit cause-chain classifier owned by the SFTP connection layer, independent of the write-operation failure categories. Unit tests must prove eligible and ineligible exception trees.
