# Atomic File Copy/Move Operation Specification

## Overview

This specification describes the implementation of atomic file copy and move operations for resource transfers. The goal is to prevent destination systems from prematurely detecting incomplete files during the transfer process.

## Problem Statement

**Current Behavior:**
- When files are copied or moved to a destination resource, they appear immediately on the destination filesystem
- Systems monitoring the destination folder may detect and attempt to process incomplete files
- This can lead to errors, data corruption, or failed processing in automated systems

**Desired Behavior:**
- Files should only become visible with their final name after the copy/move operation is complete
- During transfer, files should use a temporary naming convention
- This ensures destination systems only process complete, fully transferred files

## Use Case

When a destination resource has an automated system (file watcher, processor, etc.) monitoring for new files, those systems should not react until the file transfer is 100% complete. This prevents:
- Processing of partial/incomplete files
- Race conditions
- Data integrity issues
- Failed automated workflows

## Technical Requirements

### 1. Temporary File Naming Convention

**Format:** `(original_filename).(original_extension).temp_copy`

**Examples:**
- `photo.jpg` → `photo.jpg.temp_copy` (during copy) → `photo.jpg` (after completion)
- `video.mp4` → `video.mp4.temp_copy` (during copy) → `video.mp4` (after completion)
- `document.pdf` → `document.pdf.temp_copy` (during copy) → `document.pdf` (after completion)
- `archive.tar.gz` → `archive.tar.gz.temp_copy` (during copy) → `archive.tar.gz` (after completion)

**Edge Cases:**
- Files without extensions: `README` → `README.temp_copy` → `README`
- Files with multiple dots: `backup.2024.tar.gz` → `backup.2024.tar.gz.temp_copy` → `backup.2024.tar.gz`

## Development Stages & Phases

⚠️ **CRITICAL INSTRUCTION FOR DEVELOPER:**
For every phase and sub-task below, you MUST:
1.  **Build the project** to ensure no compilation errors.
2.  **Verify the specific change** works as expected (run the app or unit tests).
3.  **COMMIT your changes** to Git with a descriptive message (e.g., `feat: implemented atomic interface`, `test: verified ftp rename`).
**DO NOT accumulate changes.** Commit after each logical step.

### Phase 1: Foundation & Analysis
1.  **Analyze Existing Transfer Logic**
    - Identify entry points for Copy/Move in `MediaFileRepository` or similar classes.
    - Map out where `File` (Local), `SMB`, and `FTP` operations happen.
    - *Deliverable:* Code comments or mapping document.
    - **STEP:** Build & Commit.

2.  **Define Naming Strategy**
    - Create a helper class/function `TempFileStrategy` that takes an original filename and returns `(name).(ext).temp_copy`.
    - Write strict **Unit Tests** for this helper (include edge cases: no extension, multiple dots).
    - **STEP:** Build & Commit.

3.  **Create Abstraction Layer**
    - Define an `AtomicTransfer` interface (or abstract base class) to standardize the operation.
    - Ensure it supports `source`, `destination`, and `progressListener`.
    - **STEP:** Build & Commit.

### Phase 2: Core Implementation (Protocol by Protocol)
*Implement one protocol at a time to isolate issues.*

4.  **Implement Local Storage Support**
    - Implement the atomic logic for standard `java.io.File` / Android `DocumentFile`.
    - **Logic:** Copy to `*.temp_copy` → Validate → Rename to original.
    - Verify `File.renameTo()` behavior on Android 10+ (Scoped Storage).
    - **STEP:** Build & Commit.

5.  **Implement FTP Support**
    - Extend FTP client logic.
    - Use `STOR` to upload `*.temp_copy`.
    - Use `RNFR` (Rename From) / `RNTO` (Rename To) commands to finalize.
    - Handle connection stability during rename.
    - **STEP:** Build & Commit.

6.  **Implement SMB Support**
    - Extend SMB client logic.
    - Implement temporary file creation on SMB share.
    - Use SMB rename operation.
    - **STEP:** Build & Commit.

### Phase 3: Integration & UI

7.  **Service Layer Integration**
    - Swap the old "Direct Copy" calls with the new `AtomicTransfer` implementations in the main UseCases.
    - Ensure Progress Bars update correctly (0-99% during copy, 100% after rename).
    - **STEP:** Build & Commit.

8.  **User Settings**
    - Add a toggle in "General Settings": `[x] Safe Copy (Atomic Transfer)`.
    - Ensure the code reads this preference before starting transfers.
    - **STEP:** Build & Commit.

### Phase 4: Safety & Cleanup

9.  **Startup Cleanup Task**
    - Write a background job that runs on app start.
    - Scan known directories for orphaned files ending in `.temp_copy` (from crashed transfers).
    - Delete them (or log them if "Safe Mode" requires user approval).
    - **STEP:** Build & Commit.

10. **Error Handling Refinement**
    - Simulate failures (force close app mid-copy).
    - ensure no partial files remain named as "original".
    - **STEP:** Build & Commit.

### Phase 5: Final Verification

11. **E2E Testing**
    - Perform a massive copy (e.g., 100 photos) to FTP.
    - Perform a move of video files to SMB.
    - Verify filenames on destination *during* transfer (should see `.temp_copy`).
    - Verify filenames on destination *after* transfer (should be correct).
    - **STEP:** Build & Commit.

## Success Criteria

The implementation is considered successful when:

1. ✅ All copy operations create files with `.temp_copy` extension initially
2. ✅ Files are renamed to original name only after complete transfer
3. ✅ Move operations follow same atomic pattern (when crossing filesystems)
4. ✅ All supported protocols (Local, FTP, SMB, etc.) implement atomic transfer
5. ✅ Error handling properly cleans up temporary files
6. ✅ Orphaned `.temp_copy` files are detected and cleaned on startup
7. ✅ UI provides clear feedback during two-phase transfer
8. ✅ Feature can be enabled/disabled via settings
9. ✅ All tests pass (unit, integration, manual)
10. ✅ Documentation is complete and accurate

## Potential Risks and Mitigation

### Risk 1: Rename Operation Not Atomic on Some Protocols
**Mitigation:** Research and test rename atomicity for each protocol. Document limitations. Consider fallback strategies.

### Risk 2: Orphaned Temp Files After App Crash
**Mitigation:** Implement robust cleanup on startup. Consider periodic cleanup task.

### Risk 3: Performance Impact from Rename Operation
**Mitigation:** Rename is typically very fast. Measure performance. Optimize if needed.

### Risk 4: Breaking Changes to Existing Workflows
**Mitigation:** Make feature opt-in initially. Provide toggle in settings. Thorough testing before release.

### Risk 5: Filename Length Limits
**Mitigation:** Check platform limits for filename length. If `original.ext.temp_copy` exceeds limit, use alternative naming (e.g., `.tmp` or truncate original name).

## Future Enhancements

Potential improvements for future iterations:

1. **Checksums/Verification:** Add optional MD5/SHA hash verification after transfer
2. **Resume Support:** Allow resuming interrupted transfers
3. **Transaction Log:** Keep log of all transfers for audit/debugging
4. **Batch Optimization:** Special handling for multiple file transfers
5. **Compression:** Optional compression during transfer for large files

## References

- Android Storage Access Framework documentation
- FTP RFC 959 (File Transfer Protocol)
- SMB/CIFS protocol specifications
- Project architecture documents

---

**Document Version:** 1.1  
**Created:** 2026-02-10  
**Status:** Specification - Ready for Implementation
