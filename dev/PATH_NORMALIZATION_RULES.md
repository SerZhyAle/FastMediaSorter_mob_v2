# Path Normalization Rules for FastMediaSorter v2

**CRITICAL**: Follow these rules strictly to avoid double slashes and path errors.

## Universal Rule for Path Concatenation

**ALWAYS use `trimEnd('/')` before adding a filename:**

```kotlin
// ✅ CORRECT
val fullPath = "${basePath.trimEnd('/')}/$fileName"

// ❌ WRONG - creates double slashes when basePath ends with '/'
val fullPath = if (basePath.endsWith("/")) "$basePath$fileName" else "$basePath/$fileName"
```

## Protocol-Specific Path Formats

### SMB (Samba/CIFS)
**Format**: `smb://host:port/share/path/file.ext`

**Storage in Database**:
- Resource path (folder): `smb://192.168.1.100:445/test_media/`
- File path: `smb://192.168.1.100:445/test_media/file.jpg`

**Rules**:
1. Always include port (default: 445)
2. Share name is part of path (no separate field)
3. Paths are case-insensitive (Windows servers)
4. Use forward slashes `/` only (never backslashes)

**Examples**:
```kotlin
// Resource path from DB
val resourcePath = "smb://192.168.1.100:445/shared/"  // May or may not end with /

// Build file path
val filePath = "${resourcePath.trimEnd('/')}/$fileName"
// Result: smb://192.168.1.100:445/shared/file.jpg
```

### FTP (File Transfer Protocol)
**Format**: `ftp://host:port/path/file.ext`

**Storage in Database**:
- Resource path (folder): `ftp://193.178.50.43:21/`
- File path: `ftp://193.178.50.43:21/file.jpg`

**Rules**:
1. Always include port (default: 21)
2. Root directory is `/` (NOT empty string)
3. Paths are case-sensitive (Unix servers)
4. Use forward slashes `/` only
5. Special characters must be URL-encoded

**Examples**:
```kotlin
// FTP Root resource
val resourcePath = "ftp://193.178.50.43:21/"  // Root directory

// Build file path
val filePath = "${resourcePath.trimEnd('/')}/$fileName"
// Result: ftp://193.178.50.43:21/file.jpg (NOT ftp://...//file.jpg)

// Subdirectory
val resourcePath = "ftp://server.com:21/uploads"
val filePath = "${resourcePath.trimEnd('/')}/$fileName"
// Result: ftp://server.com:21/uploads/file.jpg
```

### SFTP (SSH File Transfer Protocol)
**Format**: `sftp://host:port/path/file.ext`

**Storage in Database**:
- Resource path (folder): `sftp://server.com:22/home/user/folder/`
- File path: `sftp://server.com:22/home/user/folder/file.jpg`

**Rules**:
1. Always include port (default: 22)
2. Absolute paths start with `/` (from filesystem root)
3. Relative paths are relative to user's home directory
4. Paths are case-sensitive (Unix servers)
5. Use forward slashes `/` only

**Examples**:
```kotlin
// SFTP absolute path
val resourcePath = "sftp://server:22/var/data/"

// Build file path
val filePath = "${resourcePath.trimEnd('/')}/$fileName"
// Result: sftp://server:22/var/data/file.jpg

// SFTP home-relative path
val resourcePath = "sftp://server:22/uploads"
val filePath = "${resourcePath.trimEnd('/')}/$fileName"
// Result: sftp://server:22/uploads/file.jpg
```

### Cloud Storage (Google Drive, OneDrive, Dropbox)
**Format**: `cloud://provider:resourceId/path`

**Storage in Database**:
- Resource path: `cloud://googledrive:1A2B3C4D5E/test_media`
- File path: `cloud://googledrive:1A2B3C4D5E/test_media/file.jpg`

**Rules**:
1. Provider name is lowercase
2. Resource ID identifies the cloud account/folder
3. Path separator is `/` (virtual, not filesystem)
4. No port numbers

**Examples**:
```kotlin
val resourcePath = "cloud://googledrive:ABC123/photos/"

val filePath = "${resourcePath.trimEnd('/')}/$fileName"
// Result: cloud://googledrive:ABC123/photos/file.jpg
```

### Local Storage (Android)
**Format**: `/storage/emulated/0/DCIM/file.jpg`

**Storage in Database**:
- Resource path: `/storage/emulated/0/DCIM`
- File path: `/storage/emulated/0/DCIM/IMG_001.jpg`

**Rules**:
1. Absolute paths only (start with `/`)
2. Use Android standard directories (DCIM, Pictures, Downloads, etc.)
3. Path separator is `/`
4. Case-sensitive (Linux kernel)

**Examples**:
```kotlin
val resourcePath = "/storage/emulated/0/DCIM"

val filePath = "${resourcePath.trimEnd('/')}/$fileName"
// Result: /storage/emulated/0/DCIM/IMG_001.jpg
```

## Common Pitfalls and Solutions

### ❌ PITFALL #1: Conditional concatenation
```kotlin
// WRONG - creates double slashes
val destFilePath = if (destinationPath.endsWith("/")) 
    "$destinationPath$fileName" 
else 
    "$destinationPath/$fileName"
```

**Why it fails**: When `destinationPath = "ftp://server:21/"`, this produces `"ftp://server:21/fileName"` which looks correct, BUT if the resource path was stored as `"ftp://server:21/"` (with trailing slash) in the database, and later you trim it and re-add slash, you get double slashes.

**✅ SOLUTION**: Always normalize
```kotlin
val destFilePath = "${destinationPath.trimEnd('/')}/$fileName"
```

### ❌ PITFALL #2: Multiple concatenations
```kotlin
// WRONG - each step might add extra slashes
val temp = "$basePath/$subfolder"
val final = "$temp/$fileName"  // Could be: path//subfolder/file
```

**✅ SOLUTION**: Normalize at each step
```kotlin
val temp = "${basePath.trimEnd('/')}/$subfolder"
val final = "${temp.trimEnd('/')}/$fileName"
```

### ❌ PITFALL #3: Extracting parent path
```kotlin
// WRONG - loses protocol information
val parent = File(fullPath).parent
```

**✅ SOLUTION**: Use string operations for network paths
```kotlin
fun getParentPath(path: String): String {
    if (!path.contains("://")) {
        return File(path).parent ?: "/"
    }
    // Network path: keep protocol
    val lastSlash = path.trimEnd('/').lastIndexOf('/')
    return if (lastSlash > path.indexOf("://") + 2) {
        path.substring(0, lastSlash)
    } else {
        path  // Already at root
    }
}
```

## Implementation Checklist

When working with file paths, ALWAYS:

1. ✅ Use `trimEnd('/')` before concatenating with `/` and filename
2. ✅ Store resource paths in database WITHOUT trailing slash preference (normalize on use)
3. ✅ Never use conditional `if (endsWith("/"))` logic for path building
4. ✅ Test with paths that end with `/` AND without `/`
5. ✅ Log full paths for debugging (check for `//` in logs)
6. ✅ Use protocol-aware path handling (don't use `File()` for network paths)

## Files That Handle Path Generation

**Must follow normalization rules**:

1. **UnifiedFileOperationHandler.kt** - `generateDestinationPath()` (Line ~368)
2. **SmbFileOperationHandler.kt** - Bridge copy paths (Line ~156)
3. **FtpFileOperationHandler.kt** - Destination paths (Line ~77)
4. **SftpFileOperationHandler.kt** - Destination paths (Line ~69, ~130)
5. **CloudFileOperationHandler.kt** - Cloud paths (Line ~184, ~318)
6. **LocalFileOperationHandler.kt** - Local paths (if any concatenation)

## Testing Protocol

Before committing path-related changes:

1. Create test resource with trailing slash: `ftp://server:21/`
2. Test file move/copy to this resource
3. Check logs for `//` (double slash)
4. Verify file appears in correct location
5. Test with subdirectories: `ftp://server:21/subfolder/`
6. Test all protocols: SMB, FTP, SFTP, Cloud, Local

## Emergency Fix Pattern

If you find double slashes in production:

```kotlin
// Quick fix: normalize the result
val path = generateSomePath(...)
val normalized = path.replace("//", "/")
    .replaceFirst(":/", "://")  // Restore protocol separator
```

**BUT**: Fix the root cause in path generation, not just symptoms!

---

**Last Updated**: January 19, 2026
**Applies to**: FastMediaSorter v2.60+

**Remember**: `trimEnd('/') + '/' + filename` is your friend! 🎯
