<sub class="doc-stamp">26.10.18 17:45</sub>

# Pointer - `SHARE-SESSION`

| | |
| --- | --- |
| **Id** | `SHARE-SESSION` |
| **Version** | 1.3, active. Owner: FMS Companion (the worker holds the server guarantees) |
| **Home** | `remote-folder-access/README.md` in the shared contracts catalog |
| **Role here** | client - the SFTP connection to a folder shared by the companion |

## What this repository must do to stay conformant

The client rules of section 2 of the contract (rules 6-10):

- Reconnect transparently on a transport failure before showing the user anything.
- Classify before reacting: transport failure reconnects, an auth rejection asks to re-pair, a host-key
  mismatch refuses and warns and never auto-accepts, an SFTP status is an ordinary result.
- Bound the reconnect - a few backed-off attempts, never an unbounded loop.
- Treat keepalive as an optimization, never as the thing holding the session open.
- Release the old connection before taking a new slot.
- A PIN-protected root (1.2, additive) answers only the SSH `keyboard-interactive` proof
  `Resource`/`Password`/`PIN` and scopes the proven connection to that one root; the ordinary password
  login reaches only unprotected roots. Every rule above is unchanged by 1.2.

## Where it lives here

- `app_v2/.../data/remote/sftp/SftpConnectionPool.kt` and `SftpClient.kt` - reconnect and classification.
- `app_v2/.../data/network/exceptions/NetworkErrorClassifier.kt` and `NetworkErrorMessageMapper.kt` -
  the four failure classes as the user meets them.
- `app_v2/.../data/remote/sftp/SftpRootProofRegistry.kt` and `SftpRootProofUserInfo.kt` - the root-scoped
  `keyboard-interactive` proof of a protected root, one session per root, never shared with the password login.
