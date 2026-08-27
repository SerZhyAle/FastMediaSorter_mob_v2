# Google Play Store Release - Quick Reference

> Historical reference only. Do not use its build or upload commands for a current release; follow `docs/RELEASE_READINESS_STANDARD.md` and its linked operator checklist instead.

## 📦 Release Package Contents

### 1. Release Notes (What's New)
**Location**: `store_assets/whats_new*.txt`

- ✅ **English** (`whats_new.txt`) - Primary, under 500 chars
- ✅ **Russian** (`whats_new_ru.txt`) - Translated
- ✅ **Ukrainian** (`whats_new_uk.txt`) - Translated

**Key Message**: Critical bug fixes for file visibility and delete operations + stability improvements

### 2. Store Descriptions
**Location**: `play/listing/<locale>/` - `en-US`, `ru-RU`, `uk-UA`

- ✅ **title.txt** - under 30 chars
- ✅ **short_description.txt** - under 80 chars
- ✅ **full_description.txt** - under 4000 chars

Published by `scripts/release/publish-play-listing.ps1`; the contract is `play/listing/README.md`.
The former `store_assets/play_store_description_*.txt` set was retired in S1989 - it had drifted into
a second, media-player-sounding description, and an operator pasting it from here is how the Play
listing stopped reading as a file organizer.

### 3. Assets
**Location**: `store_assets/`

- ✅ **Feature Graphic** (`feature_graphic.png`)
- ✅ **Screenshots** (`screenshots/` folder)
- ⚠️ **Video** (`Screen_recording_*.webm`) - Optional, verify format compliance

### 4. Documentation
**Location**: `store_assets/`

- ✅ **Release Checklist** (`RELEASE_CHECKLIST_JAN2026.md`) - Complete deployment guide
- ✅ **Changelog** (`CHANGELOG_JAN2026.md`) - Technical details for developers

## 🚀 Quick Deploy Steps

### Step 1: Build Release APK
```powershell
cd c:\GIT\FastMediaSorter_mob_v2
.\build-release.ps1
```

### Step 2: Locate APK
```
app_v2/build/outputs/apk/release/app_v2-release.apk
```

### Step 3: Upload to Google Play Console
1. Login to https://play.google.com/console
2. Select **FastMediaSorter** app
3. Go to **Release** → **Production** → **Create new release**
4. Upload `app_v2-release.apk`

### Step 4: Copy Release Notes
Open `store_assets/whats_new.txt` and paste into Play Console:
```
🎉 January 2026 Update - Enhanced Reliability!

✅ Critical Bug Fixes:
• Fixed file visibility after copy/move operations
• Resolved empty folder scanning issue
• Fixed file deletion permission dialogs on Android 10-14
• Corrected duplicate file removal

🔧 Improvements:
• Enhanced resource scanning with automatic media type detection
• Improved file operation stability
• Better handling of scoped storage restrictions

🌍 Stability:
• General performance optimizations
• Enhanced error handling
```

### Step 5: Review & Submit
- Review all changes in Play Console
- Set rollout percentage (start with 20% for safety)
- Click **Review Release** → **Start rollout to Production**

## 📊 Post-Release Monitoring

### First 24 Hours
- [ ] Check crash rate in Play Console
- [ ] Monitor user reviews
- [ ] Track installation success rate
- [ ] Watch for ANR (Application Not Responding) reports

### First Week
- [ ] Review user feedback
- [ ] Check reported bugs
- [ ] Monitor performance metrics
- [ ] **Technical quality thresholds (enforced from February 2027)** - run section G of
      `PLAY_CONSOLE_CHECKLIST.md`. The four surfaces and where the figures are recorded are listed
      there and deliberately not repeated here, so there is one place to update when Play changes
      the thresholds.
- [ ] Plan hotfix if needed

## 🎯 This Release Fixes

### User-Facing Issues
1. **"Files don't appear after copying"** - ✅ FIXED
2. **"Empty folders after scanning"** - ✅ FIXED  
3. **"Delete permission dialog issues"** - ✅ FIXED
4. **"Files disappear randomly"** - ✅ FIXED

### Technical Improvements
- Automatic media type detection
- Better scoped storage handling
- Improved FileObserver coordination
- Enhanced error recovery

## 📱 Supported Platforms

**Minimum**: Android 9.0 (API 28)
**Target**: Android 14.0 (API 35)
**Tested On**:
- Android 10 (API 29) - Scoped Storage
- Android 11+ (API 30+) - Batch Permissions
- Android 12-14 (API 31-35) - Latest features

## 🔐 Signing Information

**Keystore**: Use release keystore from `.secrets/keystore.properties`
**Key Alias**: Configured in build script
**Signature**: v1 + v2 + v3 for maximum compatibility

## 📞 Support Contacts

**Issues**: Check GitHub Issues
**Documentation**: See README.md files
**Privacy Policy**: PRIVACY_POLICY.md
**Terms**: TERMS_OF_SERVICE.md

---

**Quick Status**: ✅ READY FOR RELEASE
**Critical Blockers**: NONE
**Recommended Action**: Deploy to 20% rollout, monitor for 24h, then 100%

**Generated**: January 22, 2026
**Next Review**: February 2026
