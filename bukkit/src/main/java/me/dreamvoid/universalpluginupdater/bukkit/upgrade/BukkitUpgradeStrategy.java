package me.dreamvoid.universalpluginupdater.bukkit.upgrade;

import me.dreamvoid.universalpluginupdater.service.LanguageService;
import me.dreamvoid.universalpluginupdater.upgrade.IUpgradeStrategy;
import org.bukkit.Bukkit;
import org.jspecify.annotations.NonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Bukkit 更新文件夹升级策略<br>
 * 使用 Bukkit 服务器的 update 文件夹来管理插件更新
 * @author DreamVoid
 */
public class BukkitUpgradeStrategy implements IUpgradeStrategy {
    private final Logger logger;

    public BukkitUpgradeStrategy(Logger logger) {
        this.logger = logger;
    }

    @Override
    public @NonNull String getId() {
        return "bukkit";
    }

    @Override
    public String getDisplayName() {
        return "Bukkit 更新文件夹";
    }

    @Override
    public boolean supportSaveUpgrade() {
        return true;
    }

    @Override
    public boolean upgrade(String pluginId, Path newPluginFile, Path currentPluginFile) {
        try {
            // 获取 Bukkit 的 update 文件夹
            Path updateFolder = Bukkit.getUpdateFolderFile().toPath();
            if (!Files.exists(updateFolder)) {
                Files.createDirectories(updateFolder);
            }

            // 获取新文件的文件名
            String filename = newPluginFile.getFileName().toString();

            // 将新的插件文件复制到 update 文件夹
            Path targetPath = updateFolder.resolve(filename);

            // 复制新文件
            logger.info(LanguageService.instance().tr("message.strategy.bukkit.move-new-file", targetPath));
            Files.move(newPluginFile, targetPath,  StandardCopyOption.REPLACE_EXISTING);

            logger.info(LanguageService.instance().tr("message.strategy.bukkit.updated", pluginId));
            return true;
        } catch (Exception e) {
            logger.warning(LanguageService.instance().tr("message.strategy.bukkit.error.exception", pluginId, e));
            return false;
        }
    }
}
