# Phase 05 - Companion-side deliverable spec

**Goal:** hand the owner a self-contained spec to implement the v2 export on the companion program side (file + QR), so params can be set before export.

## Steps

1. [ ] Write `PLAN/S1002_companion-config-v2-resource-params/COMPANION_EXPORT_SPEC.md` (EN, self-contained) covering:
   - The full v2 JSON schema: every field, type, token vocabulary (profile + mediaType), which are required vs optional, and the v1->v2 diff.
   - The canonical v2 vector (byte-identical copy of `canonical_vector_v2.json`) to freeze on the companion side (`docs/CONFIG_FORMAT.md` + `internal/config/schema_test.go`).
   - Import semantics per field (what the Android side does when present/absent) so the companion UI presents the right controls.
   - Transport: file `.fmscfg` (plain JSON) and QR (`FMSCFG1:` + base64(gzip(json))) - envelope unchanged; QR capacity caveat and the "fall back to file / warn on overflow" requirement.
   - Companion UI requirements: per-shared-root controls for name (label), profile/type (dropdown incl. "audio library"), optional explicit media types, scan conditions, destination + color, comment, PIN, slideshow interval. Priority order: name + profile first.
   - Security note: password and PIN travel in the file/QR - warn the user before export; optionally an "exclude password/PIN" toggle.
2. [ ] Cross-reference: note the frozen-vector contract (a schemaVersion bump breaks the other side unless both adopt v2 together).
   - Verification: `Grep` shows the schema table, canonical vector, and UI-requirements section in the file.

## Notes

- This file is a handoff artifact for the owner (mirrors the S0421 `SPECIFICATION_ANDROID_FOLDER_SHARE.md` pattern). Living in the ticket folder is fine; the owner copies it to the companion repo.
- Do NOT implement companion (Go/VB) code here - out of this repo.
