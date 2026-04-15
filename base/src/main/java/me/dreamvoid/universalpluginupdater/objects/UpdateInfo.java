package me.dreamvoid.universalpluginupdater.objects;

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
        /*
         简单的版本比较：直接比较字符串
         例如 "1.0" 和 "1.1"，如果它们不相同，认为有更新
         TODO: 如果需要更复杂的版本比较逻辑（如语义化版本），可以使用专门的版本比较工具
         如果两个版本不相同，则认为存在更新
         这种简单的比较方式对大多数场景适用
        */
        return !currentVersion.equals(newVersion);
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
