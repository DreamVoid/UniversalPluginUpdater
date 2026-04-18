package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategy;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;

import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.*;

/**
 * 升级管理器<br>
 * 此服务由其自身实例化，并通过 {@link #instance()} 提供实例。
 */
public final class UpgradeManager {
    private static final UpgradeManager INSTANCE = new UpgradeManager();
    private static final Logger logger = Utils.getLogger();

    private final Queue<ScheduledUpdate> scheduledUpdates = new ConcurrentLinkedQueue<>();

    private UpgradeManager() {}

    public static UpgradeManager instance() {
        return INSTANCE;
    }

    /**
     * 执行升级操作
     */
    public boolean upgrade(String pluginId, Path newPluginPath, Path oldPluginPath, boolean executeNow) {
        UpgradeStrategyRegistry registry = UpgradeStrategyRegistry.getInstance();
        String strategyId = registry.getActiveStrategyId();
        UpgradeStrategy strategy = registry.getActiveStrategy();

        // 配置的更新策略不可用，回退native
        if (strategy == null) {
            logger.warning(tr("message.service.upgrade.warn.strategy-unavailable-fallback", strategyId));
            strategyId = "native";
            strategy = registry.getStrategy("native");
        }

        // native更新策略不可用
        if (strategy == null) {
            logger.severe(tr("message.service.upgrade.error.native-unavailable", pluginId));
            return false;
        }

        if (canUpgradeNow(executeNow, strategy)) {
            return executeUpgrade(pluginId, newPluginPath, oldPluginPath, strategyId);
        } else {
            scheduledUpdates.add(new ScheduledUpdate(pluginId, newPluginPath, oldPluginPath, strategyId));
            logger.info(tr("message.service.upgrade.queued", pluginId, strategyId));
            return true;
        }
    }

    /**
     * 是否可以立即执行更新操作
     */
    public boolean canUpgradeNow(boolean executeNow) {
        return canUpgradeNow(executeNow, UpgradeStrategyRegistry.getInstance().getActiveStrategy());
    }

    /**
     * 是否可以立即执行更新操作
     */
    private boolean canUpgradeNow(boolean executeNow, UpgradeStrategy strategy) {
        if (strategy == null) return false;
        if (strategy.supportSaveUpgrade()) return true;
        return executeNow && Config.Updater_AllowUpgradeNow;
    }

    /**
     * 执行所有队列升级任务
     */
    public ExecutionResult executeScheduledUpgrades() {
        int successCount = 0, failureCount = 0;

        ScheduledUpdate operation;
        while ((operation = scheduledUpdates.poll()) != null) {
            if (executeUpgrade(operation.pluginId, operation.newPluginPath, operation.oldPluginPath, operation.strategyId)) {
                successCount += 1;
            } else {
                failureCount += 1;
            }
        }

        return new ExecutionResult(successCount, failureCount);
    }

    private boolean executeUpgrade(String pluginId, Path newPluginPath, Path oldPluginPath, String strategyId) {
        try {
            UpgradeStrategyRegistry registry = UpgradeStrategyRegistry.getInstance();
            UpgradeStrategy strategy = strategyId != null ? registry.getStrategy(strategyId) : null;

            if (strategy == null) {
                strategyId = registry.getActiveStrategyId();
                strategy = registry.getActiveStrategy();
            }

            if (strategy == null) {
                logger.warning(tr("message.service.upgrade.warn.strategy-unavailable-fallback", strategyId));
                strategy = registry.getStrategy("native");
            }

            if (strategy == null) {
                logger.severe(tr("message.service.upgrade.error.native-unavailable", pluginId));
                return false;
            }

            return strategy.upgrade(pluginId, newPluginPath, oldPluginPath);
        } catch (Exception e) {
            logger.warning(tr("message.service.upgrade.execute.error.exception", pluginId, e));
            return false;
        }
    }

    private record ScheduledUpdate(
            String pluginId,
            Path newPluginPath,
            Path oldPluginPath,
            String strategyId
    ) { }

    public record ExecutionResult(
            int successCount,
            int failureCount
    ) {
        public int totalCount() {
            return successCount + failureCount;
        }
    }
}