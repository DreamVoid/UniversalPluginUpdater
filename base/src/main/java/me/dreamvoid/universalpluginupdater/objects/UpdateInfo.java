package me.dreamvoid.universalpluginupdater.objects;

import org.jetbrains.annotations.NotNull;

/**
 * 待更新插件的信息
 * 包含插件名、新版本、下载链接等信息
 *
 * @param pluginName     插件名称
 * @param currentVersion 当前版本
 * @param newVersion     新版本
 * @param updateChannel  更新渠道类型
 */
public record UpdateInfo(
        String pluginName,
        String currentVersion,
        String newVersion,
        String updateChannel
) {
    @NotNull
    @Override
    public String toString() {
        return String.format("%s: %s -> %s (%s)", pluginName, currentVersion, newVersion, updateChannel);
    }
}
