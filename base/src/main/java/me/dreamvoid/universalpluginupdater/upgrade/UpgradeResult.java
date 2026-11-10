package me.dreamvoid.universalpluginupdater.upgrade;

/**
 * 升级结果
 *
 * @param pluginId 插件标识符
 * @param success 是否成功
 */
public record UpgradeResult(String pluginId, boolean success) { }
