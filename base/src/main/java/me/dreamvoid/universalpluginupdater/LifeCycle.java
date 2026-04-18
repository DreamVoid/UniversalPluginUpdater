package me.dreamvoid.universalpluginupdater;

import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.LanguageManager;
import me.dreamvoid.universalpluginupdater.service.RepositoryManager;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.service.UpgradeManager;
import me.dreamvoid.universalpluginupdater.upgrade.NativeUpgradeStrategy;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;

import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

public class LifeCycle {
    private final Platform platform;
    private Logger logger;
    private Config config;

    public LifeCycle(Platform plugin) {
        platform = plugin;
    }

    /**
     * 此方法应在插件实例化时调用，用于设置必要的运行环境，此时配置尚未初始化。
     * @param logger {@link java.util.logging.Logger} 实例。由于各平台初始化 Logger 的时机不一，因此需要一个 Logger 来辅助。
     */
    public void startUp(Logger logger){
        logger.info(tr("message.lifecycle.startup.start"));

        Utils.setLogger(logger);

        logger.info(tr("message.lifecycle.startup.finish"));
    }

    public void preLoad(){
        logger = platform.getPlatformLogger();
        LanguageManager.setPlatform(platform);

        logger.info(tr("message.lifecycle.preload.start"));

        config = platform.getPlatformConfig();
        config.reloadConfig();

        logger.info(tr("message.lifecycle.preload.loaders", String.join(", ", platform.getLoaders())));
        logger.info(tr("message.lifecycle.preload.gameversions", platform.getGameVersions() == null ? "通用" : String.join(", ", platform.getGameVersions())));

        UpgradeStrategyRegistry.instance().registerStrategy("native", new NativeUpgradeStrategy(platform));

        // 初始化服务
        UpdateManager.initialize(platform);
        UpgradeManager.initialize(platform);
        RepositoryManager.initialize(platform);

        logger.info(tr("message.lifecycle.preload.finish"));
    }

    public void postLoad(){
        logger.info(tr("message.lifecycle.postload.start"));

        UpgradeStrategyRegistry.instance().setActiveStrategy(Config.Updater_Strategy);
        
        logger.info(tr("message.lifecycle.postload.moretasks"));
        logger.info(tr("message.lifecycle.postload.finish"));
    }

    public void unload(){
        logger.info(tr("message.lifecycle.unload.start"));

        UpgradeManager.ExecutionResult result = UpgradeManager.instance().executeScheduledUpgrades();
        if (result.totalCount() > 0) {
            logger.info(tr("message.lifecycle.unload.result", result.successCount(), result.failureCount()));
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
        }

        logger.info(tr("message.lifecycle.unload.finish"));
    }

    public void reload(){
        logger.info("准备 UniversalPluginUpdaer 重新加载...");

        config.reloadConfig();

        logger.info("重新加载完成.");
    }
}
