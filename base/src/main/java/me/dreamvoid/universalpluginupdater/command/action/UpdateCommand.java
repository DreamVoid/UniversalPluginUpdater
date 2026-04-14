package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.ICommandHandler;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageService.tr;

/**
 * update 子命令处理器
 * 检查所有插件的更新
 */
public final class UpdateCommand implements ICommandHandler {
    private final Logger logger;

    public UpdateCommand(IPlatformProvider platform) {
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        Locale locale = context.sender().getLocale();
        try(AsyncLock ignored = AsyncLock.acquire()){
            context.sender().broadcastMessage(tr(locale, "message.command.update.start"));

            // 获取待更新的插件列表
            UpdateManager updateManager = UpdateManager.instance();
            List<UpdateInfo> updateInfos = updateManager.checkUpdate();

            if (updateInfos.isEmpty()) {
                context.sender().broadcastMessage(tr(locale, "message.command.update.none"));
                return;
            }

            // 显示可更新的插件数量
            context.sender().broadcastMessage(tr(locale,
                    "message.command.update.count",
                    updateInfos.size()));

            // 详细列出每个可更新的插件
            for (UpdateInfo update : updateInfos) {
                String message = MessageFormat.format("  &7- &b{0} &f({1} &7-> &f{2}) &7[{3}]", update.pluginName(), update.currentVersion(), update.newVersion(), update.updateChannel());
                context.sender().sendMessage(message);
            }

            context.sender().broadcastMessage(tr(locale, "message.command.update.next"));

        } catch (IllegalStateException e) {
            context.sender().sendMessage(tr(locale, "message.command.lock.failed"));
            context.sender().sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            logger.severe(tr("message.command.update.error.log", e));
            context.sender().broadcastMessage(tr(locale, "message.command.update.error.game"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) {
        return Collections.emptyList();
    }
}
