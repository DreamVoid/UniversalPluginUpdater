package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

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
        CommandSender sender = context.sender();
        Locale locale = sender.getLocale();
        try (AsyncLock ignored = AsyncLock.acquire()) {
            sender.broadcastMessage(tr(locale, "message.command.download.start"));

            // 解析指定下载的插件ID
            List<String> targetPlugins = new java.util.ArrayList<>();
            for (String arg : context.args()) {
                if (arg != null && !arg.isBlank()) {
                    targetPlugins.add(arg.toLowerCase());
                }
            }

            // 获取缓存的更新信息并提前过滤
            UpdateManager updateManager = UpdateManager.instance();
            List<UpdateInfo> updateInfos = updateManager.getUpdateInfoList().stream()
                    .filter(UpdateInfo::hasUpdate)
                    .filter(info -> targetPlugins.isEmpty() || targetPlugins.contains(info.pluginName().toLowerCase()))
                    .toList();

            // 检查是否有可下载的更新
            if (updateInfos.isEmpty()) {
                sender.broadcastMessage(tr(locale, "message.command.download.none"));
                return;
            }

            // 统计成功和失败的下载数
            int successCount = 0;
            int failureCount = 0;

            // 遍历每个待更新的插件，执行下载
            for (UpdateInfo updateInfo : updateInfos) {
                String pluginId = updateInfo.pluginName();
                
                sender.sendMessage(tr(locale, "message.command.download.item.start", pluginId));

                try {
                    // 获取该插件的更新实例
                    AbstractUpdate updateInstance = updateManager.getUpdateInstance(pluginId);
                    if (updateInstance == null) {
                        sender.sendMessage(tr(locale, "message.command.download.item.error.no-channel", pluginId));
                        failureCount++;
                        continue;
                    }

                    // 执行下载
                    if (updateInstance.download()) {
                        sender.sendMessage(tr(locale, "message.command.download.item.success", pluginId));
                        successCount++;
                    } else {
                        sender.sendMessage(tr(locale, "message.command.download.item.error.failed", pluginId));
                        failureCount++;
                    }
                } catch (Exception e) {
                    sender.sendMessage(tr(locale, "message.command.download.item.error.failed.reason", pluginId, e));
                    failureCount++;
                }
            }

            // 显示下载总结
            sender.broadcastMessage(tr(locale, "message.command.download.summary", successCount, failureCount));

        } catch (IllegalStateException e) {
            sender.sendMessage(tr(locale, "message.command.lock.failed"));
            sender.sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            logger.severe(tr("message.command.download.exception.log", e));
            sender.broadcastMessage(tr(locale, "message.command.download.exception.game"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) {
        String[] args = context.args();
        List<String> result = new java.util.ArrayList<>();
        
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < args.length - 1; i++) {
            used.add(args[i].toLowerCase());
        }

        String currentArg = args[args.length - 1].toLowerCase();
        
        UpdateManager.instance().getUpdateInfoList().stream()
                .filter(UpdateInfo::hasUpdate)
                .map(UpdateInfo::pluginName)
                .filter(name -> !used.contains(name.toLowerCase()))
                .filter(name -> name.toLowerCase().startsWith(currentArg))
                .forEach(result::add);
        return result;
    }
}
