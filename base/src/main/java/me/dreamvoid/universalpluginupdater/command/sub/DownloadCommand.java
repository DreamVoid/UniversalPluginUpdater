package me.dreamvoid.universalpluginupdater.command.sub;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.ISubCommand;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.plugin.UpdateInfo;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * download 子命令处理器
 * 仅下载插件更新
 */
public final class DownloadCommand implements ISubCommand {
    private final Logger logger;

    public DownloadCommand(IPlatformProvider platform) {
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        if(AsyncLock.tryAcquire()) {
            try {
                context.getSender().broadcastMessage("&7开始下载插件更新...");

                // 获取缓存的更新信息
                UpdateManager updateManager = UpdateManager.getInstance();
                List<UpdateInfo> updateInfos = updateManager.getCachedUpdateInfos();

                // 检查是否有可下载的更新
                if (updateInfos.isEmpty()) {
                    context.getSender().broadcastMessage("目前没有可下载更新的插件。使用 /upu update 检查更新。");
                    return;
                }

                // 统计成功和失败的下载数
                int successCount = 0;
                int failureCount = 0;

                // 遍历每个待更新的插件，执行下载
                for (UpdateInfo updateInfo : updateInfos) {
                    String pluginId = updateInfo.pluginName();
                    context.getSender().sendMessage(MessageFormat.format("&7正在下载 {0}...", pluginId));

                    try {
                        // 获取该插件的更新实例
                        AbstractUpdate updateInstance = updateManager.getUpdateChannelForPlugin(pluginId);
                        if (updateInstance == null) {
                            context.getSender().sendMessage(MessageFormat.format("&c无法获取插件 {0} 的更新渠道！", pluginId));
                            failureCount++;
                            continue;
                        }

                        // 执行下载
                        if (updateInstance.download()) {
                            context.getSender().sendMessage(MessageFormat.format("{0} 已下载。", pluginId));
                            successCount++;
                        } else {
                            context.getSender().sendMessage(MessageFormat.format("&c{0} 下载失败！", pluginId));
                            failureCount++;
                        }
                    } catch (Exception e) {
                        context.getSender().sendMessage(MessageFormat.format("&c{0} 下载失败: {1}", pluginId, e.getMessage()));
                        failureCount++;
                    }
                }

                // 显示下载总结
                context.getSender().broadcastMessage(MessageFormat.format("下载了 {0} 个插件，有 {1} 个插件下载失败。文件已保存到 plugins/UniversalPluginUpdater/downloads/ 文件夹。", successCount, failureCount));
            } catch (Exception e) {
                logger.severe("下载更新时出现异常: " + e);
                context.getSender().broadcastMessage("&c下载更新时出现异常，请查看控制台了解更多信息！");
            } finally {
                AsyncLock.release();
            }
        } else {
            context.getSender().sendMessage("&c无法获得锁。锁正由另一个线程持有。");
            context.getSender().sendMessage("&7请注意，通过其他手段移除锁不一定是合适的解决方案，且可能损坏您的系统。");
        }
    }

    @Override
    public List<String> getTabCompletion(CommandContext context) {
        // download命令无需参数
        return Collections.emptyList();
    }
}
