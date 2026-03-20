package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.plugin.PendingUpdate;
import me.dreamvoid.universalpluginupdater.plugin.UpdateChannelManager;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 检查更新服务
 * 负责检查所有已安装插件的更新
 */
public class CheckUpdateService {
    private final IPlatformProvider platform;
    private final UpdateChannelManager channelManager;
    private final Logger logger;

    public CheckUpdateService(IPlatformProvider platform) {
        this.platform = platform;
        this.channelManager = new UpdateChannelManager(platform);
        this.logger = Utils.getLogger();
    }

    /**
     * 检查所有已安装插件的更新
     * @return 待更新的插件列表
     */
    public List<PendingUpdate> checkAllPluginUpdates() {
        List<PendingUpdate> pendingUpdates = new ArrayList<>();
        
        // 获取所有已安装的插件ID
        List<String> installedPlugins = platform.getPlugins();
        if (installedPlugins == null || installedPlugins.isEmpty()) {
            return pendingUpdates;
        }

        // 遍历每个已安装的插件
        for (String pluginId : installedPlugins) {
            PendingUpdate update = checkPluginUpdate(pluginId);
            if (update != null) {
                pendingUpdates.add(update);
            }
        }

        return pendingUpdates;
    }

    /**
     * 检查单个插件的更新
     * @param pluginId 插件标识符（小写）
     * @return 如果有更新则返回PendingUpdate对象，否则返回null
     */
    private PendingUpdate checkPluginUpdate(String pluginId) {
        try {
            // 获取该插件对应的更新实例
            AbstractUpdate updateInstance = channelManager.getUpdateChannelForPlugin(pluginId);
            if (updateInstance == null) {
                // 该插件没有配置更新渠道
                return null;
            }

            // 获取远程版本信息
            String remoteVersion = updateInstance.getVersion();
            if (remoteVersion == null) {
                logger.warning(String.format("无法获取插件 %s 的远程版本信息", pluginId));
                return null;
            }

            // 获取本地版本信息
            String localVersion = getLocalPluginVersion(pluginId);
            if (localVersion == null) {
                logger.warning(String.format("无法获取插件 %s 的本地版本信息", pluginId));
                return null;
            }

            // 比较版本
            if (isUpdateAvailable(localVersion, remoteVersion)) {
                // 获取更新渠道类型
                String channelType = updateInstance.updateType.getIdentifier();

                // 注意：不在此处获取下载链接，下载链接仅在download命令时需要
                // 这样可以避免检查更新时进行不必要的网络请求
                return new PendingUpdate(pluginId, localVersion, remoteVersion, null, channelType);
            }

            return null;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning(String.format("检查插件 %s 的更新时出错: %s", pluginId, e.getMessage()));
            }
            return null;
        }
    }

    /**
     * 获取本地插件的版本号
     * 默认实现返回null，由平台实现具体的版本获取逻辑
     * @param pluginId 插件标识符
     * @return 版本号，如果无法获取返回null
     */
    private String getLocalPluginVersion(String pluginId) {
        return platform.getPluginVersion(pluginId);
    }

    /**
     * 判断是否存在更新
     * 比较本地版本和远程版本
     * @param localVersion 本地版本
     * @param remoteVersion 远程版本
     * @return 如果存在更新返回true
     */
    private boolean isUpdateAvailable(String localVersion, String remoteVersion) {
        // 简单的版本比较：直接比较字符串
        // 例如 "1.0" 和 "1.1"，如果它们不相同，认为有更新
        // TODO: 如果需要更复杂的版本比较逻辑（如语义化版本），可以使用专门的版本比较工具
        
        // 如果两个版本不相同，则认为存在更新
        // 这种简单的比较方式对大多数场景适用
        if (localVersion.equals(remoteVersion)) {
            return false; // 版本相同，没有更新
        }
        
        return true; // 版本不同，存在更新
    }
}
