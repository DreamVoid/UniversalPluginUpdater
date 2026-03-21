<!--suppress HtmlDeprecatedAttribute -->
<div align="center">
    <h1> UniversalPluginUpdater </h1>

[![GitHub release](https://img.shields.io/github/v/release/DreamVoid/UniversalPluginUpdater?style=flat-square
)](https://github.com/DreamVoid/UniversalPluginUpdater/releases/latest)
![GitHub all releases](https://img.shields.io/github/downloads/DreamVoid/UniversalPluginUpdater/total?style=flat-square)
</div>

[简体中文](README.md) | [English](README.en-US.md)

---

## Introduction
UniversalPluginUpdater (UPU) is a Minecraft server plugin that allows you to quickly update existing server plugins, while also providing APIs to help developers easily implement automatic update functionality for their plugins.

The development of UniversalPluginUpdater was inspired by [APT](https://wiki.debian.org/zh_CN/Apt).

## Download
* Stable version
  * [Modrinth](https://modrinth.com/plugin/universalpluginupdater/versions)
  * [GitHub Releases](https://github.com/DreamVoid/UniversalPluginUpdater/releases)

## Commands and Permissions
### Commands
| Command                | Description                        | Permission                       |
|-------------------|---------------------------|--------------------------|
| /universalpluginupdater (/upu)         | UniversalPluginUpdater main command | universalpluginupdater.command |
| /upu update | update list of available plugins.                    | universalpluginupdater.command.update |
| /upu download  | download existing plugins' newer version by downloading.                  | universalpluginupdater.command.download |
| /upu upgrade  | upgrade existing plugins by downloading/installing newer version.                  | universalpluginupdater.command.upgrade |

### Permissions
| Permission                     | Description             | Default |
|--------------------------|----------------|----|
| universalpluginupdater.command | Allow use /upu | OP |
| universalpluginupdater.command.update | Allow use /upu update | OP |
| universalpluginupdater.command.download | Allow use /upu download | OP |
| universalpluginupdater.command.upgrade | Allow use /upu upgrade | OP |

## License

[GNU General Public License v3.0](https://github.com/DreamVoid/UniversalPluginUpdater/blob/main/LICENSE)

---

[DreamVoid](https://github.com/DreamVoid), Made with ❤.
