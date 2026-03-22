package me.dreamvoid.universalpluginupdater;

import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.service.UpgradeService;
import me.dreamvoid.universalpluginupdater.upgrade.NativeUpgradeStrategy;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;

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
        logger.info(LanguageService.instance().tr("message.lifecycle.startup.start"));

        Utils.setLogger(logger);

        logger.info(LanguageService.instance().tr("message.lifecycle.startup.finish"));
    }

    public void preLoad(){
        logger = platform.getPlatformLogger();
        LanguageService.instance().setPlatform(platform);

        logger.info(LanguageService.instance().tr("message.lifecycle.preload.start"));

        logger.info(LanguageService.instance().tr("message.lifecycle.preload.loaders", String.join(", ", platform.getLoaders())));
        logger.info(LanguageService.instance().tr("message.lifecycle.preload.gameversions", platform.getGameVersions() == null ? "通用" : String.join(", ", platform.getGameVersions())));

        UpgradeStrategyRegistry.getInstance().registerStrategy("native", new NativeUpgradeStrategy(platform));

        logger.info(LanguageService.instance().tr("message.lifecycle.preload.finish"));
    }

    public void postLoad(){
        logger.info(LanguageService.instance().tr("message.lifecycle.postload.start"));

        UpgradeStrategyRegistry.getInstance().setActiveStrategy("native"); // TODO: 从配置文件读取默认的升级策略

        // 初始化更新管理器
        UpdateManager.initialize(platform);

        logger.info(LanguageService.instance().tr("message.lifecycle.postload.moretasks"));
        logger.info(LanguageService.instance().tr("message.lifecycle.postload.finish"));
    }

    public void unload(){
        logger.info(LanguageService.instance().tr("message.lifecycle.unload.start"));

        UpgradeService.ExecutionResult result = UpgradeService.getInstance().executePendingUpgrades();
        if (result.totalCount() > 0) {
            logger.info(LanguageService.instance().tr("message.lifecycle.unload.result", result.successCount(), result.failureCount()));
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
        }

        logger.info(LanguageService.instance().tr("message.lifecycle.unload.finish"));
    }
}
