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

UniversalPluginUpdater 的开发受到 [APT](https://wiki.debian.org/zh_CN/Apt) 的启发。

## 文档和教程

对于初次使用 UPU 的用户来说，阅读 [UPU 帮助手册](https://docs.upu.dreamvoid.me/)能够快速开始使用 UPU，许多问题也能够通过查看文档得到解答，其中也包含了 UPU 如何检查其他插件的更新。

## 下载
* 稳定版本
  * [Modrinth](https://modrinth.com/plugin/upu/versions)
  * [Hangar](https://hangar.papermc.io/DreamVoid/UniversalPluginUpdater/versions)
  * [GitHub 发布页](https://github.com/DreamVoid/UniversalPluginUpdater/releases)

## 命令

此处仅列出了几个常用命令，要查看完整命令列表，请查阅[文档](https://docs.upu.dreamvoid.me/core/commands)。

| 命令 | 描述 |
|-------------------|---------------------------|
| /universalpluginupdater (/upu) | UniversalPluginUpdater 主命令 | 
| /upu update | 更新可用插件列表 |
| /upu list | 列出可用插件 |
| /upu download | 通过 下载 来下载现有插件的更新版本 |
| /upu upgrade | 通过 下载/安装 来升级现有插件到更新版本 |
| /upu repo | 配置仓库子命令 |

## 许可证

[GNU General Public License v3.0](https://github.com/DreamVoid/UniversalPluginUpdater/blob/main/LICENSE)

---

[DreamVoid](https://github.com/DreamVoid)，用 ❤ 制作。


## 可选的 backup 更新策略

默认仍使用 native。需要保留旧 JAR 时，可选择：

    updater:
      strategy: backup
      allow-upgrade-now: true
      filename: ${originName}
      force-filename: false
      backup:
        directory: .disabled

- /upu upgrade [插件 ID...]：下载、验证并预约，在 UPU 卸载/服务器停止回调中替换文件。
- /upu upgrade [插件 ID...] --now：立即替换磁盘文件，不禁用、卸载或重载插件；重启后加载新版。
- 旧 JAR 保留原文件名，保存到 plugins/.disabled/<时间戳>-<UUID>/。新版名称由既有下载流程决定，通常使用发布方文件名。
- 文件替换失败时尝试恢复；下次启动的运行错误、配置与数据库不属于回滚范围。
- backup 不可用时直接失败，绝不回退到 native。

停止时更新发生在 **JVM 退出过程中的回调内，而不是 JVM 完全退出之后**。JAR 文件锁可能导致任一种模式失败；立即移动运行中的 JAR 也可能影响后续类/资源读取。触发 UPU 卸载回调的插件禁用或 reload 也可能执行预约任务。

更多内容见 [更新、恢复与限制说明](docs/backup-upgrade.md)。面向 Paper/Purpur/Folia、Bukkit、Velocity、BungeeCord/Waterfall；实际服务器行为仍需集成测试。

## 语言

已内置日语消息。在 config.yml 中设置 `language: ja-JP`（或 `ja`），然后执行 `/upu reload` 或重启服务器。`language: system` 使用 JVM 的系统语言。显式设置的 JVM 参数 `-Dupu.locale=...` 优先于配置文件。
