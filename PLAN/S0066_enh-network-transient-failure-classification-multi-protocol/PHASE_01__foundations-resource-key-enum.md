# Phase 01 — Foundations: Universal Resource Key + TransientReason Enum

**Strategic spec:** [`../S0066_enh-network-transient-failure-classification-multi-protocol.md`](../S0066_enh-network-transient-failure-classification-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Introduce `TransientReason` enum and a top-level `extractNetworkResourceKey(path)` helper. No call-site changes — only new code that downstream phases will adopt. Add unit tests for `extractNetworkResourceKey` covering all four supported prefixes and local/null cases.

---

## Prerequisites

- [x] All §6 research items Resolved (see INDEX.md Pre-Implementation Blockers).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/TransientReason.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkResourceKey.kt` | New | ≤ 80 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/glide/NetworkResourceKeyTest.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 — Create `TransientReason` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/TransientReason.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file declaring `enum class TransientReason` in package `com.sza.fastmediasorter.data.network.glide` with the following values: `STALE_SHARE`, `BROKEN_CHANNEL`, `BROKEN_PIPE`, `TIMEOUT`, `TRANSPORT`. Add a one-line KDoc above each value naming the protocol(s) it applies to. Add a top-level KDoc tagging the enum as `// S0066`. No methods or fields beyond the enum constants.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/TransientReason.kt` exists.
- `Grep` — `enum class TransientReason` matches exactly once.
- `Grep` — each of `STALE_SHARE`, `BROKEN_CHANNEL`, `BROKEN_PIPE`, `TIMEOUT`, `TRANSPORT` matches at least once in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: TransientReason.kt (+19 LOC). Dev log recorded.

---

### Step 01.2 — Create `extractNetworkResourceKey` helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkResourceKey.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a new file declaring an `internal` top-level function `fun extractNetworkResourceKey(path: String): String?` in package `com.sza.fastmediasorter.data.network.glide`. Behavior: returns a normalized `"<scheme>://host:port"` for paths starting with `smb://` (default port 445), `sftp://` (default port 22), `ftp://` (default port 21). Returns `null` for any other input (including local paths, empty strings, `null`-equivalent literal, and `cloud://` since cloud is out of scope). Reuse the parsing logic from the existing `NetworkVideoFrameDecoder.extractSmbServerKey` (host:port split, default-port fallback). Add a top-level KDoc referencing `S0066`.
>
> Also add an `internal` helper `fun pathBelongsToResource(path: String, resourceKey: String): Boolean` that returns `true` iff `extractNetworkResourceKey(path) == resourceKey`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkResourceKey.kt` exists.
- `Grep` — `fun extractNetworkResourceKey\(path: String\): String\?` matches exactly once.
- `Grep` — `fun pathBelongsToResource\(path: String, resourceKey: String\): Boolean` matches exactly once.
- `Grep` — `"smb://"`, `"sftp://"`, `"ftp://"` all present in this file.
- `Grep` — `"cloud://"` does NOT appear (out of scope).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 5/5 PASS. Files: NetworkResourceKey.kt (+33 LOC). Dev log recorded.

---

### Step 01.3 — Unit tests for `extractNetworkResourceKey` and `pathBelongsToResource`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/glide/NetworkResourceKeyTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create a JUnit 4 test class `NetworkResourceKeyTest` in package `com.sza.fastmediasorter.data.network.glide`. Cover the following cases for `extractNetworkResourceKey`:
> - `smb://192.168.1.10/share/file.mp4` → `"smb://192.168.1.10:445"`.
> - `smb://server:139/share/file.mp4` → `"smb://server:139"`.
> - `sftp://host/path/file.mp4` → `"sftp://host:22"`.
> - `sftp://host:2222/path/file.mp4` → `"sftp://host:2222"`.
> - `ftp://example.com/dir/file.mp4` → `"ftp://example.com:21"`.
> - `ftp://example.com:2121/dir/file.mp4` → `"ftp://example.com:2121"`.
> - `/storage/emulated/0/x.mp4` → `null`.
> - `""` → `null`.
> - `cloud://drive/file.mp4` → `null` (out of scope).
>
> For `pathBelongsToResource`, cover at least:
> - `pathBelongsToResource("smb://192.168.1.10/share/x.mp4", "smb://192.168.1.10:445")` → `true`.
> - `pathBelongsToResource("smb://192.168.1.11/share/x.mp4", "smb://192.168.1.10:445")` → `false`.
> - `pathBelongsToResource("/local/x.mp4", "smb://192.168.1.10:445")` → `false`.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/network/glide/NetworkResourceKeyTest.kt` exists.
- `Grep` — `class NetworkResourceKeyTest` matches once.
- `Grep` — `@Test` matches at least 9 times in this file.
- `Grep` — `assertEquals\(.*"smb://192.168.1.10:445"` present.
- `Grep` — `assertEquals\(.*"sftp://host:22"` present.
- `Grep` — `assertEquals\(.*"ftp://example.com:21"` present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 5/5 PASS (12 @Test cases). Files: NetworkResourceKeyTest.kt (+99 LOC). Dev log recorded.

---

### Step 01.4 — Build gate

**Files:** —
**Depends on:** Steps 01.1–01.3

**Prompt for developer:**

> Run `/build` for `standard debug`. Confirm compilation passes with the new files. Do not invoke gradle directly.

**Verification:**

- `/build` skill returns success for `standard debug`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — BUILD SUCCESSFUL (standard debug, 45s, v2.60.5031.758).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (two new files: `TransientReason.kt`, `NetworkResourceKey.kt`).

---

## Handoff Notes to Next Phase

Phase 02 will introduce `transientFailureReason: TransientReason?` on `NetworkMediaDataSource` and per-protocol detectors that populate it. Phase 02 must keep the existing `encounteredStaleShare: Boolean` field for backward compatibility — it is set alongside `transientFailureReason = STALE_SHARE`.

---

## Rollback Plan

Revert the phase commit — no public API consumed yet, no data migration, no UI surface.
