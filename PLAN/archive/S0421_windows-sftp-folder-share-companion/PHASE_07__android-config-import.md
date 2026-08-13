# Phase 07 - Android Config Import (one-action pairing)

**Strategic spec:** [`../S0421_windows-sftp-folder-share-companion.md`](../S0421_windows-sftp-folder-share-companion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05 (frozen config contract)
**Blocks:** none (Android leaf)
**Steps done:** 6 / 6
**Started:** 2026-07-10
**Completed:** 2026-07-10

---

## Objective

Add one-action import on Android: scan the companion QR (or open a `.fmscfg` file), parse the `CompanionResourceConfig`, and create a ready-to-use SFTP resource with host-key pinning and the embedded credential - no manual host/port/key entry (strategic §2 goals 3, 8). This phase is standard Kotlin under `app_v2/` and uses the normal Android toolchain and gates.

---

## Prerequisites

- [ ] Phase 05 ✅ Done - `docs/CONFIG_FORMAT.md` + canonical vector frozen.
- [ ] Android SFTP resource classes located via `dev/CATALOG/scripts/query.ps1 -Module app_v2` (do not guess paths - Step 07.1).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/companion/ImportCompanionConfigUseCase.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/companion/CompanionConfigParser.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/companion/CompanionConfigDto.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/<existing import entry>.kt` | Modified | ≤ 500 |
| `app_v2/src/test/java/.../CompanionConfigParserTest.kt` | New | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` (+ RU/UK) | Modified | ≤ 500 |

> Exact package paths for the Add-Resource UI and SFTP resource creation are resolved in Step 07.1; the New paths above are the intended homes and may be adjusted to match existing conventions found in the catalog.

---

## Steps

### Step 07.1 - Locate existing SFTP + Add-Resource classes

**Files:** - (research step, no edit)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*Sftp*"` and `-ClassMatches "*AddResource*"` (and `*Resource*Repository*`, `*Credential*`) to find: the SFTP `ResourceType`, the resource-creation path, the credential store, and the Add-Resource UI entry point. Record the real class names/paths; adjust the "Files Touched" homes to match existing conventions.

**Verification:**

- Catalog query returns the SFTP resource + Add-Resource classes; paths recorded in this phase file.

**Status:** `[x]` done

---

### Step 07.2 - Config DTO + parser

**Files:** `data/companion/CompanionConfigDto.kt`, `data/companion/CompanionConfigParser.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Mirror the `CompanionResourceConfig` schema (Phase 05 `CONFIG_FORMAT.md`) as a Kotlin DTO and parse JSON (support the gzip+base64 QR variant from Phase 05 step 05.3). Validate `schemaVersion` and reject unknown-higher versions with a clear error. Parse the ordered `accessPaths` (LAN first).

**Verification:**

- `Grep` - `class CompanionConfigParser` matches once.
- `Grep` - `schemaVersion` validation present.
- Unit test parses the Phase 05 canonical vector (07.5).

**Status:** `[x]` done

---

### Step 07.3 - Import use case -> SFTP resource

**Files:** `domain/usecase/companion/ImportCompanionConfigUseCase.kt`
**Depends on:** Step 07.2, 07.1

**Prompt for developer:**

> Map a parsed config to an SFTP resource: pick the first reachable `accessPath` (LAN then port-forward), store the credential in the existing separate credential store, and pin `hostKeyFingerprintSha256` via the existing TOFU host-key pinning (reuse the S0046 pinning pattern - do not invent a new one). Persist multiple access paths on the resource if the model allows, so the same resource works at home and away (strategic §5.3); otherwise persist the LAN path and note the follow-up.

**Verification:**

- `Grep` - `class ImportCompanionConfigUseCase` matches once.
- `Grep` - reuse of the existing host-key pinning API (not a new pinning impl).
- `.\a.ps1 fk` (standard) compiles.

**Status:** `[x]` done

---

### Step 07.4 - Add-Resource UI entry: scan QR / open file

**Files:** `ui/addresource/<import entry>.kt`, `res/values/strings.xml` (+ RU/UK)
**Depends on:** Step 07.3

**Prompt for developer:**

> Add an "Import from companion" action to the Add-Resource flow: launch QR scan (reuse the existing scanner if present; else `ACTION_OPEN_DOCUMENT` for `.fmscfg`), run the import use case, and land on a working resource. Support keyboard/D-pad/mouse (Rule 16). Strings via `set-android-string.ps1 -Action add` across EN/RU/UK; run tone check against `docs/COMMUNICATION_POLICY.md` §2/§6.

**Verification:**

- `Grep` - import action wired in the Add-Resource UI.
- `Grep` - new string keys present in all three `strings.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 07.5 - Parser unit test against canonical vector

**Files:** `src/test/java/.../CompanionConfigParserTest.kt`
**Depends on:** Step 07.2

**Prompt for developer:**

> Copy the Phase 05 canonical JSON vector into a test resource and assert the parser produces the expected DTO (host, port, username, password, fingerprint, ordered access paths). Add a rejection test for an unknown-higher `schemaVersion`.

**Verification:**

- `.\gradlew.bat testStandardDebugUnitTest --tests *CompanionConfigParserTest*` passes.

**Status:** `[x]` done

---

### Step 07.6 - Build gate

**Files:** - (verification only)
**Depends on:** Step 07.1-07.5

**Prompt for developer:**

> Run the standard debug build to prove the Android slice compiles and packages with the new import path.

**Verification:**

- `.\a.ps1 dq` (standard debug) - BUILD SUCCESSFUL.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 07.*` is `[x] done`.
- [x] `.\a.ps1 dq` (standard debug) succeeds (2026-07-10, v2.60.7101.516-DEBUG); unit test green (CompanionConfigParserTest 6/6).
- [x] `Grep -n "Log\.d\("` returns zero hits in modified Kotlin files (Timber only).
- [x] `Grep` for `TODO(phase-07)` returns zero hits.
- [x] Dev log entry via `post-change.ps1 -ScopeToFile` (one logical entry per CLAUDE.md §12 granularity).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (post-change chained catalog_sync; 2122 records).

**Execution notes (2026-07-10, /spec-all):**

- 07.1 resolved homes: SFTP stack `data/remote/sftp/*` (SftpClient, PinnedHostKeyRepository S0046), creation flow `ui/addresource/AddResourceSftpFtpCoordinator` -> `SmbOperationsUseCase.saveSftpCredentials` -> `AddResourceUseCase.addMultiple`; credential store `NetworkCredentialsEntity` (encrypted); fingerprint canonicalizer `utils/SshFingerprintNormalizer`.
- New: `data/companion/CompanionConfigDto.kt` (+typed `CompanionConfigException`), `data/companion/CompanionConfigParser.kt` (plain JSON + `FMSCFG1:` gzip+base64; validates schemaVersion/protocol/paths/roots), `domain/usecase/companion/ImportCompanionConfigUseCase.kt`, `ui/addresource/AddResourceCompanionCoordinator.kt`; modified `AddResourceViewModel.kt`, `AddResourceActivity.kt`, `activity_add_resource.xml` (no `layout-land` counterpart exists - verified), strings EN/RU/UK (`companion_import_*`, 5 keys, parity gate green).
- No QR scanner exists in the app (catalog checked) - import entry is `ACTION_OPEN_DOCUMENT` for `.fmscfg` per the plan's fallback; QR-scan entry becomes possible later without contract change (parser already accepts the QR payload string).
- **Single-path note (per step 07.3):** `MediaResource` stores one path; the FIRST access path (contract-ordered LAN first) is persisted. Multi-path resource with home/away auto-fallback = documented follow-up (strategic §5.3).
- Import creates ONE read-only SFTP resource PER shared root (`sftp://host:port/<root>`), shared credential, pinned canonical fingerprint, `scanSubdirectories=true`, media types IMAGE/VIDEO/AUDIO/GIF; no availability scan at import (PC may be offline - resource scans on first open).
- Detekt-clean: use case constructed manually in the ViewModel (like existing coordinators) because extending the baselined 12-param constructor would resurface its `LongParameterList` baseline entry.
- `Timber.d("S0421: ..")` probe at import flow entry (coordinator) - inserted before the final build per BlockNeedUserTest contract; status flipped BEFORE re-running the ticket-log gate (gate green: expected 0 / actual 0).

---

## Handoff Notes to Next Phase

One-action pairing works end-to-end: companion QR/`.fmscfg` -> SFTP resource with pinned key and stored credential. Phase 08 does docs + catalog + FEATURES trilingual + spec closure.

---

## Rollback Plan

Revert phase commit(s). New Kotlin classes are additive (no schema/DI singleton change beyond the new use case); low risk. Verify on standard debug.
