# Phase 04 — XML schema extension and bundled-key importer

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Extend `sza_resources.xml` schema with `auth`, `privateKeyAsset`, `keyPassphrase`, `hostKeyFingerprint` attributes; extract the importer from `SettingsViewModel` into a dedicated Manager that handles bundled-key transfer into encrypted credentials storage.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/SzaResourcesImporter.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/xml/sza_resources.xml` | Modified | ≤ 200 |
| `app_v2/src/main/assets/sftp_keys/.gitkeep` | New | ≤ 1 |

> `SettingsViewModel.kt` is currently >650 LOC; `importSzaResources` (~165 LOC) is being extracted. After extraction the VM file shrinks; no backup required.

---

## Steps

### Step 04.1 — Create `assets/sftp_keys/` and update XML header

**Files:** `app_v2/src/main/assets/sftp_keys/.gitkeep`, `app_v2/src/main/res/xml/sza_resources.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> 1. Create directory `app_v2/src/main/assets/sftp_keys/` with an empty `.gitkeep` file. This folder will hold bundled `.pem` keys; production builds of `S0046` may ship one or more keys here, but this phase only sets up the path.
> 2. In the XML header comment block of `sza_resources.xml` document four new optional attributes for SFTP rows: `auth` (`password` default | `key`), `privateKeyAsset` (filename inside `assets/sftp_keys/`), `keyPassphrase` (optional), `hostKeyFingerprint` (any of the formats accepted by `SshFingerprintNormalizer`). Existing entries remain valid.

**Verification:**

- `Glob` — `app_v2/src/main/assets/sftp_keys/.gitkeep` exists.
- `Grep` — `privateKeyAsset` matches in `sza_resources.xml`.
- `Grep` — `hostKeyFingerprint` matches in `sza_resources.xml`.

**Status:** `[ ]` not done

---

### Step 04.2 — Create `SzaResourcesImporter` Manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/SzaResourcesImporter.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create class `SzaResourcesImporter(private val context: Context, private val resourceRepository: ResourceRepository, private val credentialsRepository: NetworkCredentialsRepository)` with one suspend public method `suspend fun import(): ImportResult`. Move the body of `SettingsViewModel.importSzaResources` here (the XmlPullParser loop). Add new attribute parsing for `auth`, `privateKeyAsset`, `keyPassphrase`, `hostKeyFingerprint`. Behavior:
>
> - When `auth="key"` and `privateKeyAsset` is set: open the asset via `context.assets.open("sftp_keys/$privateKeyAsset").bufferedReader().use { it.readText() }`. On `FileNotFoundException` or any read failure, log via `Timber.w` (`"S0046: bundled key '$asset' for resource '$name' unreadable: ${e.message}"`) and skip the resource entirely (do not create credentials, do not insert into DB). This matches strategic §6.2 Resolved.
> - Pass the plaintext key to `NetworkCredentialsEntity.create(plaintextPassword = password ?: "", plaintextPrivateKey = key)` — the entity already encrypts both fields.
> - Normalize `hostKeyFingerprint` via `SshFingerprintNormalizer.canonical(...)`; if non-null result, store it on the `MediaResource` row (Phase 01 added the column). If input cannot be parsed, log `Timber.w` and store null (resource keeps working in legacy permissive mode).
> - Do not log key contents or passphrase.
>
> Follow the manager naming convention from CLAUDE.md (`NounVerbManager` — here `SzaResourcesImporter` is acceptable since the existing skill template uses `Importer`/`Loader` style for one-shot imports, matching `AddResourceFinalizer`).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/SzaResourcesImporter.kt` exists.
- `Grep` — `class SzaResourcesImporter` matches exactly once.
- `Grep` — `suspend fun import\(\)` matches exactly once.
- `Grep` — `privateKeyAsset` matches at least twice (read + use).
- `Grep` — `SshFingerprintNormalizer.canonical` matches exactly once.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[ ]` not done

---

### Step 04.3 — Delegate `SettingsViewModel.importSzaResources` to the new Manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Inject `SzaResourcesImporter` into `SettingsViewModel` (Hilt — add the constructor param; the existing `@HiltViewModel` annotation handles the rest). Replace the body of `importSzaResources(context: Context)` with a coroutine launch that calls `szaResourcesImporter.import()` and surfaces the existing toast on success / Timber.e on failure. Delete the inline XmlPullParser logic that was moved out in Step 04.2. The injected `context` parameter is no longer needed if the importer holds `@ApplicationContext`; drop it if so.

**Verification:**

- `Grep` — `XmlPullParser` returns zero hits in `SettingsViewModel.kt`.
- `Grep` — `szaResourcesImporter.import\(\)` matches exactly once.
- `Grep` — `class SettingsViewModel` line count via `wc -l SettingsViewModel.kt` is below the pre-phase value (extraction shrinks the VM).

**Status:** `[ ]` not done

---

### Step 04.4 — Hilt module registration for `SzaResourcesImporter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`, the Hilt module that already provides `ResourceRepository` (`SingletonComponent` scope — search for `@Provides.*ResourceRepository`)
**Depends on:** Step 04.3

**Prompt for developer:**

> If `SzaResourcesImporter` requires explicit `@Provides` (i.e. cannot be constructor-injected via `@Inject`), add a `@Provides` method in the same module that builds `ResourceRepositoryImpl`. Prefer plain constructor injection: annotate `SzaResourcesImporter` with `@Inject constructor(...)` and `@Singleton` if appropriate — no module change needed. Verify via Hilt-compile that `SettingsViewModel` resolves the new dependency.

**Verification:**

- `Grep` — `@Inject constructor` matches in `SzaResourcesImporter.kt` OR a `@Provides.*SzaResourcesImporter` exists in some Hilt module file.
- Project compiles via `/build` (Hilt errors surface at compile time).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Predefined SFTP resources can now declare key-auth and pinned fingerprint via XML. Per-resource UI editing of fingerprint is still missing — Phase 05 adds the form field.

---

## Rollback Plan

Revert phase commit(s). The asset folder, the new Manager, and the XML attribute additions are isolated; existing predefined resources without new attributes parse unchanged.
