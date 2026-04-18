package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static me.dreamvoid.universalpluginupdater.Utils.debug;
import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * 检查更新服务<br>
 * 负责检查所有已安装插件的更新<br>
 * 此服务在 {@link UpdateManager} 实例化，并由其内部调用
 */
public final class UpdateService {
    private final Platform platform;
    private final UpdateChannelService channelManager;
    private final Logger logger;

    UpdateService(Platform platform, UpdateChannelService channelManager) {
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
    @Nullable
    private UpdateInfo checkPluginUpdate(String pluginId) {
        try {
            // 获取该插件对应的渠道候选并按顺序尝试
            List<ChannelConfig> candidates = channelManager.getChannelCandidates(pluginId);
            if (candidates.isEmpty()) {
                debug("插件 {0} 无更新渠道", pluginId);
                return null;
            }

            // 获取本地版本信息
            String localVersion = platform.getPluginVersion(pluginId);
            if (localVersion == null) {
                logger.warning(tr("message.service.check-update.error.no-local-version", pluginId));
                return null;
            }

            for (ChannelConfig candidateConfig : candidates) {
                AbstractUpdate instance = channelManager.getUpdateInstance(pluginId, candidateConfig);
                if (instance == null) {
                    continue;
                }

                if (!instance.update()) {
                    String failedType = instance.getType() == null ? "unknown" : instance.getType().getIdentifier();
                    debug("插件 {0} 渠道 {1} 更新检查失败，尝试下一个渠道", pluginId, failedType);
                    continue;
                }

                // 获取缓存的远程版本信息
                String remoteVersion = instance.getVersion();
                if (remoteVersion == null) {
                    logger.warning(tr("message.service.check-update.error.no-remote-version", pluginId));
                    return null;
                }

                String channelType = instance.getType().getIdentifier();
                debug("插件 {0} 版本: 本地={1}, 远程={2}, 渠道={3}", pluginId, localVersion, remoteVersion, channelType);
                return new UpdateInfo(pluginId, localVersion, remoteVersion, channelType);
            }


            logger.warning(tr("message.service.check-update.error", pluginId));
            return null;
        } catch (Exception e) {
            logger.warning(tr("message.service.check-update.error.exception", pluginId, e));
            return null;
        }
    }
}
