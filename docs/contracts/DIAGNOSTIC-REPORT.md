# Pointer - `DIAGNOSTIC-REPORT`

| | |
| --- | --- |
| **Id** | `DIAGNOSTIC-REPORT` |
| **Version** | 0.10, draft. Owner: StreamsPlayer |
| **Home** | `diagnostic-report/README.md` in the shared contracts catalog |
| **Role here** | producer and consumer (sanitized diagnostic logs and export bundle) |

## What this repository must do to stay conformant

- Package logs and diagnostics into `fastmediasorter_logs.zip`, bounded per rule 4: a file above 16 MB is packed as its first 1 MB and last 7 MB around a `[Diag] LOG TRUNCATED` marker; session logs rotate at 5 MB each, the last 10 kept.
- Redact credentials, PINs, passwords, and private auth keys as `[REDACTED]` before writing to export.
- Sanitize personal filesystem paths in logs (substitute `<APP_DATA>` / `<USER>`).
- Generate diagnostic bundles only upon explicit user action (no silent background telemetry).

## Where it lives here

- `app_v2/.../core/logging/LogExportHelper.kt`.
- `app_v2/.../core/logging/LoggingHelper.kt`.
- `app_v2/.../ui/settings/helpers/GeneralSettingsLogHelper.kt`.
