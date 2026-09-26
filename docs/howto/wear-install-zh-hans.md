---
layout: default
title: "在手表上安装 FastMedia - FastMediaSorter v2"
permalink: /docs/howto/wear-install-zh-hans.html
---
<div lang="zh-Hans" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_watch.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> 在手表上安装 FastMedia

> **难度：** 入门 &bull; **用时：** 约 5 分钟 &bull; **设备：** 与 Android 手机配对的 Wear OS 智能手表

> **两个版本。** Google Play 上的版本是一个精简的首发版本：计算器、秒表、迷你游戏、设置和程序图块。音乐、照片、网络共享和手机联动功能仅在完整版中提供，需要从[下载页面](../DOWNLOADS.md)直接下载 APK。

{% include lang-switcher.html doc="wear-install" dir="/docs/howto/" current="zh-Hans" %}

FastMedia Wear 是 FastMediaSorter 在手表端的对应部分。安装到手表后，您可以直接在手表上播放音乐、查看照片，访问配对手机共享的文件夹，还可以让手表自行连接网络共享。本页将带您完成安装和配对。

---

## 你需要准备什么

- 一块运行 **Wear OS 3.0** 或更高版本的智能手表
- 一部已安装 FastMediaSorter，并已在系统设置中与手表完成配对的 Android 手机
- 手表本身、或与之配对的手机具有 Wi-Fi 或移动网络连接，用于下载

---

## 第 1 步 - 在手表上安装 FastMedia Wear

1. 在手表上打开应用列表，点击 **Play 商店**。
2. 搜索 **FastMedia Wear**。
3. 点击**安装**并等待下载完成。完成后手表的应用列表中会显示该应用。

> 不同手表的输入便利程度不同。如果在手腕上搜索不太方便，可以在手机上打开 Play 商店，找到 FastMedia Wear，然后选择您的手表作为安装目标 - 手表会自行完成下载。

### 没有 Play 商店？通过 ADB 安装 APK

当您的手表无法访问 Play 商店时，可以使用这种方式。您需要一台安装了 Android SDK Platform-Tools（`adb`）的电脑，以及电脑和手表共用的本地 Wi-Fi 网络。仅靠互联网连接无法完成这一步。

1. 从[直接 APK 发布页面](../DOWNLOADS.md)下载一个 APK：
   - `FastMediaSorter_wear_debug.apk` 是用于测试的调试版本，安装后的包名为
     `com.sza.fastmediasorter.debug`。
   - `FastMediaSorter_wear_release.apk` 是已签名的正式版本，安装后的包名为
     `com.sza.fastmediasorter`。
   - 这两个版本的包名不同，因此可以同时安装、互不影响。请勿尝试用 ADB 安装 Play 商店的 `.aab` 文件。
2. 在手表上开启开发者模式：**设置** → **关于手表** → 连续点击**版本号**七次。在**开发者选项**中，启用 **ADB 调试**和**无线调试**。
3. 在**无线调试**中，选择**配对新设备**。在电脑上，输入手表显示的配对地址和配对码，然后使用无线调试主界面上另外给出的连接端口进行连接：

   ```powershell
   adb pair <watch-ip>:<pairing-port> <six-digit-code>
   adb connect <watch-ip>:<connection-port>
   adb devices
   ```

   在手表上接受调试提示。配对端口和连接端口是不同的。
4. 安装或更新 APK。请使用与您下载的文件相匹配的命令：

   ```powershell
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_debug.apk"
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_release.apk"
   ```

   `-r` 会在保留应用数据的前提下更新同一个包。它不会把调试版本转换为正式版本，因为这两者是相互独立的应用。
5. 从手表的应用列表中打开 **FastMedia Wear**。如果需要，也可以通过 ADB 启动它：

   ```powershell
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter/com.sza.fastmediasorter.wear.MainActivity
   ```

> 此方法需要 Wear OS 手表。Galaxy Watch 3、Galaxy Watch Active 和 Active 2 运行的是 Tizen 系统，
> 无法安装 Wear OS 的 APK。完成后，如果不需要再次更新，请关闭无线调试。

---

## 第 2 步 - 在手机上开启 Wear 伴侣功能

在您确认拥有一块手表之前，手机端的这一功能默认是关闭的。

1. 在手机上打开 FastMediaSorter。
2. 进入**设置**，打开**管理**选项卡。
3. 找到 **Wear OS** 分组并展开它。
4. 打开 **Wear 伴侣**复选框。

打开该复选框会启用整个伴侣功能：打开其窗口的按钮会出现在正下方，程序列表中也会新增一个条目，它还会作为面板图块和启动器快捷方式可用。

> 不带手表桥接功能的版本根本不会显示这个分组。如果您找不到它，说明您使用的版本不包含 Wear 支持。

---

## 第 3 步 - 选择要同步到手表的内容

1. 在同一个分组中，点击 **Wear 伴侣**，它的窗口会覆盖在应用上方打开。
2. 选择您希望手表能看到的资源。在您做出选择之前不会发送任何内容 - 空选择意味着什么都不发送，而不是推送您的整个媒体库。
3. 您也可以在这里调整手表自身的偏好设置：查看模式、保持唤醒行为，以及手表主屏幕上显示的区域。

---

## 第 4 步 - 检查两端是否能互相识别

1. 在手表上打开 **FastMedia Wear**。
2. 主屏幕会列出各个区域 - **手机**、**本地**、**资源**、**串流**和**应用**。
3. 点击**手机**，您在第 3 步中选择的文件夹会显示出来。

如果“手机”区域是空的，请回到手机上的伴侣窗口，确认至少选中了一个资源。

> **提示：** 您可以在手表的任意界面上，通过左边缘可见的通用返回按钮、从左边缘滑动，或按手表的硬件返回键来返回上一级。在主屏幕上，点击返回按钮会显示一个退出图标（一个箭头离开方框）用于离开应用，或一个双箭头（«）用于最小化后台播放。在每个显示该按钮的界面上，右边缘都有一个对应的黑屏按钮（一部屏幕变暗的手机图标），点击它可以熄灭手表屏幕；双击、长按或按硬件按键即可恢复。

---

## 如果遇到问题

- **Play 商店中没有出现手表应用。** 请确认手表运行的是 Wear OS 3.0 或更高版本。较旧的手表使用不同的应用模式，不受支持。
- **手机设置中缺少 Wear OS 分组。** 说明您使用的版本不包含手表桥接功能。
- **手表上的“手机”区域是空的。** 伴侣窗口中没有选中任何内容，或者手表和手机已经失去配对 - 请先在系统设置中检查配对状态。
- **通过手机连接播放时出现卡顿。** 手表和手机之间的蓝牙带宽有限。如果要长时间收听，请将文件传输到手表，或让手表直接连接网络共享。

---

## 接下来可以做什么

- [智能手表上的音乐](scenario-watch-music-zh-hans.md) - 在手表上播放您的音乐收藏，支持专辑封面、随机播放和表圈音量控制。
- [将手表连接到网络共享](scenario-watch-network-zh-hans.md) - 通过 Wi-Fi 直接从手表访问 NAS 或电脑共享，无需手机。

</div>
