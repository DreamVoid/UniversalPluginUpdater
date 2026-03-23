package me.dreamvoid.universalpluginupdater.command.sub;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.ISubCommand;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
        Locale locale = context.getSender().getLocale();
        if(AsyncLock.tryAcquire()) {
            try {
                context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.download.start"));

                // 获取缓存的更新信息
                UpdateManager updateManager = UpdateManager.getInstance();
                List<UpdateInfo> updateInfos = updateManager.getCachedUpdateInfos();

                // 检查是否有可下载的更新
                if (updateInfos.isEmpty()) {
                    context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.download.none"));
                    return;
                }

                // 统计成功和失败的下载数
                int successCount = 0;
                int failureCount = 0;

                // 遍历每个待更新的插件，执行下载
                for (UpdateInfo updateInfo : updateInfos) {
                    String pluginId = updateInfo.pluginName();
                    context.getSender().sendMessage(LanguageService.instance().tr(locale, "message.command.download.item.start", pluginId));

                    try {
                        // 获取该插件的更新实例
                        AbstractUpdate updateInstance = updateManager.getUpdateChannelForPlugin(pluginId);
                        if (updateInstance == null) {
                            context.getSender().sendMessage(LanguageService.instance().tr(locale, "message.command.download.item.error.no-channel", pluginId));
                            failureCount++;
                            continue;
                        }

                        // 执行下载
                        if (updateInstance.download()) {
                            context.getSender().sendMessage(LanguageService.instance().tr(locale, "message.command.download.item.success", pluginId));
                            successCount++;
                        } else {
                            context.getSender().sendMessage(LanguageService.instance().tr(locale, "message.command.download.item.error.failed", pluginId));
                            failureCount++;
                        }
                    } catch (Exception e) {
                        context.getSender().sendMessage(LanguageService.instance().tr(locale,
                                "message.command.download.item.error.failed.reason",
                                pluginId,
                                e.getMessage()));
                        failureCount++;
                    }
                }

                // 显示下载总结
                context.getSender().broadcastMessage(LanguageService.instance().tr(locale,
                    "message.command.download.summary",
                        successCount,
                        failureCount));
            } catch (Exception e) {
                logger.severe(LanguageService.instance().tr("message.command.download.error.log", e));
                context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.download.error.game"));
            } finally {
                AsyncLock.release();
            }
        } else {
            context.getSender().sendMessage(LanguageService.instance().tr(locale, "message.command.lock.failed"));
            context.getSender().sendMessage(LanguageService.instance().tr(locale, "message.command.lock.warning"));
        }
    }

    @Override
    public List<String> getTabCompletion(CommandContext context) {
        // download命令无需参数
        return Collections.emptyList();
    }
}
