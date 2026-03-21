package me.dreamvoid.universalpluginupdater.upgrade;

import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Native 升级策略
 * 直接删除旧插件文件，将新文件移动到插件目录
 * 需要用户重启服务器以加载新插件
 */
public class NativeUpgradeStrategy implements IUpgradeStrategy {
    private final IPlatformProvider platform;

    public NativeUpgradeStrategy(IPlatformProvider platform) {
        this.platform = platform;
    }

    @Override
    public String getIdentifier() {
        return "native";
    }

    @Override
    public String getDisplayName() {
        return "原生";
    }

    @Override
    public boolean upgrade(String pluginId, Path newPluginFile, Path currentPluginFile) {
        try {
            // 获取插件目录（当前插件文件所在目录的父目录，通常是 plugins/）
            Path pluginDirectory = currentPluginFile != null ? currentPluginFile.getParent() : null;

            if (pluginDirectory == null || !Files.exists(pluginDirectory)) {
                platform.getPlatformLogger().warning("插件目录不存在");
                return false;
            }

            platform.getPlatformLogger().info("卸载插件: " + pluginId);
            platform.unloadPlugin(pluginId);

            // 如果当前插件文件存在，删除它
            if (Files.exists(currentPluginFile)) {
                platform.getPlatformLogger().info("删除旧插件文件: " + currentPluginFile);
                Files.delete(currentPluginFile);
            }

            // 将新文件移动到插件目录
            Path targetPath = pluginDirectory.resolve(newPluginFile.getFileName());
            platform.getPlatformLogger().info("移动新插件文件到 " + targetPath);
            Files.move(newPluginFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

            platform.getPlatformLogger().info("插件 " + pluginId + " 的文件已更新，重启服务器生效。");
            return true;
        } catch (Exception e) {
            platform.getPlatformLogger().warning("更新 " + pluginId + " 时出现异常: " + e);
            return false;
        }
    }
}
