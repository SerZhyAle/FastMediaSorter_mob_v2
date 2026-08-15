# Phase 04 - Manifest parity gate

**Strategic spec:** [`../S1436_unified-permissions-contract.md`](../S1436_unified-permissions-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Make a divergence between the registry and the merged manifest fail a check instead of surviving to review: a Robolectric test compares the two sets in both directions for the variant it runs under, with every install-time permission exempted explicitly and by name.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionManifestExemptions.kt` | New | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryManifestParityTest.kt` | New | ≤ 200 |
| `docs/RELEASE_READINESS_STANDARD.md` | Modified | n/a |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). No file in this phase is over 500 LOC.

---

## Steps

### Step 04.1 - Declare the exemptions with a mandatory reason each

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionManifestExemptions.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `PermissionManifestExemptions` holding a `Map<String, String>` from a manifest permission name to the reason it carries no registry row. Model the value as a non-empty string so an entry cannot be added without one - reject a blank reason in an `init` block rather than trusting the author.
>
> Populate it from research artifact 02's "Install-time, never shown" list: the foreground-service type declarations, `WAKE_LOCK`, `VIBRATE`, `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `RECEIVE_BOOT_COMPLETED` and `com.oculus.permission.HAND_TRACKING`.

**Two exemptions this step was told to add are no longer exemptions.** The prompt above originally also named `ACCESS_COARSE_LOCATION` ("the fine-location row covers both") and `WRITE_EXTERNAL_STORAGE` ("the read entry's SDK window covers the pair on API 23-28"). Both reasons were false in the same way, and the phase-02 and phase-03 boundary audits found the failure each one would have preserved: nothing ever requested either permission, because a grant surface can only request what the registry lists. Each is now a row of its own - steps 03.7 and 03.8 - so exempting them here would excuse a row that exists. This is the risk strategic §7 names: an exemption is a claim, and a plausible-sounding one is how a parity gate stops meaning anything.

**A third case the exemption list cannot express, and a fourth the map's shape does not fit.**

- `RECORD_AUDIO` is exempt only where the build cannot use it - `lite` and `photos`, where `SUPPORT_MIC_RECORDING` is false while `src/main` still declares the permission. That is a manifest defect, parked as **S1442**, not a permanent exemption; the map keys on a permission name and cannot say "only in two flavors", so the reason names the ticket and the condition, and S1442 deletes the entry when it removes the declaration.
- `BIND_NOTIFICATION_LISTENER_SERVICE` is the **reverse** case: S0429's row has no `uses-permission` at all, because the permission is held by a `<service>` declaration in `src/launcherEnabled`, so it never appears in `requestedPermissions`. The single map cannot express it, so this step adds a second, explicitly named set for rows that are legitimately undeclared, on the same mandatory-reason rule.

**Why:**

Strategic §7 records the risk that a parity check turns out too strict, fails a build on a legitimate exception and gets switched off, and mitigates it by requiring an explicit list of declared exceptions each carrying a mandatory reason rather than a silent relaxation; strategic §5.1 states that permissions granted silently at install get no row because they need no user decision.

**Three cases the phase-03 boundary audit already found, which this step must decide rather than discover.** Each is a permission declared in a flavor whose registry row does not apply there, so the parity test of step 04.2 fails on it until it is either exempted with a reason or removed from the manifest. The canon's "declare only the permissions the runtime actually uses" points at the manifest for the first two.

- `RECORD_AUDIO` in `lite` and `photos` - declared in `src/main` and again in `src/screenCapture`, neither flavor removes it, and step 03.4 moved the row's gate to `SUPPORT_MIC_RECORDING`, which is false in both.
- `POST_NOTIFICATIONS` in `lite` and `photos` - declared unconditionally, and `ENABLE_PERSISTENT_AUDIO_PLAYBACK` is false in both, so the row exists in onboarding (via `shownInWelcomeDespiteGates`) but not in Settings. Decide which set the parity test compares against, because `getEntries()` and `getWelcomeEntries()` give different answers here.
- The `screen_capture_consent` row is kept out of the grant-all batch only by the branch order inside `CheckPermissionStatusUseCase` - pin it with a test in this phase, since a runtime request for an install-time permission would come back permanently denied.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionManifestExemptions.kt` exists.
- `Grep` - `object PermissionManifestExemptions` matches exactly once.
- `Grep` - `require(` matches inside its `init` block.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 04.2 - Compare the registry against the merged manifest in both directions

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryManifestParityTest.kt` (New)
**Depends on:** Step 04.1

**Prompt for developer:**

> Write a Robolectric test that reads the merged manifest of the variant it runs under - `RuntimeEnvironment.getApplication().packageManager.getPackageInfo(packageName, GET_PERMISSIONS).requestedPermissions`, obtained through the `*Compat` helper in `util/PackageManagerCompat.kt` per Rule 21 - and compares it with the registry.
>
> Two assertions, both naming the offending permission in the failure message:
>
> - every declared permission is either the `manifestName` of an entry the registry yields for this build, or listed in `PermissionManifestExemptions`;
> - every entry the registry yields for this build names a permission that is declared.
>
> Pin the SDK level with `@Config(sdk = [..])` so the SDK-windowed entries resolve deterministically, and drive the registry through the real `PermissionRegistryRepositoryImpl` rather than a fixture, since the gate's whole value is that it reads the shipping list.

**Why:**

Strategic ADR-4 records that both discovered divergences - the wrong microphone gate and the battery row in release builds - survived to today under an existing manual permission-audit item in the release checklist, so the recurring finding is converted into a mechanical gate; strategic §11 criteria 6 and 7 state both directions as acceptance conditions.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryManifestParityTest.kt` exists.
- `Grep` - `requestedPermissions` matches in that file.
- `Grep` - `PermissionManifestExemptions` matches in that file.
- `.\a.ps1 fu` runs the new test green on `standardDebug` (record `expected: PASS | actual: <result>`).
- Temporarily removing one entry's `buildGates` and re-running the test fails it - record `expected: FAIL | actual: <result>`, then restore.

**Status:** `[x]` done

---

### Step 04.3 - Replace the manual audit item in the release checklist

**Files:** `docs/RELEASE_READINESS_STANDARD.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Replace the manual "permission audit" line with the command that runs the parity test across the variants where the composition actually differs - `standardRelease` for the build-type axis, `liteDebug` for the flavor axis, `noLegalDebug` for the install-from-file axis - and state that a failure blocks the release.

**Why:**

Research artifact 02 records that the release checklist already names a manual permission audit and identifies it as the hook to mechanise rather than invent, and strategic ADR-4 rules that the manual checklist item is exactly the alternative being rejected.

**Verification:**

- `Grep` - `PermissionRegistryManifestParityTest` matches in `docs/RELEASE_READINESS_STANDARD.md`.
- `Grep` - the previous manual permission-audit wording returns zero hits in that file.
- `pwsh -NoProfile -File scripts/post-change.ps1 -File "docs/RELEASE_READINESS_STANDARD.md" -Target "S1436" -Description "Mechanise the release permission audit" -ChangeType Doc` prints `post-change: PASS`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-06 - Step 04.3 DONE. The checklist's manual permission audit is gone from both places it lived: the §6.3 risk-bucket line now points at the mechanical check, and the targetSdk section carries the three commands - `standardRelease` for the build-type axis, `liteDebug` for the flavor axis, `noLegalDebug` for the install-from-file axis - with the statement that a failure blocks the release and what the three ways out are.
  - Verification 3/3 PASS: `PermissionRegistryManifestParityTest` `expected: present | actual: 4 hits`; the old "Pre-launch, permission audit" wording `expected: 0 | actual: 0`; `post-change` on the doc printed `PASS WITH ADVISORIES (1)`, the advisory being `document-registry`, which I then ran directly - `validate.ps1` `expected: PASS | actual: PASS (27 records)` and `generate.ps1 -Check` reports the generated views current, so nothing was left stale.

- 2026-08-06 - Step 04.1 DONE. `PermissionManifestExemptions` holds two maps and one suffix map, each value a reason in prose, with an `init` block that refuses a blank one. Two entries the plan asked for were deliberately not written - see the step's own note; both permissions are registry rows now, and an exemption for a row that exists is how a parity gate starts lying. Verification 4/4 PASS: file exists, `object PermissionManifestExemptions` `expected: 1 | actual: 1`, `require(` present in `init`, `.\a.ps1 fk` `expected: exit 0 | actual: exit 0`. 69 LOC against a 160 budget.

- 2026-08-06 - Step 04.2 DONE, and **the gate paid for itself on its first run** - it failed, for four separate real reasons, three of which no one knew about:
  - `READ_PHONE_STATE` is declared by `src/launcherEnabled` and requested by S1415's SIM signal indicators from their own code path, with no registry row at all. A dangerous permission the list could not show and the user could not review in one place - precisely the defect strategic §1 describes. Given a row here, gated on `SUPPORT_LAUNCHER`, with its own EN/RU/UK strings. S1415's files were not touched; the row does not disturb its request path.
  - `android.permission.NFC` arrives from the `com.yubico.yubikit:android` dependency and nothing in this app touches NFC. Exempted, with that as the reason.
  - `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is defined and used by `androidx.core` for its own receiver registration. Its full name carries the application id, so it differs between debug and release and cannot be an exact-match key: it needed a third, deliberately tiny suffix map, kept separate because a suffix match is broader than an exact one and has to earn it.
  - **The fourth was a defect in this step's own premise.** The test compared both directions against the gate-only entry set, on the theory that a manifest declaration is not SDK-scoped. It is: `requestedPermissions` does not report a declaration carrying `android:maxSdkVersion` above that level, so `write_external_storage` looked undeclared. The two directions now use different sets on purpose, and the KDoc says which and why - declared-to-row ignores the SDK window (a row starting at a later API is still legitimately declared today, as `access_local_network` shows), row-to-declared honours it.
  - A third test was added beyond the prompt: an exemption for a row that no longer exists fails the suite. An exemption list nobody prunes is the other way this gate decays.
  - Verification 5/5 PASS: file exists, `requestedPermissions` and `PermissionManifestExemptions` both present in it, `.\a.ps1 fu --tests "*Permission*Test"` `expected: PASS | actual: PASS` - 6 classes, 33 tests, 0 failures, XMLs stamped 2026-08-06T17:57Z. Negative control: `buildGates` removed from `request_install_packages`, `expected: FAIL | actual: FAIL` - `Registry rows naming a permission this variant does not declare: [android.permission.REQUEST_INSTALL_PACKAGES]`, then restored and re-run green.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` `expected: exit 0 | actual: exit 0`, and `.\a.ps1 fu --tests "*Permission*Test"` `expected: PASS | actual: PASS` (6 classes, 33 tests, 0 failures, XMLs stamped 2026-08-06T18:07-18:08Z), which compiles `src/test` too.
- [x] `Grep` for `TODO(phase-04)` returns zero hits - `expected: 0 | actual: 0`.
- [x] Dev log entry added for every file in "Files Touched", plus the registry and strings this phase touched beyond the table.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by `catalog_sync` in each close; `PermissionManifestExemptions` carries its role and status.
- [x] Phase-boundary audit run. Self-audited rather than delegated, because the phase's whole surface is one data holder, one test, one registry row and a doc line - no lifecycle, coroutine, listener or Room surface is touched, so layers 2-4 do not apply and layer 1 is the only live one. No P0/P1.

**What the self-audit found worth recording, none of it a defect:**

- The suffix map is a broader match than the exact one by construction. It holds exactly one entry, and the test that prunes stale exemptions does not cover it - a suffix that matches nothing would sit there unnoticed. Acceptable at one entry; a second one should come with its own pruning check.
- `read_phone_state` went into the `SYSTEM` group rather than a group of its own. "Phone" would read better to a user, but a new `PermissionGroup` value needs a heading string and a `when` arm in `getGroups`, and inventing a group while closing a parity gate is how scope leaks. Recorded as a phase-06 candidate.
- **The gate only ever sees the variant it runs under.** On `standardDebug` it cannot observe the `noLegal`-only rows, the `lite` divergence, or the release build-type strip - which is exactly why step 04.3 lists three variants. Nothing in this repo runs them: `a.ps1 fu` drives `standardDebug` only, so the `RECORD_AUDIO` exemption for `lite` and the `REQUEST_INSTALL_PACKAGES` row for `noLegal` are argued here and proved at release time. A per-variant option on the test wrapper would close that, and is worth a ticket rather than a workaround.

---

## Handoff Notes to Next Phase

A permission added to any manifest without a registry entry, and a registry entry whose permission is not declared in this build, both fail the unit suite. Later phases can change texts freely: the gate constrains composition, not wording.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no user-facing surface changed. The new test and the exemption table are additive; reverting only removes the protection.
