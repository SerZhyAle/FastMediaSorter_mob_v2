# Writing Maestro Tests - Developer Guide

A practical guide for developers to create effective Maestro tests for FastMediaSorter v2.

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

### 2. Make Tests Resilient

Use `optional: true` for elements that may not always appear:

```yaml
# Permission dialogs may not appear if already granted
- tapOn:
    text: "Allow"
  optional: true

# Network-dependent content
- assertVisible:
    text: "Connected"
  optional: true
  timeout: 10000
```

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

- assertVisible:
    text: "Settings"

- assertVisible:
    text: "Dark Mode"
  optional: true
```

### Pattern 2: Action + Verification

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: Toggle dark mode

- launchApp
- tapOn:
    text: "Settings"
- waitForAnimationToEnd

# Action
- tapOn:
    text: "Dark Mode"

# Verification (wait for UI change)
- waitForAnimationToEnd

# Optional: Verify theme changed
- assertVisible:
    id: ".*dark.*"
  optional: true
```

### Pattern 3: Form Input

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: Add SMB connection

- launchApp
- tapOn:
    id: ".*add.*"

- tapOn:
    text: "SMB"

# Input fields
- tapOn:
    id: ".*host.*"
- inputText: "192.168.1.100"

- tapOn:
    id: ".*username.*"
- inputText: "user"

- tapOn:
    id: ".*password.*"
- inputText: "pass"

# Submit
- tapOn:
    text: "Connect"

# Verify
- waitForAnimationToEnd
- assertVisible:
    text: "192.168.1.100"
  timeout: 10000
```

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

- assertVisible:
    text: "Downloads"
```

### Pattern 5: Long Press Action

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: Long press context menu

- launchApp
- waitForAnimationToEnd

# Long press file
- longPressOn:
    text: "photo.jpg"
  duration: 1000
  optional: true

# Verify context menu
- assertVisible:
    text: "Copy"
  optional: true

- assertVisible:
    text: "Delete"
  optional: true
```

## Element Selectors

### By Text (Visible Text)
```yaml
- tapOn:
    text: "Settings"
```

### By Resource ID
```yaml
- tapOn:
    id: "com.sza.fastmediasorter:id/button_add"
```

### By Coordinates
```yaml
- tapOn:
    point: "50%, 80%"  # x%, y%
```

### By Index (Position in List)
```yaml
- tapOn:
    index: 0  # First element
```

### Regex Patterns
```yaml
# Match any image file
- tapOn:
    text: ".*\\.(jpg|png|gif)$"

# Match any text containing "Dark"
- tapOn:
    text: ".*[Dd]ark.*"
```

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

### Optional Assertion
```yaml
# Don't fail if element not found
- assertVisible:
    text: "Optional Element"
  optional: true
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
```yaml
# Handle both new user and returning user
- tapOn:
    text: "Skip Tutorial"
  optional: true

- tapOn:
    text: "Next"
  optional: true
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

### ❌ Don't: Use absolute coordinates
```yaml
- tapOn:
    point: "200, 400"  # Breaks on different screens
```

### ✅ Do: Use relative coordinates
```yaml
- tapOn:
    point: "50%, 50%"  # Works on all screens
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

### ❌ Don't: Make tests brittle
```yaml
# Exact text match may break with updates
- tapOn:
    text: "Click here to continue to the next screen"
```

### ✅ Do: Use flexible selectors
```yaml
# Partial match
- tapOn:
    text: ".*continue.*"
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
# Test: Add file to favorites
# Description: Validates that users can mark files as favorites
# Duration: ~30 seconds
# Prerequisites: At least one file in Browse view

# Setup: Launch and navigate
- launchApp
- waitForAnimationToEnd

# Handle any permission dialogs
- tapOn:
    text: "Allow"
  optional: true

# Navigate to Browse tab
- tapOn:
    text: "Browse"
- waitForAnimationToEnd

# Action: Find and open a file
- scrollUntilVisible:
    element:
      text: ".*\\.(jpg|png|mp4)$"
  timeout: 10000
  optional: true

- tapOn:
    text: ".*\\.(jpg|png|mp4)$"
  optional: true

- waitForAnimationToEnd

# Action: Add to favorites
- tapOn:
    id: ".*favorite.*"
  optional: true

# Or via menu
- tapOn:
    id: ".*menu.*"
  optional: true

- tapOn:
    text: "Add to Favorites"
  optional: true

# Wait for action to complete
- waitForAnimationToEnd

# Verification: Check favorites tab
- backPress
- waitForAnimationToEnd

- tapOn:
    text: "Favorites"

- waitForAnimationToEnd

# Verify file appears in favorites
- assertVisible:
    text: ".*\\.(jpg|png|mp4)$"
  optional: true
  timeout: 5000

# Cleanup not needed - favorites can remain
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
