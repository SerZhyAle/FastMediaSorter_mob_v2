# Phase 01 - Capability Default

Status: Done

## Goal

Replace the old flavor-gated default with a capability detector that enables the separate-window setting by default on desktop/XR/freeform-capable environments while leaving regular phones and tablets off by default.

## Expected Checks

- Existing stored preferences continue to win over default detection.
- Standard builds expose the existing setting.
- Detector handles missing/non-standard feature flags safely.
