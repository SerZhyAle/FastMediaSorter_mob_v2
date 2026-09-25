---
layout: default
title: "将智能手表连接到 NAS 和电脑共享 - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-network-zh-hans.html
---
<div lang="zh-Hans" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_smb.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> 将智能手表连接到 NAS 和电脑共享

> **难度：** 进阶 &bull; **用时：** 约 10 分钟 &bull; **设备：** Wear OS 智能手表

> **仅限完整版** - 本指南不适用于通过 Google Play 分发的版本，而是适用于从[下载页面](../DOWNLOADS.md)直接下载 APK 的完整版本。

{% include lang-switcher.html doc="scenario-watch-network" dir="/docs/howto/" current="zh-Hans" %}

Wear OS 上的 FastMediaSorter 可以通过 Wi-Fi 直接连接到您的家庭网络存储（NAS、电脑共享文件夹、FTP 或 SFTP 服务器）。您可以浏览远程文件、向蓝牙耳机串流音乐，并同步您喜欢的文件夹，而无需借助手机。

> **第一次接触网络共享？** 如果您还没有在电脑或 NAS 上设置共享文件夹，请先参考我们的[连接到 NAS / Windows 共享（SMB）](scenario-smb-setup-zh-hans.md)指南。

---

## 你需要准备什么

- 一块运行 **Wear OS 2.0** 或更高版本、已连接到家庭 Wi-Fi 网络的智能手表
- 一个共享的网络文件夹（SMB / Windows 共享、FTP 服务器，或 SFTP 服务器）
- 网络凭据：IP 地址或主机名、共享名称、用户名和密码
- 手表上已安装 FastMedia Wear

---

## 第 1 步 - 在手表上打开资源

1. 在智能手表上打开 **FastMedia Wear**。
2. 在主屏幕上点击**资源**（Wi-Fi 图标）。
3. 资源界面会显示您已配置的网络连接。

![Wear OS 上的资源界面](screenshots/screenshot-wear-network-step1.png)

> **从手机同步快捷方式：** 如果您已经在 Android 手机上的 FastMediaSorter 中添加了 SMB 或 SFTP 共享，点击**从手机同步**即可一键将所有连接设置导入到手表。

---

## 第 2 步 - 添加网络来源

1. 在资源界面上，点击**添加资源**。
2. 选择您的网络协议：
   - **SMB**：标准 Windows 共享、Synology、QNAP 或 TrueNAS
   - **FTP**：标准 FTP 文件服务器
   - **SFTP**：安全的 SSH 文件传输服务器（支持密码或私有 SSH 密钥）
3. 点击每个字段，使用手表屏幕键盘输入连接详情：
   - **名称**：可选标签（例如“家庭 NAS”或“音乐共享”）
   - **服务器地址**：您的电脑或 NAS 的 IP（例如 `192.168.1.50`）
   - **端口**：网络端口（默认：SMB 为 445，FTP 为 21，SFTP 为 22）
   - **共享名称**（仅限 SMB）：您 NAS/电脑上的共享文件夹名称
   - **用户名**和**密码**：您的登录凭据

![手表上的添加网络来源界面](screenshots/screenshot-wear-network-step2.png)

---

## 第 3 步 - 测试并保存连接

1. 滚动到表单底部，点击**测试**。
2. FastMedia Wear 会验证网络路由和凭据：
   - 成功时，屏幕会显示**连接成功！**。
   - 如果出现问题，会有一条友好的状态提示说明需要调整的内容（例如服务器地址或密码）。
3. 点击**保存**，将该网络来源存储到手表上。

![测试网络连接并保存来源](screenshots/screenshot-wear-network-step3.png)

---

## 第 4 步 - 浏览并播放网络媒体

1. 在资源界面上，点击您新保存的网络共享。
2. FastMedia Wear 会连接到远程共享并列出其内容。
3. 以列表或网格视图浏览文件夹和文件。
4. 点击任意音频曲目即可在全屏播放器中开始播放。有关详细的播放器功能和省电方式，请参见[在手表上收听音乐](scenario-watch-music-zh-hans.md)。

![浏览网络共享中的文件和文件夹](screenshots/screenshot-wear-network-step4.png)

---

## 完成！Wear OS 上的网络功能一览

- **独立 Wi-Fi 串流：** 通过 Wi-Fi 直接从 NAS 或电脑串流，无需手机中转。
- **多协议支持：** 全面支持 SMB、FTP 和 SFTP，可使用密码或 SSH 私钥进行身份验证。
- **双向同步：** 可以从手机伴侣应用同步连接，也可以把手表上的来源导出回手机。

---

## 疑难解答

| 问题 | 解决方法 |
|---------|------------|
| 连接测试提示“连接失败” | 确认手表与服务器连接的是同一个 Wi-Fi 网络，并检查 IP 地址是否正确 |
| SMB 共享名称错误 | 请只输入共享名称本身（例如 `Music`），而不是带斜杠的完整路径 |
| 身份验证失败 | 检查您的用户名和密码。对于 Windows 共享，请确认网络共享权限允许您的用户账户访问 |
| 通过 Wi-Fi 加载缓慢 | 确保手表的 Wi-Fi 信号良好，并且到本地服务器的 5 GHz / 2.4 GHz 网络路由没有被阻挡 |

</div>
