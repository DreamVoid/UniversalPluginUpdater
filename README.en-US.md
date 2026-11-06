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

## Documents and tutorials

For first-time UPU users, reading the [UPU Help Manual](https://docs.upu.dreamvoid.me/en/) can help them get started quickly. Many questions can be answered by consulting the documentation, which also includes how UPU checks for updates to other plugins.

## Download
* Stable version
  * [Modrinth](https://modrinth.com/plugin/upu/versions)
  * [Hangar](https://hangar.papermc.io/DreamVoid/UniversalPluginUpdater/versions)
  * [GitHub Releases](https://github.com/DreamVoid/UniversalPluginUpdater/releases)

## Commands

This is just a list of some commonly used commands. For a complete list of commands, please refer to the [documentation](https://docs.upu.dreamvoid.me/en/core/commands).

| Command | Description | 
| --- | --- |
| /universalpluginupdater (/upu) | UniversalPluginUpdater main command |
| /upu update | update list of available plugins |
| /upu list | list all available plugins |
| /upu download | download existing plugins' newer version by downloading. | 
| /upu upgrade | upgrade existing plugins by downloading/installing newer version. | 
| /upu repo | configuration file repository sub-command |


## License

[GNU General Public License v3.0](https://github.com/DreamVoid/UniversalPluginUpdater/blob/main/LICENSE)

---

[DreamVoid](https://github.com/DreamVoid), Made with ❤.


## Optional backup upgrade strategy

The shipped default remains native. To keep old JARs, opt in:

    updater:
      strategy: backup
      allow-upgrade-now: true
      filename: ${originName}
      force-filename: false
      backup:
        directory: .disabled

- /upu upgrade [plugin IDs...] downloads and validates updates, then reserves them for the UPU unload/shutdown callback.
- /upu upgrade [plugin IDs...] --now replaces files immediately. It does not disable, unload, or reload plugins; restart to load the new versions.
- Old JARs keep their original filenames under plugins/.disabled/<timestamp>-<UUID>/. New JAR names are selected by the existing downloader, normally the distribution filename.
- A file replacement failure triggers restoration. Runtime errors on the next boot, configurations, and databases are outside this rollback scope.
- Unavailable backup operations fail closed and never fall back to native.

The shutdown callback runs **during JVM shutdown, not after JVM exit**. JAR locks can prevent either update mode. Moving a live JAR can affect lazy class/resource loading, so immediate replacement cannot guarantee uninterrupted operation. A plugin disable/reload that invokes UPU's unload callback can also execute queued updates.

See [backup behavior, recovery, and limitations](docs/backup-upgrade.md). Supported adapters: Paper/Purpur/Folia, Bukkit, Velocity, and BungeeCord/Waterfall. Actual server behavior still requires integration testing.
