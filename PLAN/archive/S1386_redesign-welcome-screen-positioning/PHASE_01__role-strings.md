# Phase 01 - Role strings

**Strategic spec:** [`../S1386_redesign-welcome-screen-positioning.md`](../S1386_redesign-welcome-screen-positioning.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-04
**Completed:** 2026-08-04

---

## Objective

Add the trilingual string set the role showcase needs: four role titles plus the capability-dependent wording variants of their detail lines. No Kotlin change yet, so the new keys are unreferenced at the end of this phase.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `docs/COMMUNICATION_POLICY.md` §2 and §6 read before writing any user-visible wording.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_setup.xml` | Modified | ≤ 260 |
| `app_v2/src/main/res/values-ru/strings_setup.xml` | Modified | ≤ 260 |
| `app_v2/src/main/res/values-uk/strings_setup.xml` | Modified | ≤ 260 |

> No `res/layout*` file is touched in this phase, so CLAUDE.md Rule 11 landscape parity does not apply.

---

## Steps

### Step 01.1 - Add the four role titles

**Files:** `app_v2/src/main/res/values/strings_setup.xml`, `app_v2/src/main/res/values-ru/strings_setup.xml`, `app_v2/src/main/res/values-uk/strings_setup.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add five title keys with one `scripts/utils/set-android-string.ps1 -Action add -File strings_setup.xml` call each, passing `-En -Ru -Uk` in the same call so the three locales stay in parity. Four are the roles themselves and the fifth is the images-only wording of the player role:
>
> - `welcome_role_file_manager` - En `File Manager`, Ru `Файловый менеджер`, Uk `Файловий менеджер`
> - `welcome_role_player` - En `Media Player`, Ru `Проигрыватель медиа`, Uk `Програвач медіа`
> - `welcome_role_player_images_only` - En `Photo Viewer`, Ru `Просмотр фото`, Uk `Перегляд фото`
> - `welcome_role_sources` - En `Any Source`, Ru `Любые источники`, Uk `Будь-які джерела`
> - `welcome_role_sorting` - En `One-Tap Sorting`, Ru `Сортировка в одно касание`, Uk `Сортування одним дотиком`
>
> Check each value against `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist) before writing it.

**Why:**

Strategic §5.1 fixes the showcase composition as exactly these four roles, and §2 goals 1 and 2 are met only when the words "file manager" and "player" appear on the first page, so the titles are the literal delivery of both goals.

**Verification:**

- `Grep` - each of the five keys matches exactly once in `app_v2/src/main/res/values/strings_setup.xml`.
- `Grep` - each of the five keys matches exactly once in `values-ru/strings_setup.xml` and once in `values-uk/strings_setup.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.2 - Add the capability-dependent detail lines

**Files:** `app_v2/src/main/res/values/strings_setup.xml`, `app_v2/src/main/res/values-ru/strings_setup.xml`, `app_v2/src/main/res/values-uk/strings_setup.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add ten detail keys the same way, one `-Action add` call per key with `-En -Ru -Uk` together. Each detail line carries the concrete protocol, service or media-type names that prove the role above it:
>
> - `welcome_role_file_manager_detail` - En `Browse, copy, move and delete files`, Ru `Просмотр, копирование, перемещение и удаление файлов`, Uk `Перегляд, копіювання, переміщення та видалення файлів`
> - `welcome_role_player_detail` - En `Photos, video, music, GIFs, documents and text`, Ru `Фото, видео, музыка, GIF, документы и текст`, Uk `Фото, відео, музика, GIF, документи та текст`
> - `welcome_role_player_detail_no_documents` - En `Photos, video, music and GIFs`, Ru `Фото, видео, музыка и GIF`, Uk `Фото, відео, музика та GIF`
> - `welcome_role_player_detail_no_audio` - En `Photos, video and GIFs`, Ru `Фото, видео и GIF`, Uk `Фото, відео та GIF`
> - `welcome_role_player_detail_images_only` - En `Photos and GIFs`, Ru `Фото и GIF`, Uk `Фото та GIF`
> - `welcome_role_sources_detail` - En `Device, SMB, FTP, SFTP, Google Drive, OneDrive, Dropbox`, Ru `Устройство, SMB, FTP, SFTP, Google Drive, OneDrive, Dropbox`, Uk `Пристрій, SMB, FTP, SFTP, Google Drive, OneDrive, Dropbox`
> - `welcome_role_sources_detail_network_only` - En `Device storage and SMB, FTP, SFTP shares`, Ru `Память устройства и папки SMB, FTP, SFTP`, Uk `Пам'ять пристрою та теки SMB, FTP, SFTP`
> - `welcome_role_sources_detail_cloud_only` - En `Device storage, Google Drive, OneDrive, Dropbox`, Ru `Память устройства, Google Drive, OneDrive, Dropbox`, Uk `Пам'ять пристрою, Google Drive, OneDrive, Dropbox`
> - `welcome_role_sources_detail_local_only` - En `Internal storage and SD card`, Ru `Внутренняя память и SD-карта`, Uk `Внутрішня пам'ять та SD-картка`
> - `welcome_role_sorting_detail` - En `Send a file to its folder with a single tap`, Ru `Отправьте файл в нужную папку одним касанием`, Uk `Надішліть файл до потрібної теки одним дотиком`
>
> Keep every value at or under the length of the longest existing detail string in this file, because the tile detail view is capped at three lines and ellipsizes past that. Check each value against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §6.2 resolves that the brand and protocol names stay on the first page and live in the tile's second line, and §3.2 forbids the showcase from promising a source or media type the build cannot open, which is why every role that varies by build gets a truthful wording variant rather than one fixed sentence.

**Verification:**

- `Grep` - each of the ten keys matches exactly once in `app_v2/src/main/res/values/strings_setup.xml`.
- `Grep` - each of the ten keys matches exactly once in `values-ru/strings_setup.xml` and once in `values-uk/strings_setup.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.3 - Audit the new key family

**Files:** `app_v2/src/main/res/values/strings_setup.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_role_"` and fix any parity or escaping finding it reports before closing the phase.

**Why:**

Strategic §3.2 makes EN, RU and UK mandatory for every new string, and the audit script is the mechanical proof that all fifteen keys exist in all three locales.

**Verification:**

- `check_strings_localized.ps1 -KeyPrefix "welcome_role_"` exits 0.

**Status:** `[x]` done

---

## Step Log

- 2026-08-04 16:52 - Step 01.1 done. Five title keys added via `set-android-string.ps1 -Action add`; grep = 1 per key in `values`, `values-ru`, `values-uk`.
- 2026-08-04 16:53 - Step 01.2 done. Ten detail keys added the same way; grep = 1 per key in all three locales.
- 2026-08-04 16:53 - Step 01.3 done. `check_strings_localized.ps1 -KeyPrefix "welcome_role_"` - 15 keys present in en/ru/uk, exit 0.
- 2026-08-04 16:54 - Phase gate: `.\a.ps1 fr` exit 0 (resources/manifest); `post-change.ps1 -ChangeType Xml -ScopeToFile` printed `post-change: PASS`, exit 0.
- 2026-08-04 16:54 - Phase-boundary audit: Layer 1 only, no source code touched. No findings - the phase adds unreferenced string resources with verified EN/RU/UK parity.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fr` exit 0 (resource-only phase; the compile rung of the validation ladder is the resource check).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Fifteen `welcome_role_*` keys exist in all three locales and are referenced by nothing. Phase 02 consumes them and orphans the `welcome_feature_*` keys they replace.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed until Phase 02 references the keys.
