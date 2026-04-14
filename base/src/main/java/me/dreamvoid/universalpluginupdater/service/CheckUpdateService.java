package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.Utils.debug;
import static me.dreamvoid.universalpluginupdater.service.LanguageService.*;

/**
 * 检查更新服务
 * 负责检查所有已安装插件的更新
 */
public class CheckUpdateService {
    private final Platform platform;
    private final UpdateChannelManager channelManager;
    private final Logger logger;

    public CheckUpdateService(Platform platform, UpdateChannelManager channelManager) {
        this.platform = platform;
        this.channelManager = channelManager;
        this.logger = platform.getPlatformLogger();
    }

    /**
     * 检查所有已安装插件的更新
     * @return 待更新的插件列表
     */
    public List<UpdateInfo> checkUpdates() {
        List<UpdateInfo> updateInfos = new ArrayList<>();
        
        // 获取所有已安装的插件ID
        List<String> installedPlugins = platform.getPlugins();
        if (installedPlugins == null || installedPlugins.isEmpty()) {
            debug("平台返回的插件列表为空");
            return updateInfos;
        }

        debug("检查 {0} 个插件的更新", installedPlugins.size());

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
     * @return 如果有更新则返回UpdateInfo对象，否则返回null
     */
    private UpdateInfo checkPluginUpdate(String pluginId) {
        try {
            // 获取该插件对应的更新实例
            AbstractUpdate updateInstance = channelManager.getUpdateChannelForPlugin(pluginId);
            if (updateInstance == null) {
                // 该插件没有配置更新渠道
                debug("插件 {0} 无更新渠道", pluginId);
                return null;
            }

            // 执行更新检查，联网获取最新版本信息
            if (!updateInstance.update()) {
                logger.warning(tr("message.service.check-update.error", pluginId));
                return null;
            }

            // 获取缓存的远程版本信息
            String remoteVersion = updateInstance.getVersion();
            if (remoteVersion == null) {
                logger.warning(tr("message.service.check-update.error.no-remote-version", pluginId));
                return null;
            }

            // 获取本地版本信息
            String localVersion = getLocalPluginVersion(pluginId);
            if (localVersion == null) {
                logger.warning(tr("message.service.check-update.error.no-local-version", pluginId));
                return null;
            }

            debug("插件 {0} 版本比较: 本地={1}, 远程={2}", pluginId, localVersion, remoteVersion);

            // 比较版本
            if (hasUpdate(localVersion, remoteVersion)) {
                String channelType = updateInstance.getUpdateType().getIdentifier();
                debug("找到插件 {0} 的更新，使用渠道 {1}", pluginId, channelType);
                return new UpdateInfo(pluginId, localVersion, remoteVersion, channelType);
            }

            debug("插件 {0} 已是最新.", pluginId);

            return null;
        } catch (Exception e) {
            logger.warning(tr("message.service.check-update.error.exception", pluginId, e));
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
