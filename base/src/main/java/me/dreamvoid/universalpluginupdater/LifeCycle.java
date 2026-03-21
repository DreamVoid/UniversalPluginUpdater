package me.dreamvoid.universalpluginupdater;

import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.service.UpgradeService;
import me.dreamvoid.universalpluginupdater.upgrade.NativeUpgradeStrategy;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;

import java.text.MessageFormat;
import java.util.logging.Logger;

public class LifeCycle {
    private static IPlatformProvider platform;
    private Logger logger;

    public LifeCycle(IPlatformProvider plugin) {
        platform = plugin;
    }

    /**
     * 此方法应在插件实例化时调用，用于设置必要的运行环境，此时配置尚未初始化。
     * @param logger {@link java.util.logging.Logger} 实例。由于各平台初始化 Logger 的时机不一，因此需要一个 Logger 来辅助。
     */
    public void startUp(Logger logger){
        logger.info("准备 UniversalPluginUpdater 初始化.");

        Utils.setLogger(logger);

        logger.info("初始化任务完成.");
    }

    public void preLoad(){
        logger = platform.getPlatformLogger();

        logger.info("准备 UniversalPluginUpdater 预加载.");

        logger.info(MessageFormat.format("加载器: {0}", String.join(", ", platform.getLoaders())));
        logger.info(MessageFormat.format("Minecraft 版本: {0}", platform.getGameVersions() == null ? "通用" : String.join(", ", platform.getGameVersions())));

        UpgradeStrategyRegistry.getInstance().registerStrategy("native", new NativeUpgradeStrategy(platform));

        logger.info("预加载任务完成.");
    }

    public void postLoad(){
        logger.info("准备 UniversalPluginUpdater 后加载.");

        UpgradeStrategyRegistry.getInstance().setActiveStrategy("native"); // TODO: 从配置文件读取默认的升级策略

        // 初始化更新管理器
        UpdateManager.initialize(platform);

        logger.info("某些加载任务将在之后继续。");
        logger.info("后加载任务完成. 欢迎使用 UniversalPluginUpdater！");
    }

    public void unload(){
        logger.info("准备 UniversalPluginUpdater 卸载.");

        UpgradeService.ExecutionResult result = UpgradeService.getInstance().executePendingUpgrades();
        if (result.totalCount() > 0) {
            logger.info(MessageFormat.format("升级了 {0} 个插件，有 {1} 个插件升级失败。", result.successCount(), result.failureCount()));
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
        }

        logger.info("卸载任务完成. 感谢使用 UniversalPluginUpdater！");
    }
}
