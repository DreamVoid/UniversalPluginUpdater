package me.dreamvoid.universalpluginupdater.bukkit.upgrade;

import me.dreamvoid.universalpluginupdater.upgrade.ExecutionPhase;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeResult;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategy;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeTask;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
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
    public @NotNull String getId() {
        return "bukkit";
    }

    @Override
    public String getName() {
        return "Bukkit 更新文件夹";
    }

    @Override
    public ExecutionPhase getExecutionPhase() {
        return ExecutionPhase.RUNTIME;
    }

    @NotNull
    @Override
    public List<UpgradeResult> upgrade(@NotNull List<UpgradeTask> tasks) {
        List<UpgradeResult> results = new ArrayList<>(tasks.size());
        for (UpgradeTask task : tasks) {
            results.add(new UpgradeResult(task.pluginId(), moveToUpdateFolder(task.pluginId(), task.newFilePath())));
        }
        return results;
    }

    private boolean moveToUpdateFolder(String pluginId, Path newFilePath) {
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
