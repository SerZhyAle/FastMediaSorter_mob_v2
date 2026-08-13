# Phase 03 - Player Command Reactivation

Status: Done

## Goal

Use the user setting to expose the existing player command panel action and preserve player launch context when opening a duplicate playback window.

## Expected Checks

- Command visibility follows `AppSettings.allowSeparateWindow`.
- New player window receives file path, resource id, initial index, and a new window id.
- Source player remains available after opening the new window.
