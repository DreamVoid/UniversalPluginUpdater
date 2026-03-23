package me.dreamvoid.universalpluginupdater.command.sub;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.ISubCommand;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.service.UpgradeService;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * upgrade 子命令处理器
 * 执行插件更新升级（下载 + 更新）
 */
public final class UpgradeCommand implements ISubCommand {
    private final Logger logger;
    public UpgradeCommand(IPlatformProvider platform) {
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        Locale locale = context.getSender().getLocale();
        if(AsyncLock.tryAcquire()) {
            try {
                String[] subArgs = context.getSubCommandArgs();
                boolean executeNow = false;
                for (String arg : subArgs) {
                    if ("--now".equalsIgnoreCase(arg)) {
                        executeNow = true;
                    } else {
                        context.getSender().sendMessage(LanguageService.instance().tr(locale,
                                "message.command.upgrade.error.unknown-arg",
                                arg));
                    }
                }

                context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.upgrade.start"));

                // 获取缓存的更新信息
                UpdateManager updateManager = UpdateManager.getInstance();
                List<UpdateInfo> updateInfos = updateManager.getCachedUpdateInfos();

                // 检查是否有可升级的更新
                if (updateInfos.isEmpty()) {
                    context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.upgrade.none"));
                    return;
                }

                // 统计成功和失败的升级数
                int successCount = 0;
                int failureCount = 0;
                boolean scheduleUpgrade = UpgradeService.getInstance().canUpgradeNow(executeNow);

                // 遍历每个待更新的插件，执行升级
                for (UpdateInfo updateInfo : updateInfos) {
                    String pluginId = updateInfo.pluginName();
                    if (scheduleUpgrade) {
                        logger.info(LanguageService.instance().tr("message.command.upgrade.executing", pluginId));
                    } else {
                        logger.info(LanguageService.instance().tr("message.command.upgrade.scheduling", pluginId));
                    }

                    try {
                        // 获取该插件的更新实例
                        AbstractUpdate updateInstance = updateManager.getUpdateChannelForPlugin(pluginId);
                        if (updateInstance == null) {
                            logger.warning(LanguageService.instance().tr("message.command.upgrade.error.no-channel", pluginId));
                            failureCount++;
                            continue;
                        }

                        // 执行升级
                        if (updateInstance.upgrade(executeNow)) {
                            logger.info(scheduleUpgrade
                                    ? LanguageService.instance().tr("message.command.upgrade.success.now", pluginId)
                                    : LanguageService.instance().tr("message.command.upgrade.success.queued", pluginId));
                            successCount++;
                        } else {
                            logger.warning(LanguageService.instance().tr("message.command.upgrade.error.failed", pluginId));
                            failureCount++;
                        }
                    } catch (Exception e) {
                        logger.warning(LanguageService.instance().tr("message.command.upgrade.error.exception", pluginId, e.getMessage()));
                        failureCount++;
                    }
                }

                context.getSender().broadcastMessage(scheduleUpgrade
                        ? LanguageService.instance().tr(locale, "message.command.upgrade.summary.now", successCount, failureCount)
                        : LanguageService.instance().tr(locale, "message.command.upgrade.summary.queued", successCount, failureCount));
            } catch (Exception e) {
                logger.severe(LanguageService.instance().tr("message.command.upgrade.error.log", e));
                context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.upgrade.error.game"));
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
        String[] subArgs = context.getSubCommandArgs();
        if (subArgs.length <= 1) {
            if (subArgs.length == 0 || "--now".startsWith(subArgs[0])) {
                return Collections.singletonList("--now");
            }
        }
        return Collections.emptyList();
    }
}
