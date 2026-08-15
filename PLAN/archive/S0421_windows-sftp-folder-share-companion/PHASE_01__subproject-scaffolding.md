# Phase 01 - Subproject Scaffolding

**Strategic spec:** [`../S0421_windows-sftp-folder-share-companion.md`](../S0421_windows-sftp-folder-share-companion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 6 / 6
**Started:** 2026-07-10
**Completed:** 2026-07-10

---

## Objective

Stand up the `desktop/windows-companion/` Go + Wails subproject skeleton: Go module, Wails app scaffold, package layout, and repo-level isolation from the Android Gradle build and quality gates. No SFTP or networking logic yet.

---

## Prerequisites

- [ ] All Pre-Implementation Blockers in INDEX are checked (toolchain installed, `wails doctor` green, subproject location confirmed).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `desktop/windows-companion/go.mod` | New | ≤ 30 |
| `desktop/windows-companion/wails.json` | New | ≤ 40 |
| `desktop/windows-companion/main.go` | New | ≤ 120 |
| `desktop/windows-companion/internal/app/app.go` | New | ≤ 120 |
| `desktop/windows-companion/frontend/` (Wails template) | New | n/a |
| `desktop/windows-companion/.gitignore` | New | ≤ 30 |
| `desktop/windows-companion/README.md` | New | ≤ 80 |
| `desktop/windows-companion/build.ps1` | New | ≤ 60 |
| `settings.gradle.kts` | Modified | ≤ 500 |

> Desktop subproject is outside the Android build. Do not add it as a Gradle module. Line budgets are advisory for Go/Markdown here.

---

## Steps

### Step 01.1 - Initialize Wails project

**Files:** `desktop/windows-companion/` (generated tree)
**Depends on:** - start of phase

**Prompt for developer:**

> From repo root run `wails init -n windows-companion -t vanilla -d desktop/windows-companion` (vanilla TS frontend; framework can be swapped later). Keep the generated `frontend/`, `wails.json`, `main.go`, `app.go`. Set the module path in `go.mod` to `github.com/sza/fastmediasorter-companion` (or the owner's chosen path).

**Verification:**

- `Glob` - `desktop/windows-companion/wails.json` exists.
- `Glob` - `desktop/windows-companion/go.mod` exists.
- Run `wails doctor` in the subproject dir - exits 0, WebView2 present.

**Status:** `[x]` done

---

### Step 01.2 - Define internal package layout

**Files:** `desktop/windows-companion/internal/app/app.go`
**Depends on:** Step 01.1

**Prompt for developer:**

> Establish the package skeleton the later phases fill: `internal/sftpserver/` (Phase 02), `internal/service/` and `internal/ipc/` (Phase 03), `internal/netaccess/` (Phase 04), `internal/config/` (Phase 05). Create each as a package with a single doc-comment `.go` file declaring the package and a one-line purpose comment. Move Wails-bound app struct into `internal/app/app.go` and have `main.go` reference it.

**Verification:**

- `Glob` - `desktop/windows-companion/internal/{sftpserver,service,ipc,netaccess,config}/*.go` each exist.
- `Grep` - `package app` matches once in `internal/app/app.go`.

**Status:** `[x]` done

---

### Step 01.3 - Pin core dependencies

**Files:** `desktop/windows-companion/go.mod`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add and `go get` the Level A dependency set so later phases compile: `github.com/pkg/sftp`, `golang.org/x/crypto/ssh`, `github.com/kardianos/service`, `github.com/huin/goupnp`, `github.com/grandcat/zeroconf`. Do NOT add any Level B / WebRTC / PAKE dependency here. Run `go mod tidy`.

**Verification:**

- `Grep` - `github.com/pkg/sftp` present in `go.mod`.
- `Grep` - `github.com/kardianos/service` present in `go.mod`.
- `Grep` - no `pion` / `webrtc` token in `go.mod` (Level B excluded).

**Status:** `[x]` done

---

### Step 01.4 - Add build + gitignore + README

**Files:** `desktop/windows-companion/build.ps1`, `.gitignore`, `README.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> `build.ps1`: wrap `wails build` (dev + release-unsigned targets), output under `desktop/windows-companion/build/bin/` (git-ignored). `.gitignore`: ignore `build/bin/`, `frontend/node_modules/`, `frontend/dist/`, `*.exe`. `README.md`: prerequisites (Go, Node, Wails CLI, WebView2), how to run `wails dev`, how to build unsigned, and the SmartScreen note (unsigned MVP per strategic quiz 2026-06-30).

**Verification:**

- `Glob` - `desktop/windows-companion/build.ps1` exists.
- `Grep` - `node_modules` present in `desktop/windows-companion/.gitignore`.
- `Grep` - `wails build` present in `build.ps1`.

**Status:** `[x]` done

---

### Step 01.5 - Isolate from Android build and gates

**Files:** `settings.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> Ensure the Gradle build never discovers the subproject: confirm `settings.gradle.kts` includes only `:app_v2` and `:wear` and add a comment that `desktop/` is a non-Gradle subproject. If the repo has a root `.gitignore` or gate-scan root list that could sweep `desktop/`, add an exclusion so `catalog_sync` / detekt / neuroslop gates ignore Go sources.

**Verification:**

- `Grep` - `settings.gradle.kts` does not contain `desktop` as an `include(...)` argument.
- `Grep` - catalog scan roots (`dev/CATALOG/scripts/scan.ps1` `$srcRoots`) do not include `desktop/`.

**Status:** `[x]` done

---

### Step 01.6 - Smoke build

**Files:** `desktop/windows-companion/` (whole tree)
**Depends on:** Step 01.1-01.5

**Prompt for developer:**

> Run `wails dev` once to confirm the empty app window opens, then `wails build` (unsigned) to confirm a `.exe` is produced. Fix any toolchain gaps `wails doctor` surfaces.

**Verification:**

- `go build ./...` in the subproject dir exits 0.
- `Glob` - `desktop/windows-companion/build/bin/*.exe` exists after `wails build`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `go build ./...` and `wails build` both succeed in the subproject (2026-07-10: `GO_BUILD_OK`, `fms_companion.exe` built in 6.1s).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry: batched at ticket closure via `close-and-log.ps1` (subproject is out-of-repo; one logical entry per phase).
- [x] Gradle/Android gates confirmed blind to the subproject: root is `P:\windows\fms_companion` (outside repo); `settings.gradle.kts` includes only `:app_v2`/`:wear`/`:lint-rules`/`:benchmark`; catalog `scan.ps1` `$srcRoots` has no desktop path.

**Execution notes (2026-07-10, /spec-all):**

- External root confirmed: `P:\windows\fms_companion` (spec-quiz 2026-07-10); phase-file `desktop/windows-companion/` paths map onto it; step 01.5 satisfied trivially (out-of-repo), `settings.gradle.kts` untouched.
- Go module path: `github.com/sza/fastmediasorter-companion`; Wails template `vanilla` (Vite); Wails v2.13.0, Go 1.26.4, Node 24.16.
- Dependency pinning survives `go mod tidy` via blank imports in package `doc.go` files (removed as real code lands in Phases 02-04). No pion/webrtc (Level B excluded) - verified.
- `wails dev` interactive window not run (agent context); smoke proof = `go vet` + `go build ./...` + `wails build` exe, per step 01.6 verification predicates.
- Template greeter frontend replaced with a minimal placeholder (`frontend/src/main.js`) - the generated `wailsjs/go/main/App` binding import broke `wails build` after the App struct moved to `internal/app`.

---

## Handoff Notes to Next Phase

Package skeleton (`internal/sftpserver`, `service`, `ipc`, `netaccess`, `config`) exists and compiles empty. Dependencies pinned. Subproject isolated from Android build. Phase 02 fills `internal/sftpserver`.

---

## Rollback Plan

Delete `desktop/windows-companion/` and revert the `settings.gradle.kts` comment. No Android code or data touched.
