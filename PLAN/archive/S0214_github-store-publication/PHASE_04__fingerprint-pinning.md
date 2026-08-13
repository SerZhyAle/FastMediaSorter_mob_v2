# Phase 04 — Signing Fingerprint Pinning

**Strategic spec:** [`../S0214_github-store-publication.md`](../S0214_github-store-publication.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Prevent silent signing-key drift between releases by pinning the expected SHA-256 signing fingerprint of `standard`-release and `vr`-release APKs. The publish script aborts before upload if either APK's fingerprint does not match the pinned value. Document the legitimate key-rotation procedure.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done — publisher script exists and works in dry-run.
- [ ] `apksigner` is on PATH (ships with Android SDK build-tools).
- [ ] Caller has produced at least one signed standard + vr APK pair via `a.ps1 r` and `a.ps1 vr`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/expected-signing-fingerprint.txt` | New | ≤ 10 |
| `scripts/release/publish-github-release.ps1` | Modified | ≤ 400 |
| `docs/DEV_OPS.md` | Modified | ≤ +60 |

---

## Steps

### Step 04.1 — Capture and pin the current signing fingerprint

**Files:** `scripts/release/expected-signing-fingerprint.txt`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `apksigner verify --print-certs <path-to-standard-release-apk>` and `apksigner verify --print-certs <path-to-vr-release-apk>`. Both APKs are expected to share the same release signing key (single keystore). Confirm they do; if they differ, abort and raise the discrepancy with the owner before proceeding. Write the SHA-256 fingerprint (uppercase, colon-separated) to `scripts/release/expected-signing-fingerprint.txt` as the only non-empty line, preceded by a comment block: file purpose, when it was captured, who captured it, the keystore alias used.

**Verification:**

- `Glob` — `scripts/release/expected-signing-fingerprint.txt` exists.
- `Grep` — line matching pattern `^[0-9A-F]{2}(:[0-9A-F]{2}){31}$` present (a 32-byte SHA-256 in uppercase colon-separated form).
- `Grep` — comment header references `keystore alias`.
- Manual cross-check: standard APK fingerprint == vr APK fingerprint == file contents. expected: all three equal | actual: standard=`6A:A6:EA:72:75:2A:E9:44:09:29:F1:E0:BA:F8:F2:AD:13:CC:21:CE:C4:60:6B:F6:22:9D:69:02:06:C4:35:A6`, vr=same, file=same. PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Ran `apksigner verify --print-certs` on `FastMediaSorter_standard_v2.60.5150.150.apk` (SHA-256 `6aa6...35a6`) and `FastMediaSorter_vr_v2.60.5142.107-VR.apk` (SHA-256 `6aa6...35a6`). Both share the same key. Pinned to `scripts/release/expected-signing-fingerprint.txt`. Verification 4/4 PASS (file exists, regex match for 32-byte uppercase colon-separated SHA-256, comment header references "keystore alias", cross-check three values equal). Files: scripts/release/expected-signing-fingerprint.txt (+18 LOC). Dev log recorded.

---

### Step 04.2 — Hook fingerprint check into the publisher

**Files:** `scripts/release/publish-github-release.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a function `Assert-ExpectedFingerprint` to the publisher: invoked between Step 03.3 (staging) and Step 03.4 (release create) of the existing flow. For each staged APK, run `apksigner verify --print-certs` and capture the SHA-256 fingerprint. Compare (case-insensitively, colon-normalised) against `scripts/release/expected-signing-fingerprint.txt`. On mismatch, abort the publish with a clear message listing expected vs actual fingerprint AND a one-line pointer to the rotation procedure in `docs/DEV_OPS.md`. On match, print a confirmation line and continue. The check runs regardless of `-DryRun` (dry-run still validates the gate).

**Verification:**

- `Grep` — function `Assert-ExpectedFingerprint` defined exactly once in `publish-github-release.ps1`.
- `Grep` — `apksigner verify --print-certs` present.
- `Grep` — `expected-signing-fingerprint.txt` referenced (file read).
- Dry-run test with a deliberately wrong fingerprint file (temporarily edit one byte): script aborts non-zero with message containing `expected` and `actual`. Restore the file afterwards.
- expected exit non-zero on mismatch, zero on match | actual: tampered pin → exit 1 with `Fingerprint mismatch ... expected: 00:00:EA... actual: 6A:A6:EA... See docs/DEV_OPS.md "Release Signing Fingerprint (GitHub Store)"`. Restored pin → exit 0 with `Fingerprint OK: ...` for both APKs. PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS (`function Assert-ExpectedFingerprint` 1×, `apksigner verify --print-certs` 1×, `expected-signing-fingerprint.txt` 1× referenced; tampered-pin negative run exit 1 with mismatch message + DEV_OPS pointer; restored-pin positive run exit 0 with `Fingerprint OK` for both staged APKs). Files: scripts/release/publish-github-release.ps1 (+72 LOC; Resolve-Apksigner + Assert-ExpectedFingerprint hooked between staging and release-create). Dev log recorded.

---

### Step 04.3 — Document the rotation procedure in `docs/DEV_OPS.md`

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Append a new section `## Release Signing Fingerprint (GitHub Store)` near the existing release / signing content in `docs/DEV_OPS.md`. Cover: (1) what the pin protects (auto-update via GitHub Store breaks on signing-key change); (2) where the pin lives (`scripts/release/expected-signing-fingerprint.txt`); (3) how the publisher uses it; (4) the rotation procedure — when legitimate (lost keystore, mandated rotation) and the user-facing consequence (every GitHub Store user must reinstall). The rotation procedure lists the exact steps: produce new keystore → build new release → capture new fingerprint → update the pin file → publish release with explicit `## Note: signing-key rotation` in WHATS_NEW.md → record decision in a new ADR-style entry inside the section.

**Verification:**

- `Grep` — heading `## Release Signing Fingerprint (GitHub Store)` matches exactly once in `docs/DEV_OPS.md`.
- `Grep` — `expected-signing-fingerprint.txt` referenced.
- `Grep` — both `rotation` and `WHATS_NEW.md` referenced under that heading.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS (heading `## Release Signing Fingerprint (GitHub Store)` 1×, `expected-signing-fingerprint.txt` 2× under that section, `rotation`+`WHATS_NEW.md` 7× combined). Files: docs/DEV_OPS.md (+58 LOC; rotation procedure + ADR log scaffold). Dev log recorded.

---

### Step 04.4 — End-to-end dry-run with the gate active

**Files:** _(no source changes — execution-only)_
**Depends on:** Step 04.3

**Prompt for developer:**

> Run `pwsh -File scripts/release/publish-github-release.ps1 -DryRun` with: (a) the real fingerprint file from Step 04.1, (b) a real `standard` + `vr` APK pair already built. Confirm the fingerprint check passes, all prior steps still pass, no actual `gh release create` runs.

**Verification:**

- Dry-run exit code 0 with fingerprint match.
- Script stdout contains literal string `Fingerprint OK` (or equivalent; document the marker).
- expected exit 0 | actual: 0 with `Fingerprint OK: FastMediaSorter-standard-2.60.5152.145.apk` + `Fingerprint OK: FastMediaSorter-vr-2.60.5152.145.apk` + publish plan visible. PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — End-to-end dry-run pass: branch guard (warning on DEBUG-v002 under -DryRun), version `2.60.5152.145` extracted, both APKs discovered + staged with deterministic names, fingerprint gate prints `Fingerprint OK` for both, notes extractor falls back to placeholder (no WHATS_NEW section for this DEBUG version), publish plan visible, no GitHub mutation. Exit 0. Files touched in this step: none (execution-only). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `expected-signing-fingerprint.txt` contains the live release-signing fingerprint.
- [ ] `publish-github-release.ps1` aborts on fingerprint mismatch (dry-run with tampered pin file).
- [ ] `docs/DEV_OPS.md` has the rotation section.
- [ ] Dev log entries added for all three modified/new files.

---

## Handoff Notes to Next Phase

Phase 05 (README badge) and Phase 06 (final cleanup) do not depend on Phase 04. The pin file becomes an operator-owned artifact going forward — any legitimate keystore rotation re-runs Step 04.1.

---

## Rollback Plan

Revert the publisher modification (remove `Assert-ExpectedFingerprint` call), delete the pin file, revert the DEV_OPS.md section. Fingerprint enforcement disappears but auto-update still works as long as the keystore stays stable.
