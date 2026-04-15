package me.dreamvoid.universalpluginupdater.command.action;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.CommandHandler;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.service.LanguageService.tr;

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
        Locale locale = context.sender().getLocale();
        try(AsyncLock ignored = AsyncLock.acquire()){
            context.sender().broadcastMessage(tr(locale, "message.command.update.start"));

            // 获取待更新的插件列表
            UpdateManager updateManager = UpdateManager.instance();
            List<UpdateInfo> updateInfos = updateManager.checkUpdate();

            long updatableCount = updateInfos.stream().filter(UpdateInfo::hasUpdate).count();

            if (updatableCount == 0) {
                context.sender().broadcastMessage(tr(locale, "message.command.update.none"));
            } else {
                context.sender().broadcastMessage(tr(locale, "message.command.update.count", updatableCount));
            }
            
            context.sender().broadcastMessage(tr(locale, "message.command.update.next"));

        } catch (IllegalStateException e) {
            context.sender().sendMessage(tr(locale, "message.command.lock.failed"));
            context.sender().sendMessage(tr(locale, "message.command.lock.warning"));
        } catch (Exception e) {
            logger.severe(tr("message.command.update.exception.log", e));
            context.sender().broadcastMessage(tr(locale, "message.command.update.exception.game"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) {
        return Collections.emptyList();
    }
}
