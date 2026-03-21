package me.dreamvoid.universalpluginupdater.bukkit.upgrade;

import me.dreamvoid.universalpluginupdater.upgrade.IUpgradeStrategy;
import org.bukkit.Bukkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Bukkit 更新文件夹升级策略
 * 使用 Bukkit 服务器的 update 文件夹来管理插件更新
 */
public class BukkitUpdateFolderStrategy implements IUpgradeStrategy {
    private final Logger logger;

    public BukkitUpdateFolderStrategy(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String getIdentifier() {
        return "bukkit";
    }

    @Override
    public String getDisplayName() {
        return "Bukkit 更新文件夹";
    }

    @Override
    public boolean upgrade(String pluginId, Path newPluginFile, Path currentPluginFile) {
        try {
            // 获取 Bukkit 的 update 文件夹
            Path updateFolder = Bukkit.getUpdateFolderFile().toPath();
            if (!Files.exists(updateFolder)) {
                logger.warning("插件目录不存在");
                Files.createDirectories(updateFolder);
            }

            // 获取新文件的文件名
            String filename = newPluginFile.getFileName().toString();

            // 将新的插件文件复制到 update 文件夹
            Path targetPath = updateFolder.resolve(filename);

            // 如果目标文件已存在，删除它
            if (Files.exists(targetPath)) {
                logger.info("删除已存在的文件 " + targetPath);
                Files.delete(targetPath);
            }

            // 复制新文件
            logger.info("移动新插件文件到 " + targetPath);
            Files.copy(newPluginFile, targetPath);

            logger.info("插件 " + pluginId + " 的文件已移动到更新目录，重启服务器生效。");
            return true;
        } catch (Exception e) {
            logger.warning("更新 " + pluginId + " 时出现异常: " + e);
            return false;
        }
    }
}
