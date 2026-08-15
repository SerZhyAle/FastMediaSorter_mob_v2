# S1335 - READ_CONTACTS becomes a first-class permission everywhere the app explains its permissions

**Status:** Archived
**Implemented date:** 2026-08-01
**Tactical plan:** `PLAN/S1335_read-contacts-permission-plumbing/INDEX.md`
**Priority:** 55

<!-- owner decision 2026-07-31 during /spec-tech batch; unblocks S1319 -->

## 0. Owner decision

2026-07-31, answering the S1319 question "contact photo is unreachable without `READ_CONTACTS`":
**declare the permission.** Then, verbatim: «У нас есть новое право - READ_CONTACTS которое нужно
оформить со всеми другими правилами на всех экранах управления правами аналогично».

So the deliverable is not a manifest line. It is the permission appearing wherever the app already
accounts for its permissions, on the same footing as the existing ones.

## 1. Why this is its own ticket, not part of S1319

S1319 wants one thing - a photo on a launcher cell. The permission it needs is shared plumbing that
also unblocks a second, already-shipped feature, and it reverses a decision recorded in three places.

- The reversal is explicit and must be undone deliberately, not contradicted silently.
  `ContactSnapshotDataSource.kt:24` carries a standing instruction in its KDoc: **"This class works
  without any contacts permission, and that is deliberate - do not 'fix' it by declaring the
  permission."** S1176 §3.2 says the same, and the owner's own quiz on 2026-07-27 accepted
  "the avatar may fall back to a monogram" on that basis.
- It unblocks more than S1319. `ContactSnapshotDataSource.readMessageChannels` (`:89-94`) appends
  `ContactsContract.Contacts.Entity.CONTENT_DIRECTORY` to the picked contact URI - a sub-URI the
  picker's single-URI grant does not reach. Analysis predicts the MESSAGE channel therefore resolves
  for **no** contact, not just for contacts without a messenger. That is a defect in already-shipped
  S1176 work, and the same permission fixes it. Prediction, not yet measured on a device.
- It is a **restricted** Play permission. Declaring it pulls in a Play Console declaration form and a
  privacy-policy change in three languages - release-process work no feature ticket should carry
  incidentally.

## 2. Surfaces that must carry it - measured 2026-07-31

The permission model is centralised, which is the good news: one registry feeds both the settings
screen and the onboarding page.

- `data/permissions/PermissionRegistryRepositoryImpl.kt` - a single `allEntries` list. `getEntries()`
  serves the settings screen, `getWelcomeEntries()` serves onboarding. One new `PermissionEntry` is
  picked up by both.
- `domain/model/PermissionEntry.kt` - `PermissionGroup` has no contacts group today: `STORAGE`,
  `NETWORK`, `MICROPHONE`, `NOTIFICATION`, `CAMERA`, `LOCATION`, `SYSTEM`, `VR`.
- `getGroups()` maps every group to a title through an **exhaustive** `when`. A new enum constant
  without a new arm does not compile, which is the right kind of failure.
- Strings: `perm_group_*` ×8 exist today; a new group needs `perm_group_contacts`, and the entry
  needs `perm_title_read_contacts` + `perm_desc_read_contacts` - all three in EN, RU and UK.
- `ui/settings/fragments/PermissionsManagementFragment.kt` + `PermissionRowAdapter.kt` render from the
  registry, so they need no per-permission edit.
- `ui/welcome/holders/PermissionsPageViewHolder.kt` + `helpers/WelcomePermissionsManager.kt` - same.
- `ui/common/permissions/PermissionRationaleBottomSheet.kt` driven by
  `domain/usecase/RequestContextualPermissionUseCase.kt` - the contextual ask at the moment of use
  needs its own rationale copy.
- `docs/PRIVACY_POLICY.md` + `.ru.md` + `.uk.md` - a restricted permission must be described.
- Play Console - restricted-permission declaration form.

### 2.1 The blocker nobody has hit yet: the launcher has no `BuildConfig` gate

The entry must not appear on builds without the launcher, or `lite`/`photos`/`legacy`/`vr` users are
offered a permission the app can never use.

But `PermissionEntry.flavorGates` keys on **`BuildConfig` field names**, resolved through a
compile-time `when` in `resolveFlavorGate` (S0970 deliberately replaced reflection there, because R8
strips the field declarations and the old code silently disabled permissions on release builds).
Today that map knows exactly three fields: `SUPPORT_AUDIO`, `SUPPORT_LOCAL_NETWORK`,
`ENABLE_PERSISTENT_AUDIO_PLAYBACK`.

The launcher has **no `BuildConfig` field at all** - it is isolated purely by source set
(`src/launcherEnabled/` mounted by `standard` and `noLegal`; the rest mount `src/launcherDisabled/`).
So this ticket has to introduce one and add its `resolveFlavorGate` arm. An unmapped gate name is not
a crash - `resolveFlavorGate` logs an error and returns `false`, silently hiding the permission. There
is already a test asserting every declared gate field resolves; it must keep passing.

**Naming and declaration mechanism (decided during `/spec-tech`, 2026-08-01):** field name
`SUPPORT_LAUNCHER` - a capability-availability flag (matching the `SUPPORT_*` register already used
by `SUPPORT_AUDIO`/`SUPPORT_LOCAL_NETWORK`/`SUPPORT_WEAR_COMPANION`, not the `ENABLE_*` register used
for sub-feature toggles), general-purpose so a future launcher-gated permission reuses it rather than
minting another field. Declared once via the lighter `IS_NO_LEGAL_FLAVOR` pattern already in
`app_v2/build.gradle.kts` - default `false` in `defaultConfig`, overridden to `true` only in the
`standard` and `noLegal` blocks - not by repeating `buildConfigField` in all six flavor blocks; the
resulting `BuildConfig` surface is identical either way and this is two touches instead of six.

## 3. Fix

Add the permission to the registry as an **optional** entry in a new `CONTACTS` group, gated on a new
launcher `BuildConfig` field, with title, description and group strings in all three locales, a
contextual rationale, a manifest declaration, and the privacy-policy paragraph. Then revoke the three
recorded "no contacts permission" statements rather than leaving them to contradict the code.

Requesting stays **contextual and optional**: the entry is requestable by tapping its row in Settings
> Permissions or on the onboarding permissions page - both surfaces already drive a generic
`requestSingle.launch(entry.manifestName)` for any non-special permission, so this needs no new
request-triggering code, only the registry entry. Never a blocking step in onboarding (the existing
mechanism already treats every optional entry this way). A denial keeps today's monogram fallback.

**Correction (found during `/spec-tech` tactical planning, 2026-08-01):** "asked for when the user
pins a contact cell" cannot land as a call site in `LauncherContactPickManager` today - S1176's
shipped pin flow (PROFILE/DIAL/MESSAGE) already succeeds without `READ_CONTACTS` (it reads only the
system picker's one-time URI grant), and S1319 (the feature that would actually need the permission
for a photo read) is `BlockByOtherTask` on this very ticket, not yet implemented. Wiring a contextual
ask into `LauncherContactPickManager` now would request a permission nothing in that flow consumes.
**This ticket's scope is the plumbing only** (registry, group, strings, manifest, BuildConfig gate,
docs) - the pin-time contextual ask is S1319's own wiring, added when it implements the photo read
that needs `READ_CONTACTS`, using the same `RequestContextualPermissionUseCase` pattern
`GeneralSettingsPermissionsHelper` already uses for `access_local_network`. Nothing else in the app
may start depending on it.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1319 (`launcher-contact-cell-photo`, `BlockByOtherTask` on this ticket -
  corrected from a stale `Draft` reference, 2026-08-01) - the consumer; blocked until this
  lands. S1176 (`launcher-contact-shortcuts`, `BlockNeedUserTest`) - its §3.2 forbids this permission
  and its quiz decision of 2026-07-27 accepted the monogram fallback on that basis; both are reversed
  here, and its MESSAGE channel is predicted to start working. S0970 (Archived) - established the
  compile-time `resolveFlavorGate` map after R8 broke the reflective version.
- **Owner decision:** declare `READ_CONTACTS` (2026-07-31), overriding S1176 §3.2 and the 2026-07-27
  quiz answer.
- **Request timing:** contextual only, at the moment the user pins a contact cell. Never a blocking
  step in onboarding. Denial keeps the existing monogram, so the feature degrades rather than breaks.
- **Flavors:** the entry is gated to builds that ship the launcher - `standard` and `noLegal`. This
  requires a new `BuildConfig` field, since the launcher currently has none; see §2.1.
- **Strings:** three new keys - `perm_group_contacts`, `perm_title_read_contacts`,
  `perm_desc_read_contacts` - plus the contextual rationale copy, each in EN, RU and UK.
- **Legal:** `docs/PRIVACY_POLICY.md` and its RU and UK versions gain a paragraph; Play Console needs
  the restricted-permission declaration before a release carrying this can ship. The Play Console form
  itself is a release-time console action, not a repo file change - out of this ticket's tactical
  phases, tracked as a manual release gate (see Verification).

---

## 4. Verification

- Settings > permission management lists Contacts as its own group on `standard` and `noLegal`, with
  a real title and description in all three languages, and does **not** list it on `lite`, `photos`,
  `legacy` or `vr`.
- The onboarding permissions page shows the same entry under the same rule.
- Tapping the Contacts row (Settings or onboarding) requests `READ_CONTACTS` via the existing generic
  mechanism; denying it leaves today's monogram and does not break the shipped S1176 pin flow (which
  needs no permission either way). The pin-time contextual ask belongs to S1319, not this ticket - see
  correction above.
- The existing flavor-gate test still passes - every declared gate field resolves through the
  compile-time map, including the new launcher one.
- Release build check: the permission is not silently disabled by R8, which is the failure mode S0970
  was written for.
- Measure the S1176 MESSAGE-channel prediction on a device before and after: if channels appear only
  after the permission is granted, §1's prediction is confirmed and S1176 needs its own follow-up.

---

## 5. Related

- **S1319** - consumer, blocked on this.
- **S1176** - reversed by this; also the ticket whose MESSAGE channel this is predicted to repair.
- **S0970** - why `resolveFlavorGate` is a compile-time map and not reflection.

---

## Last Audit

**Date:** 2026-08-01
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 19 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 1

Audit found the third of the "three recorded 'no contacts permission' statements" §1 names
(`PLAN/S1176_launcher-contact-shortcuts.md`'s Quiz-decisions line) had not been revoked by Phase 03
step 03.3 - only §3.2 and `ContactSnapshotDataSource.kt`'s KDoc were. Completed directly during this
audit (in-contract, not an out-of-scope finding). Also confirmed both `standard` (`fk`/`dq`) and
`noLegal` (`fkn`) compile with the new field/registry entry.

### Manual / on-device

- [ ] Cross-flavor absence of the Contacts entry on `lite`/`photos`/`legacy`/`vr` - implied by
      `BuildConfig.SUPPORT_LAUNCHER = false` there, not independently re-run per flavor (unit test
      only exercises the `standardDebug` variant).
- [ ] Play Console restricted-permission declaration form - release-time gate, not a repo change.
- [ ] Device measurement of the S1176 MESSAGE-channel prediction after `READ_CONTACTS` is granted via
      Settings.
