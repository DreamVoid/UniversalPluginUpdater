package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.CommandSender;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * update 子命令处理器
 * 检查所有插件的更新
 */
public final class UpdateCommand extends CommandHandler {
    private final Logger logger;

    public UpdateCommand(Platform platform) {
        super(platform);
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        CommandSender sender = context.sender();
        Locale locale = sender.getLocale();
        String[] args = context.args();

        Set<String> targetGameVersions = new LinkedHashSet<>();
        boolean versionSpecified = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!"--version".equalsIgnoreCase(arg)) {
                sender.sendMessage(tr(locale, "message.command.error.unknown-argument", arg));
                return;
            }
            if (i + 1 >= args.length || args[i + 1].isBlank() || args[i + 1].startsWith("--")) {
                sender.sendMessage(tr(locale, "message.command.error.argument-missing-value", "--version"));
                return;
            }

            versionSpecified = true;
            targetGameVersions.clear();
            for (String version : args[++i].split(",")) {
                String trimmed = version.trim();
                if (!trimmed.isEmpty()) {
                    targetGameVersions.add(trimmed);
                }
            }
            if (targetGameVersions.isEmpty()) {
                sender.sendMessage(tr(locale, "message.command.error.argument-missing-value", "--version"));
                return;
            }
        }

        try(AsyncLock ignored = AsyncLock.acquire()){
            sender.broadcastMessage(tr(locale, "message.command.update.start"));
            if (versionSpecified) {
                sender.broadcastMessage(tr(locale, "message.command.update.target-version",
                        String.join(", ", targetGameVersions)));
            }

            // 获取待更新的插件列表
            UpdateManager updateManager = UpdateManager.instance();
            List<UpdateInfo> updateInfos = updateManager.checkUpdate(
                    versionSpecified ? List.copyOf(targetGameVersions) : null);

            long updatableCount = updateInfos.stream().filter(UpdateInfo::hasUpdate).count();

            if (updatableCount == 0) {
                sender.broadcastMessage(tr(locale, "message.command.update.none"));
            } else {
                sender.broadcastMessage(tr(locale, "message.command.update.count", updatableCount));
                sender.broadcastMessage(tr(locale, "message.command.update.next"));
            }
        } catch (IllegalStateException e) {
            sender.sendMessage(tr(locale, "message.command.lock.failed"));
            sender.sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            logger.severe(tr("message.command.update.exception.log", e));
            sender.broadcastMessage(tr(locale, "message.command.update.exception.game"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) {
        String[] args = context.args();
        if (args.length == 0) {
            return List.of();
        }

        for (int i = 0; i < args.length - 1; i++) {
            if ("--version".equalsIgnoreCase(args[i])) {
                return List.of();
            }
        }

        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        return "--version".startsWith(current) ? List.of("--version") : List.of();
    }
}
