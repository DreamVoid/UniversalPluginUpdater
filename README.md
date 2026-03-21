<!--suppress HtmlDeprecatedAttribute -->
<div align="center">
    <h1> UniversalPluginUpdater </h1>

[![GitHub release](https://img.shields.io/github/v/release/DreamVoid/UniversalPluginUpdater?style=flat-square
)](https://github.com/DreamVoid/UniversalPluginUpdater/releases/latest)
![GitHub all releases](https://img.shields.io/github/downloads/DreamVoid/UniversalPluginUpdater/total?style=flat-square)
</div>

[简体中文](README.md) | [English](README.en-US.md)

---

## 介绍
UniversalPluginUpdater（UPU）是一个 Minecraft 服务端插件，能够让你在 Minecraft 服务器上快速升级现有的插件，同时提供一些 API 帮助开发者简单的为自己的插件实现自动更新功能。

## 下载
* 稳定版本
  * [Modrinth](https://modrinth.com/plugin/universalpluginupdater/versions)
  * [GitHub 发布页](https://github.com/DreamVoid/UniversalPluginUpdater/releases)

## 指令和权限
### 指令
| 命令                | 描述                        | 权限                       |
|-------------------|---------------------------|--------------------------|
| /universalpluginupdater (/upu)         | UniversalPluginUpdater 主命令 | universalpluginupdater.command |
| /upu update | 更新可用插件列表                    | universalpluginupdater.command.update |
| /upu download  | 通过 下载 来下载现有插件的更新版本                  | universalpluginupdater.command.download |
| /upu upgrade  | 通过 下载/安装 来升级现有插件到更新版本                  | universalpluginupdater.command.upgrade |

### 权限
| 权限节点                     | 描述             | 默认 |
|--------------------------|----------------|----|
| universalpluginupdater.command | 允许使用 /upu | OP |
| universalpluginupdater.command.update | 允许使用 /upu update | OP |
| universalpluginupdater.command.download | 允许使用 /upu download | OP |
| universalpluginupdater.command.upgrade | 允许使用 /upu upgrade | OP |

## 许可证

[GNU General Public License v3.0](https://github.com/DreamVoid/UniversalPluginUpdater/blob/main/LICENSE)

[DreamVoid](https://github.com/DreamVoid)，用 ❤ 制作。
