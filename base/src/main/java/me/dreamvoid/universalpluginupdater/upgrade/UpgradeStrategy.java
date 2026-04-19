package me.dreamvoid.universalpluginupdater.upgrade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

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
     * 执行升级操作
     * @param pluginId 插件标识符
     * @param newFilePath 新的插件文件路径
     * @param oldFilePath 当前插件文件所在路径（若为null表示插件未安装）
     * @return 升级成功返回 true，否则返回 false
     */
    boolean upgrade(String pluginId, Path newFilePath, @Nullable Path oldFilePath);

    /**
     * 是否支持在服务器运行时安全地立刻执行升级文件操作<br>
     * 若为 true，调度层将直接执行升级并忽略 --now 参数
     * @return 支持安全升级返回 true，否则返回 false
     */
    default boolean supportSafeUpgrade() {
        return false;
    }
}
