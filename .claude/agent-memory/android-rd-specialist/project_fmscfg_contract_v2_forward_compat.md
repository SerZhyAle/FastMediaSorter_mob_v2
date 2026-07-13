---
name: fmscfg-contract-v2-forward-compat
description: .fmscfg/QR contract (S0988) is producer-frozen-shape + consumer-tolerant; Android client must accept schemaVersion 2
metadata:
  type: project
---

The `.fmscfg` / QR import contract ([[project_fms_companion_subproject]], spec S0988, file `PLAN/S0988_qr-scan-companion-import/attachments/01__qr-import-android-contract.md`) is stable for release without changes. Audit recorded as §5.4 "Schema versioning & backward-compatibility audit" (2026-07-13).

**Why:** The file is built by the LITE/Windows side (`ShareConfigBuilder`), not the Go worker. A future two-tier "packages of typed shares" model reorganizes only how the sender picks roots, not the emitted file shape - every future concept maps onto existing/additive `roots[]` fields (`profile`, `readOnly`, `isDestination`, `accessPin`, `slideshowInterval` are v2-additive). Worker already enforces per-root RO (S1016).

**How to apply:** Before shipping the Android importer, verify three client rules, else typed shares break: (1) parser accepts `schemaVersion == 2` not strictly `== 1` - typed shares carry `profile`, a v2 field, so future exports are almost always v2; (2) ignore unknown fields; (3) degrade unknown enum tokens (`profile`/`mediaTypes`/`accessPaths[].kind`) to a safe default, never reject. §7.5 has a v2 test vector; §8 checklist enforces all three. Contract only breaks if producer raises schemaVersion above client, changes an existing field's meaning, or makes an unknown field mandatory - none required by the future model.
