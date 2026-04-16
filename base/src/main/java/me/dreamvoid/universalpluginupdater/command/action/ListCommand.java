package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * list 子命令处理器
 * 列出当前已是最新的和可更新的所有插件
 */
public final class ListCommand extends CommandHandler {

    public ListCommand(Platform platform) {
        super(platform);
    }

    @Override
    public void execute(CommandContext context) {
        CommandSender sender = context.sender();
        Locale locale = sender.getLocale();

        // 参数处理
        boolean filterUpgradable = false;
        
        for (String arg : context.args()) {
            if ("--upgradable".equalsIgnoreCase(arg)) {
                filterUpgradable = true;
            } else {
                sender.sendMessage(tr(locale, "message.command.error.unknown-argument", arg));
            }
        }

        try (AsyncLock ignored = AsyncLock.acquire()) {
            UpdateManager updateManager = UpdateManager.instance();
            List<UpdateInfo> updateInfos = updateManager.getUpdateInfoList();

            if (filterUpgradable) {
                updateInfos = updateInfos.stream().filter(UpdateInfo::hasUpdate).toList();
            }

            if (updateInfos.isEmpty()) {
                sender.sendMessage(tr(locale, "message.command.list.none"));
                return;
            }

            sender.sendMessage(tr(locale, "message.command.list.header"));

            for (UpdateInfo update : updateInfos) {
                if (update.hasUpdate()) {
                    sender.sendMessage(MessageFormat.format("{0} &b({1} &7-> &b{2}) &7[{3}]",
                            update.pluginName(), update.currentVersion(), update.newVersion(), update.updateChannel()));
                } else {
                    sender.sendMessage(MessageFormat.format("{0} &a({1}) &7[{2}]",
                            update.pluginName(), update.currentVersion(), update.updateChannel()));
                }
            }

        } catch (IllegalStateException e) {
            sender.sendMessage(tr(locale, "message.command.lock.failed"));
            sender.sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            platform.getPlatformLogger().warning(tr(locale, "message.command.list.exception.log") + e);
            sender.sendMessage(tr(locale, "message.command.list.exception.game"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) {
        List<String> result = new ArrayList<>();
        String[] args = context.args();

        if (args.length == 1) {
            if ("--upgradable".startsWith(args[0].toLowerCase())) {
                result.add("--upgradable");
            }
        }
        return result;
    }
}
