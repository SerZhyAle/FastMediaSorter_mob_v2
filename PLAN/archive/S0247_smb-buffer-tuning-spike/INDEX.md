# Tactical: S0247 — SMB buffer-tuning spike

**Ticket:** S0247
**Status:** BlockNeedUserTest
**Strategic:** [`PLAN/S0247_smb-buffer-tuning-spike.md`](../S0247_smb-buffer-tuning-spike.md)
**Tier:** 1 — Quick

> **Scope:** Single-task PoC spike. ~30 min wall-clock. Reverts after measurement.

---

## Affected touchpoints (discovered)

- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
  - `fastConfig` (lines 153..162), `mediumConfig` (lines 164..173), `degradedConfig` (lines 178..187).
  - Three `SmbConfig.builder()` tiers. Currently: fast/medium `withReadBufferSize(65_536).withWriteBufferSize(65_536).withTransactBufferSize(4280)`; degraded `withReadBufferSize(32_768).withWriteBufferSize(32_768).withTransactBufferSize(4280)`.
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt`
  - `downloadFile()` (line 44): `smbFile.inputStream.use { input -> input.copyTo(outputStream) }` — default Kotlin `copyTo` uses 8 KiB chunk.
  - `uploadFile()` (line 229): `smbFile.outputStream.use { output -> localInputStream.copyTo(output) }` — default Kotlin `copyTo` uses 8 KiB chunk.
  - Both already have `copyToWithProgress` variants used when a `progressCallback` is supplied; spike applies to BOTH branches.

---

## Phase 1 — Apply PoC patch (SmbConfig + consumer chunk)

Goal: raise SMBJ read/write buffer ceiling to 1 MiB on all three config tiers and ensure consumer-side copy loop reads in ≥ 64 KiB chunks (not the default 8 KiB Kotlin `copyTo`).

### Steps

1. **`SmbConnectionManager.kt`** — patch all three `SmbConfig.builder()` tiers:
   - `fastConfig`: `withReadBufferSize(1_048_576).withWriteBufferSize(1_048_576)` (replace `65536` literals). Leave `withTransactBufferSize(4280)` alone — not part of spike (per S0246 §6.3 verbatim).
   - `mediumConfig`: same change as `fastConfig`.
   - `degradedConfig`: same change (replace `32768` literals) — even degraded tier benefits from the test; if it regresses we still learn something useful.
   - Add `// S0247 spike: revert after measurement` comment above each changed line.

2. **`SmbFileOperations.kt`** — wrap consumer streams with `java.io.BufferedInputStream(..., 65_536)` / `BufferedOutputStream(..., 65_536)`:
   - `downloadFile()` body: `smbFile.inputStream.use { rawInput -> val input = java.io.BufferedInputStream(rawInput, 65_536); if (progressCallback != null) input.copyToWithProgress(outputStream, fileSize, progressCallback) else input.copyTo(outputStream) }`.
   - `uploadFile()` body: `smbFile.outputStream.use { rawOutput -> val output = java.io.BufferedOutputStream(rawOutput, 65_536); if (progressCallback != null) localInputStream.copyToWithProgress(output, fileSize, progressCallback) else localInputStream.copyTo(output); output.flush() }`.
   - Add `// S0247 spike: revert after measurement` above each wrap.
   - Imports: ensure `java.io.BufferedInputStream` and `java.io.BufferedOutputStream` are present.

3. **Read-only zone check:** none of the touched paths fall under `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`. ✓

### Verification

- `expected:` `SmbConfig.Builder` calls in `SmbConnectionManager.kt` (5 changed lines × 3 tiers = literal change verified by grep) — `withReadBufferSize(1_048_576)` appears exactly **3** times, `withWriteBufferSize(1_048_576)` appears exactly **3** times, and **no** `withReadBufferSize(65536)` / `withWriteBufferSize(65536)` / `withReadBufferSize(32768)` / `withWriteBufferSize(32768)` remain.
- `expected:` `BufferedInputStream` + `BufferedOutputStream` wrappers appear inside `SmbFileOperations.downloadFile()` and `SmbFileOperations.uploadFile()` (4 new usages total, all gated by `// S0247 spike` comment).

---

## Phase 2 — Build sanity check

Goal: confirm the patch compiles cleanly on the target build variant.

### Steps

1. From repo root: `pwsh -File a.ps1 dq` (quiet debug = `assembleStandardDebug` + suppress UP-TO-DATE / deprecated-DSL noise).

### Verification

- `expected:` Gradle exit code 0; build `:app_v2:assembleStandardDebug` reaches `BUILD SUCCESSFUL`.
- On failure: read full output via `pwsh -File a.ps1 d`, fix compile error, re-run. Up to 3 retries (per `/spec-all` MAX_BUILD_RETRIES). Persistent failure → hard stop.

---

## Phase 3 — Insert measurement Timber tags + status BlockNeedUserTest

Goal: instrument the transfer hot path with `S0247:`-prefixed Timber tags so the owner sees logged before/after throughput in logcat during the manual measurement round. Aligns with CLAUDE.md "Debug Verification Tags" invariant — tag exists in code iff status is `BlockNeedUserTest`.

### Steps

1. **`SmbFileOperations.downloadFile()`** — at the start of `connectionManager.withConnection { share -> ... }` block (after `share.openFile(..)` returns the `file` handle, before `file.use`), capture `startNs = System.nanoTime()` and emit `Timber.d("S0247: SMB download START path=$remotePath size=${file.fileInformation.standardInformation.endOfFile}")`. At the end (just after `SmbResult.Success(Unit)` line), compute `elapsedMs = (System.nanoTime() - startNs) / 1_000_000` and `mbPerSec` (use file size); emit `Timber.d("S0247: SMB download DONE elapsedMs=$elapsedMs throughputMBps=$mbPerSec path=$remotePath")`.

2. **`SmbFileOperations.uploadFile()`** — symmetric instrumentation around the `localInputStream.copyTo(output)` / `copyToWithProgress` call. Use `fileSize` parameter (already accepted by `uploadFile`) for throughput math; if zero, log `throughputMBps=unknown`.

3. **Status flip:** `pwsh -File scripts/spec_catalog/update.ps1 -Id S0247 -Status BlockNeedUserTest`.

4. Update the strategic spec header (`PLAN/S0247_smb-buffer-tuning-spike.md`): `Status: Approved` → `Status: BlockNeedUserTest`.

### Verification

- `expected:` exactly 4 `Timber.d("S0247: ` occurrences across `app_v2/src/main/java/**` (download START + download DONE + upload START + upload DONE).
- `expected:` `pwsh -File scripts/spec_catalog/select.ps1 -Id S0247 -Format json` returns `"status":"BlockNeedUserTest"`.

---

## Phase 4 — Owner manual measurement protocol

Goal: clear, repeatable measurement ritual the owner runs once. Not automatable — depends on owner's physical NAS + Wi-Fi 7 environment.

### Steps (executed by owner, not by /spec-all)

1. Pick **one** representative file ≥ 100 MiB already on the NAS share.
2. Open logcat filter to `S0247:` tag.
3. **Before-baseline (this commit, with patch applied — 1 MiB buffers):** trigger 3 downloads (NAS → device) and 3 uploads (device → NAS) of the same file. Record `throughputMBps` from each log line. Average each direction.
4. **(Optional) Counter-baseline:** if curiosity demands, `git stash` the spike patch, repeat with original 64 KiB SmbConfig. Otherwise skip — historical baseline is the existing complaint of «<2 МБ/с» in S0246 §1.
5. Append a single line to `dev/CHANGELOG.md` (via `.\scripts\add_to_dev_log.ps1`): `S0247 spike result: download <X> MBps, upload <Y> MBps; recommendation: A|B|C` per S0247 §2.3.
6. Update S0247 strategic `## 11. Критерии готовности` items 3 and 4 to `CLOSED` with the measured numbers.

### Verification

- `expected:` `dev/CHANGELOG.md` contains a row with `S0247 spike result:` substring and three numbers (download, upload, throughput-vs-recommendation).

---

## Phase 5 — Rollback ritual (manual, post-measurement)

Goal: remove all spike code from production tree once the measurement is recorded. S0247 is a spike, NOT an implementation — the patch must NOT persist into release-candidate branches.

### Steps (executed by owner)

1. Locate spike commits: `git log --grep="S0247" --oneline`.
2. `git revert <sha>` for each spike commit, OR if all spike commits are squashed/recent: `git reset --hard HEAD~N` (only if branch is private and not pushed).
3. Run `pwsh -File scripts/spec_catalog/update.ps1 -Id S0247 -Status Verified` to close the ticket (research deliverable complete, spike measured, code reverted).
4. Strategic spec: `Status: BlockNeedUserTest` → `Status: Verified`. Add `## Last Audit` block describing the rollback.
5. Grep `app_v2/` for any leftover `Timber.d("S0247:` and remove if found (CLAUDE.md "Debug Verification Tags" — tags must be absent when status leaves `BlockNeedUserTest`).

### Verification

- `expected:` `grep -r 'S0247' app_v2/src/main/` returns no matches in `.kt` files (only spec/log paths if any).
- `expected:` `git status` clean against `app_v2/`.

---

## Non-goals (out of scope for this tactical)

- Productionising the 1 MiB buffer config — that becomes a separate ticket (or a new phase in S0248) if measurement recommends.
- Changing `withTransactBufferSize` — separate concern, not in S0246 §6.3 recipe.
- Touching `SmbDataSource.kt` (playback path) — different hot loop, different concerns; spike measures download/upload only.
- Testing on multiple NAS / multiple Wi-Fi environments — single-environment spike per owner request §3.1.
