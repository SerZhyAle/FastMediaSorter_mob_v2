# Phase 06 — Docs, trilingual strings, catalog cleanup

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04, Phase 05
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Add trilingual strings for all new UI surfaces, document the feature in `docs/FEATURES*.md`, regenerate the file catalog.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Phase 05 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 4000 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 4000 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 4000 |
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |

---

## Steps

### Step 06.1 — Trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following string IDs to all three files. Use `..` for ellipsis (not `...`); always use `ё`/`Ё` in Russian.
>
> - `sftp_host_key_fingerprint_hint` — EN: `Expected host key fingerprint (SHA256, optional)` · RU: `Ожидаемый отпечаток host-key (SHA256, опционально)` · UK: `Очікуваний відбиток host-key (SHA256, опційно)`
> - `sftp_host_key_fingerprint_invalid` — EN: `Cannot parse fingerprint. Use SHA256:base64, hex with colons, or raw base64.` · RU: `Не удалось разобрать отпечаток. Поддерживаются SHA256:base64, hex с двоеточиями, чистый base64.` · UK: `Не вдалося розібрати відбиток. Підтримуються SHA256:base64, hex з двокрапками, чистий base64.`
> - `sftp_host_key_mismatch_title` — EN: `Host key mismatch` · RU: `Отпечаток host-key не совпадает` · UK: `Відбиток host-key не збігається`
> - `sftp_host_key_mismatch_body_format` — `%1$s` = expected, `%2$s` = actual. EN: `Expected fingerprint:\n%1$s\n\nServer offered:\n%2$s\n\nConnection refused — possible MITM attack or legitimate key change. Update the resource manually if you trust this server.` · RU: `Ожидался отпечаток:\n%1$s\n\nСервер прислал:\n%2$s\n\nСоединение отклонено — возможна подмена сервера или легитимная смена ключа. Если вы доверяете этому серверу, обновите ресурс вручную.` · UK: `Очікувався відбиток:\n%1$s\n\nСервер надіслав:\n%2$s\n\nЗ'єднання відхилено — можлива підміна сервера або легітимна заміна ключа. Якщо ви довіряєте цьому серверу, оновіть ресурс вручну.`

**Verification:**

- `Grep` — `sftp_host_key_fingerprint_hint` matches exactly once in each of the three `strings.xml` files.
- `Grep` — `sftp_host_key_mismatch_title` matches exactly once in each of the three files.
- `Grep` — `sftp_host_key_mismatch_body_format` matches exactly once in each of the three files.

**Status:** `[ ]` not done

---

### Step 06.2 — Update `docs/FEATURES*.md` trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> In the section about network resources, append one bullet (mirror the wording across all three files). Use `..` not `...`; use `ё`/`Ё` in RU.
>
> - EN: `SFTP resources support SSH private-key authentication with optional passphrase, and host-key fingerprint pinning to detect server impersonation. Key auth is also available for predefined resources shipped in the bundled XML config.`
> - RU: `SFTP-ресурсы поддерживают авторизацию по приватному SSH-ключу с опциональной парольной фразой и проверку отпечатка host-key (защита от подмены сервера). Ключ-авторизация доступна в том числе для предзагруженных ресурсов из встроенного XML.`
> - UK: `SFTP-ресурси підтримують авторизацію за приватним SSH-ключем з опційним парольним виразом та перевірку відбитка host-key (захист від підміни сервера). Ключ-авторизація доступна також для попередньо завантажених ресурсів зі вбудованого XML.`

**Verification:**

- `Grep` — `host-key` matches in `docs/FEATURES.md`.
- `Grep` — `host-key` matches in `docs/FEATURES_RU.md`.
- `Grep` — `host-key` matches in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 06.3 — Regenerate file catalog and run dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> 1. Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
> 2. Run `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`.
> 3. Set `role`/`status` for new classes via `dev/CATALOG/scripts/set.ps1` per `dev/CATALOG/README.md` — at minimum: `SshFingerprintNormalizer`, `PinnedHostKeyVerifier`, `SzaResourcesImporter`.
> 4. Add a dev-log entry for every file modified across all phases via `.\scripts\add_to_dev_log.ps1` (one invocation per file). Use `pwsh 7` (`"/c/Program Files/PowerShell/7/pwsh.exe"`) for the catalog scripts.

**Verification:**

- `Grep` — `SshFingerprintNormalizer` matches in `dev/CATALOG/app_v2.md`.
- `Grep` — `PinnedHostKeyVerifier` matches in `dev/CATALOG/app_v2.md`.
- `Grep` — `SzaResourcesImporter` matches in `dev/CATALOG/app_v2.md`.
- `dev/CHANGELOG.md` newest section contains entries for every file in §Files Touched of phases 01–06.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `pwsh -File scripts/spec_catalog/update.ps1 -Id S0046 -Status Implemented` executed.

---

## Handoff Notes to Next Phase

Final phase — see [`INDEX.md`](INDEX.md) Completion Gate. Run `/spec-check S0046` to advance the journal status to `Verified`.

---

## Rollback Plan

Revert phase commit(s); strings and docs are additive. The catalog is regenerated from source state, so revert is automatic on next scan.
