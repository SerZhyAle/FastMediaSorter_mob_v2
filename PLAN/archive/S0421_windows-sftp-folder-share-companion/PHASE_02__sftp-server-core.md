# Phase 02 - SFTP Server Core

**Strategic spec:** [`../S0421_windows-sftp-folder-share-companion.md`](../S0421_windows-sftp-folder-share-companion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05
**Steps done:** 6 / 6
**Started:** 2026-07-10
**Completed:** 2026-07-10

---

## Objective

Implement an embeddable SFTP server in `internal/sftpserver` that serves one or more chosen host folders over SSH, with a persisted host key and a generated per-install credential. Headless library API only; no service wrapper or UI yet.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Client-authentication model confirmed (INDEX blocker): default = generated username + random password embedded in exported config.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `desktop/windows-companion/internal/sftpserver/server.go` | New | ≤ 300 |
| `desktop/windows-companion/internal/sftpserver/hostkey.go` | New | ≤ 150 |
| `desktop/windows-companion/internal/sftpserver/auth.go` | New | ≤ 150 |
| `desktop/windows-companion/internal/sftpserver/roots.go` | New | ≤ 200 |
| `desktop/windows-companion/internal/sftpserver/server_test.go` | New | ≤ 250 |

---

## Steps

### Step 02.1 - Host key generation and persistence

**Files:** `internal/sftpserver/hostkey.go`
**Depends on:** - start of phase

**Prompt for developer:**

> Generate an ed25519 host key on first run, persist it under the Windows per-user app data dir (`%LOCALAPPDATA%/FastMediaSorterCompanion/hostkey`). On subsequent runs load the existing key so the fingerprint is stable (the Android side pins it - TOFU). Expose `LoadOrCreateHostKey() (ssh.Signer, fingerprint string, error)` where fingerprint is the SHA-256 base64 form.

**Verification:**

- `Grep` - `func LoadOrCreateHostKey(` present.
- `Grep` - `ed25519` present in `hostkey.go`.
- `go test ./internal/sftpserver/ -run TestHostKeyStable` passes (same fingerprint across two loads).

**Status:** `[x]` done

---

### Step 02.2 - Credential (client auth) model

**Files:** `internal/sftpserver/auth.go`
**Depends on:** Step 02.1

**Prompt for developer:**

> Implement the confirmed client-auth model. Default: generate a fixed username (e.g. `fms`) plus a random 20+ char password on first run, persisted alongside the host key; expose `LoadOrCreateCredential() (username, password string, error)`. Wire an `ssh.ServerConfig` `PasswordCallback` that accepts only that pair using constant-time compare. Reject all other credentials.

**Verification:**

- `Grep` - `PasswordCallback` present in `auth.go`.
- `Grep` - `subtle.ConstantTimeCompare` present (no naive `==` on secrets).
- `go test ./internal/sftpserver/ -run TestAuthRejectsWrongPassword` passes.

**Status:** `[x]` done

---

### Step 02.3 - Folder roots (multi-folder, read-focused)

**Files:** `internal/sftpserver/roots.go`
**Depends on:** - start of phase

**Prompt for developer:**

> Model the chosen shared folders as named roots. Expose them to SFTP under stable virtual paths (e.g. `/Photos`, `/Music`) mapping to real host paths, so the Android resource sees a clean tree. Confine all file access to configured roots (reject path traversal outside a root). Reads are the primary path (browse, thumbnails, ranged video reads); writes may be disabled in MVP - gate behind a per-root read-only flag defaulting to read-only.

**Verification:**

- `Grep` - `func.*Roots` constructor present.
- `Grep` - path-traversal guard token (`filepath.Clean` + prefix check) present in `roots.go`.
- `go test ./internal/sftpserver/ -run TestRootTraversalBlocked` passes.

**Status:** `[x]` done

---

### Step 02.4 - SFTP server assembly

**Files:** `internal/sftpserver/server.go`
**Depends on:** Step 02.1, 02.2, 02.3

**Prompt for developer:**

> Assemble the server: TCP listener on a configurable port (0 = OS-assigned, report the actual port), SSH handshake with the host key + auth callback, then per-session `sftp.NewServer` scoped to the roots (use `sftp.WithServerWorkingDirectory` / a rooted handler enforcing 02.3 confinement). Expose `type Server` with `Start(ctx) (listenPort int, err error)` and `Stop()`. Support ranged reads (do not buffer whole files) so Android video seek works.

**Verification:**

- `Grep` - `sftp.NewServer` present in `server.go`.
- `Grep` - `func (s \*Server) Start(` and `func (s \*Server) Stop(` present.
- `go build ./...` exits 0.

**Status:** `[x]` done

---

### Step 02.5 - Integration test: real SFTP round-trip

**Files:** `internal/sftpserver/server_test.go`
**Depends on:** Step 02.4

**Prompt for developer:**

> Start the server on `127.0.0.1:0` against a temp root with a known file, connect with an SFTP client (`pkg/sftp` client side over `x/crypto/ssh`) using the generated credential, list the root, read the file fully, and read a byte range from a middle offset (proves ranged read for video seek). Assert host-key fingerprint matches 02.1.

**Verification:**

- `go test ./internal/sftpserver/ -run TestRoundTrip` passes.
- `go test ./internal/sftpserver/ -run TestRangedRead` passes.

**Status:** `[x]` done

---

### Step 02.6 - Server config surface

**Files:** `internal/sftpserver/server.go`
**Depends on:** Step 02.4

**Prompt for developer:**

> Expose a small config struct (roots list, desired port or 0, read-only flag) and a getter returning the live listen port + host-key fingerprint + credential - the inputs Phase 05 exports. Do not import `internal/config` here (Phase 05 depends on this package, not the reverse).

**Verification:**

- `Grep` - `ListenPort` and `Fingerprint` accessor tokens present.
- `Grep` - no `internal/config` import in `sftpserver` package (dependency direction correct).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] `go build ./...` and `go test ./internal/sftpserver/...` pass (2026-07-10: 7/7 PASS - HostKeyStable, CredentialStable, RootTraversalBlocked, AuthRejectsWrongPassword, RoundTrip, RangedRead, WriteDeniedOnReadOnlyRoot).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry: batched at ticket closure via `close-and-log.ps1` (out-of-repo subproject).

**Execution notes (2026-07-10, /spec-all):**

- Multi-root confinement implemented via `sftp.NewRequestServer` + custom `sftp.Handlers` on `RootSet` (virtual `/<Name>` tree), not the OS-filesystem `sftp.NewServer` - the only way to serve multiple mapped roots with traversal confinement. `*os.File` as `io.ReaderAt` gives ranged reads (video seek) with no whole-file buffering.
- Persisted state dir: `%LOCALAPPDATA%\FastMediaSorterCompanion\` (`hostkey` PEM + `credential.json`), overridable via `FMS_COMPANION_DATA_DIR` env (tests, service account).
- Corrupt persisted host key = hard error, never silent regeneration (would break every TOFU-pinned client).
- Config surface (02.6): `Config{Roots, Port, BindAddress, ReadOnly}` + `Info{ListenPort, Fingerprint, Username, Password, Roots}`; `BindAddress` added so tests bind loopback only (no firewall prompt); production binds all interfaces.
- Writes: read-only default per root; non-read-only roots get basic Rename/Remove/Mkdir/Rmdir (Android sorter move/delete path); `Setstat` ignored (chmod/utimes meaningless on Windows shares).

---

## Handoff Notes to Next Phase

`internal/sftpserver` serves confined roots over SFTP with a stable pinned host key and a generated credential, reporting its live port + fingerprint + credential. Phase 03 runs it inside a Windows service and exposes control over IPC; Phase 04 makes the port reachable; Phase 05 exports the connection facts.

---

## Rollback Plan

Revert phase commit(s). Package is self-contained; no other package imports it yet.
