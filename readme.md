# Music Hud
![Static Badge](https://img.shields.io/badge/Java-21-red?style=for-the-badge)
![Static Badge](https://img.shields.io/badge/License-LGPLv3-brightgreen?style=for-the-badge)

![Banner](https://cdn.modrinth.com/data/7Rnb6oJr/images/a870b990118bb03864c5302052995e70e3dafef4.png)
![Home View](https://cdn.modrinth.com/data/7Rnb6oJr/images/2d00c770f54f44fc194f46842f5876e307ab54e5.png)

#### A GUI-based Full Server Song Request Mod / Plugin

> Due to service scope provided by Netease, this mod might not working good outside China

## Related Links

[Third-party Bukkit plugin 1](https://github.com/Shiroiame-Kusu/MusicHud-Bukkit) (1.0.5 stable -)

[Third-party Bukkit plugin 2](https://github.com/MOPELotus/MusicHud-Paper) (1.1.4 hotfix +, now partly merged into main repository)

## Prerequisites
- ModernUI (only client)
- Forge Config API Port (only fabric)
- Architectury API (Mod edition, 1.2.0 and below)

## Features
- GUI-based, providing an in-game operation interface and a relatively aesthetic, easily configurable HUD.
- Can read NetEase Cloud Music account playlists for convenient song requests.
- The server does not retain user data; it is only temporarily stored during login.
- Streamed playback with no redundant client-side caching.
- Staggered lyrics auto scroll inspired by Apple Music
- HUD dynamic fluid background

## Functions
- Search for music.
- Log in to NetEase Cloud Music account via QR code to request songs from user playlists.
- Synchronized server-wide playlist.
- Configure playlists as idle playback sources for automatic random song switching.
- Display lyrics, requesting player (with avatar) in the HUD and user interface.
- Display the playlist in the user interface.

## Usage

### Client
First of all, place the prerequisite mods (Architectury API, ModernUI, and Forge Config API Port—required only for Fabric) and the MusicHud jar file into the mods folder.

#### Single-player or LAN Multi-player Host
> Currently experimentally supports single-player mode.

All you need is to deploy an API server

#### Multiplayer (join Server or LAN Multi-player)
You don't need to deploy an API server, but the server or LAN multiplayer host to deploy MusicHUD and API server

### Server
All you need is to deploy an API server

### How to Deploy an API server
There are 2 methods to deploy
#### Deploy bound with mod (recommend for client)
> This method will allows MusicHUD to manage lifecycle by itself
>
> Due to some limitations, API Server may not be auto-closed when game exit abnormal as crashes

1. **Login to GitHub** and goes to https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/actions/workflows/build-and-pr.yml
2. Click latest action runs in type "build-and-pr" and download platform binary artifacts matches to your pc/server
3. Uncompress the binary executable file from ZIP file to {corepath}/music-hud/ and renames to `api` (`api.exe` on Windows)

> Default path for plugin versions is `{corepath}/api(.exe)`
> Or place it in anywhere and modify config `startupBinaryApiServerWhenLaunch` in game or in config file `music_hud-server.toml`

#### Deploy seperately (recommend for server)
1. Deploy Netease Cloud Music API Enhanced (https://github.com/neteasecloudmusicapienhanced/api-enhanced).
2. If not using the default port (3000) of NCM API Enhanced or deploying on another server, modify the `serverApiBaseUrl` property in the config file.
> Configuration file location: `/config/music_hud-server.toml`

---
## CN version description

#### 一个 GUI 化的全服点歌模组/插件

## 相关链接

[MC 百科](https://www.mcmod.cn/class/23688.html)

[第三方bukkit插件实现 1](https://github.com/Shiroiame-Kusu/MusicHud-Bukkit) (1.0.5 stable -)

[第三方bukkit插件实现 2](https://github.com/MOPELotus/MusicHud-Paper) (1.1.4 hotfix +,，目前已部分合并至主仓库)

## 前置依赖
- ModernUI （仅客户端）
- Forge Config API Port （仅 Fabric）
- Architectury API （非插件版，1.2.0 以及更低版本）

## 特点
- GUI 化， 提供游戏内操作界面，以及较为美观易配置的 HUD
- 可读取网易云账户歌单，方便点歌
- 服务端不保留用户数据，仅在登录时暂存
- 流式播放，无赘余的客户端缓存
- 受 Apple Music 启发的歌词交错滚动
- HUD 动态流体背景

## 功能
- 搜索音乐
- 通过二维码登录网易云账户，从用户歌单点歌
- 全服同步播放列表
- 配置歌单作为空闲播放源，服务器随机切歌，解放双手（？）
- 在 HUD 和用户界面中展示歌词，点歌玩家（/头像）
- 在用户界面中展示播放列表

## 使用
### 客户端
首先在 mods 文件夹中放入 Architectury API, ModernUI 和 Forge Config API Port (仅Fabric需要) 这几个前置 mod 和 MusicHud 的 jar 文件
#### 单人模式 或 局域网联机主机
> 目前对单人游戏和局域网联机主机提供实验性支持
>
> 由于一些限制，在游戏非正常退出时（如崩溃）无法自动结束进程

只需要部署 API 服务器

#### 加入服务器 或 加入局域网世界
不需要部署 API 服务器但是需要服务器/局域网主机部署 MusicHUD 和 API 服务器

### 服务端
只需要部署 API 服务器

### 如何部署
两种方法
#### 绑定在 mod 中（推荐客户端使用）
> 这个方法会让 MusicHUD 管理 API 服务器生命周期

1. **登录到 GitHub** 并跳转到 https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/actions/workflows/build-and-pr.yml
2. 在 actions 类型中选择 "build-and-pr" 并进入最新的构建中根据你的平台下载对应的二进制构建产物
3. 解压下载得到的压缩包并将其中的二进制可执行文件放置到 {核心目录}/music-hud/ and renames to `api` (`api.exe` on Windows)

> 对应插件版本则是 {核心目录}/api(.exe)
> 或者放在任意处并在游戏内或配置文件中修改选项 `serverApiBinaryExecutablePath` 对应目录

#### 独立部署（推荐服务端使用）
1. 部署 Netease Cloud Music API Enhanced (https://github.com/neteasecloudmusicapienhanced/api-enhanced)
2. 如果不使用 NCM API Enhanced 的默认端口 ( 3000 ) 或在其他服务器上部署，需要修改配置文件的 serverApiBaseUrl 属性
> 配置文件位置 `/config/music_hud-server.toml`