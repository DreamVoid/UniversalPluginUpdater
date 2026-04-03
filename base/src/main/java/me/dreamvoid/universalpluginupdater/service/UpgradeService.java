package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.upgrade.IUpgradeStrategy;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;

import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * 升级执行服务
 * 支持立即升级与延迟到插件卸载阶段执行
 */
public class UpgradeService {
    private static final UpgradeService INSTANCE = new UpgradeService();
    private static final Logger logger = Utils.getLogger();

    private final Queue<PendingUpgradeOperation> pendingOperations = new ConcurrentLinkedQueue<>();

    private UpgradeService() {}

    public static UpgradeService getInstance() {
        return INSTANCE;
    }

    /**
     * 立即执行或延迟执行升级
     */
    public boolean upgrade(String pluginId, Path newPluginFile, Path currentPluginFile, boolean executeNow) {
        UpgradeStrategyRegistry registry = UpgradeStrategyRegistry.getInstance();
        String strategyId = registry.getActiveStrategyId();
        IUpgradeStrategy strategy = registry.getActiveStrategy();

        if (strategy == null) {
            logger.warning(LanguageService.instance().tr("message.service.upgrade.warn.strategy-unavailable-fallback", strategyId));
            strategyId = "native";
            strategy = registry.getStrategy("native");
        }

        if (strategy == null) {
            logger.severe(LanguageService.instance().tr("message.service.upgrade.error.native-unavailable", pluginId));
            return false;
        }

        if (canUpgradeNow(executeNow, strategy)) {
            return executeUpgrade(pluginId, newPluginFile, currentPluginFile, strategyId);
        } else {
            pendingOperations.add(new PendingUpgradeOperation(pluginId, newPluginFile, currentPluginFile, strategyId));
            logger.info(LanguageService.instance().tr("message.service.upgrade.queued", pluginId, strategyId));
            return true;
        }
    }

    /**
     * 判断在当前策略下此次升级是否会立刻执行
     */
    public boolean canUpgradeNow(boolean executeNow) {
        IUpgradeStrategy strategy = UpgradeStrategyRegistry.getInstance().getActiveStrategy();
        return canUpgradeNow(executeNow, strategy);
    }

    private boolean canUpgradeNow(boolean executeNow, IUpgradeStrategy strategy) {
        if (strategy == null) return false;
        if (strategy.supportSaveUpgrade()) return true;
        return executeNow && Config.Updater_AllowUpgradeNow;
    }

    /**
     * 在插件卸载阶段执行所有排队升级任务
     */
    public ExecutionResult executePendingUpgrades() {
        int successCount = 0, failureCount = 0;

        PendingUpgradeOperation operation;
        while ((operation = pendingOperations.poll()) != null) {
            if (executeUpgrade(operation.pluginId, operation.newPluginFile, operation.currentPluginFile, operation.strategyId)) {
                successCount += 1;
            } else {
                failureCount += 1;
            }
        }

        return new ExecutionResult(successCount, failureCount);
    }

    public int getPendingCount() {
        return pendingOperations.size();
    }

    private boolean executeUpgrade(String pluginId, Path newPluginFile, Path currentPluginFile, String preferredStrategyId) {
        try {
            UpgradeStrategyRegistry registry = UpgradeStrategyRegistry.getInstance();
            String strategyId = preferredStrategyId;
            IUpgradeStrategy strategy = strategyId != null ? registry.getStrategy(strategyId) : null;

            if (strategy == null) {
                strategyId = registry.getActiveStrategyId();
                strategy = registry.getActiveStrategy();
            }

            if (strategy == null) {
                logger.warning(LanguageService.instance().tr("message.service.upgrade.warn.strategy-unavailable-fallback", strategyId));
                strategy = registry.getStrategy("native");
            }

            if (strategy == null) {
                logger.severe(LanguageService.instance().tr("message.service.upgrade.error.native-unavailable", pluginId));
                return false;
            }

            return strategy.upgrade(pluginId, newPluginFile, currentPluginFile);
        } catch (Exception e) {
            logger.warning(LanguageService.instance().tr("message.service.upgrade.execute.error.exception", pluginId, e));
            return false;
        }
    }

    private record PendingUpgradeOperation(String pluginId, Path newPluginFile, Path currentPluginFile, String strategyId) { }

    public record ExecutionResult(int successCount, int failureCount) {
        public int totalCount() {
            return successCount + failureCount;
        }
    }
}