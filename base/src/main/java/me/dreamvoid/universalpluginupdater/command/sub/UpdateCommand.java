package me.dreamvoid.universalpluginupdater.command.sub;

import me.dreamvoid.universalpluginupdater.command.CommandContext;
import me.dreamvoid.universalpluginupdater.command.ISubCommand;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.objects.UpdateInfo;
import me.dreamvoid.universalpluginupdater.service.AsyncLock;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * update 子命令处理器
 * 检查所有插件的更新
 */
public final class UpdateCommand implements ISubCommand {
    private final Logger logger;

    public UpdateCommand(IPlatformProvider platform) {
        logger = platform.getPlatformLogger();
    }

    @Override
    public void execute(CommandContext context) {
        Locale locale = context.getSender().getLocale();
        if(AsyncLock.tryAcquire()){
            try {
                context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.update.start"));

                // 获取待更新的插件列表
                UpdateManager updateManager = UpdateManager.getInstance();
                List<UpdateInfo> updateInfos = updateManager.checkAllPluginUpdates();

                if (updateInfos.isEmpty()) {
                    context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.update.none"));
                    return;
                }

                // 显示可更新的插件数量
                context.getSender().broadcastMessage(LanguageService.instance().tr(locale,
                    "message.command.update.count",
                        updateInfos.size()));

                // 详细列出每个可更新的插件
                for (UpdateInfo update : updateInfos) {
                    String message = MessageFormat.format("  &7- &b{0} &f({1} &7-> &f{2}) &7[{3}]", update.pluginName(), update.currentVersion(), update.newVersion(), update.updateChannel());
                    context.getSender().sendMessage(message);
                }

                context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.update.next"));
            } catch (Exception e) {
                logger.severe(LanguageService.instance().tr("message.command.update.error.log", e));
                context.getSender().broadcastMessage(LanguageService.instance().tr(locale, "message.command.update.error.game"));
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
        // TODO: 占位
        return Collections.emptyList();
    }
}
