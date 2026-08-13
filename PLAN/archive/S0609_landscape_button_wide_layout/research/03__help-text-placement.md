# Research 03 - Help / description text placement

Resolves strategic §6.3. Source: widget + layout inventory (2026-06-22).

## Two kinds of help in settings

1. Inline help icon - each row widget (`SettingsToggleRow`, `SettingsDropdownRow`, `SettingsSelectionRow`, `SettingsInputRow`) already has a built-in `iconHelp` slot (`gone` by default). This is the canonical, already-supported help affordance. No layout work needed; it travels with the row.
2. Standalone label/description `TextView` - e.g. `tvImageSizeLabel`, `tvVideoSizeLabel`+`iconHelpVideoSizeLimits`, snapshot/format section labels. These currently occupy a full-width row above their control.

## Decision

- Prefer the built-in row `iconHelp` slot for per-setting help - do not convert it to a separate column.
- For a standalone label that directly precedes a single control (label + input pair), place the label in the left column and the control in the right column of one landscape row, only where the label is short. Long labels (size-limit descriptions) stay above the control (a 50% column truncates them in RU/UK).
- Do not multi-column long explanatory paragraphs (e.g. background-audio exit-behavior summary) - readability over density.

## Net

Help placement is opportunistic, not a blanket rule: collapse label+control to one row only for short labels with a single adjacent control. This is a per-fragment judgement during implementation, not a global transform.
