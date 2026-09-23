# Pointer - `FMSCFG`

| | |
| --- | --- |
| **Id** | `FMSCFG` |
| **Version** | 2.1, active; wire carrier `schemaVersion`, this product reads 1-2. Owner: FMS Companion |
| **Home** | `config-interchange/README.md` and `CONFIG_FORMAT.md` in the shared contracts catalog |
| **Role here** | producer and consumer - the `.fmscfg` importer, and the writer of `schemaVersion` 2 files and `FMSCFG1:` QR payloads when a user shares an SFTP resource |

## What this repository must do to stay conformant

- Refuse a `schemaVersion` above the supported one before reading any field, with an "update the app"
  message and no partial import.
- Ignore an unknown `accessPaths[].kind`; take every missing optional root field at the default the
  contract's table gives, never at a local choice.
- Import merges by path and never replaces what the user already has.
- Keep the canonical vectors byte-identical to the catalog's - a change to those bytes is a contract
  change, not a test update.
- The writer is pinned to its own bytes, and every way they differ from the canonical vectors is named
  by the conformance test below. A change to those bytes is a change to what the app writes at a
  contract boundary: agree it with the owner first.
- The registry lists this product as `P/C`; the contract header does not name it as a producer yet.
  That gap, and rule 5 being unmeetable by a writer of one root and one `lan` path, are proposed to the
  owner in `config-interchange/PROPOSAL-2026-09-23-android-producer.md` and carried meanwhile as a dated
  registry exception.
- The payload is a secret (rule 6): no log line, diagnostic or crash report may carry a password, a PIN
  or the payload; exported files stay in the app's private cache and are replaced on the next export.

## Where it lives here

- Reader: `app_v2/.../data/companion/CompanionConfigParser.kt`, `CompanionConfigDto.kt`,
  `CompanionResourceTokens.kt`; `app_v2/.../domain/usecase/companion/ImportCompanionConfigUseCase.kt`.
- Writer: `app_v2/.../data/companion/CompanionConfigSerializer.kt`,
  `app_v2/.../domain/usecase/companion/ExportCompanionConfigUseCase.kt`,
  `app_v2/.../ui/companionimport/qr/`.
- Vectors: `app_v2/src/test/java/.../golden/CompanionConfigGoldenTest.kt`,
  `app_v2/src/test/java/.../data/companion/CompanionConfigParserTest.kt`.
- Writer conformance: `app_v2/src/test/java/.../domain/usecase/companion/ExportCompanionConfigConformanceTest.kt`.
