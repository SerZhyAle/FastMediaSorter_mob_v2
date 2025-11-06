# FastMediaSorter v2 🚀

Android application for quickly sorting media files (images, videos, audio) from local folders, network shares (SMB/SFTP), and cloud storage.

## 📋 About the Project

FastMediaSorter v2 is a complete rewrite of the original application using modern Android technologies and architectural patterns.

### Key Features

- ✅ **Clean Architecture** with separation into Domain, Data, and Presentation layers
- ✅ **Modern Stack**: Kotlin, Hilt, Room, Coroutines, Flow
- ✅ **Java 21** (LTS) - the latest version of the Java runtime
- ✅ **Material Design 2** for a user-friendly UI/UX
- ✅ **ExoPlayer** for media playback
- ✅ **Dark Theme Support**
- ✅ **Multilingual** (English, Russian, Ukrainian)

## 🏗️ Architecture

```
app_v2/
├── core/          # Base components (DI, UI base classes)
├── data/          # Data sources (DB, network, local storage)
├── domain/        # Business logic (models, use cases, repositories)
└── ui/            # Presentation layer (Activities, ViewModels, adapters)
```

### Technology Stack

- **Language**: Kotlin 1.9.22
- **Runtime**: Java 21 (LTS)
- **Build System**: Gradle 8.2.1
- **DI**: Hilt 2.50
- **Database**: Room 2.6.1
- **Async**: Kotlin Coroutines 1.7.3
- **Navigation**: AndroidX Navigation 2.7.6
- **Media**: ExoPlayer (Media3) 1.2.1
- **Image Loading**: Coil 2.5.0
- **Network**: SMBJ 0.12.1, SSHJ 0.37.0
- **Logging**: Timber 5.0.1

## 🚀 Quick Start

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 21
- Android SDK 34
- Minimum Android version: 9.0 (API 28)

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/FastMediaSorter_mob_v2.git
cd FastMediaSorter_mob_v2
```

2. Open the project in Android Studio

3. Sync Gradle:
```bash
./gradlew build
```

4. Run the application on an emulator or device

## 📱 Core Features

### Implemented ✅

- **Resource Management**: add, edit, delete resource folders
- **Media Browsing**: list/grid view of files with previews
- **Playback**: built-in player for images, videos, audio
- **Sorting**: by name, date, size, type
- **File Operations**: copy, move, rename, delete
- **Destinations**: up to 10 destination folders for quick sorting
- **Slideshow**: automatic media playback
- **Write Permission Indicator**: visual display for read-only folders

### In Development 🔨

- Multi-selection of files by range (long press)
- Filtering resources on the main screen
- Updating (rescan) resources
- Touch zones in Player Screen (9-zone scheme)
- Command Panel mode in Player
- Application settings
- Network resources (SMB, SFTP)
- Cloud storage (Google Drive, Dropbox)

### Planned 📋

- Testing (Unit, UI, Integration)
- Performance optimization
- Accessibility features
- CI/CD pipeline
- Publication to Google Play

## 📚 Documentation

- [V2 Specification](V2_Specification.md) - full v2 specification
- [Architecture Overview](V2_architecture_overview.md) - architecture overview
- [TODO](TODO_V2.md) - task list and progress
- [Changelog](CHANGELOG_SESSION.md) - change history

## 🔄 Migration from v1

The V1 application is located in the `V1/` folder and serves as a reference for functionality. Key differences in v2:

| Aspect       | V1           | V2                                |
|--------------|--------------|-----------------------------------|
| Architecture | Monolithic   | Clean Architecture                |
| DI           | Manual       | Hilt                              |
| Database     | Room (basic) | Room + Coroutines                 |
| Navigation   | Fragments    | Activities + Navigation Component |
| Java Version | 17           | 21 (LTS)                          |
| Build System | Gradle 7.x   | Gradle 8.2                        |

## 🤝 Contributing

The project is under active development. Contributions are welcome:

- 🐛 Bug reports
- 💡 Suggestions for improvement
- 📝 Documentation improvements
- ✨ Pull requests with new functionality

## 📄 License

This project is developed for personal use and learning.

## 👤 Author

**Serhii Zhyhunenko**
- Email: serzhyale@gmail.com
- GitHub: [@serzhyale](https://github.com/serzhyale)

## 📊 Project Status

**Milestone 2 (base functionality):** ✅ Completed
**Specification improvements:** 🔄 In progress (6/19 tasks)
**Additional features:** 📋 Planned (50+ tasks)

---

**Last updated:** 06.11.2025
**Version:** 2.0.0-alpha1
