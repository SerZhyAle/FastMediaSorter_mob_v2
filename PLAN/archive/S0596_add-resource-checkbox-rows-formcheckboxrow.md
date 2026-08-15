# Strategic Specification: S0596 - Add Resource checkbox rows -> FormCheckboxRow

**Ticket:** S0596
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-21
**Tier:** 3 - Standard
**Roadmap entry:** Ad-hoc - UI unification follow-up (discovered during S0595)

<!-- discovered by /spec-all - 2026-06-21 -->

---

## 0. Raw capture (origin)

Discovered while implementing S0595 phase 01. `FormCheckboxRow` shipped in S0595 and was
applied to the Resource Editor `Remember File List` row. The same `checkbox + subtitle +
optional help` pattern repeats across the Add Resource screen (SMB / SFTP / Local / Cloud
scanning + options sections), but converting those rows requires migrating 170+ controller
call sites in `AddResourceFormManager.kt` (~135) and `AddResourceHelper.kt` (~36) from the
2-arg `CompoundButton.OnCheckedChangeListener` to `FormCheckboxRow`'s 1-arg listener API,
plus folding the per-row subtitle `TextView`s into `fcr_subtitle` and the SMB/SFTP
`btnSmb/SftpHelpRememberFileList` help buttons into `fcr_showHelp`.

That controller-wide churn carries regression risk disproportionate to a single phase of
S0595 and is cleanly separable, so it was deferred here.

---

## 1. Problem

`activity_add_resource.xml` still hand-builds `checkbox + subtitle (+ optional help)` rows:
- SMB scanning section: `cbSmbScanSubdirectories`, `cbSmbAllFiles`, `cbSmbShowSubfoldersAsItems`, `cbSmbDisableThumbnails`, `cbSmbRememberFileList` (+help), `cbSmbAddToDestinations`, `cbSmbReadOnlyMode`.
- SFTP scanning section: matching `cbSftp*` set (+`cbSftpRememberFileList` help).
- Local options: `cbLocalScanSubdirectories`, `cbLocalAddToDestinations`, `cbLocalReadOnlyMode`.
- Cloud: `cbCloudReadOnlyMode`.

Each repeats a `MaterialCheckBox` + indented subtitle `TextView`, and the two
Remember-File-List rows add a manual `ImageButton` help icon wired to `TooltipDialog` in the
activity. This is the same debt `FormCheckboxRow` (S0595) was built to remove.

## 2. Goals

1. Replace the surveyed Add Resource `checkbox + subtitle + optional help` rows with
   `FormCheckboxRow`, one focus stop each, row-owned help payload.
2. Migrate the controller checkbox wiring (`AddResourceFormManager`, `AddResourceHelper`,
   `AddResourceActivity`) to `FormCheckboxRow`'s 1-arg listener + `setCheckedSilently`.
3. Remove the `btnSmbHelpRememberFileList` / `btnSftpHelpRememberFileList` ImageButtons and
   their manual `TooltipDialog` wiring; the row owns help.
4. Remove `@string/help_remember_file_list` if it becomes orphaned after the help buttons go.

## 2.2 Non-Goals

- The media-type checkbox grids (`cbSmbSupport*` / `cbSftpSupport*`) - those are compact
  toggles without subtitles, not the `FormCheckboxRow` pattern.
- `FormFieldPairLayout` text-field pairs - already migrated in S0595.

---

## 3. Constraints & Guidelines

- Preserve every checkbox id the controllers bind; only the field type changes
  (`MaterialCheckBox` -> `FormCheckboxRow`).
- Guarded programmatic set must not fire `onFieldChanged` - use `setCheckedSilently`.
- Themed attributes only; no hardcoded HEX. EN/RU (Ё/ё)/UK lockstep for any new string.
- `activity_add_resource.xml` has no `layout-land/` counterpart - portrait-only edit.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** In-place replacement of existing Add Resource checkbox rows; no new screens, no relocations. Subtitle text and help payloads are the existing strings.
- **Visual reference:** `FormCheckboxRow` as already used in `fragment_resource_editor.xml` (S0595) is the canonical reference.
- **Accessibility:** Each row is one D-pad/keyboard focus stop; TalkBack announces checked state; non-color focus indication.
- **Communication policy:** No new user-visible strings expected; reuses existing labels/subtitles/help payloads.
- **Validation level:** `a.ps1 fc` + on-device Add Resource SMB/SFTP/Local/Cloud flows (toggle each option, verify `onFieldChanged` fires once, help opens TooltipDialog).
- **Related tickets:** S0595 (parent; shipped `FormCheckboxRow` + the field-pair migration).

---

## 5. Scope

Migrate the surveyed Add Resource checkbox rows (§1) to `FormCheckboxRow` and update the
three controllers. Reuse the S0595 `FormCheckboxRow` API verbatim.

---

## 7. Verification Plan

- `a.ps1 fc` passes after controller migration.
- Grep: zero `btnSmbHelpRememberFileList` / `btnSftpHelpRememberFileList` references remain.
- Grep: surveyed `cbSmb*`/`cbSftp*`/`cbLocal*`/`cbCloud*` option rows reference `FormCheckboxRow` in the layout.
- On-device: each migrated option toggles once and persists; Remember-File-List help opens TooltipDialog.
