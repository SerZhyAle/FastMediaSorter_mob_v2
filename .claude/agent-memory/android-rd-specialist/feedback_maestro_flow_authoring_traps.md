---
name: maestro-flow-authoring-traps
description: Three traps that make a new Maestro flow fail for non-product reasons - ASCII-only inputText, collapsed settings sections, and resources that are not registered
metadata:
  type: feedback
---

Learned writing three flows on 2026-08-13 (S1612). Each cost a full run-diagnose cycle; all three
fail in ways that look like product bugs but are not.

**1. `inputText` is ASCII-only.** A Cyrillic `inputText` **aborts the run** with
`Unicode character input is not supported` (mobile-dev-inc/maestro#146) - it does not merely match
nothing. Matching (`tapOn`, `assertVisible`) handles Cyrillic fine; only typing is restricted. The
app runs in Russian, so this hits constantly. Workaround used: the language row's title is the
trilingual literal `"Language/Язык/Мова"`, so the ASCII query `"Lang"` still matches it.

**2. A collapsed settings section hides its rows from the view tree entirely.** Not "off-screen" -
absent. `scrollUntilVisible` can never find them, whatever the timeout. The settings screen uses
collapsible headers (`csh_headerRow` inside `headerSystem`, `headerAppData`, ..) and sections
differ in whether they ship expanded: `headerInterface` was expanded, `headerSystem` collapsed.
Expand first, and guard the tap on the **target row**, not on the header - tapping an
already-expanded header collapses it.

**3. The seeded test folders are not top-level resources.** `setup_test_media.ps1` seeds
`Docs/`, `DCIM/`, `Audio/` under `/sdcard/Download/FastMediaSorter_Test`, but the app registers
them under the `"Загрузки"` LOCAL resource. Asserting a top-level `"Docs"` fails on the first tap.
Navigate `Локальные` -> `Загрузки` the way `features/player/player_documents.yaml` does.

**How to apply:** before writing a flow, read a neighbouring flow in the same directory for the
house navigation pattern instead of inferring one from the layout XML. When a step fails, dump the
live UI hierarchy and read what is actually there - but FILTER the dump (`Select-String` for the
ids you care about), because an unfiltered `uiautomator dump` of a settings screen is enormous and
burns context for nothing.

Related: [[maestro-suite-needs-ru-app-locale]] (the environment half of the same problem).
