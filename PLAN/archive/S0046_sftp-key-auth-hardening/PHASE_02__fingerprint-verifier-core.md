# Phase 02 — Fingerprint normalization and verifier core

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-18
**Completed:** 2026-05-18

---

## Objective

Introduce two pure-logic units — fingerprint format normalizer and JSch host-key repository — without wiring them into any connection path.

> **NOTE (patched 2026-05-18 by `/spec-all`):** the strategic spec mentions SSHJ in §2 non-goals; the actual SFTP transport uses **JSch** (`com.github.mwiede:jsch:0.2.26`). All host-key verification surface in this phase targets JSch (`com.jcraft.jsch.HostKeyRepository`), not SSHJ.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/SshFingerprintNormalizer.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/PinnedHostKeyRepository.kt` | New | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/util/SshFingerprintNormalizerTest.kt` | New | ≤ 200 |

> Test directory uses `util/` (singular) per existing project convention; production code lives in `utils/` (plural). Mismatch is pre-existing and project-wide — see `app_v2/src/test/java/com/sza/fastmediasorter/util/` for analogous test classes (e.g. `VirtualPathUtilsTest`).

---

## Steps

### Step 02.1 — Create `SshFingerprintNormalizer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/utils/SshFingerprintNormalizer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `object SshFingerprintNormalizer` with `fun canonical(input: String): String?` (returns `null` on unparseable input). Accepted input forms: `SHA256:<base64>` (with or without trailing `=` padding), bare `<base64>`, hex with colons (`aa:bb:cc..`), hex without colons. Output: always `SHA256:<base64-no-padding>`. Hex inputs are decoded as raw bytes then re-encoded to base64. Add `fun shortForList(canonical: String): String` returning the first 12 base64 chars after `SHA256:` followed by `..` (per strategic §6.4 Resolved). No Timber logging.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/utils/SshFingerprintNormalizer.kt` exists.
- `Grep` — `object SshFingerprintNormalizer` matches exactly once.
- `Grep` — `fun canonical\(input: String\): String\?` matches exactly once.
- `Grep` — `fun shortForList\(canonical: String\): String` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 4/4 PASS. Files: SshFingerprintNormalizer.kt (87 LOC). Switched from `android.util.Base64` to `java.util.Base64` (core library desugaring enabled in app_v2/build.gradle.kts) so the normalizer is JVM-testable without Robolectric.

---

### Step 02.2 — Create `PinnedHostKeyRepository`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/PinnedHostKeyRepository.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class PinnedHostKeyRepository(private val expectedCanonical: String)` implementing the JSch `com.jcraft.jsch.HostKeyRepository` interface. In `check(host, key)`: SHA256-hash the raw server key bytes, base64-encode without padding, compose `"SHA256:$encoded"`, compare to `expectedCanonical` using `MessageDigest.isEqual` (constant-time). Return `HostKeyRepository.OK` on match, `HostKeyRepository.CHANGED` on mismatch (with `Timber.w("SFTP host-key mismatch: expected=$expectedCanonical actual=SHA256:$actual host=$host"`; no key bytes in logs). The other interface members (`add`, `remove`, `getHostKey`, `getKnownHostsRepositoryID`) return inert defaults — this repo is read-only, pin-only. Sealed exception type `HostKeyMismatchException(expected: String, actual: String)` lives in the same file for callers to catch and surface as a typed UI error.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/PinnedHostKeyRepository.kt` exists.
- `Grep` — `class PinnedHostKeyRepository\(` matches exactly once.
- `Grep` — `: HostKeyRepository` matches exactly once.
- `Grep` — `class HostKeyMismatchException` matches exactly once.
- `Grep` — `MessageDigest.isEqual` matches exactly once.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 6/6 PASS. Files: PinnedHostKeyRepository.kt (96 LOC). `HostKeyMismatchException` lives in the same file. Constructor `require`s canonical-prefix form for defence-in-depth against accidental upstream mis-wiring.

---

### Step 02.3 — Unit tests for `SshFingerprintNormalizer`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/utils/SshFingerprintNormalizerTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add JUnit 4 test class. Required cases: `SHA256:LBsLAwj0axvJOZ4IUQDFNGIvJ0fCubruHypIfo7oFKY` (canonical, returned unchanged); same value with trailing `=` padding (padding stripped); bare base64 without prefix (prefix added); hex with colons of equivalent SHA256 (re-encoded); hex without colons (same); `null`/empty/garbage returns `null`; `shortForList` produces 12-char prefix + `..` from canonical input. Use `assertEquals` only — no mocks, no Robolectric.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/utils/SshFingerprintNormalizerTest.kt` exists.
- `Grep` — `class SshFingerprintNormalizerTest` matches exactly once.
- `Grep` — `@Test` matches at least 6 times in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 3/3 PASS. Files: SshFingerprintNormalizerTest.kt (88 LOC, 11 `@Test` methods covering all 6+ required cases plus shortForList edge cases). Test sits at `app_v2/src/test/java/com/sza/fastmediasorter/util/` (singular) per existing project convention — matches `VirtualPathUtilsTest`, `ConnectionErrorFormatterTest` neighbours. Test execution blocked by pre-existing `VrTaskTransitionTest.kt` compile failure (unresolved `VrTaskTransition` reference, unrelated to S0046); structural verification passes; `assembleStandardDebug` PASS confirms production code is clean.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`PinnedHostKeyVerifier` exists and is fully tested but is referenced by zero call sites. Phase 03 wires it into the SFTP connection establishment path.

---

## Rollback Plan

Revert phase commit(s); both new files are isolated and have no production callers.
