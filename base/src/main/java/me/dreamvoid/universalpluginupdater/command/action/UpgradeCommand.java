package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.service.UpgradeService;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageService.tr;

/**
 * upgrade 子命令处理器
 * 执行插件更新升级（下载 + 更新）
 */
public final class UpgradeCommand extends CommandHandler {
    private final Logger logger;

    public UpgradeCommand(Platform platform) {
        super(platform);
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        Locale locale = context.sender().getLocale();
        try(AsyncLock ignored = AsyncLock.acquire()) {
            String[] subArgs = context.args();
            boolean executeNow = false;
            for (String arg : subArgs) {
                if ("--now".equalsIgnoreCase(arg)) {
                    if (Config.Updater_AllowUpgradeNow) {
                        executeNow = true;
                    } else {
                        context.sender().sendMessage(tr(locale, "message.command.upgrade.warn.upgrade-now-disabled"));
                    }
                } else {
                    context.sender().sendMessage(tr(locale, "message.command.upgrade.error.unknown-arg", arg));
                }
            }

            context.sender().broadcastMessage(tr(locale, "message.command.upgrade.start"));

            // 获取缓存的更新信息
            UpdateManager updateManager = UpdateManager.instance();
            List<UpdateInfo> updateInfos = updateManager.getUpdateInfoList();

            // 检查是否有可升级的更新
            if (updateInfos.isEmpty()) {
                context.sender().broadcastMessage(tr(locale, "message.command.upgrade.none"));
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
                    logger.info(tr("message.command.upgrade.executing", pluginId));
                } else {
                    logger.info(tr("message.command.upgrade.scheduling", pluginId));
                }

                try {
                    // 获取该插件的更新实例
                    AbstractUpdate updateInstance = updateManager.getUpdateChannel(pluginId);
                    if (updateInstance == null) {
                        logger.warning(tr("message.command.upgrade.error.no-channel", pluginId));
                        failureCount++;
                        continue;
                    }

                    // 执行升级
                    if (updateInstance.upgrade(executeNow)) {
                        logger.info(scheduleUpgrade
                                ? tr("message.command.upgrade.success.now", pluginId)
                                : tr("message.command.upgrade.success.queued", pluginId));
                        successCount++;
                    } else {
                        logger.warning(tr("message.command.upgrade.error.failed", pluginId));
                        failureCount++;
                    }
                } catch (Exception e) {
                    logger.warning(tr("message.command.upgrade.error.exception", pluginId, e.getMessage()));
                    failureCount++;
                }
            }

            context.sender().broadcastMessage(scheduleUpgrade
                    ? tr(locale, "message.command.upgrade.summary.now", successCount, failureCount)
                    : tr(locale, "message.command.upgrade.summary.queued", successCount, failureCount));
        } catch(IllegalStateException e) {
            context.sender().sendMessage(tr(locale, "message.command.lock.failed"));
            context.sender().sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            logger.severe(tr("message.command.upgrade.error.log", e));
            context.sender().broadcastMessage(tr(locale, "message.command.upgrade.error.game"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) {
        List<String> result = new ArrayList<>();

        String[] args = context.args();
        if (args.length == 2) {
            if ("--now".startsWith(args[1]) && Config.Updater_AllowUpgradeNow) {
                result.add("--now");
            }
        }
        return result;
    }
}
