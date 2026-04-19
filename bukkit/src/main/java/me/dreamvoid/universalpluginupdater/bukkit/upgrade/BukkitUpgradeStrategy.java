package me.dreamvoid.universalpluginupdater.bukkit.upgrade;

import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategy;
import org.bukkit.Bukkit;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * Bukkit 更新文件夹升级策略<br>
 * 使用 Bukkit 服务器的 update 文件夹来管理插件更新
 * @author DreamVoid
 */
public final class BukkitUpgradeStrategy implements UpgradeStrategy {
    private final Logger logger;

    public BukkitUpgradeStrategy(Logger logger) {
        this.logger = logger;
    }

    @Override
    public @NonNull String getId() {
        return "bukkit";
    }

    @Override
    public String getName() {
        return "Bukkit 更新文件夹";
    }

    @Override
    public boolean supportSafeUpgrade() {
        return true;
    }

    @Override
    public boolean upgrade(String pluginId, Path newFilePath, @Nullable Path oldFilePath) {
        try {
            // 获取 Bukkit 的 update 文件夹
            Path updateFolder = Bukkit.getUpdateFolderFile().toPath();
            if (!Files.exists(updateFolder)) {
                Files.createDirectories(updateFolder);
            }

            // 获取新文件的文件名
            String filename = newFilePath.getFileName().toString();

            // 将新的插件文件复制到 update 文件夹
            Path targetPath = updateFolder.resolve(filename);

            // 复制新文件
            logger.info(tr("message.strategy.bukkit.move-new-file", targetPath));
            Files.move(newFilePath, targetPath,  StandardCopyOption.REPLACE_EXISTING);

            logger.info(tr("message.strategy.bukkit.updated", pluginId));
            return true;
        } catch (Exception e) {
            logger.warning(tr("message.strategy.bukkit.exception", pluginId, e));
            return false;
        }
    }
}
