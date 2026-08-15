# S1016 - Companion config: per-root writable flag (`readOnly`)

**Status:** Archived

## 1. Problem

The companion `.fmscfg` / QR contract shares every non-destination root as read-only: the
Android import hard-codes `isReadOnly = !isDestination`, so there is no way to import a plain
shared folder as writable (upload / rename / delete / move-into). The companion now emits an
explicit per-root `readOnly` flag; the client must honour it.

## 2. Contract delta (additive, `schemaVersion` unchanged)

- New optional field on each `roots[]` element: `readOnly` (boolean), positioned after `label`.
- Absent == `true` (read-only), so pre-S1016 exports and older parsers stay read-only.
- Additive like `accessNote` (S1014) / `ipv6` - unknown-field-tolerant parse already in place; no
  `schemaVersion` bump. `schemaVersion` still rises to 2 only when `isDestination` is present.
- Frozen write rule (exporter-version-robust): `writable = (readOnly == false) OR (isDestination == true)`, anything else read-only.
- A destination is always writable, so the companion sends `readOnly:false` with `isDestination:true`.
- Untouched: protocol, accessPaths, credentials, `hostKeyFingerprintSha256` / TOFU, QR envelope
  `FMSCFG1:` + base64(gzip). Parse by field name, not position.

## 3. Android changes

- `CompanionRootDto`: add `readOnly: Boolean? = null` after `label`; add `resolveReadOnly()`
  encoding the frozen rule as the single source of truth.
- `ImportCompanionConfigUseCase.buildResource`: set `isReadOnly = root.resolveReadOnly()` (replaces
  `!isDestination`). Only the policy flag changes; physical `MediaResource.isWritable` stays probed by
  the SFTP writability scan. Effective write gate remains `isWritable && !isReadOnly`.
- `ExportCompanionConfigUseCase.buildRoot`: emit `readOnly = if (resource.isReadOnly) null else false`
  (writable -> `false`; read-only -> omitted, absent-default). Keeps QR payload compact and the frozen
  canonical vector byte-identical (Gson omits null).

## 4. Test vectors (§7.3b, `resolveReadOnly()`)

- `{"virtualPath":"/MOV","label":"MOV","readOnly":true}` -> read-only.
- `{"virtualPath":"/Inbox","label":"Inbox","readOnly":false}` -> writable.
- `{"virtualPath":"/Sorted","label":"Sorted","readOnly":false,"isDestination":true}` -> writable + destination (`schemaVersion` 2).
- `readOnly` absent, no destination -> read-only (back-compat).
- `readOnly` absent, `isDestination:true` -> writable.

## 5. Validation

- Unit: `resolveReadOnly()` over the five vectors; `readOnly` parse (true / false / absent) in
  `CompanionConfigParserTest`; existing serializer round-trips stay green.
- No device test: mapping-only change; the `isReadOnly` UI gate already exists. Not `BlockNeedUserTest`.

## Last Audit

**Date:** 2026-07-13
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

- `CompanionConfigDto.CompanionRootDto`: `readOnly: Boolean? = null` (after `label`) + `resolveReadOnly()` = `!(readOnly == false || isDestination == true)` - frozen write rule as single source of truth.
- `ImportCompanionConfigUseCase.buildResource`: `isReadOnly = root.resolveReadOnly()` (replaced `!isDestination`).
- `ExportCompanionConfigUseCase.buildRoot`: `readOnly = if (resource.isReadOnly) null else false` (writable -> false, read-only -> omitted).
- `CompanionConfigParserTest` covers `readOnly` true/false/absent vectors.

Mapping-only contract field; §5 states no device test required. EXEMPT from FEATURES (internal companion contract detail).
