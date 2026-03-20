package me.dreamvoid.universalpluginupdater.plugin;

import java.net.URL;

/**
 * 待更新插件的信息
 * 包含插件名、新版本、下载链接等信息
 */
public class PendingUpdate {
    private final String pluginName;        // 插件名称
    private final String currentVersion;    // 当前版本
    private final String newVersion;        // 新版本
    private final URL downloadLink;         // 下载链接
    private final String updateChannel;     // 更新渠道类型

    public PendingUpdate(String pluginName, String currentVersion, String newVersion, 
                        URL downloadLink, String updateChannel) {
        this.pluginName = pluginName;
        this.currentVersion = currentVersion;
        this.newVersion = newVersion;
        this.downloadLink = downloadLink;
        this.updateChannel = updateChannel;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getNewVersion() {
        return newVersion;
    }

    public URL getDownloadLink() {
        return downloadLink;
    }

    public String getUpdateChannel() {
        return updateChannel;
    }

    @Override
    public String toString() {
        return String.format("%s: %s -> %s (%s)", pluginName, currentVersion, newVersion, updateChannel);
    }
}
