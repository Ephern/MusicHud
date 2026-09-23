![Banner](
https://cdn.modrinth.com/data/7Rnb6oJr/images/20ba5cbcaf71e2ab70436963776e5f801fea44a7.png)
![Home View (in developing)](https://cdn-alt.modrinth.com/data/7Rnb6oJr/images/1df1c89491d051b3ac5bb90f95d4d4b054c0af57.png)
(Shot on 1.3.0 alpha 2, see more in [gallery](https://modrinth.com/plugin/music-hud/gallery))

**A GUI-based, all situations (singleplayer/multiplayers) music mod/plugin designed with zero modifications to game mechanics**

> Due to service scope provided by NetEase, this mod might not work well outside China

## Related Links

[GitHub](https://github.com/Ephern/MusicHud)

[ModRinth](https://modrinth.com/mod/music-hud)

[CurseForge (no longer update)](https://www.curseforge.com/minecraft/mc-mods/music-hud)

[Third-party multi music platform (Incompatible with each other)](https://github.com/MOPELotus/MusicHud-TuneWeave)

[Third-party legacy Bukkit plugin (1.0.5 stable -)](https://github.com/Shiroiame-Kusu/MusicHud-Bukkit)

## Prerequisites
- ModernUI (only client, using mVUS fork on 1.21.9-11, [this fork](https://github.com/MOPELotus/ModernUI-MC/releases) on [26.2](https://github.com/MOPELotus/ModernUI-MC/releases#release-26.2-3.13.0.9) and [26.3](https://github.com/MOPELotus/ModernUI-MC/releases#release-26.2-3.13.0.9))
- Forge Config API Port (only Fabric)
- Fabric API (only Fabric)
- ~~Architectury API~~ (Fabric / NeoForge, required by MusicHUD 1.2.0 and below)

## Features
- With graceful GUI, providing an in-game operation interface and a easily configurable HUD.
- Can read NetEase Cloud Music account playlists for convenient song requests.
- The server does not retain user data; it is only temporarily stored during session.
- Streamed playback with no redundant client-side caching.
- Staggered lyrics auto scroll inspired by Apple Music
- HUD dynamic fluid background
- Windows SMTC / Linux MPRIS (have not fully tested yet) support (Powered by slightly modified [JMTC](https://github.com/Selemba1000/JavaMediaTransportControls))
- Using optimized high-quality audio pipeline based on OpenAL, supports NetEase Cloud Music's all quality options, and outputs 32-bit float audio and multichannel surround audio if device supports
- Connected mode: Synchronized server-wide play queue and progress.
- Isolated mode: Do all works on client, enjoy music yourself.
- Sharing same playback model between connected mode and isolated mode, easy to use
- Low network bandwidth / performance required for server side, audio stream downloading and processing is done on client side

## Functions
- Search for musics, playlists, albums and artists.
- Favorite/Unfavorite musics, subscribe/unsubscribe playlists, albums and artists.
- Vote for skipping current music
- Log in to NetEase Cloud Music account via QR code or SMS code to explore subscribed playlists, albums and artists.
- Scrobble your play record to NetEase Cloud Music account
- Manage your NetEase Cloud Music Cloud Drive
- Configure idle playback sources for automatic random song switching when music player is idle.
- Display lyrics, requesting player (or avatar) in the HUD and user interface.

## Compatibility
- Mute Reactive Music when MusicHUD is playing
- Connected mode compatible with ViaVersion
- Adapted to Sound Physics Perfected, disabled its reverb applied to MusicHUD audio pipeline to improve audio quality

## TO-DO List
- Customizable HUD layout [target 1.4.0]
- More music platform support (QQ Music, Kugou Music or more) [target 2.0.0]

## Usage

### Client
First of all, place the prerequisite mods and the MusicHud jar file into the mods folder.

#### Single-player or LAN Multi-player Host
All you need is to configure or deploy an API server.

#### Multiplayer (join Server or LAN Multi-player)
##### Connected mode
You don't need to configure or deploy an API server, but the server or LAN multiplayer host needs to configure or deploy MusicHUD and API server.

##### Isolated mode
All you need is to configure or deploy an API server.

### Server
All you need is to configure or deploy an API server.

### How to Deploy an API Server
There are 2 methods to deploy.
#### Deploy bound with mod (recommend for client)
> This method allows MusicHUD to manage lifecycle of api server.
>
> Due to some limitations, API Server may not be auto-closed when game exit abnormal (e.g., due to a critical crash)

##### Auto Deploy
You can find "Download API..." button in setting page, which will open a download guide, and download API backend from [Netease Cloud Music API Enhanced's releases](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/releases) with optional GH Proxy speedup

##### Manual Deploy
1. Go to https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/releases
2. Download platform binary artifacts matches to your pc/server platform
3. Uncompress the binary executable file from ZIP file to ...
   - Mod edition: `{corepath}/music-hud/` and rename it to `api` (`api.exe` on Windows)
   - Plugin edition: `{corepath}/plugins/MusicHud/` and rename it to `api` (`api.exe` on Windows)
   - Or place it anywhere and modify config in game or option `serverApiBinaryExecutablePath` (absolute or relative path) in config file `/config/music_hud-common.toml`.

#### Deploy separately (recommend for server)
1. Deploy [NetEase Cloud Music API Enhanced](https://github.com/neteasecloudmusicapienhanced/api-enhanced) with guide in repository.
2. If not using the default port (3000) of NCM API Enhanced or deploying on another server, modify the `serverApiBaseUrl` property in the config file `/config/music_hud-server.toml`.

> Experimentally, there is another [Rust version API server](https://github.com/SPlayer-Dev/ncm-api-rs), which remains most of api endpoints same with NodeJS version but is much faster. MusicHUD have adapted to it since 1.2.13. You can find binary executable files in its release page. But there are still some issues in this api server edition. So use it at your own risk.

---
## CN version description

**图形化的全场景（单人/多人游戏）音乐模组/插件，设计上不修改任何游戏机制**

## 相关链接

[GitHub](https://github.com/Ephern/MusicHud)

[MC 百科](https://www.mcmod.cn/class/23688.html)

[ModRinth](https://modrinth.com/mod/music-hud)

[CurseForge（暂时停更）](https://www.curseforge.com/minecraft/mc-mods/music-hud)

[第三方多音乐平台实现 (不互相兼容)](https://github.com/MOPELotus/MusicHud-TuneWeave)

[第三方旧 Bukkit 插件实现 (1.0.5 stable -)](https://github.com/Shiroiame-Kusu/MusicHud-Bukkit)

## 前置依赖
- ModernUI （仅客户端，在 1.21.9 - 1.21.11 上使用 mVUS 分支，在 26.2、26.3 上使用[该分支](https://github.com/MOPELotus/ModernUI-MC/releases) [(26.2 release)](https://github.com/MOPELotus/ModernUI-MC/releases#release-26.2-3.13.0.9) [(26.3 release)](https://github.com/MOPELotus/ModernUI-MC/releases#release-26.2-3.13.0.9)）
- Forge Config API Port （仅 Fabric）
- Fabric API （仅 Fabric）
- ~~Architectury API~~ （Fabric / NeoForge，MusicHUD 1.2.0 以及更低版本需要）

## 特点
- 具有优雅的 GUI，提供游戏内操作界面，以及易配置的 HUD
- 可读取网易云账户歌单，方便点歌
- 服务端不保留用户数据，仅在连接会话中暂存
- 流式播放，无赘余的客户端缓存
- 受 Apple Music 启发的歌词交错滚动与逐字歌词
- HUD 动态流体背景
- Windows SMTC / Linux MPRIS（尚未充分测试） 支持 （略微修改自 [JMTC](https://github.com/Selemba1000/JavaMediaTransportControls)）
- 使用优化过的 OpenAL 高质量音频管线，支持网易云的所有音质选项，在设备支持时可输出 32 位浮点音频和多声道环绕音频
- 连接模式：全服同步播放队列、进度
- 隔离模式：在客户端上独自享受音乐
- 连接模式/隔离模式共享同一播放模型，操作简便
- 服务端带宽/性能占用低，音频流下载和处理由客户端完成

## 功能
- 搜索音乐、歌单、专辑和歌手
- 收藏/取消收藏音乐、歌单、专辑和歌手
- 投票跳过歌曲
- 通过二维码或短信验证码登录网易云账户，查看收藏的歌单、专辑和歌手
- 将你的播放记录同步到网易云账户
- 管理网易云账户云盘
- 配置空闲播放源，播放器空闲时随机切歌
- 在 HUD 和用户界面中展示歌词，点歌玩家（或头像）

## 兼容
- 在 MusicHUD 播放时静音 Reactive Music
- 连接模式与 ViaVersion 兼容
- 适配 Sound Physics Perfected, 禁用 MusicHUD 音频管线的混响以保证音质

## ~~大饼~~ 待办清单
- 可自定义的 HUD 布局 [目标 1.4.0]
- 更多音乐平台支持(QQ音乐、酷狗音乐等) [目标 2.0.0]

## 使用

### 客户端
首先在 mods 文件夹中放入前置 mod 和 MusicHUD 的 jar 文件

#### 单人模式 或 局域网联机主机
只需要配置/部署 API 服务器

#### 加入服务器 或 加入局域网世界
##### 连接模式
不需要配置/部署 API 服务器

但是需要服务器/局域网主机配置/部署 MusicHUD 和 API 服务器

##### 隔离模式
只需要配置/部署 API 服务器

### 服务端
只需要部署 API 服务器

### 如何部署 API 服务器
两种方法
#### 绑定在 mod 中（推荐客户端使用）
> 这个方法会让 MusicHUD 管理 API 服务器生命周期
>
> 由于一些限制，在游戏非正常退出时（如某些严重崩溃）可能无法自动结束进程
##### 自动部署（客户端，1.2.15+）
在设置页面中的 API 状态一栏中可找到“下载 API...”按钮，可打开一个下载指引，会从 [Netease Cloud Music API Enhanced 的 release 页](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/releases) 下载 API 后端，可选使用 GH Proxy 加速下载

##### 手动部署
1. 进入 [Netease Cloud Music API Enhanced 的 release 页](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/releases)
2. 在最新的 release 中根据你的平台下载对应的二进制构建产物
3. 解压下载得到的压缩包并将其中的二进制可执行文件放置到...
   - mod 版：{核心目录}/music-hud/ 并重命名为 `api` (对于 Windows 为 `api.exe`)
   - 插件版: {核心目录}/plugins/MusicHud/api(.exe)
   - 或者放在任意处并在游戏内或配置文件`/config/music_hud-common.toml`中修改选项 `serverApiBinaryExecutablePath` 对应(绝对/相对)目录

#### 独立部署（推荐服务端使用）
1. 参考 [Netease Cloud Music API Enhanced 仓库](https://github.com/neteasecloudmusicapienhanced/api-enhanced) 内的说明部署
2. 如果不使用 NCM API Enhanced 的默认端口 ( 3000 ) 或在其他服务器上部署，需要修改配置文件`/config/music_hud-server.toml`的 `serverApiBaseUrl` 属性

> 实验性地，有另一个[Rust 版本的 API 服务器](https://github.com/SPlayer-Dev/ncm-api-rs)，保持了大部分 API 端点不变，但比 NodeJS 版本快得多。MusicHUD 已在 1.2.13 实现对其兼容。可以在它的 Release 页中找到二进制可执行文件。但它仍然有一些问题。使用前请考虑风险