package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.LifeCycle;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import me.dreamvoid.universalpluginupdater.update.UpdateType;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * 更新管理器<br>
 * 提供统一的插件更新检查接口<br>
 * 此服务在 {@link LifeCycle#preLoad()} 通过 {@link #initialize(Platform)} 实例化，并通过 {@link #instance()} 提供实例。
 */
public final class UpdateManager {
    private static UpdateManager INSTANCE;
    private final Platform platform;
    private final UpdateService updateService;
    private final UpdateChannelService updateChannelService;

    private List<UpdateInfo> cachedUpdateInfos = new ArrayList<>();  // 缓存最后一次的检查结果

    private UpdateManager(Platform platform) {
        this.platform = platform;
        updateChannelService = new UpdateChannelService(platform);
        updateService = new UpdateService(platform, updateChannelService);
    }

    /**
     * 初始化UpdateManager
     * 应该在插件启动时由LifeCycle调用
     */
    public static synchronized void initialize(Platform platform) {
        if (INSTANCE == null) {
            INSTANCE = new UpdateManager(platform);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * 获取UpdateManager实例
     */
    public static UpdateManager instance() {
        if (INSTANCE != null) {
            return INSTANCE;
        } else {
            throw new IllegalStateException(tr("message.service.error.not-initialized", "UpdateManager"));
        }
    }

    /**
     * 检查所有插件的更新
     * @return 待更新的插件列表
     */
    public List<UpdateInfo> checkUpdate() {
        updateChannelService.validateCache();
        cachedUpdateInfos = updateService.checkUpdates();
        return new ArrayList<>(cachedUpdateInfos);
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
     * 获取指定插件的更新渠道实例，用于执行download等操作
     * @param pluginId 插件ID
     * @return 对应的AbstractUpdate实例，若无法获取返回null
     */
    public AbstractUpdate getUpdateInstance(String pluginId, String channelId) {
        return updateChannelService.getUpdateInstance(pluginId, channelId);
    }

    /**
     * 获取当前已检查（缓存）的更新渠道实例列表<br>
     * 每个插件对应一个"成功渠道"的实例，供执行下载/升级等操作时遍历
     * @return 更新渠道实例列表
     */
    @NotNull
    public List<AbstractUpdate> getChannels() {
        return getUpdateInfoList().stream()
                .map(info -> updateChannelService.getUpdateInstance(info.pluginName(), info.updateChannel()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 获取平台实例<br>
     * 供第三方更新渠道获取平台能力（如下载目录、插件版本等）
     * @return 平台实例
     */
    @NotNull
    public Platform getPlatform() {
        return platform;
    }

    /**
     * 获取下载文件保存目录<br>
     * 内置更新渠道与第三方更新渠道统一通过此方法获取下载目录
     * @return 下载目录
     */
    @NotNull
    public Path getDownloadPath() {
        return platform.getDataPath().resolve("downloads");
    }

    /**
     * 注册外部更新实例
     * @param updateInstance {@link UpdateType#Plugin} 类型的更新实例
     * @throws IllegalArgumentException updateInstance 为 null 时<br>{@link AbstractUpdate#getPluginId()} 为 null 时<br>{@link AbstractUpdate#getType()} 不为 {@link UpdateType#Plugin} 时
     */
    public static void registerUpdateInstance(AbstractUpdate updateInstance) throws IllegalArgumentException {
        UpdateChannelService.registerInstance(updateInstance);
    }

    /**
     * 注销外部更新实例
     * @param pluginId 插件ID
     */
    public static void unregisterUpdateInstance(String pluginId) {
        UpdateChannelService.unregisterUpdateInstance(pluginId);
    }
}
