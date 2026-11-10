package me.dreamvoid.universalpluginupdater.upgrade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 升级策略接口<br>
 * 定义了不同的插件升级方式，可由各平台或开发者自定义实现
 */
public interface UpgradeStrategy {
    /**
     * 获取升级策略的唯一标识符
     * @return 标识符
     */
    @NotNull
    String getId();

    /**
     * 获取升级策略的显示名称
     * @return 显示名称
     */
    @Nullable
    default String getName() {
        return null;
    }

    /**
     * 获取 upgrade() 允许被调用的阶段
     * @return 执行阶段，默认在插件禁用阶段
     */
    @NotNull
    default ExecutionPhase getExecutionPhase() {
        return ExecutionPhase.SHUTDOWN;
    }

    /**
     * 执行一批升级任务<br>
     * 调度层保证同一策略在一次调度中只调用一次
     *
     * @param tasks 待执行的升级任务
     * @param forced 是否由 --now 强制在命令执行时立即执行
     * @return 每个任务的执行结果
     */
    @NotNull
    default List<UpgradeResult> upgrade(@NotNull List<UpgradeTask> tasks, boolean forced) {
        List<UpgradeResult> results = new ArrayList<>(tasks.size());
        for (UpgradeTask task : tasks) {
            results.add(new UpgradeResult(task.pluginId(), upgrade(task.pluginId(), task.newFilePath(), task.oldFilePath())));
        }
        return results;
    }

    /**
     * 执行单个升级任务
     *
     * @param pluginId 插件标识符
     * @param newFilePath 新的插件文件路径
     * @param oldFilePath 当前插件文件所在路径，插件未安装时为 {@code null}
     * @return 升级成功返回 true，否则返回 false
     * @deprecated 使用 {@link #upgrade(List, boolean)}
     */
    @Deprecated(forRemoval = true)
    default boolean upgrade(@NotNull String pluginId, @NotNull Path newFilePath, @Nullable Path oldFilePath) {
        throw new UnsupportedOperationException();
    }
}
