# Research 01 - Error display surface for the two new outcomes

Resolves §6 item 1: how should host-key-change and auth-reject reach the user on the live path?

## Live-path error channel

Live SFTP errors (browse/open a resource) surface through a single, canonical path:

- `ui/main/helpers/ResourceNavigationCoordinator.kt:143-166` - on connection-test failure it calls
  `NetworkErrorClassifier.classify(error)` then `NetworkErrorMessageMapper.toContextAwareMessage(...)`
  and returns `NavigationResult.Error(userMessage, null)`.
- The result is a **string message** rendered on a non-modal surface (error state / snackbar), not a
  hard modal barrier.

So the live path already routes every failure through `classify()` -> `toContextAwareMessage()`. The
defect is purely what those two produce for auth-reject and host-key-change (see research 02).

## AddResource (test) path - the pattern to mirror

- `ui/addresource/AddResourceSftpKeyCoordinator.kt:49-63` (`emitSftpTestFailure`) already distinguishes
  `HostKeyMismatchException` and shows a distinct message built from strings
  `sftp_host_key_mismatch_title` + `sftp_host_key_mismatch_body_format`, versus a generic
  `addresource_connection_failed` otherwise. Same split exists in `AddResourceSftpFtpCoordinator.kt`.
- This is a **message**, not a blocking dialog. Precedent: a distinct security-worded string is the
  established surface for a host-key mismatch in this app.

## Decision

- **No new modal barrier / dialog is required.** A modal is not needed to satisfy contract acceptance #2
  ("warning shown, no data transferred"): `PinnedHostKeyRepository.check()` returns `CHANGED`, and JSch
  aborts the connection **before auth**, so no bytes ever flow on a mismatch regardless of UI. The UI
  obligation is only to (a) surface a security-worded message and (b) not treat it as a transient blip.
- Implement both new outcomes as **typed exceptions mapped to strings** through the existing
  `NetworkErrorMessageMapper`, consistent with every other network error and with the AddResource path.
  - Host-key-change -> new sealed subtype -> forced exhaustive branch in `toMessageRes` -> new
    security-worded string (reuse the wording of `sftp_host_key_mismatch_*`).
  - Auth-reject -> existing `NetworkAccessDeniedException`, but `toContextAwareMessage` gains a
    companion/SFTP branch so the message reads as "re-pair needed (rescan QR)" instead of the generic
    `error_network_access_denied`.

## Consequence for the spec

§6 item 1 Resolved: surface = existing string-message channel; no dialog; new work is one sealed
subtype + one exhaustive mapper branch + one companion-scoped access-denied message + trilingual
strings.
