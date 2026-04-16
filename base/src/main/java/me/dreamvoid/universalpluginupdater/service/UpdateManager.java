package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import me.dreamvoid.universalpluginupdater.update.UpdateType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * 更新管理器
 * 提供统一的插件更新检查接口
 */
public class UpdateManager {
    private static UpdateManager instance;
    private UpdateService updateService;
    private UpdateChannelManager updateChannelManager;
    private List<UpdateInfo> cachedUpdateInfos = new ArrayList<>();  // 缓存最后一次的检查结果

    private UpdateManager() {}

    /**
     * 初始化UpdateManager
     * 应该在插件启动时由LifeCycle调用
     */
    public static synchronized void initialize(Platform platform) {
        if (instance == null) {
            instance = new UpdateManager();
            instance.updateChannelManager = new UpdateChannelManager(platform);
            instance.updateService = new UpdateService(platform, instance.updateChannelManager);
        }
    }

    /**
     * 获取UpdateManager实例
     */
    public static UpdateManager instance() {
        if (instance != null) {
            return instance;
        } else {
            throw new IllegalStateException(tr("message.service.error.update-manager-not-initialized"));
        }
    }

    /**
     * 检查所有插件的更新
     * @return 待更新的插件列表
     */
    public List<UpdateInfo> checkUpdate() {
        if (updateService == null) {
            throw new IllegalStateException(tr("message.service.error.check-update-service-not-initialized"));
        }
        if (updateChannelManager != null) {
            updateChannelManager.validateCache();
        }
        // 执行检查并缓存结果
        cachedUpdateInfos = updateService.checkUpdates();
        return cachedUpdateInfos;
    }

    /**
     * 获取缓存的更新信息列表
     * @return 最后一次检查的结果
     */
    @NotNull
    public List<UpdateInfo> getUpdateInfoList() {
        return new ArrayList<>(cachedUpdateInfos);  // 返回副本以防外部修改
    }

    /**
     * 获取指定插件的更新渠道实例，用于执行download/upgrade等操作
     * @param pluginId 插件ID
     * @return 对应的AbstractUpdate实例，若无法获取返回null
     */
    public AbstractUpdate getUpdateChannel(String pluginId) {
        if (updateChannelManager == null) {
            throw new IllegalStateException(tr("message.service.error.update-channel-manager-not-initialized"));
        }
        return updateChannelManager.getUpdateChannelForPlugin(pluginId);
    }

    /**
     * 注册外部更新实例
     * @param updateInstance {@link UpdateType#Plugin} 类型的更新实例
     */
    public static void registerUpdateInstance(AbstractUpdate updateInstance) {
        UpdateChannelManager.registerUpdateInstance(updateInstance);
    }

    /**
     * 注销外部更新实例
     * @param pluginId 插件ID
     */
    public static void unregisterUpdateInstance(String pluginId) {
        UpdateChannelManager.unregisterUpdateInstance(pluginId);
    }
}
