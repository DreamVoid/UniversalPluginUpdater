package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.LifeCycle;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategy;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.*;

/**
 * 升级管理器<br>
 * 此服务在 {@link LifeCycle#preLoad()} 通过 {@link #initialize(Platform)} 实例化，并通过 {@link #instance()} 提供实例。
 */
public final class UpgradeManager {
    private static UpgradeManager INSTANCE;
    private final Logger logger;
    private final Platform platform;

    private final Queue<UpgradeOperation> scheduledUpgrade = new ConcurrentLinkedQueue<>();

    private UpgradeManager(Platform platform) {
        this.logger = platform.getPlatformLogger();
        this.platform = platform;
    }

    public static UpgradeManager instance() {
        if(INSTANCE != null) {
            return INSTANCE;
        } else {
            throw new IllegalStateException(tr("message.service.error.not-initialized", "UpgradeManager"));
        }
    }
    
    public static void initialize(Platform platform){
        if(INSTANCE == null){
            INSTANCE = new UpgradeManager(platform);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * 执行升级操作
     */
    public boolean upgrade(String pluginId, Path newPluginPath, Path oldPluginPath, boolean executeNow) {
        UpgradeStrategyRegistry registry = UpgradeStrategyRegistry.instance();
        String strategyId = registry.getActiveStrategyId();
        UpgradeStrategy strategy = registry.getActiveStrategy();

        UpgradeOperation operation = new UpgradeOperation(pluginId, newPluginPath, oldPluginPath, strategyId);

        if (canUpgradeNow(executeNow, strategy)) {
            return executeUpgrade(operation);
        } else {
            scheduledUpgrade.add(operation);
            logger.info(tr("message.service.upgrade.queued", pluginId, strategyId));
            return true;
        }
    }

    /**
     * 是否可以立即执行更新操作
     */
    public boolean canUpgradeNow(boolean executeNow) {
        return canUpgradeNow(executeNow, UpgradeStrategyRegistry.instance().getActiveStrategy());
    }

    /**
     * 是否可以立即执行更新操作
     */
    private boolean canUpgradeNow(boolean executeNow, UpgradeStrategy strategy) {
        if (strategy == null) return false;
        if (strategy.supportSafeUpgrade()) return true;
        return executeNow && Config.Updater_AllowUpgradeNow;
    }

    /**
     * 批量执行升级操作（下载 + 升级）<br>
     * 遍历更新渠道，过滤出存在更新的渠道，下载新文件后执行通用升级操作
     * @param channels 更新渠道实例列表
     * @param executeNow 是否立即执行（false 则延迟到插件卸载阶段执行）
     * @param targetPlugins 目标插件ID白名单（小写，为空或null表示全部）
     * @return 升级执行结果统计
     */
    public ExecutionResult upgradeAll(List<AbstractUpdate> channels, boolean executeNow, List<String> targetPlugins) {
        int successCount = 0;
        int failureCount = 0;
        boolean immediate = canUpgradeNow(executeNow);

        for (AbstractUpdate channel : channels) {
            String pluginId = channel.getPluginId();

            if (targetPlugins != null && !targetPlugins.isEmpty() && !targetPlugins.contains(pluginId.toLowerCase())) {
                continue;
            }

            if (!channel.hasUpdate()) {
                continue;
            }

            logger.info(tr(immediate ? "message.command.upgrade.executing" : "message.command.upgrade.scheduling", pluginId));

            try {
                // 下载新文件
                Path newPluginFile = channel.download();
                if (newPluginFile == null || !Files.exists(newPluginFile)) {
                    logger.warning(tr("message.update.error.downloaded-file-missing", newPluginFile));
                    failureCount++;
                    continue;
                }

                // 获取当前插件文件
                Path oldPluginFile = platform.getPluginFile(pluginId);

                // 执行升级
                if (upgrade(pluginId, newPluginFile, oldPluginFile, executeNow)) {
                    logger.info(tr(immediate ? "message.command.upgrade.success.now" : "message.command.upgrade.success.queued", pluginId));
                    successCount++;
                } else {
                    logger.warning(tr("message.command.upgrade.error.failed", pluginId));
                    failureCount++;
                }
            } catch (Exception e) {
                logger.warning(tr("message.command.upgrade.error.failed.exception", pluginId, e.getMessage()));
                failureCount++;
            }
        }

        return new ExecutionResult(successCount, failureCount);
    }

    /**
     * 执行所有队列升级任务
     */
    public ExecutionResult executeScheduledUpgrades() {
        int successCount = 0, failureCount = 0;

        UpgradeOperation operation;
        while ((operation = scheduledUpgrade.poll()) != null) {
            if (executeUpgrade(operation)) {
                successCount += 1;
            } else {
                failureCount += 1;
            }
        }

        return new ExecutionResult(successCount, failureCount);
    }

    /**
     * 执行升级操作<br>
     * 此方法实际调用升级策略的 {@link UpgradeStrategy#upgrade(String, Path, Path)} 方法。
     * @param operation 升级操作
     * @return 升级是否成功
     */
    private boolean executeUpgrade(UpgradeOperation operation) {
        String pluginId = operation.pluginId();
        String strategyId = operation.strategyId();

        try {
            UpgradeStrategyRegistry registry = UpgradeStrategyRegistry.instance();
            UpgradeStrategy strategy = strategyId != null ? registry.getStrategy(strategyId) : null;

            if (strategy == null) {
                strategyId = registry.getActiveStrategyId();
                strategy = registry.getActiveStrategy();
            }

            // 配置的更新策略不可用，回退native
            if (strategy == null) {
                logger.warning(tr("message.service.upgrade.warn.strategy-unavailable-fallback", strategyId));
                strategy = registry.getStrategy("native");
            }

            // native更新策略不可用
            if (strategy == null) {
                logger.severe(tr("message.service.upgrade.error.native-unavailable", pluginId));
                return false;
            }

            return strategy.upgrade(pluginId, operation.newPluginPath(), operation.oldPluginPath());
        } catch (Exception e) {
            logger.warning(tr("message.service.upgrade.execute.error.exception", pluginId, e));
            return false;
        }
    }

    public record UpgradeOperation(
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