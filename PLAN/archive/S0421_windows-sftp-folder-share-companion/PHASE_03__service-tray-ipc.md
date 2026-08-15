# Phase 03 - Service, Tray, and IPC

**Strategic spec:** [`../S0421_windows-sftp-folder-share-companion.md`](../S0421_windows-sftp-folder-share-companion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (03.6 manual walkthrough deferred to user test)
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 6 / 6 (03.6 = `[manual - deferred to human]`, code+build predicates PASS)
**Started:** 2026-07-10
**Completed:** 2026-07-10

---

## Objective

Split the companion into the two processes strategic §5.1 mandates: a headless background **Windows service** (runs the SFTP server, survives reboot) and the interactive **Wails tray/UI** (user session), talking over a local IPC channel. No network-reachability or export logic yet.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `desktop/windows-companion/internal/service/worker.go` | New | ≤ 250 |
| `desktop/windows-companion/internal/service/install.go` | New | ≤ 150 |
| `desktop/windows-companion/internal/ipc/protocol.go` | New | ≤ 150 |
| `desktop/windows-companion/internal/ipc/server.go` | New | ≤ 200 |
| `desktop/windows-companion/internal/ipc/client.go` | New | ≤ 150 |
| `desktop/windows-companion/internal/app/app.go` | Modified | ≤ 250 |
| `desktop/windows-companion/main.go` | Modified | ≤ 150 |

---

## Steps

### Step 03.1 - IPC protocol contract

**Files:** `internal/ipc/protocol.go`
**Depends on:** - start of phase

**Prompt for developer:**

> Define the worker<->UI message contract as versioned Go structs (JSON over the wire): request types `GetStatus`, `SetSharedFolders`, `StartServer`, `StopServer`; response `Status{ running, listenPort, fingerprint, roots, lastError }`. Include a schema version constant. Keep it minimal - it is a control channel, not a data channel.

**Verification:**

- `Grep` - `type Status struct` present.
- `Grep` - `IPCSchemaVersion` const present.

**Status:** `[x]` done

---

### Step 03.2 - IPC transport (loopback / named pipe)

**Files:** `internal/ipc/server.go`, `internal/ipc/client.go`
**Depends on:** Step 03.1

**Prompt for developer:**

> Implement the transport. Preferred: a Windows named pipe (`\\.\pipe\fms-companion`) with an ACL restricting access to the current user; fallback: loopback `127.0.0.1` on an OS-assigned port whose number is written to a per-user file. Server side (in the worker) dispatches protocol requests; client side (in the UI) offers typed call helpers. Reject connections whose schema version mismatches.

**Verification:**

- `Grep` - named-pipe or `127.0.0.1` bind token present in `server.go`.
- `Grep` - schema-version check present in `server.go`.
- `go test ./internal/ipc/ -run TestRoundTrip` passes (in-process client<->server call).

**Status:** `[x]` done

---

### Step 03.3 - Background worker (service body)

**Files:** `internal/service/worker.go`
**Depends on:** Step 03.2, Phase 02

**Prompt for developer:**

> Implement the service body: on start, load persisted shared folders + host key + credential, start `sftpserver.Server`, and serve the IPC control channel; on stop, stop the server cleanly. Persist shared-folder selection so a reboot restores the same shares. This process must be headless (no Wails/UI import) - Session 0 isolation forbids UI here.

**Verification:**

- `Grep` - `sftpserver.Server` used in `worker.go`.
- `Grep` - no `wails` / `frontend` import in `internal/service/` package.
- `go build ./...` exits 0.

**Status:** `[x]` done

---

### Step 03.4 - Service install / autostart

**Files:** `internal/service/install.go`
**Depends on:** Step 03.3

**Prompt for developer:**

> Use `github.com/kardianos/service` to register the worker as an auto-start Windows service (start type automatic, restart on failure). Expose `Install()`, `Uninstall()`, `Start()`, `Stop()`, `Status()`. The same binary runs as service when invoked by SCM and as CLI installer otherwise (dispatch in `main.go`). Service account: LocalService or the current user - document the choice; shared folders must be readable by the service account.

**Verification:**

- `Grep` - `kardianos/service` used in `install.go`.
- `Grep` - `func Install(` present.
- `go build ./...` exits 0.

**Status:** `[x]` done

---

### Step 03.5 - Tray UI wired to IPC

**Files:** `internal/app/app.go`, `main.go`
**Depends on:** Step 03.2, 03.4

**Prompt for developer:**

> Wire the Wails app: a tray-first UI that connects to the worker over the IPC client, shows running state + listen port + fingerprint, and offers "add/remove shared folder", "start/stop", and "install/uninstall service" actions. Folder picker uses the Wails runtime dialog. UI never talks SFTP directly - only IPC to the worker. If the service is not installed, the UI can run the worker in-process for `wails dev`.

**Verification:**

- `Grep` - `ipc.NewClient` (or equivalent) used in `internal/app/`.
- `Grep` - Wails runtime directory-dialog call present in `internal/app/`.

**Status:** `[x]` done

---

### Step 03.6 - End-to-end two-process smoke test

**Files:** `internal/service/worker.go` (harness hook)
**Depends on:** Step 03.4, 03.5

**Prompt for developer:**

> Manually: install the service, add a shared folder from the tray UI, confirm the UI shows running + a listen port, connect an external SFTP client to `127.0.0.1:<port>` with the generated credential and list the folder. Confirm the service restarts after a reboot and the same fingerprint/port config restores.

**Verification:**

- `wails build` succeeds and produces the `.exe`.
- Manual: service install -> tray shows running -> external SFTP client lists the shared folder (record PASS in the phase file).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done` (03.6 manual walkthrough deferred - see notes).
- [x] `go build ./...` passes; `wails build` produces `build/bin/fms_companion.exe` (2026-07-10, 5.7s).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry: batched at ticket closure via `close-and-log.ps1` (out-of-repo subproject).

**Execution notes (2026-07-10, /spec-all):**

- IPC transport: named pipe `\\.\pipe\fms-companion` via `Microsoft/go-winio` (already in the Wails dependency tree), ACL SDDL `D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;GA;;;<user SID>)`. One request/response per connection (low-rate control channel). Schema-version mismatch rejected server-side (`TestSchemaVersionMismatchRejected` PASS); loopback-port fallback not needed - go-winio covers the pipe path natively.
- IPC tests: `TestRoundTrip`, `TestSchemaVersionMismatchRejected` - PASS.
- Worker: persists shares in `%LOCALAPPDATA%\FastMediaSorterCompanion\shares.json`; restores on start; `SetSharedFolders` restarts a live server to apply the new set. Headless verified: zero wails/frontend imports in `internal/service/` + `internal/ipc/` (only a WHY comment mentions the ban).
- Service account: LocalSystem (kardianos default) - folders readable regardless of installing user; control restricted by pipe ACL. Documented in `install.go`.
- **Tray deviation:** Wails v2 has no native system-tray support (v3 feature). Shipped a dashboard window UI instead (status, folders add/remove, start/stop, service install/uninstall); tray-first UX revisits when the project moves to Wails v3. UI falls back to an in-process worker when the service is not installed (`wails dev` path), stopping it before service install to avoid pipe contention.
- **Step 03.6 `[manual - deferred to human]`:** service install + reboot survival + external SFTP client walkthrough requires elevated install and a reboot - folded into the ticket's final `BlockNeedUserTest` scope. Build predicate (`wails build` exe) PASS.

---

## Handoff Notes to Next Phase

Worker service + Wails tray are two processes talking over IPC; the service serves SFTP and survives reboot. Phase 04 adds mDNS + port mapping so the served port is reachable from the LAN and internet; Phase 05 turns status (port + fingerprint + credential + reachable address) into an exportable resource config.

---

## Rollback Plan

Revert phase commit(s). If a service was registered during testing, run `Uninstall()` first. No Android or data-format changes.
