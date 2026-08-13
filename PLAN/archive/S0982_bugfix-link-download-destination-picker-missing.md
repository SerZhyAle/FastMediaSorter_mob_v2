# Compact specification: S0982 - Link download destination picker investigation

**Ticket:** S0982
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-10
**Tier:** 2 - Easy
**Related tickets:** S0980, S0842

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - implementation through `/spec-all`.
- **Goal / expected outcome:** Provided by user - determine why the link-download destination picker appeared missing and restore access if defective.
- **Local anchor:** Provided by user - Android 13 standard-debug device drain for S0980; `linkAutoDownloadResourceId`; settings screen.
- **Scope boundaries / forbidden areas:** Delegated by user - limit work to the active settings implementation and ticket documentation; do not touch read-only zones or redesign unrelated settings.
- **Done / success signal:** Delegated by user - establish the actual picker location and gates, prove persistence wiring and search indexing, and fix only a confirmed defect.
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions under `/spec-all`.
- **UI decisions / delegation:** Delegated by user - preserve the existing canonical destination-picker pattern and current portrait/landscape placement unless investigation proves it broken.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0980, S0842
- **UI placement:** Keep the existing Incoming links subgroup placement in portrait and landscape.
- **UI visibility:** Keep the selector visible and disable it only when link auto-download is disabled.

## Goal

Установить, почему во время проверки S0980 пикер назначения link-download сочли отсутствующим. Если дефект существует, исправить его; если пикер уже работает по утверждённому UI-контракту, зафиксировать правильный путь и не вносить лишние изменения.

## Root cause

The S0980 device drain looked for the link-download selector inside the collapsible **Quick Sort destinations** card. The selector belongs to the separate **Incoming links** subgroup farther down the same Management/Operations settings tab.

The live implementation already satisfies the feature contract:

- Portrait and landscape layouts both declare `btnSelectLinkAutodownloadResource`.
- `OperationsSettingsFragment` opens the destination picker and persists the selected id to `linkAutoDownloadResourceId`.
- The selector remains visible and becomes disabled only while `linkAutoDownloadEnabled` is off.
- A null or unavailable selection falls back to Downloads.
- Settings search explicitly allow-lists the icon-only selector and indexes it as `Select resource..`.
- S0842 verified this selector on-device on 2026-07-09 as one of eight rendered, tappable, D-pad-accessible destination pickers.

No rendering, flavor, capability, resource-count, or search-index defect was found. The Android 13 profile was not the cause; the expected navigation path was incorrect.

## UI Clarification Status

Status: READY

### Approved Decisions

- Keep the selector in **Management/Operations > Incoming links** in portrait and landscape.
- Keep it visible for all supported standard profiles; disable it with the auto-download master toggle.
- Keep Downloads as the unset/unavailable fallback.
- Keep the icon-only canonical resource affordance, tooltip, content description, and search entry.
- Do not duplicate the control inside **Quick Sort destinations**.

## Phase 01 - Investigate and classify

**Status:** Done

- [x] Inspect the portrait and landscape settings layouts.
  - Verification: both layouts declare `btnSelectLinkAutodownloadResource` under `layoutIncomingLinksSubgroup`.
- [x] Trace click handling, persistence, visibility, and fallback behavior.
  - Verification: the fragment opens `showDestinationPicker`, writes `linkAutoDownloadResourceId`, disables children only with the master toggle, and renders the Downloads fallback.
- [x] Inspect settings-search extraction and generated settings documentation.
  - Verification: `LayoutSettingsSearchSource.CD_TITLED_BUTTON_IDS` includes the selector and `settings-manifest.json` contains its OPERATIONS entry.
- [x] Check prior device evidence.
  - Verification: the 2026-07-09 S0842 dev-log entry confirms render, tap, and D-pad behavior for all eight destination pickers.

## Phase 02 - Resolve

**Status:** Done

- [x] Classify the report as a navigation expectation mismatch, not an application defect.
  - Verification: no code predicate hides or removes the selector, and prior device verification covers the same control.
- [x] Preserve the existing UI and avoid a duplicate selector.
  - Verification: no application source or resource file is changed by S0982.
- [x] Record the correct device-test path for S0980 follow-up.
  - Verification: use **Settings > Management/Operations > Incoming links > Auto-download incoming links > Download destination resource**.

## Validation

- PASS - static portrait/landscape layout presence.
- PASS - click-to-persistence wiring for `linkAutoDownloadResourceId`.
- PASS - master-toggle enabled-state behavior and Downloads fallback.
- PASS - settings-search allow-list and generated manifest entry.
- PASS - prior S0842 device verification on 2026-07-09.
- MANUAL - fresh device replay was unavailable because no emulator or phone was online during this run; it is not required to classify the already device-verified control.

## Last Audit

**Date:** 2026-07-11
**Mode:** compact full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 - WARN 0 - FAIL 0 - MANUAL 1 - EXEMPT 1

### Manual / on-device

- [ ] When resuming S0980, navigate to **Incoming links**, not **Quick Sort destinations**, and select the destination there.
