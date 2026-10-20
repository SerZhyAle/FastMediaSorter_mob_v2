<sub class="doc-stamp">26.10.18 17:45</sub>

# Pointer - `DIAGNOSTIC-REPORT`

| | |
| --- | --- |
| **Id** | `DIAGNOSTIC-REPORT` |
| **Version** | 0.14, draft. Owner: StreamsPlayer |
| **Home** | `diagnostic-report/README.md` in the shared contracts catalog |
| **Role here** | producer and consumer (sanitized diagnostic logs and export bundle) |

## What this repository must do to stay conformant

- Package logs and diagnostics into `fastmediasorter_logs.zip`, bounded per rule 4: a file above 16 MB is packed as its first 1 MB and last 7 MB around a `[Diag] LOG TRUNCATED` marker; session logs rotate at 5 MB each, the last 10 kept.
- Redact every secret shape rule 3 names (section 7) - secret query parameters including the
  signed-CDN family, credential-in-path segments, URL userinfo whose password contains `/`, `?` or `#`,
  PINs and private auth keys: the value becomes `[REDACTED]`, the parameter name stays.
- Redact by content, not by key name: a JSON, query-string or connection-string value is redacted
  field by field, or dropped whole (section 8 C).
- Redaction happens before any head/tail cut; a source that cannot be redacted before it is cut is
  omitted whole with a `[Diag] LOG OMITTED` marker (section 8 C).
- The 16 MB, 1 MB head and 7 MB tail ceilings are maxima, and a live session log is never pruned.
- Sanitize personal filesystem paths in logs: the app data directory becomes `<APP_DATA>`; Android has no user-named directory for `<USER>`.
- Generate diagnostic bundles only upon explicit user action (no silent background telemetry).
- Treat the exchange account values of section 10 as private diagnostic values: the device token, the pairing code, the exchange login and server address, and every certificate fingerprint including the old and the replacement pin become `[REDACTED]` at the log sink and again when a bundle is exported. An exchange account file is never packed, and an environment summary names no account identity.

## Where it lives here

- `app_v2/.../core/logging/LogExportHelper.kt`.
- `app_v2/.../core/logging/LoggingHelper.kt`.
- `app_v2/.../ui/settings/helpers/GeneralSettingsLogHelper.kt`.
- `app_v2/.../core/security/SecretMasker.kt` - rule 3 redaction for every phone log line.
- `wear/.../core/logging/WearLogTree.kt` (`WearSecretMasker`) - the same rules on the watch.
- `app_v2/.../data/remote/exchange/` - the code that holds the values section 10 names: the account entry (host, login, pinned fingerprint), the token store and the enrollment credential.
