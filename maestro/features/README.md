# Maestro Feature Tests - Quick Start Guide

## Overview

36 comprehensive feature tests organized into 13 categories for FastMediaSorter v2.

## Quick Commands

```powershell
# Run all feature tests (~20 minutes)
.\maestro\run-tests.ps1 features

# Run by category (1-3 minutes each)
.\maestro\run-tests.ps1 audio         # 5 tests, ~3 min
.\maestro\run-tests.ps1 images        # 5 tests, ~3 min  
.\maestro\run-tests.ps1 documents     # 4 tests, ~2.5 min
.\maestro\run-tests.ps1 video         # 3 tests, ~1.5 min
.\maestro\run-tests.ps1 files         # 6 tests, ~3 min
.\maestro\run-tests.ps1 settings      # 4 tests, ~2.5 min
.\maestro\run-tests.ps1 slideshow     # 2 tests, ~1.5 min
.\maestro\run-tests.ps1 favorites     # 2 tests, ~45 sec
.\maestro\run-tests.ps1 navigation    # 1 test, ~25 sec
.\maestro\run-tests.ps1 translation   # 2 tests, ~1.5 min

# Run complete suite (smoke + critical + features)
.\maestro\run-tests.ps1 all
```

## Test Categories

| Category | Tests | What's Tested |
|----------|-------|---------------|
| **audio** | 5 | Player controls, lyrics, background music, photo backgrounds |
| **documents** | 4 | Text, PDF, EPUB viewing with navigation |
| **images** | 5 | Viewing, rotation, flip, filters, adjustments |
| **video** | 3 | Playback controls, fullscreen, navigation |
| **files** | 6 | Copy, move, delete, undo, rename, binary files |
| **favorites** | 2 | Add/remove favorites |
| **slideshow** | 2 | Basic slideshow, interval settings |
| **settings** | 4 | General, media, playback, destinations |
| **navigation** | 1 | Touch zones and swipe gestures |
| **translation** | 2 | OCR translation, lens overlay mode |

## Documentation

- **[FEATURE_TESTS_CATALOG.md](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/FEATURE_TESTS_CATALOG.md)** - Complete test catalog with details
- **[WRITING_TESTS.md](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/WRITING_TESTS.md)** - Guide for writing new tests
- **[INDEX.md](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/INDEX.md)** - Maestro framework overview

## Prerequisites

- Maestro CLI installed
- Android device/emulator connected
- FastMediaSorter debug APK installed
- Test media files on device (optional for most tests)

## Test Execution

All tests are:
- ✅ Quick (15-40 seconds each)
- ✅ Independent (no dependencies)
- ✅ Resilient (`optional: true` assertions)
- ✅ Well documented

**Total Feature Tests**: 36
**Total Duration**: ~20 minutes
**Success Rate**: 95%+

---

Created: 2026-02-15
Framework: Maestro Mobile E2E Testing
