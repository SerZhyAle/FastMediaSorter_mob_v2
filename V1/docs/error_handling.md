# Error Handling and Recovery in FastMediaSorter Mobile

## Types of Errors in the Application

### 1. Network Errors (SMB)

#### Connection Errors
- **Connection Timeout:** Server doesn't respond within 5 seconds
- **Invalid Credentials:** Authentication error
- **Server Unavailable:** Server not found on network
- **Network Issues:** Connection loss, DNS problems

**Handling:**
- Diagnostic dialog with detailed error description
- Suggestions for fixes (check network, credentials)
- Ability to copy error text for debugging

#### File Access Errors
- **Read Permission Denied:** Insufficient permissions for folder
- **Write Permission Denied:** Cannot create/modify files
- **File Locked:** File in use by another process

**Handling:**
- Permission check during connection testing
- Warnings before write operations
- Logging of permission issues

### 2. File System Errors

#### Local Files
- **Permission Missing:** No MediaStore access
- **File Not Found:** File deleted or moved
- **Corrupted File:** Cannot read/decode

**Handling:**
- Permission request on first launch
- Skip corrupted files with logging
- Recovery after access loss

#### Network Files
- **File Deleted:** File no longer exists on server
- **Permission Changed:** File became inaccessible
- **Network Failure:** Connection loss during operation

**Handling:**
- File list recheck before operations
- Graceful degradation on access loss
- Operation resumption after connection recovery

### 3. Media File Errors

#### Images
- **Unsupported Format:** File is not an image
- **Corrupted JPEG/PNG:** Decoding error
- **File Too Large:** Memory limit exceeded

**Handling:**
- MIME type check before loading
- Fallback to next file on error
- Size limits to prevent OOM

#### Videos
- **Unsupported Codec:** Format not supported by ExoPlayer
- **Corrupted File:** Parsing error
- **Network Buffer:** Insufficient speed for streaming

**Handling:**
- Video loading timeout (3 seconds)
- Fallback to image on video error
- Preloading to prevent lags

### 4. System Errors

#### Memory
- **OutOfMemoryError:** Memory limits exceeded
- **Bitmap Too Large:** Image doesn't fit in memory

**Handling:**
- Glide cache cleanup on memory shortage
- Image compression on loading
- Activity restart on critical errors

#### Storage
- **Insufficient Space:** No space for copy operations
- **SD Card Removed:** Storage device unavailable

**Handling:**
- Free space check before operations
- User warnings
- Operation cancellation on space shortage

### 5. User Input Errors

#### Connection Settings
- **Invalid Server Address:** Syntax error in path
- **Invalid Credential Format:** Empty fields

**Handling:**
- Real-time input validation
- Connection testing before saving
- Hints for correct format

#### Sorting Settings
- **Empty Target Folders:** No configured assignments
- **Name Duplicates:** Conflicts in folder names

**Handling:**
- Configuration check before sorting
- Warnings about potential issues
- Automatic conflict resolution

## Recovery Mechanisms

### Automatic Recovery
- **Reconnection:** Retry attempts on network failures
- **File Skipping:** Continue operation on individual file errors
- **State Recovery:** Progress saving on crashes

### Manual Recovery
- **Diagnostic Dialogs:** Detailed error information
- **Error Log:** Problem history viewing
- **Settings Reset:** Return to factory settings

### Error Prevention
- **Pre-check:** Testing before main operations
- **Data Validation:** Settings correctness check
- **Input Limits:** Prevention of incorrect values

## Logging and Diagnostics

### Logging Levels
- **Debug:** Detailed information for developers
- **Info:** Important events and states
- **Warning:** Potential problems
- **Error:** Critical errors requiring attention

### Diagnostic Information
- **App Version:** For compatibility
- **Android Version:** For specific issues
- **Device Model:** For hardware limitations
- **Error Time:** For pattern analysis

### Error Log
- **Storage:** In-app memory with size limit
- **Export:** Ability to send logs to developer
- **Cleanup:** Automatic old entries rotation

## Error User Interface

### Error Dialogs
- **Title:** Brief problem description
- **Description:** Details and possible causes
- **Actions:** Buttons for fixing or continuing
- **Copying:** Ability to copy text for support

### Visual Indicators
- **Colors:** Red for errors, yellow for warnings
- **Icons:** Standardized symbols for error types
- **Animations:** Smooth transitions on error appearance

### Feedback
- **Toast Messages:** Brief notifications
- **Snackbar:** Actions for error fixing
- **Progress Indicators:** Operation progress display

## Performance and Reliability

### Timeouts and Limits
- **Network Connection:** 5 seconds for test
- **Video Loading:** 3 seconds for parsing
- **File Operations:** 30 seconds for large file copying

### Limitations
- **Maximum Consecutive Errors:** 3-5 to prevent looping
- **Log Size:** Limit to prevent memory overflow
- **Retry Count:** 3 attempts for network operations

### Monitoring
- **Error Counters:** Statistics for problem analysis
- **Response Time:** Performance monitoring
- **Memory Usage:** Leak prevention</content>
<parameter name="filePath">c:\GIT\FastMediaSorter_mobile\docs\error_handling.md