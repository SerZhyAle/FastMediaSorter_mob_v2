# Developer Specification: Network Integration Test Failure Remediation

## 1. Document Control

- **Document ID:** FMS-SPEC-INTTEST-NET-001
- **Status:** Ready for implementation
- **Module:** `app_v2`
- **Date:** 2026-02-19

## 2. Objective

Eliminate current integration test failures in network file operations and provide deterministic diagnostics for future regressions.

Target failing scenarios:

1. `Copy SFTP -> Local` fails with `No such file`.
2. `Copy SMB -> SFTP` fails with `Failed to download source for bridge copy`.
3. `Copy FTP -> SMB` fails with `Authentication failed`.

## 3. Problem Statement

Current failures indicate a combination of:

1. Non-deterministic source test-file existence.
2. Incorrect or inconsistent remote URI/path construction in test code.
3. Credential resolution ambiguity (especially SMB) without sufficient observability.

## 4. Scope

### 4.1 In Scope

1. `IntegrationTestRunner` logic hardening for remote copy tests.
2. Path/URI construction normalization for `sftp://`, `smb://`, `ftp://` test flows.
3. Additional structured diagnostics in transfer strategies.

### 4.2 Out of Scope

1. Production feature changes unrelated to integration tests.
2. Credentials storage redesign.
3. Changes to test infrastructure outside project codebase.

## 5. Functional Requirements

### FR-1: Deterministic Source Preparation

1. `testCopySftpToLocal` and `testCopyFtpToLocal` must verify source file existence before copy.
2. If source file is absent, test must create/upload required source file in a controlled setup step.
3. Setup failure must produce explicit diagnostic result (not generic copy failure text).

### FR-2: URI/Path Construction Correctness

1. Test code must construct protocol-specific URIs consistently.
2. Avoid malformed URIs (including accidental extra slash sequences) unless protocol parser explicitly requires them.
3. Paths used in operation requests must be logged in normalized form.

### FR-3: SMB Credential Traceability

1. SMB credential resolution must log lookup path (which source was queried, success/failure per step).
2. Username may be logged; password must never be logged.
3. Failures must include actionable reason (`missing credentials`, `wrong account`, `path mismatch`, etc.).

### FR-4: SFTP Path Parsing Observability

1. SFTP parser must log parsed host, port, and remote path for test operations.
2. Parser failures must include original input (sanitized) and parse stage.

## 6. Non-Functional Requirements

1. Logging implementation must use `Timber` (no `Log.d`).
2. New logs must be concise and non-sensitive.
3. Existing public APIs and behavior outside test/debug scope must remain backward compatible.

## 7. Change Specification by File

### 7.1 `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/IntegrationTestRunner.kt`

Required changes:

1. Introduce deterministic source-file setup for SFTP/FTP download tests.
2. Normalize remote URI/path construction helpers.
3. Add structured logs:
    - resolved source and destination paths
    - selected protocol
    - credential identity (username only, masked secrets)
4. Ensure failure reporting distinguishes setup failure vs copy failure.

### 7.2 `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt`

Required changes:

1. Extend `resolveSmbCredentials` diagnostics with step-by-step lookup logging.
2. Explicitly log final credential source when resolved.
3. Maintain secret redaction in all error paths.

### 7.3 `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt`

Required changes:

1. Add parse diagnostics in SFTP path parser.
2. Log parsed host/port/path and normalized output used by transfer operations.

## 8. Validation Strategy

### 8.1 Build Validation (Mandatory)

1. Run `assembleStandardDebug`.
2. No compilation errors in touched files.

### 8.2 Functional Validation (Mandatory)

Re-run integration tests and verify target cases:

1. `Copy SFTP -> Local` passes or fails with explicit setup/root-cause message.
2. `Copy SMB -> SFTP` no longer fails on bridge source ambiguity.
3. `Copy FTP -> SMB` provides clear credential-resolution diagnostics and succeeds with valid credentials.

### 8.3 Log Validation (Mandatory)

Verify logs include:

1. Normalized operation paths.
2. SFTP parse result (host/port/path).
3. SMB credential lookup trace.
4. No password/token leakage.

## 9. Acceptance Criteria

1. All three listed failing scenarios are either fixed or fail with deterministic actionable diagnostics.
2. Build succeeds for debug variant.
3. No secrets are present in logs.
4. No unrelated behavior regression in network transfer strategies.

## 10. Risks and Mitigations

1. **Risk:** Additional logs create noise.  
    **Mitigation:** Use structured, stage-based log prefixes and debug-level granularity.
2. **Risk:** Path normalization introduces protocol-specific regressions.  
    **Mitigation:** Keep protocol-specific helper functions and verify each protocol independently.
3. **Risk:** Credential tracing accidentally exposes sensitive data.  
    **Mitigation:** redact all secrets; only username and source identifier allowed.

## 11. Definition of Done

1. All in-scope file changes implemented.
2. Mandatory validation steps completed.
3. Acceptance criteria fully satisfied.
4. PR notes include before/after failure evidence and log samples (sanitized).
