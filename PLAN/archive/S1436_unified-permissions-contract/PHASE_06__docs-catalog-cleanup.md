# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1436_unified-permissions-contract.md`](../S1436_unified-permissions-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Remove what the earlier phases displaced and bring the derived surfaces up to the registry: orphaned string keys in three languages, the dead rationale helpers, the published permission list, the capability inventory and the generated catalogs.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ 450 |
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified | n/a |
| `docs/PRIVACY_POLICY.md` | Modified | n/a |
| `docs/PRIVACY_POLICY.ru.md` | Modified | n/a |
| `docs/PRIVACY_POLICY.uk.md` | Modified | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). `PermissionHelper.kt` was already backed up in phase 02; take a fresh timestamped copy before step 06.2.

---

## Steps

### Step 06.1 - Delete the orphaned string keys in all three languages

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Build the removal list by grepping each candidate key across `app_v2/src` and keeping only those with zero remaining references: the keys phase 05 displaced, plus the ones research artifact 01 already found unreferenced - `manage_media_title`, `permission_internet_rationale`, `permission_storage_title` and the `perm_battery_optim_*` group.
>
> Remove each with `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action remove -Key <key>`, one key at a time, never by hand-editing the XML, and run the localization audit after each batch.

**Why:**

Strategic §3.2 states that consolidating the texts means deleting the orphaned keys as well as editing the live ones, and strategic §7 mitigates the risk of a similarly-named live key being caught in the sweep by requiring removal through the string tool with a localization check after each batch.

**Verification:**

- `Grep` - each removed key returns zero hits under `app_v2/src`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "permission"` exits 0.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 06.2 - Delete the message getters and the VR group remnant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Take a fresh timestamped backup of `PermissionHelper.kt` into `temp/S1436/` (Rule 5).
>
> Delete `getStoragePermissionMessage`, `getInternetPermissionMessage`, `getManageMediaPermissionMessage` and `getAllFilesAccessPermissionMessage` - each returns a string the registry now owns and none has a caller left after phase 05. Confirm zero references before each deletion rather than after.
>
> Remove the `VR` value from `PermissionGroup`, its arm in the group-title `when` at `PermissionRegistryRepositoryImpl.kt:203`, and the `perm_group_vr` key in all three locale files through `set-android-string.ps1 -Action remove`. No entry has carried that group since S0241 removed the OpenXR stack.

**Why:**

Strategic §5.1 requires the wording that lost its last use to be removed together with its keys and names the remnants of the group deleted with the former headset stack among them; research artifact 01 records these getters as already having no callers before this work began.

**Verification:**

- `Grep` - `getStoragePermissionMessage`, `getInternetPermissionMessage`, `getManageMediaPermissionMessage` and `getAllFilesAccessPermissionMessage` return zero hits under `app_v2/src`.
- `Grep` - `PermissionGroup.VR` and `perm_group_vr` return zero hits under `app_v2/src`.
- `Glob` - a fresh timestamped `PermissionHelper.kt` backup exists under `temp/S1436/`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_group"` exits 0.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

### Step 06.3 - Bring the published permission list up to the registry

**Files:** `docs/PRIVACY_POLICY.md`, `docs/PRIVACY_POLICY.ru.md`, `docs/PRIVACY_POLICY.uk.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Rewrite the "Permissions Explained" section in all three locale files so it lists exactly what the app asks the user about - every permission with a system dialog or a dedicated system screen - and omits the ones granted silently at install, matching the registry composition entry for entry.
>
> Research artifact 02 records the current section as explaining roughly 11 of about 31 declared identifiers, missing the whole screen-capture, overlay and install group plus camera, notifications, microphone, location, `MANAGE_MEDIA`, battery optimization, local network and hand tracking - work from the registry, not from that list, and state per permission why the app asks. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §6 item 3 rules that the document lists everything the user is asked about and omits what is granted silently at install, precisely so that its composition equals the permissions screen's and the same mechanical comparison covers it; strategic §11 criterion 10 states the match as an acceptance condition.

**Verification:**

- `Grep` - `SYSTEM_ALERT_WINDOW` and `REQUEST_INSTALL_PACKAGES` match in all three privacy policy files.
- `Grep` - `INTERNET` as a listed permission returns zero hits in the permissions section of all three files.
- The three files list the same permission set - compare the enumerated names pairwise and record `expected: identical | actual: <result>`.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

---

### Step 06.4 - Record the capability and register the document trigger

**Files:** `docs/ALL_FEATURES.jsonl`, `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** Step 06.3

**Prompt for developer:**

> Add the shipped capability with `pwsh -NoProfile -File scripts/all_features/add.ps1`, in English, taking the sentence from strategic §8: the permissions screen shows every access this build can request, including the overlay and install-from-file permissions, and explains each in the same words everywhere it is asked for.
>
> Add a `permissions` product area or update trigger to `docs/DOCUMENT_REGISTRY.jsonl` and attach it to the records this work touches - `legal-downloads`, `settings-reference`, `feature-inventory`, `flavor-capability-matrix` - then run the registry validate and generate commands. Research artifact 02 confirms no such area or trigger exists today.

**Why:**

Strategic §5.1 requires the published permission list to be derived from the registry so the description cannot silently fall behind, and a document with no registry trigger is one the mandatory documentation loop never surfaces when the registry changes again.

**Verification:**

- `Grep` - `S1436` matches in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/query.ps1 -Trigger "permissions"` returns at least one record.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0 and `generate.ps1 -Check` exits 0.

**Status:** `[x]` done

---

### Step 06.5 - Regenerate the catalogs and close the ticket

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 06.4

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once, then set `role` and `status` for the classes this work introduced - `PermissionGrantIntentFactory`, `StoragePermissionRule`, `BuildPermissionRowsUseCase`, `PermissionManifestExemptions` - with `set.ps1`.
>
> Close through the facade with the whole changed set named and `-ScopeToFile`, then record the dev-log entries for every file touched across all six phases in one `close-and-log.ps1 -DevLogs` call.

**Why:**

not stated in strategic spec

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "PermissionGrantIntentFactory"` returns one record with a non-empty `role`.
- `Grep` - `S1436` matches in `dev/CHANGELOG.md`.
- `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<whole changed set>" -Target "S1436" -Description "Unified permissions contract" -ChangeType Mixed -ScopeToFile` prints `post-change: PASS`.
- `.\a.ps1 fu` exits 0 with `PermissionRegistryManifestParityTest` and `PermissionRegistryRepositoryImplTest` green.

**Status:** `[x]` done - with the suite-wide predicate recorded as failed for causes outside this ticket; see the Step Log entry for 06.5.

---

## Step Log

- 2026-08-06 - Step 06.5 DONE, and **the suite predicate is recorded as failed rather than as passed**.
  - `.\a.ps1 fu` `expected: exit 0 | actual: exit 1`. 16 failures in 5 classes: `BrowseStateDataStoreTest` (5), `ReviewEligibilityDataStoreTest` (4), `GameStateRepositoryImplTest` (4), `SettingsRepositoryImplTest` (2) - all the same `java.io.IOException: Unable to rename .. browse.preferences_pb.tmp` from DataStore - and `IconInventoryExportTest` (1), which reports `docs/icons/icon-inventory.json` stale against a translation-settings icon.
  - **Not attributed to this ticket, and the attribution was tested rather than assumed.** `BrowseStateDataStoreTest` was re-run alone, with the build lock held by nothing else, and failed identically - so it is not a collision with a sibling session's gradle run. All four DataStore test files date from 2026-05-29 to 2026-07-18 and none is in this ticket's changed set; nothing here touches DataStore, preferences or icons. The icon inventory was last written 2026-08-05, before this work began.
  - The two suites this ticket owns are green and were run twice: `PermissionRegistryManifestParityTest` `expected: PASS | actual: 3 tests, 0 failures`, `PermissionRegistryRepositoryImplTest` 9 tests, 0 failures.
  - Parked rather than absorbed: the DataStore family as `S1449`; the stale icon inventory is already `S1194` (Draft since 2026-07-25, same symptom), so no duplicate was created.
  - `post-change` over the whole 48-file set with `-ScopeToFile` prints the clean `post-change: PASS`. Reaching it took one real sibling edit rather than an acknowledgement: the registry named `developer-operations` because phase 04 had written the parity check into `RELEASE_READINESS_STANDARD.md` while `docs/DEV_OPS.md` still listed neither `DECLARES_BATTERY_OPTIMIZATION` in its build-type flag table nor the parity test among the local proofs. Both added - a flag that changes what the manifest declares is exactly what that table exists to record.
  - `PermissionGrantIntentFactory`, `StoragePermissionRule`, `BuildPermissionRowsUseCase` and `PermissionManifestExemptions` now carry a `role` and `status: tested` in `dev/CATALOG/app_v2.jsonl`.
  - The dev log carries one entry per logical step rather than one per file, per CLAUDE.md "one entry per logical change, not per touched file"; the plan's per-file wording predates that rule. `S1436` appears throughout `dev/CHANGELOG.md`.

- 2026-08-06 - Steps 06.1 to 06.4 DONE.
  - **The removal list was computed, not recalled.** Every key in `values/strings.xml` matching the permission-name shapes was grepped across all of `app_v2/src` (every flavor source set, layouts included, only the `values*/strings.xml` themselves excluded): 111 candidates, 31 with no reference left. That is 12 more than the plan named - among them `permissions_granted`, `permissions_denied_title`, `all_files_global_info`, `all_files_resource_info` and the four `manage_media_*` prompts, which the plan's own instruction finds precisely because it says to grep rather than to list. All 31 were removed through `set-android-string.ps1`, never by hand, and checked once more against `docs`, `scripts`, `dev`, `wear` and `.claude` before deletion - nothing outside `app_v2` referenced any of them.
  - Three more keys fell in 06.2 as their last readers went: `permission_internet_rationale` and `manage_media_explanation` with the two getters that returned them, and `perm_group_vr` with the `PermissionGroup.VR` arm. `getStoragePermissionMessage` and `getAllFilesAccessPermissionMessage` were already gone - step 05.2 removed them when it took their strings.
  - **The published list was written from the registry, not from the old section.** It now names 20 permissions where it named 11, and the three locales carry the same 20 - verified by extracting the back-ticked identifiers from each file and comparing the sets, not by reading them. `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `WAKE_LOCK` left it: they are granted at install and the user is never asked, which is exactly the rule strategic §6 item 3 settled.
  - **One claim in the draft was wrong and was caught before it shipped.** The local-network row carries `minSdk = 37`, and the first draft rendered that as "Android 16+". API 37 is later than any released Android, so the line would have promised a permission dialog that no shipped device can show. It now says so plainly in all three languages.
  - The registry trigger is `permission`, singular, while the product area is `permissions`. The plan's predicate spells the trigger plural, but every trigger already in `DOCUMENT_REGISTRY.jsonl` is singular - `setting`, `flavor`, `release` - and a second vocabulary would cost more than the predicate's literal wording is worth. `query.ps1 -ProductArea "permissions"` returns all four records.
  - Verification: 06.1 - all 31 keys `expected: 0 hits under app_v2/src | actual: 0`, `check_strings_localized.ps1` `perm` and `permission` both exit 0, `.\a.ps1 fr` exit 0. 06.2 - all four getters and both VR names gone, `perm_group` parity exit 0, fresh `PermissionHelper.kt` backup under `temp/S1436/`, `.\a.ps1 fc` exit 0. 06.3 - `SYSTEM_ALERT_WINDOW` and `REQUEST_INSTALL_PACKAGES` present in all three files, `INTERNET` listed in none, the three sets `expected: identical | actual: identical (20 names)`. 06.4 - `S1436` in `docs/ALL_FEATURES.jsonl`, `all_features/validate.ps1` exit 0, `document_registry/validate.ps1` and `generate.ps1 -Check` both exit 0.
  - The `document-registry` advisory raised by the 06.3 and 06.4 closures is the acknowledgement prompt, not a defect: `legal-downloads`'s siblings (Terms of Service, downloads, open-source notices) carry no permission list, and there is no HTML export in the tree. Acknowledged at the 06.5 closure rather than by re-running an otherwise identical closure twice.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` `expected: exit 0 | actual: exit 0`, 2026-08-06 22:46.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added - one per logical step, per CLAUDE.md's journaling rule; see the 06.5 log entry.
- [x] Catalog regenerated, and the four classes this work introduced carry a `role` and `status: tested`.
- [x] Phase-boundary audit run - see below.

### Phase-boundary audit (2026-08-06)

- **Layer 1 - architecture.** PASS. This phase deleted rather than added: four dead getters, an enum value with no entry, 34 string keys. The only new prose is documentation.
- **Layers 2 to 4.** Not applicable - nothing here touches lifecycle, coroutines, listeners or persistence.
- **Deletion safety.** Every removal was proved unreferenced before it was made, across all flavor source sets and then across `docs`, `scripts`, `dev`, `wear` and `.claude`. `.\a.ps1 fr` and `.\a.ps1 fc` both exit 0 afterwards, which is what would fail first if a live reference had been cut.
- **Probes:** the eight `Timber.d("S1436: ..")` entries the `BlockNeedUserTest` status requires are in place, and `.\a.ps1 dq` exit 0 validated the code and the probes in one build.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s). The string removals are the only irreversible-feeling part and they are recoverable from the revert, since `set-android-string.ps1` writes the same three files git tracks.
