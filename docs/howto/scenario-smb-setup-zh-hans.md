---
layout: default
title: "连接到 NAS / Windows 共享（SMB）- FastMediaSorter v2"
permalink: /docs/howto/scenario-smb-setup-zh-hans.html
---
<div lang="zh-Hans" dir="ltr" markdown="1">

# 🖥️ 连接到家庭 NAS / Windows 共享（SMB）

> **难度：** 入门 &bull; **版本：** Standard、Photos、Legacy、VR、noLegal（Lite 没有网络资源功能）

{% include lang-switcher.html doc="scenario-smb-setup" dir="/docs/howto/" current="zh-Hans" %}

SMB（也叫 Windows 文件共享或 CIFS）可以让您浏览家里电脑、笔记本或 NAS 设备上的文件，就像它们就在您手机上一样 - 无需数据线，无需 USB，只需 Wi-Fi。

> **通俗解释：** 想象您的电脑在家庭 Wi-Fi 上有一块公共布告栏。家里的任何设备都能读取这块布告栏。FastMediaSorter 会连接到这块“布告栏”（也就是您共享的文件夹），让您像浏览手机本地存储一样浏览这些文件。没有任何内容会被提前复制或下载 - 文件都是按需打开的。

---

## 你需要准备什么

- 手机和电脑/NAS 连接到**同一个 Wi-Fi 网络**（同一台路由器）
- 电脑或 NAS 的 **IP 地址**（例如 `192.168.1.100`）
- **共享名称**（您共享的文件夹名称，例如 `Photos`）
- 该共享的**用户名和密码**（如果启用了访客访问则不需要）

> **不清楚这些术语？** 别担心 - 第 1 步和第 2 步会准确说明去哪里找到它们。

---

## 第 1 步 - 找到电脑的 IP 地址

IP 地址就是您电脑在 Wi-Fi 网络中的“家庭地址”。您需要它，手机才知道去哪里查找。

在 **Windows** 上：
1. 按 `Win + R`，输入 `cmd`，回车 - 会打开一个黑色文本窗口
2. 输入 `ipconfig` 并回车
3. 在 Wi-Fi 适配器下查找 **IPv4 Address** - 类似 `192.168.1.100`

> 您需要的那一行标注为 **“IPv4 Address”**（不是 IPv6，IPv6 看起来是一长串字母和数字）。在大多数家庭网络中，它通常以 `192.168.` 开头。

在 **NAS**（Synology、QNAP 等）上：
- 打开 NAS 的网页管理面板 → 网络设置 - 那里会显示 IP 地址

> 把这个 IP 地址记下来 - 第 6 步会用到。

![Windows PowerShell - ipconfig 输出，可见 IPv4 Address `192.168.1.100`](screenshots/screenshot-smb-step1.png)

---

## 第 2 步 - 找到电脑上的共享名称

“共享名称”是您文件夹在网络上的公开名字，可能与文件夹名相同，也可能不同。

在 **Windows** 上：
1. 打开**文件资源管理器**
2. 右键点击您想共享的文件夹 → **属性**
3. 进入**共享**选项卡
4. 查看**网络路径** - 类似 `\\DESKTOP-ABC\Photos`
5. 最后一个 `\` 之后的部分就是您的**共享名称**（此处为：`Photos`）

> **文件夹还没有共享？** 点击**共享..** → 选择 **Everyone** → **添加** → **共享**。Windows 会立即显示网络路径。

> **重要提示：** 请确保 Windows 中的**网络发现**和**文件共享**已开启。进入控制面板 → 网络和共享中心 → 更改高级共享设置 → 开启“网络发现”和“文件和打印机共享”。

![Windows 文件夹属性 - 共享选项卡，可见网络路径 `\\MARK\Common`](screenshots/screenshot-smb-step2.png)

---

## 第 3 步 - 打开 FastMediaSorter 并点击“+”

1. 打开应用
2. 在**主屏幕**上，点击顶部工具栏中的**“添加” <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** 按钮

![FastMediaSorter 主屏幕 - 顶部工具栏中高亮显示的添加按钮，可见 SMB 选项卡](screenshots/screenshot-smb-step3.png)

---

## 第 4 步 - 选择“网络文件夹 (SMB)”

在资源类型列表中，点击**“网络文件夹 (SMB)”**（或 SMB 选项卡）。

![选择文件夹类型对话框 - 四个选项：本地文件夹、网络文件夹 (SMB)、SFTP/FTP、云存储](screenshots/screenshot-smb-step4.png)

---

## 第 5 步 - 先尝试自动发现

点击**“扫描网络”**按钮。应用会扫描本地 Wi-Fi 中带有 SMB 共享的设备。

- 等待约 10 秒
- 会出现已找到设备的列表
- 点击您的电脑或 NAS - 地址会自动填入

<!-- TODO screenshot: Scan Network in progress - spinner or "Scanning.." text -->

<!-- TODO screenshot: Scan results list showing one or more found devices -->

> **什么都没找到？** 没关系 - 跳到第 6 步手动输入 IP 地址。这通常是因为您的路由器开启了 AP 隔离（一种出于安全考虑、会阻止手机与电脑通信的设置）。手动输入 IP 总是有效的。

---

## 第 6 步 - 填写连接详情

填写以下表单：

| 字段 | 应输入的内容 | 示例 |
|-------|--------------|---------|
| **服务器 / 路径** | `\\IP\共享名称` | `\\192.168.1.100\Photos` |
| **用户名** | 您的 Windows 登录名 | `john` |
| **密码** | 您的 Windows 密码 | `••••` |
| **显示名称** | 任意您喜欢的名称（可选） | `Home PC - Photos` |

> **使用 Microsoft 账户（邮箱）登录 Windows？** 请使用您的**完整邮箱地址**作为用户名（例如 `john@outlook.com`），而不仅仅是名字。密码与解锁电脑时使用的密码相同。

> **没有密码，或使用访客账户？** 试试把用户名和密码留空，然后点击测试连接 - 部分家用电脑允许开放访问。

![添加网络文件夹 (SMB) - 已填写服务器 IP `192.168.1.100`、共享名称和凭据](screenshots/screenshot-smb-step6.png)

![添加网络文件夹 (SMB) - 下方区域：选项、媒体类型、添加此资源按钮](screenshots/screenshot-smb-step6b.png)

**地址格式参考：**

| 格式 | 示例 |
|--------|---------|
| 标准 Windows 格式 | `\\192.168.1.100\Photos` |
| Linux / macOS 风格 | `smb://192.168.1.100/Photos` |
| 子文件夹 | `\\192.168.1.100\Media\Movies` |
| 自定义端口 | `smb://192.168.1.100:445/Photos` |

---

## 第 7 步 - 测试连接

点击**“测试连接”**。

- **绿色提示** = 成功 → 前往第 8 步！马上就完成了。
- **红色提示** = 出了点问题 → 请查看下方的疑难解答表格。最常见的解决方法：仔细检查 IP 地址和共享名称。

<!-- TODO screenshot: Green "Connection successful" toast or inline success message -->

---

## 第 8 步 - 保存并打开

点击**“保存”**。新的文件夹会带着 SMB 标记出现在主屏幕上。

点击它即可浏览其中的内容 - 照片、视频和其他文件会像任何本地文件夹一样以缩略图形式显示。

![FastMediaSorter 主屏幕 - 新的“Common” SMB 资源卡片（smb://192.168.1.100/Common），高亮显示网络文件夹 SMB 标记](screenshots/screenshot-smb-step8.png)

---

## 完成！您现在可以..

- 从手机浏览电脑上的所有文件
- 直接播放视频和音乐 - 无需下载
- 在手机和电脑之间复制或移动文件
- 将此文件夹用作幻灯片、相框或车载音乐的来源

---

## 疑难解答

| 问题 | 解决方法 |
|---------|------------|
| “连接被拒绝” | 打开 Windows 防火墙 → 允许 **TCP 端口 445** 的入站连接。或暂时关闭防火墙进行测试 |
| “密码错误” | 试着把**用户名留空**（访客访问）。如果您使用 Microsoft 账户，请将**完整邮箱地址**作为用户名输入 |
| “未找到主机” | 确认手机和电脑在**同一个 Wi-Fi**、同一台路由器下。AP 隔离（一种路由器安全设置）可能会阻止此连接 - 可以尝试在路由器设置中关闭它 |
| 扫描什么都找不到 | 关闭手机上的 VPN。同时检查 Windows 中的**网络发现**是否已开启（控制面板 → 网络和共享中心）。然后尝试手动输入 IP 地址 |
| 浏览速度非常慢 | 点击资源上的**编辑** → 运行**速度测试**查看实际吞吐量。对于速度较慢的连接，可关闭视频缩略图 |
| 在 Wi-Fi 下可用，但移动数据下不行 | 这是正常现象 - SMB 只是一种局域网协议，无法在移动数据下工作 |

→ 更多帮助：[疑难解答](../TROUBLESHOOTING-zh-hans.md)

</div>
