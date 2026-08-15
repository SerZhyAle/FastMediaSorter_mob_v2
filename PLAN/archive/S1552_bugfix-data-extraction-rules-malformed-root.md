# S1552 - data_extraction_rules.xml has the wrong root element, so Android 12+ backs up everything

**Status:** Archived

## Goal

Файл правил бэкапа для Android 12+ написан с неправильным корневым элементом, поэтому не описывает ни одного правила. Все запреты, аккуратно прописанные для старых версий Android, на новых устройствах не действуют: в облачный бэкап уходят и настройки, и база сетевых паролей.

Из-за этого при переустановке система возвращает старый слепок настроек поверх свежей установки - владелец видит это как «настройки сбились». Отдельная проблема - зашифрованные учётные данные покидают устройство, хотя ключ шифрования привязан к железу и восстановить их всё равно невозможно.

Задача - привести правила для Android 12+ в соответствие с уже записанным намерением проекта и закрыть дыру гейтом, чтобы она не открылась снова незаметно.

<!-- auto-approved by /spec-all - 2026-08-09 -->

---

## 1. Symptom and evidence

Owner report: settings lost after reinstalling the app. Device is samsung SM-S731B, Android 16 / API 36, so the Android 12+ backup path applies.

`app_v2/src/main/res/xml/data_extraction_rules.xml` is:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources />
```

The root element the platform reads is `<data-extraction-rules>`, holding `<cloud-backup>` and `<device-transfer>`. `<resources />` is the generic value-resource root, so the file declares no rule of any kind - and being a syntactically valid resource file, neither the build nor lint objects.

`app_v2/src/main/AndroidManifest.xml:217` sets `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"` and `android:fullBackupContent="@xml/backup_rules"`. From API 31 `dataExtractionRules` supersedes `fullBackupContent` for both cloud backup and device-to-device transfer, so on the reporting device only the empty file is consulted.

`app_v2/src/main/res/xml/backup_rules.xml` - the pre-31 file, and the only place the project's intent is written down - excludes:

- `network_credentials.db` (+ `-shm`, `-wal`), because the encrypted credentials are tied to the hardware Keystore and cannot be restored to another device
- `datastore/`, because it holds device-specific settings and the encrypted default password

None of those exclusions are in force on API 31+.

## 2. Consequences

- **Settings roll back on reinstall.** `files/datastore/settings.preferences_pb` is eligible for cloud backup and D2D transfer, so a reinstall on API 31+ restores the last backed-up snapshot over the fresh install. Independent of S1551, which explains resets *during use*; this one explains the reset *at reinstall*.
- **Encrypted credentials leave the device.** `network_credentials.db` is backed up on API 31+ against the explicit intent in `backup_rules.xml`. The Keystore key does not travel, so restored rows are undecryptable - and credential material still sits in a cloud backup.
- **Undecryptable default password.** The DataStore holds `defaultPassword` encrypted against the same Keystore, with the same restore problem.
- Possibly related, unproven: `logs/fastmediasorter_20260809_032728.log:362` reports 16 orphaned credentials referenced by no resource - consistent with a partial restore, not established.

## 3. Decisions

### 3.1 Mirror the existing intent into both sections

`<cloud-backup>` and `<device-transfer>` both get the exclusion list already stated in `backup_rules.xml`. The stated reason for excluding - a Keystore key that cannot be restored to a different device - holds identically for a D2D transfer, since hardware-backed keys do not migrate. Writing a narrower `<device-transfer>` would change the project's recorded intent, which is an owner decision, not a bugfix.

### 3.2 Out of scope

- `disableIfNoEncryptionCapabilities` on `<cloud-backup>`: moot once the sensitive paths are excluded outright.
- Widening backup coverage so settings survive a phone change - see §6.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1551

---

## Phase 1 - Correct the Android 12+ extraction rules

**Objective:** `data_extraction_rules.xml` declares real rules under the correct root, matching `backup_rules.xml`.

**Files Touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/xml/data_extraction_rules.xml` | Modified | <= 40 |

### Step 1.1 - Rewrite data_extraction_rules.xml with the correct root

**Files:** `app_v2/src/main/res/xml/data_extraction_rules.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the `<resources />` body with a `<data-extraction-rules>` root holding a `<cloud-backup>` and a `<device-transfer>` section. Give each section the same three `<exclude domain="database" path="network_credentials.db|-shm|-wal" />` entries and the same `<exclude domain="file" path="datastore/" />` entry that `backup_rules.xml` carries. Add a header comment stating that this file governs API 31+ and must stay in step with `backup_rules.xml`, and naming S1552 as the reason the file previously declared nothing.

**Why:**

The wrong root element makes the file declare no rule, so on API 31+ - where this file supersedes `backup_rules.xml` - the settings DataStore and the Keystore-bound credentials database are backed up and restored against the project's recorded intent, which is what rolls the owner's settings back on reinstall.

**Verification:**

- `Grep` - `<data-extraction-rules>` matches exactly once in that file.
- `Grep` - `<resources` returns zero hits in that file.
- `Grep` - `<cloud-backup>` and `<device-transfer>` each match exactly once.
- `Grep` - `network_credentials.db"` matches exactly twice (once per section).
- `Grep` - `path="datastore/"` matches exactly twice (once per section).
- `.\a.ps1 fr` (resources + manifest) exits 0.

**Status:** `[x]` done

---

## Phase 2 - Gate the two files against drifting apart

**Objective:** a mechanical gate fails when the two backup files disagree, or when either uses the wrong root element.

**Files Touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-backup-rules-consistent.ps1` | New | <= 150 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | <= 5 |

### Step 2.1 - Write the gate script

**Files:** `scripts/quality/assert-backup-rules-consistent.ps1`
**Depends on:** Step 1.1

**Prompt for developer:**

> Write a gate that reads both `app_v2/src/main/res/xml/backup_rules.xml` and `app_v2/src/main/res/xml/data_extraction_rules.xml`. Fail when `backup_rules.xml` lacks a `<full-backup-content>` root, when `data_extraction_rules.xml` lacks a `<data-extraction-rules>` root, or when the set of `domain`+`path` pairs excluded by `<full-backup-content>` is not a subset of the pairs excluded by each of `<cloud-backup>` and `<device-transfer>`. Follow the house script shape: `#requires -Version 7.0`, a `.SYNOPSIS`/`.DESCRIPTION`/`.NOTES` header naming S1552 and listing the exit codes, `[CmdletBinding()] param([switch]$Gate, [switch]$Quiet)`, `Set-StrictMode -Version Latest`, repo root resolved from `$PSScriptRoot`. Exit 0 clean, 1 on disagreement, 2 when either file is missing or unparseable. Per CLAUDE.md Rule 7, precede any non-1 exit with `Write-Error <msg> -ErrorAction Continue`.

**Why:**

The defect was invisible for the file's whole life because a wrong root element is still a valid resource file - neither the build nor lint objects - so only a gate that reads both files and compares them can keep the pre-31 and post-31 rules from silently diverging again.

**Verification:**

- `Glob` - `scripts/quality/assert-backup-rules-consistent.ps1` exists.
- Run it; exit code is 0 against the tree as fixed by Phase 1.
- `Grep` - `exit 2` present in the script.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Quiet` exits 0.

**Status:** `[x]` done

### Step 2.2 - Register the gate in the fast-gates batch

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 2.1

**Prompt for developer:**

> Add `assert-backup-rules-consistent.ps1` to the gate map in `assert-fast-gates.ps1` with `@('-Quiet')`, following the entries already there, and name it in the header comment list alongside its ticket id.

**Why:**

A gate nobody runs is prose; the fast-gates batch is what `post-change.ps1` and `.\a.ps1 fg` actually execute, so registration is what turns this check into an enforced rule.

**Verification:**

- `Grep` - `assert-backup-rules-consistent.ps1` matches in `scripts/quality/assert-fast-gates.ps1`.
- `.\a.ps1 fg` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every step above is `[x] done`.
- [x] `.\a.ps1 fr` exits 0 (resources + manifest are the only build surface touched; no Kotlin changed).
- [x] `.\a.ps1 fg` reports `assert-backup-rules-consistent.ps1 PASS` and no gate newly failing because of this ticket. The batch exit is currently 1 on `assert-memory-budget` alone - MEMORY.md over its ceiling, pre-existing and tracked by S1542, untouched here.
- [x] Dev log entry added for every file in "Files Touched" (one batched entry, `post-change.ps1 -Files` set of 3).

---

## 6. Open items for the owner

- Excluding `datastore/` from `<device-transfer>` means a user moving to a new phone loses every setting. That is the intent recorded in `backup_rules.xml` and this ticket preserves it, but a narrower rule - excluding only the encrypted password while transferring the rest of the settings - would keep the security property and drop the UX cost. Needs an owner ruling and its own ticket.
- Whether `allowBackup` should stay `true` at all, given that everything sensitive is now excluded and what remains is a settings file the app can regenerate.

## 7. Related

- S1551 - settings overwritten by constructor-default `AppSettings` on SettingsActivity open. Same symptom, different mechanism, no dependency in either direction.
- S1542 - agent memory index over budget. Unrelated; the only reason `.\a.ps1 fg` exits non-zero on this tree.

---

## Last Audit

**Date:** 2026-08-09 - `/spec-all`, Simple path.

### Proven

- `app_v2/src/main/res/xml/data_extraction_rules.xml` carries the `<data-extraction-rules>` root, one `<cloud-backup>` and one `<device-transfer>` section, each repeating all four exclusions from `backup_rules.xml`. Grep predicates from Step 1.1 all match; `<resources` returns zero hits.
- `.\a.ps1 fr` - expected exit 0 | actual exit 0. The resource pipeline packages the file.
- The merged manifest, read at `app_v2/build/intermediates/packaged_manifests/standardDebug/processStandardDebugManifestForPackage/AndroidManifest.xml`, still carries `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"` and `android:fullBackupContent="@xml/backup_rules"`. No merged library overrode the attributes, so the rules the app ships are the ones in this repo.
- `scripts/quality/assert-backup-rules-consistent.ps1` exits 0 on the fixed tree and exits 1 with a named violation on three seeded defect shapes: the original wrong root, a partially populated `<cloud-backup>` with an empty `<device-transfer>`, and a missing `<device-transfer>` section.
- The gate is registered in `assert-fast-gates.ps1` and reported `PASS (238 ms)` inside the batch.
- `assert-exit-contract.ps1` - expected 0 | actual 0 unreachable exit sites.
- `post-change.ps1 -ScopeToFile` over the three changed files - `PASS WITH ADVISORIES (1)`; the sole advisory, a stale `docs/SCRIPT_CHEATSHEET.md`, was caused by the new script and was regenerated, after which `assert-script-cheatsheet-sync.ps1` reports `in sync`.

### Defect found and fixed during implementation

The gate's first draft returned its `HashSet` with a bare `return $keys`. PowerShell unrolls an enumerable on output, so an empty set arrived at the caller as `$null` and a populated one as a plain `string[]`. The clean-tree run had been passing on `string[]`'s own `.Contains`, and the seeded-defect run crashed on a null reference while still exiting 1 - a gate that looked like it worked in both directions and did neither. Fixed with `return ,$keys`, then re-proven against all four cases. Worth recording because it is the same failure class as the ticket itself: a check that is present, accepted, and doing nothing.

### Not proven

- **Runtime behaviour.** Nothing here demonstrates that Android actually honours the new rules; the evidence is that the file matches the platform contract and reaches the packaged app. Confirming the exclusions take effect needs a device or emulator with a backup transport - `adb shell bmgr backupnow <pkg>`, then a reinstall, then checking that settings come back at defaults rather than at a restored snapshot, and that no credentials row returns. `device-ready.ps1` reported `no-device` on this run, so this was deferred rather than skipped.

### Status rationale

Set to `Implemented`, not `Verified`. Every criterion this spec wrote down is met with fresh, cited evidence, but the claim the ticket exists to make - that Android 12+ now respects the exclusions - is a runtime claim, and the whole defect class here is configuration that reads correctly and does nothing. Calling it `Verified` on static evidence alone would repeat the mistake being fixed.
