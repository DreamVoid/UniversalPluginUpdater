package me.dreamvoid.universalpluginupdater.objects;

import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.Objects;

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
        if (currentVersion == null || newVersion == null) {
            return !Objects.equals(currentVersion, newVersion);
        }

        String[] currentParts = currentVersion.split("\\.");
        String[] newParts = newVersion.split("\\.");

        int max = Math.max(currentParts.length, newParts.length);
        for (int i = 0; i < max; i++) {
            String currentPart = i < currentParts.length ? currentParts[i].trim() : "0";
            String newPart = i < newParts.length ? newParts[i].trim() : "0";

            try {
                int currentValue = Integer.parseInt(currentPart);
                int newValue = Integer.parseInt(newPart);

                if (newValue > currentValue) {
                    return true;
                } else if (newValue < currentValue) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return !currentPart.equals(newPart);
            }
        }

        return false;
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
