# TODO Features Specifications Index

**Created:** 2026-02-17  
**Purpose:** Index of all specifications created for TODO features

---

## Overview

This document indexes all development specifications created for valid TODO placeholders found in the codebase during the dead code analysis.

**Total TODOs analyzed:** 31  
**Specifications created:** 4

---

## Specification Files

### 1. Network Transfer Operations Completion
**File:** [NETWORK_TRANSFER_COMPLETION_SPEC.md](file:///c:/GIT/FastMediaSorter_mob_v2/dev/NETWORK_TRANSFER_COMPLETION_SPEC.md)  
**Priority:** High  
**TODOs covered:** 13  
**Estimated effort:** 30-35 hours

**Covers:**
- SFTP file transfer operations (copy, move, delete, check exists)
- FTP file transfer operations (copy, move, delete, check exists)
- Cross-server and cross-protocol transfers
- File access implementations

**Blocked by:** Network Credentials Resolution System

---

### 2. SMB Credentials Integration
**File:** [SMB_CREDENTIALS_INTEGRATION_SPEC.md](file:///c:/GIT/FastMediaSorter_mob_v2/dev/SMB_CREDENTIALS_INTEGRATION_SPEC.md)  
**Priority:** High (Blocker)  
**TODOs covered:** 8  
**Estimated effort:** 12-15 hours

**Covers:**
- Integrate NetworkCredentialsRepository with SmbTransferProvider
- Fetch credentials from database instead of hardcoded empty strings
- Add file size lookup before downloads
- Proper error handling for missing credentials

**Prerequisite for:** Network Transfer Operations

---

### 3. UI Features
**File:** [UI_FEATURES_SPEC.md](file:///c:/GIT/FastMediaSorter_mob_v2/dev/UI_FEATURES_SPEC.md)  
**Priority:** Medium  
**TODOs covered:** 5  
**Estimated effort:** Varies by feature

**Features:**
1. **Multiple File Rename** (8-12h) - Bulk rename with patterns
2. **Gesture Hint Overlay** (4-6h) - Onboarding for gestures
3. **PDF Editing Dialog** (20-30h) - Rotate, delete, merge pages
4. **Manual Cloud Sync** (6-8h) - Trigger sync on demand
5. **Surface Renderer Migration** (15-20h) - Performance improvement

---

### 4. Performance Optimizations
**File:** [PERFORMANCE_OPTIMIZATIONS_SPEC.md](file:///c:/GIT/FastMediaSorter_mob_v2/dev/PERFORMANCE_OPTIMIZATIONS_SPEC.md)  
**Priority:** Low (implement only if needed)  
**TODOs covered:** 5  
**Estimated effort:** Varies by optimization

**Optimizations:**
1. **FTP Native Pagination** (4-6h) - Reduce memory for large directories
2. **SMB EXIF/Metadata Extraction** (8-12h) - Show EXIF for network files
3. **Network Trash Cleanup** (15-20h) - If feature is planned
4. **SFTP Passphrase Support** (10-15h) - SSH key authentication

---

## Implementation Roadmap

### Phase 1: Foundation (HIGH PRIORITY)
**Goal:** Enable network file transfer operations

1. ✅ **SMB Credentials Integration** (12-15h)
   - Prerequisite for all network operations
   - Highest impact per effort ratio

2. ✅ **Network Transfer Completion** (30-35h)
   - Complete SFTP/FTP operations
   - Unblocks major functionality

**Total:** ~45-50 hours

---

### Phase 2: User Experience (MEDIUM PRIORITY)
**Goal:** Improve usability and convenience

1. **Manual Cloud Sync** (6-8h)
   - Users request this frequently
   - Quick to implement

2. **Multiple File Rename** (8-12h)
   - Requested by power users
   - Improves workflow

**Total:** ~15-20 hours

---

### Phase 3: Polish (LOW PRIORITY)
**Goal:** Nice-to-have enhancements

1. **Gesture Hints** (4-6h)
   - Quick win for onboarding
   - Low complexity

2. **FTP Pagination** (4-6h)
   - Only if users report slow browsing
   - Easy optimization

**Total:** ~8-12 hours

---

### Phase 4: Advanced Features (OPTIONAL)
**Goal:** Complex features for specific use cases

1. **PDF Editing** (20-30h)
   - Only if users request
   - High complexity

2. **SFTP Passphrase** (10-15h)
   - Only if users need SSH keys
   - Security enhancement

3. **SMB Metadata** (8-12h)
   - Only if users need EXIF
   - Performance tradeoff

**Total:** ~40-60 hours (implement selectively)

---

## Decision Tree

```
Start
  ↓
Are users requesting network file operations?
  ├─ YES → Implement Phase 1 (SMB + Network Transfer)
  └─ NO → Wait for user requests
       ↓
Are users reporting slow FTP browsing?
  ├─ YES → Implement FTP Pagination
  └─ NO → Skip optimization
       ↓
Are users requesting EXIF for network files?
  ├─ YES → Implement SMB Metadata
  └─ NO → Skip feature
       ↓
Continue with Phase 2/3 based on requests
```

---

## Questions for User Review

### 🔍 Requires Decision

1. **Multiple File Rename** - Still planned? (If YES → Phase 2)
2. **Network Trash** - Is this feature supported? (If YES → Create spec)
3. **Surface Renderer Migration** - Continue or abandon? (If CONTINUE → Phase 3)
4. **PDF Editing** - Users requesting this? (If YES → Phase 4)

### ✅ Recommended to Proceed

- SMB Credentials Integration (high priority)
- Network Transfer Completion (high priority)
- Manual Cloud Sync (user demand)

### ⌛ Recommended to Defer

- Performance optimizations (until needed)
- Advanced features (until requested)

---

## Total Effort Summary

| Phase | Priority | Effort | Status |
|-------|----------|--------|--------|
| Phase 1: Foundation | HIGH | 45-50h | Recommended |
| Phase 2: UX | MEDIUM | 15-20h | If requested |
| Phase 3: Polish | LOW | 8-12h | If needed |
| Phase 4: Advanced | OPTIONAL | 40-60h | Selective |

**Total possible:** ~110-140 hours  
**Recommended minimum:** ~45-50 hours (Phase 1 only)

---

## Related Documents

- [Dead Code Report](file:///C:/Users/serzh/.gemini/antigravity/brain/43196bc2-6ee6-4f58-8a1e-3db02b9f9c5b/dead_code_report.md) - Original analysis
- [TODO Audit](file:///C:/Users/serzh/.gemini/antigravity/brain/43196bc2-6ee6-4f58-8a1e-3db02b9f9c5b/todo_audit.md) - Categorized TODO list
- [Walkthrough](file:///C:/Users/serzh/.gemini/antigravity/brain/43196bc2-6ee6-4f58-8a1e-3db02b9f9c5b/walkthrough.md) - Dead code removal results
