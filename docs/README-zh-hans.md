---
layout: default
title: "FastMediaSorter v2"
permalink: /docs/README-zh-hans.html
---
<div lang="zh-Hans" dir="ltr" markdown="1">

# FastMediaSorter v2 🚀

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple?style=flat-square&logo=kotlin)
![Android](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)
![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square&logo=apache)

{% include lang-switcher.html doc="README" dir="/docs/" current="zh-Hans" %}

**📦 下载：** [<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="56">](https://apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter)

直接安装 APK？Android 会对它未见过的安装包发出警告 - [了解警告为什么出现、该点什么](INSTALL_TRUST.md)。

**📘 用户文档：**[覆盖每个功能的分步指南，支持搜索](https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/)

## 关于项目

**FastMediaSorter v2** 是一款面向 Android 设备的完整外壳。它接管主屏幕、播放你的媒体、打开直播流、启动你的应用、与手表通信、监控设备状态，并管理你拥有的每一个文件 - 无论是在本地文件夹、网络驱动器（SMB、SFTP、FTP），还是云存储（Google Drive、OneDrive、Dropbox）中。

它建立在八大支柱之上：设备外壳、媒体播放器、直播流、应用启动、替代系统自带应用、手表伴侣、设备监控，以及完整的文件管理器。整理来自所有这些来源的文件正是这款应用的起点，也依然是其余一切的基石 - 但它已不再是应用的全部。

本手册目前采用与 [FEATURES.md](FEATURES.md) 中权威功能清单，以及 [DOCS_MAP.md](DOCS_MAP.md) 中文档地图相同的公开术语体系。请以这两个页面作为应用故事、可用版本和当前功能范围的最新权威来源。

## Windows 版本 🖥️

在寻找桌面解决方案？看看 **Fast Media Sorter for Windows**（前身为 FastMediaSorter LITE）- 一款轻量级的 Windows Forms 应用程序，用于快速整理、查看和管理图片与视频文件：

🔗 **[Fast Media Sorter for Windows](https://github.com/SerZhyAle/FastMediaSorter_Lite)**

📄 [如何将电脑文件夹发布到 Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html) - 通过 SFTP 将你电脑上的文件夹与应用共享（在手机上使用伴侣导入 / 扫描二维码）。

功能包括：

- 在包含大量图片和视频的文件夹中快速浏览
- 幻灯片放映与随机文件查看模式
- 最近文件和文件夹追踪
- 文件操作：移动、复制、重命名和删除
- 用于快速浏览的图像面板
- 可自定义的键盘快捷键，提升操作效率
- 多语言支持（英语/俄语）
- 支持 Windows 7/10/11，需要 .NET Framework 4.8

## 目录

- [下载](#download-)
- [版本](#editions-)
- [核心功能](#key-features)
- [支持的媒体格式](#supported-media-formats-)
- [截图](#screenshots-)
- [使用场景](#usage-scenarios-)
- [文档](#documentation-)
- [Wear OS 伴侣](#wear-os-companion-)
- [构建说明](#build-instructions)
- [测试](#testing-)
- [快速上手指南](#first-steps-quick-usage-guide-)
- [技术栈](#technology-stack)

## 版本 🎯 {#editions-}

FastMediaSorter v2 提供**七个版本** - 面向日常手机和平板的五个版本（Standard、Lite、Photos、Legacy、FOSS），再加上两个头显与旁加载版本 VR 和 noLegal。权威的功能对照表由构建自动生成，见 [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md)：

| 版本 | 说明 | 备注 |
|--------|-------------|-------|
| **Standard** | 全功能版本 | 涵盖媒体、文档、OCR、翻译和云访问的最广泛功能集 |
| **Lite** | 轻量版本 | 仅支持本地文件 - 视频、音频和图片；不支持网络来源、云、文档或 Streams |
| **Photos** | 以照片为核心的版本 | 仅支持图片，配合 SMB/FTP/SFTP 和云；不支持视频和音频 |
| **Legacy** | 面向兼容性的版本 | 功能集与 Standard 相同，包括 SMB/FTP/SFTP 和云（Google Drive、Dropbox、OneDrive）；面向 Android 6/7（API 23+）构建 |
| **FOSS** | F-Droid 目录版本 | 不含任何专有 SDK：本地媒体、文档、EPUB 以及 SMB/FTP/SFTP；不支持云、Streams、OCR、翻译、投屏和 Wear OS 伴侣 |
| **VR** | 商店合规的头显版本 | 面向头显的完整媒体功能集；不支持 Google Cast 和 Wear OS 伴侣 |
| **noLegal** | 旁加载版本 | 包含 Standard 的全部功能，外加 OpenXR 沉浸式播放器和仅限旁加载的附加功能 |

### 我该下载哪个版本？

- **Standard** ⭐ **（推荐）**：适合大多数用户的默认最佳选择
- **Lite**：想要更轻量的安装包和更简单的设置时优先选择
- **Photos**：适合以照片为主的使用流程
- **Legacy**：适用于 Android 6/7 设备（API 23+）- 包含网络和云功能
- **FOSS**：想要一个不含专有 SDK 的构建时，从 F-Droid 目录中选择它
- **VR**：适用于 XR 头显 - 不含 Cast 和 Wear 支持的商店版本
- **noLegal**：仅限旁加载 - 需要 OpenXR 沉浸式播放器时选择它

如需查看各版本的确切功能可用性，请参阅权威文档：

- [功能清单（权威版）](FEATURES.md)
- [操作指南（功能可用性表）](HOW_TO-zh-hans.md)
- [快速上手（版本选择器）](QUICK_START-zh-hans.md)
- [程序限制](LIMITATIONS.md)

> 🧭 **首次启动：** 就在语言选择器下方，应用会让你选择一个**设备档案**（手机、平板、电视、车载、相框、VR 等），为你定制起始默认设置 - 之后随时可在设置中更改。参见[首次启动：选择你的设备档案](QUICK_START-zh-hans.md#first-launch-choose-your-device-profile-30-seconds-)。

## 下载 📥 {#download-}

📲 **[在 Google Play 上获取](https://play.google.com/store/apps/details?id=com.sza.fastmediasorter)**

**编译好的 APK 文件不存放在此 GitHub 仓库中。** 所有构建版本均可在 **Google Drive** 上获取：

🔗 **[从 Google Drive 下载所有构建版本](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)**

| 版本 | 文件名 | 说明 |
|--------|-----------|-------------|
| **Standard** | `FastMediaSorter_standard_release.zip` | 全部功能（云、OCR、EPUB、翻译） |
| **Lite** | `FastMediaSorter_lite_release.zip` | 仅本地媒体（视频、音频、图片；不支持网络、云、文档或 Streams） |
| **Photos** | `FastMediaSorter_photos_release.zip` | 仅图片，支持网络（SMB/FTP/SFTP）和云 |
| **Legacy** | `FastMediaSorter_legacy_release.zip` | 功能与 Standard 相同，包括网络（SMB/FTP/SFTP）和云；适用于 Android 6/7（API 23+） |

> **注意：** 所有构建版本在编译成功后都会自动上传到 Google Drive。
>
> 🔐 **ZIP 密码：`1`**（APK 文件被打包为带密码保护的 ZIP 压缩包，以绕过 Google Drive 的限制）

## 截图 📱 {#screenshots-}

| Main Screen | File Actions | Settings |
|:-----------:|:------------:|:--------:|
| <a href="images/Screenshot_20251109_000251.png"><img src="images/Screenshot_20251109_000251.png" width="200"></a> | <a href="images/Screenshot_20251109_000314.png"><img src="images/Screenshot_20251109_000314.png" width="200"></a> | <a href="images/Screenshot_20251109_000323.png"><img src="images/Screenshot_20251109_000323.png" width="200"></a> |
| **播放器界面** | | |
| <a href="images/Screenshot_20251114_184930.png"><img src="images/Screenshot_20251114_184930.png" width="200"></a> | | |

完整尺寸图片：

- [主界面](images/Screenshot_20251109_000251.png)
- [文件操作](images/Screenshot_20251109_000314.png)
- [设置](images/Screenshot_20251109_000323.png)
- [播放器界面](images/Screenshot_20251114_184930.png)

## 核心功能 {#key-features}

- 🗂️ **统一界面：** 在一个窗口中查看和管理来自所有来源的文件。
- ⚡ **快速分类：** 一键将文件复制或移动到预先配置好的目标文件夹。
- ⭐ **收藏系统：** 将重要文件标记为收藏，并通过一个汇总所有来源收藏内容的专属标签页快速访问。
- 🔒 **PIN 保护：** 为单个资源设置访问 PIN 码，防止未经授权的浏览和编辑。
- ⚙️ **按资源单独配置：** 为每个文件夹单独自定义幻灯片间隔、扫描深度（子目录）和缩略图生成方式。
- 🧭 **设备档案设置：** 首次运行时可为手机、平板、电视/机顶盒、车载主机、媒体播放器、相框、音频播放器、电子书阅读器、VR 头显或自定义默认设置选择一个档案；应用会应用相应的安全、屏幕、内容和命令优先级默认值。
- 📋 **预设智能资源：** 内置虚拟资源 - **All Music**、**All Videos**、**All Photos** - 无需任何配置即可汇总整个设备上的媒体。无需手动逐个添加文件夹，即可立即访问完整的媒体库。
- 🖥️ **网络与云支持：** 处理网络驱动器（SMB，支持自动网络扫描）、SFTP 服务器、FTP 以及云存储（Google Drive、Dropbox、OneDrive）上的文件。
- 🖼️ **灵活的查看方式：** 以可自定义的网格或详细列表形式显示文件，并为大型合集（1000+ 文件）提供分页支持。
- ▶️ **内置播放器：** 无需离开应用即可播放视频和音频、查看图片和 GIF。支持幻灯片放映和全屏缩放。
- 🧩 **默认播放器集成：** 可选的播放开关让 FastMediaSorter 充当系统的媒体处理程序，响应打开/分享意图（ACTION_VIEW / ACTION_SEND），并将硬件媒体按键的唤醒事件转发给音频播放服务。
- 🗣️ **助手 AppFunctions（Android 16+）：** 应用声明了可被助手调用的操作 - 搜索你的媒体、打开一个文件，或打开一个电脑文件夹 - 只需询问，设备的系统助手就能找到并打开你的内容。
- 🎛️ **硬件按键支持：** 方向盘控制、耳机按键和物理媒体键（播放/暂停、下一首、上一首）均通过后台音频服务得到完整支持 - 无需触碰屏幕。
- 📻 **网络直播流（Streams 界面）：** 直接在专属的 Streams 界面播放网络电台（http/https，支持 ICY 正在播放信息的 Icecast/Shoutcast）、HLS/DASH 流和 RTSP 来源。可手动添加网址、导入 `.m3u` 播放列表，或下载 FastMediaSorter 精选目录。可将收藏置顶；按分类和语言筛选。内嵌音频播放：电台通过底部常驻迷你控制条在列表中播放，列表本身仍可滚动。视频和 RTSP 在全屏播放器中打开。适用于 Standard、Legacy、VR 和 noLegal；Lite 和 Photos 中不提供。
- 🎵 **歌词支持：** 查看当前播放曲目的歌词。使用 `api.lyrics.ovh` 根据元数据（艺术家/标题）自动搜索，若失败则回退到解析文件名。
- 🎶 **幻灯片背景音乐：** 在图片幻灯片放映期间播放背景音乐。可选择任意音频资源作为音乐来源，支持随机播放曲目、音量控制和曲目名称显示。点按曲目名称即可跳到另一首随机曲目。与网络和云文件无缝配合。
- ✏️ **图片编辑：** 旋转、翻转、应用滤镜（灰度、复古棕褐、负片），调整亮度/对比度/饱和度 - 本地和网络文件均适用。
- 🗂️ **二进制文件支持：** 查看和管理二进制文件（ZIP、RAR、APK、ISO、EXE、DLL 等），生成显示文件扩展名的缩略图。右键菜单支持分享/打开方式/复制/移动/重命名/删除。仅在 "All Files mode" 中可用。
- ⌨️ **键盘、鼠标与手柄：** 在所有界面 - 浏览、播放器、设置、对话框 - 全面支持键盘、鼠标和手柄输入。可通过 设置 → 管理 → 控制与按键绑定 完全重新映射；在任意界面按 F1 可查看该界面的按键帮助浮层。支持方向键（D-pad）列表导航；鼠标支持右键菜单和悬停效果。
- 🔍 **排序与筛选：** 按名称、日期、大小和时长对文件排序。应用筛选条件以快速查找。支持隐藏文件（以 `.` 开头），并提供专门的开关。
- ↩️ **撤销与回收站：** 可撤销上一步操作（复制、移动、删除），删除采用软删除方式移至 `.trash/` 文件夹。为各资源提供 "Empty Trash" 功能。
- 🎨 **现代化界面：** 支持浅色和深色主题、直观的操作控件，采用 Material Design 3。
- 💾 **智能缓存：** 分两阶段加载视频元数据（初始 1MB，扩展 5MB），并提供可配置的缩略图缓存（默认 2GB，最高可达 16GB）。
- 📄 **文档查看器：** 内置文本文件（.txt、.md、.log、.json、.xml）和 PDF 文档查看器，支持缩放、平移和手势导航。
- 📚 **EPUB 电子书阅读器：** 原生 EPUB 阅读器，支持章节导航、目录、字体大小控制、书内搜索以及深色/浅色主题。可处理本地和网络文件。
- 📥 **下载并打开：** 将网络文件（SMB/SFTP/FTP）下载到本地存储，并在外部应用中打开，同时提供进度跟踪。
- 🌐 **自动翻译：** 完全在设备本地即时翻译来自图片、PDF 和文本文件的文字：**Tesseract** 识别拉丁字母和西里尔字母的文本，再由 Google ML Kit 进行翻译。支持标准模式和**镜头式叠加模式**的原位翻译。
- 📱 **小组件支持：** 十余种主屏幕小组件，覆盖广泛场景 - 资源快捷方式、媒体播放器、相机拍摄、计算器、计划任务、收藏、迷你游戏等等。可在启动器的小组件选择器中浏览完整列表。
- 🏠 **主屏幕模式：** 让应用成为设备的主屏幕（Standard 和 noLegal 版本）：拥有自己的桌面，其中的资源快捷方式可直接打开浏览、幻灯片放映或播放，还有可调整大小的时钟和天气等小组件、无需通讯录权限的联系人图格、应用网格和任务栏。随时可以关闭，Android 会恢复你之前的主屏幕。
- ⏰ **计划文件操作：** 使用基于时间的规则自动执行文件操作（复制/移动/删除），支持灵活的筛选条件和后台执行。
- 👆 **高级手势：** 面向图片的智能缩放控制（2x/3x/4x），以及用于文件导航的直观触控区域。
- 📸 **保存画面：** 将当前视频画面截取为 PNG 或 JPG 快照，并保存到任意已配置的资源 - 本地或网络。输出格式和目标资源在视频设置中配置。
- 🖨️ **打印：** 直接从内置播放器将文档（PDF、TXT）和图片发送到打印机。网络和云文件会先在本地缓存再打印。
- ⬇️ **流媒体缓存下载：** 在播放前或播放中将网络文件下载到本地缓存，并显示实时进度对话框。之后可选择清理提示以回收存储空间。
- 🔊 **DTS/DTS-HD 音频：** DTS 和 DTS-HD 音轨通过定制的 FFmpeg 构建以软件方式解码 - 无需特殊硬件。
- 🎨 **视频色彩与亮度：** 使用 Media3 GPU 效果实时调整色相和亮度。设置在本次会话中的各个视频文件之间保持不变。
- 📤 **分享到 FastMediaSorter：** 通过标准的 Android 分享面板接收来自任意应用的文件，一键复制到选定的资源。
- 📷 **浏览界面中的相机拍摄：** 使用设备相机拍照，直接保存到当前资源 - 本地或网络 - 无需离开应用。
- 🔗 **链接自动下载：** 通过 Android 分享面板将任意 http(s) 链接分享给应用；媒体文件会被自动下载并保存到选定的资源。
- 👁️ **单眼 3D 模式：** 将立体内容（SBS/OU）裁剪为单眼画面，便于在平面屏幕上舒适观看；视频和图片均适用。
- 📲 **屏幕截取与录制：** 左侧边缘手势条支持截屏、快速拍照、裁剪分享，以及屏幕/语音/视频录制，全程无需离开当前文件。
- 📊 **使用统计（可选开启）：** 本地仪表盘显示已整理的文件数、释放的空间和播放时长 - 除非你主动导出，否则数据不会离开设备。
- 🧹 **重复文件查找与大小清理：** 基于内容的重复文件扫描（大小、快速哈希、SHA-256），支持手动或自动删除，另附按大小清理功能。

## 支持的媒体格式 🎞️ {#supported-media-formats-}

FastMediaSorter v2 支持广泛的格式：

- **图片：** JPG、JPEG、PNG、GIF、BMP、WEBP、HEIC、HEIF
- **视频：** MP4、MKV、MOV、WMV、FLV、WEBM、M4V、3GP、MPG、MPEG
- **音频：** MP3、FLAC、AAC、OGG、M4A、WMA、OPUS、DTS、DTS-HD
- **文档：** TXT、MD、LOG、JSON、XML、PDF、**EPUB**
- **二进制文件**（All Files mode）：ZIP、RAR、7z、TAR、GZ、ISO、DMG、IMG、APK、EXE、DLL、SO，以及其他 60 余种格式

## 使用场景 💡 {#usage-scenarios-}

以下是 FastMediaSorter v2 可以帮到你的几种方式：

### 1. 📸 整理相机照片

连接你的手机，或打开本地相机文件夹。设置一个 "Best Photos" 目标文件夹。打开查看器，快速滑动浏览成千上万张照片，点按目标按钮即可立即复制最满意的照片。

### 2. 🏠 网络备份（NAS）

通过 SMB 添加你家中的 NAS。浏览你的本地媒体文件。选择多个文件或一段范围，将它们 "Move" 到 NAS 上妥善保存，为设备腾出空间。

### 3. ☁️ 云端管理

连接你的 Google Drive、Dropbox 或 OneDrive 账号。无需全部下载即可浏览云端文件。直接在云端删除不需要的文件，或将它们整理到文件夹中。

### 4. 📺 幻灯片放映与演示

打开一个存有家庭照片或演示幻灯片的文件夹。点按 "Play" 开始幻灯片放映。使用按资源单独设置的选项，按喜好调整每张幻灯片的停留时长。

### 5. ⭐ 管理收藏

浏览时用星标按钮标记重要文件。之后在主菜单中点按 "Favorites" 标签页，即可在一处立即访问来自所有来源的全部收藏文件 - 非常适合打造一份精选的最佳媒体合集。

### 6. 🎶 带背景音乐的幻灯片放映

将你的音乐收藏添加为一个资源。在 **Settings → Media → Images** 中，启用 **"Play music during slideshow"** 并选择你的音乐资源。现在开始照片幻灯片放映时，你喜欢的曲目就会在后台播放。点按曲目名称即可跳到另一首随机歌曲，为你的照片演示营造完美氛围。

### 7. 🖼️ 平板上的数码相框

把任意 Android **平板**变成一台常亮的精美数码相框。放在支架上，连接你家中的电脑（SMB）或云存储 - 照片直接串流播放，不占用任何本地存储空间。调整幻灯片间隔、保持屏幕常亮、加上背景音乐，尽情回味你的美好回忆。即使是又老又慢的入门级平板也能完美胜任 - 应用已针对低资源持续播放做了优化。

### 8. 🍿 家庭影院与 VR

直接在手机或 VR 头显上观看存储在电脑或云端的喜爱剧集。无需等待复制，也不用担心空间不够。只需按下播放，下一集就会自动开始。

**VR 头显使用场景** - FastMediaSorter 可在基于 Android 的 VR 头显（Meta Quest、Pico 等）上原生运行，无需任何修改：

- **🎬 巨幕虚拟影院：** 打开来自家中 NAS 或云存储的视频，在一整面墙大小的虚拟屏幕上观看。无需把几个 GB 的文件复制到头显上 - 应用通过你的家庭网络直接串流播放。一集结束后，下一集会自动开始。
- **🎵 沉浸式音乐播放器：** 在 VR 环境中启动你的音乐收藏。后台音频服务能让音乐持续播放，即使你在应用之间切换或打开 VR 主屏幕。头显上的硬件按键（播放/暂停、下一曲）无需触碰手柄即可使用。
- **🖼️ 满墙 VR 相框：** 把 VR 头显变成沉浸式的照片体验 - 开始幻灯片放映后，你的照片会铺满周围一整面巨大的虚拟墙。搭配背景音乐，打造充满整个房间的电影般回忆体验。照片直接从家中电脑或云端串流播放，头显的存储空间不会被占用。

### 9. 🧹 下载整理器

下载文件夹乱糟糟？在来源面板中打开它，为 "Documents"、"Images" 和 "Installers" 设置目标按钮。快速浏览文件、预览内容，一键分类到正确的位置。你甚至可以把手机当作遥控器，直接在网络中的电脑上整理文件。

### 10. 🚗 车载安卓主机上的音乐

在你搭载 Android 系统的车载音响或主机上安装 FastMediaSorter。添加 U 盘或 SD 卡上的音乐文件夹 - 或直接使用内置的 **All Music** 虚拟资源，零配置立即访问你的全部收藏。方向盘控制、音量旋钮等硬件媒体按键通过后台音频服务无缝配合：播放/暂停、下一首/上一首，全程无需触碰屏幕。应用会记住播放位置，并在启动时自动恢复。

启用 **Streams** 界面后，同一台车机还能直接通过移动数据或 Wi-Fi 播放网络电台 - 无需另装 TuneIn 或 RadioDroid。添加任意电台网址，或从 Extensions 界面导入精选电台目录。常驻迷你控制条会显示当前的 ICY 曲目名称，同时电台列表保持可见。

### 11. 📺 Android TV 盒子上的媒体中心

在任意 Android TV 盒子（小米盒子、Nvidia Shield、Amazon Fire TV，或普通的 Android 盒子）上安装 FastMediaSorter。通过 SMB 连接家中的 NAS、添加 Google Drive 或 Dropbox，或插入 U 盘 - 这一切都在一个应用中完成。用遥控器或蓝牙键盘操控整个流程：方向键移动焦点，**OK** 打开项目，**Back** 返回上一级，**Backspace** 在浏览器中上移一个文件夹。遥控器上的彩色按键映射到常用文件操作（**红色** = 删除，**绿色** = 复制，**黄色** = 移动，**蓝色** = 重命名）。在电视上开始带背景音乐的全屏幻灯片放映，或切换到带专辑封面和歌词的音频播放。全程无需触摸屏。

## 文档 📚 {#documentation-}

**🗺️ Documentation Map / Карта документации:** [View all docs / Все документы](DOCS_MAP.md)

**🌐 官方网站：** [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

### 权威来源（唯一可信来源）

以下文件应被视为面向用户内容的权威信息来源：

- [完整功能列表](FEATURES.md)
- [文档地图](DOCS_MAP.md)
- [产品历史](PRODUCT_HISTORY.md)
- [下载（英文）](DOWNLOADS.md)
- [操作指南](HOW_TO-zh-hans.md)
- [程序限制](LIMITATIONS.md)
- [快速上手指南](QUICK_START-zh-hans.md)
- [服务条款](TERMS_OF_SERVICE.md)

详细指南提供多种语言版本：

**🇺🇸 英语：**

- [产品历史](PRODUCT_HISTORY.md)
- [操作指南](HOW_TO-zh-hans.md)
- [启动器网页门户](launcher/index.md)
- [Wear OS 网页门户](wear/index.md)
- [快速上手](QUICK_START-zh-hans.md)
- [常见问题](FAQ-zh-hans.md)
- [故障排查](TROUBLESHOOTING-zh-hans.md)
- [程序限制](LIMITATIONS.md)
- [下载指南](DOWNLOADS.md)
- [完整功能列表](FEATURES.md)

**🇷🇺 俄语：**

- [产品历史](PRODUCT_HISTORY-ru.md)
- [操作指南](HOW_TO-ru.md)
- [快速上手](QUICK_START-ru.md)
- [常见问题](FAQ-ru.md)
- [故障排查](TROUBLESHOOTING-ru.md)
- [程序限制](LIMITATIONS-ru.md)
- [下载构建版本](DOWNLOADS-ru.md)

**🇺🇦 乌克兰语：**

- [产品历史](PRODUCT_HISTORY-uk.md)
- [操作指南](HOW_TO-uk.md)
- [快速上手](QUICK_START-uk.md)
- [常见问题](FAQ-uk.md)
- [故障排查](TROUBLESHOOTING-uk.md)
- [程序限制](LIMITATIONS-uk.md)
- [下载构建版本](DOWNLOADS-uk.md)

**技术 / 开发者文档：**

- [架构概览](ARCHITECTURE.md)
- [DevOps 与构建脚本](DEV_OPS.md)
- [技术栈](TECH_STACK.md)
- [Wear OS 文档](WEAR_OS_QUICK_START.md)
- [开源组件](OPEN_SOURCE.md)

## Wear OS 伴侣 ⌚ {#wear-os-companion-}

FastMediaSorter 包含一个功能完整的 Wear OS 独立应用和手机伴侣端，专为智能手表的形态设计。

- 浏览和播放来自配对手机、手表自身存储，以及手表通过 Wi-Fi 直接访问的 SMB/FTP/SFTP 共享中的文件夹和收藏
- 云端资源留在手机端 - 手表本身没有云客户端；只有当你在手机上用 "Send to.." 功能发送时，云端文件才会到达手表
- 在手机和手表之间移动文件、从手表进行直播广播，并使用内置的小工具（计算器、网络监视器、迷你游戏），无需打开手机端应用
- 界面和运行时行为针对圆形和紧凑型屏幕做了优化
- 为手表相关流程提供专属网页门户、设置指南和故障排查

媒体播放、网络共享和文件传输功能包含在手表应用的完整版本中（直接安装的 APK）。Google Play 版本是较小的首个版本 - 提供计算器、秒表、迷你游戏和设置；各版本具体拥有哪些功能，参见 [Wear OS 门户](wear/index.md)。

Wear OS 文档：

- 🌟 **[Wear OS 网页门户](wear/index.md)** - 完整的功能展示、截图和应用商店下载入口
- [Wear OS 快速上手](WEAR_OS_QUICK_START.md) - 分步配对与设置指南
- [Wear OS 设置](WEAR_OS_SETUP.md) - 模块架构和伴侣桥接配置
- [Features 文档中的 Wear OS 部分](FEATURES.md#16-settings--navigation)

## 构建说明 {#build-instructions}

### 环境要求

- Android Studio Hedgehog（2023.1.1）或更高版本

- JDK 17+
- Android SDK 35
- 最低 Android 版本：Standard/Lite/Photos/VR/noLegal 为 8.0（API 26）；Legacy 为 6.0（API 23）

### 构建步骤

1. 克隆仓库：

    ```bash
    git clone https://github.com/SerZhyAle/FastMediaSorter_mob_v2.git
    cd FastMediaSorter_mob_v2
    ```

2. 在 Android Studio 中打开该项目。
3. 等待 Gradle 同步完成。
4. 在模拟器或真实设备上运行该应用。

### 推荐构建命令（Windows / PowerShell）

```powershell
.\build-debug.PS1
.\gradlew.bat :app_v2:assembleStandardDebug
.\gradlew.bat testStandardDebugUnitTest
.\gradlew.bat :app_v2:lintStandardDebug
```

### 生成的 APK 📦

每次成功构建后，生成的 APK 文件都会带着时间戳自动复制到项目根目录下的 `DOWNLOADS` 文件夹中。你可以在那里找到全部构建历史。

## 测试 🧪 {#testing-}

FastMediaSorter v2 使用 **Maestro** 进行端到端测试，以确保应用的质量和可靠性。

### 快速运行测试

```bash
# Install Maestro - macOS/Linux (Homebrew)
brew tap mobile-dev-inc/tap
brew install maestro

# Or Linux/macOS (curl)
curl -Ls "https://get.maestro.mobile.dev" | bash

# Windows (PowerShell as Administrator) - External: install.ps1 is the Maestro installer
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1  # External: Maestro installer
Remove-Item install.ps1  # External: Maestro installer

# Run smoke tests (2-3 minutes)
./maestro/run-tests.sh smoke    # Linux/macOS
.\maestro\run-tests.ps1 smoke   # Windows

# Or use shortcut
.\scripts\utils\run-maestro-smoke.ps1  # Windows
```

**注意：** 请勿使用 `npm install -g maestro-cli` - 那是一个完全不同、不相关的包！

### 测试套件

- **冒烟测试**（`maestro/smoke/`）：核心功能测试（约 2-3 分钟）
  - 应用启动与权限
  - 本地文件浏览
  - 媒体播放
  - 图片查看

- **关键路径测试**（`maestro/critical/`）：核心操作（约 1-2 分钟）
  - 文件操作（复制、移动、删除）
  - 设置持久化

### 相关文档

- 📚 [快速上手指南](../maestro/INDEX.md)
- 📝 [编写测试](../maestro/WRITING_TESTS.md)
- 🔍 [测试示例](../maestro/EXAMPLES.md)
- 🔧 [故障排查](../maestro/TROUBLESHOOTING.md)
- 📖 [完整文档](../maestro/README.md)

### CI/CD 集成

每次推送都会通过 GitHub Actions 自动运行测试。参见 [`.github/workflows/maestro-tests.yml`](../.github/workflows/maestro-tests.yml)。

## 快速上手指南 🚀 {#first-steps-quick-usage-guide-}

1. **添加文件夹（资源）：**
    - 在主界面上，点按带有 "Plus"（+）图标的按钮以添加新资源。
    - 选择资源类型（例如 "Local Folder"）。
    - 使用扫描功能或手动添加文件夹。添加后，它会出现在主界面的列表中。

2. **查看文件：**
    - 双击（或长按）列表中已添加的资源。
    - 浏览界面会打开，你会以列表或网格形式看到该文件夹中的所有媒体文件。
    - 使用顶部面板上的按钮进行排序、筛选或切换视图。

3. **播放与分类：**
    - 点按任意文件，在全屏播放器中打开它。
    - 使用左右滑动或触控区域在文件之间导航。
    - 需要执行操作（复制、移动）时，使用控制面板上对应的触控区域或按钮。

4. **配置目标文件夹（Destinations）：**
    - 在设置的 "Destinations" 标签页中，你最多可以指定 30 个用于快速分类的文件夹。
    - 或者，在任意资源的编辑界面中启用 "Is Destination"，将其加入快速分类列表。
    - 之后，播放器界面上就会出现用于快速复制或移动文件到这些文件夹的按钮。

## 技术栈 {#technology-stack}

- **语言：** Kotlin
- **架构：** Clean Architecture、MVVM
- **界面：** Android View 系统（XML）、Material Design 3
- **异步处理：** Kotlin Coroutines 与 Flow
- **依赖注入：** Hilt（Dagger）
- **数据库：** Room 2.7.0
- **导航：** AndroidX Navigation Component
- **媒体：** ExoPlayer（Media3 1.2.1）
- **图片加载：** Glide 5.0.9，配合自定义的 NetworkFileModelLoader
- **网络协议：**
  - SMB：SMBJ 0.12.1，附带 BouncyCastle（传递依赖）
  - SFTP：JSch 0.2.26（com.github.mwiede 分支，内置 Ed25519）
  - FTP：Apache Commons Net 3.10.0
- **云端：** Google Drive API、OneDrive（MSAL）、使用 OAuth 2.0 的 Dropbox API
- **OCR 与翻译：**
  - Tesseract4Android（Tesseract 5.3.x）- 提取拉丁字母和西里尔字母文本
  - Google ML Kit（翻译、语言识别）- 翻译提取出的文本
- **搜索与歌词：** api.lyrics.ovh（JSON API）

## 构建版本号

版本号格式：`Y.YM.MDDH.Hmm`（例如 `2.60.1102.207` 对应 2026/01/10 20:07）

详细的发布说明参见 [dev/CHANGELOG.md](../dev/CHANGELOG.md)。

---

## 贡献 🤝

欢迎提交 Pull Request。如涉及较大改动，请先创建一个 issue 讨论你想做的更改。

## 联系方式 📧

- **开发者：** <sza@ukr.net>
- **网站：** [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)
- **GitHub Issues：** [https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

## 许可证 📄

项目法律信息：

- [服务条款](TERMS_OF_SERVICE.md)
- [隐私政策](PRIVACY_POLICY.md)
- [开源组件](OPEN_SOURCE.md)

</div>
