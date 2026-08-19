# UniversalPluginUpdater Agent Guide

## Project Shape

- This is a multi-module Maven project for a Minecraft plugin updater.
- `base/` contains platform-independent lifecycle, services, update channels, commands, configuration, and public extension APIs.
- `bukkit/`, `bungee/`, and `velocity/` contain platform adapters and platform-specific upgrade strategies. `bukkit/compatible/` produces the shaded compatibility artifact.
- Keep platform-neutral behavior in `base/`; implement server API calls, scheduling, plugin discovery, and file operations in the owning platform module.
- `Platform` is the boundary for platform services. `LifeCycle` owns shared startup, loading, post-loading, reload, and shutdown sequencing.

## Build And Verify

- Use the Maven wrapper from the repository root: `./mvnw -B package` on Unix-like shells or `mvnw.cmd -B package` on Windows.
- The reactor targets Java 17; CI builds with JDK 21, so use a JDK 21 environment when reproducing CI behavior.
- A package build also shades artifacts and copies final jars into the root `target/` directory. Do not treat generated `target/` files as source changes.
- Before changing a module POM, check its parent and dependency relationship in the root `pom.xml`; module artifact versions inherit `${project.parent.version}`.
- Run the narrowest useful Maven check after edits, then run the root package build for changes crossing module boundaries. There is no documented test suite, so compilation and packaging are the primary checks.

## Implementation Conventions

- Preserve Java 17 source compatibility even when using a newer local JDK.
- Normalize plugin IDs and channel keys consistently with existing code, which generally treats them case-insensitively and stores them lowercase.
- Reuse `LanguageManager` translations and existing resource files under `base/src/main/resources/lang/` for user-visible messages; do not hard-code new localized command output.
- Reuse `Utils` for shared HTTP, JSON, logging, and file-related behavior instead of adding parallel helpers.
- Update-channel implementations extend `AbstractUpdate`; built-in channel registration belongs in `UpdateChannelService` and external integrations must remain `UpdateType.Plugin` instances.
- Upgrade behavior goes through `UpgradeStrategy` and `UpgradeStrategyRegistry`; account for delayed shutdown upgrades and platform-specific safe-upgrade support.
- Platform lifecycle classes should delegate shared initialization to `LifeCycle` and register platform-specific upgrade strategies at the platform API lifecycle hook.
- Preserve existing resource filtering and generated `BuildConstants` behavior when editing build files or resources.

## Change Boundaries

- For a new command, add the shared handler under `base/.../command/action/`, register it in the shared `CommandHandler`, and only touch platform command registration when the server API requires it.
- For a new update provider, add its configuration model and `AbstractUpdate` implementation in `base/`, register it in `UpdateChannelService`, and update relevant default configuration/resources.
- For platform-specific behavior, inspect the corresponding adapter and neighboring upgrade strategy before editing shared services.
- Keep unrelated formatting, generated files, and release workflow changes out of feature fixes.

## Documentation

- User-facing commands and configuration are documented in the [Chinese README](README.md), [English README](README.en-US.md), and the linked [UPU Help Manual](https://docs.upu.dreamvoid.me/).
- CI and release packaging examples are in [.github/workflows/maven.yml](.github/workflows/maven.yml) and [.github/workflows/publish.yml](.github/workflows/publish.yml).