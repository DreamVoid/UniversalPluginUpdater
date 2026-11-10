package me.dreamvoid.universalpluginupdater.upgrade;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * 升级任务
 *
 * @param pluginId 插件标识符
 * @param newFilePath 新插件文件路径
 * @param oldFilePath 当前插件文件路径，插件未安装时为 {@code null}
 */
public record UpgradeTask(
        String pluginId,
        Path newFilePath,
        @Nullable Path oldFilePath
) { }
