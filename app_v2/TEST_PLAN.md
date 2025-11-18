# FastMediaSorter v2 - Test Coverage Plan

## Test Coverage Status: 0% → Target: 70%+

### Phase 1: Critical Unit Tests (Priority: HIGH) ✅ IN PROGRESS
**Estimated: 12-16 hours**

#### 1.1 Core UseCases
- [x] `CryptoHelperTest.kt` - Encryption/decryption validation
- [ ] `FileOperationUseCaseTest.kt` - Copy/move/delete with undo mechanism
- [ ] `GetMediaFilesUseCaseTest.kt` - Pagination, filtering, sorting
- [ ] `SmbOperationsUseCaseTest.kt` - Network file operations
- [ ] `NetworkImageEditUseCaseTest.kt` - Download→edit→upload workflow

#### 1.2 Repositories
- [ ] `ResourceRepositoryTest.kt` - CRUD operations for resources
- [ ] `NetworkCredentialsRepositoryTest.kt` - Encrypted credentials storage
- [ ] `MediaFileRepositoryTest.kt` - Media file caching logic

---

### Phase 2: Instrumented Tests (Priority: HIGH)
**Estimated: 8-10 hours**

#### 2.1 Room Database
- [ ] `ResourceDaoTest.kt` - Insert/update/delete/query operations
- [ ] `MediaFileDaoTest.kt` - Pagination queries, foreign key constraints
- [ ] `NetworkCredentialsDaoTest.kt` - Encrypted data persistence

#### 2.2 Dependency Injection
- [ ] `HiltTestRunner.kt` - Custom test runner configuration
- [ ] `HiltModulesTest.kt` - Verify all @Inject dependencies resolve correctly

---

### Phase 3: Additional Unit Tests (Priority: MEDIUM)
**Estimated: 10-12 hours**

#### 3.1 Media Processing UseCases
- [ ] `ExtractExifMetadataUseCaseTest.kt` - EXIF parsing edge cases
- [ ] `ExtractVideoMetadataUseCaseTest.kt` - Video metadata extraction
- [ ] `RotateImageUseCaseTest.kt` - Image rotation operations
- [ ] `FlipImageUseCaseTest.kt` - Image flip operations

#### 3.2 Resource Management
- [ ] `AddResourceUseCaseTest.kt` - Resource creation validation
- [ ] `UpdateResourceUseCaseTest.kt` - Resource update logic
- [ ] `DeleteResourceUseCaseTest.kt` - Cascade delete behavior
- [ ] `GetDestinationsUseCaseTest.kt` - Filter destinations (max 10)

---

### Phase 4: Integration Tests (Priority: MEDIUM)
**Estimated: 12-15 hours**

#### 4.1 Network Protocol Tests
- [ ] `SmbClientIntegrationTest.kt` - Real SMB connection (TestContainer/Mock server)
- [ ] `SftpClientIntegrationTest.kt` - SFTP connection lifecycle
- [ ] `FtpClientIntegrationTest.kt` - FTP passive mode operations
- [ ] `GoogleDriveClientIntegrationTest.kt` - OAuth + file listing

#### 4.2 File Operation Handlers
- [ ] `LocalFileOperationHandlerTest.kt` - Local filesystem operations
- [ ] `SmbFileOperationHandlerTest.kt` - Network copy/move with progress
- [ ] `CloudFileOperationHandlerTest.kt` - Cloud upload/download

---

### Phase 5: UI Tests (Priority: LOW)
**Estimated: 8-10 hours**

#### 5.1 Fragment/Activity Tests
- [ ] `BrowseFragmentTest.kt` - Grid/list mode switching, pagination scroll
- [ ] `PlayerActivityTest.kt` - Fullscreen image/video playback
- [ ] `ResourceManagementActivityTest.kt` - Add/edit/delete resources

#### 5.2 RecyclerView Performance
- [ ] `MediaFileAdapterTest.kt` - 1000+ items with smooth scrolling
- [ ] `PagingMediaFileAdapterTest.kt` - Paging3 integration

---

## Testing Tools & Frameworks

### Unit Tests (JVM)
- **Framework**: JUnit 4.13.2
- **Mocking**: MockK 1.13.9 (Kotlin-native)
- **Coroutines**: kotlinx-coroutines-test 1.7.3
- **LiveData/Flow**: androidx.arch.core:core-testing 2.2.0

### Instrumented Tests (Android)
- **Framework**: AndroidX Test + JUnit 1.1.5
- **UI Testing**: Espresso 3.5.1
- **DI Testing**: Hilt Testing 2.50
- **Database**: Room in-memory testing

---

## Test Execution Commands

```bash
# Run all unit tests
.\gradlew.bat :app_v2:test

# Run specific test class
.\gradlew.bat :app_v2:test --tests "CryptoHelperTest"

# Run all instrumented tests (requires connected device/emulator)
.\gradlew.bat :app_v2:connectedAndroidTest

# Generate test coverage report
.\gradlew.bat :app_v2:testDebugUnitTestCoverage
```

---

## Code Coverage Targets

| Component | Target Coverage | Current |
|-----------|----------------|---------|
| UseCases | 80%+ | 0% |
| Repositories | 70%+ | 0% |
| ViewModels | 60%+ | 0% |
| File Handlers | 75%+ | 0% |
| Utility Classes | 85%+ | 0% |
| **Overall** | **70%+** | **0%** |

---

## Testing Best Practices

1. **AAA Pattern**: Arrange → Act → Assert
2. **Isolation**: Mock all external dependencies (Room, Network, Android APIs)
3. **Coroutines**: Use `runTest` (formerly `runBlockingTest`) for suspend functions
4. **LiveData/Flow**: Use `observeForTesting()` extension for synchronous observation
5. **Deterministic**: Avoid `Thread.sleep()`, use `advanceUntilIdle()` in coroutine tests
6. **Naming**: `functionName_condition_expectedResult` (e.g., `copyFile_whenSourceExists_returnsSuccess`)

---

## Continuous Integration

- **Pre-commit Hook**: Run unit tests before allowing commit
- **CI Pipeline**: GitHub Actions to run all tests on PR
- **Code Review**: Require tests for new features (min 70% coverage)

---

**Last Updated**: 2025-11-18
**Status**: Phase 1 in progress (CryptoHelper completed)
