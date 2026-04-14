package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageService.tr;

/**
 * download 子命令处理器
 * 仅下载插件更新
 */
public final class DownloadCommand extends CommandHandler {
    private final Logger logger;

    public DownloadCommand(Platform platform) {
        super(platform);
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        Locale locale = context.sender().getLocale();
        try (AsyncLock ignored = AsyncLock.acquire()) {
            context.sender().broadcastMessage(tr(locale, "message.command.download.start"));

            // 获取缓存的更新信息
            UpdateManager updateManager = UpdateManager.instance();
            List<UpdateInfo> updateInfos = updateManager.getUpdateInfoList();

            // 检查是否有可下载的更新
            if (updateInfos.isEmpty()) {
                context.sender().broadcastMessage(tr(locale, "message.command.download.none"));
                return;
            }

            // 统计成功和失败的下载数
            int successCount = 0;
            int failureCount = 0;

            // 遍历每个待更新的插件，执行下载
            for (UpdateInfo updateInfo : updateInfos) {
                String pluginId = updateInfo.pluginName();
                context.sender().sendMessage(tr(locale, "message.command.download.item.start", pluginId));

                try {
                    // 获取该插件的更新实例
                    AbstractUpdate updateInstance = updateManager.getUpdateChannel(pluginId);
                    if (updateInstance == null) {
                        context.sender().sendMessage(tr(locale, "message.command.download.item.error.no-channel", pluginId));
                        failureCount++;
                        continue;
                    }

                    // 执行下载
                    if (updateInstance.download()) {
                        context.sender().sendMessage(tr(locale, "message.command.download.item.success", pluginId));
                        successCount++;
                    } else {
                        context.sender().sendMessage(tr(locale, "message.command.download.item.error.failed", pluginId));
                        failureCount++;
                    }
                } catch (Exception e) {
                    context.sender().sendMessage(tr(locale, "message.command.download.item.error.failed.reason", pluginId, e));
                    failureCount++;
                }
            }

            // 显示下载总结
            context.sender().broadcastMessage(tr(locale, "message.command.download.summary", successCount, failureCount));

        } catch (IllegalStateException e) {
            context.sender().sendMessage(tr(locale, "message.command.lock.failed"));
            context.sender().sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            logger.severe(tr("message.command.download.error.log", e));
            context.sender().broadcastMessage(tr(locale, "message.command.download.error.game"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) {
        // download命令无需参数
        return Collections.emptyList();
    }
}
