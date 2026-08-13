# Research 02 - Email + log attachment delivery

Spec: S0483. §6 item 2. Source: codebase audit (read-only) 2026-06-17.

## Question

How to compose an email to the author with the error text in the body and the app log
attached, reusing existing infrastructure?

## Findings

- Support intents are built by a single factory in `ui/common/support/` (`SupportIntentFactory`).
  Its `REPORT_PROBLEM` channel emits `ACTION_SENDTO mailto:..` with a prefilled subject.
- `ACTION_SENDTO` **cannot carry an attachment** - mail clients ignore `EXTRA_STREAM` on it.
  This is already documented in the codebase (`EmailShareTargetHandler`). For an attachment
  the intent must be `ACTION_SEND` with `EXTRA_EMAIL` (recipient array) + `EXTRA_SUBJECT` +
  `EXTRA_TEXT` (body) + `EXTRA_STREAM` (the log URI) + `FLAG_GRANT_READ_URI_PERMISSION`.
- A complete log-export pipeline already exists in `core/logging/` (`LogExportHelper.exportLogs()`):
  it zips all log files (owned by `LoggingHelper` / its file-logging tree) into the app cache
  dir and shares them through `FileProvider`. The chosen attachment scope for S0483 is the
  **full ZIP of all logs** - this pipeline is reused directly.
- FileProvider authority is `${applicationId}.fileprovider`; `file_provider_paths.xml`
  already maps `<cache-path>`, so a zip written to `cacheDir` is shareable without manifest
  changes.
- The settings screen already sends a bug-report email via the support factory
  (`GeneralSettingsLogHelper.openEmailClient()`) - closest existing pattern, but it attaches
  no log.

## Recommended direction (strategic)

- Reuse the existing log-zip pipeline to produce the attachment (full ZIP).
- Build an `ACTION_SEND` email intent (not `ACTION_SENDTO`) carrying recipient + subject +
  body + the zip URI. `/spec-tech` decides whether to extend the support factory with a new
  attachment-capable builder or route through the existing system-share invoker that already
  does `ACTION_SEND` + file + recipient.
- Recipient (owner decision 2026-06-17): **serzhyale@gmail.com** - a crash-report channel
  distinct from the general support address (`sza@ukr.net`). `/spec-tech` decides whether to
  add a new constant or a `BuildConfig`/resource value; keep it a single source of truth.
- Threading: the zip build is disk I/O; keep it off the main thread before firing the chooser.

## API notes

- `ACTION_SEND` + `EXTRA_STREAM` + FileProvider works on all supported API levels (legacy 23+).
- No `@RequiresApi` gating needed. Attachment uses `cacheDir`, not `MediaStore`.
