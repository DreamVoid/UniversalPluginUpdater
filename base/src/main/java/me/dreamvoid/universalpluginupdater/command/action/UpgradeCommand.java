package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import me.dreamvoid.universalpluginupdater.service.UpgradeManager;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;

import java.util.*;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

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
        CommandSender sender = context.sender();
        Locale locale = sender.getLocale();
        try(AsyncLock ignored = AsyncLock.acquire()) {
            boolean executeNow = false;
            List<String> targetPlugins = new java.util.ArrayList<>();
            for (String arg : context.args()) {
                if ("--now".equalsIgnoreCase(arg)) {
                    if (Config.Updater_AllowUpgradeNow) {
                        executeNow = true;
                    } else {
                        sender.sendMessage(tr(locale, "message.command.upgrade.warn.upgrade-now-disabled"));
                    }
                } else {
                    targetPlugins.add(arg.toLowerCase());
                }
            }

            sender.broadcastMessage(tr(locale, "message.command.upgrade.start"));

            // 获取缓存的更新渠道实例，并交由 UPU 执行通用升级流程（下载 + 升级）
            UpdateManager updateManager = UpdateManager.instance();
            List<AbstractUpdate> channels = updateManager.getChannels();
            UpgradeManager.ExecutionResult result = UpgradeManager.instance().upgradeAll(channels, executeNow, targetPlugins);

            // 没有可升级的插件
            if (result.totalCount() == 0) {
                sender.broadcastMessage(tr(locale, "message.command.upgrade.none"));
                return;
            }

            // 汇总结果
            boolean scheduleUpgrade = UpgradeManager.instance().canUpgradeNow(executeNow);
            sender.broadcastMessage(tr(locale, scheduleUpgrade ? "message.command.upgrade.summary.now" : "message.command.upgrade.summary.queued", result.successCount(), result.failureCount()));
        } catch(IllegalStateException e) {
            sender.sendMessage(tr(locale, "message.command.lock.failed"));
            sender.sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            logger.severe(tr("message.command.upgrade.exception.log", e));
            sender.broadcastMessage(tr(locale, "message.command.upgrade.exception.game"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) {
        List<String> result = new ArrayList<>();
        String[] args = context.args();

        Set<String> used = new HashSet<>();
        for (int i = 0; i < args.length - 1; i++) {
            used.add(args[i].toLowerCase());
        }

        String currentArg = args[args.length - 1].toLowerCase();

        // 检查之前是否已经输入过 --now
        if (!used.contains("--now") && "--now".startsWith(currentArg) && Config.Updater_AllowUpgradeNow) {
            result.add("--now");
        }

        UpdateManager.instance().getChannels().stream()
                .filter(AbstractUpdate::hasUpdate)
                .map(AbstractUpdate::getPluginId)
                .filter(name -> !used.contains(name.toLowerCase()))
                .filter(name -> name.toLowerCase().startsWith(currentArg))
                .forEach(result::add);
        return result;
    }
}
