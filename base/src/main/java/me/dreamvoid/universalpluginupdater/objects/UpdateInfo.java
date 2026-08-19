package me.dreamvoid.universalpluginupdater.objects;

import me.dreamvoid.universalpluginupdater.Utils;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

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

    /**
     * 是否存在更新
     * @return 如果存在更新，返回true
     */
    public boolean hasUpdate() {
        return Utils.isVersionNewer(newVersion, currentVersion);
    }

    @NotNull
    @Override
    public String toString() {
        if (hasUpdate()) {
            return MessageFormat.format("{0}: {1} -> {2} ({3})", pluginName, currentVersion, newVersion, updateChannel);
        } else {
            return MessageFormat.format("{0}: {1} ({2})", pluginName, currentVersion, updateChannel);
        }
    }
}
