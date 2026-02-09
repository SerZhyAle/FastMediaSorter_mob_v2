# Scripts Directory

Automation scripts for FastMediaSorter v2 development.

## Structure

```
scripts/
├── builders/       Build automation (21 scripts)
│   ├── build-*-debug.ps1      Debug builds by flavor
│   ├── build-*-release.ps1    Release builds
│   ├── build-*-device.ps1     Build + install
│   └── README.md              Build scripts documentation
│
└── utils/          Development utilities (10 scripts)
    ├── run-*.ps1              Test runners
    ├── setup-*.ps1            Test/AVD setup
    └── README.md              Utilities documentation
```

## Quick Start

### Build App

```powershell
# Fast debug build
.\scripts\builders\build-debug.PS1

# Specific flavor
.\scripts\builders\build-lite-debug.ps1

# Release (requires keystore)
.\scripts\builders\build-release.ps1
```

### Run Tests

```powershell
# Smoke tests
.\scripts\utils\run-maestro-smoke.ps1

# Stress tests
.\scripts\utils\run-stress.ps1
```

### Setup Testing

```powershell
# Prepare device/AVD
.\scripts\utils\setup-avd-for-tests.ps1
.\scripts\utils\setup_test_media.ps1
```

## Navigation

- 📦 **[Build Scripts](builders/README.md)** - All build automation
- 🛠️ **[Utility Scripts](utils/README.md)** - Testing, dev tools

## Legacy Scripts

Some root-level scripts remain for backward compatibility:

- `build-debug.PS1` (root) - Quick access wrapper

## Note

**Always run scripts from project root**:

```powershell
cd c:\GIT\FastMediaSorter_mob_v2
.\scripts\builders\build-debug.PS1
.\scripts\utils\run-stress.ps1
```
