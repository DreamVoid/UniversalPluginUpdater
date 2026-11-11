package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.LifeCycle;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import me.dreamvoid.universalpluginupdater.upgrade.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * 升级管理器<br>
 * 此服务在 {@link LifeCycle#preLoad()} 通过 {@link #initialize(Platform)} 实例化，并通过 {@link #instance()} 提供实例。
 */
public final class UpgradeManager {
    private static UpgradeManager INSTANCE;
    private final Logger logger;
    private final Platform platform;

    private final Queue<UpgradeTask> pendingUpgrades = new ConcurrentLinkedQueue<>();

    private UpgradeManager(Platform platform) {
        this.logger = platform.getPlatformLogger();
        this.platform = platform;
    }

    public static UpgradeManager instance() {
        if (INSTANCE != null) {
            return INSTANCE;
        } else {
            throw new IllegalStateException(tr("message.service.error.not-initialized", "UpgradeManager"));
        }
    }

    public static void initialize(Platform platform) {
        if (INSTANCE == null) {
            INSTANCE = new UpgradeManager(platform);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * 提交一批升级任务<br>
     * RUNTIME 策略立即执行；SHUTDOWN 策略默认排队，--now 可强制立即执行
     *
     * @param tasks      升级任务
     * @param executeNow 是否由 --now 要求立即执行
     * @return 提交结果
     */
    public ExecutionResult submit(List<UpgradeTask> tasks, boolean executeNow) {
        if (tasks == null || tasks.isEmpty()) {
            return new ExecutionResult(0, 0, false);
        }

        UpgradeStrategy strategy = UpgradeStrategyRegistry.instance().getActiveStrategy();
        if (strategy == null) {
            logger.severe(tr("message.service.upgrade.error.native-unavailable", tasks.size()));
            return new ExecutionResult(0, tasks.size(), false);
        }

        Set<String> pluginIds = tasks.stream().map(task -> task.pluginId().toLowerCase()).collect(Collectors.toSet());
        pendingUpgrades.removeIf(task -> pluginIds.contains(task.pluginId().toLowerCase()));

        boolean forced = executeNow && Config.Updater_AllowUpgradeNow;
        if (forced || strategy.getExecutionPhase() == ExecutionPhase.RUNTIME) {
            return executeBatch(strategy, tasks, forced);
        } else {
            pendingUpgrades.addAll(tasks);
            logger.info(tr("message.service.upgrade.queued", tasks.size(), strategy.getId()));
            return new ExecutionResult(tasks.size(), 0, true);
        }
    }

    /**
     * 在插件禁用阶段执行所有待处理任务
     *
     * @return 执行结果
     */
    public ExecutionResult flushPending() {
        List<UpgradeTask> tasks = new ArrayList<>();
        UpgradeTask task;
        while ((task = pendingUpgrades.poll()) != null) {
            tasks.add(task);
        }
        if (tasks.isEmpty()) {
            return new ExecutionResult(0, 0, false);
        }

        UpgradeStrategy strategy = UpgradeStrategyRegistry.instance().getActiveStrategy();
        if (strategy == null) {
            logger.severe(tr("message.service.upgrade.error.native-unavailable", tasks.size()));
            return new ExecutionResult(0, tasks.size(), false);
        }

        return executeBatch(strategy, tasks, false);
    }

    /**
     * 批量执行升级操作（下载 + 升级）<br>
     * 遍历更新渠道，过滤出存在更新的渠道，下载新文件后统一提交给当前升级策略
     *
     * @param channels      更新渠道实例列表
     * @param executeNow    是否立即执行（false 则延迟到插件卸载阶段执行）
     * @param targetPlugins 目标插件ID白名单（小写，为空或null表示全部）
     * @return 升级执行结果统计
     */
    public ExecutionResult upgradeAll(List<AbstractUpdate> channels, boolean executeNow, List<String> targetPlugins) {
        List<UpgradeTask> tasks = new ArrayList<>();
        int failureCount = 0;
        boolean immediate = canExecuteImmediately(executeNow);

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
                tasks.add(new UpgradeTask(pluginId, newPluginFile, oldPluginFile));
            } catch (Exception e) {
                logger.warning(tr("message.command.upgrade.error.failed.exception", pluginId, e.getMessage()));
                failureCount++;
            }
        }

        ExecutionResult result = submit(tasks, executeNow);
        return new ExecutionResult(result.successCount(), result.failureCount() + failureCount, result.queued());
    }

    private boolean canExecuteImmediately(boolean executeNow) {
        UpgradeStrategy strategy = UpgradeStrategyRegistry.instance().getActiveStrategy();
        if (strategy == null) return false;
        return (executeNow && Config.Updater_AllowUpgradeNow) || strategy.getExecutionPhase() == ExecutionPhase.RUNTIME;
    }

    private ExecutionResult executeBatch(UpgradeStrategy strategy, List<UpgradeTask> tasks, boolean forced) {
        if (tasks.isEmpty()) {
            return new ExecutionResult(0, 0, false);
        }

        try {
            List<UpgradeResult> results = strategy.upgrade(tasks);
            int successCount = 0;
            int failureCount = 0;
            for (UpgradeResult result : results) {
                if (result.success()) {
                    successCount++;
                } else {
                    failureCount++;
                    logger.warning(tr("message.command.upgrade.error.failed", result.pluginId()));
                }
            }
            return new ExecutionResult(successCount, failureCount, false);
        } catch (Exception e) {
            logger.warning(tr("message.service.upgrade.execute.error.exception", tasks.size(), e));
            return new ExecutionResult(0, tasks.size(), false);
        }
    }

    /**
     * 升级执行结果
     *
     * @param successCount 成功数量；{@code queued} 为 true 时表示已排队的数量
     * @param failureCount 失败数量
     * @param queued       成功数量是否代表已排队的任务
     */
    public record ExecutionResult(
            int successCount,
            int failureCount,
            boolean queued
    ) {
        public int totalCount() {
            return successCount + failureCount;
        }
    }
}