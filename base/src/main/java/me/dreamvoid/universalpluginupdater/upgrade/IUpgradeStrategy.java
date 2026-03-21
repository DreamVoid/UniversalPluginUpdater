package me.dreamvoid.universalpluginupdater.upgrade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * 升级策略接口<br>
 * 定义了不同的插件升级方式，可由各平台或开发者自定义实现
 */
public interface IUpgradeStrategy {
    /**
     * 获取升级策略的唯一标识符
     * @return 标识符（如 "native", "bukkit"）
     */
    @NotNull
    String getId();

    /**
     * 获取升级策略的显示名称
     * @return 显示名称
     */
    @Nullable
    String getDisplayName();

    /**
     * 执行升级操作
     * @param pluginId 插件标识符
     * @param newPluginFile 新的插件文件路径
     * @param currentPluginFile 当前插件文件所在路径（若为null表示插件未安装）
     * @return 升级是否成功
     */
    boolean upgrade(String pluginId, Path newPluginFile, Path currentPluginFile);

    /**
     * 是否支持在服务器运行时安全地立刻执行升级文件操作<br>
     * 若为 true，调度层将直接执行升级并忽略 --now 参数
     * @return true 表示可安全立刻执行，false 表示建议延迟到卸载阶段
     */
    default boolean supportSaveUpgrade() {
        return false;
    }
}
