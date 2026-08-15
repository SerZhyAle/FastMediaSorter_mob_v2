# Writing Maestro Tests - Developer Guide

A practical guide for developers to create effective Maestro tests for FastMediaSorter v2.

## Oracle convention (authoritative)

A flow is **green only when it proves the behaviour happened**. Three rules, in order:

1. **Assert the expected post-action element by exact id or exact text.** The assertion that
   proves the feature must NOT carry `optional: true`. `optional` is reserved for genuinely
   variable UI (system permission dialogs in `_shared/permissions.yaml`) - never for the
   element that is the point of the test.
2. **Where a stable completion log marker exists, wait for it** (`extendedWaitUntil` on the
   element plus, conceptually, the operation's log marker). Markers per operation and ids per
   screen are inventoried in the S0551 research notes (browse listing
   `BrowseLoadingManager: COMPLETE`, video `onRenderedFirstFrame` / `Playback ready`, file ops
   `FileOperationProgressDialog: Completed`, PDF `firstPageRendered`, EPUB `firstChapterRendered`,
   slideshow `Slideshow auto-start COMPLETE`). Rename, undo, image, text, resume and the info
   dialog have no marker - assert by element only, never weaken to optional-only.
3. **Carry a crash guard:** `assertNotVisible` on the crash-activity text after the action.

**Forbidden** (these silently pass and defeat the oracle - they are why the legacy flows were
fictitious):

- `optional: true` on the proof assertion.
- Regex matchers in `id:` / `text:` (`id: ".*recycler.*"`, `text: ".*\.(jpg|png)$"`). Maestro
  does not reliably match these; the assertion never fires. Use exact entry-name ids
  (`id: "rvMediaFiles"`) or exact visible text, locale-fixed for the run.
- Coordinate taps (`point: "50%,50%"`) as a stand-in for a real assertion.

The patterns later in this guide that show `optional: true` on a result assertion or regex
selectors predate this convention and must not be copied - this section overrides them.

## Test Structure

Every Maestro test follows this pattern:

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: [Test Name]
# Description: [What this test validates]
# Duration: [Expected time]

# Setup phase
- launchApp
- waitForAnimationToEnd

# Action phase
- tapOn:
    text: "Button"

# Assertion phase
- assertVisible:
    text: "Expected Result"
```

## Core Principles

### 1. One Test, One Feature
Each test should validate ONE specific feature or user flow.

**Good**:
```yaml
# Test: Browse to folder
- launchApp
- tapOn: { text: "Pictures" }
- assertVisible: { text: "Pictures" }
```

**Bad**:
```yaml
# Test: Everything
- launchApp
- tapOn: { text: "Pictures" }
- tapOn: { text: "Settings" }
- tapOn: { text: "Favorites" }
# Too many unrelated actions
```

### 2. Make Tests Resilient - Without Weakening the Oracle

`optional: true` is for genuinely variable UI only: a system permission dialog that may or may
not appear because a prior run already granted it.

```yaml
# Correct use: the dialog is genuinely absent when permission is already granted.
- tapOn:
    text: "Allow"
  optional: true
```

Never put `optional: true` on the assertion that proves the feature. An optional proof passes
whether or not the behaviour happened, which is the definition of a fictitious test:

```yaml
# WRONG - passes even when playback never started.
- assertVisible:
    id: "playerView"
  optional: true

# RIGHT - the flow fails when playback did not start.
- assertVisible:
    id: "playerView"
```

Resilience comes from waiting longer, not from asserting less. Use `extendedWaitUntil` when an
operation is slow.

### 3. Wait for UI to Stabilize

Always wait after navigation or state changes:

```yaml
- tapOn:
    text: "Settings"
- waitForAnimationToEnd  # Critical!

- assertVisible:
    text: "Preferences"
```

### 4. Use Descriptive Names

```yaml
# Good: Clear intent
# Test: User can favorite a file

# Bad: Vague
# Test: Test 1
```

## Common Test Patterns

### Pattern 1: Navigation Test

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: Navigate to Settings
# Validates: Settings screen is accessible

- launchApp
- waitForAnimationToEnd

- tapOn:
    text: "Settings"

# The proof assertion - an element that exists only on the Settings screen.
# Never assert the label you just tapped: it is visible either way.
- assertVisible:
    id: "settingsRoot"
```

### Pattern 2: Action + Verification

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: Toggle a setting

- launchApp
- tapOn:
    text: "Settings"
- waitForAnimationToEnd

# Action
- tapOn:
    id: "switchDarkMode"

# Verification - assert the state that only exists after the toggle applied.
- assertVisible:
    id: "switchDarkMode"
    enabled: true
```

### Pattern 3: Form Input

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: Add SMB connection
# Every field is addressed by its exact resource-id entry name.

- launchApp
- tapOn:
    id: "fabAddResource"

- tapOn:
    text: "SMB"

# Input fields
- tapOn:
    id: "etHost"
- inputText: "192.168.1.100"

- tapOn:
    id: "etUsername"
- inputText: "user"

- tapOn:
    id: "etPassword"
- inputText: "pass"

# Submit
- tapOn:
    text: "Connect"

# Proof: the saved resource appears in the list. Not optional.
- extendedWaitUntil:
    visible:
      text: "192.168.1.100"
    timeout: 10000
```

### Pattern 3a: Expand a collapsible settings section first

A settings row inside a **collapsed** section is not merely off-screen - it is absent from the view
tree, so `scrollUntilVisible` can never find it however long the timeout. The settings screen uses
collapsible headers (`csh_headerRow` inside `headerSystem`, `headerAppData`, ..), and sections
differ in whether they ship expanded.

```yaml
# Guard on the TARGET row, not on the header: tapping an already-expanded header collapses it.
- scrollUntilVisible:
    element:
      id: "com.sza.fastmediasorter.debug:id/headerSystem"
    timeout: 15000
    visibilityPercentage: 30
    centerElement: true
- runFlow:
    when:
      notVisible:
        id: "com.sza.fastmediasorter.debug:id/rowEnableStatistics"
    commands:
      - tapOn:
          id: "com.sza.fastmediasorter.debug:id/csh_headerRow"
          childOf:
            id: "com.sza.fastmediasorter.debug:id/headerSystem"
      - waitForAnimationToEnd
```

### Pattern 3b: `inputText` is ASCII-only

Maestro cannot type non-ASCII text (`mobile-dev-inc/maestro#146`): a Cyrillic `inputText` aborts
the run with `Unicode character input is not supported`, it does not merely match nothing. This
matters here because the app under test runs in Russian.

```yaml
# WRONG - aborts the flow on a ru-locale device.
- inputText: "Язык"

# RIGHT - an ASCII substring that the target still matches.
- inputText: "Lang"
```

Matching (`tapOn`, `assertVisible`) handles Cyrillic fine - only **typing** is restricted. When no
ASCII substring exists, drive the field another way (a preset value, a picker) rather than
weakening the assertion.

### Pattern 4: List Scrolling

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: Scroll through file list

- launchApp
- tapOn:
    text: "Browse"
- waitForAnimationToEnd

# Scroll to find item
- scrollUntilVisible:
    element:
      text: "Downloads"
  timeout: 10000

- tapOn:
    text: "Downloads"

# Proof: an element of the opened folder, not the folder label itself.
- assertVisible:
    id: "rvMediaFiles"
```

### Pattern 5: Long Press Action

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: Long press context menu

- launchApp
- waitForAnimationToEnd

# Long press a file that the seeded test media guarantees exists.
- longPressOn:
    text: "IMG_001.jpg"
  duration: 1000

# Verify context menu - these assertions are the point of the test, so none is optional.
- assertVisible:
    text: "Copy"

- assertVisible:
    text: "Delete"
```

## Element Selectors

### By Text (Visible Text)
```yaml
- tapOn:
    text: "Settings"
```

### By Resource ID (preferred)
```yaml
# The entry name alone is enough - it matches ...:id/button_add.
- tapOn:
    id: "button_add"
```

### By Index (Position in List)
```yaml
- tapOn:
    index: 0  # First element
```

### Not available: coordinates and regex

Coordinate taps (`point: "50%, 80%"`) and regex selectors (`id: ".*add.*"`,
`text: ".*\\.(jpg|png)$"`) are **forbidden** by the Oracle convention at the top of this file.
Maestro does not reliably match regex selectors, so the step silently never fires; a coordinate
tap proves nothing about which element was hit and breaks on the next layout change.

Where no stable id exists, use exact visible text and fix the run locale. Where neither exists,
add an id to the layout - that is cheaper than a flow nobody can trust.

### Relative Selectors
```yaml
# Tap button below title
- tapOn:
    text: "OK"
    below:
      text: "Confirmation"

# Tap button to the right
- tapOn:
    text: "Next"
    rightOf:
      text: "Previous"
```

## Assertions

### Assert Visible
```yaml
- assertVisible:
    text: "Success"
  timeout: 5000
```

### Assert Not Visible
```yaml
- assertNotVisible:
    text: "Loading..."
```

### Optional Assertion - permission dialogs only
```yaml
# Legitimate: the dialog is genuinely absent once permission was granted.
- tapOn:
    text: "Allow"
  optional: true
```

Anywhere else, `optional: true` turns the step into a no-op that passes silently. See the
Oracle convention at the top of this file.

### Crash Guard (required)
```yaml
# Third oracle rule: prove the action did not crash the app.
- assertNotVisible:
    text: "Отправить отчёт о сбое?"
```

## Waits and Timing

### Basic Wait
```yaml
- waitForAnimationToEnd
```

### Extended Wait
```yaml
- extendedWaitUntil:
    visible:
      text: "Loaded"
    timeout: 15000  # 15 seconds
```

### Wait Until Not Visible
```yaml
- extendedWaitUntil:
    notVisible:
      text: "Loading..."
    timeout: 30000
```

## Testing Best Practices

### 1. Test Happy Path First
Start with the ideal user flow where everything works perfectly.

### 2. Then Add Edge Cases
- Empty states
- Error conditions
- Network failures
- Permission denials

### 3. Keep Tests Independent
Each test should:
- Start from a clean state
- Not depend on other tests
- Clean up after itself (if needed)

### 4. Use Test Data
```yaml
# Use test files that exist on device
- scrollUntilVisible:
    element:
      text: "test_photo.jpg"
```

### 5. Handle Multiple Scenarios

`optional: true` is allowed on a **navigation tap** whose target genuinely varies by prior
state - an onboarding page a returning user never sees. It stays forbidden on the assertion
that proves the feature.

```yaml
# Handle both new user and returning user.
- tapOn:
    text: "Skip Tutorial"
  optional: true

- tapOn:
    text: "Next"
  optional: true

# The proof assertion that follows is never optional.
- assertVisible:
    id: "rvResources"
```

## Common Mistakes to Avoid

### ❌ Don't: Forget to wait
```yaml
- tapOn: { text: "Settings" }
- assertVisible: { text: "Preferences" }
# May fail if animation is slow
```

### ✅ Do: Wait for UI
```yaml
- tapOn: { text: "Settings" }
- waitForAnimationToEnd
- assertVisible: { text: "Preferences" }
```

### ❌ Don't: Tap coordinates at all
```yaml
- tapOn:
    point: "50%, 50%"  # Proves nothing about which element was hit
```

### ✅ Do: Tap the element
```yaml
- tapOn:
    id: "btnPlayPause"
```

### ❌ Don't: Hard-code delays
```yaml
- tapOn: { text: "Load" }
- sleep: 5000  # Bad: Fixed delay
```

### ✅ Do: Wait for conditions
```yaml
- tapOn: { text: "Load" }
- extendedWaitUntil:
    visible: { text: "Loaded" }
    timeout: 10000  # Wait up to 10s
```

### ❌ Don't: Match on long, editable prose
```yaml
# A whole sentence changes with any copy edit
- tapOn:
    text: "Click here to continue to the next screen"
```

### ✅ Do: Match on the element's id
```yaml
# Stable across copy edits and locales. A regex is NOT the answer here.
- tapOn:
    id: "btnContinue"
```

## Debugging Your Tests

### 1. Run with Debug Flag
```bash
maestro test --debug maestro/smoke/my_test.yaml
```

### 2. Use Maestro Studio
```bash
maestro studio
# Test selectors interactively
```

### 3. Add Debug Assertions
```yaml
# Verify element exists before tapping
- assertVisible:
    text: "Button"

- tapOn:
    text: "Button"
```

### 4. Take Screenshots
```bash
# In test
- tapOn: { text: "Settings" }
- runScript:
    file: scripts/take_screenshot.js
```

### 5. Check Logs
```bash
# View test execution log
cat ~/.maestro/tests/<test_name>/maestro.log
```

## Test Organization

### Smoke Tests (maestro/smoke/)
- Critical user flows
- Must pass for every build
- Fast execution (< 1 min each)

Example:
- `app_launch.yaml`
- `browse_files.yaml`
- `play_media.yaml`

### Critical Path Tests (maestro/critical/)
- Important features
- Should pass before release
- Medium execution time (1-2 min each)

Example:
- `file_operations.yaml`
- `settings_persistence.yaml`

### Feature Tests (maestro/features/)
- Specific feature validation
- Run as needed
- Variable execution time

Example:
- `smb_connection.yaml`
- `image_editing.yaml`
- `cloud_sync.yaml`

## Example: Complete Test

Here's a complete test with all best practices:

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: Open an image in the player
# Description: Proves an image from the seeded DCIM resource renders in the viewer.
# Prerequisites: setup_test_media.ps1 has seeded DCIM; DCIM registered as a LOCAL resource.

# Deterministic start.
- launchApp:
    clearState: true

# Permission dialogs are genuinely variable - the one legitimate use of optional.
- runFlow: ../_shared/permissions.yaml

# Entry-screen assertion before acting.
- assertVisible:
    id: "rvResources"

- tapOn:
    text: "DCIM"

# Wait for the listing to finish - the browse layer has a stable completion marker.
- extendedWaitUntil:
    visible:
      id: "rvMediaFiles"
    timeout: 15000

# Act on a file the seeded media guarantees by exact name.
- tapOn:
    text: "IMG_001.jpg"

# THE PROOF. Exact id, never optional: this assertion is the point of the test.
- extendedWaitUntil:
    visible:
      id: "photoView"
    timeout: 10000

# Crash guard - third oracle rule.
- assertNotVisible:
    text: "Отправить отчёт о сбое?"
```

## Next Steps

1. Read existing tests in `maestro/smoke/` and `maestro/critical/`
2. Try modifying an existing test
3. Create a new test for a feature you're working on
4. Run your test locally before committing
5. Add test to appropriate suite (smoke/critical/features)

## Resources

- [Maestro Documentation](https://maestro.mobile.dev)
- [YAML Syntax Guide](https://maestro.mobile.dev/reference/yaml-syntax)
- [Project Examples](EXAMPLES.md)
- [Troubleshooting Guide](TROUBLESHOOTING.md)

Happy Testing! 🎯
