package me.dreamvoid.universalpluginupdater.upgrade;

import me.dreamvoid.universalpluginupdater.Utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Native 升级策略
 * 直接删除旧插件文件，将新文件移动到插件目录
 * 需要用户重启服务器以加载新插件
 */
public class NativeUpgradeStrategy implements IUpgradeStrategy {
    private static final Logger logger = Utils.getLogger();

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
                logger.warning("Plugin directory not found");
                return false;
            }

            // 如果当前插件文件存在，删除它
            if (Files.exists(currentPluginFile)) {
                Files.delete(currentPluginFile);
                logger.info("Deleted old plugin file: " + currentPluginFile);
            }

            // 将新文件移动到插件目录
            Path targetPath = pluginDirectory.resolve(newPluginFile.getFileName());
            Files.move(newPluginFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Moved new plugin file to: " + targetPath);
            logger.info("Please restart the server to load the new plugin");

            return true;
        } catch (Exception e) {
            logger.warning("Native upgrade failed: " + e);
            return false;
        }
    }
}
