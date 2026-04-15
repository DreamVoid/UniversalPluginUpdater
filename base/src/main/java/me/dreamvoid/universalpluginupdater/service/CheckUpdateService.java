package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static me.dreamvoid.universalpluginupdater.Utils.debug;
import static me.dreamvoid.universalpluginupdater.service.LanguageService.tr;

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
        return platform.getPlugins().stream().map(this::checkPluginUpdate).filter(Objects::nonNull).collect(Collectors.toList());
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

            // 获取本地版本信息
            String localVersion = platform.getPluginVersion(pluginId);
            if (localVersion == null) {
                logger.warning(tr("message.service.check-update.error.no-local-version", pluginId));
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

            String channelType = updateInstance.getType().getIdentifier();
            debug("插件 {0} 版本: 本地={1}, 远程={2}, 渠道={3}", pluginId, localVersion, remoteVersion, channelType);
            return new UpdateInfo(pluginId, localVersion, remoteVersion, channelType);
        } catch (Exception e) {
            logger.warning(tr("message.service.check-update.error.exception", pluginId, e));
            return null;
        }
    }
}
