# Maestro Test Examples
# FastMediaSorter v2

This file contains example test patterns and common scenarios you can use as templates.

## Example 1: Simple Navigation Test

```yaml
appId: com.sza.fastmediasorter.debug
---
# Navigate through app tabs

- launchApp
- waitForAnimationToEnd

# Browse tab
- tapOn:
    text: "Browse"
- assertVisible:
    text: "Browse"

# Favorites tab
- tapOn:
    text: "Favorites"
- assertVisible:
    text: "Favorites"

# Settings tab
- tapOn:
    text: "Settings"
- assertVisible:
    text: "Settings"
```

## Example 2: File Selection Test

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test file selection and deselection

- launchApp
- tapOn:
    text: "Browse"
- waitForAnimationToEnd

# Long press to select file
- longPressOn:
    text: ".*\\.jpg$"
  duration: 1000
  optional: true

# Verify selection mode activated
- assertVisible:
    text: "1 selected"
  optional: true

# Tap another file
- tapOn:
    text: ".*\\.png$"
  optional: true

# Verify multiple selection
- assertVisible:
    text: "2 selected"
  optional: true

# Clear selection
- tapOn:
    id: ".*clear.*"
  optional: true
```

## Example 3: Search Functionality

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test search functionality

- launchApp
- tapOn:
    text: "Browse"

# Open search
- tapOn:
    id: ".*search.*"
- assertVisible:
    id: ".*search.*input.*"

# Type search query
- inputText: "vacation"

# Wait for results
- waitForAnimationToEnd

# Verify results appear
- assertVisible:
    text: "vacation.*"
  optional: true

# Clear search
- tapOn:
    id: ".*clear.*"
  optional: true
```

## Example 4: Settings Toggle Test

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test settings toggles

- launchApp
- tapOn:
    text: "Settings"

# Find dark mode toggle
- scrollUntilVisible:
    element:
      text: "Dark Mode"
  timeout: 5000

# Toggle dark mode
- tapOn:
    text: "Dark Mode"

# Verify UI changes (optional visual check)
- waitForAnimationToEnd

# Toggle back
- tapOn:
    text: "Dark Mode"

- waitForAnimationToEnd
```

## Example 5: Permission Handling

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test permission dialogs

- clearState  # Clear app data to trigger permissions

- launchApp

# Handle storage permission
- tapOn:
    text: "Allow"
  optional: true

# Handle notification permission (Android 13+)
- tapOn:
    text: "Allow"
  optional: true

# Handle media permission (Android 13+)
- tapOn:
    text: "Allow"
  optional: true

# Verify app launched successfully
- assertVisible:
    text: "Browse"
```

## Example 6: Network Resource Test

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test adding a network resource (SMB)

- launchApp
- tapOn:
    text: "Browse"

# Open add resource menu
- tapOn:
    id: ".*add.*"
  optional: true

# Select SMB
- tapOn:
    text: "SMB"
  optional: true

# Fill in host
- tapOn:
    id: ".*host.*"
- inputText: "192.168.1.100"

# Fill in username
- tapOn:
    id: ".*user.*"
- inputText: "testuser"

# Fill in password
- tapOn:
    id: ".*password.*"
- inputText: "testpass"

# Connect
- tapOn:
    text: "Connect"

# Wait for connection
- waitForAnimationToEnd

# Verify connection successful
- assertVisible:
    text: ".*192.168.1.100.*"
  optional: true
```

## Example 7: Slideshow Test

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test slideshow functionality

- launchApp
- tapOn:
    text: "Browse"

# Navigate to images folder
- scrollUntilVisible:
    element:
      text: "Pictures"
  optional: true

- tapOn:
    text: "Pictures"
  optional: true

- waitForAnimationToEnd

# Start slideshow
- tapOn:
    id: ".*slideshow.*"
  optional: true

# Or via menu
- tapOn:
    id: ".*menu.*"
  optional: true

- tapOn:
    text: "Slideshow"
  optional: true

# Wait for slideshow to run
- waitForAnimationToEnd

# Stop slideshow (back button)
- backPress
```

## Example 8: Error Handling

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test error handling with invalid input

- launchApp
- tapOn:
    text: "Browse"

# Try to add invalid network resource
- tapOn:
    id: ".*add.*"
  optional: true

- tapOn:
    text: "SMB"
  optional: true

# Invalid host
- tapOn:
    id: ".*host.*"
- inputText: "invalid_host"

# Try to connect
- tapOn:
    text: "Connect"

# Verify error message appears
- assertVisible:
    text: ".*[Ee]rror.*"
  timeout: 10000
  optional: true

# Close error dialog
- tapOn:
    text: "OK"
  optional: true

- backPress
```

## Example 9: Stress Test - Rapid Actions

```yaml
appId: com.sza.fastmediasorter.debug
---
# Stress test with rapid navigation

- launchApp

# Rapidly switch tabs
- tapOn:
    text: "Browse"
- tapOn:
    text: "Favorites"
- tapOn:
    text: "Browse"
- tapOn:
    text: "Settings"
- tapOn:
    text: "Browse"

# Verify app is still responsive
- assertVisible:
    text: "Browse"

# Rapid scrolling
- scroll
- scroll
- scroll

- swipe:
    direction: DOWN

# Verify no crashes
- assertVisible:
    text: "Browse"
```

## Example 10: Multi-File Operations

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test operations on multiple files

- launchApp
- tapOn:
    text: "Browse"
- waitForAnimationToEnd

# Select multiple files
- longPressOn:
    index: 0
  duration: 1000

- tapOn:
    index: 1

- tapOn:
    index: 2

# Verify selection count
- assertVisible:
    text: "3 selected"
  optional: true

# Open actions menu
- tapOn:
    id: ".*action.*"
  optional: true

# Select copy action
- tapOn:
    text: "Copy"
  optional: true

# Navigate to destination
- backPress
- tapOn:
    text: "Destination"
  optional: true

# Paste
- tapOn:
    id: ".*paste.*"
  optional: true

# Verify operation completed
- assertVisible:
    text: ".*copied.*"
  optional: true
```

## Best Practices

1. **Always use `optional: true`** for elements that might not appear in all scenarios
2. **Add `waitForAnimationToEnd`** after navigation or state changes
3. **Use regex patterns** for flexible text matching: `text: ".*\\.jpg$"`
4. **Set reasonable timeouts** for network operations: `timeout: 10000`
5. **Clear app state** when testing permissions: `clearState`
6. **Test error cases** not just happy paths
7. **Use descriptive test names** in comments
8. **Keep tests focused** - one feature per test file
9. **Use `assertVisible`** to verify expected state
10. **Handle device differences** with optional steps

## Running Examples

To run any example:

1. Copy the YAML content
2. Save as `maestro/custom/my_test.yaml`
3. Run with: `maestro test maestro/custom/my_test.yaml`

## Debugging Tips

- Use `maestro studio` for interactive testing
- Add `--debug` flag for verbose output
- Use `--record` to capture video of test execution
- Check `~/.maestro/tests/` for test logs
