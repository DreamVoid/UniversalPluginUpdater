package me.dreamvoid.universalpluginupdater.upgrade;

import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.logging.Logger;

/**
 * Native 升级策略<br>
 * 卸载现有插件并删除旧插件文件，将新文件移动到插件目录
 * @author DreamVoid
 */
public class NativeUpgradeStrategy implements IUpgradeStrategy {
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
    public boolean upgrade(String pluginId, Path newPluginFile, Path currentPluginFile) {
        try {
            // 获取插件目录（当前插件文件所在目录的父目录，通常是 plugins/）
            Path pluginDirectory = currentPluginFile != null ? currentPluginFile.getParent() : null;

            if (pluginDirectory == null || !Files.exists(pluginDirectory)) {
                logger.warning("插件目录不存在！");
                return false;
            }

            if (platform.unloadPlugin(pluginId)) {
                logger.info(MessageFormat.format("卸载插件 {0}", pluginId));
            } else {
                logger.warning(MessageFormat.format("插件 {0} 卸载失败！", pluginId));
            }

            // 如果当前插件文件存在，删除它
            if(Files.deleteIfExists(currentPluginFile)){
                logger.info(MessageFormat.format("删除旧插件文件 {0}", currentPluginFile));
            }

            // 将新文件移动到插件目录
            Path targetPath = pluginDirectory.resolve(newPluginFile.getFileName());
            logger.info(MessageFormat.format("移动新插件文件到 {0}", targetPath));
            Files.move(newPluginFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

            logger.info(MessageFormat.format("插件 {0} 已更新，重启服务器生效。", pluginId));
            return true;
        } catch (Exception e) {
            logger.warning(MessageFormat.format("更新 {0} 时出现异常: {1}", pluginId, e));
            return false;
        }
    }
}
