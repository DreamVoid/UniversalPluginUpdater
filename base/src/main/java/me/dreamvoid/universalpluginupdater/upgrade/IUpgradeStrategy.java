package me.dreamvoid.universalpluginupdater.upgrade;

import java.nio.file.Path;

/**
 * 升级策略接口
 * 定义了不同的插件升级方式，可由各平台或开发者自定义实现
 */
public interface IUpgradeStrategy {
    /**
     * 获取升级策略的唯一标识符
     * @return 标识符（如 "native", "bukkit"）
     */
    String getIdentifier();

    /**
     * 获取升级策略的显示名称
     * @return 显示名称
     */
    String getDisplayName();

    /**
     * 执行升级操作
     * @param pluginId 插件标识符
     * @param newPluginFile 新的插件文件路径
     * @param currentPluginFile 当前插件文件所在路径（若为null表示插件未安装）
     * @return 升级是否成功
     */
    boolean upgrade(String pluginId, Path newPluginFile, Path currentPluginFile);
}
