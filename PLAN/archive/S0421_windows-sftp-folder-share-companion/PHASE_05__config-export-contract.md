# Phase 05 - Config Export Contract

**Strategic spec:** [`../S0421_windows-sftp-folder-share-companion.md`](../S0421_windows-sftp-folder-share-companion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04
**Blocks:** Phase 06, Phase 07
**Steps done:** 5 / 5
**Started:** 2026-07-10
**Completed:** 2026-07-10

---

## Objective

Define and produce the **companion-defined resource-config format** that the Android side imports in one action. This is the cross-project interface contract: both the Go exporter (this phase) and the Kotlin importer (Phase 07) key off it. Emit it as a scannable QR code and a portable file.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (status: port, fingerprint, credential).
- [ ] Phase 04 ✅ Done (reachability: LAN + external address).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `desktop/windows-companion/internal/config/schema.go` | New | ≤ 200 |
| `desktop/windows-companion/internal/config/export.go` | New | ≤ 250 |
| `desktop/windows-companion/internal/config/schema_test.go` | New | ≤ 200 |
| `desktop/windows-companion/docs/CONFIG_FORMAT.md` | New | ≤ 150 |
| `desktop/windows-companion/internal/app/app.go` | Modified | ≤ 250 |

---

## Steps

### Step 05.1 - Define the versioned config schema

**Files:** `internal/config/schema.go`, `docs/CONFIG_FORMAT.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Define a versioned JSON schema `CompanionResourceConfig{ schemaVersion, resourceName, protocol:"sftp", accessPaths:[{kind:"lan"|"portforward", host, port}], username, password, hostKeyFingerprintSha256, roots:[{virtualPath, label}], createdAt }`. `accessPaths` is an ordered list so the Android side can try LAN first then internet (strategic §5.3). Document every field and the version-bump rule in `docs/CONFIG_FORMAT.md` - this doc is the authoritative contract Phase 07 implements against.

**Verification:**

- `Grep` - `type CompanionResourceConfig struct` present.
- `Grep` - `SchemaVersion` field present.
- `Glob` - `desktop/windows-companion/docs/CONFIG_FORMAT.md` exists.

**Status:** `[x]` done

---

### Step 05.2 - Build config from live state

**Files:** `internal/config/export.go`
**Depends on:** Step 05.1

**Prompt for developer:**

> Assemble a `CompanionResourceConfig` from the worker status (port, credential, fingerprint, roots) plus reachability (LAN address, external host/port). Populate `accessPaths` LAN-first, then port-forward when reachable. Omit internet path entirely when CGNAT with no mapping (Level A cannot serve it - honest omission, not a broken entry).

**Verification:**

- `Grep` - `func BuildConfig(` present.
- `Grep` - LAN-before-portforward ordering token present.
- `go test ./internal/config/ -run TestAccessPathOrder` passes.

**Status:** `[x]` done

---

### Step 05.3 - Serialize to QR and file

**Files:** `internal/config/export.go`
**Depends on:** Step 05.2

**Prompt for developer:**

> Serialize the config to compact JSON. Emit two outputs: (a) a `.fmscfg` file the user can transfer; (b) a QR code image the Android app scans. If the JSON exceeds a comfortable QR density, gzip+base64 before encoding, or fall back to file-only with a short pairing code. Keep the secret (password) inside the payload - warn in the doc that the QR/file is sensitive.

**Verification:**

- `Grep` - QR-encode call present in `export.go`.
- `Grep` - `.fmscfg` extension token present.
- `go test ./internal/config/ -run TestRoundTripSerialize` passes (marshal -> unmarshal equal).

**Status:** `[x]` done

---

### Step 05.4 - Expose export in the tray UI

**Files:** `internal/app/app.go`
**Depends on:** Step 05.3, Phase 03

**Prompt for developer:**

> Add an "Export / Show QR" action in the Wails UI that calls the worker for current config, renders the QR on screen, and offers "save `.fmscfg`". Regenerate when shares or reachability change. Surface the manual-forward hint (Phase 04) next to the QR when the internet path is unavailable.

**Verification:**

- `Grep` - export/QR action bound in `internal/app/`.
- `Grep` - `manualForwardHint` referenced in `internal/app/`.

**Status:** `[x]` done

---

### Step 05.5 - Contract test vector for the Android side

**Files:** `internal/config/schema_test.go`, `docs/CONFIG_FORMAT.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Produce a canonical example payload (fixed field values) and assert the serializer emits it byte-stably; embed the same example JSON in `docs/CONFIG_FORMAT.md`. Phase 07 uses this exact vector as its Android parser fixture, guaranteeing both ends agree.

**Verification:**

- `go test ./internal/config/ -run TestCanonicalVector` passes.
- `Grep` - the canonical JSON example present in `docs/CONFIG_FORMAT.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` is `[x] done`.
- [x] `go build ./...` and `go test ./internal/config/...` pass (2026-07-10: 4/4 PASS - CanonicalVector, AccessPathOrder, RoundTripSerialize, QrPayloadCompressedVariant).
- [x] `docs/CONFIG_FORMAT.md` documents the schema + canonical vector (companion repo `P:\windows\fms_companion\docs\CONFIG_FORMAT.md`).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry: batched at ticket closure via `close-and-log.ps1` (out-of-repo subproject).

**Execution notes (2026-07-10, /spec-all):**

- QR encoder dep: `skip2/go-qrcode` (not in the 01.3 pinned set - the plan omitted a QR lib). QR payload contract: plain JSON when <=900 bytes, else `FMSCFG1:` + base64(gzip(json)) - single prefix decides, both variants documented for the Android parser.
- `ipc.Status` gained a `Password` field (worker fills it) - the exporter needs the credential and the pipe is ACL-restricted; the QR/file embeds the password by contract anyway.
- `BuildConfig(status, resourceName, createdAt)` takes `createdAt` explicitly so the canonical vector is byte-stable (`TestCanonicalVector` asserts exact bytes; the same JSON is embedded in CONFIG_FORMAT.md and becomes the Phase 07 Android fixture).
- Internet path included only when `ExternalHost != "" && ExternalPort > 0 && !IsCgnat`; CGNAT-no-mapping config carries only the LAN path (tested).
- UI: `ExportConfig()` returns QR as data-URI + `manualForwardHint` shown when no internet path; `SaveConfigFile()` via `runtime.SaveFileDialog`, file written 0600.
- `.fmscfg` = plain compact JSON (same bytes as `MarshalCompact`).

---

## Handoff Notes to Next Phase

The config contract (`CONFIG_FORMAT.md` + canonical test vector) is frozen. Phase 06 wraps export in the first-run onboarding; Phase 07 implements the Android importer against the same vector.

---

## Rollback Plan

Revert phase commit(s). If the schema was already consumed by a shipped Android importer, bump `schemaVersion` instead of breaking it.
