---
layout: default
title: "扫描一个二维码打开电脑文件夹 - FastMediaSorter v2"
permalink: /docs/howto/scenario-companion-share-zh-hans.html
---
<div lang="zh-Hans" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_sftp.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> 扫描一个二维码打开电脑文件夹

> **难度：** 入门 &bull; **版本：** Standard、Photos、Legacy、VR、noLegal（Lite 没有网络资源功能；扫码需要摄像头，文件方式在所有设备上都可用）

{% include lang-switcher.html doc="scenario-companion-share" dir="/docs/howto/" current="zh-Hans" %}

您在 Windows 电脑上运行一个小型辅助程序，选择存放视频、音乐、文档或照片的文件夹，它就会在屏幕上生成一个二维码。在手机上点击**添加**，把摄像头对准这个码，电脑文件夹就会立即连接 - 无需输入地址、端口、密码，也不需要数据线。

> **通俗解释：** Windows 辅助程序会把您选择的文件夹变成家庭 Wi-Fi 上的私有只读共享，并生成一个已经包含手机连接所需全部信息的码。扫描这个码，效果和手动填写一份很长的连接表格完全一样 - 只是手机一眼就能读完。文件之后按需打开，通过 Wi-Fi 串流播放；在您主动要求之前，不会有任何内容复制到手机上。

---

## 辅助程序

“伴侣”是**[Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/)**（原名 FastMediaSorter LITE）内置的一项功能 - 出自同一位作者的免费 Windows 媒体整理工具。当您用它共享文件夹时，它会：

- 只为这些文件夹在您的电脑上启动一个私有 SFTP 服务器。
- 自动生成密钥并设置开机自启，让共享在下次启动时依然存在。
- 在屏幕上显示一个**二维码**，也可以保存一个小型的 `.fmscfg` 配置文件。

**获取地址：**

- 官网：[serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- 发布文件夹（分步说明）：[如何将电脑文件夹发布到 Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub：[最新版本](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest)（安装程序或便携版 ZIP）
- winget：`winget install SerZhyAle.FastMediaSorter`
- Microsoft Store：搜索“FastMediaSorter LITE”（仍以旧名称上架）

---

## 你需要准备什么

- 一台安装了 **Fast Media Sorter for Windows** 的电脑
- 手机和电脑连接到**同一个 Wi-Fi 网络**（同一台路由器）
- 最快的方式：手机**摄像头**用于扫码（如果没有摄像头，也可以使用基于文件的方式）

---

## 第 1 步 - 在电脑上共享文件夹

1. 安装并运行 **Fast Media Sorter for Windows**，然后在设置中打开**共享**选项卡。
2. 选择您想要在手机上访问的文件夹 - 电影、音乐、文档、照片，任何内容都可以。
3. 应用会自动启动 SFTP 服务器、生成密钥并设置开机自启。无需其他配置。
4. 此时电脑屏幕上会显示一个**二维码**。请保持该窗口打开，用于第 2 步。

> 想用文件代替二维码？在同一个窗口中使用**保存 .fmscfg**，然后把该文件发送到手机（邮件、Telegram 或任意共享文件夹）。参见[第 2 步，方法 B](#step-2-method-b---import-the-file)。

---

## 第 2 步，方法 A - 扫码（最快）

1. 打开 FastMediaSorter，点击主屏幕上的**添加 <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** 按钮。
2. 点击**“通过二维码导入”** - 它位于四个资源类型卡片（本地、SMB、SFTP/FTP、云）旁边，也出现在 SFTP 表单的顶部。
3. 摄像头会打开，并显示提示*“将摄像头对准伴侣二维码”*。把手机举到电脑上的二维码前。在光线较暗的房间里，点击**手电筒**。
4. 会出现确认提示 - *“导入访问权限 - 添加带有 N 个文件夹的 SFTP 资源..？”*。点击**导入**。
5. 完成。每个共享文件夹都会在主屏幕上生成一个只读资源，服务器密钥会自动被固定保存。

> 在没有摄像头的设备和 VR 头显上，**通过二维码导入**选项会被隐藏 - 此时请使用方法 B。

<!-- TODO screenshot: Add-resource screen with the four type cards plus "Import from file" and "Import by barcode" entries -->

<!-- TODO screenshot: QR scan screen with the "Point the camera at the companion QR code" hint and Torch button -->

---

## 第 2 步，方法 B - 导入文件 {#step-2-method-b---import-the-file}

当手机没有摄像头，或电脑和手机不在同一个地方时，请使用此方法。

1. 在电脑上使用**保存 .fmscfg**，然后把文件传到手机（邮件、Telegram、云盘或共享文件夹）。
2. **如果文件已经在手机上：** 点击**添加 <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** -> **“SFTP / FTP”** -> **“从文件导入”**，然后选择 `.fmscfg` 文件。
3. **如果是以附件形式收到的**（Telegram 或邮件）：直接点击 `.fmscfg` 附件 - 应用会直接打开确认对话框。
4. 确认同样的*“导入访问权限”*对话框，然后点击**导入**。只读资源随即出现。

> **请把二维码和文件当作钥匙一样对待。** 两者都内嵌了访问密码，让手机无需输入任何内容即可连接。请勿公开发布二维码截图或 `.fmscfg` 文件。

---

## 完成！您现在可以..

共享文件夹的使用方式与应用中的其他资源完全相同。例如：

- **在手机、平板或 Android TV 盒子上观看**电脑里的电影和剧集 - 串流播放，不复制任何文件。参见[家庭影院与 VR 串流](scenario-home-cinema-zh-hans.md)。
- 在路上或车机上**播放您的音乐库**。
- **阅读**存放在电脑上的 **PDF 和 EPUB**，并保留上次的阅读位置。
- **浏览照片档案**并用 Quick Sort 整理，或将其显示为[数字相框](scenario-photo-frame-zh-hans.md)。
- **把文件交给专门的应用打开** - 打开网络文件的信息面板，点击下载并打开。
- 在电脑和手机之间双向**复制或移动文件**。

---

## 工作原理（底层机制）

- Windows 辅助程序运行一个轻量级的 **SFTP 服务器**，绑定到您选择的文件夹，仅在本地网络中可见。
- 二维码（或 `.fmscfg` 文件）编码了连接信息：主机、端口、凭据、共享文件夹路径，以及服务器的主机密钥指纹。内容较多的共享会被压缩发送，因此即使文件夹很多，也能放进一个码里。
- 手机读取这些数据并验证，然后为每个文件夹创建一个**只读 SFTP 资源**。二维码中还携带电脑服务器密钥的指纹，手机在每次连接时都会检查它 - 无论是浏览、复制、生成缩略图还是播放。如果有其他电脑冒充您的电脑应答，手机将不会加载任何内容，并提示服务器看起来不一样。
- 由于这是本地 Wi-Fi 且为只读，手机浏览和串流文件时不会更改电脑上的任何内容。
- **在同一 Wi-Fi 下，手机会自动找到电脑。** 伴侣程序会在本地网络中广播共享信息，手机通过固定的密钥进行匹配 - 因此即使电脑在网络中的地址发生变化，共享也无需重新扫码即可继续工作。
- **一次导入可以在家里和外面都用。** 二维码可以携带多个地址 - 本地地址、IPv6 地址，以及互联网端口转发地址。手机会依次尝试，使用当前可用的那个：在家用本地地址，用移动数据时用互联网地址。只要电脑在您所在的位置能被访问到，同一个资源在您切换网络时也能持续工作。
- **如果无法连接，应用会说明该怎么做** - 连接到同一个 Wi-Fi，或在电脑上设置访问权限 - 而不是只显示一条干巴巴的错误。如果伴侣程序附带了关于访问的说明，手机会显示出来。

---

## 疑难解答

| 问题 | 解决方法 |
|---------|------------|
| 没有“通过二维码导入”选项 | 设备没有摄像头，或者是 VR 版本。请使用[方法 B - 导入文件](#step-2-method-b---import-the-file) |
| 摄像头提示需要授权 | 出现提示时授予摄像头权限 - 该权限仅用于扫码 |
| “此文件不是有效的伴侣配置” | 该二维码或文件不是来自 Windows 伴侣程序。请从**共享**选项卡重新导出 |
| “由更新版本的伴侣程序创建” | 更新手机上的 FastMediaSorter，或使用匹配版本的伴侣程序重新导出 |
| 资源已添加但文件夹为空 | 确认电脑上的辅助程序仍在运行。在**同一 Wi-Fi** 下应用会自动找到电脑；如果仍然失败，应用会提示需要检查的内容 |
| 在 Wi-Fi 下可用，但移动数据下不行 | 要从其他网络访问电脑，电脑必须能从互联网访问到 - 请在伴侣程序的**共享**设置中配置端口转发或 IPv6。否则共享仅在同一 Wi-Fi 下可用 |

→ 更多帮助：[疑难解答](../TROUBLESHOOTING-zh-hans.md) &bull; 基础知识：[连接到 NAS / Windows 共享（SMB）](scenario-smb-setup-zh-hans.md)

</div>
