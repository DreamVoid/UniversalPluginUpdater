package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.plugin.UpdateInfo;

import java.util.List;

/**
 * 更新管理器
 * 提供统一的插件更新检查接口
 */
public class UpdateManager {
    private static UpdateManager instance;
    private CheckUpdateService checkUpdateService;

    private UpdateManager() {
    }

    /**
     * 初始化UpdateManager
     * 应该在插件启动时由LifeCycle调用
     */
    public static synchronized void initialize(IPlatformProvider platform) {
        if (instance == null) {
            instance = new UpdateManager();
            instance.checkUpdateService = new CheckUpdateService(platform);
        }
    }

    /**
     * 获取UpdateManager实例
     */
    public static UpdateManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("UpdateManager 尚未初始化");
        }
        return instance;
    }

    /**
     * 检查所有插件的更新
     * @return 待更新的插件列表
     */
    public List<UpdateInfo> checkAllPluginUpdates() {
        if (checkUpdateService == null) {
            throw new IllegalStateException("CheckUpdateService 未初始化");
        }
        return checkUpdateService.checkAllPluginUpdates();
    }
}
