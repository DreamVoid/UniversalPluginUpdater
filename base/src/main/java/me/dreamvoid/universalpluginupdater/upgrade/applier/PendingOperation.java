package me.dreamvoid.universalpluginupdater.upgrade.applier;

import java.nio.file.Path;

/**
 * 待执行的升级操作<br>
 * 此包内的类会被单独解压执行，仅可使用 JDK API
 *
 * @param pluginId    插件标识符
 * @param newFilePath 新插件文件路径
 * @param targetPath  目标插件文件路径
 * @param oldFilePath 当前插件文件路径，为 {@code null} 表示插件未安装
 */
public record PendingOperation(
        String pluginId,
        Path newFilePath,
        Path targetPath,
        Path oldFilePath
) { }
