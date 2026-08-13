# Research 01 - Reliable file-type association for the resource share file

**Strategic item:** §6 - reliable «open with» association on Android.
**Status:** Resolved.
**Date:** 2026-06-15.

## Question

Which intent-filter set makes a custom resource-share file open in FastMediaSorter from file managers and the system share sheet, across modern Android (content:// URIs) without polluting the global «open with» chooser.

## Findings

- For `content://` URIs Android matches an `<intent-filter>` by MIME type only - the URI path and `pathPattern`/file extension are ignored. A custom extension (`.fmsr`) is invisible to a content-scheme filter.
- MimeTypeMap has no mapping for a custom extension, so a file manager hands a `.fmsr` content URI as `application/octet-stream`. Matching `application/octet-stream` would register the app for every unknown binary on the device - unacceptable chooser noise.
- For `file://` URIs `pathPattern` works: `scheme="file"` + `host="*"` + `pathPattern=".*\\.fmsr"` + `mimeType="*/*"` matches by extension. Modern file managers increasingly hand `content://`, so this covers only part of the field.
- `ACTION_SEND` matches by the MIME type the **sender** puts on the intent, not by anything derived from the file. A custom vendor MIME (`application/vnd.fms.resources+xml`) set by our own export flow matches a `SEND` filter on the exact type with zero collision with other apps.
- The project already ships this exact pattern for media (S0380): per-type `ACTION_VIEW` and `ACTION_SEND` activity-aliases targeting `ReceiveShareActivity`, plus a `FileProvider` (`${applicationId}.fileprovider`).

## Decision (drives the plan)

Three entry points, ordered by reliability:

1. **Settings «Import..»** - `ActivityResultContracts.OpenDocument`. Always reliable, no association needed. Primary path.
2. **`ACTION_SEND` with the vendor MIME** `application/vnd.fms.resources+xml`. Reliable friend-to-friend: export shares the file with this exact type, the recipient's app declares a `SEND` filter on it. No global chooser noise.
3. **`ACTION_VIEW` `file://` `pathPattern=".*\\.fmsr"`** - best-effort for file managers still using `file://`. A content-scheme VIEW filter is also declared on the vendor MIME for the rare provider that reports it.

`application/octet-stream` is deliberately NOT registered - the noise cost outweighs the niche gain, and entry points 1 and 2 cover the real flows.

Format: reuse the existing `<media-resources>` XML (the bundled-config parser already reads it). Add a `version` attribute on the root (parser ignores unknown root attributes, so old files stay readable). Extension `.fmsr`; vendor MIME `application/vnd.fms.resources+xml`.

## Sources

- [Registering Your Android App for File Types](https://richardleggett.com/blog/2013/01/26/registering_for_file_types_in_android/)
- [Android intent filter for a particular file extension](https://ask.xiaolee.net/questions/1125473)
- Project manifest precedent: `app_v2/src/main/AndroidManifest.xml` (S0380 VIEW/SEND aliases, `ReceiveShareActivity`, FileProvider).
