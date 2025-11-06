# SMB Protocol Integration in FastMediaSorter Mobile

## SMB Integration Overview

The application uses **SMB/CIFS** protocol for accessing network file resources (NAS, Samba shares). Implementation is based on **jcifs-ng** library - modern Java implementation of SMB protocol.

## SMB Components Architecture

```
SMB Server
    │
    ▼
SmbClient (jcifs-ng)
    │
    ▼
SmbDataSource (ExoPlayer)
    │
    ▼
ImageRepository / SortRepository
    │
    ▼
UI Components
```

## Key Components

### SmbClient
Main class for SMB connection operations.

```kotlin
class SmbClient {
    // Connection and authentication
    fun connect(server: String, username: String, password: String): Boolean
    
    // Getting file list
    fun getImageFiles(server: String, path: String, recursive: Boolean, limit: Int): SmbResult
    
    // File operations
    fun copyFile(source: String, destination: String): Boolean
    fun moveFile(source: String, destination: String): Boolean
    fun deleteFile(path: String): Boolean
    
    // Permission checking
    fun checkWritePermission(server: String, path: String): Boolean
}
```

### SmbDataSource
Custom DataSource implementation for ExoPlayer, allowing streaming video playback over SMB.

```kotlin
class SmbDataSource : DataSource {
    override fun open(dataSpec: DataSpec): Long
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int
    override fun getUri(): Uri?
    override fun close()
}
```

### SmbDataSourceFactory
Factory for creating SmbDataSource instances.

```kotlin
class SmbDataSourceFactory(
    private val smbClient: SmbClient,
    private val config: ConnectionConfig
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource(smbClient, config)
}
```

## Connection Process

### 1. Authentication
```kotlin
val context = CIFSContext()
val credentials = NtlmPasswordAuthenticator(domain, username, password)
val session = context.getSession(serverAddress, credentials)
```

**Supported authentication methods:**
- NTLMv2 (recommended)
- NTLMv1 (for compatibility)
- Kerberos (via SPNEGO)

### 2. File System Navigation
```kotlin
val root = SmbFile("smb://server/share/", context)
val files = root.listFiles { file ->
    // Media file filtering
    isMediaFile(file.name)
}
```

### 3. File Reading
```kotlin
val smbFile = SmbFile("smb://server/share/photo.jpg", context)
val inputStream = smbFile.inputStream
// Reading data for Glide/ExoPlayer
```

## Supported SMB Capabilities

### Protocol Versions
- **SMB 2.0+** (recommended)
- **SMB 1.0** (for legacy servers)
- **CIFS** (legacy support)

### Security Features
- **Encryption:** Connection encryption support
- **Signing:** Digital packet signatures
- **NTLM Authentication:** User authentication

### File Operations
- **Reading:** Images, videos, metadata
- **Writing:** Copying, moving, deleting
- **Directory Creation:** For sorting target directories
- **Permission Checking:** Read/write for each path

## Performance Optimizations

### Connection Pooling
- SMB session reuse
- Avoiding re-authentication
- Connection timeout management

### Preloading
- Asynchronous next file loading
- File metadata caching
- Optimization for large lists

### Memory Management
- Streaming for large files
- Buffer size limits
- Automatic resource cleanup

## Error Handling

### Network Errors
- **Connection Timeout:** 5 seconds for connection
- **Authentication Failed:** Invalid credentials
- **Server Unreachable:** Server unavailable
- **Network Interrupt:** Connection loss

### File System Errors
- **Access Denied:** Insufficient permissions
- **File Not Found:** File deleted or moved
- **Disk Full:** No disk space
- **File Locked:** File used by another process

### Diagnostics
```kotlin
data class SmbResult(
    val files: List<String> = emptyList(),
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val hasWritePermission: Boolean = false
)
```

## Security

### Data Encryption
- **Transport Encryption:** SMB 3.0+ encryption
- **Password Storage:** Encryption in EncryptedSharedPreferences
- **Certificate Validation:** For secure connections

### Authentication
- **Secure Storage:** Protected credential storage
- **Session Management:** Limited session lifetime
- **Permission Checks:** Permission validation before operations

## Compatibility

### Supported Servers
- **Windows SMB shares**
- **Samba servers**
- **NAS devices (QNAP, Synology, etc.)**
- **macOS file sharing**

### Limitations
- **Anonymous access:** Not supported (authentication required)
- **Large files:** File size limitations
- **Special characters:** Unicode path issues

## Monitoring and Debugging

### Logging
- **Connection Events:** Connect/disconnect
- **File Operations:** Read/write operations
- **Error Details:** Detailed error information

### Diagnostic Tools
- **Connection Test:** Server availability check
- **Permission Check:** Access rights validation
- **Performance Metrics:** Operation times

## Future Improvements

### Extended Support
- **SMB 3.1.1:** Latest protocol features
- **Multi-channel:** Parallel connections
- **Compression:** Data compression

### Additional Features
- **File Watching:** Change monitoring
- **Background Sync:** Background synchronization
- **Cloud Integration:** Cloud storage support via SMB</content>
<parameter name="filePath">c:\GIT\FastMediaSorter_mobile\docs\smb_integration.md