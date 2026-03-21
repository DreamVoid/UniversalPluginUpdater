package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.plugin.UpdateInfo;
import me.dreamvoid.universalpluginupdater.plugin.UpdateChannelManager;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.text.MessageFormat;
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
    public List<UpdateInfo> checkAllPluginUpdates() {
        List<UpdateInfo> updateInfos = new ArrayList<>();
        
        // 获取所有已安装的插件ID
        List<String> installedPlugins = platform.getPlugins();
        if (installedPlugins == null || installedPlugins.isEmpty()) {
            return updateInfos;
        }

        // 遍历每个已安装的插件
        for (String pluginId : installedPlugins) {
            UpdateInfo update = checkPluginUpdate(pluginId);
            if (update != null) {
                updateInfos.add(update);
            }
        }

        return updateInfos;
    }

    /**
     * 检查单个插件的更新
     * @param pluginId 插件标识符（小写）
     * @return 如果有更新则返回PendingUpdate对象，否则返回null
     */
    private UpdateInfo checkPluginUpdate(String pluginId) {
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
                logger.warning(MessageFormat.format("无法获取插件 {0} 的远程版本信息！", pluginId));
                return null;
            }

            // 获取本地版本信息
            String localVersion = getLocalPluginVersion(pluginId);
            if (localVersion == null) {
                logger.warning(MessageFormat.format("无法获取插件 {0} 的本地版本信息！", pluginId));
                return null;
            }

            // 比较版本
            if (hasUpdate(localVersion, remoteVersion)) {
                String channelType = updateInstance.updateType.getIdentifier();
                return new UpdateInfo(pluginId, localVersion, remoteVersion, channelType);
            }

            return null;
        } catch (Exception e) {
            logger.warning(MessageFormat.format("检查插件 {0} 的更新时出错: {1}", pluginId, e));
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
    private boolean hasUpdate(String localVersion, String remoteVersion) {
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
