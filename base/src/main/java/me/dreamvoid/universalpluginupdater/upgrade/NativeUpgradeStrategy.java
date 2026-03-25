package me.dreamvoid.universalpluginupdater.upgrade;

import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Native 升级策略<br>
 * 卸载现有插件并删除旧插件文件，将新文件移动到插件目录
 * @author DreamVoid
 */
public final class NativeUpgradeStrategy implements IUpgradeStrategy {
    private final IPlatformProvider platform;
    private final Logger logger;

    public NativeUpgradeStrategy(IPlatformProvider platform) {
        this.platform = platform;
        this.logger = platform.getPlatformLogger();
    }

    @NotNull
    @Override
    public String getId() {
        return "native";
    }

    @Override
    public String getDisplayName() {
        return "原生";
    }

    @Override
    public boolean upgrade(String pluginId, Path newFilePath, @Nullable Path oldFilePath) {
        try {
            // 获取插件目录（当前插件文件所在目录的父目录，通常是 plugins/）
            Path pluginDirectory = oldFilePath != null ? oldFilePath.getParent() : null;

            if (pluginDirectory == null || !Files.exists(pluginDirectory)) {
                logger.warning(LanguageService.instance().tr("message.strategy.native.error.plugin-directory-missing"));
                return false;
            }

            if (platform.unloadPlugin(pluginId)) {
                logger.info(LanguageService.instance().tr("message.strategy.native.unload", pluginId));
            } else {
                logger.warning(LanguageService.instance().tr("message.strategy.native.error.unload-failed", pluginId));
            }

            // 将新文件移动到插件目录
            Path targetPath = pluginDirectory.resolve(newFilePath.getFileName());
            logger.info(LanguageService.instance().tr("message.strategy.native.move-new-file", targetPath));
            Files.move(newFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            logger.info(LanguageService.instance().tr("message.strategy.native.updated", pluginId));
            return true;
        } catch (Exception e) {
            logger.warning(LanguageService.instance().tr("message.strategy.native.error.exception", pluginId, e));
            return false;
        }
    }
}
