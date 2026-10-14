<sub class="doc-stamp">26.10.07 05:04</sub>

# Pointer - `FMSCFG`

| | |
| --- | --- |
| **Id** | `FMSCFG` |
| **Version** | 2.4, active; wire carrier `schemaVersion`, this product reads 1-2. Owner: FMS Companion |
| **Home** | `config-interchange/README.md` and `CONFIG_FORMAT.md` in the shared contracts catalog |
| **Role here** | producer and consumer - the `.fmscfg` importer, and the writer of `schemaVersion` 2 files and `FMSCFG1:` QR payloads when a user shares an SFTP resource |

## What this repository must do to stay conformant

- Refuse a `schemaVersion` above the supported one before reading any field, with an "update the app"
  message and no partial import.
- Ignore an unknown `accessPaths[].kind`; take every missing optional root field at the default the
  contract's table gives, never at a local choice.
- A `rendezvousTunnel` path still carries a valid `host` and `port` and is written last (2.4 rule 7);
  an `accessNote`, when written, is in the exporter's UI language with an English copy (2.3).
- Import merges by path and never replaces what the user already has.
- Keep the canonical vectors byte-identical to the catalog's - a change to those bytes is a contract
  change, not a test update.
- The writer is pinned to its own bytes, and every way they differ from the canonical vectors is named
  by the conformance test below. A change to those bytes is a change to what the app writes at a
  contract boundary: agree it with the owner first.
- The writer is a named producer (2.2 header). Rule 5 binds it to its own pinned bytes plus the
  itemized difference from the canonical vectors; the canonical vectors are the importer's fixture.
- An empty or whitespace-only `password` means "not carried": ask the user before anything is created,
  and never create a resource that cannot log in. An export that leaves the password out writes
  `"password":""` and never omits the key (2.2 amendment, item C).
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
