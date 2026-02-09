# Utility Scripts

Development utilities and tools for FastMediaSorter v2.

## Testing

### Smoke & Critical Tests

```powershell
.\scripts\utils\run-maestro-smoke.ps1              # Run smoke tests
.\scripts\utils\run-maestro-smoke.ps1 -Suite critical  # Run critical tests
```

### Stress Tests

```powershell
.\scripts\utils\run-stress.ps1                     # All stress tests (with monitoring)
.\scripts\utils\run-stress.ps1 -Test monkey        # Only monkey (random taps)
.\scripts\utils\run-stress.ps1 -Test navigation    # Only rapid navigation
.\scripts\utils\run-stress.ps1 -Test lifecycle     # Only lifecycle torture
.\scripts\utils\run-stress.ps1 -NoMonitor          # Quick mode (no monitoring)

.\scripts\utils\run-maestro-stress.ps1 -Suite all -Monitor -Report  # Full stress with report
```

### Test Setup

```powershell
.\scripts\utils\setup_test_media.ps1               # Upload test media to device
.\scripts\utils\setup-avd-for-tests.ps1            # Configure AVD for Maestro
```

## Development Tools

### Run Configurations

```powershell
.\scripts\utils\create-run-configs.ps1             # Generate IDE run configs
```

### Source Control

```powershell
.\scripts\utils\commit-push.ps1                    # Interactive commit+push
```

### Documentation

```powershell
.\scripts\utils\update_docs_frontmatter.ps1        # Update doc metadata
```

### Test Assets

```powershell
.\scripts\utils\generate_test_images.py            # Generate test image files
```

### Installation

```powershell
.\scripts\utils\Install_release_on_adb_connected_device.ps1  # Install release APK
```

## Usage Notes

All utility scripts should be run from project root:

```powershell
cd c:\GIT\FastMediaSorter_mob_v2
.\scripts\utils\run-stress.ps1
```

## Test Suites

- **smoke**: Basic functionality (6 tests, ~10 min)
- **critical**: Core features (6 tests, ~7 min)
- **stress**: Stability under load (3 tests, ~15 min)

Test logs and reports are saved to `temp/`.
